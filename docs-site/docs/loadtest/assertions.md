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
    .assertP90Below(150)          // 90th percentile < 150ms
    .assertP95Below(250)          // 95th percentile < 250ms
    .assertP99Below(500)          // 99th percentile < 500ms
    .assertMaxLatencyBelow(1200); // Worst single request < 1.2s
```

When an assertion fails, TestFly produces detailed diagnostic failure messages:

```
java.lang.AssertionError: [LoadTest] P95 latency assertion failed: expected <= 200 ms, but was 348 ms (P50: 120ms, P90: 280ms, P99: 512ms)
```

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
    .assertStatusCodeCount(200, 1000);   // Exactly 1,000 OK responses
```

---

## 4. Multi-Step Journey Assertions

In multi-step scenarios, you can assert against individual steps by name:

```java
LoadScenario flow = LoadScenario.named("Checkout Flow")
    .step("Login", req -> req.post("/api/login").body("..."))
    .step("Process Payment", req -> req.post("/api/pay").body("..."));

load(flow)
    .users(15)
    .run()
    .assertStepP95Below("Login", 150)
    .assertStepP95Below("Process Payment", 800)
    .assertStepErrorRateBelow("Process Payment", 0.0);
```

---

## 5. Inspecting Raw Metrics

If you need custom statistics or custom reporting logic, retrieve the underlying `LoadTestMetrics` snapshot:

```java
LoadTestMetrics metrics = load("/api/summary").run().metrics();

System.out.println("Total requests: " + metrics.totalRequests());
System.out.println("P95 Latency: " + metrics.p95LatencyMs() + " ms");
System.out.println("HTTP 200 count: " + metrics.statusCodeCount(200));
```
