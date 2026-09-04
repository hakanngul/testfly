---
description: "Complete testfly.yml configuration reference: browser, parallel threads, timeouts, retry, and CI quality gates, every option in one place."
id: configuration
title: Configuration Reference
sidebar_position: 3
---

# Configuration Reference

All framework behaviour is controlled by `testfly.yml`.

---

## File resolution order

The framework looks for the config file in this priority order:

1. **System property** — `-Dtestfly.config=/path/to/custom.yml`
2. **Working directory** — `./testfly.yml` (next to `pom.xml`)
3. **Classpath** — `src/test/resources/testfly.yml`

---

## Full reference

```yaml
# ── Browser ────────────────────────────────────────────────────────────────
browser:
  name: chrome              # chrome | firefox | edge | safari
  headless: false           # true in CI (auto-forced when CI detected)
  lifecycle: per-test       # per-test (default) | per-suite

  # Optional: extra browser arguments
  arguments:
    - --start-maximized
    - --disable-notifications

  # Optional: raw capability overrides
  capabilities:
    acceptInsecureCerts: true

# ── Execution ───────────────────────────────────────────────────────────────
execution:
  mode: local               # local | remote | browserstack | saucelabs
  baseUrl: https://your-app.com
  gridUrl: http://localhost:4444   # only for remote mode

  parallel: none            # none | methods | classes | tests | instances
  threadCount: 1            # ignored when parallel: none
  maxActiveSessions: 5      # max concurrent browser instances (semaphore)

  # ── BrowserStack (mode: browserstack) ──────────────────────────────────────
  browserstack:
    username:      ${BS_USER}
    accessKey:     ${BS_KEY}
    os:            Windows          # Windows | OS X
    osVersion:     "11"
    browser:       chrome           # chrome | firefox | edge | safari
    browserVersion: latest
    device:                         # optional — mobile device name
    realMobile:    true
    capabilities:                   # raw bstack:options overrides
      debug: false

  # ── Sauce Labs (mode: saucelabs) ───────────────────────────────────────────
  saucelabs:
    username:      ${SAUCE_USER}
    accessKey:     ${SAUCE_KEY}
    region:        us-west-1        # us-west-1 | eu-central | apac-southeast
    platformName:  "Windows 11"
    browser:       chrome
    browserVersion: latest
    capabilities:                   # raw sauce:options overrides
      recordVideo: true

# ── Locators ─────────────────────────────────────────────────────────────────
locators:
  selfHealing: false        # auto-retry failed locators with derived fallbacks
  testIdAttribute: data-testid  # attribute used by getByTestId()

# ── Retry ───────────────────────────────────────────────────────────────────
retry:
  enabled: true             # applies to all tests globally
  maxAttempts: 2            # total attempts (1 = no retry)

# ── Timeouts ────────────────────────────────────────────────────────────────
timeouts:
  explicit: 10              # seconds — WaitEngine default timeout
  pageLoad: 30              # seconds — browser page load timeout

# ── CI / Build Quality Gates ────────────────────────────────────────────────
ci:
  failOnPassRateBelow: 80   # 0 = disabled. Fail build if pass rate < 80%
  maxFlakyTests: 3          # -1 = disabled. Fail if more than 3 tests retried
  captureMetadata: true     # auto in CI; set false to disable. Writes provider,
                            # branch, commit, build URL to reports and metrics JSON

# ── Database Assertions ─────────────────────────────────────────────────────
database:
  url:      jdbc:postgresql://localhost/mydb
  username: ${DB_USER}
  password: ${DB_PASS}
  driver:   org.postgresql.Driver  # optional; auto-detected from URL by most drivers

  # Named datasources (access via db("reporting"))
  datasources:
    reporting:
      url:      jdbc:postgresql://localhost/reporting
      username: ${REPORTING_DB_USER}
      password: ${REPORTING_DB_PASS}

# ── Multi-Session Testing ───────────────────────────────────────────────────
sessions:
  maxPerTest: 2   # max named sessions per test (guard against resource leaks)

# ── Email Verification ──────────────────────────────────────────────────────
email:
  provider: mailhog          # mailhog | mailtrap | outlook | imap
  timeoutSeconds: 30         # default wait for waitForEmail()
  pollIntervalMs: 1000       # polling interval
  autoClear: false           # clear inbox before each test automatically

  mailhog:
    host: localhost
    port: 8025

  mailtrap:
    apiToken:  ${MAILTRAP_TOKEN}
    accountId: ${MAILTRAP_ACCOUNT_ID}
    inboxId:   ${MAILTRAP_INBOX_ID}

  outlook:
    tenantId:     ${AZURE_TENANT_ID}
    clientId:     ${AZURE_CLIENT_ID}
    clientSecret: ${AZURE_CLIENT_SECRET}
    mailbox:      test-inbox@yourcompany.com

  imap:
    host:     imap.gmail.com
    port:     993
    ssl:      true
    username: ${EMAIL_USER}
    password: ${EMAIL_PASS}
    folder:   INBOX

# ── Reporting ───────────────────────────────────────────────────────────────
reporting:
  allure:
    enabled: false        # write Allure 2 results to target/allure-results/

  reportportal:
    enabled: false
    endpoint: http://localhost:8080
    apiKey: ${RP_API_KEY}
    project: superadmin_personal
    launch: "TestFly Launch"
    description: "Automated TestFly test execution"
    attributes: "env:ci;branch:main"

# ── API Testing ─────────────────────────────────────────────────────────────
api:
  baseUrl: https://api.example.com  # fallback: execution.baseUrl
  timeoutSeconds: 30
  logBody: false               # log request/response body in step timeline
  logContext: false            # log additional request context
  truncationLimit: 300         # max body chars logged (when logBody: true)

  # Named auth strategies (use with @UseAuth("name"))
  auth:
    adminToken:
      type: bearer             # bearer | basic | oauth2
      token: ${ADMIN_TOKEN}
    basicUser:
      type: basic
      username: user
      password: ${USER_PASSWORD}
    serviceAccount:
      type: oauth2
      tokenUrl: https://auth.example.com/token
      clientId: ${CLIENT_ID}
      clientSecret: ${CLIENT_SECRET}

  # API-level retry (for flaky HTTP responses)
  retry:
    enabled: false
    maxAttempts: 3
    backoffMs: 500
    retryOnStatus: [502, 503, 504]
    retryOnException: true

# ── AI Failure Analysis ─────────────────────────────────────────────────────
ai:
  failureAnalysis: true        # enable AI-powered failure root-cause analysis
  provider: gemini             # gemini | claude | openai-compatible
  baseUrl:                     # optional — override provider endpoint (for openai-compatible)
  apiKey: ${GEMINI_API_KEY}
  model: gemini-2.0-flash      # gemini-2.0-flash | claude-haiku-4-5-20251001 | deepseek-chat
  language: en                 # analysis output language (en, tr, de, fr, etc.)
  timeoutSeconds: 20

# ── Performance (Core Web Vitals) ──────────────────────────────────────────
performance:
  captureOnEveryTest: false    # auto-collect CWV on every page load
  lcpWarnMs: 2500              # Largest Contentful Paint warning threshold
  fcpWarnMs: 1800              # First Contentful Paint warning threshold
  ttfbWarnMs: 800              # Time To First Byte warning threshold
  clsWarn: 0.1                 # Cumulative Layout Shift warning threshold

# ── Visual Regression ──────────────────────────────────────────────────────
visual:
  baselineDir: target/visual/baselines
  diffDir: target/visual/diffs
  defaultTolerance: 0.01       # pixel-difference threshold (0.0–1.0)
  updateBaselines: false       # set true to regenerate baselines

# ── Screen Recording ──────────────────────────────────────────────────────
recording:
  enabled: false               # record test execution as video
  fps: 2                       # frames per second
  maxDurationSeconds: 60       # max recording length per test

# ── Execution Tracing ─────────────────────────────────────────────────────
tracing:
  enabled: false               # capture execution trace (DOM snapshots + network)
  captureOnPass: true          # include passing tests in traces

# ── Quarantine ─────────────────────────────────────────────────────────────
quarantine:
  enabled: true                # skip quarantined tests from the run
  cucumberTag: quarantine      # Cucumber tag that marks quarantined scenarios

# ── Flakiness Tracking ────────────────────────────────────────────────────
flakiness:
  historyRuns: 20              # how many past runs to analyse
  highRiskThreshold: 33.0      # % — flag tests flaky above this rate
  failOnHighFlakiness: false   # fail the build if high-risk flaky tests exist

# ── Notifications ──────────────────────────────────────────────────────────
notifications:
  slack:
    webhookUrl: ${SLACK_WEBHOOK_URL}
    notifyOnFailureOnly: false
  teams:
    webhookUrl: ${TEAMS_WEBHOOK_URL}
    notifyOnFailureOnly: false

# ── Test Management ───────────────────────────────────────────────────────
testmanagement:
  testrail:
    enabled: false
    url: https://yourcompany.testrail.io
    username: ${TR_USER}
    apiKey: ${TR_API_KEY}
    projectId: 1
    suiteId: 2
    runName: "TestFly Run"
    autoCreateRun: true
    runId:                     # optional — use existing run

  xray:
    enabled: false
    mode: cloud                # cloud | server
    clientId: ${XRAY_CLIENT_ID}
    clientSecret: ${XRAY_CLIENT_SECRET}
    projectKey: PROJ
    testPlanKey: PROJ-1
    jiraUrl: https://yourcompany.atlassian.net
    username: ${JIRA_USER}
    password: ${JIRA_TOKEN}

# ── Clock Mocking ────────────────────────────────────────────────────────────
clock:
  injectHeader: false      # send X-Mock-Date header to server
  headerName: X-Mock-Date
```

