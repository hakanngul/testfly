# TestFly agent instructions

## Scope

TestFly is a Java 21/Maven SDK for multi-domain test automation. Selenium is one adapter, not the architecture. Preserve framework-neutral contracts, consumer compatibility, parallel safety, and extension points.

## Work from evidence

- Inspect the relevant source and tests before editing. Do not substitute summaries for implementation evidence or scan the whole repository by default.
- Treat `@TestFlyApi` declarations and current source as the public-contract authority. Use `CONTRIBUTING.md` and `docs/` as supporting context; report conflicts instead of copying stale claims.
- Keep loaded context task-specific. Do not create or update memories, scratchpads, session logs, wikis, or task journals.

## Shared workflows

- Activate `testfly-change` for Java runtime, public API, configuration, lifecycle, integration, or SPI changes.
- Activate `testfly-verify` when code, build behavior, public contracts, SPI, or consumer compatibility may change.
- Activate `testfly-docs` for `README.md`, `docs/`, or `docs-site/` changes.
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
- Documentation: `scripts/agent/verify.sh docs`
- Full local gate: `scripts/agent/verify.sh full`

Run the smallest relevant gate first. State which checks ran, failed, or were unavailable. Never claim consumer, Kiro, network, or release verification without executing it.

## Safety

- Never commit or push unless explicitly requested. Never commit or push directly to `main`.
- Preserve unrelated and uncommitted work. Avoid destructive Git operations.
- Never commit secrets; use environment-variable placeholders in configuration examples.
