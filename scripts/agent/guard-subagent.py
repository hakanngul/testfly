#!/usr/bin/env python3
"""PreToolUse hook that confines subagents. The main session is never restricted.

Claude Code sends the hook a JSON payload on stdin. Calls made inside a subagent
carry `agent_id` and `agent_type`; calls from the main session carry neither, so
the lead keeps the ability to commit and push after the user approves.

Exit 0 allows the call. Exit 2 blocks it and returns stderr to the model.

This is a safety net against mistakes and prompt injection, not a sandbox: a
subagent that writes a script and runs it is not analysed. Every project agent
must be listed in PROFILES (scripts/agent/validate_agents.py enforces it).
"""
from __future__ import annotations

import json
import os
import re
import shlex
import sys

# agent_type -> profile
#   developer  : may edit production code (src/main), not agent infrastructure
#   writer     : may edit tests, docs and config, not src/main and not agent infrastructure
#   readonly   : no edits; Bash limited to a read-only allowlist
#   device-scan: no edits; Bash may drive a device but not destroy it; redirects only into /tmp
#   default    : any other subagent (built-in or plugin): base Bash policy, no infrastructure edits
PROFILES = {
    "java-developer": "developer",
    "mobile-developer": "developer",
    "docs-writer": "writer",
    "test-author": "writer",
    "tdd-test-writer": "writer",
    "testng-test-writer": "writer",
    "junit5-test-writer": "writer",
    "cucumber-test-writer": "writer",
    "api-test-writer": "writer",
    "ios-test-writer": "writer",
    "android-test-writer": "writer",
    "k6-load-test-writer": "writer",
    "ci-triage": "writer",
    "code-reviewer": "readonly",
    "researcher": "readonly",
    "mobile-locator-finder": "device-scan",
}

PROTECTED_PATHS = (
    ".claude/", ".agents/", ".kiro/", "AGENTS.md", "CLAUDE.md",
    "scripts/agent/", ".github/workflows/", ".git/",
)
PRODUCTION_PATHS = ("src/main/",)
PROTECTED_HOME = (".claude", ".ssh", ".aws", ".gnupg", ".gitconfig", ".config/gh", ".git-credentials", ".netrc")

SEPARATORS = {";", "&&", "||", "|", "&", "|&", "(", ")", ";;"}
WRAPPERS = {"command", "builtin", "exec", "time", "nohup", "nice", "ionice", "stdbuf", "setsid", "env", "timeout", "xargs"}
SHELLS = {"bash", "sh", "zsh", "dash", "ksh"}

GIT_GLOBAL_FLAGS = {"--no-pager", "-P", "--paginate", "-p", "--no-optional-locks", "--literal-pathspecs", "--bare"}
GIT_READ_ONLY = {
    "status", "diff", "log", "show", "blame", "annotate", "ls-files", "ls-tree", "rev-parse",
    "rev-list", "describe", "cat-file", "grep", "merge-base", "shortlog", "name-rev", "diff-tree",
    "diff-index", "diff-files", "show-ref", "for-each-ref", "whatchanged", "version", "help",
}
GIT_LISTING_ONLY = {  # subcommand -> flags that keep it a read
    "branch": {"--show-current", "--list", "-l", "-a", "-r", "-v", "-vv", "--all", "--remotes", "--verbose",
               "--contains", "--no-contains", "--merged", "--no-merged", "--points-at", "--sort", "--format", "--abbrev"},
    "tag": {"-l", "--list", "-n", "--contains", "--no-contains", "--merged", "--no-merged", "--points-at", "--sort", "--format"},
    "remote": {"-v", "--verbose"},
    "config": {"--get", "--get-all", "--get-regexp", "--list", "-l", "--show-origin", "--name-only"},
    "stash": {"list", "show"},
    "worktree": {"list"},
}

