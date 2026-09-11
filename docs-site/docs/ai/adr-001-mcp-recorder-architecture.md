---
id: adr-001-mcp-recorder-architecture
title: "ADR-001: MCP Server & Interactive Recorder Architecture"
sidebar_label: "ADR-001: Recorder Architecture"
sidebar_position: 8
description: Architecture Decision Record for the TestFly MCP server, Chrome companion recorder, and multi-file Java codegen engine.
---

# ADR-001: MCP Server & Interactive Recorder Architecture

## Status
**Accepted**

## Date
2026-09-11

## Context

Enterprise Java test automation teams face two significant modern testing challenges:
1. **AI Agent Enablement:** Autonomous coding agents (such as Antigravity, Claude Code, and Cursor) need structured, real-time access to live browser execution, DOM inspection, accessibility auditing, and test generation via standardized interfaces.
2. **Resilient Test Generation:** Traditional browser recorders (such as legacy Selenium IDE or raw Playwright codegen) generate brittle scripts characterized by absolute XPaths (`/html/body/div[2]/div/button`), arbitrary `Thread.sleep()` pauses, and single monolithic files that do not align with enterprise Java architectural patterns (e.g. Page Object Model with `BasePage`, TestNG with `BaseTest`, or Cucumber BDD).

We needed an architecture that:
- Connects AI agents to browsers via the official **Model Context Protocol (MCP)** specification.
- Provides human engineers with a visual, low-latency **Live Companion Recorder**.
- Generates idiomatic, framework-native **TestFly Java 17+** code with auto-waiting assertions and accessibility-first locators.
- Emits clean, compilable multi-file project structures (`pages/`, `tests/`, and `resources/features/`).

---

## Decision

We designed and implemented a dual-purpose automation bridge consisting of:

### 1. Unified Python MCP Execution Engine (`testfly-mcp`)
- Implements the **Model Context Protocol (MCP)** JSON-RPC specification over `stdio` and `sse`.
- Exposes **88 atomic automation tools** covering browser lifecycle, element interaction, accessibility auditing, and code generation.
- Bridges directly to local Selenium WebDriver sessions with headless or visible desktop browser execution.

### 2. Live Companion Studio with CDP Script Injection
- Rather than requiring browser extensions that suffer from permission and update barriers, TestFly injects an active DOM event interceptor (`injected_recorder.js`) directly via the Chrome DevTools Protocol (`Page.addScriptToEvaluateOnNewDocument`).
- Google Chrome runs in a dedicated profile with relaxed cross-origin flags (`--disable-web-security`, `--allow-running-insecure-content`), enabling instant callbacks to the local recorder server on `127.0.0.1`.
- A background port manager scans and binds the first available port (starting at `8765`), eliminating port collision issues.

### 3. Accessibility-First Locator Hierarchy
When synthesizing element locators, TestFly enforces a strict stability hierarchy:
1. **Semantic Test IDs:** `getByTestId("name")` (e.g. `data-testid`, `data-test`)
2. **Accessible Roles & Names:** `getByRole(Role.BUTTON, "Submit")`, `getByLabel("Email")`
3. **Unique IDs:** `$("#login-btn")`
4. **SmartLocator Fallback:** Emits a multi-strategy resolver for brittle structural selectors that tries multiple By candidates in sequence.

### 4. Separated Multi-File Project Routing
When saving generated code to a project:
- The server parses `File: <path>` boundaries and writes each class into its dedicated project directory:
  - **Page Objects** are placed in `src/test/java/.../pages/`
  - **Test Classes** are placed in `src/test/java/.../tests/`
  - **Cucumber Features** are placed in `src/test/resources/features/`
- Monolithic merged files are strictly forbidden, preventing invalid Java compilation units with multiple package statements or duplicate public classes.

### 5. Compiler-Level Identifier & Keyword Sanitization
- Element names and generated methods are checked against the Java Language Specification reserved keyword set (`continue`, `break`, `return`, `class`, `default`, `goto`, etc.).
- Reserved words automatically receive an `Element` suffix (e.g., `continue` becomes `continueElement` and `clickContinueElement()`).
- File header markers and separators are escaped with comments (`//` for Java, `#` for Gherkin) to guarantee zero compilation errors in strict build tools.

---

## Alternatives Considered

### 1. Chrome Extension-Based Recorder
- **Pros:** Runs directly inside any standard browser window without launching a dedicated process.
- **Cons:** Browser extension permissions (Manifest V3) make local HTTP communication to AI coding agents difficult. It cannot be automated headlessly by CLI tools or AI agents over MCP.
- **Rejected:** Standalone CDP injection provides greater flexibility and allows CLI and AI agents to invoke the recorder programmatically.

### 2. Emitting Raw Selenium Standalone Code
- **Pros:** Simple, single-file scripts with `public static void main` or standalone JUnit tests.
- **Cons:** Violates enterprise best practices, introduces boilerplate `new ChromeDriver()` lifecycle logic, lacks automatic waiting, and misses TestFly's ThreadLocal parallel test runner benefits.
- **Rejected:** Emitting TestFly-native code (`BaseTest`, `BasePage`, `assertThat(...)`) provides immediate production readiness and self-healing reliability.

### 3. Single Monolithic Output File for Page Object Model
- **Pros:** Simple saving mechanism that dumps everything into a single `.java` file.
- **Cons:** Java requires separate files for public classes in different packages. Merging a `HomePage` class and a `HomeTest` class into one file triggers severe compilation errors (`Syntax error on token(s), misplaced construct(s)`).
- **Rejected:** Multi-file parsing and separated directory routing are mandatory for valid Java compilation.

---

## Consequences

### Positive
- **Instant Productivity:** QA teams can record full regression scenarios and immediately commit clean, modular Java code without manual refactoring.
- **Zero Compilation Friction:** Reserved keyword sanitization and multi-file routing eliminate syntax errors during `mvn test-compile`.
- **Unified Agent & Human Experience:** Both human testers (via Web Studio) and AI coding agents (via MCP tools) operate against the same underlying automation engine.

### Negative / Trade-offs
- **Chrome Dependency:** Live companion recording currently requires Google Chrome to be installed locally (though headless agentic execution supports other browsers).
- **Local Port Management:** Requires opening a local HTTP port for the web studio and SSE stream.
