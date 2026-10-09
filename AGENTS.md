# TestFly agent instructions

## Scope

TestFly is a Java 21/Maven SDK for multi-domain test automation. Selenium is one adapter, not the architecture. Preserve framework-neutral contracts, consumer compatibility, parallel safety, and extension points.

## Work from evidence

- Inspect the relevant source and tests before editing. Do not substitute summaries for implementation evidence or scan the whole repository by default.
- Treat `@TestFlyApi` declarations and current source as the public-contract authority. Use `CONTRIBUTING.md` and `docs/` as supporting context; report conflicts instead of copying stale claims.
- Keep loaded context task-specific. Do not create or update memories, scratchpads, session logs, wikis, or task journals.

## Shared workflows

- Activate `testfly-change` for SDK architecture reviews and Java runtime, public API, configuration, lifecycle, integration, or SPI changes.
- Activate `testfly-api-write` for consumer-facing API automation, reusable API clients, authentication, validation, negative scenarios, and migrations using TestFly APIs.
- Activate `testfly-test-authoring` to add or revise framework coverage and executable examples outside consumer-facing API authoring.
- Activate `testfly-triage` for failing, flaky, hanging, or environment-dependent tests and builds.
- Activate `testfly-docs` for documentation audits and changes in `README.md`, `docs/`, `docs-site/`, or release text.
- Activate `testfly-verify` for implementation handoff, API/SPI/consumer validation, or release-readiness checks.
- Skills live in `.agents/skills/` and load on demand. Kiro adapters live in `.kiro/`.

## Invariants

- Preserve public and binary compatibility within a major version. New methods on stable interfaces require a compatible default implementation.
- Keep WebDriver/session state thread-confined. Do not add shared mutable driver or execution state.
- Prefer JDK facilities and existing dependencies. Do not add speculative abstractions or dependencies.
- Keep optional integrations optional for consumers.
- Preserve English/Turkish documentation parity where a localized counterpart exists.
- Do not change production behavior while modifying agent infrastructure.

## Verification

- Agent infrastructure: `scripts/agent/validate.sh`
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

## Graphify — Code Intelligence

Use Graphify for efficient codebase discovery and change-impact analysis.

- Before cross-module changes, refactoring, or unfamiliar feature development, prefer targeted Graphify queries over broad repository scans.
- Use `graphify query "<question>" --budget 1000` to locate relevant components and relationships.
- Use `graphify explain "<symbol>"` to explore an unfamiliar class or method.
- Use `graphify affected "<symbol>" --depth 2` to identify potentially impacted components before modifying shared infrastructure.
- Skip Graphify for trivial changes or when the relevant files are already known.
- Use Graphify only when `graphify-out/graph.json` exists and is sufficiently current.
- If the graph is missing, stale, or incomplete, fall back to targeted source-code inspection.
- Treat inferred graph relationships as hypotheses, not verified facts.
- Always inspect actual Java source signatures before modifying code.
- After changes, validate affected functionality using the existing TestFly verification workflow.
- Do not rebuild the graph, scan the full repository, or read `GRAPH_REPORT.md` on every task.
- Never treat Graphify as a replacement for compilation, tests, or API compatibility checks.
