---
id: fluent-api
title: Fluent Load Testing API
sidebar_position: 3
---

# Fluent Load Testing API

Use `load(path)` for a single GET step. For headers, payloads, checks, and multiple steps, start with `loadScenario(name)`. `step(name)` returns `LoadStep`; `and()` returns its parent `LoadScenario`.

## POST request

The following fragment belongs inside a `BaseLoadTest` test method, with `java.util.Map` imported:


```java
loadScenario("Create order").users(10)
    .step("Create").post("/orders")
        .header("Content-Type", "application/json")
        .body(Map.of("sku", "PROD-998", "quantity", 1))
        .check(status().is(201)).and()
    .run().assertErrorRateBelow(0.01);
```


## Multi-step journey


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


## Method reference

| Receiver | Methods |
| --- | --- |
| `BaseLoadTest` | `load(String path)`, `loadScenario(String name)`, `lastLoadMetrics()`, `status()`, `jsonPath(String)` |
| `LoadScenario` | `users`, `rampUp`, `hold`, `cooldown`, `engine`, `baseUrl`, `feed`, `feedCsv`, `feedJson`, `thinkTime`, `step`, `run` |
| `LoadStep` | `get(path)`, `post(path)`, `put(path)`, `patch(path)`, `delete(path)`, `header`, `queryParam`, `formParam`, `body`, `check`, `extract`, `and`, `run` |

`thinkTime(Duration)` sets a fixed pause; `thinkTime(minMs, maxMs)` sets a random pause between requests. `extract` makes a response value available to subsequent steps of the same virtual user. There is no `load(LoadScenario)` overload or `targetRps()` throttling API. Place load settings on the scenario and request settings on its steps.
