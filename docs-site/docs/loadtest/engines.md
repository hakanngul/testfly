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
| **Underlying Tech** | Gatling 3.16.0 + Netty async IO | Java 21+ `HttpClient` + Virtual Threads |
| **Classpath Dependency** | Optional (`gatling-charts-highcharts`) | Built into Java standard library |
| **Process Isolation** | Dedicated forked JVM subprocess | Runs in-process on test worker thread |
| **Native Report** | Interactive Highcharts HTML report | Integrated TestFly HTML dashboard |
| **Subprocess Log** | `gatling-subprocess.log` | Standard TestFly logs |
| **Metrics Source** | TestFly's own sidecar `testfly-metrics.json` (counts, status codes, millisecond latency histogram) | Measured in-process |
| **Latency Percentiles** | p50, p75, p90, p95, p99 (exact, linear interpolation) | p50, p75, p90, p95, p99 (exact, linear interpolation) |
| **Latency Population** | Requests that received an HTTP response | Also includes the elapsed time of connection failures and timeouts |
| **HTTP Status Codes** | Real status per response | Real status per response |
| **JSONPath in checks** | Full Gatling JSONPath (a path without `$` is read as `$.path`) | `$.a.b`, `$.a[0].b`, `$[0]` only (same `$.path` rule) |
| **`users` / `rampUp` / `hold`** | Yes | Yes |
| **[Workload models](./fluent-api.md#workload-models)** (`workload(...)`) | Yes | **No**: rejected before any request |
| **[Simulations](./simulations-sla.md)** (`loadSimulation(...)`) | Yes | **No**: rejected before any request |
| **[SLAs](./simulations-sla.md#slas)** (`sla(...)`) | Yes, evaluated by TestFly; also shown as native assertions in Gatling's report | Yes, evaluated by TestFly |
| **[Baselines](./baselines.md)** and `testfly-loadtest-result.json` | Yes | Yes |
| **Partial-run detection** | Yes (lost outcomes, `maxDuration` stop) | Not needed: an aborted run fails with `IllegalStateException` |
| **Recommended For** | Benchmark runs, stress tests, CI pipelines | Local dev smoke tests, fast pull requests |

Gatling is an optional dependency of TestFly. Add `io.gatling.highcharts:gatling-charts-highcharts` to your own build to use the Gatling engine and the Gatling-only features; TestFly is built and tested against Gatling 3.16.0.

---

## 2. Automatic Engine Selection (`auto`)

By default, `loadtest.engine: auto` uses dynamic classpath inspection:

```java
// If io.gatling.app.Gatling is found on the classpath:
//    Uses GatlingEngine
// Else:
//    Falls back to JdkLoadEngine without throwing ClassNotFoundException
```

This means developer machines without Gatling dependencies can still run the entire test suite without modification. The fallback is not silent: the first fallback in a JVM logs a WARN, and every run logs the resolved engine in its step name, for example `engine=jdk (auto: Gatling not on classpath)`. `metrics().engine()` returns the engine that actually ran.

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
@io.testfly.loadtest.LoadTest(engine = "gatling")
public void stressTest() {
    load("/api/payment/checkout").run();
}
```


Before any request is sent, TestFly validates the scenario for the resolved engine and fails with `IllegalArgumentException` on input that engine cannot run faithfully, for example a JSONPath outside the JDK subset or a step with both a body and form parameters. See [Fluent API](./fluent-api.md#checks-and-request-options).

## 4. Gatling Run Lifecycle

Each Gatling run executes in its own child JVM and results directory (`<resultsDir>/<run-id>/`):

- Every request has the `requestTimeoutSeconds` timeout. The simulation has a `maxDuration` of the injection time (`rampUp + hold`, or a workload's total duration) plus, per scenario step, `requestTimeoutSeconds` and the longest think time, plus 30s. When it is reached, Gatling stops and still writes its report, and TestFly flags the metrics as partial.
- TestFly waits at most `maxDuration + 120s` for the child JVM. If it is still running, the child and its descendant processes are terminated and the run fails with `IllegalStateException`. The child is also terminated when the test thread is interrupted and when the parent JVM exits.
- A non-zero exit code fails the run. The exception names the exit code and includes the root error lines and the last 20 lines of `gatling-subprocess.log`, with credentials (`Authorization`, `Cookie`, `Bearer` tokens, and token, password, secret and API-key values) masked as `***`. One exception: when an [SLA](./simulations-sla.md#slas) was registered as native Gatling assertions, exit code 2 only means "Gatling assertions failed". The run is then parsed as usual and TestFly's own SLA verdict decides.
- TestFly records the metrics itself while the simulation runs and writes them to `testfly-metrics.json` in the run directory: per request name the attempts, OK and KO counts, status codes and a millisecond latency histogram. `LoadTestMetrics` is built from this file only. TestFly does not read Gatling's report files or `simulation.log`, which is a binary internal format since Gatling 3.13. If the file is missing, the run fails with `IllegalStateException`.
- A request that cannot be built (for example one whose `${var}` has no value) counts as KO with no response (status `-1`). Gatling's own report leaves it out, so its request totals can be lower than TestFly's.
- A run is **partial** when requests were started without a recorded outcome, or when it ran until `maxDuration` stopped it. TestFly logs a WARN and sets `LoadTestMetrics.partial()`. Latency, status-code, error-rate and `assertThroughputBelow` assertions then fail, every SLA rule fails, and no result file or baseline is written from that run.
- The `users/rampUp/hold` loop finishes the iteration in progress when `hold` ends, as the JDK engine does, so a run can last up to one iteration longer than `rampUp + hold`.
- Gatling's HTTP warm-up request and usage analytics are disabled, so a run only connects to the system under test.

The JDK engine follows the same rule for cancellation: when the test thread is interrupted, or the run passes its safety timeout (`rampUp + hold + cooldown + 60s`), the virtual users are stopped and the run fails with `IllegalStateException`. Partial metrics are never returned as a normal result.

Concurrency and throughput depend on hardware, the target service and scenario. No fixed capacity or startup time is guaranteed; measure your workload. JDK load execution uses its own transport, so ApiClient interceptors and mock rules do not automatically apply.
