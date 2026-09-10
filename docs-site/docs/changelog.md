---
description: "TestFly release notes and version history: new features, fixes, and breaking changes across every release."
id: changelog
title: Changelog
sidebar_position: 99
---

# Changelog

All notable changes to TestFly are documented here.

---

## Unreleased

_Nothing yet._

---

## [1.1.0] — 2026-09-10

### Added — Load & Performance Testing Module

- **Dual-Engine Load Testing Architecture**:
  - **Gatling Engine**: High-concurrency subprocess execution, automatic simulation generation, stdout/log parsing, and full interactive Gatling HTML report generation linked directly in TestFly reports.
  - **Lightweight Virtual Thread Engine**: Zero-dependency JDK virtual thread engine for developer machines and fast CI/CD feedback loops.
- **Fluent Load Testing DSL**:
  - Declarative scenario builder via `load(url).users(n).during(duration).rampUp(duration).run()` or `loadScenario("name").step(...).run()`.
  - Seamlessly available in `BaseTest`, `BaseApiTest`, `BaseLoadTest`, and `BaseJUnit5Test`.
- **Annotation-Driven Execution (`@LoadTest`)**:
  - Configure load test parameters (`users`, `duration`, `rampUp`, `targetRps`, `engine`, `feeders`, `warmUp`) directly at test class or method level.
- **Data Feeders (`Feeder`)**:
  - Built-in CSV, JSON, Array, and custom Supplier feeders with `circular()`, `random()`, and `batch()` iteration strategies.
- **SLA & Latency Assertions (`LoadTestAssert`)**:
  - Fluent assertions for percentiles (P50, P90, P95, P99), max response time, min throughput (RPS), error rate thresholds, and HTTP status distributions.
- **Unified Multi-Channel Reporting**:
  - **TestFly HTML Report**: Dedicated "Load Testing" tab with interactive KPI cards (Users, RPS, Total Requests, P95, Error Rate), response time percentiles breakdown, status code distribution, and clickable link to Gatling interactive HTML reports.
  - **Allure Report Adapter**: Adds load test parameters (Users, Engine, RPS, P95, Error Rate), interactive link to Gatling report, summary markdown attachment, and full subprocess execution log attachment.
  - **ReportPortal Adapter**: Sends load test Markdown summary log entries and attaches subprocess execution logs to the test step.

### Added — Feature Switchboard

- **`features:` master on/off panel** — master switchboard in `testfly.yml` to effortlessly enable/disable optional subsystems (`ai`, `recording`, `tracing`, `network`, `healing`, `visual`, `performance`, `flakiness`, `quarantine`, `testManagement`, `notifications`, `consoleErrors`).
- **`FeatureGate`** (`io.testfly.config.FeatureGate`) — unified programmatic gate for feature status.
- **`ai.enabled` master AI switch** (default `true`) — single switch to gate all AI capabilities.

### Changed

- **Tolerant `testfly.yml` parsing** — unrecognized configuration keys are logged as warnings rather than crashing startup.
- **Fail-fast on disabled features** — direct programmatic invocations of disabled features fail clearly rather than silently skipping.

---

## [1.0.4] — 2026-09-07

### Added — Agentic Testing & Autonomous AI

- **AI-Driven Advanced Self-Healing** — `DomPruner` compresses complex web DOM trees to under 8K tokens by stripping non-semantic and decorative nodes. `AiHealingEngine` synthesizes replacement locators with LLM reasoning when static fallbacks are exhausted, caching healed locators to `.testfly/healed-locators.json` for 0 ms replay latency.
- **AI-Powered Self-Remediation & Auto-PR Patches** — `SourceCodeLocator` maps runtime failures back to consumer test and page object sources. `RemediationPatchGenerator` generates clean, unified git diff `.patch` files into `target/remediations/` for single-command `git apply` resolution.
- **Semantic Natural Language Assertions** — `satisfiesAi(condition)` and `violatesAi(condition)` on `PageAssert` and `LocatorAssert`, plus `assertWithAi(condition)` convenience method in `AssertionSupport`. Features single-evaluation anti-throttle guard and soft-assertion compatibility.
- **Goal-Oriented Dynamic Steps (`act`) & Compile & Freeze Caching** — High-level natural language goal execution via `act(String goal)` in `ActionSupport` (`BaseTest`, `BasePage`, `BaseJUnit5Test`, `BaseCucumberSteps`) and `byIntent(String intent)` in `LocatorSupport`. Compiles user intents into deterministic Selenium action plans and freezes them into `.testfly/action-cache.json` for instant replay.
- **Dropdown & Navigation Action Primitives** — `ActionType.SELECT` picks a `<select>` option by visible text through Selenium's `Select`; `ActionType.NAVIGATE` calls `driver.get()`, passing absolute URLs through and resolving relative paths against `execution.baseUrl`. `NAVIGATE` bypasses locator parsing, so agent plans can move between pages without a target element. Both are advertised to the LLM by `ActionCompiler.buildPrompt`.

