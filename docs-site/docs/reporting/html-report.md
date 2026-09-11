---
description: "TestFly HTML report: an Allure-style self-contained SPA dashboard with cumulative suite totals, hero telemetry capsule, run history archiving, AI failure analysis, and dark mode."
id: html-report
title: Selenium HTML Report
sidebar_label: HTML Report
sidebar_position: 1
---

# HTML Report

TestFly generates an interactive, Cupertino Lab-styled single-page (SPA) HTML report after every test execution. It requires no external server, database, or network connectivity — simply open `target/testfly-report.html` directly in any modern web browser.

---

## Architecture & File Locations

The report uses a **JSON-driven architecture**. Test execution metrics are exported as structured JSON and embedded into the HTML file for 100% offline self-containment.

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

## 6-Tab Interface Architecture

The report is divided into six specialized tabs designed for rapid triage, telemetry analysis, and performance tracking:

### 1. Dashboard Tab
The mission-control view summarizing the entire execution:
- **Hero Telemetry Summary Capsule:** An Apple-style hero component featuring:
  - **Circular SVG Progress Ring:** Dynamic pass-rate percentage donut gauge with smooth rounded caps.
  - **Interactive Filter Pills:** Color-coded badges for `Passed`, `Healed`, `Failed`, and `Flaky` that instantly navigate and filter the Test Cases tab on click.
  - **Telemetry Metadata Line:** Concise monospace line: `<SuiteName> · <Duration> · <Browser> · <ExecutionMode>` (e.g. `AgenticSuite · 1.8s · chrome 126 · Thread-isolated`).
  - **Linear Progress Track:** Real-time visual progress bar.
- **Suite Totals:** Large KPI cards for Total Tests, Passed, Failed, Skipped, and Wall-clock Duration.
- **Latency Percentiles:** Tabular breakdown of Min, Mean, P50, P90, P95, and Max execution times.
- **Slowest Tests Leaderboard:** Ranked list of top latency consumers for optimization.
- **Build & CI Metadata:** Active profile, CI provider (GitHub Actions, GitLab, Jenkins), build number, branch, commit SHA, and direct build links.

:::tip Cumulative Suite Totals (Merge Runs)
When running tests sequentially across different test classes or batches, enable `reporting.mergeRuns: true` or pass `-Dtestfly.merge=true`. TestFly retains previous test results and calculates unified cumulative totals instead of overwriting them.
:::

---

### 2. Test Cases Tab
A comprehensive, hierarchical view of every executed test:
- **Grouped by Class / Feature:** Organized under expandable section headers displaying class name, total tests, and status badges.
- **Two-Tier Test Identity:** Clearly demarcates the primary scenario / method title from the secondary feature file or package path.
- **Filter Toolbar & Search:** Instant search filter (`/` hotkey) and filter pills (`All`, `Passed`, `Failed`, `Skipped`, `Flaky`, `Healed`).
- **Tabular Figures:** Fixed-width column geometry with aligned logic time and total time in milliseconds.

---

### 3. Failure Triage Tab
A dedicated workspace focused entirely on broken and failing tests:
- **Error Snippet Badges:** Telemetry error pills with monospace font and subtle red tint that truncate gracefully with ellipsis.
- **Step Execution Timeline:** Shows the exact step and latency offset where the failure occurred.
- **AI Root Cause Analysis:** Displays contextual failure analysis and remediation advice powered by Google Gemini or Claude.
- **Formatted Stack Traces:** Monospace exception trace box with a one-click **"Copy Stack Trace"** button.
- **Zero Failures State:** When all tests pass, presents an elevated green telemetry badge confirming zero defects.

---

### 4. Run History & Quality Trends Tab
Maintains a historical ledger of test runs preserved in `target/reports/`:
- **Current Run Indicator:** The active report is tagged with a distinct `CURRENT` badge and a non-duplicated action button.
- **Historical Runs:** Prior runs include pass percentage badges, total/passed/failed/skipped breakdown, duration, and a clickable `View →` link to open that specific archived run.
- **Run Switcher in Appbar:** A header dropdown allowing instant toggling between **Suite Total (All Tests)**, **Latest Run**, and historical snapshots.

