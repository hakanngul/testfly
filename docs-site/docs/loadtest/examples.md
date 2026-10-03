---
id: examples
title: Load Testing Examples & Recipes
sidebar_position: 9
---

# Load Testing Examples

These tests require your own reachable backend and the [development build](./getting-started.md). Adapt paths, payloads and thresholds to your application.

## Health smoke


```java
import io.testfly.loadtest.BaseLoadTest;
import org.testng.annotations.Test;
import java.time.Duration;

public class HealthLoadTest extends BaseLoadTest {
    @Test
    public void healthUnderLoad() {
        load("/health").engine("jdk").users(10)
            .rampUp(Duration.ofSeconds(2)).hold(Duration.ofSeconds(5))
            .run().assertP95Below(250).assertErrorRateBelow(0.01);
    }
}
```


## Login and payment

Create `testdata/users.csv` on the filesystem or classpath:

```csv
username,password
demo-user,demo-password
```


```java
import io.testfly.loadtest.BaseLoadTest;
import io.testfly.loadtest.LoadTestFeeder;
import org.testng.annotations.Test;
import java.time.Duration;
import java.util.Map;

public class CheckoutLoadTest extends BaseLoadTest {
    @Test
    public void checkout() {
        loadScenario("Checkout")
            .engine("jdk").users(15).hold(Duration.ofSeconds(10))
            .feed(LoadTestFeeder.csv("testdata/users.csv"))
            .step("Login").post("/login")
                .body(Map.of("username", "${username}", "password", "${password}"))
                .check(status().is(200)).extract("token", "$.accessToken").and()
            .step("Payment").post("/payment")
                .header("Authorization", "Bearer ${token}")
                .body(Map.of("amount", 10)).check(status().is(200)).and()
            .run().assertStepP95Below("Login", 300)
                .assertStepP95Below("Payment", 800)
                .assertStepErrorRateBelow("Payment", 0.01);
    }
}
```


## Combining UI and load coverage

Keep UI tests in `BaseTest` classes and load tests in `BaseLoadTest` classes in the same TestNG suite. Use separate suites/processes if UI checks must observe a service during load. Do not assume `CompletableFuture.runAsync()` carries TestFly’s thread-local test ID or annotation context to a background load run. `BaseTest` does not provide `load()` and `BaseLoadTest` does not start a WebDriver.
