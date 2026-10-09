---
name: testfly-change
description: Plan and implement TestFly Java SDK changes while preserving public API, lifecycle, thread-safety, optional integrations, and SPI contracts. Use for runtime code, configuration, test-engine adapters, public APIs, or extensions; not for documentation-only edits.
---

# TestFly change workflow

## Establish the boundary

1. Inspect the requested source, its callers, and focused tests. Search by symbol or package before expanding scope.
2. Check `@TestFlyApi` on changed types, methods, and constructors. The annotation and compiled signatures are authoritative for stability.
3. For lifecycle or concurrency changes, trace creation, ownership, cleanup, retry, and reporting paths. Preserve thread confinement and cleanup on failure.
4. For configuration changes, preserve existing keys/defaults and environment/system-property overrides.
5. For optional integrations, confirm the dependency remains optional and core paths work without it.

## Architecture map

Use this only to locate evidence; verify the current implementation before relying on it.

- Core consumer surfaces: `io.testfly.test`, `io.testfly.api`, `io.testfly.client`
- Execution and state: `lifecycle`, `execution`, `context`, `session`, `internal`
- Web automation: `driver`, `locator`, `wait`, `browser`, `network`, `shadow`, `visual`, `accessibility`
- API testing and mocking: `client`, `api`, `api.mock`, `internal.api`
- Mobile support currently means browser/device emulation in `browser`; do not imply native mobile automation without source evidence.
- Load/performance: `loadtest`, `performance`
- Assertions: `assertion`
- Reporting/observability: `reporting`, `metrics`, `steps`, `tracing`, `recording`
- Configuration/environments: `config`, root `testfly.yml`
- Extensibility: `extension`, `hooks`, `driver.NamedDriverProvider`, `reporting.ReportAdapter`, and `META-INF/services`
- Engines: TestNG listeners plus `junit5` and `cucumber` bridges
- Consumer compatibility: public base classes, fluent APIs, config keys, SPI implementations, and optional dependency boundaries

## Compatibility decisions

- Do not remove, rename, narrow visibility, or change descriptors of stable APIs within the same major version.
- Add methods to stable interfaces only with a compatible `default` body.
- Deprecate before removal and retain the old path for the documented compatibility window.
- Add focused tests for observable behavior and compatibility. For SPI work, test both discovery and programmatic registration where applicable.
- Avoid Selenium-specific types in framework-neutral seams unless that seam is explicitly the web adapter.

## Verification

Run the narrowest relevant mode from `scripts/agent/verify.sh`: `code`, `api`, or `spi`. Run `consumer` when a consumer checkout is available. Report skipped external checks explicitly.
