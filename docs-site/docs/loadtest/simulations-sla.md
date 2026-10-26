---
id: simulations-sla
title: Simulations and SLAs
description: "Run several load scenarios together as one Gatling simulation, and declare service-level rules that TestFly evaluates on both engines."
sidebar_position: 6.1
---

# Simulations and SLAs

A **simulation** runs several scenarios at the same time, each with its own load shape, as one Gatling run. An **SLA** is a set of rules on latency, error rate and throughput that TestFly checks when a run ends. SLAs work on both engines; simulations need Gatling.

---

## 1. Simulations (Gatling only) {#simulations}

Start a simulation with `loadSimulation(name)`, add scenarios with `scenario(...)` and call `run()`. It returns a `LoadSimulationResult`.

```java
import io.testfly.loadtest.BaseLoadTest;
import io.testfly.loadtest.ClosedStep;
import io.testfly.loadtest.LoadSimulationResult;
import io.testfly.loadtest.LoadTestMetrics;
import io.testfly.loadtest.OpenStep;
import io.testfly.loadtest.Workload;
import org.testng.annotations.Test;
import java.time.Duration;
import java.util.Map;

public class ShopSimulationTest extends BaseLoadTest {
    @Test
    public void shop() {
        LoadSimulationResult result = loadSimulation("Shop")
            .baseUrl("https://shop.example.com")
            .scenario(loadScenario("Browse")
                .workload(Workload.closed(ClosedStep.constantConcurrent(20, Duration.ofMinutes(2))))
                .step("home").get("/").and()
                .step("product").get("/products/42").and())
            .scenario(loadScenario("Buy")
                .workload(Workload.open(OpenStep.constantRate(2, Duration.ofMinutes(2))))
                .sla(sla().p95Below(800).step("pay").errorRateBelow(0.01))
                .step("pay").post("/pay").body(Map.of("amount", 10)).check(status().is(201)).and())
            .sla(sla().errorRateBelow(0.02).step("Browse :: product").p99Below(1500))
            .run();

        result.global().assertThroughputAbove(5);
        result.scenario("Browse").assertStepP95Below("product", 400);
        result.global().assertStepP95Below("Browse :: product", 400);

        LoadTestMetrics buy = result.scenarioMetrics("Buy");
        System.out.println(result.scenarioNames() + ": Buy p90 = " + buy.p90LatencyMs() + " ms");
    }
}
```

| `LoadSimulationResult` method | Returns |
| :--- | :--- |
| `global()` | `LoadTestAssert` on all scenarios together |
| `scenario(name)` | `LoadTestAssert` on one scenario |
| `globalMetrics()`, `scenarioMetrics(name)` | The `LoadTestMetrics` behind them |
| `scenarioNames()` | Scenario names in the order they were added |
| `slaResults()` | Every SLA result: the simulation's rules first, then each scenario's rules in scenario order |

`scenario(name)` and `scenarioMetrics(name)` throw `IllegalArgumentException` for an unknown name. The flat `slaResults()` list does not say which scenario a rule belongs to; use `globalMetrics().slaResults()` and `scenarioMetrics(name).slaResults()` for that.

**How the results are built**

- Each scenario keeps its own workload or `users/rampUp/hold` shape, steps, feeder and think time. A scenario's own `baseUrl` wins over the simulation's `baseUrl(...)`, which wins over the configured one.
- With more than one scenario, request names become `"<scenario> :: <step>"`. The global metrics use these names as step keys; each scenario's own metrics keep the plain step names. With a single scenario the names are not prefixed.
- A scenario's throughput is its request count divided by the duration of the **whole** run, so the scenario throughputs add up to the global one.
- The global metrics are named `"<simulation> (global)"`. Each scenario and the global entry are recorded for the reports, and `lastLoadMetrics()` returns the global metrics.
- A scenario is partial on its own when some of its requests have no recorded outcome. When Gatling's `maxDuration` stops the run, every scenario and the global metrics are partial.

**Validation before any request is sent** (`IllegalArgumentException`):

- The resolved engine must be Gatling. `engine("jdk")`, or `auto` without Gatling on the classpath, is rejected. A scenario that sets an engine other than the simulation's (or `auto`) is rejected.
- At least one scenario. Scenario names must be non-blank and unique, must not contain `#{`, `${` or `" :: "`, and must not end with `" ::"`.
- Each scenario is validated as on its own (steps, checks, workload rules).
- `loadtest.maxUsers` caps the **sum** of the closed peaks (a `users/rampUp/hold` scenario counts its users) and, separately, the sum of the open peak arrival rates. A scenario whose own `users(..)` exceeds `maxUsers` is rejected here instead of being clamped as a single `run()` does, so the combined load is never silently reduced.
- A bare step name in the simulation's SLA must exist in only one scenario (see below).

