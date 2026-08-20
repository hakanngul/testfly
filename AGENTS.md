# TestFly — Agent Guide

This file is intended for AI coding agents working in the `testfly` repository.
It summarizes the project's architecture, build/test workflows, code conventions, and extension points so you can be productive without guessing.

---

## Project Overview

**TestFly** is an opinionated, zero-boilerplate Java test-automation framework built on top of Selenium WebDriver.
It is published to Maven Central as a single JAR that users add as a dependency.

- **Group / Artifact:** `io.testfly:testfly`
- **Current version:** `1.0.0`
- **Java baseline:** 17 (compiled with `--release 17`)
- **Build tool:** Maven 3.8+
- **Primary test framework:** TestNG 7.9.0
- **License:** Apache License 2.0

The framework's philosophy is "the Spring Boot of Selenium":
convention over configuration, sensible defaults, minimal required YAML, and a stable public API — while never hiding raw Selenium (`WebDriver`, `By`, `WebElement`) from the user.

Key selling points:

- Framework-managed WebDriver lifecycle (per-test or per-suite)
- Thread-local driver isolation for safe parallel execution
- Auto-waiting `WaitEngine` and fluent `Locator` API
- Accessibility-first locators (`getByRole`, `getByText`, `getByLabel`, etc.)
- Automatic retry via `@Retryable`
- HTML report + JUnit XML + screenshots on failure
- API testing via `BaseApiTest` and `ApiClient`
- Optional JUnit 5 and Cucumber bridges
- Pluggable driver providers, report adapters, and lifecycle hooks via SPI

---

## Technology Stack

| Layer | Technology |
|-------|------------|
| Language | Java 17 |
| Build | Maven |
| Browser automation | Selenium Java 4.40.0 |
| Test framework | TestNG 7.9.0 |
| YAML parsing | SnakeYAML 2.2 |
| JSON processing | Jackson Databind 2.21.0 |
| Unit-test mocking | Mockito 5.11.0 |
| Optional: Cucumber | `cucumber-java` + `cucumber-testng` 7.20.1 |
| Optional: JUnit 5 | `junit-jupiter-api` + `junit-platform-launcher` 1.10.2 |
| Optional: JSON Schema | `json-schema-validator` 1.4.3 |
| Optional: IMAP email | `jakarta.mail` 2.0.1 |
| Optional: Excel data | Apache POI 5.2.5 |

Docs site:

| Layer | Technology |
|-------|------------|
| Static site generator | Docusaurus 3.5.2 |
| Runtime | Node 18+ / React 18 |
| Search | `@easyops-cn/docusaurus-search-local` |

---

## Repository Layout

```
testfly/
├── pom.xml                           # Maven build configuration
├── testfly.yml                 # Framework config for local test runs
├── README.md                         # User-facing landing page
├── CLAUDE.md                         # Maintainer cheat sheet (read it!)
├── CONTRIBUTING.md                   # PR checklist and philosophy
├── CHANGELOG.md                      # Release history
├── SECURITY.md                       # Vulnerability reporting policy
├── ci/
│   └── Jenkinsfile                   # Jenkins CI pipeline
├── docs-site/                        # Docusaurus documentation site
│   ├── package.json
│   ├── docusaurus.config.js
│   ├── docs/
│   └── src/
├── src/main/java/com/testfly/   # Framework source
└── src/test/java/com/testfly/unit/# Framework unit tests
```

### Main source packages (`src/main/java/com/testfly/`)

