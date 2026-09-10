---
id: engines
title: Execution Engines (Gatling vs. JDK)
description: "Understand the differences, resource footprints, and performance capabilities between the Gatling and JDK load engines."
sidebar_position: 8
---

# Execution Engines (Gatling vs. JDK)

TestFly is engine-agnostic. It features a dual-engine architecture designed to deliver maximum flexibility: an ultra-high performance Gatling engine for enterprise load simulations, and a zero-dependency JDK engine for lightweight local smoke tests.

---

## 1. Engine Comparison

| Feature | Gatling Engine (`gatling`) | JDK Engine (`jdk`) |
| :--- | :--- | :--- |
| **Underlying Tech** | Gatling 3.10.x + Netty async IO | Java 17+ `HttpClient` + Virtual Threads |
| **Classpath Dependency** | Optional (`gatling-charts-highcharts`) | Built into Java standard library |
| **Max Concurrency** | 10,000+ virtual users | ~100–500 virtual users |
| **Process Isolation** | Dedicated forked JVM subprocess | Runs in-process on test worker thread |
| **Native Report** | Interactive Highcharts HTML report | Integrated TestFly HTML dashboard |
| **Subprocess Log** | `gatling-subprocess.log` | Standard TestFly logs |
| **Startup Overhead** | ~1.5–2.5 seconds (JVM warmup) | < 20 ms (instant) |
| **Recommended For** | Benchmark runs, stress tests, CI pipelines | Local dev smoke tests, fast pull requests |

---

## 2. Automatic Engine Selection (`auto`)

By default, `loadtest.engine: auto` uses dynamic classpath inspection:

```java
// If io.gatling.app.Gatling is found on the classpath:
//    Uses GatlingEngine
// Else:
//    Silently falls back to JdkLoadEngine without throwing ClassNotFoundException
```

This means developer machines without Gatling dependencies can still run the entire test suite without modification.

---

## 3. Explicit Engine Selection

Force an engine at the configuration or test level:

### In `testfly.yml`
```yaml
loadtest:
  engine: gatling # or 'jdk'
```

### In Code (Fluent API)
```java
load("/api/health")
    .engine("jdk") // Forces JDK engine for fast execution
    .users(10)
    .run();
```

### Via Annotation
```java
@Test
@LoadEngine("gatling")
public void stressTest() {
    load("/api/payment/checkout").run();
}
```
