# Compile-checked examples

These examples use Java 21 and the current public TestFly API. Configure `api.baseUrl` in `testfly.yml`; do not embed an environment host in the test.

## TestNG: core scenarios and reusable service

```java
package example.api;

import io.testfly.client.ApiAuth;
import io.testfly.client.ApiClient;
import io.testfly.client.ApiResponse;
import io.testfly.test.BaseApiTest;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.Objects;

public final class UserApiTest extends BaseApiTest {
    private final UsersApi users = new UsersApi();

    @Test
    public void getsUser() {
        users.get(42)
                .assertStatus(200)
                .assertHeaderPresent("Content-Type")
                .assertJson("$.id", 42)
                .assertJsonExists("$.email");
    }

    @Test
    public void createsUserFromJsonObject() {
        users.create(new CreateUser("Ada", "ada@example.test"))
                .assertStatus(201)
                .assertJson("$.name", "Ada")
                .assertJsonExists("$.id");
    }

    @Test
    public void authenticatesPerRequest() {
        String token = Objects.requireNonNull(
                System.getenv("API_TOKEN"), "API_TOKEN is required");

        ApiClient.get("/me")
                .auth(ApiAuth.bearerToken(token))
                .send()
                .assertStatus(200)
                .assertJsonExists("$.id");
    }

    @Test
    public void rejectsUnknownUser() {
        users.get(9_999_999)
                .assertStatus(404)
                .assertJson("$.code", "USER_NOT_FOUND")
                .assertJsonExists("$.message");
    }

    @Test
    public void validatesUserSchema() {
        users.get(42)
                .assertStatus(200)
                .assertSchema("schemas/user-response.json");
    }

    record CreateUser(String name, String email) {}

    static final class UsersApi {
        ApiResponse get(long id) {
            return ApiClient.get("/users/{id}")
                    .pathParam("id", id)
                    .header("Accept", "application/json")
                    .send();
        }

        ApiResponse create(CreateUser user) {
            return ApiClient.post("/users")
                    .contentType("application/json")
                    .body(user)
                    .send();
        }

        ApiResponse search(String email) {
            return ApiClient.get("/users")
                    .queryParams(Map.of("email", email, "limit", 1))
                    .send();
        }
    }
}
```

`assertSchema` compiles through TestFly but needs `com.networknt:json-schema-validator:1.4.3` at runtime and a classpath resource such as `src/test/resources/schemas/user-response.json`.

Current TestNG caveat: because `BaseApiTest` implements `MockSupport`, its teardown requires `org.wiremock:wiremock-standalone:3.5.4` on the runtime classpath even when the test itself does not start `ApiMockServer`.

## Deterministic client-side mock and polling

```java
package example.api;

import io.testfly.client.ApiClient;
import io.testfly.client.ApiMockRule;
import io.testfly.client.ApiResponse;
import io.testfly.test.BaseApiTest;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public final class JobPollingTest extends BaseApiTest {
    @Test
    public void pollsUntilJobCompletesWithoutExternalTraffic() {
        AtomicInteger calls = new AtomicInteger();
        ApiMockRule job = ApiMockRule.builder()
                .match(request -> request.uri().getPath().equals("/jobs/42"))
                .respond(request -> {
                    boolean complete = calls.incrementAndGet() >= 2;
                    return ApiResponse.builder()
                            .request(request)
                            .status(200)
                            .header("Content-Type", "application/json")
                            .body("{\"state\":\"" + (complete ? "COMPLETED" : "PENDING") + "\"}")
                            .build();
                })
                .build();

        ApiClient.get("http://unused.invalid/jobs/42")
                .mockRule(job)
                .pollUntil(
                        response -> "COMPLETED".equals(response.json("$.state")),
                        Duration.ofSeconds(2),
                        Duration.ofMillis(50))
                .assertStatus(200)
                .assertJson("$.state", "COMPLETED");
    }
}
```

This TestNG example is runtime-validated with the WireMock dependency required by the current `BaseApiTest` teardown; the request itself is satisfied entirely by `ApiMockRule` and performs no network traffic.

## JUnit 5 lifecycle

Only the lifecycle imports differ; request/service code remains the same:

```java
package example.api;

import io.testfly.client.ApiClient;
import io.testfly.junit5.BaseJUnit5ApiTest;
import org.junit.jupiter.api.Test;

final class HealthApiTest extends BaseJUnit5ApiTest {
    @Test
    void reportsHealthy() {
        ApiClient.get("/health").send()
                .assertStatus(200)
                .assertJson("$.status", "UP");
    }
}
```

JUnit consumers must add `junit-jupiter-api`, an engine, and the platform launcher at compatible versions. For Cucumber, place the same service calls in a `BaseCucumberSteps` subclass and tag pure API scenarios `@api`; do not duplicate the client layer per framework.
