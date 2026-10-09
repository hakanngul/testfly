---
name: testfly-test-authoring
description: Create or revise TestFly framework tests and executable examples across TestNG, JUnit 5, Cucumber, browser, API/mock, and load/performance domains. Use for SDK coverage or confirmed-bug reproduction; use testfly-api-write for consumer-facing API automation, and do not use for SDK implementation or failure diagnosis alone.
---

# TestFly test authoring

## Choose the test boundary

1. State the behavior and failure signal before choosing a framework or fixture.
2. Prefer the lowest-cost boundary that proves it: unit → registry/engine integration → domain integration → external/consumer.
3. Match the affected adapter: TestNG listener, JUnit 5 bridge, Cucumber bridge, browser lifecycle, API/mock, or load/performance. Do not express every scenario as a Selenium test.
4. Reuse nearby fixtures and patterns; use `src/test/java/io/testfly/examples/` only for intentionally consumer-facing examples.

## Author deterministic tests

- Use Arrange/Act/Assert and assert observable behavior, not internal call order unless order is the contract.
- Give each test independent state and cleanup. Never share mutable driver, server, port, output directory, or execution context across parallel tests.
- Use TestFly waits and lifecycle hooks for browser state; do not add fixed sleeps or retry a deterministic assertion.
- For API tests, use local mock servers/fixtures where possible and assert status, schema/body, headers, and diagnostics without exposing secrets.
- For SPI tests, cover ServiceLoader discovery, programmatic registration, duplicate/invalid providers, lifecycle, and absence of optional dependencies as applicable.
- For configuration tests, cover precedence, default, missing/blank, malformed, substitution, and compatibility behavior.
- Separate external, browser, real-backend, load, and intentionally failing demonstrations behind existing profiles/tags so the default suite remains deterministic.

## Verify and document

Run the new test alone, then the narrowest relevant `testfly-verify` mode. If it is a public example, keep Java 21 compatibility and update its English/Turkish explanation through `testfly-docs`. Record any environment or profile required to execute it.
