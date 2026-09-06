# TestFly – Architecture Overview

This document describes the high-level architecture of TestFly, including its core components, design boundaries, and execution flow.

The architecture is intentionally simple, opinionated, and extensible only at well-defined points.

---

## Architectural Goals

The architecture of TestFly is designed to:

- Minimize Selenium framework boilerplate across web, API, and BDD tests
- Enforce consistent, thread-isolated execution patterns across parallel test runs
- Reduce flakiness through standardized explicit waits and smart auto-retries
- Remain transparent to native Selenium WebDriver APIs without hiding or obfuscating them
- Provide unified observability (HTML report, Allure, ReportPortal) with zero configuration
- Power AI test generation via the official Model Context Protocol (TestFly MCP)

---

## Layered Architecture

TestFly follows a layered, responsibility-driven architecture:

```
┌────────────────────────────────────────────────────────┐
│                   Test Layer (User)                    │
│   TestNG (BaseTest) · JUnit 5 (BaseJUnit5Test)         │
│   Cucumber 7 BDD (@TestFlySession) · Page Objects      │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│                   Agentic AI Layer                     │
│   ActionCompiler · ActionExecutor · ActionCache        │
│   AiAssertEngine · AiHealingEngine · DomPruner         │
│   AiFailureAnalyzer · RemediationPatchGenerator        │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│                      TestFly Core                      │
│   Lifecycle Orchestrator · ThreadLocal Driver Manager │
│   Fluent Locators & Assertions (PageAssert, Locator)  │
│   Network Mocking (CDP v152) · REST API Client        │
│   Precondition Session Cache · WaitEngine & Retries   │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│                  Infrastructure Layer                  │
│   YAML Config Loader (testfly.yml) · Profile Resolver  │
│   Interactive HTML Reporter · Allure / ReportPortal   │
│   Selenium Manager (Driver Binary Resolver)           │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│                     Selenium 4 (CDP)                   │
│   Chromium (Chrome/Edge) · Firefox · Safari · Grid    │
└────────────────────────────────────────────────────────┘
```

---

## Layer Responsibilities

### 1. Test Layer (User-Owned)

Responsibilities:
- Test classes written in TestNG, JUnit 5, or Cucumber BDD
- Page Object Models extending `BasePage`
- Business-level assertions using `assertThat(locator)` and `assertThatPage()`
- API endpoint verification using `api()`
- Agentic testing via `act("goal")`, `assertWithAi("condition")`, `byIntent("description")`

Rules:
- No manual `new ChromeDriver()` or `driver.quit()` in tests
- No static WebDriver variables
- Page Objects do not contain assertions

---

### 2. Agentic AI Layer (Framework-Owned)

Responsibilities:
- **Goal Compilation (`ActionCompiler`)**: Transforms natural language goals into deterministic `ActionPlan` steps via LLM reasoning.
- **Action Execution (`ActionExecutor`)**: Executes compiled plans as Selenium WebDriver actions (`CLICK`, `TYPE`, `CLEAR`, `HOVER`, `WAIT_VISIBLE`, `PRESS_ENTER`, `SELECT`, `NAVIGATE`).
- **Compile & Freeze Caching (`ActionCache`)**: Persists action plans to `.testfly/action-cache.json` for 0ms replay on subsequent runs.
- **Semantic Assertions (`PageAssert` / `LocatorAssert` → `AiAssertEngine`)**: Prune the DOM, then evaluate natural language conditions via LLM reasoning; fail closed on malformed responses.
- **AI Self-Healing (`AiHealingEngine`)**: Synthesizes new locators when selectors break, using pruned DOM context.
- **DOM Optimization (`DomPruner`)**: Strips non-semantic HTML noise to stay within LLM token budgets (<8K tokens).
- **Failure Analysis (`AiFailureAnalyzer`)**: Explains test failure root causes in HTML reports.
- **Auto-PR Remediation (`RemediationPatchGenerator` + `SourceCodeLocator`)**: Generates Unified Git Diff `.patch` files for permanent locator failures.

Supported LLM Providers:
- OpenAI-compatible (OpenAI, Qwen/Alibaba Cloud, DeepSeek, Groq, Ollama)
- Anthropic Claude (native Messages API + Token Plan proxy)
- Google Gemini

---

### 3. TestFly Core (Framework-Owned)

Responsibilities:
- **Lifecycle Orchestration**: Automates driver start, pre-conditions, and teardown across TestNG, JUnit 5, and Cucumber.
- **ThreadLocal Isolation**: Ensures zero cross-thread driver contamination during parallel execution.
- **Fluent Locators & Assertions**: Auto-waiting locator factories (`find()`, `getByRole()`) and assertions.
- **Network Mocking (`page().route()`)**: Declarative request stubbing and routing over Chrome DevTools Protocol.
- **Unified REST API Client**: Built-in HTTP client with polling and JSONPath validation.
- **Session Caching**: Caches cookies and web storage via `@PreCondition` to skip repetitive UI logins.
- **Wait Engine & Retries**: Centralized explicit waits (preventing harmful implicit waits) and automated flakiness retry.

---

### 4. Infrastructure & Reporting Layer

Responsibilities:
- **Configuration Engine**: Loads and validates `testfly.yml`, merging system properties and environment variables.
- **HTML Reporting**: Generates a self-contained, interactive HTML report (`target/testfly-report.html`) containing execution timeline, flakiness radar, video recordings, and step-by-step screenshots.
- **Third-Party Reporting Adapters**: Out-of-the-box integration with Allure and ReportPortal.

---

### 5. Selenium & Browser Layer

Responsibilities:
- Native Selenium 4.48.0 WebDriver APIs and Chrome DevTools Protocol (CDP v152).
- Automated driver binary discovery via Selenium Manager (no manual chromedriver downloads needed).
- Support for Local (Chrome, Firefox, Edge, Safari) and Remote Grid execution.

---

## Execution Flow

```text
Test Runner             Config Manager        Driver Manager         CDP / DevTools         Test Method           HTML Reporter
    │                         │                     │                      │                     │                      │
 1. │─── Load testfly.yml ───>│                     │                      │                     │                      │
    │                         │                     │                      │                     │                      │
 2. │─── Request WebDriver ────────────────────────>│                      │                     │                      │
    │                                               │ (Provision Browser)  │                     │                      │
    │                                               │                      │                     │                      │
 3. │─── Attach CDP Session (if Chromium) ────────────────────────────────>│                     │                      │
    │                                                                      │                     │                      │
 4. │─── Execute Test Logic ────────────────────────────────────────────────────────────────────>│                      │
    │                                                                                            │                      │
    │                                                                                            │ (Actions & Asserts)  │
    │                                                                                            │                      │
    │   [ If Test Fails ]                                                                        │                      │
 5. │─── Capture Screenshot & DOM Snapshot ─────────────────────────────────────────────────────────────────────────────>│
 6. │─── Trigger Retry (if configured) ─────────────────────────────────────────────────────────>│                      │
    │                                                                                                                   │
 7. │─── Quit WebDriver & Release Thread ──────────>│                                                                   │
    │                                               │                                                                   │
 8. │─── Compile target/testfly-report.html ───────────────────────────────────────────────────────────────────────────>│
    │                                                                                                                   │
```

---

## Architectural Constraints

1. **Strict Isolation**: No global static driver state. Every thread owns an independent browser session.
2. **Zero Implicit Waits**: Implicit waits are enforced at `0ms` to prevent compounding retry delays.
3. **Transparent APIs**: The native Selenium `WebDriver` instance is always accessible via `getDriver()` if custom Actions or JavaScript execution is needed.
