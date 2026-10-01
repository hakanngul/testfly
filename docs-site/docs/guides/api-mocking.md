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

Rules are captured at call start, run after interceptor changes and before legacy request hooks. Synthetic results participate in response hooks and YAML status retry. Predicate/factory errors are not retried. Test cleanup removes registrations. Predicates/factories must be thread-safe for parallel calls. Existing WireMock support is independent.

Runnable example: `mvn test -Dtest=ApiMockExamplesTest`.
