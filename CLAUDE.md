@AGENTS.md

Claude Code reads this file, not `AGENTS.md`, so the import above is the single source of the shared instructions. Add Claude-specific notes below this line; everything shared belongs in `AGENTS.md`. `scripts/agent/validate.sh` fails when this file duplicates it.
