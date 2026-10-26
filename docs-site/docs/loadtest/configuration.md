---
id: configuration
title: Load Testing Configuration
description: "Configure TestFly load testing defaults in testfly.yml: engine selection, virtual users, ramp up, hold durations, and reporting."
sidebar_position: 2
---

# Load Testing Configuration

TestFly provides a centralized `loadtest:` configuration block in `testfly.yml`. This allows teams to define global sensible defaults for performance runs across local and CI environments.

---

## 1. The `loadtest:` Block

Add the optional `loadtest:` section to your `testfly.yml`:

```yaml
loadtest:
  enabled: false          # reserved flag; explicit runner calls are not currently gated
  baseUrl: https://api.example.com
  engine: auto            # auto (prefers Gatling if present, else JDK) | gatling | jdk
  users: 10               # virtual users started over rampUp (not a concurrency target)
  rampUp: 10s             # window over which the users start evenly (e.g. 10s, 1m)
  hold: 30s               # how long each user loops, counted from its own start (e.g. 30s, 5m)
  cooldown: 5s            # accepted but not applied by any engine yet (logs a WARN)
  maxUsers: 1000          # safety ceiling: users is clamped; workloads above it are rejected
  resultsDir: target/loadtest # one run directory per run (metrics, reports, result file)
  reportEnabled: true     # generate standalone HTML load test report
  requestTimeoutSeconds: 30 # per-request timeout in seconds (both engines)
```

---

## 2. Configuration Reference

| Property | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `enabled` | `boolean` | `false` | Reserved effective flag (overridable via `features.loadtest`); the current `LoadTestRunner.run()` path does not enforce it. |
| `baseUrl` | `string` | `null` | Target URL after scenario/annotation overrides. Gatling falls back to `execution.baseUrl`; the JDK engine requires this value or a scenario/annotation URL. |
| `engine` | `string` | `auto` | Execution engine: `auto` (prefers Gatling if present, falls back to JDK), `gatling` (strictly Gatling), or `jdk` (native virtual threads). |
| `users` | `int` | `10` | Number of virtual users started evenly over `rampUp` if not specified in code. Each one runs the scenario in a loop for `hold`. This is not a concurrency target: peak concurrency equals `users` only when `rampUp <= hold`. |
| `rampUp` | `string` | `10s` | Window over which the virtual users start, evenly spaced (e.g. `10s`, `1m`). |
| `hold` | `string` | `30s` | How long each virtual user loops over the scenario, counted from its own start (e.g. `30s`, `5m`). A run takes about `rampUp + hold`. |
| `cooldown` | `string` | `5s` | Accepted for compatibility but **not applied** by either engine. When it is greater than zero, one WARN is logged per JVM. |
| `maxUsers` | `int` | `1000` | Safety ceiling. The resolved `users` value is clamped to it. A [workload](./fluent-api.md#workload-models) whose peak exceeds it is rejected before any request: the peak concurrency of a closed workload, or the peak arrival rate and user-count steps of an open one. A [simulation](./simulations-sla.md) checks the combined peaks. |
| `resultsDir` | `string` | `target/loadtest` | Parent directory of the run directories. Every run, on both engines, creates `<resultsDir>/<scenario-or-simulation>-<millis>/` with its result file `testfly-loadtest-result.json`; see [Reporting](./reporting.md#run-directory). TestFly does not delete old run directories. |
| `reportEnabled` | `boolean` | `true` | Generates standalone HTML load test report and correlates native Gatling interactive reports. |
| `requestTimeoutSeconds` | `int` | `30` | Per-request timeout in seconds, applied by both engines (JDK: connect and request timeout; Gatling: `requestTimeout`). A timed-out request is a failed request with status `-1`. |

:::note Workload model
`users`, `rampUp` and `hold` describe an open workload: users arrive evenly over `rampUp` and each one loops for `hold` from its own start. With `users: 10`, `rampUp: 6s` and `hold: 2s`, early users finish before late ones start, so fewer than 10 run at the same time. `cooldown` has no effect. The decision is recorded in `docs/adr/0003-load-test-workload-model.md` (ADR 0003) in the repository.

For constant concurrency, arrival rates, spikes or a ramp-down, set an explicit workload in code with `LoadScenario.workload(...)` (Gatling engine only). It is configured in code only: a scenario with a workload ignores `users`, `rampUp`, `hold` and `cooldown` from this block and from `@LoadTest`. See [Workload models](./fluent-api.md#workload-models).
:::

---

## 3. Environment Profiles & CI Overrides

TestFly profiles allow you to define lightweight parameters for local smoke testing while executing high-concurrency benchmarks in CI pipelines:

### `testfly.yml` (Local Default)
```yaml
loadtest:
  engine: auto
  users: 5
  rampUp: 2s
  hold: 5s
```

### `testfly-performance.yml` (CI Staging / Benchmark Profile)
```yaml
loadtest:
  engine: gatling
  users: 250
  rampUp: 30s
  hold: 120s
  cooldown: 10s
  reportEnabled: true
```

Activate the profile with Maven:
```bash
mvn test -Dtestfly.profile=performance
```

---

## 4. Feature Switchboard Integration

Under TestFly's master switchboard, load testing can also be toggled globally across all suites:

```yaml
features:
  loadtest: false # Completely disables load test execution across all suites
```
