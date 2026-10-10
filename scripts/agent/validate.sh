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
  .agents/skills/testfly-docs/SKILL.md
  .agents/skills/testfly-test-authoring/SKILL.md
  .agents/skills/testfly-triage/SKILL.md
  .agents/skills/testfly-verify/SKILL.md
  .agents/skills/graphify/SKILL.md
  .kiro/agents/testfly.json
  .kiro/steering/api-test-authoring.md
  .kiro/steering/web-test-authoring.md
  .kiro/steering/documentation.md
  .kiro/steering/java-sdk.md
  .kiro/steering/test-authoring.md
  .kiro/steering/test-triage.md
  .kiro/steering/verification.md
)

for path in "${required_files[@]}"; do
  [[ -f "$path" ]] || fail "missing $path"
done

if git check-ignore -q .kiro/agents/testfly.json; then
  fail ".kiro is ignored and cannot be shared"
fi

python3 -m json.tool .kiro/agents/testfly.json >/dev/null

python3 - <<'PY'
import json
from pathlib import Path

config = json.loads(Path(".kiro/agents/testfly.json").read_text())
resources = config.get("resources", [])
required = {"file://./AGENTS.md", "skill://.agents/skills/*/SKILL.md"}
missing = required.difference(resources)
if missing:
    raise SystemExit(f"agent validation: Kiro agent missing resources: {sorted(missing)}")
PY

if command -v kiro-cli >/dev/null 2>&1; then
  kiro-cli agent validate --path .kiro/agents/testfly.json
  kiro_agents="$(kiro-cli agent list 2>&1)"
  if ! rg -q '(^|[[:space:]])testfly([[:space:]]|$)' <<< "$kiro_agents"; then
    fail "Kiro CLI did not discover the workspace agent"
  fi
  echo "agent validation: Kiro workspace agent discovered"
else
  echo "agent validation: Kiro CLI unavailable; runtime discovery not checked"
fi

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
[[ "$skill_count" -eq 8 ]] || fail "expected 8 shared skills, found $skill_count"

for steering in .kiro/steering/*.md; do
  inclusion="$(sed -n '2,/^---$/s/^inclusion:[[:space:]]*//p' "$steering" | head -n 1)"
  case "$inclusion" in
    always)
      ;;
    auto)
      rg -q '^name:' "$steering" || fail "$steering auto inclusion has no name"
      rg -q '^description:' "$steering" || fail "$steering auto inclusion has no description"
      ;;
    fileMatch)
      rg -q '^fileMatchPattern:' "$steering" || fail "$steering has no file match pattern"
      ;;
    *)
      fail "$steering is not conditionally included"
      ;;
  esac
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

if rg -n '/Users/|\.agents/memories|memory-sync|\.agents/\.obsidian' AGENTS.md .agents/skills .kiro; then
  fail "new agent configuration contains machine-specific or retired infrastructure references"
fi

agents_lines="$(wc -l < AGENTS.md | tr -d ' ')"
[[ "$agents_lines" -le 80 ]] || fail "AGENTS.md exceeds the 80-line context budget"

echo "agent validation: PASS ($skill_count skills, $agents_lines AGENTS.md lines)"
