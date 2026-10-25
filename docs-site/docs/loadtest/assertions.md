---
id: assertions
title: Fluent Performance Assertions
description: "Establish strict quality gates for latency percentiles, throughput RPS, error thresholds, and HTTP status codes via LoadTestAssert."
sidebar_position: 6
---

# Fluent Performance Assertions

TestFly treats performance results as first-class assertions. When `run()` finishes, it returns a `LoadTestAssert` instance, allowing you to enforce Service Level Agreements (SLAs) directly in CI pipelines.

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
    .assertP90Below(150)          // 90th percentile < 150ms (JDK engine only)
    .assertP95Below(250)          // 95th percentile < 250ms
    .assertP99Below(500)          // 99th percentile < 500ms
    .assertMaxLatencyBelow(1200); // Worst single request < 1.2s
```

When an assertion fails, TestFly produces detailed diagnostic failure messages:

```
java.lang.AssertionError: [LoadTest] /api/v1/feed: p95 latency 348ms is not below 250ms
```

### Which percentiles each engine measures

| Percentile | JDK engine | Gatling engine |
| :--- | :--- | :--- |
| p50, p75, p95, p99 | Measured | Measured (read from Gatling's statistics) |
| p90 | Measured | **Not measured** |

A value the engine did not measure is `Double.NaN` in `LoadTestMetrics` (check it with `LoadTestMetrics.isMeasured(value)`), `null` in the JSON results and `n/a` in the reports. It is never reported as `0`. A latency assertion on an unmeasured value fails instead of passing:

```
java.lang.AssertionError: [LoadTest] /api/v1/feed: p90 latency was not measured by engine 'gatling'
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

If the engine could not record status codes for a run (for example, the Gatling status file `testfly-status.json` is missing), `statusCodes()` is empty and `LoadTestMetrics.statusCodesMeasured()` returns `false`. `assertStatus`, `assertStatusCodeCount` and `assertNoStatus` then fail with `HTTP status codes were not measured by engine '<engine>'` instead of passing on missing data.

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
