# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

- **Primary:** QA Engineers, SDETs, and Java test automation engineers building web and API test suites.
- **Context:** Teams tired of boilerplate Selenium setup, flaky waits, manual driver configuration, and disjointed reporting tools; or teams migrating from legacy Selenium/TestNG/WebDriverManager setups.
- **Job to be Done:** Write reliable, fast, maintainable automated tests with zero boilerplate, predictable waits, and out-of-the-box HTML reports and CI integrations.

## Product Purpose

TestFly is "The Spring Boot of Selenium" — an opinionated, zero-boilerplate Java test automation framework built on Selenium WebDriver and published as a single JAR. It eliminates repetitive setup code while preserving direct access to raw Selenium primitives (`WebDriver`, `By`, `WebElement`). Success means automated tests run faster, fail only on real product bugs (not timing or infrastructure flakiness), and deliver instant, rich visual reporting.

## Positioning

Unlike raw Selenium (which requires hundreds of lines of boilerplate setup and flaky sleep logic) or Playwright/Cypress (which require moving away from the enterprise Java ecosystem), TestFly provides convention-over-configuration, automatic waiting (`WaitEngine`), accessibility-first locators (`getByRole`), thread-local driver isolation, self-healing locators, and built-in timeline HTML reporting natively in Java 17.

## Operating Context

- **Test Execution Environment:** Local development machines (IDE/Maven), CI/CD pipelines (Jenkins, GitHub Actions), remote Selenium Grids, and cloud providers (BrowserStack, Sauce Labs).
- **Frontend / Visual Surfaces:**
  1. **Documentation Site (`docs-site`):** Docusaurus-based public documentation portal featuring guides, architecture deep-dives, recipes, and API references.
  2. **HTML Execution Report (`reporting`):** In-browser single-page execution dashboard and step timeline generated after test runs, displaying pass/fail metrics, step logs, screenshots, and failure traces.

## Capabilities and Constraints

- **Language Baseline:** Java 17 (`--release 17`).
- **Core Dependencies:** Selenium Java 4.40+, TestNG 7.9.0, SnakeYAML, Jackson Databind.
- **Web Stack (Docs):** Docusaurus 3.5.2, React 18, Node.js 18+.
- **Architectural Rules:** No raw `Thread.sleep()`, thread-safe WebDriver lifecycle, `@TestFlyApi` backward compatibility commitment.

## Brand Commitments

- **Tone & Voice:** Professional, developer-first, clear, authoritative, and pragmatic.
- **Identity:** Fast, modern, enterprise-ready, robust ("Spring Boot simplicity meets enterprise test automation").

## Evidence on Hand

- Published framework source and unit tests in `src/main/` and `src/test/`.
- Working Docusaurus documentation portal in `docs-site/`.
- Built-in HTML reporting engine in `src/main/java/io/testfly/reporting/HtmlReportGenerator.java`.
- Comprehensive architecture and guideline files: [README.md](file:///Users/hagul/Projects/TestFramework/testfly/README.md), [AGENTS.md](file:///Users/hagul/Projects/TestFramework/testfly/AGENTS.md), [testfly.yml](file:///Users/hagul/Projects/TestFramework/testfly/testfly.yml).

## Product Principles

1. **Convention Over Configuration:** Sensible defaults for everything; minimal required configuration.
2. **Zero Boilerplate, Never Hide Raw Selenium:** Provide high-level fluent APIs (`Locator`, `WaitEngine`) without restricting direct WebDriver access.
3. **Flakiness is a Defect:** Auto-waiting, thread isolation, and self-healing eliminate test infrastructure flakiness.
4. **First-Class Visual Clarity:** Both the public documentation and test run reports must look polished, modern, and effortless to navigate.

## Accessibility & Inclusion

- Accessibility-first locators (`getByRole`, `getByText`, `getByLabel`) are a core architectural selling point of the framework.
- The documentation site and HTML report surfaces must adhere to WCAG AA guidelines for contrast, keyboard navigation, and semantic structure.
