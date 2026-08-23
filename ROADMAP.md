# TestFly – Roadmap v1.0

This document outlines the planned evolution of TestFly from MVP to a stable, extensible automation framework.

The roadmap is intentionally opinionated and incremental. Each phase focuses on delivering production value before expanding scope.

> **Contributors, start here.** Phases 0–5 below are complete (the framework is past v1.0). The list
> immediately below is where active work and **open contribution opportunities** live today. The
> [issue tracker](https://github.com/hakanngul/testfly/issues) is the source of truth for what's
> actionable right now — this document is the higher-level picture.

---

## Post-1.0 — current focus & where help is welcome

Items tagged **`good first issue`** or **`help wanted`** are open for contribution. Read
[CONTRIBUTING.md](CONTRIBUTING.md), comment on the issue to claim it, then open a PR against `master`.

### Documentation & discoverability (current priority)

Most users find a framework by searching, not by browsing GitHub.

- ✅ **Per-page SEO descriptions** across all docs pages — completed.
- ✅ **"Why" pages** — Why TestFly? · Why not plain Selenium? · Why not Playwright? · Why accessibility-first locators? · Why WaitEngine?
- ✅ **Recipes section** — task-titled, search-matched guides: upload a file, download a PDF, iframes, Shadow DOM, tables, infinite scroll, OAuth/SSO, alerts, drag & drop, REST + UI.
- ✅ **Migration guides** — from Selenium + TestNG, from WebDriverManager, from Selenide, from Serenity; plus a "coming from Playwright" bridge (familiar vs. different, **not** a replacement claim).
- ✅ **Homepage before/after** — a visual `wait.until(...)` → `click("#login")` comparison component.
- ✅ **SEO hygiene** — `sitemap.xml` generation verified, generic page `<title>`s tightened.

### Framework & ecosystem

- ✅ More built-in `WaitEngine` conditions requested by users.
- ✅ Additional first-class browser providers (Edge, Safari) via the existing SPI.
- ✅ CI metadata capture — provider, build, branch, commit, and build URL auto-detected from major CI/CD platforms and surfaced in HTML/JUnit reports and metrics JSON.
- **testfly-mcp** — keep MCP codegen output framework-native and accessibility-first as the API evolves. (See the [MCP repo](https://github.com/hakanngul/testfly-mcp).)

### Ongoing quality

- ✅ Grow unit-test coverage for untested code paths.
- ✅ Blocking session queue instead of fail-fast when `maxActiveSessions` is reached — implemented via fair Semaphore in `DriverManager`.
- Keep the consumer sample project (`testfly-test`) in step with new features.

> Don't see what you want to work on? Open a
> [Discussion](https://github.com/hakanngul/testfly/discussions) — ideas that fit the
> philosophy are welcome, and we'll turn agreed ones into issues.

---

## Guiding Roadmap Principles

- Deliver usable value early
- Stabilize before adding features
- Avoid speculative abstractions
- Optimize for real enterprise usage
- Prefer extensibility over monolithic growth

---

## Phase 0 – Foundation

**Status:** Complete
**Goal:** Establish core vision, scope, and structure

### Deliverables
- Project vision and positioning
- Opinionated design principles
- Initial repository structure
- Public roadmap and documentation baseline

---

## Phase 1 – MVP Core (v0.1)

**Status:** Complete — released as v0.1.0
**Goal:** Enable teams to run Selenium tests with minimal setup

### Features
- Java + Selenium + TestNG integration
- Opinionated project structure
- Automatic WebDriver management
- Centralized test lifecycle management
- Smart explicit waits with safe defaults
- Retry mechanism for flaky interactions
- Parallel execution enabled by default
- Single YAML-based configuration file
- Clean HTML execution report
- One-command execution via Maven

### Non-Goals
- Cross-framework support
- Plugin system
- Advanced reporting analytics

---

## Phase 2 – Stability & Observability (v0.2)

**Status:** Complete — released as v0.2.0
**Goal:** Improve reliability and execution transparency

### Features
- Enhanced retry intelligence (action-level vs test-level)
- Screenshot and page source capture on failure
- Execution summary with flaky test detection
- Execution timing and performance metrics
- Environment-aware configuration profiles
- Improved logging structure

---

## Phase 3 – Extensibility Layer (v0.3)

**Status:** Complete — releasing as v0.3.0
**Goal:** Allow controlled customization without breaking conventions

### Features
- ✅ Plugin-style extension points (`TestFlyPlugin` + `PluginRegistry`)
- ✅ Custom driver providers (`NamedDriverProvider` + `DriverProviderRegistry`)
- ✅ Custom reporting adapters (`ReportAdapter` + `ReportAdapterRegistry`)
- ✅ Hook system for execution lifecycle events (`ExecutionHook` + `HookRegistry`)
- ✅ Framework-safe overrides for defaults (`TestFlyDefaults`)

---

## Phase 4 – CI/CD & Enterprise Readiness (v0.4)

**Status:** Complete — releasing as v0.4.0
**Goal:** Seamless integration into enterprise pipelines

### Features
- ✅ CI-friendly execution modes (`CiEnvironmentDetector` — GitHub Actions, Jenkins, CircleCI, GitLab CI, Travis, TeamCity, Bitbucket)
- ✅ Parallel execution tuning for CI environments (thread count auto-derived from CPU cores)
- ✅ Machine-readable execution outputs (`JUnitXmlReporter` → `target/surefire-reports/TEST-TestFly.xml`)
- ✅ Build failure strategies and thresholds (`BuildThresholdEnforcer` — pass rate gate, flaky test gate)
- ✅ Docker-friendly execution support (`--no-sandbox`, `--disable-dev-shm-usage` auto-applied in containers)
- ✅ Sample CI templates (`.github/workflows/testfly.yml`, `ci/Jenkinsfile`)

---

## Phase 5 – Ecosystem & Community (v1.0)

**Status:** Complete
**Goal:** Establish TestFly as a stable ecosystem

### Features
- ✅ Official documentation website — live at https://testfly.github.io/testfly/
- ~~Sample reference projects~~ — replaced by the consumer test project at https://github.com/hakanngul/testfly-test
- ✅ Community contribution guidelines — see CONTRIBUTING.md
- ✅ Versioned plugin ecosystem — `FrameworkVersion`, `minFrameworkVersion()`, `IncompatiblePluginException`
- ✅ Backward compatibility guarantees — `@TestFlyApi` annotation, policy in CONTRIBUTING.md

---

## Roadmap Disclaimer

This roadmap represents current intent, not a fixed contract.

Priorities may shift based on:
- Community feedback
- Real-world adoption challenges
- Stability and maintenance considerations

---

## Contribution Alignment

All contributions should align with:
- The current roadmap phase
- The opinionated nature of the framework
- Long-term maintainability goals

Features that significantly increase complexity without clear value may be declined.

---

## Versioning Strategy (Planned)

- Pre-1.0 releases may introduce breaking changes
- Post-1.0 releases will follow semantic versioning
- Stability and predictability are prioritized over rapid feature growth
