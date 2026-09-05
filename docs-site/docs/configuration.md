---
description: "Complete testfly.yml configuration reference: browser, execution, parallel threads, timeouts, retry, AI analysis, API testing, database, reporting, and CI quality gates."
id: configuration
title: Configuration Reference
sidebar_position: 3
---

# Configuration Reference

All TestFly behaviour is controlled by `testfly.yml`. This document is the exhaustive reference for every top-level section, nested configuration property, default value, environment variable resolution, and profile override supported by the framework.

---

## File Resolution Order

When TestFly bootstraps at suite execution start, it searches for the configuration file using the following priority order:

1. **System Property** — `-Dtestfly.config=/path/to/custom.yml` (highest priority)
2. **Working Directory** — `./testfly.yml` (at the root of your project, next to `pom.xml` or `build.gradle`)
3. **Classpath Resource** — `src/test/resources/testfly.yml` (fallback)

If no file is found at any of these locations, suite initialization fails immediately with a descriptive `IllegalStateException`.

---

## Environment Variable Substitution & Dynamic Overrides

### Placeholder Syntax

Any scalar value in `testfly.yml` can reference environment variables or Java system properties using `${VAR_NAME}` syntax:

```yaml
execution:
  baseUrl: ${BASE_URL}

browserstack:
  username: ${BS_USER}
  accessKey: ${BS_KEY}

api:
  auth:
    admin:
      type: bearer
      token: ${API_TOKEN}
```

* If the environment variable exists, its value replaces `${VAR_NAME}` at load time.
* If the variable is unset, TestFly checks Java system properties (`System.getProperty("VAR_NAME")`).
* If neither is set, `${VAR_NAME}` remains as a literal string or resolves to an empty string depending on context.

### Environment Profiles (`-Dtestfly.profile`)

You can create environment-specific override files by naming them `testfly-<profile>.yml`:

```text
testfly.yml            # Base configuration (shared defaults)
testfly-staging.yml    # Staging overrides
testfly-prod.yml       # Production overrides
testfly-ci.yml         # CI pipeline overrides
```

Activate a profile via Maven or Gradle:

```bash
mvn test -Dtestfly.profile=staging
```

:::tip Deep Merge Behavior
Profile files **only need to declare the properties they wish to override**. TestFly merges the profile file on top of `testfly.yml`, retaining all base settings that are not explicitly overridden.
:::

---

## Master `testfly.yml` Template

The following commented template demonstrates every supported configuration block with recommended defaults:

