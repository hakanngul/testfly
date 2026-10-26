#!/usr/bin/env bash
# Estimates the instruction context that skills and subagents load.
#
#   scripts/agent/measure-context.sh [git-ref]
#
# Without a ref it measures the working tree; with a ref it measures that commit
# (for before/after comparisons). Tokens are bytes/4: a reproducible proxy, not a
# measured model count (expect roughly +/-25%).
#
# Cost model, from the Claude Code docs: a subagent receives the CLAUDE.md
# hierarchy, its own definition and the FULL body of every skill in its `skills:`
# list. Skill descriptions are always loaded; skill bodies load on invocation.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

ref="${1:-}"
[[ -z "$ref" ]] || git rev-parse --verify "${ref}^{commit}" >/dev/null

REF="$ref" python3 -I - <<'PY'
import os, re, subprocess, sys

ref = os.environ.get("REF", "")

def read(path):
    if ref:
        r = subprocess.run(["git", "show", f"{ref}:{path}"], capture_output=True)
        return r.stdout.decode("utf-8", "replace") if r.returncode == 0 else None
    return open(path, encoding="utf-8").read() if os.path.isfile(path) else None

def listing(prefix):
    if ref:
        out = subprocess.run(["git", "ls-tree", "-r", "--name-only", ref, prefix],
                             capture_output=True, text=True).stdout
        return [l for l in out.splitlines() if l]
    found = []
    for root, _, files in os.walk(prefix):
        found += [os.path.join(root, f) for f in files]
    return found

def split(text):
    m = re.match(r"---\n(.*?)\n---\n(.*)", text, re.S)
    return (m.group(1), m.group(2)) if m else ("", text)

def field(head, key):
    m = re.search(rf"^{key}:\s*(.*)$", head, re.M)
    return m.group(1).strip() if m else ""

tok = lambda n: n // 4

# CLAUDE.md as Claude Code loads it: CLAUDE.md, plus AGENTS.md when it is imported.
claude = read("CLAUDE.md") or ""
agents_md = read("AGENTS.md") or ""
claude_bytes = len(claude.encode())
if re.search(r"^@AGENTS\.md\s*$", claude, re.M):
    claude_bytes += len(agents_md.encode())

skills = {}
for p in sorted(listing(".agents/skills")):
    m = re.match(r"\.agents/skills/([^/]+)/SKILL\.md$", p)
    if m:
        head, body = split(read(p))
        skills[m.group(1)] = (len(body.encode()), len(field(head, "description")))

agents = {}
for p in sorted(listing(".claude/agents")):
    if p.endswith(".md"):
        text = read(p)
        head, _ = split(text)
        name = os.path.basename(p)[:-3]
        listed = re.findall(r"^\s+-\s+([a-z][a-z0-9-]*)\s*$",
                            re.search(r"^skills:\s*\n((?:\s+-\s+.*\n)+)", head + "\n", re.M).group(1)
                            if re.search(r"^skills:\s*\n", head + "\n", re.M) else "", re.M)
        agents[name] = (len(text.encode()), len(field(head, "description")), listed)

print(f"# Context estimate for {ref or 'working tree'} (tokens = bytes/4)\n")
always = tok(claude_bytes) + tok(sum(d for _, d in skills.values())) + tok(sum(d for _, d, _ in agents.values()))
print(f"- CLAUDE.md as loaded: ~{tok(claude_bytes)} tokens")
print(f"- {len(skills)} skill descriptions: ~{tok(sum(d for _, d in skills.values()))} tokens")
print(f"- {len(agents)} agent descriptions: ~{tok(sum(d for _, d, _ in agents.values()))} tokens")
print(f"- always-loaded total: ~{always} tokens\n")
print("| agent | agent file | preload skills | preloaded names | CLAUDE.md | per-spawn total |")
print("|---|---:|---:|---|---:|---:|")
total = 0
for name, (size, _, listed) in sorted(agents.items()):
    pre = sum(skills[s][0] for s in listed if s in skills)
    spawn = tok(size) + tok(pre) + tok(claude_bytes)
    total += spawn
    print(f"| {name} | {tok(size)} | {tok(pre)} | {', '.join(listed) or '-'} | {tok(claude_bytes)} | {spawn} |")
print(f"\nSum of one spawn of every agent: ~{total} tokens")
PY
