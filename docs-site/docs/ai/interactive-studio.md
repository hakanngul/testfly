---
id: interactive-studio
title: Interactive Web Studio
sidebar_label: Interactive Web Studio
sidebar_position: 4
description: Visual test generation, browser playground, live companion recording, and configuration management powered by TestFly Web Studio.
---

# Interactive Web Studio

The **TestFly Web Studio** is a local, zero-dependency visual interface that empowers QA teams and developers to interactively drive browsers, test MCP tools, generate production-ready Java code, and manage project configurations.

```bash
# Launch the studio at http://127.0.0.1:8765
testfly studio
```

*(You can also use the alias `testfly ui`).*

---

## Studio Modes

The Web Studio operates in two complementary modes:

### 1. Standalone Playground Mode (`testfly studio`)
Designed for manual browser exploration, tool inspection, and configuration editing:
- **Browser Execution:** Choose between **🤖 Headless (Background)** (streams live screenshot previews without popups) and **🖥️ Visible Window** for hands-on visual debugging.
- **Tools Directory (88 Tools):** Search, inspect schemas, and execute any MCP tool directly with customized JSON arguments.
- **Visual `testfly.yml` Editor:** Synchronized graphical form and YAML preview with one-click saving to your project root.
- **Environment Diagnostics:** Embedded status checks for Python, Selenium, Chrome, and IDE configurations.

### 2. Live Companion Recording Mode (`testfly record <url>`)
Designed for live recording of real user flows in Google Chrome:
- **Chrome Companion:** Automatically launches Google Chrome with injected DOM recording scripts and relaxed cross-origin security.
- **Live Event Stream (SSE):** Real-time timeline of clicks, debounced typing, and custom assertions.
- **Assertion Recording Toolbar:** One-click recording of element visibility, enabled states, and exact/contains text verifications.
- **Smart Locator Tester:** Real-time selector validation against the live companion page.
- **Multi-Framework Java Codegen:** Live syntax-highlighted code output across Page Object Model (`BasePage` + `BaseTest`), TestNG, JUnit 5, and Cucumber BDD.
- **Save to Project:** Automatic multi-file export routing Page Objects to `pages/`, Test classes to `tests/`, and Gherkin features to `resources/features/`.

> [!TIP]
> For a comprehensive walkthrough of the companion recorder, see the [Interactive Recorder Guide](./recorder.md).

---

## Visual `testfly.yml` Editor

Managing test execution parameters is simple with the built-in configuration editor:

1. **Execution Settings:** Configure local execution, Selenium Grid URLs, parallel execution mode (`methods` vs `classes`), thread counts, and active session limits.
2. **Browser Profiles:** Select default browser (`chrome`, `firefox`, `edge`), viewport dimensions, headless preferences, and custom launch arguments.
3. **Timeouts & Retries:** Configure explicit wait thresholds, page load timeouts, and flaky test retry policies (`enabled`, `maxAttempts`).
4. **Reporting Adapters:** Toggle HTML reports, Allure 2, and ReportPortal integrations.
5. **Direct Project Sync:** Click **"Save testfly.yml to Project Root"** to write the configuration directly to your project without manual copy-pasting.

---

## Port Management & Conflict Resolution

By default, the Web Studio binds to port `8765`. If port `8765` is already in use by another application or an existing TestFly session:
- The server automatically increments the port (`8766`, `8767`, ...) up to 20 attempts.
- The active port is logged to the console and automatically launched in your default desktop browser:
  ```text
  ✓ TestFly Web Studio running at: http://127.0.0.1:8766
  ```