```yaml
# ── Browser ──────────────────────────────────────────────────────────────────
browser:
  name: chrome                      # chrome | firefox | edge | safari
  headless: false                   # auto-forced to true when CI environment is detected
  lifecycle: per-test               # per-test (clean isolation) | per-suite (reuse session per thread)
  downloadDir: ./target/downloads   # target directory for browser file downloads
  captureConsoleErrors: false       # collect browser console (JS) error logs
  failOnConsoleErrors: false        # fail test if severe console errors are detected
  device:                           # optional mobile emulation profile (e.g. "iPhone 14")
  arguments:                        # extra command-line flags passed to browser executable
    - --start-maximized
    - --disable-notifications
    - --remote-allow-origins=*
  capabilities:                     # raw WebDriver capability overrides
    acceptInsecureCerts: true
    pageLoadStrategy: eager

# ── Execution ────────────────────────────────────────────────────────────────
execution:
  mode: local                       # local | remote | browserstack | saucelabs
  baseUrl: https://example.com      # default base URL used by open("/")
  gridUrl: http://localhost:4444    # Selenium Grid hub URL (mode: remote)
  parallel: none                    # none | methods | classes | tests | instances
  threadCount: 1                    # worker thread count when parallel is active
  maxActiveSessions: 5              # concurrency semaphore limiting active browsers

  # ── BrowserStack (mode: browserstack)
  browserstack:
    username: ${BS_USER}
    accessKey: ${BS_KEY}
    os: Windows                     # Windows | OS X
    osVersion: "11"
    browser: chrome                 # chrome | firefox | edge | safari
    browserVersion: latest
    device:                         # real mobile device name (e.g. "iPhone 14")
    realMobile: true
    capabilities:                   # extra bstack:options overrides
      debug: false

  # ── Sauce Labs (mode: saucelabs)
  saucelabs:
    username: ${SAUCE_USER}
    accessKey: ${SAUCE_KEY}
    region: us-west-1               # us-west-1 | eu-central | apac-southeast
    platformName: "Windows 11"
    browser: chrome
    browserVersion: latest
    capabilities:                   # extra sauce:options overrides
      recordVideo: true

# ── Timeouts ─────────────────────────────────────────────────────────────────
timeouts:
  explicit: 10                      # seconds — WaitEngine & Locator explicit wait timeout
  pageLoad: 30                      # seconds — WebDriver page load timeout

# ── Retry ────────────────────────────────────────────────────────────────────
retry:
  enabled: true                     # global automatic test retry toggle
  maxAttempts: 2                    # total attempts per test (1 = no retry, 2 = 1 initial + 1 retry)

# ── Locators ─────────────────────────────────────────────────────────────────
locators:
  selfHealing: false                # auto-heal broken locators using fallback strategies
  testIdAttribute: data-testid      # attribute queried by getByTestId()

# ── AI Failure Analysis ──────────────────────────────────────────────────────
ai:
  failureAnalysis: false            # generate AI root-cause analysis on test failure
  provider: gemini                  # gemini | claude | openai-compatible
  apiKey: ${AI_API_KEY}             # provider API key
  model:                            # optional — defaults: gemini-2.5-flash or claude-haiku-4-5-20251001
  language: en                      # analysis language: en, tr, de, fr, es, etc.
  timeoutSeconds: 20                # HTTP timeout for AI response generation
  baseUrl:                          # optional — custom endpoint (required for openai-compatible)

# ── CI / Build Quality Gates ─────────────────────────────────────────────────
ci:
  failOnPassRateBelow: 0            # 0 = disabled. Example: 85 (fails build if pass rate < 85%)
  maxFlakyTests: -1                 # -1 = disabled. Fails build if retried tests exceed threshold
  captureMetadata: true             # auto-captures provider, branch, commit, build URL in reports

# ── Notifications ────────────────────────────────────────────────────────────
notifications:
  slack:
    webhookUrl: ${SLACK_WEBHOOK}
    notifyOnFailureOnly: false
  teams:
    webhookUrl: ${TEAMS_WEBHOOK}
    notifyOnFailureOnly: false

# ── Reporting ────────────────────────────────────────────────────────────────
reporting:
  mergeRuns: false                  # retain and merge sequential test runs into cumulative report (-Dtestfly.merge=true)
  historyRuns: 10                   # number of historical test runs to preserve in report run switcher
  allure:
    enabled: false                  # export Allure 2 test results to target/allure-results/
  reportportal:
    enabled: false
    endpoint: http://localhost:8080
    apiKey: ${RP_API_KEY}
    project: testfly_project
    launch: "Regression Suite"
    description: "Nightly automated test run"
    attributes: "env:staging;team:qa"
    type: auto                      # auto (auto-detect Web vs API) | web | api
    mode: default                   # default | step

# ── Screen Recording ─────────────────────────────────────────────────────────
recording:
  enabled: false                    # record MP4 video of browser execution
  mode: retain-on-failure          # retain-on-failure | on | off
  format: mp4                      # mp4 (default, pure-Java H.264) | gif
  fps: 5                           # frame rate (1-10 recommended)
  maxDurationSeconds: 60           # maximum video length per test
  cdp: true                        # use Chrome DevTools Protocol screencast on Chromium

# ── Execution Tracing ────────────────────────────────────────────────────────
tracing:
  enabled: false                    # capture DOM snapshots and execution timeline
  captureOnPass: false              # include passing tests in trace bundle

# ── Visual Regression ────────────────────────────────────────────────────────
visual:
  baselineDir: src/test/resources/baselines  # golden image directory
  diffDir: target/visual-diffs               # visual mismatch image output directory
  defaultTolerance: 0.01                     # allowable pixel difference ratio (0.0 to 1.0)
  updateBaselines: false                     # update baselines from current run if true

# ── Multi-Session Isolation ──────────────────────────────────────────────────
sessions:
  maxPerTest: 2                     # max isolated browser sessions per test (e.g. multi-user chat)

# ── Performance (Core Web Vitals) ────────────────────────────────────────────
performance:
  captureOnEveryTest: false         # collect CWV metrics on navigation
  lcpWarnMs: 2500                   # Largest Contentful Paint threshold (ms, 0 = disabled)
  fcpWarnMs: 1800                   # First Contentful Paint threshold (ms)
  ttfbWarnMs: 800                   # Time to First Byte threshold (ms)
  clsWarn: 0.1                      # Cumulative Layout Shift score threshold

# ── Quarantine ───────────────────────────────────────────────────────────────
quarantine:
  enabled: true                     # skip quarantined tests automatically
  cucumberTag: quarantine           # Cucumber tag marking quarantined scenarios

# ── Flakiness Tracking ───────────────────────────────────────────────────────
flakiness:
  historyRuns: 20                   # previous execution runs analyzed for stability score
  highRiskThreshold: 33.0           # percentage flaky failure rate triggering high risk warning
  failOnHighFlakiness: false        # fail build if any high-risk flaky tests are detected

# ── Clock Mocking ────────────────────────────────────────────────────────────
clock:
  injectHeader: false               # send mock date header on HTTP requests
  headerName: X-Mock-Date           # custom header name for backend clock synchronization

# ── Network Interception ─────────────────────────────────────────────────────
network:
  interceptEnabled: false           # enable CDP network interception and route mocking

# ── Email Verification ───────────────────────────────────────────────────────
email:
  provider: mailhog                 # mailhog | mailtrap | outlook | imap
  timeoutSeconds: 30                # max wait duration for expected emails
  pollIntervalMs: 1000              # inbox polling interval
  autoClear: false                  # wipe inbox before each test method runs
  mailhog:
    host: localhost
    port: 8025
  mailtrap:
    apiToken: ${MAILTRAP_TOKEN}
    accountId: ${MAILTRAP_ACCOUNT}
    inboxId: ${MAILTRAP_INBOX}
  outlook:
    tenantId: ${AZURE_TENANT_ID}
    clientId: ${AZURE_CLIENT_ID}
    clientSecret: ${AZURE_CLIENT_SECRET}
    mailbox: test@example.com
  imap:
    host: imap.example.com
    port: 993
    ssl: true
    username: ${EMAIL_USER}
    password: ${EMAIL_PASS}
    folder: INBOX

# ── Database Assertions ──────────────────────────────────────────────────────
database:
  url: jdbc:postgresql://localhost:5432/maindb
  username: ${DB_USER}
  password: ${DB_PASS}
  driver: org.postgresql.Driver
  datasources:
    analytics:
      url: jdbc:postgresql://localhost:5432/analytics
      username: ${ANALYTICS_USER}
      password: ${ANALYTICS_PASS}

# ── API Testing ──────────────────────────────────────────────────────────────
api:
  baseUrl: https://api.example.com  # default base URL for ApiClient
  timeoutSeconds: 30
  logBody: false                    # attach request/response bodies to HTML step log
  logContext: true                  # log query parameters and headers
  prettyLog: false                  # format JSON bodies with indentation
  logCurl: false                    # print equivalent curl command for failed requests
  truncationLimit: 300              # maximum characters logged for response bodies
  maskedHeaders:                    # headers redacted in execution logs
    - Authorization
    - Cookie
    - X-Api-Key
  retry:
    enabled: false                  # retry failed HTTP requests automatically
    maxAttempts: 3
    backoffMs: 500
    retryOnStatus: [502, 503, 504]
    retryOnException: true
  auth:
    adminBearer:
      type: bearer
      token: ${ADMIN_TOKEN}
    basicAuth:
      type: basic
      username: apiuser
      password: ${API_PASS}
    oauthClient:
      type: oauth2
      tokenUrl: https://auth.example.com/oauth/token
      clientId: ${CLIENT_ID}
      clientSecret: ${CLIENT_SECRET}

# ── Test Management ──────────────────────────────────────────────────────────
testmanagement:
  testrail:
    enabled: false
    url: https://myorg.testrail.io
    username: ${TR_USER}
    apiKey: ${TR_KEY}
    projectId: 1
    suiteId: 10
    runName: "Automated Suite"
    autoCreateRun: true
  xray:
    enabled: false
    mode: cloud                     # cloud | server
    clientId: ${XRAY_ID}
    clientSecret: ${XRAY_SECRET}
    projectKey: PROJ
    testPlanKey: PROJ-100
```

