#!/usr/bin/env python3
"""Validates the shared agent infrastructure: skills, subagents, routing docs and guard hook.

Run through scripts/agent/validate.sh. Needs PyYAML (pip install pyyaml). Counts are
discovered from the tree, never hard-coded. Pass --root to validate another tree
(scripts/agent/test_agent_infra.py does this with deliberately broken copies).
"""
from __future__ import annotations

import sys

sys.dont_write_bytecode = True  # do not leave __pycache__ in the repository

import argparse
import importlib.util
import json
import os
import re

try:
    import yaml
except ImportError:  # pragma: no cover
    sys.stderr.write("agent validation: PyYAML is required (pip install pyyaml); frontmatter cannot be checked without it\n")
    sys.exit(2)

# Frontmatter fields documented by Claude Code and the Agent Skills specification (2026-10-25).
SKILL_FIELDS = {
    "name", "description", "when_to_use", "argument-hint", "arguments", "disable-model-invocation",
    "user-invocable", "allowed-tools", "disallowed-tools", "model", "effort", "context", "agent",
    "background", "hooks", "paths", "shell", "metadata", "license", "compatibility",
}
AGENT_FIELDS = {
    "name", "description", "tools", "disallowedTools", "model", "permissionMode", "maxTurns",
    "autoCompactWindow", "skills", "mcpServers", "hooks", "memory", "background", "omitClaudeMd",
    "effort", "isolation", "color", "initialPrompt", "experimental",
}
KNOWN_TOOLS = {
    "Read", "Grep", "Glob", "Edit", "Write", "MultiEdit", "NotebookEdit", "Bash", "BashOutput", "KillShell",
    "WebSearch", "WebFetch", "TodoWrite", "Skill", "Agent", "Task", "LS", "ExitPlanMode",
}
WRITE_TOOLS = {"Edit", "Write", "MultiEdit", "NotebookEdit"}
MODEL_ALIASES = {"inherit", "sonnet", "opus", "haiku", "fable"}
NAME_RE = re.compile(r"^[a-z0-9]+(-[a-z0-9]+)*$")
MAX_AGENTS_MD_LINES = 80
MAX_SKILL_LINES = 500
MAX_DESCRIPTION = 1024

PATH_REF_RE = re.compile(r"(?<![\w/.-])((?:\.agents/skills|scripts|docs)/[A-Za-z0-9_./-]*[A-Za-z0-9_])")
LINK_RE = re.compile(r"\]\(([^)\s#]+)(?:#[^)]*)?\)")
MACHINE_RE = re.compile(r"/Users/|\.agents/memories|memory-sync|\.agents/\.obsidian")
OBSOLETE = [".agents/.obsidian", ".agents/memories", ".agents/plugins", ".agents/rules", ".agents/tasks", ".agents/wiki"]


class UniqueKeyLoader(yaml.SafeLoader):
    """SafeLoader that rejects duplicate mapping keys (PyYAML keeps the last silently)."""


def _construct_mapping(loader, node, deep=False):
    seen = set()
    for key_node, _ in node.value:
        key = loader.construct_object(key_node, deep=deep)
        if key in seen:
            raise yaml.constructor.ConstructorError(None, None, f"duplicate key {key!r}", key_node.start_mark)
        seen.add(key)
    return yaml.SafeLoader.construct_mapping(loader, node, deep)


UniqueKeyLoader.add_constructor(yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, _construct_mapping)


class Report:
    def __init__(self) -> None:
        self.errors: list[str] = []
        self.warnings: list[str] = []

    def error(self, where: str, message: str) -> None:
        self.errors.append(f"{where}: {message}")

    def warn(self, where: str, message: str) -> None:
        self.warnings.append(f"{where}: {message}")


