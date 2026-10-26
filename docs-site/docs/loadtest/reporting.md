---
id: reporting
title: Load Testing Reporting & Dashboards
description: "Visualize performance metrics, latency distribution charts, Gatling Highcharts reports, Allure attachments, and ReportPortal logs."
sidebar_position: 7
---

# Load Testing Reporting & Dashboards

TestFly automatically collects, correlates, and presents performance metrics across all supported reporting backends: the built-in **HTML Report**, **Allure**, and **ReportPortal**.

---

## 1. TestFly HTML Report Dashboard

When load tests run, the HTML report (`target/testfly-report.html`) automatically displays a dedicated **⚡ Load Testing & Performance Analysis** tab.

### Visual Components:
- **KPI Summary Header:** Global requests, aggregate throughput (req/s), overall P95 latency, and system error rate.
- **Interactive Charts (Chart.js):**
  - **Latency Distribution Bar Chart:** Min, Mean, P50, P90, P95, P99, and Max response times. P75 and P90 also appear as tiles on each scenario card. Both engines measure every percentile; a value that was not measured (for example a latency in a run where no request received a response) is shown as `n/a`, never as 0.
  - **Status Code Breakdown Doughnut Chart:** One slice per recorded HTTP status code. Requests without a response appear under the `-1` key, labelled "No response". If status codes were not measured, the chart shows "Status codes not measured" instead of any slice.