---

## Detailed Section Guide

## Browser {#browser}

Controls WebDriver browser provisioning, execution mode, capabilities, and process arguments.

| Property | Type | Default | Description |
|---|---|---|---|
| `name` | `string` | `chrome` | Target browser executable. Valid values: `chrome`, `firefox`, `edge`, `safari`. |
| `headless` | `boolean` | `false` | Run without a visible GUI window. Automatically forced to `true` when CI environment variables are detected. |
| `lifecycle` | `string` | `per-test` | Lifecycle scope for WebDriver instances: `per-test` (closes browser after each test method) or `per-suite` (retains browser per thread across tests). |
| `downloadDir` | `string` | `./target/downloads` | Path where downloaded files are saved. Auto-configured in browser options. |
| `captureConsoleErrors` | `boolean` | `false` | When `true`, intercepts browser `console.error` entries during execution. |
| `failOnConsoleErrors` | `boolean` | `false` | When `true`, automatically fails the test if any `SEVERE` browser console errors occurred. |
| `device` | `string` | `null` | Emulate a specific mobile device viewport and user agent (e.g. `"iPhone 14"`, `"Pixel 7"`). |
| `arguments` | `list<string>` | `[]` | Extra CLI flags passed directly to the browser binary (e.g. `--incognito`, `--no-sandbox`). |
| `capabilities` | `map` | `{}` | Raw capability key-values merged into WebDriver options (e.g. `acceptInsecureCerts`, `pageLoadStrategy`). |

