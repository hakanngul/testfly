---
description: "Run TestFly tests in Bitbucket Pipelines: complete configuration with headless browser setup, quality gates, and report artifact archiving."
id: bitbucket-pipelines
title: Bitbucket Pipelines
sidebar_position: 3
---

# Bitbucket Pipelines

Run your TestFly test suite in **Bitbucket Pipelines** on every commit and pull request. 

TestFly's built-in **`CiEnvironmentDetector`** automatically recognizes the Bitbucket environment (`BITBUCKET_BUILD_NUMBER`, `BITBUCKET_BRANCH`, `BITBUCKET_COMMIT`) and stamps execution metadata directly into the HTML timeline report.

---

## Basic Configuration (`bitbucket-pipelines.yml`)

Create `bitbucket-pipelines.yml` in your repository root:

```yaml title="bitbucket-pipelines.yml"
image: maven:3.9.6-eclipse-temurin-17

definitions:
  caches:
    maven: ~/.m2/repository

pipelines:
  default:
    - step:
        name: Run TestFly Test Suite
        caches:
          - maven
        script:
          # 1. Install Google Chrome for headless execution
          - apt-get update && apt-get install -y wget gnupg
          - wget -q -O - https://dl-ssl.google.com/linux/linux_signing_key.pub | apt-key add -
          - sh -c 'echo "deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main" >> /etc/apt/sources.list.d/google.list'
          - apt-get update && apt-get install -y google-chrome-stable

          # 2. Run TestFly test suite
          - mvn test -B -Dtestfly.browser.headless=true

        # 3. Archive TestFly HTML report & artifacts
        artifacts:
          - target/testfly-reports/**
          - target/surefire-reports/**
```

---

## Enforcing Quality Gates in CI

Use TestFly's built-in **`BuildThresholdEnforcer`** to fail the Bitbucket pipeline if the pass rate drops or flaky test count exceeds thresholds:

```yaml title="testfly.yml"
ci:
  failOnPassRateBelow: 95.0
  maxFlakyTests: 2
```

When a build fails due to threshold violations, TestFly prints a concise diagnostic banner in the Bitbucket Pipelines console log and halts with an exit code of 1.

---

## Bitbucket Test Results Tab

Bitbucket automatically parses JUnit XML files located under `target/surefire-reports/` and displays test outcomes in the native **Test Results** tab of the pipeline run.