| Package | Responsibility |
|---------|----------------|
| `test/` | User-facing base classes: `BaseTest`, `BaseApiTest`, `BasePage`, `SmartLocator` |
| `driver/` | `DriverManager`, driver providers (Chrome, Firefox, Remote, BrowserStack, Sauce Labs), registries |
| `config/` | `ConfigurationLoader`, `TestFlyConfig`, defaults and validation |
| `lifecycle/` | `FrameworkBootstrap` — wires the framework at suite start |
| `listeners/` | TestNG listeners for suite/test execution and `@Retryable` handling |
| `hooks/` | `ExecutionHook` + `HookRegistry` lifecycle callbacks |
| `precondition/` | `@PreCondition` / `@ConditionProvider` session caching |
| `wait/` | `WaitEngine` centralized explicit waits |
| `locator/` | Fluent `Locator`, `Role`, semantic-locator synthesis |
| `assertion/` | `SeleniumAssert`, `LocatorAssert`, soft assertions |
| `client/` + `api/` | Fluent HTTP `ApiClient`, `ApiResponse`, auth strategies |
| `browser/` | `ConsoleErrorCollector`, `StorageHelper`, `GeoLocation`, `ClipboardHelper`, `DeviceEmulator` |
| `steps/` | `StepLogger` named steps + screenshots for the HTML timeline |
| `reporting/` | HTML report generator, JUnit XML, `ScreenshotManager`, report adapters (Allure, Slack, Teams, etc.) |
| `metrics/` | `ExecutionMetrics`, `TestTiming` — suite timing and outcomes |
| `ci/` | CI environment detection and build threshold enforcement |
| `extension/` | SPI plugin system: `TestFlyPlugin`, `PluginRegistry` |
| `junit5/` | Optional JUnit 5 bridge: `BaseJUnit5Test`, `EnableTestFly`, `TestFlyExtension` |
| `cucumber/` | Optional Cucumber bridge: `BaseCucumberTest`, `BaseCucumberSteps`, hooks |
| `email/` | Mailbox clients (Mailhog, Mailtrap, Outlook Graph, IMAP) |
| `db/` | `DbClient` and database assertions |
| `testdata/` | `@TestData` loaders (CSV, Excel, DB) |
| `testmanagement/` | TestRail and Xray result push |
| `accessibility/` | axe-core wrapper and assertions |
| `performance/` | Core Web Vitals collection and assertions |
| `visual/` | Visual regression assertions |
| `network/` | CDP network interception / stubbing |
| `shadow/` | Shadow DOM helpers |
| `healing/` | Self-healing locator fallback |
| `recording/` | Test session screen recordings |
| `tracing/` | Execution tracing |
| `clock/` | Browser clock mocking (`TestClock`) |
| `quarantine/` | `testfly-quarantine.yml` loader |
| `flakiness/` | Flakiness history and scoring |
| `internal/` | Framework-only context (`TestFlyContext`) |
| `exceptions/` | Framework-specific runtime exceptions |

### Tests (`src/test/java/com/testfly/unit/`)

- Pure unit tests using **TestNG + Mockito**
- No real browser is required to run the framework test suite
- All browser interactions are mocked

---

## Build and Test Commands

All commands run from the repository root.

```bash
# Compile
mvn compile

# Run the framework unit-test suite (no browser needed)
mvn test

# Full build: compile + test + package + source/javadoc jars
mvn clean verify

# Install locally for consumer-project testing
mvn clean install -DskipTests

# Run a single test class
mvn test -Dtest=ConfigurationLoaderTest

# Run a single test method
mvn test -Dtest=ConfigurationLoaderTest#testMethodName

# Run with an environment profile (uses testfly-{profile}.yml)
mvn test -Denv=staging

# Skip GPG signing during local install
mvn clean install -DskipTests -Dgpg.skip=true
```

Docs site:

```bash
cd docs-site
npm install
npm run start     # dev server
npm run build     # production build
```

---

## Configuration

Consumer projects configure the framework via a **required** YAML file at the project root: `testfly.yml`.

The minimum required config:

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

Key config blocks (all in `TestFlyConfig`):

- `execution`: `mode` (`local`/`remote`/`browserstack`/`saucelabs`), `baseUrl`, `parallel`, `threadCount`, `maxActiveSessions`, `gridUrl`, cloud credentials
- `browser`: `name`, `headless`, `arguments`, `capabilities`, `lifecycle` (`per-test`/`per-suite`), `downloadDir`, `captureConsoleErrors`, `failOnConsoleErrors`, `matrix`, `device`
- `timeouts`: `explicit`, `pageLoad`
- `retry`: `enabled`, `maxAttempts`
- `api`: `baseUrl`, `timeoutSeconds`, `logBody`, `logContext`, named auth strategies
- `ci`: `failOnPassRateBelow`, `maxFlakyTests`
- `database`: default datasource + named datasources
- `email`: provider config for Mailhog / Mailtrap / Outlook / IMAP
- `visual`: baseline/diff directories, tolerance, update-baselines flag
- `recording`: screen-recording settings
- `testmanagement`: TestRail and Xray credentials
- `quarantine`: `enabled`, `cucumberTag`
- `locators`: `selfHealing`, `testIdAttribute`
- `ai`: failure-analysis model/key settings
- `flakiness`: history runs and risk thresholds

Profiles are activated with `-Denv=<profile>` and load `testfly-<profile>.yml`.

---

## Code Style Guidelines

- **No enforced formatter** — follow the style already present in the file you are editing.
- Use explicit, descriptive names; avoid clever abbreviations.
- Prefer JDK builtins over new dependencies.
- Keep public API surfaces small; every public method is a long-term commitment.
- Mark stable public types/methods with `@TestFlyApi(since = "x.y.z")`.
- Internal implementation details belong in `*.internal.*` packages or lack the annotation.
- Utility classes should have a `private` constructor.
- Avoid `Thread.sleep()`; route waits through `WaitEngine`.
- Do not introduce static global WebDriver state.

