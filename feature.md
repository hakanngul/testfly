# TestFly — Feature Catalog

This document is a living inventory of TestFly capabilities. It covers what the framework does today, what was added in recent iterations, and the features planned for upcoming releases.

For implementation status details, see [`implementation-status.md`](implementation-status.md) and [`ROADMAP.md`](ROADMAP.md).

---

## Current Features (v1.0.0)

### Core Framework

- **Convention over configuration** — minimal `testfly.yml` + sensible defaults via `TestFlyDefaults`
- **Single YAML configuration** — `testfly.yml` with environment profiles (`testfly-staging.yml`, `testfly-prod.yml`)
- **Framework-managed WebDriver lifecycle** — per-test and per-suite modes
- **Thread-local driver isolation** — safe parallel execution
- **Automatic retry** — `@Retryable` annotation + global retry policy
- **Parallel execution** — TestNG parallel modes configured from YAML
- **Accessibility-first locators** — `getByRole`, `getByText`, `getByLabel`, `getByPlaceholder`, `getByTestId`, `getByAltText`, `getByTitle`
- **Smart locator fallback** — `SmartLocator` tries multiple strategies until one resolves
- **Self-healing locators** — automatic fallback when a locator fails in `WaitEngine`
- **Step logging** — named steps with timestamps and per-step screenshots
- **Soft assertions** — collect multiple failures before failing a test

### Web Testing

- **Fluent `Locator` API** — `find(".btn").withText("Save").nth(0).click()`
- **Auto-waiting actions** — every terminal action waits for the required element state
- **WaitEngine** — centralized explicit waits with 10+ built-in conditions
- **Screenshot capture on failure** — embedded in HTML report
- **Page source capture on failure**
- **Console error capture** — Chrome logs + Firefox shim
- **Fail-on-console-error mode**
- **File download testing** — `DownloadManager` polls download directory
- **File upload helper**
- **iFrame and Shadow DOM helpers**
- **Browser storage helpers** — cookies, localStorage, sessionStorage
- **Clipboard helper**
- **Geo-location mocking**
- **Device emulation**
- **Browser matrix / device profiles**
- **Multi-session testing** — run two browsers in one test (`withSession`)
- **Visual regression** — baseline/diff comparisons
- **Accessibility testing** — axe-core wrapper and assertions
- **Performance Core Web Vitals** — LCP, FCP, CLS, TTFB capture and assertions

### API Testing

- **BaseApiTest**
- **Fluent `ApiClient`** — chainable HTTP calls
- **ApiResponse assertions** — status, body, JSONPath, schema validation
- **Auth strategies** — bearer, basic, OAuth2
- **Hybrid UI + API tests** — same suite, same config
- **JSON Schema validation** — optional `json-schema-validator`

### Reporting & Observability

- **HTML report** — `target/testfly-report.html` with pass-rate gauge, timeline, dark mode
- **JUnit XML** — `target/surefire-reports/TEST-TestFly.xml`
- **Execution metrics JSON** — `target/testfly-metrics.json`
- **Metrics history** — historical copies in `target/metrics-history/`
- **Flakiness analyzer** — detect and score flaky tests
- **Allure adapter** — writes Allure 2 results to `target/allure-results/`
- **Slack / Teams notification adapter**
- **ReportPortal integration** — live launch dashboards (added in recent work)

### CI/CD & Enterprise

- **CI environment detection** — GitHub Actions, Jenkins, CircleCI, GitLab CI, Travis, TeamCity, Bitbucket
- **Auto headless in CI**
- **Auto thread-count tuning** from CPU cores
- **Build quality gates** — pass-rate threshold, max flaky tests
- **Docker-friendly flags** — auto-applied in containers
- **Sample CI templates** — `.github/workflows/testfly.yml`, `ci/Jenkinsfile`

### Extensibility

- **Plugin system** — `TestFlyPlugin` + `PluginRegistry`
- **Custom driver providers** — `NamedDriverProvider` + `DriverProviderRegistry`
- **Custom report adapters** — `ReportAdapter` + `ReportAdapterRegistry`
- **Lifecycle hooks** — `ExecutionHook` + `HookRegistry`
- **Programmatic defaults** — `TestFlyDefaults`
- **Backward compatibility contract** — `@TestFlyApi` annotation and policy

### Test Data & Integrations

- **Email verification** — Mailhog, Mailtrap, Outlook, IMAP
- **Database assertions** — `DbClient` and plain JDBC assertions
- **External test data** — CSV, Excel, DB via `@TestData`
- **Test management** — TestRail and Xray result push
- **Clock mocking** — `TestClock` header injection
- **Network interception / stubbing** — CDP-based `NetworkMock`
- **Tracing** — execution traces
- **Screen recording** — test session recordings
- **Quarantine support** — skip known-flaky tests via tag/config

