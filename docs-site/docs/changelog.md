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

#### Config

```yaml
api:
  baseUrl: https://api.example.com
  timeoutSeconds: 30
  truncationLimit: 300
  retry:
    enabled: true
    maxAttempts: 3
    backoffMs: 500
    retryOnStatus: [502, 503, 504]
    retryOnException: true
```

#### Usage

```java
// Query params + timeout + assertions
apiClient().get("/users")
    .queryParam("page", 1)
    .queryParam("limit", 10)
    .timeout(60)
    .send()
    .assertStatus(200)
    .assertDurationLessThan(500)
    .assertHeaderPresent("Content-Type")
    .assertJsonExists("$.data")
    .assertJsonArraySize("$.data", 10);

// Cookie jar
apiClient().post("/login").body(creds).withCookies().send().assertStatus(200);
apiClient().get("/profile").withCookies().send().assertStatus(200);

// Interceptors
ApiClient.addRequestInterceptor(builder ->
    builder.header("X-Correlation-Id", UUID.randomUUID().toString()));
```

---

### Added — Report Portal Launch Enrichment

- **Auto run type detection** — `SuiteExecutionListener` scans suite test classes; `BaseApiTest` subclasses → "API", otherwise → "Web"; overridable via `reporting.reportportal.type: api|web|auto`
- **Enriched launch name** — format: `<configured-name> — <API|Web> | <env> | <timestamp>`
- **Enriched description** — multi-line with run type, base URL (context-aware), environment, triggered-by user@hostname, CI platform and build info
- **New config field** — `reporting.reportportal.type` (auto/api/web)
- **CI platform detection** — GitHub Actions, Jenkins, GitLab CI, CircleCI, Travis CI, Bitbucket Pipelines, Azure Pipelines

#### Example Output

**Launch name:** `Demo Web - Dev — API | dev | 2026-08-22 15:30`

**Description:**
```
Automated test execution powered by TestFly

Run type: API
Base URL: https://fakeapi.net
Environment: dev
Triggered by: hagul@MacBook-Pro.local
CI: Jenkins #42
Build URL: https://jenkins.example.com/job/demo/42
```

#### Config

```yaml
reporting:
  reportPortal:
    enabled: true
    endpoint: ${REPORTPORTAL_ENDPOINT}
    apiKey: ${REPORTPORTAL_API_KEY}
    project: demo-web
    launch: "Demo Web - Dev"
    description: "Automated test execution powered by TestFly"
    attributes: "env:dev"
    type: auto    # auto | api | web
    mode: default
```

---

### Added — JUnit 5 → Report Portal Integration

- **`agent-java-junit5` dependency** — optional; Report Portal's JUnit 5 agent for pushing test results
- **`ReportPortalJUnit5Bridge`** — reflection-based bridge that loads `ReportPortalExtension` when on classpath
- **`TestFlyExtension` RP integration** — automatically pushes JUnit 5 test results to Report Portal; same replay pattern as TestNG's `SuiteExecutionListener`

---

### Changed

- **Default launch name** — `"TestFly Launch"` → `"TestFly Suite"`
- **Default description** — `"Automated TestFly test execution"` → `"Automated test execution powered by TestFly"`
- **Base URL in description** — now context-aware: Web runs show `execution.baseUrl`, API runs show `api.baseUrl`

### Fixed

- **OAuth2 token cache race condition** — double-checked locking prevents thundering herd on expired tokens
- **Dark theme text visibility** — CSS safety nets for all Prism token types; fixed invisible code block text; dark mode overrides for search dropdown, tabs, collapsible content, badges, admonitions, footer
- **`footer.style: 'light'`** — CSS override for Infima's `.footer--light` in dark mode

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
- Moved API credentials out of committed YAML. `testfly.yml` now uses `${DEEPSEEK_API_KEY}` and `${REPORTPORTAL_API_KEY}` placeholders.

### Build
- Split integration tests from the unit-test suite. Tests that need real backends moved to `src/test/java/io/testfly/integration/` and run via `maven-failsafe-plugin` with `mvn verify -Preal-backends`.
- Added `quality` Maven profile enabling JaCoCo, SpotBugs, Checkstyle, and PMD.
