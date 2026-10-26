#!/usr/bin/env bash
# Validates skills, subagents, routing docs and the subagent guard hook.
# The checks live in validate_agents.py (real YAML parsing, discovered counts).
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

command -v python3 >/dev/null || { echo "agent validation: python3 is required" >&2; exit 2; }
exec python3 -I scripts/agent/validate_agents.py --root "$repo_root"
