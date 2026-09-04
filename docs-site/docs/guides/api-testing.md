---
description: "API testing in TestFly: write pure API tests or hybrid UI plus API tests in the same suite, config, and HTML report."
sidebar_position: 13
---

# API Testing

TestFly supports **pure API tests** and **hybrid UI + API tests** out of the box — same framework, same config, same report.

---

## Pure API Tests — `BaseApiTest`

Extend `BaseApiTest` instead of `BaseTest`. No browser is launched; the full framework lifecycle still applies (reporting, `@TestData`, retry, CI gates).

```java
import io.testfly.test.BaseApiTest;
import io.testfly.client.ApiClient;
import io.testfly.client.ApiResponse;
import org.testng.annotations.Test;

public class UserApiTest extends BaseApiTest {

    @Test
    public void getUserById() {
        ApiResponse res = ApiClient.get("https://api.example.com/users/1")
                .send();

        res.assertStatus(200);
        res.assertJson("$.name", "John Doe");
    }
}
```

---

## `ApiClient` — Fluent HTTP Client

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

---

## `ApiResponse` — Assertions and Extraction

### Status assertion

```java
res.assertStatus(201);
```

### Body assertions

```java
res.assertBodyContains("success");
res.assertJson("$.user.name", "Alice");
```

### JSONPath extraction

```java
String token = res.json("$.token");
int    id     = res.json("$.user.id", Integer.class);
JsonNode node = res.jsonNode("$.user.metadata"); // direct Jackson JsonNode
```

### Advanced JSON Assertions

```java
// Numeric comparisons
res.assertJsonGreaterThan("$.score", 85.0);
res.assertJsonLessThan("$.responseTimeMs", 500.0);

// Substring matching
res.assertJsonContains("$.email", "@company.com");

// Boolean checks
res.assertJsonTrue("$.verified");
res.assertJsonFalse("$.isSuspended");

// Custom functional predicates
res.assertJson("$.roles", node -> node.isArray() && node.size() >= 2, "at least 2 user roles");
```

### Deserialize to object

```java
User user = res.asObject(User.class);
```

### Schema validation

Validate the response structure against a JSON Schema file:

```java
res.assertStatus(200).assertSchema("schemas/user.json");
```

Place schema files under `src/test/resources/schemas/`. See [Schema Validation](#schema-validation) below.

### Raw access

```java
int    status   = res.status();
String body     = res.body();
long   duration = res.durationMs();
```

### Fluent chaining

```java
ApiClient.get("/api/users/1")
        .send()
        .assertStatus(200)
        .assertJson("$.name", "Alice")
        .assertJsonTrue("$.active")
        .assertSchema("schemas/user.json");
```

---

## Asynchronous Polling — `pollUntil`

Microservices and event-driven architectures often require waiting for asynchronous jobs or eventual consistency. Instead of arbitrary `Thread.sleep()`, use `pollUntil()`:

```java
import java.time.Duration;

// Poll until condition is met or timeout (default 500ms interval)
ApiResponse res = ApiClient.get("/api/jobs/job-12345")
        .pollUntil(r -> "COMPLETED".equals(r.json("$.status")), Duration.ofSeconds(30));

res.assertJson("$.result", "SUCCESS");

// Custom polling interval
ApiResponse order = ApiClient.get("/api/orders/ord-999")
        .pollUntil(r -> r.status() == 200, Duration.ofSeconds(15), Duration.ofMillis(250));
```

During polling, `ApiClient` logs attempts to `StepLogger` (`[API Polling] Started...`, `[API Polling] Condition satisfied in 1420ms`). If the condition is not satisfied before timeout, an `ApiException` with details is thrown.


---

## Schema Validation

Validate that a response matches a JSON Schema (Draft-07):

**`src/test/resources/schemas/user.json`**
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["id", "name", "email"],
  "properties": {
    "id":    { "type": "integer" },
    "name":  { "type": "string", "minLength": 1 },
    "email": { "type": "string" }
  }
}
```

```java
ApiClient.get("/api/users/1")
        .send()
        .assertStatus(200)
        .assertSchema("schemas/user.json");
```

Requires `com.networknt:json-schema-validator` in your `pom.xml`:

```xml
<dependency>
  <groupId>com.networknt</groupId>
  <artifactId>json-schema-validator</artifactId>
  <version>1.4.3</version>
</dependency>
```

---

## Hybrid UI + API Tests

Mix API calls and browser interactions in the same test. Available in `BaseTest` via `apiClient()`:

```java
public class CheckoutTest extends BaseTest {

    @Test
    public void placeOrder() {
        // Set up order via API (fast)
        ApiResponse order = apiClient().post("/api/orders")
                .body(Map.of("productId", 42, "qty", 1))
                .send()
                .assertStatus(201);

        String orderId = order.json("$.orderId");

        // Verify in the UI
        open("/orders/" + orderId);
        assertThat(By.id("status")).hasText("Pending");
    }
}
```

---

## Step Timeline & API Tracing

Every `ApiClient` request is automatically logged in the step timeline:

```
[API] GET /api/users/1 → 200 (143ms)
[API] POST /api/orders → 201 (89ms)
[API] DELETE /api/orders/5 → 404 (12ms)   ← logged as FAIL
```

Enable body and cURL logging in `testfly.yml`:

```yaml
api:
  logBody: true
```

When `logBody: true` is enabled:
- The HTML report renders requests with an expandable **cURL snippet** ready to copy-paste directly into your terminal or Postman.
- Formatted request and response JSON payloads are highlighted in preformatted blocks inside the test's execution drawer.