- **Scenario Cards:** Shows virtual users (the configured `users` value, or a workload's planned users; not measured peak concurrency), duration, total requests, throughput, and error rates. A [simulation](./simulations-sla.md) adds one card per scenario and one `<simulation> (global)` card, labelled **Aggregate**; the summary KPIs count only the scenario cards.
  - **Card status:** FAILED when any [SLA](./simulations-sla.md) rule failed. A [partial](./engines.md#4-gatling-run-lifecycle) run shows a **PARTIAL** badge; its card status is WARN unless it is FAILED (KO requests or a failed SLA rule; on a partial run every SLA rule fails).
  - **SLA table:** one row per SLA rule with Scope, Metric, Comparison, Threshold, Actual (`n/a` when the metric was not measured), Result (PASS or FAIL) and Reason.
- **SLA summary:** the summary shows how many scenarios have a failed SLA. Aggregate (`(global)`) entries are not counted, so a failed simulation-level SLA shows only on the Aggregate card (status FAILED).
- **Step Breakdown Table:** Detailed request count, successful/failed count, P95 and P99 latency, and error rate per scenario step.
- **Direct Gatling Report Link:** Clickable button (`📊 Open Gatling Report →`) opening the full native Gatling report.

### Standalone Load Report
If `loadtest.reportEnabled: true` is configured, TestFly also generates a focused `target/loadtest-report.html` highlighting only performance tests.

---

## 2. Allure Report Integration

TestFly automatically enriches Allure 2 JSON outputs in `target/allure-results/`:

- **Parameters:** Adds `Load Engine`, `Virtual Users`, `Total Requests`, `Throughput`, `P95 Latency`, and `Error Rate`.
- **Links:** Direct custom report link pointing to the native Gatling HTML report (`📊 Gatling Interactive Report`).
- **Attachments:**
  - `⚡ Load Test Summary` (`text/markdown`): Full Markdown table of percentiles and step breakdowns. Unmeasured P75/P90 values are shown as `n/a`. It also has a `Partial` line and, when the run has SLA rules, an SLA table.
  - `📋 Gatling Subprocess Log` (`text/plain`): Complete stdout/stderr logs from the Gatling execution run.

Generate and view your Allure report:
```bash
allure serve target/allure-results
```

---

## 3. ReportPortal Integration

When connected to ReportPortal, TestFly emits load testing data while the test item is active:

- **Markdown Summary:** Formatted table of virtual users, throughput RPS, latency percentiles, and error rate logged at `INFO` level, with a `Partial` line and, when the run has SLA rules, an SLA table.
- **Subprocess Log Attachment:** Attaches `gatling-subprocess.log` as a binary artifact to the ReportPortal item.
- **Failures:** If assertions fail (e.g. `assertP95Below`), the stack trace and full metrics snapshot are attached automatically.

---

## 4. Subprocess & CI Logging

To ensure clean CI outputs without console clutter:
- Gatling's high-frequency terminal redraw output is redirected to `target/loadtest/<run-id>/gatling-subprocess.log` by default (`loadtest.resultsDir` controls the parent directory).
- High-level progress is logged to standard output:
  ```
  [LoadTest] Starting Gatling engine: <scenario> (<users> users, rampUp=..., hold=...)
  [LoadTest] Gatling execution complete. Parsing results...
  [LoadTest] Gatling results parsed from: target/loadtest/...
  ```
- TestFly's metrics file `testfly-metrics.json` and the result file `testfly-loadtest-result.json` are written to the same run directory (see [section 5](#run-directory)).
- If the Gatling process exits with a non-zero code, the run fails with an exception that contains the root error lines of the subprocess log (such as the exception message) followed by its last 20 lines, with credentials masked as `***`. Raw log lines are no longer printed to `System.err`. Two exceptions: exit code 2 when an SLA was registered as native assertions, and a run in which no request could be sent (all requests KO with status `-1`). See [Gatling Run Lifecycle](./engines.md#4-gatling-run-lifecycle).

---

## 5. Run Directory and Result Files {#run-directory}

Every run creates its own directory, `<resultsDir>/<name>-<millis>/` (by default `target/loadtest/<run-id>/`), where `<name>` is the scenario or simulation name with every character other than letters, digits, `_` and `-` replaced by `_`. JDK runs get one too.

| File | Engine | Content |
| :--- | :--- | :--- |
| `testfly-metrics.json` | Gatling | TestFly's own metrics, recorded inside the Gatling run: per request name the attempts, OK and KO counts, status codes and a millisecond latency histogram. `LoadTestMetrics` is built from this file only. |
| `testfly-loadtest-result.json` | Both | The run's key metrics in the [baseline](./baselines.md) format. In a simulation with more than one scenario it holds the global metrics. |
| `testfly-loadtest-result-<scenario>.json` | Gatling (simulation with several scenarios) | One result file per scenario. If two scenario names give the same file name, the later one gets `-2`, `-3`, … and a WARN is logged. |
| `gatling-subprocess.log` and Gatling's HTML report | Gatling | The child JVM's output and Gatling's own report. |

The result file is written on a best-effort basis: a write failure logs a WARN and never fails the run, and a [partial](./engines.md#4-gatling-run-lifecycle) run writes none. To use a run as a baseline, copy its `testfly-loadtest-result.json` into your repository.

:::caution Disk usage
TestFly never deletes run directories. Each run adds one small directory (a JDK run only its result file; a Gatling run also the Gatling report and log), so `resultsDir` grows with every run. Clean it in CI, or keep it under `target/` so that `mvn clean` removes it.
:::

The suite-level `target/testfly-metrics.json`, which feeds the HTML report, is a different file with the same name. Its load-test entries also carry `partial`, `aggregate` (`true` for a simulation's global entry) and `sla`: one entry per SLA rule with `scope`, `metric`, `comparison`, `threshold`, `actual`, `passed` and `reason`. The HTML dashboard shows these fields on the scenario cards (see [section 1](#1-testfly-html-report-dashboard)); in code, read SLA results with `slaResults()`. Regression results against a [baseline](./baselines.md) are not shown in any report.

### Percentiles: TestFly vs. Gatling's report {#percentiles}

On Gatling, TestFly computes p50, p75, p90, p95 and p99 exactly from its millisecond histogram, with linear interpolation (the method the JDK engine uses), over requests that received a response. Gatling's HTML report estimates percentiles with a t-digest, so its values can differ from TestFly's, most visibly for high percentiles on small samples. Request totals can differ too: TestFly counts a request that could not be built as KO, while Gatling's report leaves it out. TestFly's figures are the ones used by assertions, SLAs, baselines and TestFly's reports. When TestFly's SLA verdict and Gatling's native assertions disagree on the same rules, a WARN names both.