---

## Browser

### `name`
The browser to use. Selenium Manager downloads the matching driver automatically.

| Value | Browser |
|---|---|
| `chrome` | Google Chrome (default) |
| `firefox` | Mozilla Firefox |
| `edge` | Microsoft Edge |
| `safari` | Safari (macOS only) |

### `headless`
Runs the browser without a visible window. Automatically forced to `true` when a CI environment is detected (GitHub Actions, Jenkins, etc.).

### `lifecycle`
Controls when the WebDriver session is closed.

| Value | Behaviour |
|---|---|
| `per-test` | Browser opens and closes for every test method (default — full isolation) |
| `per-suite` | Browser stays open for the entire suite; one instance per thread, closed at suite end |

:::tip When to use `per-suite`
Use `per-suite` when your suite has many sequential tests and browser startup time is a bottleneck. The browser retains cookies and state between tests — plan your test flow accordingly.
:::

---

## Execution

### `parallel`
Maps directly to TestNG parallel execution mode. Thread count is set via `threadCount`. TestFly validates this value at suite bootstrap against TestNG's own set of modes; an unrecognised value fails immediately with a message naming both the rejected value and the accepted ones.

| Value | Behaviour |
|---|---|
| `none` | Sequential execution (default) |
| `methods` | Each `@Test` method runs in its own thread |
| `classes` | Each test class runs in its own thread |
| `tests` | Each `<test>` in the suite XML runs in its own thread |
| `instances` | Each test class instance runs in its own thread |

