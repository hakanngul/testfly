---
id: annotations
title: Declarative Annotations (@LoadTest)
description: "Configure performance tests declaratively using @LoadTest and @LoadEngine annotations on test classes and methods."
sidebar_position: 4
---

# Declarative Annotations (@LoadTest)

In addition to the fluent builder API, TestFly supports declarative load testing via `@LoadTest` and `@LoadEngine` annotations. This is ideal for standardization across testing teams and maintaining clean separation between test configuration and assertion logic.

---

## 1. `@LoadTest` Annotation

Apply `@LoadTest` to any test method or test class:

```java
import io.testfly.loadtest.LoadTest;
import io.testfly.test.BaseTest;
import org.testng.annotations.Test;

public class BenchmarkSuite extends BaseTest {

    @Test
    @LoadTest(
        users = 50,
        rampUpSeconds = 10,
        durationSeconds = 30,
        targetRps = 200
    )
    public void benchmarkInventoryApi() {
        load("/api/inventory/status")
            .run()
            .assertP95Below(150)
            .assertNoStatus(500);
    }
}
```

When `@LoadTest` is present, the values provided in the annotation override the defaults from `testfly.yml`. If fluent calls (e.g. `.users(100)`) are chained inside the method, the fluent calls take precedence.

### Annotation Attributes

| Attribute | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `users` | `int` | `1` | Number of concurrent virtual users. |
| `rampUpSeconds` | `int` | `0` | Linear ramp-up time in seconds. |
| `durationSeconds` | `int` | `10` | Peak duration in seconds. |
| `targetRps` | `int` | `0` | Max throughput limit (0 = unthrottled). |
| `scenarioName` | `String` | `""` | Custom display name for reports. |

---

## 2. `@LoadEngine` Annotation

Force a specific performance engine for a particular test without changing `testfly.yml`:

```java
import io.testfly.loadtest.LoadEngine;
import io.testfly.loadtest.LoadTest;
import io.testfly.test.BaseTest;
import org.testng.annotations.Test;

public class HighConcurrencyEngineTest extends BaseTest {

    @Test
    @LoadEngine("gatling") // Explicitly forces GatlingEngine
    @LoadTest(users = 500, rampUpSeconds = 20, durationSeconds = 60)
    public void massiveConcurrentTraffic() {
        load("/api/heavy-computation")
            .run()
            .assertThroughputAbove(400);
    }
}
```

Supported engine identifiers:
- `"auto"`: Probe classpath for Gatling; fall back to JDK virtual threads.
- `"gatling"`: Fail fast if Gatling is not on the classpath.
- `"jdk"`: Always execute via JDK HttpClient + virtual threads.

---

## 3. Class-Level Inheritance

You can annotate the entire test class to establish baseline performance criteria for all contained tests:

```java
@LoadTest(users = 25, durationSeconds = 15)
@LoadEngine("gatling")
public class ProductServiceBenchmarks extends BaseTest {

    @Test
    public void testFeaturedItems() {
        load("/api/featured").run().assertP95Below(200);
    }

    @Test
    @LoadTest(users = 100) // Overrides class-level 'users', inherits engine and duration
    public void testSearchEndpoint() {
        load("/api/search?q=phone").run().assertP95Below(350);
    }
}
```