---

## Execution {#execution}

Governs test execution topology, base URLs, concurrency, and cloud grid providers.

| Property | Type | Default | Description |
|---|---|---|---|
| `mode` | `string` | `local` | Execution environment: `local`, `remote`, `browserstack`, or `saucelabs`. |
| `baseUrl` | `string` | `null` | Default web URL. When calling `open("/home")`, TestFly prefixes it with this URL. |
| `gridUrl` | `string` | `null` | Hub endpoint for remote Selenium Grid (used when `mode: remote`). Example: `http://localhost:4444`. |
| `parallel` | `string` | `none` | Parallel test distribution mode: `none`, `methods`, `classes`, `tests`, `instances`. Validated against TestNG `ParallelMode`. |
| `threadCount` | `int` | `1` | Concurrency worker count when `parallel` is enabled. |
| `maxActiveSessions` | `int` | `5` | Semaphore limiting concurrent WebDriver sessions. Extra threads queue and wait up to 30 seconds for a slot. |

#### Cloud Sub-Blocks: `browserstack` & `saucelabs`

```yaml
execution:
  mode: browserstack
  browserstack:
    username: ${BS_USER}
    accessKey: ${BS_KEY}
    os: Windows
    osVersion: "11"
    browser: chrome
    browserVersion: latest
    capabilities:
      projectName: "E-Commerce"
      buildName: "Build #104"
```

---

## Timeouts {#timeouts}

Centralized explicit and implicit wait durations in seconds.

| Property | Type | Default | Description |
|---|---|---|---|
| `explicit` | `int` | `10` | Timeout in seconds for all `WaitEngine`, `Locator`, and `assertThat()` DOM polling operations. |
| `pageLoad` | `int` | `30` | Browser document load timeout passed to `WebDriver.Timeouts.pageLoadTimeout()`. |

---

## Retry {#retry}

Automatic retry configuration for failed test executions.

| Property | Type | Default | Description |
|---|---|---|---|
| `enabled` | `boolean` | `true` | Global switch for automatic test retries. |
| `maxAttempts` | `int` | `1` | Maximum attempts for each test. `1` means no retry. `2` means 1 original execution + 1 retry attempt. |

:::info Method-Level Override
You can override global retry settings on individual test methods using `@Retryable(maxAttempts = 3)`.
:::

