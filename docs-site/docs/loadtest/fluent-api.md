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

## Checks and request options

Checks behave the same on the JDK and Gatling engines:

| Check | Meaning |
| --- | --- |
| `status().is(code)`, `status().in(codes...)` | The HTTP status equals `code` or is one of `codes`. |
| *(no status check)* | Default rule: the status must be 2xx (200–299) or 304. |
| `jsonPath(p).exists()` | A value exists at the JSONPath `p`. |
| `jsonPath(p).is(n)`, `jsonPath(p).in(n...)` | The JSON value at `p` equals `n` or is one of the values (compared as strings, so JSON number `5` matches `5`). |
| `status().matches(regex)`, `jsonPath(p).matches(regex)` | The regex is found anywhere in the **whole response body** (`Matcher.find()`, no implicit DOTALL). The JSONPath is not used. |

- `status().exists()` throws `IllegalStateException`; `exists()` is only valid on `jsonPath(...)`.
- `extract(var, path)` marks the request as failed when `path` is not found.
- `formParam(name, value)` sends an `application/x-www-form-urlencoded` body on both engines, unless you set a `Content-Type` header. A step cannot combine `body(...)` and `formParam(...)`.
- `queryParam` values are URL-encoded (UTF-8, spaces as `%20`) after `${var}` substitution.
- A step with a `body(...)` and no `Content-Type` header is sent as `application/json`.

Unsupported input fails with `IllegalArgumentException` before any request is sent. Examples are a step with both a body and form parameters, a blank JSONPath, or an invalid regex. A path without a leading `$` is read as `$.` plus the path on both engines (`token` means `$.token`). The JDK engine supports only the JSONPath forms `$.a.b`, `$.a[0].b` and `$[0]`. On both engines a JSON `null` value counts as present: `exists()` passes and `extract(...)` stores `null`. Wildcards, deep scan (`..`), filters, slices and bracket keys such as `['x']` fail fast on the JDK engine and need `engine("gatling")`.
