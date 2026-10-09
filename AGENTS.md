# TestFly — Agent Guide

> [!CAUTION]
> **STOP — READ THIS FIRST, DO NOT SKIP.**
> Before reading the rest of this file or touching any code:
> 1. Read [`.agents/memories/scratchpad.md`](.agents/memories/scratchpad.md) — current state & active tasks.
> 2. If you need deeper context, open [`.agents/MAP.md`](.agents/MAP.md) and follow only the relevant `[[wikilink]]`.
> 3. **DO NOT** scan `src/`, `wiki/`, or this entire file blindly. You will waste tokens.
> 4. After completing work, update `scratchpad.md` and append to [`.agents/memories/log.md`](.agents/memories/log.md).
> 5. **ASLA `main` DALINA COMMIT VEYA PUSH YAPMA.** `main` dalı korumalıdır. Tüm geliştirme ve commit'ler istisnasız `development` dalına yapılır. Her git işleminden önce `git branch --show-current` kontrolü ZORUNLUDUR.
>
> Full protocol: [`.agents/rules/memory-protocol.md`](.agents/rules/memory-protocol.md) | Git kuralı: [`.agents/rules/git-release-workflow.md`](.agents/rules/git-release-workflow.md)

This file is intended for AI coding agents working in the `testfly` repository.
It summarizes the project's architecture, build/test workflows, code conventions, and extension points so you can be productive without guessing.

---

## Persistent Memory & Context Constitution (2 Yol & 3 Parça)

> [!IMPORTANT]
> Bu projede çalışan tüm AI ajanları **"2 Yol & 3 Parça"** ve **"LLM Wiki / Obsidian Graph"** kurallarına uymakla yükümlüdür. Detaylı anayasa için bkz: [[.agents/rules/memory-protocol.md]].

### Sistemin 3 Temel Parçası
1. **KURAL (Constitution):** `AGENTS.md`, [[.agents/rules/memory-protocol.md]], [[.agents/rules/docusaurus-workflow.md]] ve [[.agents/rules/git-release-workflow.md]]. Ajanın haritayı nasıl okuyacağını, docs-site güncellemelerinde her zaman `/docusaurus-config` skill'ini kullanacağını, commit/push ve tag süreçlerinde kullanıcıdan onay alacağını, ne zaman yazacağını ve token koruma disiplinini dikte eder.
2. **HARİTA (Navigational GPS):** [[.agents/MAP.md]]. Tüm modül, kural ve wiki sayfalarının indeksidir. Bilgiye körlemesine dosya tarayarak değil, harita üzerinden `[[sayfa-adi]]` çift yönlü linkleriyle gidilir.
3. **DEPO (Synthesized Storage):** [[.agents/memories/scratchpad.md]] (kısa hafıza) ve [[.agents/wiki/index.md]] (kalıcı LLM Wiki). Bilgiler ham log olarak değil, sentezlenmiş bilgi grafiği olarak saklanır.

