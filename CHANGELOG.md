# Changelog

All notable changes to **TestFly** are documented in this file.

The format is loosely based on [Keep a Changelog](https://keepachangelog.com/),
and this project adheres to [Semantic Versioning](https://semver.org/).

---

## Unreleased

### Added — API Testing Improvements

- **HTTP-level retry** (`api.retry.*`) — config-driven retry for transient network failures and configurable status codes (502/503/504 by default); exponential backoff; `retryOnException` toggle; retry attempts are step-logged with `WARN` status
- **Per-request timeout override** — `apiClient().get("/slow-report").timeout(120).send()` overrides the default 30s timeout for a single request
- **Query parameter builder** — `apiClient().get("/users").queryParam("page", 1).queryParam("limit", 10).send()` with automatic URL encoding; replaces manual string concatenation
- **Request/Response interceptors** — `ApiClient.addRequestInterceptor()` and `ApiClient.addResponseInterceptor()`; global, thread-safe
- **Cookie jar** — `apiClient().post("/login").withCookies().send()` captures `Set-Cookie` headers; subsequent `withCookies()` requests auto-send them; thread-local
- **Response time assertion** — `res.assertDurationLessThan(500)` and `res.assertDurationLessThan(2, TimeUnit.SECONDS)`
- **Header assertions** — `res.assertHeader("Content-Type", "application/json")` and `res.assertHeaderPresent("X-Request-Id")`
- **Body regex assertion** — `res.assertBodyMatches("\\d{4}-\\d{2}-\\d{2}")` with dotall mode
- **JSON structure assertions** — `assertJsonExists("$.path")`, `assertJsonNull("$.field")`, `assertJsonArraySize("$.items", 3)`
- **Configurable truncation limit** — `api.truncationLimit: 1000` controls body truncation in assertion error messages (default 300)

### Added — Report Portal Launch Enrichment

- **Auto run type detection** — `SuiteExecutionListener` scans suite test classes; `BaseApiTest` subclasses → "API", otherwise → "Web"; overridable via `reporting.reportportal.type: api|web|auto`
- **Enriched launch name** — format: `<configured-name> — <API|Web> | <env> | <timestamp>`; e.g. `Demo Web - Dev — API | dev | 2026-08-22 15:30`
- **Enriched description** — multi-line with run type, base URL (Web→execution.baseUrl, API→api.baseUrl), environment, triggered-by user@hostname, CI platform and build info
- **New config field** — `reporting.reportportal.type` (auto/api/web) controls run type detection behavior
- **CI platform detection** — GitHub Actions, Jenkins, GitLab CI, CircleCI, Travis CI, Bitbucket Pipelines, Azure Pipelines

### Added — JUnit 5 → Report Portal Integration

- **`agent-java-junit5` dependency** — optional; Report Portal's JUnit 5 agent for pushing test results
- **`ReportPortalJUnit5Bridge`** — reflection-based bridge that loads `ReportPortalExtension` when on classpath; delegates full lifecycle events
- **`TestFlyExtension` RP integration** — automatically pushes JUnit 5 test results to Report Portal when `reporting.reportportal.enabled: true`

### Changed

- Default launch name: `"TestFly Launch"` → `"TestFly Suite"`
- Default description: `"Automated TestFly test execution"` → `"Automated test execution powered by TestFly"`
- Base URL in RP description now context-aware: Web runs show `execution.baseUrl`, API runs show `api.baseUrl`

### Fixed

- **OAuth2 token cache race condition** — double-checked locking prevents thundering herd on expired tokens
- **Dark theme text visibility** — CSS safety nets for all Prism token types; fixed invisible code block text; dark mode overrides for search dropdown, tabs, collapsible content, badges, footer
- **`footer.style: 'light'`** — CSS override for Infima's `.footer--light` in dark mode

---

## [1.0.0] — 2026-08-20

### Changed
- **Project rebrand to TestFly** — complete identity migration:
  - Maven coordinates: `io.testfly:testfly:1.0.0`
  - Java namespace: `io.testfly`
  - Configuration file: `testfly.yml`
  - Public API annotation: `@TestFlyApi`
  - Report artifacts: `testfly-report.html` and `testfly-metrics.json`
  - All documentation, CI workflows, and MCP tooling rebranded

### Security
- API credentials moved out of committed YAML. `testfly.yml` uses `${DEEPSEEK_API_KEY}` and `${REPORTPORTAL_API_KEY}` placeholders sourced from environment variables or `.env` file.

### Build
- Integration tests split from unit-test suite. Real-backend tests moved to `src/test/java/io/testfly/integration/` and run via `maven-failsafe-plugin` with `mvn verify -Preal-backends`.
- Added `quality` Maven profile enabling JaCoCo, SpotBugs, Checkstyle, and PMD.

---

## [0.24.0] — 2026-08-15

### Fixed
- **`execution.parallel` validation now delegates to TestNG's `XmlSuite.ParallelMode`** — `tests` and `instances` are legitimate TestNG modes that were rejected by a hand-written allowlist with a misleading "Parallel execution configuration missing" error. Now anything TestNG accepts, TestFly accepts.

---

## [0.23.0] — 2026-07-18

### Added
- **Three new `WaitEngine` conditions**:
  - `waitForAttribute(By, attribute, value)` — exact attribute match
  - `waitForUrlMatches(String regex)` — URL regex match
  - `waitForTextMatches(By, String regex)` — element text regex match

---

## [0.22.0] — 2026-06-26

### Fixed
- **Report overwrite with multiple test engines** — metrics JSON, HTML report, and metrics history now honor `testfly.reports.dir` system property. When TestNG (Surefire) and JUnit 5 (Failsafe) run in the same build, each engine can target its own directory.

---

## [0.21.0] — 2026-06-25

### Added
- **Accessibility-first locators** — Playwright-style semantic locators on `BaseTest` and `BasePage`: `getByRole`, `getByText`, `getByLabel`, `getByPlaceholder`, `getByTestId`, `getByAltText`, `getByTitle`
- **`getByRole(Role)`** — 38 WAI-ARIA roles with implicit HTML element matching and explicit `role="…"` attribute support; `.withName()` and `.withLevel()` refinement
- **Case-insensitive substring by default**, exact opt-in via `.exact()`
- **`toBy()` escape hatch** — every semantic locator returns its synthesized Selenium `By`
- **Configurable test-id attribute** — `locators.testIdAttribute` in `testfly.yml` (default `data-testid`)

---

## [0.20.0] — 2026-06-21

### Added
- **TestRail integration** — `@TestRailCase("C1234")` pushes results automatically; multiple IDs supported; auto-creates named runs; maps PASSED/FAILED/SKIPPED
- **Xray integration** — `@XrayTest("PROJ-123")` for Xray Cloud (OAuth2) and Xray Server/DC (HTTP Basic); batch-import at suite end
- **Zero extra dependencies** — both clients use `java.net.http.HttpClient`
- **TestNG + JUnit 5** — same annotations work in both frameworks

---

## [0.19.0] — 2026-06-20

### Added
- **Gradle build support** — `testImplementation 'io.testfly:testfly'` + `test { useTestNG() }`; Groovy + Kotlin DSL samples; JUnit 5 bridge; parallel config
- **JUnit XML auto-detection** — `JUnitXmlReporter` detects Maven (`target/`) vs Gradle (`build/`) and writes to the correct directory
- **Cross-build-tool version** — `FrameworkVersion.get()` reads `MANIFEST.MF` (works with both Maven and Gradle)

---

## [0.18.0] — 2026-06-20

### Added
- **Accessibility assertions (axe-core)** — `accessibility().withTags("wcag2a","wcag21aa").withLevel(Impact.SERIOUS).excluding("#cookie-banner").run()`; axe-core 4.10.2 bundled in JAR
- Detailed `AssertionError` with rule ID, severity, fix guidance, element selector, and docs URL
- `.collect()` for raw `AccessibilityResult` access without asserting

---

## [0.17.0] — 2026-05-19

### Added
- **Performance assertions (Core Web Vitals)** — `assertPerformance().lcp().isBelow(2500).fcp().isBelow(1800).ttfb().isBelow(600).cls().isBelow(0.1)`
- Browser-native `window.performance` API — no extra dependency
- `performance.captureOnEveryTest: true` shows ⚡ metrics strip in HTML report

---

## [0.16.0] — 2026-05-17

### Added
- **Test quarantine** — `testfly-quarantine.yml` lists tests to skip permanently; survives fresh CI clones; supports TestNG, JUnit 5, and Cucumber
- Class-level quarantine skips all methods in a class
- Cucumber quarantine via `@quarantine` tag or YAML entries

---

## [0.15.0] — 2026-05-12

### Added
- **External `@TestData` sources** — `csv:`, `excel:`, and `db:` prefixes; Apache POI for Excel; JDBC for database queries; row selection; type coercion
- **`TestClock`** — `clock().set("2030-01-01T00:00:00Z")` injects JS `Date` override; `clock().advance(Duration.ofDays(30))` fast-forwards; auto-reset after every test

---

## [0.14.0] — 2026-05-04

### Added
- **BrowserStack integration** — `execution.mode: browserstack`; W3C `bstack:options` capabilities; mobile device support; session dashboard URL in HTML report
- **Sauce Labs integration** — `execution.mode: saucelabs`; three regions; W3C `sauce:options` capabilities
- Existing `mode: remote` (self-hosted Grid) unchanged — all three remote modes coexist

---

## [0.13.0] — 2026-05-04

### Added
- **Email verification** — `mailbox().waitForEmail(to("user@example.com"))` polls until email arrives
- **Four backends**: Mailhog (local/Docker), Mailtrap (hosted), Outlook/Office 365 (Graph API OAuth2), IMAP (any server)
- `email.assertSubject()`, `email.assertBodyContains()`, `email.extractLink(linkText)`
- `email.autoClear: true` clears inbox before each test

---

## [0.12.0] — 2026-05-03

### Added
- **`@NoBrowser`** — skip WebDriver creation entirely; no browser opened, no screenshot; all other framework services active; ideal for DB assertions and API-only tests in `BaseTest`

---

## [0.11.0] — 2026-05-03

### Added
- **Multi-session testing** — `withSession("alice", () -> { ... })` runs lambda with named browser session; `session("name")` returns raw `WebDriver`; auto-closed at test end
- **Database assertions** — `db().assertRowExists()`, `db().assertRowCount()`, `db().query(sql).assertValue()`; plain JDBC; named datasources; per-thread connection lifecycle

---

## [0.10.0] — 2026-05-03

### Added
- **`@Retryable` for JUnit 5** — `InvocationInterceptor` retries with full driver recreation; method and class level
- **`@Retryable` for Cucumber** — entire scenario reruns from step 1 with fresh driver; retry badge in HTML report

---

## [0.9.0] — 2026-05-02

### Added
- **JUnit 5 support** — `TestFlyExtension` (`@ExtendWith`); driver lifecycle, screenshot on failure, AI analysis, trace, recording
- `WebDriver` injectable as test method parameter
- `BaseJUnit5Test` base class with `getDriver()`, `getWait()`, `open()`, `find()`, `assertThat()`, `step()`
- `TestFlyLauncherListener` generates HTML report when JUnit Platform test plan finishes
- Parallel execution via `junit-platform.properties`

---

## [0.8.0] — 2026-05-02

### Added
- **BDD / Cucumber integration** — `BaseCucumberTest` + `BaseCucumberSteps`; automatic driver lifecycle per scenario
- `CucumberStepLogger` plugin pipes Gherkin step names into HTML report timeline
- Scenario Outlines produce individual report entries per example row
- `cucumber-java` and `cucumber-testng` declared as optional

---

## [0.7.0] — 2026-04-16

### Added
- **Self-healing locators** — `locators.selfHealing: true`; fallback strategies: `id`, `name`, `text`, `class`, `data-testid`; `⚠ healed` badge in HTML report
- **AI-assisted failure analysis** — `ai.failureAnalysis: true`; calls AI model with error, stack trace, step log, URL, page title; root-cause + fix suggestion in HTML report
- **Flakiness prediction** — reads last N JSON runs; classifies as STABLE/WATCH/HIGH; Flakiness Radar card in HTML report

---

## [0.6.0] — 2026-04-16

### Added
- **Trace viewer** — `tracing.enabled: true`; self-contained HTML trace per failed test; clickable step timeline with screenshots; final screenshot; error + stack trace
- **Visual regression testing** — `VisualAssert.assertScreenshot()` pixel-by-pixel comparison; auto-baseline; configurable tolerance
- **Mobile device emulation** — `DeviceEmulator.emulate("iPhone 14")`; CDP-based on Chrome/Edge; 6 built-in profiles
- **Clipboard helpers** — `ClipboardHelper.write/read/clear()`
- **GeoLocation mock** — CDP on Chrome/Edge, JS override on Firefox
- **Network interception** — `NetworkMock.stub(pattern)` stubs API responses via CDP
- **Browser storage helpers** — `StorageHelper.localStorage()`, `sessionStorage()`, `cookies()`
- **Fluent Locator API** — `find(css)` / `find(By)` chainable; `filter()`, `withText()`, `within()`, `nth()`
- **Web-first assertions** — `assertThat(By)` with auto-retry: `isVisible`, `hasText`, `hasAttribute`, `count`

---

## [0.5.0] — 2026-04-07

### Added
- **Shadow DOM helpers** — `ShadowDom` utility + `BasePage` methods (`shadowFind`, `shadowClick`, `shadowType`, `shadowPierce`)
- **Component-aware waits** — `WaitEngine.waitForAngular()` and `waitForReactHydration()`
- **Enhanced HTML report** — pass rate gauge, donut chart, retry badges, expandable errors, filter bar, search, dark mode
- **Allure adapter** — opt-in Allure 2 JSON result files
- **Slack / Teams notifications** — webhook-based post-suite summary
- **`@DependsOnApi`** — skip test if dependent endpoint is unreachable; cached per suite

---

## [0.4.0] — 2026-03-28

### Added
- **Schema validation** — `ApiResponse.assertSchema("schemas/user.json")` validates against JSON Schema
- **`@UseAuth` annotation** — apply named auth strategy from config to any test method or class
- **`ApiAuth.oauth2()`** — OAuth2 client credentials with automatic token caching and expiry refresh
- **`ApiClient.setGlobalAuth()` / `clearGlobalAuth()`** — set auth once per suite

---

## [0.3.0] — 2026-03-25

### Added
- **`BaseApiTest`** — pure API test base class; no browser started; full framework lifecycle
- **`ApiClient`** — fluent HTTP client (Java built-in `HttpClient`); GET, POST, PUT, PATCH, DELETE; auto step-logging
- **`ApiResponse`** — JSONPath extraction (`$.user.id`), `asObject(Class)`, fluent assertions
- **`ApiAuth`** — `bearerToken(token)`, `basicAuth(user, pass)` strategies
- **`ScenarioContext`** — thread-local in-test key-value store; auto-cleared
- **`SuiteContext`** — global thread-safe store for cross-test state

---

## [0.2.0] — 2026-03-22

### Added
- **`@TestData`** — annotation-driven test data injection from JSON/YAML files; env-specific overrides
- **Browser matrix** — `browser.matrix: [chrome, firefox]` runs every test on every browser
- **`SessionCache`** — global cross-thread session reuse; reduces repeated login overhead
- **Soft assertions** — `softAssert().that(condition, "message")` collects failures; flushed at test end

---

## [0.1.0] — 2026-03-16

### Added
- **`BaseTest`** — test base class with framework lifecycle management
- **`BasePage`** — page object base: `click`, `type`, `getText`, `withinFrame`, `upload`
- **`WaitEngine`** — centralized explicit waits; `waitForVisible`, `waitForClickable`, `waitForAlert`
- **`Locator`** — fluent auto-waiting locator chain
- **`StepLogger`** — named test steps with timestamps and per-step screenshots
- **`DriverManager`** — thread-local WebDriver lifecycle; per-test or per-suite
- **`testfly.yml`** — convention-over-configuration YAML; sensible defaults for everything
- **HTML report** — tabbed Dashboard, Test Cases, Failures; pass rate gauge; retry badges
- **JUnit XML reporter** — `target/surefire-reports/`
- **CI auto-detection** — GitHub Actions, Jenkins, CircleCI, GitLab CI
- **Build quality gates** — pass-rate and flaky-test thresholds
- **Plugin system** — `TestFlyPlugin` + `PluginRegistry` via SPI
- **Custom driver providers** — `NamedDriverProvider` + `DriverProviderRegistry`
- **Custom report adapters** — `ReportAdapter` + `ReportAdapterRegistry`
- **Execution hooks** — `ExecutionHook` + `HookRegistry` lifecycle callbacks
- **`@PreCondition`** — session-aware pre-conditions with cookie + localStorage caching
- **`@Retryable`** — per-method retry; `retry.enabled` + `retry.maxAttempts` in config
- **Chrome + Firefox providers** — with auto download directory configuration
- **Console error collector** — JS error capture; optional test failure on errors
- **Download manager** — `waitForFile`, partial download detection
- **iFrame helpers** — `withinFrame`, `withinFrameIndex`, `withinFrameName`
- **Alert handling** — `acceptAlert`, `dismissAlert`, `getAlertText`
- **`@TestFlyApi`** — annotation marking stable public API with `since` version
