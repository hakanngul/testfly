---
id: baselines
title: Baseline Comparison
description: "Save the key metrics of a load-test run as a versioned baseline file and fail later runs that regress beyond a tolerance."
sidebar_position: 6.2
---

# Baseline Comparison

A baseline stores the key metrics of one run: p95, p99 and mean latency, error rate and throughput. Later runs are compared with it, so a test can fail on a regression ("p95 grew by more than 15%") instead of a fixed threshold. Baselines work on both engines.

---

## 1. Save and Compare

```java
import io.testfly.loadtest.BaseLoadTest;
import io.testfly.loadtest.LoadBaseline;
import io.testfly.loadtest.LoadTestAssert;
import io.testfly.loadtest.Tolerance;
import org.testng.annotations.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public class CheckoutRegressionTest extends BaseLoadTest {
    private static final Path BASELINE = Path.of("src/test/resources/baselines/checkout.json");

    @Test
    public void checkoutHasNotRegressed() {
        LoadTestAssert result = loadScenario("Checkout")
            .users(20).rampUp(Duration.ofSeconds(10)).hold(Duration.ofSeconds(30))
            .step("Cart").get("/cart").and()
            .run();

        if (Files.notExists(BASELINE)) {
            LoadBaseline.of(result.metrics()).save(BASELINE);
            return;
        }
        result.assertNoRegression(LoadBaseline.load(BASELINE),
                Tolerance.percent(15).errorRateDelta(0.005));
    }
}
```

- `LoadBaseline.of(metrics)` takes the metrics of a finished run. It throws `IllegalArgumentException` for partial metrics: an incomplete run never becomes a baseline. For a run without any request, latency and throughput are stored as not measured.
- `save(path)` writes JSON, creating missing parent directories. It writes a temporary file and moves it into place, so a reader never sees half a file.
- `load(path)` throws `IllegalArgumentException` when the file is missing, is not a JSON object, has an unsupported `schemaVersion`, or holds an invalid value.

You do not have to save baselines in code: every run already writes its metrics in this format to `testfly-loadtest-result.json` in its run directory (see [Reporting](./reporting.md#run-directory)). Copy that file into your repository to use it as a baseline.

---

## 2. File Format

```json
{
  "schemaVersion" : 1,
  "scenarioName" : "Checkout",
  "engine" : "gatling",
  "createdAt" : "2026-10-25T10:15:30Z",
  "p95" : 120.5,
  "p99" : 180.0,
  "mean" : 60.2,
  "errorRate" : 0.0,
  "throughput" : 42.1
}
```

Latencies are in milliseconds, `errorRate` is 0.0 to 1.0 and `throughput` is requests per second. A value that was not measured is `null`; `LoadBaseline` returns it as an empty `OptionalDouble` from `p95()`, `p99()`, `mean()`, `errorRate()` and `throughput()`. Only `schemaVersion` 1 is supported.

---

## 3. Tolerance and Limits

`Tolerance.percent(p)` sets a relative tolerance for latency and throughput. `errorRateDelta(d)` sets an absolute tolerance for the error rate; the default is `0.01` (one percentage point), and the percent does not apply to it.

| Metric | Regresses when | Example: baseline, `percent(15)`, default delta |
| :--- | :--- | :--- |
| `p95`, `p99`, `mean` | actual > baseline × (1 + p/100) | p95 100 ms: fails above 115 ms |
| `throughput` | actual < baseline × (1 − p/100) | 40 req/s: fails below 34 req/s |
| `errorRate` | actual > baseline + d | 0.02: fails above 0.03 |

- The comparisons are strict, and the limits are computed exactly in decimal: with `percent(15)`, a p95 of exactly 115 ms over a 100 ms baseline passes.
- A percent of 100 or more turns the throughput check off (its limit is 0 or less). No relative tolerance can absorb a latency increase over a 0 ms baseline.
- `Tolerance.percent(..)` and `errorRateDelta(..)` throw `IllegalArgumentException` for a negative or non-finite value.

---

## 4. Results

`assertNoRegression(baseline, tolerance)` returns the `LoadTestAssert` when nothing regressed. Otherwise it throws one `AssertionError` that lists every regressed metric with its baseline, actual value and limit.

- **Not compared is never a pass.** A metric that is not measured in the baseline or in the run is reported as `not compared: not measured`. If no metric at all could be compared, the assertion fails. If it passes with some metrics not compared, a WARN lists them.
- **Partial runs** fail the assertion with `metrics are partial`.
- **Engine or scenario mismatch** only logs a WARN: the engines measure latency differently (see [Engines](./engines.md)), so compare runs of the same engine.

`regressionResults(baseline, tolerance)` returns the comparison without asserting: one `RegressionResult` per metric, in the order p95, p99, mean, errorRate, throughput. Continuing the example above, with `io.testfly.loadtest.RegressionResult` imported:

```java
LoadBaseline baseline = LoadBaseline.load(BASELINE);
for (RegressionResult r : result.regressionResults(baseline, Tolerance.percent(15))) {
    System.out.printf("%s baseline=%s actual=%s regressed=%s %s%n",
            r.metric(), r.baseline(), r.actual(), r.regressed(), r.reason());
}
```

`reason` is `null` for a metric within tolerance, the cause for a regressed one, and `not compared: not measured` (`LoadBaseline.NOT_COMPARED`) when a side was not measured; `baseline` or `actual` is then `NaN`.

In a [simulation](./simulations-sla.md), compare each scenario with `result.scenario(name).assertNoRegression(..)` and the whole run with `result.global().assertNoRegression(..)`; with more than one scenario, the run directory holds a result file for each scenario and one for the global metrics.