def read(path: str) -> str:
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def frontmatter(path: str, rep: Report, rel: str):
    text = read(path)
    if not text.startswith("---\n"):
        rep.error(rel, "no YAML frontmatter (file must start with '---')")
        return None, text
    end = text.find("\n---", 4)
    if end < 0:
        rep.error(rel, "frontmatter is not closed with '---'")
        return None, text
    block, body = text[4:end], text[end + 4:].lstrip("\n")
    try:
        data = yaml.load(block, Loader=UniqueKeyLoader)
    except yaml.YAMLError as exc:
        first = str(exc).strip().splitlines()[0]
        rep.error(rel, f"frontmatter is not valid YAML ({first}); quote the value if it contains ': '")
        return None, body
    if not isinstance(data, dict):
        rep.error(rel, "frontmatter must be a mapping")
        return None, body
    return data, body


def check_name(rep: Report, rel: str, name, expected: str, what: str) -> None:
    if not isinstance(name, str) or not name:
        rep.error(rel, "name is missing")
        return
    if name != expected:
        rep.error(rel, f"name '{name}' must match the {what} '{expected}'")
    if not NAME_RE.match(name) or len(name) > 64:
        rep.error(rel, f"name '{name}' must be 1-64 lowercase letters, digits and single hyphens")
    if re.search(r"anthropic|claude", name):
        rep.error(rel, f"name '{name}' uses a reserved word")


def check_description(rep: Report, rel: str, description) -> None:
    if not isinstance(description, str) or not description.strip():
        rep.error(rel, "description is missing or empty")
    elif len(description) > MAX_DESCRIPTION:
        rep.error(rel, f"description has {len(description)} characters (limit {MAX_DESCRIPTION})")
    elif re.search(r"<[A-Za-z/][^>]*>", description):
        rep.error(rel, "description must not contain XML tags")


def split_tools(value) -> list[str]:
    if isinstance(value, list):
        return [str(v).strip() for v in value]
    parts, depth, current = [], 0, ""
    for ch in str(value):
        depth += ch == "("
        depth -= ch == ")"
        if ch == "," and depth == 0:
            parts.append(current.strip())
            current = ""
        else:
            current += ch
    if current.strip():
        parts.append(current.strip())
    return parts


def load_guard(root: str, rep: Report):
    path = os.path.join(root, "scripts/agent/guard-subagent.py")
    if not os.path.isfile(path):
        rep.error("scripts/agent/guard-subagent.py", "missing (subagent guard hook)")
        return None
    spec = importlib.util.spec_from_file_location("guard_subagent", path)
    module = importlib.util.module_from_spec(spec)
    try:
        spec.loader.exec_module(module)
    except Exception as exc:  # noqa: BLE001 - any import failure is a validation error
        rep.error("scripts/agent/guard-subagent.py", f"cannot be imported ({exc})")
        return None
    if not os.access(path, os.X_OK):
        rep.error("scripts/agent/guard-subagent.py", "is not executable")
    return module


