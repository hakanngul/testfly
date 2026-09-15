---
id: configuration
title: Load Testing Configuration
description: "Configure TestFly load testing defaults in testfly.yml: engine selection, concurrent users, ramp up, hold durations, and reporting."
sidebar_position: 2
---

# Load Testing Configuration

TestFly provides a centralized `loadtest:` configuration block in `testfly.yml`. This allows teams to define global sensible defaults for performance runs across local and CI environments.

---

## 1. The `loadtest:` Block

Add the optional `loadtest:` section to your `testfly.yml`:

```yaml
loadtest:
  enabled: false          # enable load testing (can also be toggled via features.loadtest)
  baseUrl: https://api.example.com
  engine: auto            # auto (prefers Gatling if present, else JDK) | gatling | jdk
  users: 10               # default concurrent virtual users
  rampUp: 10s             # linear ramp-up duration (e.g. 10s, 1m)
  hold: 30s               # peak load sustain duration (e.g. 30s, 5m)
  cooldown: 5s            # cooldown period after test run (e.g. 5s)
  maxUsers: 1000          # safety ceiling on concurrent users
  resultsDir: target/loadtest # target directory for metrics and reports
  reportEnabled: true     # generate standalone HTML load test report
  requestTimeoutSeconds: 30 # HTTP connection and request read timeout in seconds
```

---

## 2. Configuration Reference

| Property | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `enabled` | `boolean` | `false` | Enables or disables load testing execution (can be overridden via `features.loadtest`). |
| `baseUrl` | `string` | `null` | Target base URL for load tests (falls back to test-defined endpoint or `execution.baseUrl`). |
| `engine` | `string` | `auto` | Execution engine: `auto` (prefers Gatling if present, falls back to JDK), `gatling` (strictly Gatling), or `jdk` (native virtual threads). |
| `users` | `int` | `10` | Default number of concurrent virtual users if not specified in code. |
| `rampUp` | `string` | `10s` | Linear ramp-up period to reach peak virtual users (e.g. `10s`, `1m`). |
| `hold` | `string` | `30s` | Duration to sustain peak virtual users (e.g. `30s`, `5m`). |
| `cooldown` | `string` | `5s` | Cooldown period after peak load finishes (e.g. `5s`). |
| `maxUsers` | `int` | `1000` | Safety ceiling for maximum allowed concurrent users. |
| `resultsDir` | `string` | `target/loadtest` | Output directory where load test metrics and reports are saved. |
| `reportEnabled` | `boolean` | `true` | Generates standalone HTML load test report and correlates native Gatling interactive reports. |
| `requestTimeoutSeconds` | `int` | `30` | HTTP connection and request read timeout in seconds. |

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
