---
title: "Client-side Mocking"
---

# Client-side Mocking

`ApiMockRule` matches prepared requests and returns synthetic responses without network access. Request rules precede test rules; the first match wins. Unmatched requests use normal HTTP transport.

```java
ApiMockRule rule = ApiMockRule.builder()
    .match(r -> r.method().equals("POST") && r.uri().getPath().equals("/payment"))
    .respond(r -> ApiResponse.builder().request(r).status(200)
        .header("Content-Type", "application/json")
        .body("{\"status\":\"mocked\"}").build())
    .build();
ApiClient.post("http://localhost:1/payment").mockRule(rule).send().assertStatus(200);
ApiClient.addMockRule(rule);
ApiClient.post("http://localhost:1/payment").sendAsync().join().assertStatus(200);
ApiClient.clearMockRules();
```

Rules are captured at call start, run after interceptor changes and before legacy request hooks. Synthetic results participate in response hooks and YAML status retry. Predicate/factory errors are not retried. Test cleanup removes registrations. Predicates/factories must be thread-safe for parallel calls. WireMock support (below) is independent.

Runnable example: `mvn test -Dtest=ApiMockExamplesTest`.

## WireMock server (`ApiMockServer`)

Classes implementing `MockSupport` (including `BaseApiTest`) get `mockServer()` and `stubGet(...)`. The optional `org.wiremock:wiremock-standalone` dependency must be on the test classpath.

```java
mockServer().stubJson("POST", "/orders", 201, "{\"id\":7}");
mockServer().stubFor(WireMock.get("/health").willReturn(WireMock.ok("UP")));
ApiClient.to(mockServer().getBaseUrl()).path("/orders").post().body("{}").send().assertStatus(201);
```

The server is a JVM singleton. `MockSupport.resetMocks()` runs after every TestNG test method and calls `reset()`, which removes **all** stubs. For parallel tests, register stubs through `stubFor`, `stubJson` or `stubGet` and override `resetMocks()` so it removes only the stubs registered by the current thread:

```java
@Override
@AfterMethod(alwaysRun = true)
public void resetMocks() {
    mockServer().resetOwnStubs();
}
```

The static `WireMock.stubFor(...)` DSL targets the same server but is not parallel-safe.