---

## Locators {#locators}

Smart locator synthesis and resilience settings.

| Property | Type | Default | Description |
|---|---|---|---|
| `selfHealing` | `boolean` | `false` | When enabled, locators that time out in `waitForVisible` are healed using alternate heuristics (id, test-id, text, placeholder) and saved to `target/healed-locators.json`. |
| `testIdAttribute` | `string` | `data-testid` | The HTML attribute targeted by `getByTestId("submit-btn")`. Can be configured to `data-qa`, `data-test`, etc. |

---

## AI Failure Analysis {#ai}

AI-driven test triage and automated root-cause suggestion engine.

| Property | Type | Default | Description |
|---|---|---|---|
| `failureAnalysis` | `boolean` | `false` | When `true`, automatically sends failure stack traces, step logs, and DOM state to the LLM upon test failure. |
| `provider` | `string` | `claude` | AI backend provider: `gemini`, `claude`, or `openai-compatible`. |
| `apiKey` | `string` | `null` | API authorization key for the chosen provider. |
| `model` | `string` | `null` | Target model. Defaults automatically to `gemini-2.5-flash` for Gemini or `claude-haiku-4-5-20251001` for Claude if omitted. |
| `baseUrl` | `string` | `null` | Custom API base URL (required for `openai-compatible` providers like Ollama, LocalAI, vLLM, or DeepSeek). |
| `language` | `string` | `en` | Language of the generated analysis report (e.g. `en`, `tr`, `de`, `fr`). |
| `timeoutSeconds` | `int` | `20` | Maximum wait time for the AI provider response. |

---

## CI / Quality Gates {#ci}

Controls CI environment detection and build pass/fail criteria.

| Property | Type | Default | Description |
|---|---|---|---|
| `failOnPassRateBelow` | `double` | `0` | Minimum test pass rate percentage (e.g. `90.0`). If actual pass rate is lower, build fails. `0` disables this gate. |
| `maxFlakyTests` | `int` | `-1` | Maximum allowable number of tests that failed initially but passed on retry. `-1` disables this gate. |
| `captureMetadata` | `boolean` | Auto | Extracts CI environment details (Git branch, commit SHA, PR number, build URL) into reports. |

---

## Reporting {#reporting}

Settings for the built-in HTML dashboard report, run history archiving, and third-party test portals.

| Property | Type | Default | Description |
|---|---|---|---|
| `mergeRuns` | `boolean` | `false` | When `true`, sequential test executions merge their test results into a cumulative report instead of overwriting previous tests. Can also be toggled via CLI: `-Dtestfly.merge=true`. |
| `historyRuns` | `int` | `10` | Maximum number of historical test runs to preserve in `target/reports/` and list in the interactive HTML report run switcher dropdown. |

:::tip Run Archiving & History Switcher
TestFly automatically archives every test execution to `target/reports/testfly-report-YYYYMMDD-HHmmss.html` alongside the primary `target/testfly-report.html`. The top header includes an interactive run switcher dropdown and a dedicated **Run History** tab to navigate past runs, timelines, and pass rates.
:::

#### `reportportal`

| Property | Type | Default | Description |
|---|---|---|---|
| `enabled` | `boolean` | `false` | Enable streaming results to ReportPortal. |
| `endpoint` | `string` | `null` | ReportPortal server URL (e.g. `http://reportportal.myorg.com:8080`). |
| `apiKey` | `string` | `null` | User API Access Token. |
| `project` | `string` | `superadmin_personal` | ReportPortal project name. |
| `launch` | `string` | `TestFly Suite` | Name of the test launch created in ReportPortal. |
| `type` | `string` | `auto` | Launch enrichment type: `auto` (detects Web vs API), `web`, or `api`. |
| `mode` | `string` | `default` | ReportPortal TestNG listener mode: `default` or `step`. |
| `attributes` | `string` | `""` | Semicolon-delimited tags and metadata attached to the launch (e.g. `"env:ci;team:core"`). |

#### `allure`

| Property | Type | Default | Description |
|---|---|---|---|
| `enabled` | `boolean` | `false` | When enabled, generates Allure 2 test results in `target/allure-results/`. |

---

