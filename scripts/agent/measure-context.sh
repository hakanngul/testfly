#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"
baseline="${1:-ace3c6a}"

git rev-parse --verify "${baseline}^{commit}" >/dev/null

measure_stream() {
  awk '{ lines += 1; words += NF; bytes += length($0) + 1 } END { printf "%d\t%d\t%d", lines, words, bytes }'
}

old_agents="$(git show "$baseline:AGENTS.md" | measure_stream)"
old_scratchpad="$(git show "$baseline:.agents/memories/scratchpad.md" | measure_stream)"
new_agents="$(measure_stream < AGENTS.md)"

old_skill_count="$(git ls-tree -r --name-only "$baseline" .agents/skills | rg -c '/SKILL.md$' || true)"
new_skill_count="$(find .agents/skills -mindepth 2 -maxdepth 2 -name SKILL.md | wc -l | tr -d ' ')"

printf 'surface\tlines\twords\tbytes\n'
printf 'old AGENTS.md\t%s\n' "$old_agents"
printf 'old mandatory scratchpad\t%s\n' "$old_scratchpad"
printf 'new AGENTS.md\t%s\n' "$new_agents"
printf '\nold skill count\t%s\nnew skill count\t%s\n' "$old_skill_count" "$new_skill_count"
printf 'note\tWords and bytes are reproducible proxies, not measured model tokens.\n'
