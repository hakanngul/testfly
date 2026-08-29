# Changelog

All notable changes to **TestFly** are documented in this file.

The format is loosely based on [Keep a Changelog](https://keepachangelog.com/),
and this project adheres to [Semantic Versioning](https://semver.org/).

---

## Unreleased

### Changed
- **SessionCache rename** — `browser.SessionCache` → `BrowserSessionCache`, `precondition.SessionCache` → `PreconditionSessionCache` to eliminate naming ambiguity

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

### Added — Report Portal Enhancements

- **Auto run type detection** — suite test classes scanned; `BaseApiTest` → "API", otherwise → "Web"; `reporting.reportportal.type: api|web|auto`
- **Enriched launch name** — `<name> — <API|Web> | <env> | <timestamp>`
- **Enriched description** — run type, context-aware base URL (Web→execution.baseUrl, API→api.baseUrl), environment, user@hostname, CI platform + build info
- **CI platform detection** — GitHub Actions, Jenkins, GitLab CI, CircleCI, Travis CI, Bitbucket Pipelines, Azure Pipelines
- **JUnit 5 → Report Portal** — `agent-java-junit5` dependency + `ReportPortalJUnit5Bridge` reflection bridge; JUnit 5 tests push results to RP automatically

### Fixed

- **OAuth2 token cache race condition** — double-checked locking prevents thundering herd on expired tokens
- **RP launch naming** — context-aware base URL selection based on run type (Web vs API)
- **Dark theme text visibility** — CSS safety nets for all Prism token types; fixed invisible code block text; dark mode overrides for search dropdown, tabs, collapsible, badges, footer

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
  - Fluent locator API renamed from `$()` to `find()` across all docs and source

### Added
- **Report Portal integration** — zero-boilerplate opt-in via `testfly.yml`; `ReportPortalPropertiesWriter` converts YAML config to RP system properties; `ReportPortalReportAdapter` logs dashboard URL and launch summary after suite; `SuiteExecutionListener` dynamically registers RP TestNG listener with manual lifecycle replay (`onExecutionStart` + `onStart`); Guice dependency for TestNG runtime listener registration; SLF4J 2.x + logback-classic + RP logger for structured test observability
- **RP failure artifacts** — screenshots and AI failure analysis sent to Report Portal while the test item is still open (before RP closes it); `ReportPortalAttachmentSender` handles in-flight attachment delivery
- **RP Cucumber 7 plugin** — auto-registers `com.epam.reportportal.cucumber.ScenarioReporter` when RP is enabled and Cucumber agent is on classpath; Given/When/Then steps reported as nested items
- **Pluggable AI provider architecture** — `AiProvider` interface with `AiProviderRegistry`; `ClaudeProvider` for Anthropic Claude; `OpenAiCompatibleProvider` for DeepSeek, Ollama, Gemini, and any OpenAI-compatible API; `ai.provider` and `ai.baseUrl` config options
- **`.env` credential handling** — `DotEnvLoader` resolves `${VAR}` and `${VAR:-default}` placeholders from `.env` file or environment variables; API keys and endpoints stay out of committed YAML
- **CI metadata detection** — `CiEnvironmentDetector` auto-detects GitHub Actions, Jenkins, CircleCI, GitLab CI; forces headless, tunes thread count to CPU cores; container detection for Docker/Kubernetes
- **Chrome options builder** — extracted Chrome options construction; password manager disabled by default
- **StepLogger across assertions** — every assertion, DB query, and interaction method logs a named step for full test observability in HTML report and Report Portal
- **Turkish language support** — complete documentation site translation; dynamic site title and tagline rendering based on locale; `i18n/tr/` content tree
- **Apple-inspired docs redesign** — light/dark theme with Apple Design tokens; frosted-glass navbar; bento grid feature cards; scroll-reveal animations
- **Integration test suite** — `src/test/java/io/testfly/integration/` for real-backend tests; `maven-failsafe-plugin` with `-Preal-backends` profile
- **Comprehensive documentation** — migration guides, configuration recipes, why-testfly pages, feature catalog, testfly.yml configuration guide, comprehensive test examples

### Security
- API credentials moved to `${ENV_VAR}` placeholders; `.env` file support; credentials never committed to YAML

### Build
- `quality` Maven profile: JaCoCo, SpotBugs, Checkstyle, PMD

---

## [0.24.0] — 2026-08-15

### Fixed
- **`execution.parallel` validation** — delegates to TestNG's `XmlSuite.ParallelMode`; `tests` and `instances` modes now accepted

---

## [0.23.0] — 2026-07-18

### Added
- **`waitForAttribute(By, attribute, value)`** — exact attribute match
- **`waitForUrlMatches(String regex)`** — URL regex match
- **`waitForTextMatches(By, String regex)`** — element text regex match

---

## [0.22.0] — 2026-06-26

### Fixed
- **Report overwrite with multiple test engines** — `testfly.reports.dir` honored by all report outputs; `ReportPaths` helper centralizes path resolution

---

## [0.21.0] — 2026-06-25

### Added
- **Accessibility-first locators** — `getByRole`, `getByText`, `getByLabel`, `getByPlaceholder`, `getByTestId`, `getByAltText`, `getByTitle`
- **`getByRole(Role)`** — 38 WAI-ARIA roles; `.withName()`, `.withLevel()` refinement
- **`toBy()` escape hatch** — returns synthesized Selenium `By`

---

## [0.20.0] — 2026-06-21

### Added
- **TestRail** — `@TestRailCase("C1234")`; auto-creates runs; PASSED/FAILED/SKIPPED mapping
- **Xray** — `@XrayTest("PROJ-123")`; Cloud (OAuth2) and Server/DC (Basic auth); batch import

---

## [0.19.0] — 2026-06-20

### Added
- **Gradle build support** — `testImplementation 'io.testfly:testfly'`; JUnit XML auto-detects Maven vs Gradle
- **`FrameworkVersion`** reads `MANIFEST.MF` for cross-build-tool version reporting

---

## [0.18.0] — 2026-06-20

### Added
- **Accessibility assertions (axe-core)** — `accessibility().withTags("wcag2a","wcag21aa").withLevel(Impact.SERIOUS).run()`; axe-core 4.10.2 bundled in JAR

---

## [0.17.0] — 2026-05-19

### Added
- **Performance assertions (Core Web Vitals)** — `assertPerformance().lcp().isBelow(2500).cls().isBelow(0.1)`
- `performance.captureOnEveryTest: true` — ⚡ metrics strip in HTML report

---

## [0.16.0] — 2026-05-17

### Added
- **Test quarantine** — `testfly-quarantine.yml`; TestNG, JUnit 5, Cucumber support; `@quarantine` tag

---

## [0.15.0] — 2026-05-12

### Added
- **External `@TestData` sources** — `csv:`, `excel:` (Apache POI), `db:` (JDBC) prefixes
- **`TestClock`** — `clock().set("2030-01-01T00:00:00Z")` freezes browser time; `clock().advance()`

---

## [0.14.0] — 2026-05-04

### Added
- **BrowserStack** — `execution.mode: browserstack`; W3C capabilities; mobile devices; session URL in report
- **Sauce Labs** — `execution.mode: saucelabs`; three regions

---

## [0.13.0] — 2026-05-04

### Added
- **Email verification** — `mailbox().waitForEmail(to("user@example.com"))`; Mailhog, Mailtrap, Outlook (Graph API), IMAP
- `email.assertSubject()`, `email.assertBodyContains()`, `email.extractLink()`

---

## [0.12.0] — 2026-05-03

### Added
- **`@NoBrowser`** — skip WebDriver creation; all other framework services active

---

## [0.11.0] — 2026-05-03

### Added
- **Multi-session testing** — `withSession("alice", () -> { ... })`; named browser sessions
- **Database assertions** — `db().assertRowExists()`, `db().query()`, `db().scalar()`; plain JDBC; named datasources

---

## [0.10.0] — 2026-05-03

### Added
- **`@Retryable` for JUnit 5** — `InvocationInterceptor` with driver recreation
- **`@Retryable` for Cucumber** — full scenario rerun from step 1

---

## [0.9.0] — 2026-05-02

### Added
- **JUnit 5 support** — `TestFlyExtension` (`@ExtendWith`); full lifecycle; `WebDriver` parameter injection
- `BaseJUnit5Test` base class; `TestFlyLauncherListener` via ServiceLoader

---

## [0.8.0] — 2026-05-02

### Added
- **Cucumber / BDD** — `BaseCucumberTest` + `BaseCucumberSteps`; auto driver lifecycle per scenario
- `CucumberStepLogger` pipes Gherkin steps into HTML report

---

## [0.7.0] — 2026-04-16

### Added
- **Self-healing locators** — `locators.selfHealing: true`; fallback through `id`, `name`, `text`, `class`, `data-testid`
- **AI failure analysis** — root-cause + fix suggestion via AI model; embedded in HTML report
- **Flakiness prediction** — STABLE/WATCH/HIGH classification; Flakiness Radar card

---

## [0.6.0] — 2026-04-16

### Added
- **Trace viewer** — self-contained HTML trace per failed test; clickable step timeline with screenshots
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
- **`@DependsOnApi`** — skip test if endpoint unreachable; cached per suite

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
- **`ApiAuth`** — Bearer and Basic auth strategies
- **`ScenarioContext`** / **`SuiteContext`** — thread-local and global state stores

---

## [0.2.0] — 2026-03-22

### Added
- **`@TestData`** — annotation-driven test data from JSON/YAML; env overrides
- **Browser matrix** — run every test on every browser
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
- Chrome + Firefox providers, console error collector, download manager, iFrame helpers, alert handling
