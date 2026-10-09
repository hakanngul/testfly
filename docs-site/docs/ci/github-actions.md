---
description: "Run Selenium tests in GitHub Actions: a copy-paste workflow that installs Chrome, runs the suite headless, and uploads the HTML report on every push."
id: github-actions
title: GitHub Actions
sidebar_position: 1
---

# GitHub Actions

Run your TestFly tests on every push and pull request. The workflow below installs Chrome, runs the suite, and uploads the HTML report as a downloadable artifact.

---

## Basic workflow

```yaml title=".github/workflows/test.yml"
name: Selenium Tests

on:
  push:
    branches: [main, master]
  pull_request:

permissions:
  contents: read

jobs:
  test:
    runs-on: ubuntu-latest
    timeout-minutes: 30

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven

      - name: Install Chrome
        uses: browser-actions/setup-chrome@v1

      - name: Run tests
        run: mvn test -B

      - name: Upload HTML report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: testfly-report
          path: target/testfly-report.html
```

`permissions: contents: read` keeps the workflow token least-privileged, and `timeout-minutes` stops a hung browser session from consuming runner minutes for the default six hours.

---

## Headless Chrome

CI runners have no display, so browsers must run headless. TestFly detects CI automatically (GitHub Actions sets `GITHUB_ACTIONS=true` and `CI=true`) and forces `browser.headless=true` at startup — the log shows `[TestFly] CI override: browser.headless=true`. No extra environment variable is needed.

You can still set it explicitly in `testfly.yml`:

```yaml title="testfly.yml"
browser:
  name: chrome
  headless: true
```

:::note Environment overrides
TestFly does not read ad-hoc variables such as `SELENIUM_HEADLESS` or `-Dbrowser.name`. Environment values reach the config only through `${VAR}` / `${VAR:-default}` placeholders in `testfly.yml`, and placeholders are resolved for **string** fields only (for example `browser.name`, `execution.baseUrl`). Boolean and numeric fields such as `headless` or `threadCount` must be literal values — use a profile file (`-Dtestfly.profile=ci` → `testfly-ci.yml`) when they must differ per environment.
:::

```yaml title="testfly.yml"
browser:
  name: ${TESTFLY_BROWSER:-chrome}
```

---

## Publish JUnit XML test results

Publishing a check run needs `checks: write`. Grant it at job level so other jobs keep the read-only default:

```yaml
jobs:
  test:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    permissions:
      contents: read
      checks: write

    steps:
      # ... checkout, setup-java, run tests ...

      - name: Publish test results
        uses: dorny/test-reporter@v1
        if: always()
        with:
          name: Test Results
          path: '**/surefire-reports/TEST-*.xml'
          reporter: java-junit
          fail-on-empty: false
```

This renders pass/fail counts directly in the GitHub Actions summary and PR checks. For pull requests from forks the token is read-only regardless of `permissions`, so the publish step cannot create a check there.

---

## Matrix — multiple browsers

Use the `${TESTFLY_BROWSER:-chrome}` placeholder shown above; environment variables do not override browser fields unless referenced in YAML.

```yaml
jobs:
  test:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    strategy:
      fail-fast: false
      matrix:
        browser: [chrome, firefox]

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven

      - name: Install Chrome
        if: matrix.browser == 'chrome'
        uses: browser-actions/setup-chrome@v1

      - name: Install Firefox
        if: matrix.browser == 'firefox'
        uses: browser-actions/setup-firefox@v1

      - name: Run tests
        run: mvn test -B
        env:
          TESTFLY_BROWSER: ${{ matrix.browser }}

      - name: Upload report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: report-${{ matrix.browser }}
          path: target/testfly-report.html
```

---

## Caching Maven dependencies

The `cache: maven` option in `setup-java` caches `~/.m2/repository` automatically. This significantly reduces build time on subsequent runs.

---

## Full example with parallel tests

```yaml title="testfly.yml"
execution:
  mode: local
  baseUrl: https://example.com
  parallel: methods
  threadCount: 4
```

Commit these parallel settings in `testfly.yml` — the CI runner picks them up automatically. If you leave `threadCount` at its default of `1` (and `parallel` is not `none`), TestFly sizes it from the runner's CPU cores when CI is detected.