### `maxActiveSessions`
Maximum concurrent browser instances. Tests wait (up to 30s) for a slot rather than failing immediately. Prevents resource exhaustion in parallel runs.

---

## Retry

### `enabled`
When `true`, all test methods are retried on failure up to `maxAttempts` times. Set to `false` to disable retry globally.

Use `@Retryable` on a method to override the global setting for that specific test.

```java
@Test
@Retryable(maxAttempts = 3)
public void flakyTest() { ... }
```

---

## Locators

### `selfHealing`
When `true`, a locator that fails inside `waitForVisible` / `waitForClickable` is
automatically retried with fallback strategies derived from the original `By`
descriptor (extract `id`, `name`, text, class, `data-testid`, or `placeholder`).
Healed tests get a `⚠ healed` badge in the HTML report, and every heal is written
to `target/healed-locators.json`. Off by default. See
[Self-Healing Locators](./guides/self-healing).

### `testIdAttribute`
The HTML attribute resolved by `getByTestId()`. Defaults to `data-testid`; set it
to `data-qa`, `data-test`, etc. to match your app. See
[Semantic Locators](./guides/semantic-locators).

---

## Environment profiles

Override the default config for a specific environment using a profile suffix:

```
testfly.yml            ← base config
testfly-staging.yml    ← staging overrides
testfly-prod.yml       ← prod overrides
```

Activate with:

```bash
mvn test -Dtestfly.profile=staging
```

Only the fields present in the profile file are overridden — everything else falls back to the base config.
