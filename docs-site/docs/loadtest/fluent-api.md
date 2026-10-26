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


## Workload models (Gatling only) {#workload-models}

`users`, `rampUp` and `hold` describe one fixed shape: users start evenly over `rampUp` and each one loops for `hold` (see [Configuration](./configuration.md)). For any other shape, give the scenario a workload with `workload(Workload.open(...))` or `workload(Workload.closed(...))`. The steps run in order, and each virtual user runs the scenario steps **once**; there is no hold loop.

- **Closed model:** you set how many users run at the same time. A user that finishes is replaced by a new one.
- **Open model:** you set how users arrive, whatever the number already running. Concurrency is an outcome: on a slow endpoint, users pile up.

```java
import io.testfly.loadtest.BaseLoadTest;
import io.testfly.loadtest.ClosedStep;
import io.testfly.loadtest.OpenStep;
import io.testfly.loadtest.Workload;
import org.testng.annotations.Test;
import java.time.Duration;

public class CartWorkloadLoadTest extends BaseLoadTest {
    @Test
    public void cartUnderConstantConcurrency() {
        loadScenario("Cart")
            .engine("gatling")
            .workload(Workload.closed(
                ClosedStep.rampConcurrent(0, 50, Duration.ofSeconds(30)),
                ClosedStep.constantConcurrent(50, Duration.ofMinutes(2)),
                ClosedStep.rampConcurrent(50, 0, Duration.ofSeconds(30)))) // ramp-down
            .step("Cart").get("/cart").and()
            .run()
            .assertP95Below(300)
            .assertErrorRateBelow(0.01);
    }

    @Test
    public void searchArrivalRate() {
        loadScenario("Search")
            .engine("gatling")
            .workload(Workload.open(
                OpenStep.atOnce(10),
                OpenStep.rampRate(1, 20, Duration.ofSeconds(30)),
                OpenStep.constantRate(20, Duration.ofMinutes(2)),
                OpenStep.nothingFor(Duration.ofSeconds(10)),
                OpenStep.stressPeak(100, Duration.ofSeconds(20))))
            .step("Search").get("/search?q=shoes").and()
            .run()
            .assertErrorRateBelow(0.01);
    }
}
```

| Step | Model | Meaning |
| --- | --- | --- |
| `OpenStep.atOnce(users)` | open | Starts `users` users at once (lasts 0). |
| `OpenStep.ramp(users, over)` | open | Starts `users` users spread evenly over `over`. |
| `OpenStep.constantRate(usersPerSec, during)` | open | Starts `usersPerSec` new users per second during `during`. |
| `OpenStep.rampRate(fromPerSec, toPerSec, during)` | open | Changes the arrival rate linearly from `fromPerSec` to `toPerSec`. |
| `OpenStep.stressPeak(users, over)` | open | Starts `users` users following a smooth (Heaviside) spike over `over`. |
| `OpenStep.nothingFor(duration)` | open | Starts no user for `duration`. |
| `ClosedStep.constantConcurrent(users, during)` | closed | Keeps `users` users running at the same time during `during`. |
| `ClosedStep.rampConcurrent(fromUsers, toUsers, during)` | closed | Changes the concurrent users linearly from `fromUsers` to `toUsers`. |

Rules, all checked before any request is sent:

- **Gatling only.** On the JDK engine, or with `auto` when Gatling is not on the classpath, `run()` fails with `IllegalArgumentException` ("workload(...) requires the Gatling engine").
- **No mixing.** Open and closed steps are different types, so they cannot be mixed in one workload. A workload cannot be combined with `users`, `rampUp`, `hold` or `cooldown` on the same scenario (`IllegalArgumentException`). Values for those keys from `@LoadTest` or `testfly.yml` are ignored, and an INFO line says so.
- **Arguments.** The factories throw `IllegalArgumentException` at once for a user count below 1 (`rampConcurrent` accepts 0), a negative or non-finite rate, or a duration that is not positive. A workload that starts nobody (only pauses, only zero rates, or a closed peak of 0) is rejected.
- **`loadtest.maxUsers`.** For a closed workload it caps the peak concurrency, ramp end points included. For an open workload it caps the peak arrival rate (users/s of `constantRate` and `rampRate`) and the user count of each `atOnce`, `ramp` and `stressPeak` step. It does not cap the concurrency that results from an open workload.
- **Ramp-down.** Write it as `rampConcurrent(n, 0, duration)`. `cooldown` is still not applied to the `users/rampUp/hold` shape.
- **Reported users.** `metrics().users()` is the planned number of users: for a closed workload the peak concurrency; for an open workload the users of the `atOnce`, `ramp` and `stressPeak` steps plus the rate steps' `rate × duration`, rounded up.

New step types may be added in minor releases, so a `switch` over `OpenStep` or `ClosedStep` needs a `default` branch. To run several workloads together, see [Simulations and SLAs](./simulations-sla.md).

## Method reference

| Receiver | Methods |
| --- | --- |
| `BaseLoadTest` | `load(String path)`, `loadScenario(String name)`, `loadSimulation(String name)`, `sla()`, `lastLoadMetrics()`, `status()`, `jsonPath(String)` |
| `LoadScenario` | `users`, `rampUp`, `hold`, `cooldown`, `workload`, `sla`, `engine`, `baseUrl`, `feed`, `feedCsv`, `feedJson`, `thinkTime`, `step`, `run` |
| `LoadStep` | `get(path)`, `post(path)`, `put(path)`, `patch(path)`, `delete(path)`, `header`, `queryParam`, `formParam`, `body`, `check`, `extract`, `and`, `run` |
| `LoadSimulation` | `scenario`, `baseUrl`, `engine`, `sla`, `run` (see [Simulations and SLAs](./simulations-sla.md)) |

`thinkTime(Duration)` sets a fixed pause; `thinkTime(minMs, maxMs)` sets a random pause between requests. `extract` makes a response value available to subsequent steps of the same virtual user. There is no `load(LoadScenario)` overload or `targetRps()` throttling API; for an arrival rate, use an open workload with `constantRate`. Place load settings on the scenario and request settings on its steps.

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