### Added — Video Recording

- **Playwright-style retain-on-failure recording** — Web UI tests are recorded to MP4 and kept only when a test fails; passing tests discard the video automatically
- **Real MP4 encoding** — videos encoded as H.264 MP4 and embedded as HTML5 `<video>` players in both TestFly HTML report and Allure report
- **Headless viewport auto-config** — 1920×1080 viewport automatically applied for headless Chrome/Edge when `startMaximized` is set

### Added — Network Mocking Framework

- **Comprehensive network mocking and assertions** — CDP-based request interception with pattern matching, response stubbing, and assertion API for verifying captured requests

### Changed

- **SessionCache rename** — `browser.SessionCache` → `BrowserSessionCache`, `precondition.SessionCache` → `PreconditionSessionCache` to eliminate naming ambiguity
- **`.env` resolution priority** — `DotEnvLoader` now resolves `${VAR}` placeholders with explicit priority: `.env` file > shell environment > system property (`-D`). Added `DotEnvLoader.fromDotEnv(key)` to query `.env` values directly. Removed `dotenv-java` dependency; `.env` parsing is handled internally.

### Added — API Testing Improvements

- **HTTP-level retry** (`api.retry.*`) — config-driven retry for transient failures and status codes (502/503/504); exponential backoff; step-logged with `WARN`
- **Per-request timeout override** — `apiClient().get("/report").timeout(120).send()`
- **Query parameter builder** — `.queryParam("page", 1).queryParam("limit", 10)` with URL encoding
- **Request/Response interceptors** — `ApiClient.addRequestInterceptor()` and `addResponseInterceptor()`; global, thread-safe
- **Cookie jar** — `.withCookies()` captures and auto-sends cookies across requests; thread-local
- **Response time assertion** — `res.assertDurationLessThan(500)` and `assertDurationLessThan(2, TimeUnit.SECONDS)`
- **Header assertions** — `assertHeader("Content-Type", "application/json")`, `assertHeaderPresent("X-Request-Id")`
- **Body regex assertion** — `assertBodyMatches("\\d{4}-\\d{2}-\\d{2}")` with dotall mode
- **JSON structure assertions** — `assertJsonExists("$.path")`, `assertJsonNull("$.field")`, `assertJsonArraySize("$.items", 3)`
- **Configurable truncation limit** — `api.truncationLimit: 1000` (default 300)
- **Self-healing locator cache** — healed locators persisted across runs for faster recovery

```yaml
api:
  retry:
    enabled: true
    maxAttempts: 3
    backoffMs: 500
    retryOnStatus: [502, 503, 504]
    retryOnException: true
```

### Added — Report Portal Enhancements

- **Auto run type detection** — suite test classes scanned; `BaseApiTest` → "API", otherwise → "Web"; `reporting.reportportal.type: api|web|auto`
- **Enriched launch name** — `<name> — <API|Web> | <env> | <timestamp>`
- **Enriched description** — run type, context-aware base URL (Web→execution.baseUrl, API→api.baseUrl), environment, user@hostname, CI platform + build info
- **CI platform detection** — GitHub Actions, Jenkins, GitLab CI, CircleCI, Travis CI, Bitbucket Pipelines, Azure Pipelines
- **JUnit 5 → Report Portal** — `agent-java-junit5` dependency + `ReportPortalJUnit5Bridge` reflection bridge; JUnit 5 tests push results to RP automatically

```yaml
reporting:
  reportPortal:
    enabled: true
    launch: "Demo Web - Dev"
    type: auto
```

### Fixed

- **OAuth2 token cache race condition** — double-checked locking prevents thundering herd on expired tokens
- **RP launch naming** — context-aware base URL selection based on run type (Web vs API)
- **Dark theme text visibility** — CSS safety nets for all Prism token types; fixed invisible code block text; dark mode overrides for search dropdown, tabs, collapsible, badges, footer
- **AI model defaults and validation** — `ai.model` is optional and defaults per provider (`claude-haiku-4-5-20251001` for Claude, `gemini-2.5-flash` for Gemini); sanitized Gemini model names against injection
- **Session cache isolation & script safety** — `BrowserSessionCache` and `PreconditionSessionCache` clear localStorage before restore and pass items safely via WebDriver script arguments
- **PreCondition retry vs DataProvider isolation** — fixed premature session cache invalidation during multi-row `@DataProvider` runs; cache is now only invalidated on true retries (`result.wasRetried()`)
- **LocatorAssert failure propagation** — restrict caught wait exceptions to `TimeoutException` so unexpected browser/WebDriver faults are not masked
- **Locator withText matching** — `Locator.withText` defaults to case-insensitive substring matching and respects the exact-match parameter
- **ReportPortal credential guard** — skip RP registration when credentials are missing instead of failing the suite
- **Headless viewport** — automatically configure 1920×1080 viewport for headless Chrome/Edge when `startMaximized` is set