READONLY_COMMANDS = {
    "cat", "head", "tail", "ls", "wc", "sort", "uniq", "cut", "tr", "nl", "stat", "file", "find", "rg",
    "grep", "egrep", "fgrep", "diff", "cmp", "jq", "basename", "dirname", "realpath", "readlink", "pwd",
    "echo", "printf", "which", "type", "true", "false", "test", "[", "javap", "unzip", "sed", "column",
    "tree", "du", "date", "git", "cd",
}
FIND_WRITES = {"-delete", "-exec", "-execdir", "-ok", "-okdir", "-fprint", "-fprint0", "-fprintf", "-fls"}
DEVICE_TOOLS = {"mobilecli", "adb", "xcrun", "simctl", "emulator", "idb"}
DEVICE_DESTRUCTIVE = {
    "uninstall", "install", "erase", "wipe", "delete", "shutdown", "reboot", "factory-reset", "clear",
    "reset", "kill", "rm", "root", "remount", "format", "emu", "wipe-data",
}
SAFE_RM_ROOTS = ("target/", "./target/", "/tmp/", "build/", "node_modules/", "docs-site/node_modules/", "docs-site/build/")
LOOPBACK = re.compile(r"(localhost|127\.0\.0\.1|\[::1\]|0\.0\.0\.0)")


class Denied(Exception):
    pass


def deny(reason: str) -> None:
    raise Denied(reason)


# ---------------------------------------------------------------- Bash parsing

def _prepare(command: str) -> str:
    """Drop heredoc bodies and turn unquoted newlines into separators."""
    out, pending = [], None
    for line in command.split("\n"):
        if pending is not None:
            if line.strip() == pending:
                pending = None
            continue
        m = re.search(r"<<-?\s*['\"]?([A-Za-z0-9_]+)['\"]?", line)
        out.append(line)
        if m:
            pending = m.group(1)
    return " ; ".join(out)


def split_segments(command: str) -> list[list[str]]:
    lexer = shlex.shlex(_prepare(command), posix=True, punctuation_chars="();<>|&")
    lexer.commenters = ""
    lexer.whitespace_split = True  # keeps words such as release:perform whole
    try:
        tokens = list(lexer)
    except ValueError as exc:
        deny(f"cannot parse the command ({exc}); simplify it")
    segments, current = [], []
    for tok in tokens:
        if tok in SEPARATORS:
            if current:
                segments.append(current)
            current = []
        else:
            current.append(tok)
    if current:
        segments.append(current)
    return segments


def _strip_wrappers(tokens: list[str], depth: int) -> list[str]:
    tokens = list(tokens)
    while tokens:
        while tokens and re.match(r"^[A-Za-z_][A-Za-z0-9_]*=", tokens[0]):
            tokens.pop(0)
        if not tokens:
            break
        name = os.path.basename(tokens[0])
        if name in {"sudo", "su", "doas", "pkexec"}:
            deny(f"'{name}' is not allowed for subagents")
        if name in SHELLS and len(tokens) > 2 and re.match(r"^-[a-z]*c[a-z]*$", tokens[1]):
            analyse(tokens[2], depth + 1)
            return []
        if name == "eval":
            analyse(" ".join(tokens[1:]), depth + 1)
            return []
        if name not in WRAPPERS:
            break
        tokens.pop(0)
        if name == "timeout":
            while tokens and tokens[0].startswith("-"):
                tokens.pop(0)
            if tokens:
                tokens.pop(0)
        elif name in {"env", "xargs", "nice", "ionice", "stdbuf", "nohup", "time", "setsid", "command", "builtin", "exec"}:
            while tokens and (tokens[0].startswith("-") or re.match(r"^[A-Za-z_][A-Za-z0-9_]*=", tokens[0])):
                tokens.pop(0)
    return tokens


# ---------------------------------------------------------------- Bash policy

