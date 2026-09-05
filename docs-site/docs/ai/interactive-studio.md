---
id: interactive-studio
title: Interactive Web Studio
sidebar_label: Interactive Web Studio
sidebar_position: 4
description: Visual test generation, browser playground, and configuration management powered by the TestFly MCP Web Studio.
---

# Interactive Web Studio

The **TestFly MCP Studio** is a local, zero-dependency web application that allows QA engineers and developers to visually control browsers, test MCP tools, generate TestFly code, and configure settings directly from their browser.

```bash
testfly-mcp ui
```

This launches the studio at **`http://127.0.0.1:8765`** and opens your default browser automatically.

---

## Features & Modules

### 1. Browser Playground
The **Browser Playground** lets you drive real browser sessions and observe the page state in real time:

- **Execution Modes:**
  - **🤖 Headless (Background):** Chrome runs silently in the background, streaming screenshots directly into the **Live Preview** panel without intrusive popup desktop windows. *(Default)*
  - **🖥️ Visible Window:** Launches Google Chrome as a visible desktop window for direct visual debugging.
- **Navigation & Actions:** Enter a URL and click **Go** to navigate. Test clicking elements and typing values directly.
- **Real-Time Live Preview:** High-resolution screenshots automatically refresh after page navigations or interactions.
- **Inspect Elements:** Lists interactive inputs, buttons, and links discovered on the current page.
- **A11y Audit:** Runs an automated accessibility check highlighting missing labels or contrast issues.

---

### 2. Codegen Studio
The **Codegen Studio** converts real browser sessions into production-ready TestFly Java code:

- **Target Frameworks:**
  - **Page Object Model:** Generates classes extending `BasePage` with accessibility-first locators (`getByRole`, `getByLabel`, `getByTestId`, `find`).
  - **TestNG Tests:** Generates test classes extending `BaseTest` with auto-waiting assertions (`assertThat(getDriver()).hasTitle(...)`).
  - **JUnit 5 Tests:** Generates test classes extending `BaseJUnit5Test`.
  - **Cucumber BDD:** Generates Gherkin feature files, step definitions extending `BaseCucumberSteps`, and runners extending `BaseCucumberTest`.
- **One-Click Copy:** Copy syntax-highlighted Java code directly into your test project.

---

### 3. Tools Directory (88 Tools)
- Search and filter all 88 MCP tools by name or description.
- View parameter JSON schemas and default arguments.
- Execute any tool on the live session with custom JSON inputs and inspect the raw output.

---

### 4. Visual `testfly.yml` Editor
- Adjust browser types (`chrome`, `firefox`, `edge`), execution mode (`local` vs `grid`), thread count, timeouts, and reporting options using form controls.
- View real-time synchronized YAML preview.
- Save directly to your project root with one click (**"Save testfly.yml to Project Root"**).

---

### 5. Environment Doctor
- Built-in diagnostic verification of Python runtime, Selenium WebDriver, Google Chrome binary path, and IDE configuration files.
