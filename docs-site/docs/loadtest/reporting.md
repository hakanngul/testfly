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
  - **Latency Distribution Bar Chart:** Min, Mean, P50, P90, P95, P99, and Max response times. P75 and P90 also appear as tiles on each scenario card. A percentile the engine did not measure (P90 on Gatling) is shown as `n/a`, never as 0.
  - **Status Code Breakdown Doughnut Chart:** One slice per recorded HTTP status code. Requests without a response appear under the `-1` key, labelled "No response". If status codes were not measured, the chart shows "Status codes not measured" instead of any slice.
- **Scenario Cards:** Shows virtual users (the configured `users` value, not measured peak concurrency), duration, total requests, throughput, and error rates.
- **Step Breakdown Table:** Detailed request count, successful/failed count, P95 latency, and error rate per scenario step.
- **Direct Gatling Report Link:** Clickable button (`📊 Open Gatling Report →`) opening the full native Gatling report.

### Standalone Load Report
If `loadtest.reportEnabled: true` is configured, TestFly also generates a focused `target/loadtest-report.html` highlighting only performance tests.

---

## 2. Allure Report Integration

TestFly automatically enriches Allure 2 JSON outputs in `target/allure-results/`:

- **Parameters:** Adds `Load Engine`, `Concurrent Users`, `Total Requests`, `Throughput`, `P95 Latency`, and `Error Rate`.
- **Links:** Direct custom report link pointing to the native Gatling HTML report (`📊 Gatling Interactive Report`).
- **Attachments:**
  - `⚡ Load Test Summary` (`text/markdown`): Full Markdown table of percentiles and step breakdowns. Unmeasured P75/P90 values are shown as `n/a`.
  - `📋 Gatling Subprocess Log` (`text/plain`): Complete stdout/stderr logs from the Gatling execution run.

Generate and view your Allure report:
```bash
allure serve target/allure-results
```

---

## 3. ReportPortal Integration

When connected to ReportPortal, TestFly emits load testing data while the test item is active:

- **Markdown Summary:** Formatted table of virtual users, throughput RPS, latency percentiles, and error rate logged at `INFO` level.
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
- The status-code file `testfly-status.json` is written to the same run directory.
- If the Gatling process exits with a non-zero code, the run fails with an exception that contains the last 20 lines of the subprocess log, with credentials masked as `***`. Raw log lines are no longer printed to `System.err`. See [Gatling Run Lifecycle](./engines.md#4-gatling-run-lifecycle).
