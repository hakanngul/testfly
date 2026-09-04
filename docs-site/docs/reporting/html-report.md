---
description: "TestFly HTML report: an Allure-style self-contained SPA dashboard with cumulative suite totals, run history archiving, AI failure analysis, and dark mode."
id: html-report
title: Selenium HTML Report
sidebar_label: HTML Report
sidebar_position: 1
---

# HTML Report

TestFly generates an interactive, Allure-style single-page HTML report after every test execution. It requires no external server or database — simply double-click and open `target/testfly-report.html` directly in any web browser.

---

## Architecture & File Locations

The report uses a **JSON-driven architecture (Allure style)**. Test execution metrics are exported as structured JSON and embedded into the HTML file for 100% offline self-containment.

```
target/
├── testfly-report.html           ← Main interactive HTML report
├── testfly-report-data.json      ← Standalone JSON data file
├── testfly-metrics.json          ← Raw execution metrics data
├── reports/
│   └── testfly-report-*.html     ← Timestamped archived historical runs
└── metrics-history/
    └── testfly-metrics-*.json    ← Historical metrics JSON snapshots
```

---

## Key Features

### 1. Cumulative Suite Totals on Dashboard
Unlike traditional test reports that only display the last single run, TestFly's dashboard prominently displays **Cumulative Suite Totals** across all tests recorded in the suite:

- **Total Tests** — Total unique test cases in the test suite
- **Total Passed** — Cumulative successful tests
- **Total Failed** — Cumulative failures requiring triage
- **Total Skipped** — Cumulative bypassed tests
- **Overall Pass Rate** — Cumulative pass percentage
- **Total Duration** — Total cumulative wall-clock execution time

:::tip Cumulative Test Merging
When running tests sequentially across different test classes or batches, enable `reporting.mergeRuns: true` or pass `-Dtestfly.merge=true`. TestFly will automatically retain and merge previous test results into a single unified report instead of overwriting them.
:::

---

### 2. Allure-Inspired Color Palette
The report interface adopts Allure's iconic QA color design:

| Status | Color | Hex | Description |
|---|---|---|---|
| **Passed** | Allure Green | `#97cc64` | Successful test execution |
| **Failed** | Allure Red | `#fd5a3e` | Assertion or unexpected failure |
| **Broken / Warning** | Allure Amber | `#ffb238` | Environment or startup error |
| **Skipped** | Allure Gray | `#8c8c8c` | Test bypassed or ignored |
| **Primary / Brand** | Allure Blue | `#1890ff` | Navigation active states & accents |

---

### 3. Run History & Interactive Switcher
Every test execution automatically archives a timestamped report in `target/reports/testfly-report-YYYYMMDD-HHmmss.html`.

- **Run Switcher Dropdown:** Located in the top header, allowing one-click switching between **Suite Total (All Tests)**, **Latest Run**, and past historical executions.
- **Run History Tab:** Displays a quality trend timeline, historical pass rates, test counts, durations, and direct links to archived reports.

---

### 4. Diagnostic & Triage Tools
Expanding any test row opens an inline detail panel equipped with:

- **Step Execution Timeline:** Shows step offsets (`+56ms`), step status (`PASS`, `INFO`, `FAIL`), and descriptions logged via `StepLogger`.
- **One-Click "Copy Stack Trace":** Instantly copies formatted exception stack traces to the clipboard.
- **AI Failure Analysis Card:** Displays root-cause explanations and actionable remediation steps when AI failure analysis is active.
- **Screenshot Lightbox:** Base64-embedded thumbnails expand into a high-resolution lightbox modal upon click.

---

## Configuration

Configure reporting behaviour in your [`testfly.yml`](../guides/testfly-yml-guide.md):

```yaml
reporting:
  mergeRuns: false                  # set true or pass -Dtestfly.merge=true to merge sequential test runs
  historyRuns: 10                   # max historical run reports to keep in run switcher (default: 10)
  allure:
    enabled: false                  # export Allure 2 results to target/allure-results/
```