def check_git(args: list[str]) -> None:
    i = 0
    while i < len(args):
        a = args[i]
        if a in GIT_GLOBAL_FLAGS:
            i += 1
        elif a == "-C" and i + 1 < len(args):
            i += 2
        elif a.startswith(("--git-dir=", "--work-tree=", "--namespace=")):
            i += 1
        elif a == "-c" or a.startswith(("-c", "--exec-path", "--config-env")):
            deny("git -c / --exec-path is not allowed for subagents (config injection)")
        else:
            break
    if i >= len(args):
        return
    sub, rest = args[i], args[i + 1:]
    if any(r == "--output" or r.startswith("--output=") or r == "-o" for r in rest) and sub in {"diff", "log", "show", "format-patch"}:
        deny("git --output writes files; the lead writes files")
    if sub in GIT_READ_ONLY:
        return
    if sub in GIT_LISTING_ONLY:
        allowed = GIT_LISTING_ONLY[sub]
        if sub in {"stash", "worktree"}:
            if rest and rest[0] in allowed:
                return
        elif sub in {"remote"}:
            if all(r in allowed or r in {"show", "get-url"} for r in rest) or (rest and rest[0] in {"show", "get-url"}):
                return
        else:
            takes_value = {"--contains", "--no-contains", "--merged", "--no-merged", "--points-at", "--sort", "--format", "--abbrev", "-n"}
            list_mode = any(r in {"-l", "--list", "--get", "--get-all", "--get-regexp", "--contains", "--no-contains",
                                 "--merged", "--no-merged", "--points-at"} for r in rest)
            ok = True
            skip = False
            for r in rest:
                if skip:
                    skip = False
                    continue
                if r.startswith("-"):
                    base = r.split("=", 1)[0]
                    if base not in allowed:
                        ok = False
                    elif base in takes_value and "=" not in r:
                        skip = True
                elif not list_mode:
                    ok = False
            if ok:
                return
    if sub in {"commit", "push", "merge", "rebase", "reset", "cherry-pick", "revert", "tag", "am", "pull"}:
        deny(f"git {sub} is lead-only: report the finished work and let the lead commit and push after the user approves")
    deny(f"git {sub} is not allowed for subagents (read-only git: status, diff, log, show, blame, ls-files, rev-parse ...)")


def check_rm(args: list[str]) -> None:
    recursive = any(re.match(r"^-[A-Za-z]*[rR][A-Za-z]*$", a) or a == "--recursive" for a in args)
    if not recursive:
        return
    targets = [a for a in args if not a.startswith("-")]
    if not targets:
        deny("rm -r without a target")
    for t in targets:
        if t in {"target", "./target", "build", "node_modules"}:
            continue
        if not t.startswith(SAFE_RM_ROOTS) or ".." in t.split("/"):
            deny(f"recursive rm is limited to build and temp directories, not '{t}'")


def check_common(name: str, args: list[str], segment_text: str) -> None:
    if name == "git":
        check_git(args)
    elif name == "rm":
        check_rm(args)
    elif name == "find" and any(a in FIND_WRITES for a in args):
        deny("find with -delete/-exec/-fprint is not allowed for subagents")
    elif name in {"mvn", "mvnw", "./mvnw"} and any(re.match(r"^(deploy|release:.*|site-deploy|gpg:sign.*|deploy:.*)$", a) for a in args):
        deny("publishing goals (deploy, release:*) are release-operator actions")
    elif name in {"npm", "yarn", "pnpm", "npx"} and any(a in {"publish", "login", "adduser", "unpublish", "deprecate"} for a in args):
        deny("registry publishing and login are not allowed for subagents")
    elif name == "gh":
        read_only = {("pr", "view"), ("pr", "list"), ("pr", "diff"), ("pr", "checks"), ("pr", "status"),
                     ("run", "view"), ("run", "list"), ("issue", "view"), ("issue", "list"), ("repo", "view")}
        pair = tuple(a for a in args if not a.startswith("-"))[:2]
        if pair not in read_only:
            deny("gh is limited to read-only pr/run/issue/repo views for subagents")
    elif name in {"curl", "wget"}:
        writes = any(a in {"-X", "--request", "-d", "--data", "--data-raw", "--data-binary", "-F", "--form", "-T", "--upload-file", "--post-data", "--post-file", "--method"}
                     or a.startswith(("--data", "-d")) for a in args)
        if writes and not LOOPBACK.search(segment_text):
            deny("curl/wget with a request body or write method is limited to loopback targets")
    elif name in {"dd", "mkfs", "shutdown", "reboot", "poweroff", "halt", "mount", "umount"} or name.startswith("mkfs."):
        deny(f"'{name}' is not allowed for subagents")
    elif name in {"chmod", "chown", "chgrp"} and any(a in {"-R", "--recursive"} for a in args):
        deny(f"recursive {name} is not allowed for subagents")