### TestFly Hafıza Döngüsü ve İki Yol Protokolü (Read & Write Path)
- **Ajan Kimliği (Soul):** [[.agents/soul.md]] — Ajanın kıdemi, yaklaşımı ve kırmızı çizgilerini içeren tek paragraf.
- **Okuma Yolu (Read Path - Token Koruma):** ASLA proje dosyalarını veya tüm wiki'yi körlemesine tarama. Önce [[.agents/memories/scratchpad.md]] dosyasını oku. Geçmiş karar veya derin domain bilgisi gerekiyorsa [[.agents/MAP.md]] haritasına bak ve sadece ilgili `[[wiki/<dosya>]]` sayfasına nokta atışı git.
- **Yazma Yolu (Write Path - Sentez & Budama):** Oturum sonunda güncel durumu [[.agents/memories/scratchpad.md]] içine işle. **Maksimum 2.200 karakter sınırına** kesinlikle uy. Karakter dolduğunda kalıcı mimari kararları [[.agents/wiki/<kavram>.md]] olarak oluştur, [[.agents/MAP.md]] haritasına `[[<kavram>]]` olarak bağla ve tamamlanan işleri buda ([[ .agents/skills/memory-sync/SKILL.md ]]). Önemli işlemlerden (ingest, refactor, yeni özellik, lint) sonra [[.agents/memories/log.md]] dosyasına kronolojik giriş ekle.
- **Docusaurus Kuralı:** `docs-site` üzerinde güncelleme yapılırken her zaman `[[.agents/skills/docusaurus-config/SKILL.md]]` kullanılır, çift dil (TR/EN) korunur ve `npm run build` ile doğrulanır ([[ .agents/rules/docusaurus-workflow.md ]]).
- **Git & Release Kuralı:** **`main` dalına doğrudan commit/push KESİNLİKLE YASAKTIR (Protected Main Branch).** Tüm geliştirmeler, commit'ler ve push'lar istisnasız `development` dalında yürütülür. Herhangi bir git commit/push işleminden önce `git branch --show-current` ile dal doğrulanır. Kullanıcı "commit at" dediğinde [[ .agents/rules/git-release-workflow.md ]] protokolüne harfiyen uyulur.
- **Obsidian Graph Standardı:** Tüm referanslar çift yönlü `[[...]]` link formatında tutulur ve YAML frontmatter (`tags`, `date`, `status`, `type`) kullanılır.

---

## Project Overview

**TestFly** is an opinionated, zero-boilerplate Java test-automation framework built on top of Selenium WebDriver.
It is published to Maven Central as a single JAR that users add as a dependency.

- **Group / Artifact:** `io.github.hakanngul:testfly`
- **Current version:** `1.0.7`
- **Java baseline:** 21 (compiled with `--release 21`)
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
| Language | Java 21 |
| Build | Maven |
| Browser automation | Selenium Java 4.48.0 |
| Test framework | TestNG 7.9.0 |
| YAML parsing | SnakeYAML 2.2 |
| JSON processing | Jackson Databind 2.21.7 (via `jackson-bom`) |
| Unit-test mocking | Mockito 5.11.0 |
| Optional: Cucumber | `cucumber-java` + `cucumber-testng` 7.20.1 |
| Optional: JUnit 5 | `junit-jupiter-api` + `junit-platform-launcher` 1.10.2 |
| Optional: JSON Schema | `json-schema-validator` 1.4.3 |
| Optional: IMAP email | `jakarta.mail` 2.0.2 |
| Optional: Excel data | Apache POI 5.4.0 |

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
├── testfly.yml                       # Framework config for local test runs
├── README.md                         # User-facing landing page
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
├── src/main/java/io/testfly/         # Framework source
└── src/test/java/io/testfly/unit/    # Framework unit tests
```

### Main source packages (`src/main/java/io/testfly/`)

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

### Tests (`src/test/java/io/testfly/unit/`)

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
mvn test -Dtestfly.profile=staging

# GPG signing only runs with -Prelease, so no flag is needed locally
mvn clean install -DskipTests
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

Profiles are activated with `-Dtestfly.profile=<name>` and load `testfly-<name>.yml`.

---

## Shared Agent and Product Context

`AGENTS.md` is the single entry point for coding-agent instructions, including Gemini/Antigravity. Consult the linked rules and skills rather than maintaining separate tool-specific copies.

- Follow the `testfly.yml` schema and `${VAR}` environment placeholders. Treat loaded configuration as immutable during execution; use framework APIs for scoped overrides.
- TestFly serves QA engineers, SDETs, and Java automation teams in local, CI, Grid, and cloud environments. Preserve direct access to Selenium primitives and prefer convention over boilerplate.
- Public documentation and HTML reports should use clear, practical language and meet WCAG AA contrast, keyboard navigation, and semantic markup requirements. Visual rules live in [DESIGN.md](DESIGN.md).
- For real-backend integration checks use `mvn verify -Preal-backends`; for the optional quality gate use `mvn clean verify -Pquality`. These are separate from browser-free unit tests.

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

- Located in `src/test/java/io/testfly/unit/`
- Run with TestNG via `maven-surefire-plugin` (configured for TestNG in `pom.xml`)
- Mockito is used to mock Selenium/browser interactions
- No real browser is opened
- Integration tests that need real backends live under `src/test/java/io/testfly/integration/` and are run by `maven-failsafe-plugin` via `mvn verify -Preal-backends`

### Consumer integration tests

A separate sample/consumer project exists at `github.com/hakanngul/testfly-test`.
To test framework changes end-to-end:

```bash
# 1. Install the local framework JAR
mvn clean install -DskipTests