---

## 2. SLAs {#slas}

Build an SLA with `sla()` (on `BaseLoadTest`, or `LoadSla.sla()`) and attach it with `sla(...)` to a scenario or a simulation. Global rules come first; `step(name)` starts rules on one step, and each step rule returns the SLA, so the chain continues.

```java
loadScenario("Checkout")
    .users(20).rampUp(Duration.ofSeconds(10)).hold(Duration.ofSeconds(30))
    .sla(sla().p95Below(300).errorRateBelow(0.01)
              .step("Pay").p99Below(800))
    .step("Cart").get("/cart").and()
    .step("Pay").post("/pay").body(Map.of("amount", 10)).check(status().is(201)).and()
    .run();
```

| Scope | Rules |
| :--- | :--- |
| Global (`LoadSla`) | `p50Below`, `p75Below`, `p90Below`, `p95Below`, `p99Below`, `meanBelow`, `maxBelow` (ms), `errorRateBelow` (0.0 to 1.0), `throughputAbove` (requests/s) |
| Step (`StepSla`, from `step(name)`) | `p95Below`, `p99Below` (ms), `errorRateBelow` (0.0 to 1.0) |

**Evaluation**

- TestFly evaluates the rules from `LoadTestMetrics`, the same way on both engines. The verdict never depends on Gatling's output.
- Comparisons are strict: `xBelow(t)` passes only when the value is below `t`, `throughputAbove(t)` only when it is above `t`.
- A rule fails with one of these reasons, checked in this order: `metrics are partial` (the run is [partial](./engines.md#4-gatling-run-lifecycle)), `step not found` (no step with that name in the metrics), `not measured` (the value is `NaN`), or a comparison such as `250.0 is not below 200.0`.
- `run()` first records the run, with its SLA results, for the reports and `lastLoadMetrics()`. Then, if any rule failed, it throws one `AssertionError` that lists every failed rule with its scope, metric, threshold, actual value and reason.
- Invalid thresholds throw `IllegalArgumentException` when the rule is declared: a non-finite value, or an error rate outside 0.0 to 1.0. `step("global")` and a blank step name are rejected too.
- `rules()` returns a snapshot, and a run evaluates the rules it saw when it started.

To log the results before the test fails, catch the error and read `lastLoadMetrics()` (a fragment inside a `BaseLoadTest` test method, with `io.testfly.loadtest.SlaResult` imported):

```java
try {
    loadScenario("Checkout")
        .users(20)
        .sla(sla().p95Below(300).throughputAbove(50))
        .step("Cart").get("/cart").and()
        .run();
} catch (AssertionError e) {
    for (SlaResult r : lastLoadMetrics().slaResults()) {
        System.out.println(r.rule().scope() + " " + r.rule().metric() + " " + r.rule().threshold()
                + " actual=" + r.actual() + " passed=" + r.passed() + " " + r.reason());
    }
    throw e;
}
```

When the SLA passes, `run()` returns the `LoadTestAssert` as usual, and its `slaResults()` and `assertSlaPassed()` are available. For the differences from `assertP95Below` and the other assertions, see [SLAs or assertions?](./assertions.md#slas-or-assertions).

### SLAs in a simulation

- **Scenario SLA** (`loadScenario(..).sla(..)`): evaluated against that scenario's own metrics, with plain step names (`"pay"`).
- **Simulation SLA** (`loadSimulation(..).sla(..)`): evaluated against the global metrics. Name a step as `"<scenario> :: <step>"`, or by its bare name when only one scenario has a step with that name. A bare name that exists in several scenarios fails validation before the run. A step name that exists nowhere fails at evaluation with `step not found`.
- If any SLA fails, `run()` throws one `AssertionError` that groups the failed rules per scenario and for the global metrics.

### Native assertions in Gatling's report

On Gatling, TestFly also registers the rules as Gatling assertions, so they appear in Gatling's HTML report. Gatling then exits with code 2 when one of them fails; TestFly treats that code as "assertions failed", not as a crash. TestFly's verdict is the one that counts: when it disagrees with Gatling's on the same rules, a WARN explains that TestFly uses exact percentiles from its own histogram while Gatling estimates them (see [Reporting](./reporting.md#percentiles)). A scenario's global rules in a simulation with several scenarios, and rules on steps that the run does not have, have no native counterpart and are evaluated by TestFly only.