## Notifications {#notifications}

Webhooks for publishing test summary reports on completion.

| Property | Type | Default | Description |
|---|---|---|---|
| `slack.webhookUrl` | `string` | `null` | Incoming Slack Webhook URL. |
| `slack.notifyOnFailureOnly` | `boolean` | `false` | Only send notification if one or more tests failed. |
| `teams.webhookUrl` | `string` | `null` | Microsoft Teams Connector Webhook URL. |
| `teams.notifyOnFailureOnly` | `boolean` | `false` | Only send notification if one or more tests failed. |

---

## API Testing {#api}

Configuration for the built-in fluent REST client (`ApiClient` & `BaseApiTest`).

| Property | Type | Default | Description |
|---|---|---|---|
| `baseUrl` | `string` | `null` | Default HTTP endpoint for API tests (falls back to `execution.baseUrl` if unset). |
| `timeoutSeconds` | `int` | `30` | HTTP request timeout in seconds. |
| `logBody` | `boolean` | `false` | Log request and response payloads into the step timeline report. |
| `logContext` | `boolean` | `true` | Log headers, query parameters, and HTTP methods. |
| `prettyLog` | `boolean` | `false` | Pretty-print JSON bodies in step logs. |
| `logCurl` | `boolean` | `false` | Generate and log equivalent `curl` commands for troubleshooting. |
| `truncationLimit` | `int` | `300` | Max characters logged per response body to avoid bloating reports. |
| `maskedHeaders` | `list<string>` | `["Authorization", "Cookie", "X-Api-Key"]` | Headers redacted in test logs. |

#### `api.retry`

HTTP-level retry policy for transient server errors (e.g. 502, 503, 504).

```yaml
api:
  retry:
    enabled: true
    maxAttempts: 3
    backoffMs: 500
    retryOnStatus: [502, 503, 504]
    retryOnException: true
```

#### `api.auth`

Named authentication profiles referenced in tests via `@UseAuth("name")` or `apiClient().withAuth("name")`:

```yaml
api:
  auth:
    adminBearer:
      type: bearer
      token: ${SECRET_TOKEN}
    gatewayUser:
      type: basic
      username: testuser
      password: ${USER_PASS}
    keyAuth:
      type: apiKey
      headerName: X-API-Token
      apiKey: ${API_KEY}
    oauth2Service:
      type: oauth2
      tokenUrl: https://auth.myorg.com/oauth/token
      clientId: ${CLIENT_ID}
      clientSecret: ${CLIENT_SECRET}
```

---

## Database {#database}

Database connectivity for state assertions and test data seeding via `db()`.

| Property | Type | Default | Description |
|---|---|---|---|
| `url` | `string` | `null` | JDBC connection string for default datasource. |
| `username` | `string` | `null` | Database user. |
| `password` | `string` | `null` | Database password. |
| `driver` | `string` | `null` | Optional explicit JDBC driver class name (auto-detected from URL for major DBs). |
| `datasources` | `map` | `{}` | Named datasources accessed via `db("name")`. |

---

## Email Verification {#email}

Mailbox testing integrations (`Mailhog`, `Mailtrap`, `Outlook Graph`, `IMAP`).

| Property | Type | Default | Description |
|---|---|---|---|
| `provider` | `string` | `mailhog` | Active provider: `mailhog`, `mailtrap`, `outlook`, `imap`. |
| `timeoutSeconds` | `int` | `30` | Timeout waiting for email arrival. |
| `pollIntervalMs` | `int` | `1000` | Interval between inbox polling checks. |
| `autoClear` | `boolean` | `false` | Wipe inbox before each test method starts. |

---

## Performance {#performance}

Automated capture and validation of Google Core Web Vitals during web tests.

| Property | Type | Default | Description |
|---|---|---|---|
| `captureOnEveryTest` | `boolean` | `false` | Automatically extract CWV metrics on every `open()` call. |
| `lcpWarnMs` | `double` | `0` | Largest Contentful Paint warning threshold (ms). `0` = disabled. |
| `fcpWarnMs` | `double` | `0` | First Contentful Paint warning threshold (ms). |
| `ttfbWarnMs` | `double` | `0` | Time to First Byte warning threshold (ms). |
| `clsWarn` | `double` | `0` | Cumulative Layout Shift threshold (e.g. `0.1`). |

