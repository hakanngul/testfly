# API core and capability inventory

Source authority: current public signatures in `io.testfly.client`, `io.testfly.test.BaseApiTest`, `io.testfly.test.support.ApiSupport`, `io.testfly.junit5.BaseJUnit5ApiTest`, and `io.testfly.cucumber.BaseCucumberSteps`. Internal execution and transport classes explain behavior but are not consumer APIs.

## Capability inventory

| Area | Classification | Verified behavior and boundary |
|---|---|---|
| HTTP methods | Supported | `get`, `post`, `put`, `patch`, and `delete`, either as static path factories or instance method selection. No public HEAD or OPTIONS builder. |
| URL and parameters | Supported | Configured default URL, absolute URLs, `to`, named `toService`, `path`, encoded `pathParam(s)`, and encoded `queryParam(s)`. Parameter maps hold one value per name. Unresolved path placeholders fail before transport. |
| Headers and cookies | Supported | `header(s)`, `contentType`, response header lookup/assertions, and opt-in `withCookies`. The cookie jar is thread-local and cleared by framework test cleanup. |
| Request bodies | Supported | Strings pass through; other objects use Jackson JSON serialization. Form fields and multipart text/file parts are supported. Streaming bodies and custom codecs are not exposed. |
| Authentication | Partially supported | Bearer, Basic, header/query API key, OAuth2 client credentials/password grant, HMAC, and a limited Digest helper. Read [authentication.md](authentication.md); Digest is not a full challenge-response implementation. |
| Request/response specs | Supported | Immutable `ApiRequestSpec` covers base URL, headers, query defaults, auth, and content type. `ApiResponseSpec` covers status, content type, duration, and JSON Schema. |
| JSON parsing/extraction | Partially supported | Whole-body POJO/list conversion and a small `$.field`, `$.a.b`, `$.items[0].name` path subset. No filters, wildcards, recursive descent, or general JSONPath expressions. |
| Assertions | Supported | Status, body substring/regex, headers, duration, JSON equality/predicate/numeric/string/boolean/array/existence/null, soft variants, response specs, schema, and OpenAPI response validation. |
| JSON Schema | Optional dependency | `assertSchema(classpathPath)` uses JSON Schema draft v7 and needs `com.networknt:json-schema-validator`. |
| OpenAPI | Partially supported; optional dependency | `assertOpenApi(specPath)` needs `swagger-request-validator-core` and validates the response against the method/path. It does not perform full request-contract validation. |
| Interceptors/customization | Supported with lifecycle limits | Per-request and thread-local around interceptors are available. Legacy request/response interceptors are process-global. Read [advanced-features.md](advanced-features.md). |
| Timeout, retry, polling | Partially supported | Per-request/default timeout, configuration-driven retry for selected statuses/transport errors, and `pollUntil`. Retry policy has no per-request fluent override. |
| Async and batch | Supported | `sendAsync` and ordered `ApiBatchRunner` results with an optional concurrency limit. This is functional/concurrency execution, not the load-test subsystem. |
| Mocking | Supported; dependency caveat | Client-side `ApiMockRule` needs no server. `ApiMockServer` wraps WireMock. Current `BaseApiTest` teardown references `ApiMockServer`, so TestNG consumers extending it need WireMock at runtime even when a test uses only client-side rules. |
| Errors/negative tests | Supported | Assertion and transport failures use `ApiException`, exposing method, URL, status, body, client/server classification, and timeout detection. Ordinary 4xx/5xx responses do not throw until asserted. |
| Configuration | Supported | `api.baseUrl`, `baseUrls`, timeouts, concurrency, SSL, logging, retries, masking, and named auth are loaded from `testfly.yml`/profile files with string placeholder resolution. |
| Test frameworks | Supported with optional adapters | TestNG is core, subject to the current `BaseApiTest`/WireMock runtime caveat above. JUnit 5 and Cucumber need matching optional dependencies. `@UseAuth` lifecycle resolution is implemented for TestNG and JUnit 5, not Cucumber. |
| Reporting/logging | Supported | API exchange/result steps use TestFly `StepLogger`; body, pretty JSON, failure curl, truncation, and masked headers are configurable. |

## Request construction

Prefer the clearest of these equivalent shapes:

```java
ApiResponse response = ApiClient.get("/users/{id}")
        .pathParam("id", 42)
        .queryParam("expand", "roles")
        .header("Accept", "application/json")
        .send();

ApiResponse health = ApiClient.to("https://service.example")
        .path("/health")
        .get()
        .send();
```

`ApiSupport` supplies `apiGet`, `apiPost`, `apiPut`, `apiPatch`, `apiDelete`, `apiTo`, and `apiToService`. Static methods can be called through an `apiClient()` expression in Java, but prefer the support shortcuts or `ApiClient` factories because they communicate intent without relying on static-through-instance syntax.

An object body is serialized as JSON and defaults `Content-Type` to `application/json`. A string body is sent unchanged. Form parameters select `application/x-www-form-urlencoded`; multipart fields/files select `multipart/form-data`. Do not combine body modes in one request.

## Specs and assertions

`spec(requestSpec)` applies and overwrites values already present on that client. A global request spec fills only absent values. Because the global spec is process-wide and not cleared by ordinary per-test API cleanup, prefer explicit specs in parallel suites.

Use TestFly assertions for the contract under test, then use the test framework for domain relationships that TestFly does not express:

```java
response.assertStatus(200)
        .assertHeaderPresent("Content-Type")
        .assertJsonExists("$.id")
        .assertJson("$.state", "ACTIVE");
```

For a negative response, assert the expected status and machine-readable error fields. Catch `ApiException` only when testing assertion/transport failure behavior itself.