---

### 5. Flakiness Radar Tab
Statistical stability analysis tracking intermittent failures across runs:
- **Risk Categorization:**
  - **HIGH (≥ 33% failure rate):** Unstable tests recommended for `@Quarantine`.
  - **WATCH (10% - 33% failure rate):** Intermittent tests requiring observation.
  - **STABLE (< 10% failure rate):** Highly dependable tests.
- **Action Column:** Direct action indicators (`Quarantine` chip vs. `Monitored` chip) and an `Inspect →` button that deep-links directly into the test case details in the Test Cases tab.

---

### 6. Load Testing & Performance Tab
*(Automatically displayed when performance or Gatling tests are executed)*:
- **Latency Distribution Chart:** Multi-color bar chart comparing Min, Mean, P50, P90, P95, P99, and Max latencies.
- **HTTP Status Codes Doughnut:** Visual breakdown of 2xx, 3xx, 4xx, and 5xx response codes.
- **Scenario Breakdown:** Virtual user concurrency, throughput (RPS), total requests, and error rate per scenario.
- **Gatling Report Link:** Direct link to the native Gatling Highcharts report.

---

## Diagnostic & Triage Capabilities

Expanding any test row opens an inline detail drawer with rich diagnostic media:

| Diagnostic Tool | Description |
|---|---|
| **Step Timeline** | Step offsets (`+45ms`), status badges (`PASS`, `INFO`, `FAIL`), and descriptions logged via `StepLogger`. |
| **API Tracing & cURL** | HTTP method, endpoint, status code, latency, and syntax-highlighted request/response JSON payloads with full cURL reproduction snippets. |
| **HTML5 Video Recording** | Embedded Base64 MP4/GIF video player with play/pause, looping, time scrubbing, and fullscreen lightbox modal. |
| **Screenshot Lightbox** | Base64-embedded failure screenshots that zoom into a high-resolution modal overlay. |
| **Self-Healing Telemetry** | Displays `HEALED` status badges and retry counts when AI/fallback locators recover broken selectors. |
| **AI Failure Analysis** | Root-cause diagnosis and actionable fix suggestions embedded directly in the triage drawer. |

---

## Design System & Color Palette

TestFly uses the **Cupertino Lab** design system (`DESIGN.md`), featuring pure pill action shapes, neutral elevation, and high-contrast typography:

| Token | Name | Hex | Purpose |
|---|---|---|---|
| `--primary` | Apple Blue | `#0071e3` | Active tabs, primary links, focus rings |
| `--good` | Telemetry Emerald | `#97cc64` | Passed tests, 2xx responses, stable risk |
| `--bad` | Telemetry Crimson | `#fd5a3e` | Failed tests, 5xx responses, quarantine risk |
| `--warn` | Telemetry Amber | `#ffb238` | Flaky tests, 4xx warnings, watch list |
| `--text-muted` | Neutral Slate | `#8c8c8c` | Skipped tests, secondary file metadata |

The interface includes a native Dark Mode / Light Mode toggle in the app bar that adapts borders, surfaces, and charts to OLED Midnight (`#000000`) or Studio Off-White (`#f5f5f7`).

---

## Configuration

Configure reporting behaviour in your [`testfly.yml`](../guides/testfly-yml-guide.md):

```yaml
reporting:
  htmlReport: true                  # generate target/testfly-report.html (default: true)
  screenshotOnFailure: true         # embed Base64 screenshot on failure (default: true)
  mergeRuns: false                  # set true or pass -Dtestfly.merge=true to merge sequential test runs
  historyRuns: 10                   # max historical run reports to keep in run switcher (default: 10)
  allure:
    enabled: false                  # export Allure 2 results to target/allure-results/
  reportPortal:
    enabled: false                  # stream real-time logs and launches to ReportPortal
```