---

## Visual Regression {#visual}

Pixel-based screenshot comparison and visual baseline management.

| Property | Type | Default | Description |
|---|---|---|---|
| `baselineDir` | `string` | `src/test/resources/baselines` | Directory containing approved golden images. |
| `diffDir` | `string` | `target/visual-diffs` | Directory where mismatch diff images are written. |
| `defaultTolerance` | `double` | `0` | Percentage pixel mismatch tolerance (e.g. `0.02` for 2%). |
| `updateBaselines` | `boolean` | `false` | Overwrite baseline golden images with current run screenshots. |

---

## Screen Recording & Tracing {#recording}

Session capture for debugging, failure analysis, and audit compliance. See the full [Video Recording Guide](guides/video-recording) for details.

| Property | Type | Default | Description |
|---|---|---|---|
| `recording.enabled` | `boolean` | `false` | Master toggle to enable Web UI video recording. |
| `recording.mode` | `string` | `retain-on-failure` | `retain-on-failure`: Discard on pass, compile on fail.<br/>`on`: Record all tests.<br/>`off`: Disable recording. |
| `recording.format` | `string` | `mp4` | Video format: `mp4` (pure-Java H.264 video, default) or `gif` (animated GIF). |
| `recording.fps` | `int` | `2` | Captured frames per second (recommended 2–5). |
| `recording.maxDurationSeconds` | `int` | `60` | Maximum recording duration in seconds before capping. |
| `recording.cdp` | `boolean` | `true` | When true, uses CDP screencast on Chrome/Edge without blocking execution. |
| `tracing.enabled` | `boolean` | `false` | Capture complete DOM states and network events for timeline replay. |
| `tracing.captureOnPass` | `boolean` | `false` | Also capture traces for passing tests. |

---

## Quarantine {#quarantine}

Automated handling for flaky or under-repair tests.

| Property | Type | Default | Description |
|---|---|---|---|
| `enabled` | `boolean` | `true` | When active, tests listed in `testfly-quarantine.yml` or tagged in Cucumber are skipped. |
| `cucumberTag` | `string` | `quarantine` | Tag marking quarantined Cucumber scenarios (without `@`). |

---

## Flakiness Tracking {#flakiness}

Historical stability scoring and flaky test prevention.

| Property | Type | Default | Description |
|---|---|---|---|
| `historyRuns` | `int` | `20` | Number of previous execution metrics examined. |
| `highRiskThreshold` | `double` | `33.0` | Flakiness score percentage considered high risk. |
| `failOnHighFlakiness` | `boolean` | `false` | Fails build if any test exceeds the high risk threshold. |

---

## Test Management {#testmanagement}

Push execution outcomes and run links to Jira and TestRail.

```yaml
testmanagement:
  testrail:
    enabled: true
    url: https://yourorg.testrail.io
    username: ${TR_USER}
    apiKey: ${TR_KEY}
    projectId: 1
    suiteId: 2
    autoCreateRun: true

  xray:
    enabled: true
    mode: cloud                 # cloud | server
    clientId: ${XRAY_ID}
    clientSecret: ${XRAY_SECRET}
    projectKey: PROJ
    testPlanKey: PROJ-12
```

---

## Clock & Network {#clock}

* **`clock.injectHeader`**: Injects mock date HTTP header into browser requests (default `false`).
* **`clock.headerName`**: Custom header name (default `"X-Mock-Date"`).
* **`network.interceptEnabled`**: Activates Chrome DevTools Protocol network interception and stubbing (default `false`).

---

## Validation & Startup Diagnostics

TestFly performs strict schema validation upon bootstrap. If an invalid or unknown configuration value is detected (such as an illegal parallel mode or missing mandatory cloud credentials), the run terminates immediately before any browser session is spawned, preventing wasted compute resources:

```text
[TestFly] Configuration validation failed:
- execution.parallel: 'invalid_mode' is not valid. Allowed: [none, methods, classes, tests, instances]
- execution.baseUrl: Must be a valid absolute HTTP/HTTPS URL
```
