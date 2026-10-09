# Advanced API features

## Interceptors

Prefer `client.interceptor(ApiInterceptor)` for request-local middleware. An around interceptor receives immutable `ApiRequest`, may rebuild it with `newBuilder()`, may call `proceed` sequentially more than once, and may return a synthetic `ApiResponse`. The chain is valid only on its executing thread and during `intercept`.

`ApiClient.addInterceptor` and `addMockRule` are thread-local and cleared by TestFly test cleanup. `addRequestInterceptor` and `addResponseInterceptor` are legacy process-global hooks; `clearInterceptors` must be called explicitly, and concurrent registration/removal can affect unrelated tests. The process-global `setGlobalSpec` likewise requires explicit `clearGlobalSpec`.

## Timeouts, retries, and polling

- `requestTimeout(Duration)` and `timeout(int seconds)` override `api.timeoutSeconds`. Connect timeout comes from `api.connectTimeoutSeconds`.
- Configuration retry is disabled by default. When enabled, it retries only configured response statuses and optionally transport failures, using increasing `backoffMs * attempt` delays. Assertion, interceptor, mock callback, and response-hook failures are not transport retries.
- Use retries only for known transient behavior. Do not retry deterministic 4xx validation or assertion failures.
- `pollUntil(predicate, maxTimeout[, interval])` resends the configured request until the predicate succeeds. It enforces a minimum 50 ms interval and throws `ApiException` on timeout. Use it instead of sleeps for eventual consistency.

## Async and batch execution

`sendAsync()` returns `CompletableFuture<ApiResponse>` and snapshots caller-thread auth, cookies, thread-local interceptors, mocks, logging, retry, and transport settings. `ApiBatchRunner.fire(count).concurrently(threads).request(supplier).execute()` preserves result order. The supplier must return a new client on every invocation. `api.maxConcurrentRequests` limits concurrent real HTTP sends; client-side mocks bypass transport permits.

Do not use these APIs as a replacement for TestFly's separate load/performance facilities.

## Mocking

Use client-side `ApiMockRule` for the smallest deterministic test:

```java
ApiMockRule rule = ApiMockRule.builder()
        .match(request -> request.uri().getPath().equals("/health"))
        .respond(request -> ApiResponse.builder()
                .request(request).status(200)
                .header("Content-Type", "application/json")
                .body("{\"status\":\"UP\"}").build())
        .build();
```

Attach it with `.mockRule(rule)` for one request, or thread-locally with `addMockRule`. Mock predicates/factories shared by async or batch execution must be thread-safe.

Use `ApiMockServer` only when a real socket boundary matters. It requires `org.wiremock:wiremock-standalone`; prefer dynamic ports, stop/reset in teardown, and avoid the singleton in parallel tests unless access is serialized. `stubGet` is the only TestFly convenience stub; advanced stubbing uses the optional raw WireMock API.

Current limitation: `BaseApiTest` implements TestNG `MockSupport`, and its unconditional teardown calls `ApiMockServer`. A consumer running any `BaseApiTest` therefore needs WireMock on the runtime classpath even if the scenario uses only client-side rules. Treat this as an SDK optional-boundary gap; do not hide it with invented APIs or production changes.

## Schema and OpenAPI validation

- `assertSchema("schemas/user.json")` loads a classpath resource, uses JSON Schema draft v7, and requires `com.networknt:json-schema-validator:1.4.3` in the consumer.
- `assertOpenApi(specPath)` requires `com.atlassian.oai:swagger-request-validator-core:2.39.0`. It validates response status/content type/body for the request method and URI path. Do not describe it as full request/response contract validation.
- Keep schemas under test resources, version them with the API contract, and pair schema checks with explicit business assertions.

## Framework lifecycle

- TestNG: extend `BaseApiTest`. Its listeners provide reporting, context, retry integration, auth resolution, and browser-free execution.
- JUnit 5: extend `BaseJUnit5ApiTest`; consumers add matching JUnit Jupiter/platform dependencies. `TestFlyExtension` supplies lifecycle and browser-free execution.
- Cucumber: extend `BaseCucumberSteps`, include `io.testfly.cucumber` in glue, add matching Cucumber dependencies, and tag pure API scenarios `@api` or `@noBrowser` so hooks do not create WebDriver. Apply auth in step/helper code because `@UseAuth` is not resolved by Cucumber hooks.

All three lifecycle paths clean thread-local API auth, cookies, chain interceptors, mocks, and execution scope after a test/scenario. They do not make process-global legacy interceptors or a global request spec test-local.

## Reporting and safe logging

Every exchange and final result is recorded through `StepLogger`. Configure `api.logBody`, `prettyLog`, `logCurl`, `truncationLimit`, and `maskedHeaders`. Body logging can still expose secrets that are not headers; leave it off unless needed and use non-sensitive fixtures. Do not print response bodies or credentials with `System.out`.