---

## [1.0.0] — 2026-08-20

### Changed
- **Project rebrand to TestFly** — complete identity migration:
  - Maven coordinates: `io.testfly:testfly:1.0.0`
  - Java namespace: `io.testfly`
  - Config file: `testfly.yml`
  - Public API annotation: `@TestFlyApi`
  - Report artifacts: `testfly-report.html` and `testfly-metrics.json`

### Security
- API credentials moved to `${ENV_VAR}` placeholders sourced from environment or `.env` file.

### Build
- Integration tests split into `src/test/java/io/testfly/integration/` with `maven-failsafe-plugin`
- `quality` Maven profile: JaCoCo, SpotBugs, Checkstyle, PMD

---

## [0.24.0] — 2026-08-15

### Fixed
- **`execution.parallel` validation** — now delegates to TestNG's `XmlSuite.ParallelMode`; `tests` and `instances` modes accepted

---

## [0.23.0] — 2026-07-18

### Added
- **`waitForAttribute(By, attribute, value)`** — exact attribute match
- **`waitForUrlMatches(String regex)`** — URL regex match
- **`waitForTextMatches(By, String regex)`** — element text regex match

---

## [0.22.0] — 2026-06-26

### Fixed
- **Report overwrite with multiple test engines** — `testfly.reports.dir` system property honored by all report outputs

---

## [0.21.0] — 2026-06-25

### Added
- **Accessibility-first locators** — `getByRole`, `getByText`, `getByLabel`, `getByPlaceholder`, `getByTestId`, `getByAltText`, `getByTitle`
- **`getByRole(Role)`** — 38 WAI-ARIA roles with implicit + explicit matching; `.withName()`, `.withLevel()`
- **`toBy()` escape hatch** — returns synthesized Selenium `By`

```java
getByRole(Role.BUTTON).withName("Submit").click();
getByText("Welcome").isVisible();
getByTestId("checkout-btn").click();
```

---

## [0.20.0] — 2026-06-21

### Added
- **TestRail** — `@TestRailCase("C1234")` auto-pushes results; multiple IDs; auto-creates runs
- **Xray** — `@XrayTest("PROJ-123")` for Cloud (OAuth2) and Server/DC (Basic auth)
- Zero extra dependencies — both use `java.net.http.HttpClient`

---

## [0.19.0] — 2026-06-20

### Added
- **Gradle build support** — `testImplementation 'io.testfly:testfly'` + `test { useTestNG() }`
- JUnit XML auto-detects Maven vs Gradle directory layout
- `FrameworkVersion.get()` reads `MANIFEST.MF` (works with both build tools)

---

## [0.18.0] — 2026-06-20

### Added
- **Accessibility assertions (axe-core)** — `accessibility().withTags("wcag2a","wcag21aa").withLevel(Impact.SERIOUS).run()`
- axe-core 4.10.2 bundled in JAR — no CDN dependency

---

## [0.17.0] — 2026-05-19

### Added
- **Performance assertions (Core Web Vitals)** — `assertPerformance().lcp().isBelow(2500).cls().isBelow(0.1)`
- `performance.captureOnEveryTest: true` — ⚡ metrics strip in HTML report

---

## [0.16.0] — 2026-05-17

### Added
- **Test quarantine** — `testfly-quarantine.yml` for permanent test skipping; TestNG, JUnit 5, Cucumber support

---

## [0.15.0] — 2026-05-12

### Added
- **External `@TestData` sources** — `csv:`, `excel:` (Apache POI), `db:` (JDBC) prefixes
- **`TestClock`** — `clock().set("2030-01-01T00:00:00Z")` freezes browser time; `clock().advance()` fast-forwards

---

## [0.14.0] — 2026-05-04

### Added
- **BrowserStack** — `execution.mode: browserstack`; W3C capabilities; mobile devices; session URL in report
- **Sauce Labs** — `execution.mode: saucelabs`; three regions

---

## [0.13.0] — 2026-05-04

### Added
- **Email verification** — `mailbox().waitForEmail(to("user@example.com"))`; Mailhog, Mailtrap, Outlook, IMAP
- `email.assertSubject()`, `email.assertBodyContains()`, `email.extractLink()`

---

## [0.12.0] — 2026-05-03

