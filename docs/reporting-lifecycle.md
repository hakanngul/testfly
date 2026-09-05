# TestFly – Reporting Lifecycle Specification

This document defines the **reporting lifecycle contract** for TestFly.
It specifies reporting events, generated artifacts, and final outputs.

The reporting system is designed to provide **maximum observability** without affecting test execution behavior.

---

## Design Goals

The reporting system aims to:

- Provide transparent, real-time execution visibility
- Capture failure evidence (screenshots, DOM snapshots, network traces) automatically
- Produce a single, self-contained interactive HTML report for easy CI artifact sharing
- Support standard machine-readable formats (JSON metrics export, Allure, ReportPortal)
- Scale gracefully for high-concurrency, parallel test suites

---

## Reporting Architecture Overview

```
Test Execution (TestNG / JUnit 5 / Cucumber)
    │
    ├── Step Events (@Step, StepLogger, ApiLogger)
    ├── Evidence Capture (ScreenshotOnFailure, DOM snapshot)
    └── Session Metadata (Timing, Flakiness, Browser metrics)
            │
            ▼
    Report Aggregator & Model
            │
            ├──► target/testfly-report.html (Self-contained interactive dashboard)
            ├──► target/testfly-metrics.json (CI / machine-readable summary)
            └──► Third-Party Adapters (Allure, ReportPortal)
```

---

## Reporting Events

### 1. Suite Execution Start
Triggered once per test run:
- Captures environment details, OS, Java version, and browser capabilities.
- Initializes the run timeline and execution timer.

### 2. Test Start
Triggered before each test method / scenario:
- Binds test metadata (class, method, thread ID, description).
- Initializes per-test step and action loggers.

### 3. Step Events
Triggered during test execution:
- UI actions (`click`, `fill`, `hover`) and explicit step annotations (`@Step`).
- REST API calls (endpoint, status code, response time, request/response headers, cURL commands).

### 4. Test Completion (Pass / Fail / Skip)
- **On Pass**: Records duration and steps; cleans up temporary resources.
- **On Failure**: Captures immediate full-page screenshot (Base64 or PNG file) and failure stack trace.
- **On Retry**: Logs previous attempts in the test's retry history tab.

### 5. Suite Completion
- Compiles the final interactive HTML report.
- Writes structured execution metrics to `target/testfly-metrics.json`.

---

## Generated Artifacts

### 1. `target/testfly-report.html` (Primary Report)
A zero-dependency, self-contained interactive report that opens directly in any browser without needing a web server:
- **Executive Summary**: Pass/fail/skip rates, duration, and thread utilization.
- **Timeline Tab**: Visual timeline chart showing parallel thread execution.
- **Flakiness Radar**: Identifies flaky tests that succeeded only after retries.
- **Step-by-Step Breakdown**: Hierarchical test view with embedded screenshots and cURL reproductions.
- **Theme**: Built-in Dark Mode and Light Mode with responsive design.

### 2. `target/testfly-metrics.json`
Structured JSON report for CI/CD gates and trend tracking dashboards.

---

## Retry Reporting Guarantees

- Every retry attempt is explicitly recorded with its individual duration and failure reason.
- Retries are aggregated under a single test entry with a clear retry counter.
- Tests that pass on retry are flagged as **Flaky** in the Flakiness Radar to alert developers of intermittent stability issues.

---

## Parallel Execution Guarantees

- All reporting events and loggers use `ThreadLocal` context tracking.
- Artifact generation is completely lock-free and thread-safe.
- Concurrent tests running across multiple worker threads never overwrite each other's evidence.
