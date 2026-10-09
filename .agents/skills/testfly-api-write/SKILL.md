---
name: testfly-api-write
description: Author consumer-facing REST API automation with TestFly, including API test classes, reusable clients, authentication, response and schema validation, positive and negative integration scenarios, and migrations to supported TestFly APIs. Do not use for unrelated Java work, Web UI or load tests, SDK redesign, or public API compatibility review.
---

# TestFly API test authoring

Write Java 21 tests against TestFly's public `io.testfly.client`, API-test base-class, and support APIs. Do not modify production SDK code for an authoring request or expose `io.testfly.internal.*` to consumers.

## Work from the installed contract

1. Inspect the relevant public signatures and nearby tests when working in the TestFly repository or when the dependency version differs from these references.
2. Choose the lifecycle boundary: `BaseApiTest` for TestNG, `BaseJUnit5ApiTest` for JUnit 5, or `BaseCucumberSteps` plus TestFly glue and an `@api`/`@noBrowser` scenario tag for Cucumber.
3. Put endpoint construction and serialization in a small service/helper; keep scenario intent and assertions in tests. Reuse `ApiRequestSpec` and `ApiResponseSpec` only when defaults genuinely repeat.
4. Prefer `ApiClient` over another HTTP client. Use explicit TestFly response assertions and verify negative error contracts, not only status codes.
5. Compile the new test or example first. Run deterministic local/mock-backed tests before any opt-in external integration test.

## Non-negotiable authoring rules

- Use only verified methods. In particular, there is no `withAuth(String)`, HEAD/OPTIONS fluent method, or full JSONPath engine.
- Use `api.baseUrl`, `ApiClient.to(...)`, or `ApiClient.toService(...)`; never hardcode environment-specific hosts or credentials.
- Read secrets from environment-backed configuration. Never put tokens, passwords, cookies, or client secrets in source or logs.
- Use per-request auth or `@UseAuth` for named TestNG/JUnit 5 auth. Avoid process-global specs/interceptors in parallel tests; clean up any global state explicitly.
- Keep API-only tests browser-free. Do not add fixed sleeps; use `pollUntil` for eventually consistent state and enable retries only for defined transient failures.
- Keep each test isolated. A batch supplier must create a fresh `ApiClient`; mutable interceptor/mock callbacks used concurrently must be thread-safe.
- Treat schema, OpenAPI, WireMock, JUnit 5, and Cucumber support as optional dependencies. Do not add one unless the scenario uses it.
- Let TestFly lifecycle and `StepLogger` capture request outcomes. Keep body/curl logging opt-in and preserve masked sensitive headers.

## Capability index

Read only what the task needs:

- Core requests, responses, assertions, specs, configuration, and the supported/partial/unsupported inventory: [references/api-core.md](references/api-core.md)
- Authentication factories, named auth, secret handling, and lifecycle limits: [references/authentication.md](references/authentication.md)
- Interceptors, retry/polling, async/batch, mocking, schema/OpenAPI, reporting, and framework integration: [references/advanced-features.md](references/advanced-features.md)
- Complete compile-checked examples for TestNG, JUnit 5, reusable services, auth, negative tests, schema validation, mocks, and polling: [references/examples.md](references/examples.md)

## Verification and handoff

Run the authored test alone, then use `testfly-verify` for the smallest applicable repository gate. Keep external-backend tests behind the existing profile/tag convention and report compilation separately from runtime execution. State optional dependencies and any feature that remains deliberately unsupported.