### Added
- **`@NoBrowser`** — skip WebDriver creation; ideal for DB/API-only tests

---

## [0.11.0] — 2026-05-03

### Added
- **Multi-session testing** — `withSession("alice", () -> { ... })`; named browser sessions
- **Database assertions** — `db().assertRowExists()`, `db().query()`, `db().scalar()`; plain JDBC

---

## [0.10.0] — 2026-05-03

### Added
- **`@Retryable` for JUnit 5** — `InvocationInterceptor` with driver recreation
- **`@Retryable` for Cucumber** — full scenario rerun from step 1

---

## [0.9.0] — 2026-05-02

### Added
- **JUnit 5 support** — `TestFlyExtension` (`@ExtendWith`); full lifecycle management
- `WebDriver` injectable as test method parameter
- `BaseJUnit5Test` base class; `TestFlyLauncherListener` via ServiceLoader

---

## [0.8.0] — 2026-05-02

### Added
- **Cucumber integration** — `BaseCucumberTest` + `BaseCucumberSteps`; auto driver lifecycle per scenario
- `CucumberStepLogger` pipes Gherkin steps into HTML report

---

## [0.7.0] — 2026-04-16

### Added
- **Self-healing locators** — fallback through `id`, `name`, `text`, `class`, `data-testid`; `⚠ healed` badge
- **AI failure analysis** — root-cause + fix suggestion in HTML report via AI model
- **Flakiness prediction** — STABLE/WATCH/HIGH classification; Flakiness Radar card

---

## [0.6.0] — 2026-04-16

### Added
- **Trace viewer** — self-contained HTML trace per failed test; step timeline with screenshots
- **Visual regression** — `VisualAssert.assertScreenshot()` pixel comparison; auto-baseline
- **Device emulation** — `DeviceEmulator.emulate("iPhone 14")`; 6 built-in profiles
- **Network interception** — `NetworkMock.stub(pattern)` via CDP
- **Fluent Locator API** — `find(css)` chainable; `filter()`, `withText()`, `nth()`
- **Web-first assertions** — `assertThat(By)` with `isVisible`, `hasText`, `count`

---

## [0.5.0] — 2026-04-07

### Added
- **Shadow DOM helpers** — `shadowFind`, `shadowClick`, `shadowPierce`
- **Angular/React waits** — `waitForAngular()`, `waitForReactHydration()`
- **Enhanced HTML report** — pass rate gauge, donut chart, dark mode, search
- **Allure adapter** — opt-in Allure 2 JSON results
- **Slack / Teams notifications** — webhook-based post-suite summary
- **`@DependsOnApi`** — skip test if endpoint unreachable

---

## [0.4.0] — 2026-03-28

### Added
- **Schema validation** — `res.assertSchema("schemas/user.json")`
- **`@UseAuth` annotation** — apply named auth strategy from config
- **OAuth2 client credentials** — `ApiAuth.oauth2()` with token caching

---

## [0.3.0] — 2026-03-25

### Added
- **`BaseApiTest`** — pure API testing without browser
- **`ApiClient`** — fluent HTTP client (JDK `HttpClient`)
- **`ApiResponse`** — JSONPath extraction, fluent assertions
- **`ApiAuth`** — Bearer token and Basic auth strategies
- **`ScenarioContext`** / **`SuiteContext`** — thread-local and global state stores

---

## [0.2.0] — 2026-03-22

### Added
- **`@TestData`** — annotation-driven test data from JSON/YAML; env overrides
- **Browser matrix** — run every test on every browser in one `mvn test`
- **`SessionCache`** — cross-thread session reuse
- **Soft assertions** — collect failures, flush at test end

---

## [0.1.0] — 2026-03-16

### Added
- **`BaseTest`** — test base class with framework lifecycle
- **`BasePage`** — page object base: `click`, `type`, `getText`, `withinFrame`
- **`WaitEngine`** — centralized explicit waits
- **`Locator`** — fluent auto-waiting locator chain
- **`StepLogger`** — named steps with timestamps and screenshots
- **`DriverManager`** — thread-local WebDriver lifecycle
- **`testfly.yml`** — convention-over-configuration YAML
- **HTML report** — tabbed dashboard, pass rate gauge, retry badges
- **JUnit XML** + **CI auto-detection** (GitHub Actions, Jenkins, CircleCI, GitLab CI)
- **Build quality gates** — pass-rate and flaky-test thresholds
- **Plugin system** — SPI-based `TestFlyPlugin`, custom driver providers, report adapters
- **`@PreCondition`** — session-aware pre-conditions with cookie caching
- **`@Retryable`** — per-method retry with config
- Chrome + Firefox providers with auto download directory
- Console error collector, download manager, iFrame helpers, alert handling
