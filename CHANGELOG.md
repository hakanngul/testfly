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
- **Request/Response interceptors** — `ApiClient.addRequestInterceptor(builder -> builder.header("X-Correlation-Id", uuid))` and `ApiClient.addResponseInterceptor(response -> metrics.record(...))`; global, thread-safe, `clearInterceptors()` to reset
- **Cookie jar** — `apiClient().post("/login").withCookies().send()` captures `Set-Cookie` headers; subsequent `withCookies()` requests auto-send them; thread-local, `clearCookies()` to reset
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
- **`ReportPortalJUnit5Bridge`** — reflection-based bridge that loads `ReportPortalExtension` when on classpath; delegates `beforeAll`, `beforeEach`, `afterTestExecution`, `testFailed`, `testSuccessful`, `afterAll` lifecycle events
- **`TestFlyExtension` RP integration** — automatically pushes JUnit 5 test results to Report Portal when `reporting.reportportal.enabled: true` and `agent-java-junit5` is on classpath; same replay pattern as TestNG's `SuiteExecutionListener`

### Changed

- **Default launch name** — `"TestFly Launch"` → `"TestFly Suite"`
- **Default description** — `"Automated TestFly test execution"` → `"Automated test execution powered by TestFly"`
- **Base URL in description** — now context-aware: Web runs show `execution.baseUrl`, API runs show `api.baseUrl`, mixed runs show both

### Fixed

- **OAuth2 token cache race condition** — double-checked locking on `ConcurrentHashMap` prevents thundering herd when multiple threads hit an expired token simultaneously
- **Dark theme text visibility** — added CSS safety nets for all Prism token types in dark mode; fixed invisible code block text (parentheses, YAML keys, operators); added dark mode overrides for search dropdown, tabs, collapsible content, badges, admonitions, footer, hash links, TOC border
- **`footer.style: 'light'`** — added safety-net CSS override for Infima's `.footer--light` class in dark mode

### Internal

- **643 unit tests** — added `ApiClientFeaturesTest` (20 tests), `ApiResponseAssertionsTest` (22 tests), and 13 new `ReportPortalPropertiesWriterTest` tests for launch enrichment

---

## [1.0.0] — 2026-08-20

### Changed
- **Project rebrand to TestFly** — complete identity migration from Selenium Boot:
  - Maven coordinates changed to `io.testfly:testfly:1.0.0`
  - Java namespace changed to `io.testfly`
  - Configuration file renamed to `testfly.yml`
  - Public API annotation renamed to `@TestFlyApi`
  - Report artifacts renamed to `testfly-report.html` and `testfly-metrics.json`
  - Documentation, CI workflows, and MCP tooling rebranded under TestFly
  - No functional breaking changes; the same Selenium-based ecosystem continues under the new identity.

### Security
- Moved API credentials out of committed YAML. `testfly.yml` now uses `${DEEPSEEK_API_KEY}` and `${REPORTPORTAL_API_KEY}` placeholders. Real values are sourced from environment variables or a local `.env` file.

### Build
- Split integration tests from the unit-test suite. Tests that need real backends (DeepSeek, ReportPortal, live browsers/APIs) moved to `src/test/java/io/testfly/integration/` and run via `maven-failsafe-plugin` with `mvn verify -Preal-backends`.
- Added `quality` Maven profile (`mvn clean verify -Pquality`) enabling JaCoCo, SpotBugs, Checkstyle, and PMD.