### AI

- **AI failure analysis** — optional `AiFailureAnalyzer`
- **MCP server contract** *(coming soon)* — framework-native Java codegen for the TestFly MCP server

### Bridges

- **JUnit 5 bridge** — `BaseJUnit5Test`, `@EnableTestFly`, `TestFlyExtension`
- **Cucumber bridge** — `BaseCucumberTest`, `BaseCucumberSteps`, hooks

---

## Recently Added (targeted for v1.1.0)

### 1. ReportPortal Integration

Real-time TestNG result streaming to [ReportPortal](https://reportportal.io/).

- Optional `com.epam.reportportal:agent-java-testng` dependency
- `reporting.reportportal` config block in `testfly.yml`
- Runtime property injection (`rp.endpoint`, `rp.api.key`, etc.)
- Dynamic listener registration via reflection
- Console summary with dashboard URL

See [`docs-site/docs/reporting/report-portal.md`](docs-site/docs/reporting/report-portal.md).

### 2. `find()` Locator Alias

A new, more Java-idiomatic alias for the fluent locator API.

- `find(String css)` and `find(By by)` added to `BaseTest`, `BasePage`, `BaseJUnit5Test`, `BaseCucumberSteps`
- Old `$()` methods deprecated with `@Deprecated(since = "1.1.0", forRemoval = true)`
- Documentation examples migrated to `find()`
- Backward compatible: existing `$()` calls continue to work

### 3. CI Metadata Integration

Structured CI/CD metadata capture for richer reports and downstream tooling.

- `io.testfly.ci.CiMetadata` value object + provider-aware `CiEnvironmentDetector.captureMetadata()`
- Supports GitHub Actions, Jenkins, GitLab CI, CircleCI, Travis CI, TeamCity, Bitbucket Pipelines, and generic `CI`
- Metadata embedded in `target/testfly-metrics.json`, HTML report Build Metadata card, and JUnit XML `<properties>`
- Configurable via `ci.captureMetadata` in `testfly.yml`

---

## Future Roadmap

Items from [`ROADMAP.md`](ROADMAP.md) that are actively open for contribution:

### Documentation & Discoverability

- ✅ Per-page SEO descriptions — completed; all docs pages include `description:` frontmatter
- ✅ Additional "Why" pages — `why-testfly`, `why-not-plain-selenium`, `why-not-playwright`, `why-accessibility-first`, `why-waitengine`
- ✅ Recipes section — `upload-a-file`, `download-and-verify-a-pdf`, `handle-iframes`, `handle-shadow-dom`, `tables`, `infinite-scroll`, `oauth-sso`, `alerts`, `drag-and-drop`
- ✅ Migration guides — `from-selenium-testng`, `from-webdrivermanager`, `coming-from-playwright`, `from-selenide`, `from-serenity`
- ✅ Homepage before/after visual component — Plain Selenium vs TestFly comparison on homepage
- ✅ SEO hygiene — sitemap plugin configured; homepage title tightened
- 📋 Complete Turkish translation of the docs site content — translate the 60+ markdown pages under `docs-site/docs/` into `docs-site/i18n/tr/docusaurus-plugin-content-docs/current/`. Priority pages: `intro.md`, `getting-started.md`, `configuration.md`

### Framework & Ecosystem

- ✅ More built-in `WaitEngine` conditions — `waitForEnabled`, `waitForDisabled`, `waitForSelected`, `waitForNumberOfWindowsToBe`, `waitForFrameAvailableAndSwitchToIt`, `waitForMinimumElementCount`
- ✅ First-class Edge and Safari driver providers via SPI — `LocalEdgeDriverProvider`, `LocalSafariDriverProvider`
- 🔄 Continuous alignment with `testfly-mcp` codegen *(coming soon)*

### Quality & Platform

- ✅ Grow unit-test coverage for untested paths — `WaitEngine`, `DriverProviderFactory`, and `BrowserArgumentValidator` coverage expanded
- ✅ Blocking session queue instead of fail-fast when `maxActiveSessions` is reached — implemented via fair Semaphore in `DriverManager`
- Keep consumer sample project (`testfly-test`) in sync with new features

---

## How to Request a Feature

1. Check this catalog and [`ROADMAP.md`](ROADMAP.md) to see if it is already planned.
2. Open a [Discussion](https://github.com/hakanngul/testfly/discussions) for early feedback.
3. Open an issue using the feature request template.
4. Read [`CONTRIBUTING.md`](CONTRIBUTING.md) for the backward-compatibility policy before proposing API changes.

---

## Legend

| Status | Meaning |
|--------|---------|
| ✅ Stable | Implemented and maintained |
| 🔄 In Progress | Active work happening |
| 📋 Planned | Roadmap item, open for contribution |
