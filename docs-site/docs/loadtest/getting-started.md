---
id: getting-started
title: Getting Started with Load Testing
description: "Run load and performance tests directly inside your TestFly test suite using Gatling or lightweight JDK virtual threads."
sidebar_position: 1
---

# Getting Started with Load Testing

TestFly 1.1.0 brings seamless **Load & Performance Testing** directly into your existing automation suite. Write UI, API, and load tests side-by-side using the same familiar base classes (`BaseTest`, `BaseApiTest`, `BaseJUnit5Test`) without context switching.

---

## Why Load Testing in TestFly?

In traditional setups, performance testing is isolated from functional testing—written in separate tools, maintained in different repositories, and executed on distinct schedules. TestFly breaks down this barrier:

- **Unified Suite:** Run a 50-user load test as a standard `@Test` method in TestNG or JUnit 5.
- **Zero-Boilerplate Engine:** Uses Gatling under the hood when available on the classpath, or falls back to an ultra-lightweight JDK engine without any external dependencies.
- **Fluent & Declarative APIs:** Configure scenarios imperatively with `load(url).users(50)...` or declaratively with `@LoadTest(users = 50)`.
- **Integrated Reporting:** View throughput, latency percentiles (P50, P90, P95, P99), and Gatling's interactive Highcharts reports directly inside TestFly's HTML, Allure, and ReportPortal dashboards.

---

## 1. Quick Installation

Ensure you have TestFly `1.1.0` or higher in your project:

```xml
<dependency>
    <groupId>io.github.hakanngul</groupId>
    <artifactId>testfly</artifactId>
    <version>1.1.0</version>
    <scope>test</scope>
</dependency>
```

### Optional: Gatling High-Performance Engine

If you want enterprise-scale simulations with Gatling's full HTTP engine, add the optional Gatling dependencies:

```xml
<dependency>
    <groupId>io.gatling.highcharts</groupId>
    <artifactId>gatling-charts-highcharts</artifactId>
    <version>3.10.3</version>
    <scope>test</scope>
</dependency>
```

:::tip Zero-Config Fallback
If Gatling is not on the classpath, TestFly automatically defaults to its built-in JDK engine. You do not need to install Gatling to get started!
:::

---

## 2. Your First Load Test

Extend `BaseTest` (or `BaseApiTest`) and call `load(url)`:

```java
package com.example.tests;

import io.testfly.test.BaseTest;
import org.testng.annotations.Test;
import java.time.Duration;

public class OrderLoadTest extends BaseTest {

    @Test
    public void testOrderServiceUnderLoad() {
        load("/api/v1/orders")
            .users(20)
            .rampUp(Duration.ofSeconds(5))
            .hold(Duration.ofSeconds(15))
            .run()
            .assertThroughputAbove(50.0)
            .assertP95Below(250)
            .assertErrorRateBelow(0.01)
            .assertNoStatus(500);
    }
}
```

### What Happens Behind the Scenes?

1. **Resolution:** TestFly resolves `/api/v1/orders` against `execution.baseUrl` configured in your `testfly.yml`.
2. **Execution:** Automatically selects the best engine (Gatling if present, or JDK engine), ramps up 20 concurrent virtual users over 5 seconds, and holds steady for 15 seconds.
3. **Assertions:** Gathers response latencies and status codes, then executes fluent performance gates (`assertP95Below`, `assertErrorRateBelow`, etc.).
4. **Reporting:** Metrics, latency distribution charts, and native Gatling report links are automatically archived into `target/reports/testfly-report.html`.

---

## 3. Declarative Execution via Annotations

For standard benchmark tests, you can configure the load profile using `@LoadTest`:

```java
import io.testfly.loadtest.LoadTest;
import io.testfly.test.BaseTest;
import org.testng.annotations.Test;

public class CatalogLoadTest extends BaseTest {

    @Test
    @LoadTest(users = 50, rampUpSeconds = 10, durationSeconds = 30)
    public void testProductCatalog() {
        load("/api/products")
            .run()
            .assertP95Below(300);
    }
}
```

---

## Next Steps

- Explore [Configuration](./configuration.md) to set global load testing defaults.
- Learn about multi-step scenarios in the [Fluent API Guide](./fluent-api.md).
- Parameterize requests with dynamic data via [Feeders](./feeders.md).
- Discover full performance validation with [Assertions](./assertions.md).