def validate_skills(root: str, rep: Report) -> dict:
    skills = {}
    base = os.path.join(root, ".agents/skills")
    for name in sorted(os.listdir(base)):
        directory = os.path.join(base, name)
        if not os.path.isdir(directory):
            continue
        rel = f".agents/skills/{name}/SKILL.md"
        path = os.path.join(root, rel)
        if not os.path.isfile(path):
            rep.error(f".agents/skills/{name}", "directory has no SKILL.md")
            continue
        data, body = frontmatter(path, rep, rel)
        vendor = os.path.isfile(os.path.join(directory, ".graphify_version"))
        skills[name] = {"vendor": vendor, "lines": body.count("\n") + 1, "tokens": len(body.encode()) // 4}
        if data is None:
            continue
        for key in data:
            if key not in SKILL_FIELDS:
                rep.error(rel, f"unsupported frontmatter field '{key}'")
        check_name(rep, rel, data.get("name"), name, "directory")
        check_description(rep, rel, data.get("description"))
        lines = body.count("\n") + 1
        if lines > MAX_SKILL_LINES:
            (rep.warn if vendor else rep.error)(rel, f"{lines} lines exceeds {MAX_SKILL_LINES}" + (" (vendored skill, left unchanged)" if vendor else ""))
        for target in LINK_RE.findall(body):
            if re.match(r"^[a-z]+:", target) or target.startswith("/"):
                continue
            if not os.path.exists(os.path.normpath(os.path.join(directory, target))):
                rep.error(rel, f"link target '{target}' does not exist")
    return skills


def validate_agents(root: str, rep: Report, skills: dict, guard) -> dict:
    agents = {}
    base = os.path.join(root, ".claude/agents")
    for filename in sorted(os.listdir(base)):
        if not filename.endswith(".md"):
            continue
        stem = filename[:-3]
        rel = f".claude/agents/{filename}"
        data, body = frontmatter(os.path.join(base, filename), rep, rel)
        if data is None:
            continue
        for key in data:
            if key not in AGENT_FIELDS:
                rep.error(rel, f"unsupported frontmatter field '{key}'")
        check_name(rep, rel, data.get("name"), stem, "file name")
        check_description(rep, rel, data.get("description"))
        model = data.get("model", "inherit")
        if model not in MODEL_ALIASES and not re.match(r"^claude-[a-z0-9-]+(\[1m\])?$", str(model)):
            rep.error(rel, f"model '{model}' is not an alias or a model ID")
        tools = split_tools(data["tools"]) if "tools" in data else []
        if "tools" not in data:
            rep.error(rel, "tools must be listed explicitly (an omitted list inherits every tool)")
        for tool in tools:
            bare = re.sub(r"\(.*\)$", "", tool)
            if bare not in KNOWN_TOOLS and not bare.startswith("mcp__"):
                rep.error(rel, f"unknown tool '{tool}'")
        listed = data.get("skills") or []
        if not isinstance(listed, list) or not all(isinstance(s, str) for s in listed):
            rep.error(rel, "skills must be a list of skill names")
            listed = []
        if len(set(listed)) != len(listed):
            rep.error(rel, "skills lists a skill twice")
        if len(listed) > 32:
            rep.error(rel, "skills lists more than 32 entries")
        for skill in listed:
            if skill not in skills:
                rep.error(rel, f"skills lists unknown skill '{skill}'")
        agents[stem] = {"tools": set(tools), "skills": listed, "tokens": len(body.encode()) // 4}
        if guard is not None:
            profile = guard.PROFILES.get(stem)
            if profile is None:
                rep.error(rel, "is not listed in PROFILES of scripts/agent/guard-subagent.py (choose a permission profile)")
            elif profile in {"readonly", "device-scan"} and agents[stem]["tools"] & WRITE_TOOLS:
                rep.error(rel, f"profile '{profile}' must not have file-writing tools")
    if guard is not None:
        for stem in guard.PROFILES:
            if stem not in agents:
                rep.error("scripts/agent/guard-subagent.py", f"PROFILES lists '{stem}' but .claude/agents/{stem}.md does not exist")
    return agents


def validate_routing_docs(root: str, rep: Report, skills: dict, agents: dict) -> int:
    agents_md = os.path.join(root, "AGENTS.md")
    if not os.path.isfile(agents_md):
        rep.error("AGENTS.md", "missing")
        return 0
    text = read(agents_md)
    lines = text.count("\n")
    if lines > MAX_AGENTS_MD_LINES:
        rep.error("AGENTS.md", f"{lines} lines exceeds the {MAX_AGENTS_MD_LINES}-line context budget")
    for name in skills:
        if f"`{name}`" not in text:
            rep.error("AGENTS.md", f"does not route to skill `{name}`")
    for name in agents:
        if f"`{name}`" not in text:
            rep.error("AGENTS.md", f"does not route to subagent `{name}`")
    for token in set(re.findall(r"`(testfly-[a-z0-9-]+)`", text)):
        if token not in skills:
            rep.error("AGENTS.md", f"mentions unknown skill `{token}`")

    claude_md = os.path.join(root, "CLAUDE.md")
    if os.path.islink(claude_md):
        if os.path.realpath(claude_md) != os.path.realpath(agents_md):
            rep.error("CLAUDE.md", "is a symlink that does not point to AGENTS.md")
    elif not os.path.isfile(claude_md):
        rep.error("CLAUDE.md", "missing (it must import AGENTS.md with a line '@AGENTS.md')")
    else:
        claude = read(claude_md)
        if not re.search(r"^@AGENTS\.md\s*$", claude, re.M):
            rep.error("CLAUDE.md", "must import AGENTS.md with a line '@AGENTS.md' (Claude Code does not read AGENTS.md when CLAUDE.md exists)")
        if claude.count("\n") > 30:
            rep.error("CLAUDE.md", "has more than 30 lines; shared rules belong in AGENTS.md")
        shared = set(re.findall(r"^#{1,3} .+$", text, re.M)) & set(re.findall(r"^#{1,3} .+$", claude, re.M))
        if shared:
            rep.error("CLAUDE.md", f"duplicates AGENTS.md sections {sorted(shared)}; keep one source")
    return lines


def validate_links_and_paths(root: str, rep: Report) -> None:
    files = [os.path.join(root, "AGENTS.md")]
    for top, _, names in os.walk(os.path.join(root, ".claude/agents")):
        files += [os.path.join(top, n) for n in names if n.endswith(".md")]
    for top, _, names in os.walk(os.path.join(root, ".agents/skills")):
        if os.path.isfile(os.path.join(top, ".graphify_version")) or "/graphify" in top.replace(os.sep, "/"):
            continue  # vendored skill
        files += [os.path.join(top, n) for n in names if n.endswith(".md")]
    for path in files:
        if not os.path.isfile(path):
            continue
        rel = os.path.relpath(path, root)
        bases = [root]
        parts = rel.split(os.sep)
        if parts[:2] == [".agents", "skills"] and len(parts) > 3:
            bases.append(os.path.join(root, *parts[:3]))  # paths relative to the skill's own directory
        for ref in sorted(set(PATH_REF_RE.findall(read(path)))):
            if not any(os.path.exists(os.path.join(b, ref)) for b in bases):
                rep.error(rel, f"references '{ref}', which does not exist")
        if MACHINE_RE.search(read(path)):
            rep.error(rel, "contains a machine-specific or retired-infrastructure reference")


def validate_settings(root: str, rep: Report) -> None:
    path = os.path.join(root, ".claude/settings.json")
    if not os.path.isfile(path):
        rep.error(".claude/settings.json", "missing")
        return
    try:
        settings = json.loads(read(path))
    except ValueError as exc:
        rep.error(".claude/settings.json", f"is not valid JSON ({exc})")
        return
    entries = (settings.get("hooks") or {}).get("PreToolUse") or []
    covered = set()
    for entry in entries:
        commands = " ".join(h.get("command", "") for h in entry.get("hooks", []))
        if "guard-subagent.py" in commands:
            covered |= set((entry.get("matcher") or "").split("|"))
    missing = {"Bash", "Edit", "Write"} - covered
    if missing:
        rep.error(".claude/settings.json", f"PreToolUse hook running scripts/agent/guard-subagent.py must match {sorted(missing)}")
    for link in (".claude/skills", ".kiro/skills"):
        full = os.path.join(root, link)
        if os.path.lexists(full):
            if not os.path.islink(full) or os.path.realpath(full) != os.path.realpath(os.path.join(root, ".agents/skills")):
                rep.error(link, "must be a symlink to ../.agents/skills")
        elif link == ".claude/skills":
            rep.error(link, "missing: Claude Code discovers the shared skills through this symlink")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))
    root = os.path.abspath(parser.parse_args().root)
    rep = Report()

    for rel in OBSOLETE:
        if os.path.exists(os.path.join(root, rel)):
            rep.error(rel, "obsolete path remains")
    guard = load_guard(root, rep)
    skills = validate_skills(root, rep)
    agents = validate_agents(root, rep, skills, guard)
    agents_lines = validate_routing_docs(root, rep, skills, agents)
    validate_links_and_paths(root, rep)
    validate_settings(root, rep)

    for message in rep.warnings:
        print(f"agent validation: warning: {message}")
    if rep.errors:
        for message in rep.errors:
            print(f"agent validation: FAIL: {message}", file=sys.stderr)
        print(f"agent validation: FAIL ({len(rep.errors)} problems)", file=sys.stderr)
        return 1
    print(f"agent validation: PASS ({len(skills)} skills, {len(agents)} subagents, {agents_lines} AGENTS.md lines, {len(rep.warnings)} warnings)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
