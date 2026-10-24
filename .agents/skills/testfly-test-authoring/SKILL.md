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

## Preconditions (setup the test needs, not what it tests)

| Need | Use | Scope |
|---|---|---|
| Logged-in browser state | `@PreCondition("login")` on the test + `@ConditionProvider("login")` method in a `BaseConditions` subclass, registered in `META-INF/services/io.testfly.precondition.BaseConditions` or with `PreConditionRegistry.register(...)` | Browser only: caches cookies and localStorage per thread, restores them for the next test, reruns the provider on retry. TestNG reads it from the method; JUnit 5 from the method, then the class. Cucumber does not read it. Example: `examples/conditions/SauceDemoConditions.java` + `examples/testng/SauceDemoLoginTest.java`. |
| Backend must be up | `@DependsOnApi("https://…/health")` (repeatable, class or method) | Any test (web, API, mobile): skipped, not failed, when the URL is not 2xx; checked once per URL per suite. |
| Named API auth | `@UseAuth("admin")` | TestNG and JUnit 5 API tests; Cucumber applies `ApiAuth` in a step. See `testfly-api-write`. |
| Data the test creates | Arrange step or `@BeforeMethod`/`@BeforeEach` through `ApiClient`, cleaned up in `@AfterMethod`/`@AfterEach` | Per test; never share mutable fixtures across parallel tests. |
| Cucumber shared steps | `Background:` in the feature | Per scenario. |
| Native mobile state (e.g. logged in) | `@PreCondition("loggedIn")` + `@ConditionProvider` in a `MobileConditions` subclass (`META-INF/services/io.testfly.mobile.MobileConditions` or `MobileConditions.register`), written with page objects | TestNG (method) and JUnit 5 (method, then class) mobile tests. Runs before every test, or once per device with `mobile.app.reset: none`; reruns on retry. Example: `examples/mobile/LoginConditions.java`. See `testfly-mobile-write`. |

Prefer API calls over UI clicks to create preconditions; a precondition provider must not assert the behaviour under test.

## Verify and document

Run the new test alone, then the narrowest relevant `testfly-verify` mode. If it is a public example, keep Java 21 compatibility and update its English/Turkish explanation through `testfly-docs`. Record any environment or profile required to execute it.
