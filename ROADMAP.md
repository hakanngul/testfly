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
[CONTRIBUTING.md](CONTRIBUTING.md), comment on the issue to claim it, then open a PR against `development`.

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
- ✅ **TestFly MCP Bridge & Scaffolder (`npx @testfly/mcp init`)** — Official developer MCP bridge and project generator for Java 21 test suites.
- ✅ **Smart Test Sharder (LPT Bin-Packing)** — Mathematical multi-worker CI/CD load balancing via Longest Processing Time bin-packing.
- ✅ **TestFly MCP Bridge** — Model Context Protocol bridge paired with Playwright MCP for autonomous Java 21 codegen, action-caching, and self-healing.

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
- ✅ Official documentation website — live at https://hakanngul.github.io/TestFly/
- ~~Sample reference projects~~ — replaced by the consumer test project at https://github.com/testfly/testfly-test
- ✅ Community contribution guidelines — see CONTRIBUTING.md
- ✅ Versioned plugin ecosystem — `FrameworkVersion`, `minFrameworkVersion()`, `IncompatiblePluginException`
- ✅ Backward compatibility guarantees — `@TestFlyApi` annotation, policy in CONTRIBUTING.md

---

## Phase 6 – Autonomous Testing & AI-Driven Ecosystem (v1.2+)

**Status:** In Progress
**Goal:** Transform TestFly into an autonomous, self-generating, self-healing, and self-optimizing test intelligence platform

### Planned Features

1. **TestFly Autonomous Explorer (`testfly explore`)**
   - Headless autonomous crawler that traverses target web apps, handles authentication, and discovers forms, buttons, and state transitions.
   - Extracts clean Accessibility Trees (`getByRole`, `getByLabel`, `getByTestId`) rather than brittle CSS/XPath.
   - Automatically generates idiomatic TestFly Page Objects (`BasePage`) and complete TestNG/JUnit 5 test classes (`BaseTest`) without manual coding.

2. **AI Root-Cause & Auto-Fix Analyzer**
   - Automatically diagnoses test failures by correlating DOM mutations, CDP console errors, and network interception traces.
   - Outputs precise root-cause analysis (e.g. *"Button click timed out because `/api/v1/auth` returned 500"*).
   - Generates PR suggestions or automatic selector/assertion diffs to fix broken tests.

3. **Visual Layout Shift & Smart UI AI**
   - AI-powered perceptual visual regression testing beyond rigid pixel-by-pixel comparisons.
   - Detects responsive layout breaks, element overlapping, text clipping, and CSS regressions across viewports without false positives from dynamic content.

4. **Synthetic Test Data Factory (`@TestDataFactory`)**
   - Context-aware synthetic test data generator producing realistic localized data (names, identification numbers, addresses, credit cards).
   - Integrates natively with `@TestData` to generate dynamic mock API payloads and database seeds on the fly.

---

## Consolidated Feature Backlog

These ideas consolidate earlier AI feature notes and README RFC listings. They are proposals for further work, not promises of shipped APIs or fixed release dates. Existing AI healing, triage, assertion, visual, network and MCP capabilities should be extended rather than replaced by duplicate implementations.

| Area | Proposed next work | Validation boundary |
|---|---|---|
| Confidence-aware self-healing | Supply structured DOM candidates to the AI provider and validate the selected locator and confidence before applying it. | Confidence does not eliminate hallucinations; reject ambiguous or invalid selections and retain an audit trail. |
| Failure triage | Correlate stack traces, recent DOM events, network errors and screenshots; distinguish suspected infrastructure failures from application failures in reports. | Classification is advisory and must not silently turn a failing test into a pass. |
| Semantic assertions | Compare user-visible text against an expected meaning through the existing AI assertion infrastructure. | Explicit opt-in, bounded provider calls, and clear failure evidence; deterministic assertions remain available. |
| Page Object generation and MCP | Extend the existing MCP bridge to generate idiomatic BasePage locators and TestNG/JUnit tests from observed accessibility/DOM evidence. | Generated code must compile and be reviewed; no guarantee of perfect selectors. |
| Time-travel trace viewer | Explore an interactive execution timeline with navigation across captured steps and state. | Build on existing tracing and recording outputs. |
| Declarative network mocking | Explore a concise browser-network interception DSL. | Keep browser CDP routing distinct from API client mock rules already implemented. |
| CI auto-healer | Produce reviewable failure-remediation patches and optional PR workflows. | Publishing changes requires explicit authorization; do not hide product defects. |
| Visual regression | Extend existing visual assertions with clearer diff output and perceptual analysis. | Account for dynamic content and make thresholds explicit. |
| IDE locator inspector | Explore locator inspection and highlighting directly in the IDE. | Reuse current inspection/recorder tooling and verify locators against the page. |

## Retained Decisions from Completed Plans

- **Java baseline:** Java 21, `--release 21`, managed virtual threads for blocking concurrent work, stable public signatures, and no additional mandatory dependencies for modernization. Collection and switch refactors are optional maintenance, not a migration release gate.
- **Load testing:** Keep the fluent scenario/step/feeder API independent of engine implementation; resolve YAML defaults, annotation overrides and fluent choices in that order. External engines remain optional and selected through the existing engine abstraction.
- **Validation:** Browser-free unit tests, opt-in real-backend integration tests, and EN/TR user documentation. Preserve per-step metrics, percentile/error assertions and reporting failure isolation. Reporting adapters sharing output files run serially.
- **Documentation:** Completed sprint checklists and migration instructions are removed from the active documentation; current contracts live in [AGENTS.md](AGENTS.md), [load-test wiki](.agents/wiki/load-testing.md) and the load-testing guides under `docs-site`.

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