# 2. In the consumer project, pin its pom.xml to the current framework version
# 3. Run the consumer tests
```

### CI

GitHub Actions (`.github/workflows/testfly-ci.yml`):

1. `unit-tests` job — runs `mvn test`
2. `integration-tests` job — installs the framework, checks out `<owner>/testfly-test`, pins it to the current version, and runs API demo tests

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

- Maven Central publishing uses `central-publishing-maven-plugin`; GPG signing lives in the `release` profile of `pom.xml` (`-Prelease`), so plain `mvn verify` needs no key.
- Releases are published by `.github/workflows/release.yml` (tag `vX.Y.Z` reachable from `main`, or `workflow_dispatch` with a validated `version`), gated by the `release` GitHub environment. Credentials are repository/environment secrets; they are **not** in this repository.
- For a local manual deploy: `mvn deploy -Prelease` (needs `~/.m2/settings.xml` credentials and a GPG key).
- The docs site deploys via GitHub Pages when `docs-site/**` changes on `main`.

### Version-bump checklist

Docs present the version being released as the current Maven Central release, so the install pins move with every release. When changing the framework version, update **all** occurrences:

- `pom.xml` `<version>`
- `AGENTS.md` "Current version" line (Project Overview)
- `README.md` dependency snippets and "Current release" line
- `CHANGELOG.md` new release entry
- `.github/profile/README.md` dependency snippet
- `docs-site/docs/changelog.md`
- Install pins (Maven `<version>`, Gradle `io.github.hakanngul:testfly:X.Y.Z`) in `docs-site/docs/getting-started.md`, `junit5.md`, `cucumber.md`, `gradle.md`, `loadtest/getting-started.md`, `migration/from-selenium-testng.md`
- Version prose in `docs-site/docs/migration/from-selenium-testng.md` (Java version note), `loadtest/getting-started.md` and `loadtest/examples.md` (Availability / current release wording), `guides/api-schema-validation.md`
- `docs-site/src/pages/index.js` (Maven Central badge) and `docs-site/src/data/homeData.js` (stats value and install snippet)
- `docs-site/docusaurus.config.js` has no version text now (the announcement bar was removed); keep it that way
- every Turkish mirror of the docs above under `docs-site/i18n/tr/docusaurus-plugin-content-docs/current/`
- Do not bump `@testfly/mcp` references in `docs-site/docs/cli.md` / `ai/*` (separate npm package) or `@TestFlyApi(since = ...)` annotations (they record when an API was introduced)

Verify with `git grep -nE '<old-version>' -- . ':!CHANGELOG.md' ':!docs-site/docs/changelog.md' ':!docs-site/i18n/tr/docusaurus-plugin-content-docs/current/changelog.md' ':!docs-site/package-lock.json'` and `cd docs-site && npm run build`.

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
- `CONTRIBUTING.md` — philosophy, PR checklist, backward-compatibility policy
- `docs/architecture.md` — high-level architecture
- `docs/internals.md` — internal design contracts
- `docs/ci-execution.md` — CI contract
- `docs/configuration.md` — full config reference
- `CHANGELOG.md` — release history
