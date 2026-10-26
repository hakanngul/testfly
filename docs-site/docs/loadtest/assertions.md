---
id: assertions
title: Fluent Performance Assertions
description: "Establish strict quality gates for latency percentiles, throughput RPS, error thresholds, and HTTP status codes via LoadTestAssert."
sidebar_position: 6
---

# Fluent Performance Assertions

TestFly treats performance results as first-class assertions. When `run()` finishes, it returns a `LoadTestAssert` instance, allowing you to enforce performance thresholds directly in CI pipelines. You can also declare the thresholds before the run as an SLA; see [SLAs or assertions?](#slas-or-assertions).

---

## 1. Latency & Percentile Assertions

Check response times across key latency percentiles:

```java
load("/api/v1/feed")
    .users(50)
    .hold(Duration.ofSeconds(15))
    .run()
    .assertMeanLatencyBelow(100)  // Average latency < 100ms
    .assertP50Below(80)           // Median latency < 80ms
    .assertP75Below(120)          // 75th percentile < 120ms
    .assertP90Below(150)          // 90th percentile < 150ms
    .assertP95Below(250)          // 95th percentile < 250ms
    .assertP99Below(500)          // 99th percentile < 500ms
    .assertMaxLatencyBelow(1200); // Worst single request < 1.2s
```

When an assertion fails, TestFly produces detailed diagnostic failure messages:

```
java.lang.AssertionError: [LoadTest] /api/v1/feed: p95 latency 348ms is not below 250ms
```

### Which percentiles each engine measures

Both engines measure p50, p75, p90, p95 and p99, overall and (p95, p99) per step. TestFly computes them exactly, with linear interpolation; on Gatling it uses its own millisecond histogram, so the values can differ from Gatling's HTML report (see [Reporting](./reporting.md#percentiles)). On Gatling the latency figures cover only requests that received an HTTP response; the JDK engine also includes the elapsed time of connection failures and timeouts.

A value the engine did not measure, for example a latency in a run where no request received a response, is `Double.NaN` in `LoadTestMetrics` (check it with `LoadTestMetrics.isMeasured(value)`), `null` in the JSON results and `n/a` in the reports. It is never reported as `0`. A latency assertion on an unmeasured value fails instead of passing:

```
java.lang.AssertionError: [LoadTest] /api/v1/feed: p95 latency was not measured by engine 'gatling'
```

`assertStepP95Below(...)` behaves the same way when a step's p95 was not measured.

---

## 2. Throughput & Error Rate Assertions

Ensure your services meet load capacity without degrading reliability:

```java
load("/api/orders")
    .users(30)
    .run()
    .assertThroughputAbove(120.0)    // Minimum 120 requests/second
    .assertErrorRateBelow(0.01)     // Error rate must stay below 1%
    .assertSuccessRateAbove(0.99);  // At least 99% requests must succeed
```

:::caution What counts as an error
Both engines classify requests the same way. A request is failed when it gets no HTTP response (connection refused, timeout, I/O error), when one of its `check(...)` conditions fails, or when an `extract(...)` path is not found in the response. If a step has no `status().is(...)` or `status().in(...)` check, a default status check applies: the response must be 2xx (200–299) or 304, so a 4xx or 5xx response raises the error rate. Add an explicit status check to accept other codes, for example `check(status().in(200, 404))`.
:::

---

## 3. HTTP Status Code Assertions

Verify that servers do not return 5xx errors or rate-limit warnings under pressure:

```java
load("/api/payments")
    .users(20)
    .run()
    .assertNoStatus(500)                 // No Internal Server Error
    .assertNoStatus(502)                 // No Bad Gateway
    .assertNoStatus(503)                 // No Service Unavailable
    .assertNoStatus(504)                 // No Gateway Timeout
    .assertNoStatus(429)                 // No Too Many Requests
    .assertStatusCodeCount(200, 1000);   // At least 1,000 OK responses
```

Both engines record the real HTTP status of every response, including responses whose checks failed. Requests that got no response at all are counted under the key `-1`. `LoadScenario.assertStatus(n)` passes only when every request returned `n`, so a `-1` entry fails it.

If the engine could not record status codes for a run, `statusCodes()` is empty and `LoadTestMetrics.statusCodesMeasured()` returns `false`. `assertStatus`, `assertStatusCodeCount` and `assertNoStatus` then fail with `HTTP status codes were not measured by engine '<engine>'` instead of passing on missing data.

---

## 4. Multi-Step Journey Assertions

In multi-step scenarios, you can assert against individual steps by name:


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


---

## 5. Inspecting Raw Metrics

If you need custom statistics or custom reporting logic, retrieve the underlying `LoadTestMetrics` snapshot:

```java
LoadTestMetrics metrics = load("/api/summary").run().metrics();

System.out.println("Total requests: " + metrics.totalRequests());
System.out.println("P95 Latency: " + metrics.p95LatencyMs() + " ms");
System.out.println("HTTP 200 count: " + metrics.statusCodeCount(200));
System.out.println("No response: " + metrics.statusCodeCount(-1));
if (LoadTestMetrics.isMeasured(metrics.p90LatencyMs())) {
    System.out.println("P90 Latency: " + metrics.p90LatencyMs() + " ms");
}
```

`metrics.partial()` is `true` when the run is incomplete (Gatling only: some requests have no recorded outcome, or `maxDuration` stopped the run). Latency, status-code, error-rate, success-rate and failed-request assertions and `assertThroughputBelow` then fail with `metrics are partial` instead of passing on incomplete data. Lower bounds that undercounting cannot fake (`assertThroughputAbove`, `assertTotalRequestsAbove`, `assertSuccessfulRequestsAbove`) still apply.

---

## 6. SLAs or Assertions? {#slas-or-assertions}

The `assert*` methods above run **after** `run()` returns and stop at the first failure. An SLA is declared **before** the run with `sla(...)`; TestFly evaluates every rule, records the results with the run and then fails with one `AssertionError` that lists every failed rule.

```java
loadScenario("Checkout")
    .users(20).rampUp(Duration.ofSeconds(10)).hold(Duration.ofSeconds(30))
    .sla(sla().p95Below(300).errorRateBelow(0.01)
              .step("Pay").p99Below(800))
    .step("Cart").get("/cart").and()
    .step("Pay").post("/pay").body(Map.of("amount", 10)).check(status().is(201)).and()
    .run();
```

| | `assertP95Below(..)` and other `assert*` methods | `sla(..)` |
| :--- | :--- | :--- |
| When | After `run()` returns | Declared before the run, evaluated when it ends |
| Failure | The first failed assertion throws | Every rule is evaluated; one `AssertionError` lists all failed rules |
| Results kept | No | `slaResults()` on `LoadTestAssert` and `LoadTestMetrics`, and the `sla` entry of the JSON results |
| Gatling HTML report | Not shown | Also registered as native Gatling assertions |
| Global metrics | Latency (min to max), throughput above and below, error and success rate, request counts, status codes | p50, p75, p90, p95, p99, mean, max, error rate, throughput above |
| Per step | `assertStepP95Below`, `assertStepErrorRateBelow` | p95, p99, error rate |
| Simulations | On `result.global()` or `result.scenario(name)` | On the simulation or on each scenario |

Both are strict (`p95Below(300)` passes only when p95 is below 300), and both fail rather than pass on an unmeasured value. On partial metrics every SLA rule fails; the `assert*` methods behave as described in section 5. You can combine them: the `LoadTestAssert` returned by a run whose SLA passed still accepts `assert*` calls. See [Simulations and SLAs](./simulations-sla.md#slas) for the rules, and [Baselines](./baselines.md) for comparing a run with an earlier one.
