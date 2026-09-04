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

### Added — API Testing Improvements

- **HTTP-level retry** (`api.retry.*`) — config-driven retry for transient network failures and configurable status codes (502/503/504 by default); exponential backoff; `retryOnException` toggle
- **Per-request timeout override** — `apiClient().get("/slow-report").timeout(120).send()`
- **Query parameter builder** — `.queryParam("page", 1).queryParam("limit", 10)` with automatic URL encoding
- **Request/Response interceptors** — `ApiClient.addRequestInterceptor()` and `addResponseInterceptor()`; global, thread-safe
- **Cookie jar** — `.withCookies()` captures and auto-sends cookies across requests
- **New assertions** — `assertDurationLessThan`, `assertHeader`, `assertHeaderPresent`, `assertBodyMatches`, `assertJsonExists`, `assertJsonNull`, `assertJsonArraySize`

```yaml
api:
  retry:
    enabled: true
    maxAttempts: 3
    backoffMs: 500
    retryOnStatus: [502, 503, 504]
    retryOnException: true
```

### Added — Report Portal Launch Enrichment

- **Auto run type detection** — suite test classes scanned; `BaseApiTest` → "API", otherwise → "Web"
- **Enriched launch name** — `<name> — <API|Web> | <env> | <timestamp>`
- **Enriched description** — run type, context-aware base URL, environment, user@hostname, CI info
- **New config field** — `reporting.reportportal.type` (auto/api/web)

```yaml
reporting:
  reportPortal:
    enabled: true
    launch: "Demo Web - Dev"
    type: auto
```

### Added — JUnit 5 → Report Portal

- **`agent-java-junit5`** dependency + `ReportPortalJUnit5Bridge` reflection bridge
- JUnit 5 tests now push results to Report Portal automatically

### Fixed

- OAuth2 token cache race condition (double-checked locking)
- Dark theme code block text visibility (Prism token CSS safety nets)
- Dark theme component overrides (search, tabs, badges, footer)
- AI model defaults and validation (`ai.model` optional, provider-specific defaults, input sanitization)
- Session cache isolation and script safety (clearing localStorage prior to restore, safe script argument passing)
- PreCondition retry vs DataProvider isolation (prevent premature cache invalidation during multi-row DataProvider runs)
- LocatorAssert failure propagation (narrow caught wait exceptions to `TimeoutException`)
- Locator withText matching (case-insensitive substring default with exact-match support)

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
