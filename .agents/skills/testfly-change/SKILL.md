---
name: testfly-change
description: Design, review, or implement TestFly Java SDK architecture, public APIs, configuration, lifecycle, engine adapters, and extension points. Use for API compatibility decisions, architecture reviews, extension development, or configuration evolution; not for docs-only work, test authoring, or failure triage.
---

# TestFly SDK change workflow

## Select the path

- **Review/design:** inspect only, map the affected contracts and dependencies, and report findings with file evidence. Do not edit unless asked.
- **Public API:** find `@TestFlyApi` declarations and compiled consumers before changing a signature or behavior.
- **Extension:** inspect the SPI, registry, service descriptor, lifecycle, and existing implementations together.
- **Configuration:** trace the key from input through precedence, parsing, defaults, validation, runtime use, and documentation.

Start with the requested symbol, its callers, focused tests, and adjacent package. Expand only when a boundary crosses into another subsystem.

## Architecture boundaries

Use this map to locate evidence; source remains authoritative.

- Consumer surfaces: `io.testfly.test`, `io.testfly.api`, `io.testfly.client`
- Execution/state: `lifecycle`, `execution`, `context`, `session`, `internal`
- Web automation: `driver`, `locator`, `wait`, `browser`, `network`, `shadow`, `visual`, `accessibility`
- API/mock: `client`, `api`, `api.mock`, `internal.api`
- Load/performance: `loadtest`, `performance`; assertions: `assertion`
- Reporting/observability: `reporting`, `metrics`, `steps`, `tracing`, `recording`
- Configuration: `config`, `testfly.yml`, environment and system-property inputs
- Extension points: `extension`, `hooks`, `driver.NamedDriverProvider`, `reporting.ReportAdapter`, `META-INF/services`
- Engines: TestNG listeners, `junit5`, and `cucumber` bridges

Selenium is an adapter, not the framework boundary. Mobile support means browser/device emulation unless current source proves otherwise.

## Preserve contracts

- Do not remove, rename, narrow visibility, or change descriptors of stable APIs within the same major version.
- Add methods to stable interfaces only with a behaviorally compatible `default` implementation.
- Deprecate before removal and retain the old path for the documented compatibility window.
- Treat public base classes, fluent chains, constructor behavior, configuration keys/defaults, SPI discovery, and optional dependency boundaries as consumer contracts.
- For lifecycle/concurrency changes, trace creation, ownership, cleanup, retry, and reporting on success and failure. Keep driver and execution state thread-confined.
- Keep optional integrations optional; core loading and execution must work when they are absent.

## Extension development

1. Choose the narrowest existing SPI before adding one. Keep framework-neutral seams free of Selenium types unless explicitly web-specific.
2. Define discovery and programmatic registration behavior. Update `META-INF/services` only for built-in providers.
3. Specify duplicate ordering, failure isolation, thread-safety, `onLoad`/`onUnload`, and minimum framework-version behavior where applicable.
4. Test both ServiceLoader discovery and programmatic registration, plus absent/invalid provider behavior.

## Configuration evolution

1. Preserve precedence across explicit/system/environment/file/default sources and `${VAR:-default}` substitution.
2. Centralize the default; do not duplicate it in loaders or consumers.
3. Decide missing, blank, malformed, and unknown-key behavior explicitly.
4. Test precedence and parsing in focused configuration tests, then update English/Turkish docs and sample `testfly.yml` through `testfly-docs`.

## Handoff

Use `testfly-verify`: `api` for public contracts, `spi` for extensions, `code` for internal behavior, and `consumer` when downstream compatibility is in scope. In review-only work, report the recommended gates without running mutation-oriented checks.
