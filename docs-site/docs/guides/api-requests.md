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
