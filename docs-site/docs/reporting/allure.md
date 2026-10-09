---
description: "Generate Allure 2-compatible test reports with TestFly with zero external agent configuration."
id: allure
title: Allure Report Integration
sidebar_position: 4
---

# Allure Report Integration

TestFly provides a built-in **`AllureReportAdapter`** that automatically writes Allure 2-compatible JSON result files directly to `target/allure-results/`.

Unlike traditional setups that require bulky Maven/Gradle agents or complex aspect weavers, TestFly converts its internal execution metrics, steps, and failure screenshots into Allure JSON format seamlessly at suite completion.

---

## Enabling Allure in `testfly.yml`

Enable Allure reporting with a simple toggle in your `testfly.yml`:

```yaml
reporting:
  allure:
    enabled: true
```

When enabled, TestFly registers the built-in adapter and generates one `*-result.json` per test upon suite finish. The output path is fixed at `target/allure-results/`; `reporting.allure.resultsDir` is not a supported setting. Exporting JSON needs no Allure agent dependency; rendering HTML requires the separately installed Allure CLI.

---

## What Gets Captured

- **Test Metadata:** Test name, full name (`Class#method`) and the labels `suite`, `testClass`, `thread`, `framework` (`testng`) and `language` (`java`), plus `browser` when known and `flaky` when the test was retried.
- **Timing:** `start` / `stop` epoch timestamps are synthesized from the suite end time (`stop` = suite end, `start` = `stop` − test duration). They are not the real wall-clock times of each test.
- **Execution Status:** 
  - `PASSED` / `PASS` / `INFO` → `passed`
  - `FAILED` / `FAIL` → `failed` (including full exception message and stack trace)
  - `SKIPPED` → `skipped`
  - `WARN` / other → `broken`
- **Step Timeline:** Steps logged via `StepLogger` (`step("...")`) are formatted as Allure nested steps with status and duration.
- **Attachments & Screenshots:** Failure screenshots taken by `ScreenshotManager` are decoded and saved as `.png` attachments linked to the failing step.

---

## Viewing the Report

After your test suite finishes, use the Allure CLI to view or build the HTML report:

```bash
# Serve live interactive report in your browser
allure serve target/allure-results

# Or build static HTML report files in target/allure-report
allure generate target/allure-results -o target/allure-report --clean
allure open target/allure-report
```

---

## CI/CD Pipeline Integration

### GitHub Actions

In GitHub Actions, you can archive or publish the Allure report using standard actions:

```yaml
- name: Run TestFly Tests
  run: mvn test

- name: Get Allure history
  uses: actions/checkout@v4
  if: always()
  continue-on-error: true
  with:
    ref: gh-pages
    path: gh-pages

- name: Allure Report action
  uses: simple-elf/allure-report-action@master
  if: always()
  with:
    allure_results: target/allure-results
    allure_history: allure-history

- name: Deploy report to GitHub Pages
  if: always()
  uses: peaceiris/actions-gh-pages@v3
  with:
    github_token: ${{ secrets.GITHUB_TOKEN }}
    publish_branch: gh-pages
    publish_dir: allure-history
```
