# TestFly agent instructions

## Scope

TestFly is a Java 21/Maven SDK for multi-domain test automation. Selenium is one adapter, not the architecture. Preserve framework-neutral contracts, consumer compatibility, parallel safety, and extension points.

## Work from evidence

- Inspect the relevant source and tests before editing. Do not substitute summaries for implementation evidence or scan the whole repository by default.
- Treat `@TestFlyApi` declarations and current source as the public-contract authority. Use `CONTRIBUTING.md` and `docs/` as supporting context; report conflicts instead of copying stale claims.
- Keep loaded context task-specific. Do not create or update memories, scratchpads, session logs, wikis, or task journals.

## Routing: skills and subagents

Skills live in `.agents/skills/` (linked as `.claude/skills/`) and load on demand. Subagents for team-lead delegation live in `.claude/agents/`. Match the deliverable and the domain together; the table is the rule.

| Task or deliverable | Agent | Skill |
|---|---|---|
| Gherkin feature, steps or runner (web, API or mobile) | `cucumber-test-writer` | `testfly-web-write`, `testfly-api-write`, `testfly-mobile-write` |
| TestNG or JUnit 5 class for a web UI flow | `testng-test-writer`, `junit5-test-writer` | `testfly-web-write`, `testfly-test-authoring` |
| TestNG or JUnit 5 class for a REST API | `api-test-writer` (name the engine) | `testfly-api-write` |
| TestNG or JUnit 5 class for native iOS or Android | `ios-test-writer`, `android-test-writer` | `testfly-mobile-write` |
| Find web locators from a URL | main session | `testfly-locator-finder` |
| Find native selectors on a device | `mobile-locator-finder` | `testfly-mobile-locator-finder` |
| k6 load script | `k6-load-test-writer` | `testfly-k6-write` |
| Failing test first (red, green, refactor) | `tdd-test-writer`; green step: `java-developer` or `mobile-developer` | `testfly-test-authoring` |
| SDK coverage, fakes, a bug as one failing test | `test-author` | `testfly-test-authoring` |
| SDK code: public API, config, lifecycle, SPI | `java-developer` | `testfly-change` |
| `io.testfly.mobile` and the `@mobile` bridge branches | `mobile-developer` | `testfly-mobile` |
| Diff review before commit (read-only) | `code-reviewer` | `testfly-review` |
| Failing, flaky or hanging test or build | `ci-triage` | `testfly-triage` |
| External library, tool or protocol research | `researcher` | `testfly-research` |
| Docs, README, release text (English and Turkish) | `docs-writer` | `testfly-docs` |
| Gates and release readiness | main session | `testfly-verify` |
| Impact analysis, only with a current graph | main session | `graphify` |

- One file or one command (a broken locator, `scripts/agent/verify.sh`, a graph query): do it in the main session; a subagent costs a fresh context.
- Two rows match: name the engine or platform in the delegation prompt instead of guessing.
- Ownership: `java-developer` owns `src/main` except `io.testfly.mobile` and the `@mobile` branches of the TestNG, JUnit 5 and Cucumber bridges, which `mobile-developer` owns. Shared bridge lifecycle code stays with `java-developer`; run `Mobile*Test` and `verify.sh api` for either.
- A subagent sees none of this conversation: pass the task, file paths, acceptance criteria and known facts. It reports back; the main session commits and pushes after the user approves.
- Subagents never commit, push, rewrite history or edit agent infrastructure; `scripts/agent/guard-subagent.py` enforces it. Test writers and `ci-triage` never edit `src/main`: production changes go through the main session to `java-developer` or `mobile-developer`.
- Gates run in the main session or in the agent that wrote the change. `code-reviewer` only reads: give it the diff scope and the gate output.

## Invariants

- Preserve public and binary compatibility within a major version. New methods on stable interfaces require a compatible default implementation.
- Keep WebDriver/session state thread-confined. Do not add shared mutable driver or execution state.
- Prefer JDK facilities and existing dependencies. Do not add speculative abstractions or dependencies.
- Keep optional integrations optional for consumers.
- Preserve English/Turkish documentation parity where a localized counterpart exists.
- Do not change production behavior while modifying agent infrastructure.

## Verification

- Agent infrastructure: `scripts/agent/verify.sh agent` (validator, guard and routing-fixture tests)
- Java changes: `scripts/agent/verify.sh code`
- Public API changes: `scripts/agent/verify.sh api [baseline-ref]`
- SPI changes: `scripts/agent/verify.sh spi`
- Consumer compatibility: `scripts/agent/verify.sh consumer [checkout]`
- Documentation: `scripts/agent/verify.sh docs`
- Release readiness: `scripts/agent/verify.sh release [baseline-ref] [consumer-checkout]`
- Full local gate: `scripts/agent/verify.sh full`

Run the smallest relevant gate first. State which checks ran, failed, or were unavailable. Never claim consumer, Kiro, network, or release verification without executing it.

## Safety

- Never commit or push unless explicitly requested. Never commit or push directly to `main`.
- Preserve unrelated and uncommitted work. Avoid destructive Git operations.
- Never commit secrets; use environment-variable placeholders in configuration examples.

## Graphify

- Use it only when `graphify-out/graph.json` exists and is current; otherwise inspect source. Query with `graphify query "<question>" --budget 1000` or `graphify explain "<symbol>"`.
- Inferred edges are hypotheses: read the Java source before editing, and never replace compilation, tests or API checks with graph results.
- Do not rebuild the graph, scan the whole repository, or read `GRAPH_REPORT.md` per task. The `graphify` skill is vendored; do not edit it.
