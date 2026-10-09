# ADR 0001: Minimal cross-tool agent infrastructure

- Status: Accepted
- Date: 2026-10-09

## Context

The prior agent system duplicated product knowledge across a 395-line `AGENTS.md`, an Obsidian wiki, memories, task logs, rules, skills, and eight personas. Auditing commit `ace3c6a` found stale claims and mandatory context work unrelated to the active task. The clean branch removed that system before this replacement.

## Decision

- Keep universal invariants and workflow routing in root `AGENTS.md`.
- Keep three task-oriented workflows in `.agents/skills/`; their bodies load only on activation.
- Let Kiro apply file-matched adapters from `.kiro/steering/`. The adapters reference shared skill files instead of copying them.
- Provide one optional Kiro agent profile that registers `AGENTS.md` and the shared skills.
- Keep deterministic checks in `scripts/agent/` and reuse Maven, current tests, and the Docusaurus build.
- Treat current source, `@TestFlyApi`, tests, and build configuration as evidence. Architectural summaries are navigation aids, not substitutes for inspection.
- Do not maintain agent memory, scratchpads, session logs, wikis, orchestrator personas, or duplicated tool-specific skills.

## Consequences

Codex discovers `.agents/skills/` natively. Default Kiro sessions discover `AGENTS.md` and conditional steering; selecting the `testfly` Kiro profile also registers the shared skills through `skill://`. Actual IDE activation still requires a local Kiro smoke test. Historical infrastructure remains recoverable from Git commit `ace3c6a` and the migration archive recorded in the implementation report.
