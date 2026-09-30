---
description: "API testing in TestFly: write pure API tests or hybrid UI plus API tests in the same suite, config, and HTML report."
sidebar_position: 13
sidebar_label: Getting Started
---

# API Testing

TestFly supports **pure API tests** and **hybrid UI + API tests** out of the box — same framework, same config, same report.

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

## Topic Guides

- [Requests & Configuration](api-requests.md)
- [Responses & Assertions](api-responses.md)
- [Authentication](api-auth.md)
- [Interceptor Chain](api-interceptors.md)
- [Async Calls & Batch](api-async-batch.md)
- [Polling](api-polling.md)
- [Schema Validation](api-schema-validation.md)
- [Hybrid UI + API Tests](api-hybrid-tests.md)
- [Logging & Reporting](api-reporting.md)
- [Scenario & Suite Context](scenario-context.md)
