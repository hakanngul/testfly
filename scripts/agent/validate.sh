#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

fail() {
  echo "agent validation: $*" >&2
  exit 1
}

required_files=(
  AGENTS.md
  .agents/skills/testfly-change/SKILL.md
  .agents/skills/testfly-api-write/SKILL.md
  .agents/skills/testfly-web-write/SKILL.md
  .agents/skills/testfly-locator-finder/SKILL.md
  .agents/skills/testfly-docs/SKILL.md
  .agents/skills/testfly-test-authoring/SKILL.md
  .agents/skills/testfly-triage/SKILL.md
  .agents/skills/testfly-verify/SKILL.md
  .agents/skills/testfly-mobile/SKILL.md
  .agents/skills/testfly-review/SKILL.md
  .agents/skills/testfly-research/SKILL.md
  .agents/skills/testfly-mobile-write/SKILL.md
  .agents/skills/testfly-k6-write/SKILL.md
  .agents/skills/testfly-mobile-locator-finder/SKILL.md
  .agents/skills/graphify/SKILL.md
)

for path in "${required_files[@]}"; do
  [[ -f "$path" ]] || fail "missing $path"
done

skill_count=0
for skill_file in .agents/skills/*/SKILL.md; do
  [[ -f "$skill_file" ]] || continue
  skill_count=$((skill_count + 1))
  skill_dir="$(basename "$(dirname "$skill_file")")"
  first_line="$(sed -n '1p' "$skill_file")"
  [[ "$first_line" == "---" ]] || fail "$skill_file has no YAML frontmatter"
  skill_name="$(sed -n '2,/^---$/s/^name:[[:space:]]*//p' "$skill_file" | head -n 1)"
  description="$(sed -n '2,/^---$/s/^description:[[:space:]]*//p' "$skill_file" | head -n 1)"
  [[ "$skill_name" == "$skill_dir" ]] || fail "$skill_file name must match its directory"
  [[ -n "$description" ]] || fail "$skill_file has no description"
done
[[ "$skill_count" -eq 15 ]] || fail "expected 15 shared skills, found $skill_count"

# Claude Code discovers the shared skills through this link.
[[ -L .claude/skills && "$(readlink .claude/skills)" == "../.agents/skills" ]] || fail ".claude/skills must link to ../.agents/skills"

# Subagents: name matches the file, description present, every listed skill exists.
agent_count=0
for agent_file in .claude/agents/*.md; do
  [[ -f "$agent_file" ]] || continue
  agent_count=$((agent_count + 1))
  [[ "$(sed -n '1p' "$agent_file")" == "---" ]] || fail "$agent_file has no YAML frontmatter"
  agent_name="$(sed -n '2,/^---$/s/^name:[[:space:]]*//p' "$agent_file" | head -n 1)"
  [[ "$agent_name" == "$(basename "$agent_file" .md)" ]] || fail "$agent_file name must match its file name"
  [[ -n "$(sed -n '2,/^---$/s/^description:[[:space:]]*//p' "$agent_file" | head -n 1)" ]] || fail "$agent_file has no description"
  while IFS= read -r skill; do
    [[ -f ".agents/skills/$skill/SKILL.md" ]] || fail "$agent_file lists unknown skill $skill"
  done < <(sed -n '2,/^---$/p' "$agent_file" | sed -n 's/^[[:space:]]*-[[:space:]]*\([a-z][a-z0-9-]*\)[[:space:]]*$/\1/p')
done


obsolete_paths=(
  .agents/.obsidian
  .agents/memories
  .agents/plugins
  .agents/rules
  .agents/tasks
  .agents/wiki
)
for path in "${obsolete_paths[@]}"; do
  [[ ! -e "$path" ]] || fail "obsolete path remains: $path"
done

if rg -n '/Users/|\.agents/memories|memory-sync|\.agents/\.obsidian' AGENTS.md .agents/skills .claude/agents; then
  fail "new agent configuration contains machine-specific or retired infrastructure references"
fi

agents_lines="$(wc -l < AGENTS.md | tr -d ' ')"
[[ "$agents_lines" -le 80 ]] || fail "AGENTS.md exceeds the 80-line context budget"

echo "agent validation: PASS ($skill_count skills, $agent_count subagents, $agents_lines AGENTS.md lines)"
