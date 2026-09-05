---
description: "A practical guide to the testfly.yml file used to configure TestFly tests, based on the example config in src/test/resources/testfly.yml."
id: testfly-yml-guide
title: testfly.yml Guide
sidebar_position: 2
---

# `testfly.yml` Guide

`testfly.yml` is the single configuration file that controls how TestFly runs your tests. The copy under `src/test/resources/testfly.yml` is the framework's own example configuration and is also used when running the example tests in this repository.

---

## Where the file lives

TestFly resolves the config in this order:

1. **System property** — `-Dtestfly.config=/path/to/custom.yml`
2. **Working directory** — `./testfly.yml` (next to `pom.xml` or `build.gradle`)
3. **Classpath** — `src/test/resources/testfly.yml`

For a consumer project, put `testfly.yml` at the project root. Inside the TestFly framework itself, the example config lives in `src/test/resources/testfly.yml` so it is available on the test classpath.

---

## Minimum required config

The smallest file that will start a test is:

```yaml
execution:
  mode: local
  baseUrl: https://example.com

browser:
  name: chrome

timeouts:
  explicit: 10
  pageLoad: 30
```

Everything else is optional.

---

## Annotated example (`src/test/resources/testfly.yml`)

```yaml
browser:
  name: chrome
  headless: true
  arguments:
    - --start-maximized
    - --disable-notifications
    - --remote-allow-origins=*
  capabilities:
    acceptInsecureCerts: true
    pageLoadStrategy: eager
```

| Key | What it does |
|-----|--------------|
| `name` | Browser to launch: `chrome`, `firefox`, `edge`, or `safari`. |
| `headless` | Runs the browser without a visible window. Forced to `true` automatically when TestFly detects a CI environment. |
| `arguments` | Extra command-line flags passed to the browser executable. |
| `capabilities` | Raw Selenium capability overrides, e.g. `acceptInsecureCerts` for self-signed certificates. |

```yaml
execution:
  mode: local
  baseUrl: https://www.saucedemo.com/
  gridUrl: http://localhost:4444/wd/hub
  parallel: methods
  threadCount: 4
  maxActiveSessions: 4
```

| Key | What it does |
|-----|--------------|
| `mode` | `local`, `remote`, `browserstack`, or `saucelabs`. |
| `baseUrl` | Default URL used by `open()` and `BaseCucumberSteps.open()`. |
| `gridUrl` | Selenium Grid / standalone server URL, used when `mode: remote`. |
| `parallel` | TestNG parallel mode: `none`, `methods`, `classes`, `tests`, or `instances`. |
| `threadCount` | Number of threads when parallel execution is enabled. |
| `maxActiveSessions` | Maximum concurrent browser instances. Extra tests wait for a free slot instead of failing. |

```yaml
api:
  baseUrl: https://fakeapi.net
  timeoutSeconds: 30
  logBody: false
```

| Key | What it does |
|-----|--------------|
| `baseUrl` | Default base URL for `ApiClient` requests. |
| `timeoutSeconds` | Request timeout in seconds. |
| `logBody` | When `true`, response bodies are written to the step log. |

```yaml
retry:
  enabled: true
  maxAttempts: 2
```

| Key | What it does |
|-----|--------------|
| `enabled` | Global retry switch. |
| `maxAttempts` | Total attempts per test. `1` means no retry. Override per test with `@Retryable(maxAttempts = 3)`. |

```yaml
timeouts:
  explicit: 10
  pageLoad: 30
```

| Key | What it does |
|-----|--------------|
| `explicit` | Default wait timeout used by `WaitEngine`, `Locator`, and `BasePage` helpers. |
| `pageLoad` | Browser page-load timeout in seconds. |

```yaml
reporting:
  mergeRuns: false
  historyRuns: 10
```

| Key | What it does |
|-----|--------------|
| `mergeRuns` | When `true` (or via `-Dtestfly.merge=true`), sequential test executions merge test cases into a cumulative report instead of overwriting previous runs. |
| `historyRuns` | Maximum number of timestamped historical run reports preserved in `target/reports/` and listed in the report's run switcher dropdown. |

---

## Environment profiles

Keep one base file and create per-environment overrides:

```text
testfly.yml            # base config
testfly-staging.yml    # staging overrides
testfly-ci.yml         # CI overrides
```

Only the fields present in the profile file are replaced; everything else is inherited from the base config.

Activate a profile with:

```bash
mvn test -Dtestfly.profile=staging
```

Example `testfly-ci.yml`:

```yaml
browser:
  headless: true
  arguments:
    - --no-sandbox
    - --disable-dev-shm-usage

execution:
  parallel: methods
  threadCount: 8
  maxActiveSessions: 8
```

---

## Common patterns

### Run against a local Selenium Grid

```yaml
execution:
  mode: remote
  baseUrl: https://www.saucedemo.com/
  gridUrl: http://localhost:4444/wd/hub
```

### Run against BrowserStack

```yaml
execution:
  mode: browserstack
  baseUrl: https://www.saucedemo.com/

browserstack:
  username: ${BS_USER}
  accessKey: ${BS_KEY}
  os: Windows
  osVersion: "11"
  browser: chrome
  browserVersion: latest
```

### Disable retry for fast feedback during development

```yaml
retry:
  enabled: false
```

### Increase timeouts for slow environments

```yaml
timeouts:
  explicit: 20
  pageLoad: 60
```

---

## Validation

TestFly validates the config at suite startup. Missing required fields or invalid values (e.g. an unknown `parallel` mode) fail immediately with a clear message. Running `mvn test` with a broken config prints the problem before any browser opens, so you do not waste time on a misconfigured run.

---

## See also

- [Configuration Reference](../configuration) — complete option list
- [Browser Lifecycle](../guides/browser-lifecycle)
- [Parallel Execution](../guides/parallel)
- [API Testing](../guides/api-testing)