def check_redirects(tokens: list[str], roots: tuple[str, ...] | None) -> None:
    if roots is None:
        return
    for i, tok in enumerate(tokens):
        if ">" in tok and set(tok) <= set(">&|0123456789"):
            target = tokens[i + 1] if i + 1 < len(tokens) else ""
            if tok.endswith("&") and target.isdigit():
                continue
            if target == "/dev/null" or target.startswith(roots):
                continue
            deny(f"redirecting output to '{target}' is not allowed for this agent")
        if tok in {"tee"}:
            deny("tee writes files; this agent is limited")


def check_readonly(name: str, args: list[str]) -> None:
    if name not in READONLY_COMMANDS:
        deny(f"'{name}' is not in the read-only command list; ask the lead to run builds, tests and gates (for example scripts/agent/verify.sh) and send you the output")
    if name == "sed" and any(a == "--in-place" or re.match(r"^-[A-Za-z]*i", a) for a in args):
        deny("sed -i edits files")
    if name == "unzip" and not any(a.startswith(("-l", "-p", "-Z")) for a in args):
        deny("unzip is limited to listing (-l) and printing (-p)")


def analyse_segment(tokens: list[str], profile: str, depth: int) -> None:
    tokens = _strip_wrappers(tokens, depth)
    if not tokens:
        return
    name, args = os.path.basename(tokens[0]), tokens[1:]
    # pipelines into a shell run arbitrary text
    if name in SHELLS and not args:
        deny("piping into a shell is not allowed for subagents")
    check_common(name, args, " ".join(tokens))
    if profile == "readonly":
        check_readonly(name, args)
        check_redirects(tokens, ())
    elif profile == "device-scan":
        check_redirects(tokens, ("/tmp/", "target/"))
        if name in DEVICE_TOOLS and any(a in DEVICE_DESTRUCTIVE for a in args):
            deny("destructive or state-changing device commands are not allowed for the locator finder (read screens, tap to navigate)")


def analyse(command: str, profile: str, depth: int = 0) -> None:
    if depth > 3:
        deny("command nesting is too deep to verify")
    for segment in split_segments(command):
        analyse_segment(segment, profile, depth)


# ---------------------------------------------------------------- file policy

def check_edit(payload: dict, profile: str) -> None:
    if profile in {"readonly", "device-scan"}:
        deny("this agent is read-only and must not edit files; return the result to the lead")
    tool_input = payload.get("tool_input") or {}
    raw = tool_input.get("file_path") or tool_input.get("notebook_path") or tool_input.get("path") or ""
    if not raw:
        return
    root = os.path.realpath(os.environ.get("CLAUDE_PROJECT_DIR") or payload.get("cwd") or os.getcwd())
    path = os.path.realpath(os.path.join(payload.get("cwd") or root, os.path.expanduser(raw)))
    home = os.path.realpath(os.path.expanduser("~"))
    for h in PROTECTED_HOME:
        protected = os.path.join(home, h)
        if path == protected or path.startswith(protected + os.sep):
            deny(f"'{raw}' is a user configuration or credential path")
    if path != root and not path.startswith(root + os.sep):
        return
    rel = os.path.relpath(path, root).replace(os.sep, "/")
    for p in PROTECTED_PATHS:
        if rel == p or (p.endswith("/") and (rel.startswith(p) or rel == p[:-1])):
            deny(f"'{rel}' is agent infrastructure; only the lead changes it")
    if profile == "writer":
        for p in PRODUCTION_PATHS:
            if rel.startswith(p):
                deny(f"'{rel}' is production code: this agent hands production changes to java-developer or mobile-developer through the lead")


# ---------------------------------------------------------------- entry point

def evaluate(payload: dict) -> None:
    if not payload.get("agent_id"):
        return  # main session: never restricted
    profile = PROFILES.get(payload.get("agent_type") or "", "default")
    tool = payload.get("tool_name")
    if tool == "Bash":
        analyse((payload.get("tool_input") or {}).get("command") or "", profile)
    elif tool in {"Edit", "Write", "MultiEdit", "NotebookEdit"}:
        check_edit(payload, profile)


def main() -> int:
    try:
        payload = json.load(sys.stdin)
    except (ValueError, OSError):
        return 0  # no payload: nothing to judge
    try:
        evaluate(payload)
    except Denied as exc:
        sys.stderr.write(f"guard-subagent: blocked ({exc}). Report this to the lead; the lead performs commits, pushes and agent-infrastructure changes.\n")
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
