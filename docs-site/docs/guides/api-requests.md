---
title: "Requests & Configuration"
---

# Requests & Configuration

### Supported methods

```java
ApiClient.get("/api/users")
ApiClient.post("/api/users")
ApiClient.put("/api/users/1")
ApiClient.patch("/api/users/1")
ApiClient.delete("/api/users/1")
ApiClient.head("/api/users/1")      // headers only, empty body
ApiClient.options("/api/users")     // e.g. read the Allow header
```

### Base URL

By default, `ApiClient` uses `api.baseUrl` from `testfly.yml`. Falls back to `execution.baseUrl` if not set.

```yaml
api:
  baseUrl: https://api.example.com
  timeoutSeconds: 30
  logBody: false   # set true to log request/response body in step timeline
```

Override per-request with `ApiClient.to(url)`:

```java
ApiClient.to("https://other-service.com").path("/health").get().send();
```

### Request headers and body

```java
ApiClient.post("/api/users")
        .header("X-Request-ID", "abc123")
        .contentType("application/json")
        .body(Map.of("name", "Alice", "email", "alice@example.com"))
        .send();
```

### Query parameters

`queryParam` sets a value and replaces earlier values of the same name. Use `addQueryParam` / `addQueryParams` for repeated keys:

```java
ApiClient.get("/api/products")
        .addQueryParam("id", 1).addQueryParam("id", 2)   // ?id=1&id=2
        .addQueryParams("tag", List.of("new", "sale"))   // &tag=new&tag=sale
        .queryParam("page", 1)
        .send();
```

### Binary bodies

A `byte[]` body is sent unchanged with `Content-Type: application/octet-stream` unless you set another type. Strings are sent as-is and other objects are serialised to JSON.

```java
ApiClient.put("/api/avatars/42")
        .contentType("image/png")
        .body(Files.readAllBytes(Path.of("avatar.png")))
        .send();
```

### Per-request retry

`api.retry` in `testfly.yml` is the default. A request can override it, and an explicit override enables retry even when `api.retry.enabled` is `false`:

```java
ApiClient.get("/api/flaky").retry(3).send();                                   // 3 attempts in total
ApiClient.get("/api/rate-limited").retry(5, Duration.ofMillis(200)).retryOn(429, 503).send();
ApiClient.post("/api/payments").noRetry().send();                             // never retry this call
```

Backoff is linear (`backoff × attempt`). Unset values come from `api.retry` (defaults: statuses 502/503/504, 500 ms, transport errors retried).

### Proxy

```yaml
api:
  proxy:
    host: proxy.corp.local
    port: 3128
    nonProxyHosts: [localhost, "127.*", "*.internal"]
```

```java
ApiClient.get("https://partner.example.com/ping").proxy("127.0.0.1", 8888).send(); // e.g. a debugging proxy
ApiClient.get("http://localhost:8080/health").noProxy().send();                    // bypass api.proxy
```

`nonProxyHosts` entries match host names case-insensitively and accept `*` wildcards. Proxy authentication is not supported.

### Reusable request specs

```java
ApiRequestSpec tenant = ApiRequestSpec.builder()
        .header("X-Tenant", "acme")
        .queryParam("locale", "tr")
        .build();

ApiClient.setTestSpec(tenant);    // current test thread only, cleared after each test
ApiClient.setGlobalSpec(tenant);  // JVM-global: every thread, never cleared automatically
apiClient().get("/api/me").spec(tenant).send(); // explicit: overwrites the request's values
```

Precedence: values set on the request win, then the test spec, then the global spec. `ApiClient.addRequestInterceptor` / `addResponseInterceptor` hooks are JVM-global as well; for test-scoped middleware use `ApiClient.addInterceptor` (see [Interceptor Chain](api-interceptors.md)).

### Request body logging

With `api.logBody: true` the step log contains the response body and the request body. JSON, text, XML and form bodies are logged (truncated to `api.truncationLimit`). Binary and multipart bodies appear only as `<binary, N bytes, content-type>`. Headers listed in `api.maskedHeaders` are masked; body fields are not, so keep `logBody` off for payloads with secrets.
