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

When load tests run, the HTML report (`target/reports/testfly-report.html`) automatically displays a dedicated **⚡ Load Testing & Performance Analysis** tab.

### Visual Components:
- **KPI Summary Header:** Global requests, aggregate throughput (req/s), overall P95 latency, and system error rate.
- **Interactive Charts (Chart.js):**
  - **Latency Distribution Bar Chart:** P50, P75, P90, P95, P99, Min, and Max response times.
  - **Status Code Breakdown Doughnut Chart:** Distribution of 2xx, 4xx, and 5xx responses.
- **Scenario Cards:** Shows concurrency (VUs), duration, total requests, throughput, and error rates.
- **Step Breakdown Table:** Detailed request count, successful/failed count, P95 latency, and error rate per scenario step.
- **Direct Gatling Report Link:** Clickable button (`📊 Open Gatling Report →`) opening the full native Gatling report.

### Standalone Load Report
If `loadtest.reportEnabled: true` is configured, TestFly also generates a focused `target/reports/loadtest-report.html` highlighting only performance tests.

---

## 2. Allure Report Integration

TestFly automatically enriches Allure 2 JSON outputs in `target/allure-results/`:

- **Parameters:** Adds `Load Engine`, `Concurrent Users`, `Total Requests`, `Throughput`, `P95 Latency`, and `Error Rate`.
- **Links:** Direct custom report link pointing to the native Gatling HTML report (`📊 Gatling Interactive Report`).
- **Attachments:**
  - `⚡ Load Test Summary` (`text/markdown`): Full Markdown table of percentiles and step breakdowns.
  - `📋 Gatling Subprocess Log` (`text/plain`): Complete stdout/stderr logs from the Gatling execution run.

Generate and view your Allure report:
```bash
allure serve target/allure-results
```

---

## 3. ReportPortal Integration

When connected to ReportPortal, TestFly emits load testing data while the test item is active:

- **Markdown Summary:** Formatted table of user concurrency, throughput RPS, latency percentiles, and error rate logged at `INFO` level.
- **Subprocess Log Attachment:** Attaches `gatling-subprocess.log` as a binary artifact to the ReportPortal item.
- **Failures:** If assertions fail (e.g. `assertP95Below`), the stack trace and full metrics snapshot are attached automatically.

---

## 4. Subprocess & CI Logging

To ensure clean CI outputs without console clutter:
- Gatling's high-frequency terminal redraw output is redirected to `target/reports/loadtest/<run-id>/gatling-subprocess.log`.
- High-level progress is logged to standard output:
  ```
  [LoadTest] Starting Gatling engine (simulation: ..., results: ...)
  [LoadTest] Gatling results parsed from target/reports/loadtest/...
  ```
- If a simulation fails or crashes, the last 25 lines of the subprocess log are automatically dumped to `System.err`.