---

## Testing Instructions

### Framework tests

```bash
mvn test
```

- Located in `src/test/java/com/testfly/unit/`
- Run with TestNG via `maven-surefire-plugin` (configured for TestNG in `pom.xml`)
- Mockito is used to mock Selenium/browser interactions
- No real browser is opened

### Consumer integration tests

A separate sample/consumer project exists at `github.com/testfly/testfly-test`.
To test framework changes end-to-end:

```bash
# 1. Install the local framework JAR
mvn clean install -DskipTests

# 2. In the consumer project, pin its pom.xml to the current framework version
# 3. Run the consumer tests
```

### CI

GitHub Actions (`.github/workflows/testfly.yml`):

1. `unit-tests` job — runs `mvn test`
2. `integration-tests` job — installs the framework, checks out `testfly/testfly-test`, pins it to the current version, and runs API demo tests

Jenkins (`ci/Jenkinsfile`):

- Compiles, runs `mvn test`, archives HTML report / metrics / JUnit XML
- On success, optionally triggers the consumer test job

---

## Public API Stability Contract

- Types and methods annotated with `@TestFlyApi` are the stable public contract.
- Do not rename, remove, or change signatures of `@TestFlyApi` elements within the same major version.
- When adding a method to a stable interface, provide a `default` implementation.
- Deprecate for at least one minor version before removing; remove only in the next major version.
- Internal classes (no annotation or in `*.internal.*`) may change freely.

Important stable entry points:

- `BaseTest`, `BasePage`, `BaseApiTest`
- `BaseJUnit5Test` + `@EnableTestFly`
- `BaseCucumberTest`, `BaseCucumberSteps`
- `WaitEngine`
- `ApiClient`, `ApiResponse`
- `StepLogger`
- `DriverManager` (public static lifecycle helpers)

---

## CI/CD and Publishing

- Maven Central publishing uses `central-publishing-maven-plugin` + GPG signing.
- Credentials live in `~/.m2/settings.xml`; they are **not** in this repository.
- Publishing is currently a manual step: `mvn deploy`
- The docs site deploys via GitHub Pages when `docs-site/**` changes on `master`.

### Version-bump checklist

When changing the framework version, update **all** occurrences:

- `pom.xml` `<version>`
- `README.md` dependency snippet and "Current release" line
- `CHANGELOG.md` new release entry
- `docs-site/docs/getting-started.md`
- `docs-site/docs/junit5.md`
- `docs-site/docs/changelog.md`
- `docs-site/src/pages/index.js`

After release, also update `LATEST_VERSION` in the separate `testfly/website` repo.

---

## Security Considerations

- TestFly is a test framework; it runs inside your build and drives browsers you control.
- **Never commit secrets** (API keys, cloud credentials, OAuth client secrets, DB passwords) to this repo.
- Sensitive config values should be injected via environment variables and referenced with `${VAR}` placeholders in `testfly.yml`.
- Report vulnerabilities privately to `security@testfly.github.io/testfly` per `SECURITY.md`; do not open public issues.
- Optional dependencies (Cucumber, JUnit 5, JSON Schema validator, IMAP, POI) are marked `<optional>true</optional>` so they are not pulled transitively into consumer projects.
- If you add a feature that reads external input (config files, test data, email bodies, network stubs), validate and sanitize it defensively.

---

## Extension Points

The framework supports controlled extension via Java SPI and programmatic registration:

| Extension | Registration |
|-----------|--------------|
| Custom driver provider | `META-INF/services/io.testfly.driver.NamedDriverProvider` or `DriverProviderRegistry.register(...)` |
| Custom report adapter | `META-INF/services/io.testfly.reporting.ReportAdapter` or `ReportAdapterRegistry.register(...)` |
| Lifecycle hook | `META-INF/services/io.testfly.hooks.ExecutionHook` or `HookRegistry.register(...)` |
| Full plugin | Implement `TestFlyPlugin` and register via SPI or `PluginRegistry` |

Plugins can declare a `minFrameworkVersion()` to fail fast on incompatibility.

---

## Useful References

- `README.md` — user-facing quickstart and feature overview
- `CLAUDE.md` — maintainer commands and version-bump details
- `CONTRIBUTING.md` — philosophy, PR checklist, backward-compatibility policy
- `docs/architecture.md` — high-level architecture
- `docs/internals.md` — internal design contracts
- `docs/ci-execution.md` — CI contract
- `docs/configuration.md` — full config reference
- `CHANGELOG.md` — release history
