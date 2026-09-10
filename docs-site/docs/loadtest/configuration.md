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
  enabled: true
  engine: auto            # auto | gatling | jdk
  defaultUsers: 10
  defaultRampUpSeconds: 5
  defaultDurationSeconds: 15
  targetRps: 100
  timeoutSeconds: 30
  reportEnabled: true
  feeder:
    path: "src/test/resources/data/users.csv"
    format: csv           # csv | json
```

---

## 2. Configuration Reference

| Property | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `enabled` | `boolean` | `true` | Enables or disables load testing execution. |
| `engine` | `string` | `auto` | Execution engine: `auto` (prefers Gatling if present, falls back to JDK), `gatling` (strictly Gatling), or `jdk` (lightweight virtual threads). |
| `defaultUsers` | `int` | `1` | Default number of concurrent virtual users if not specified in code. |
| `defaultRampUpSeconds` | `int` | `0` | Default linear ramp-up period in seconds. |
| `defaultDurationSeconds`| `int` | `10`| Default duration to sustain peak virtual users. |
| `targetRps` | `int` | `0` | Optional global request throttling target. `0` indicates unthrottled maximum throughput. |
| `timeoutSeconds` | `int` | `30`| HTTP connection and request read timeout in seconds. |
| `reportEnabled` | `boolean` | `true` | Generates standalone `loadtest-report.html` and correlates native Gatling interactive reports. |
| `feeder.path` | `string` | `null` | Optional default file path for data-driven feeding (CSV or JSON). |
| `feeder.format` | `string` | `csv` | File format parser for the default feeder (`csv` or `json`). |

---

## 3. Environment Profiles & CI Overrides

TestFly profiles allow you to define lightweight parameters for local smoke testing while executing high-concurrency benchmarks in CI pipelines:

### `testfly.yml` (Local Default)
```yaml
loadtest:
  engine: auto
  defaultUsers: 5
  defaultDurationSeconds: 5
```

### `testfly-performance.yml` (CI Staging / Benchmark Profile)
```yaml
loadtest:
  engine: gatling
  defaultUsers: 250
  defaultRampUpSeconds: 30
  defaultDurationSeconds: 120
  reportEnabled: true
```

Activate the profile with Maven:
```bash
mvn test -Dtestfly.profile=performance
```

---

## 4. Feature Switchboard Integration

Under TestFly's master switchboard, load testing can also be toggled globally:

```yaml
features:
  loadtest: false # Completely disables load test execution across all suites
```
