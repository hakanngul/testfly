---
id: testfly-mcp
title: TestFly MCP Server & CLI
sidebar_label: MCP Server & CLI
sidebar_position: 2
description: Comprehensive installation, CLI command suite, IDE setup, environment diagnostics, and tool catalog for TestFly MCP.
---

# TestFly MCP Server & CLI

The **TestFly MCP Server** is an enterprise-grade automation bridge connecting AI coding assistants and developers to real browser sessions, accessibility engines, and Java code generators. 

It exposes **88 specialized automation tools** to AI agents via the standard [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) JSON-RPC specification, while providing developers with a unified command-line toolkit (`testfly`).

---

## Installation

### Recommended: Global Tool Installation via `uv`

The fastest and most isolated way to install the CLI across all environments is using [`uv`](https://github.com/astral-sh/uv):

```bash
# Install globally from local repository or source
cd testfly-mcp
uv tool install --editable . --force
```

Verify that the CLI executable is available in your shell:
```bash
testfly --version
# Output: testfly-mcp 1.0.0
```

### Alternative: Pip / Virtual Environment
```bash
pip install -e .
```

---

## Command-Line Interface (CLI)

The `testfly` binary provides a comprehensive command suite for recording, project scaffolding, environment verification, and IDE connectivity:

```bash
testfly [COMMAND] [OPTIONS]
```

### Complete Command Matrix

| Command | Description | Example |
| :--- | :--- | :--- |
| `testfly record <url>` | Launches the **Live Companion Recorder** with Chrome DevTools Protocol injection and interactive Studio. | `testfly record https://saucedemo.com` |
| `testfly studio` *(or `ui`)* | Launches the standalone **TestFly Web Studio** for browser playground testing, tool execution, and visual YAML editing. | `testfly studio --port 8765` |
| `testfly doctor` | Performs a comprehensive environment diagnostic check across Python, Selenium, Chrome, and IDE configs. | `testfly doctor` |
| `testfly init [name]` | Scaffolds a new TestFly project interactively or via flags (supports TestNG, JUnit 5, Cucumber BDD; Web, API, Hybrid). | `testfly init my-tests --framework testng` |
| `testfly tools` | Lists all 88 available MCP tools with parameter counts and descriptions. | `testfly tools --search locator` |
| `testfly mcp` *(or `stdio`)* | Runs the MCP server over standard input/output (used by IDE agents like Claude Code, Cursor, Windsurf). | `testfly mcp` |
| `testfly init-config` | Emits a standard, pre-configured `testfly.yml` file in the current directory. | `testfly init-config` |
| `testfly` *(without args)* | Interactive TTY mode presenting a step-by-step terminal wizard. | `testfly` |

---

## Environment Diagnostics (`testfly doctor`)

Before running tests or connecting an AI agent, run `testfly doctor` to verify that all automation prerequisites are met:

```bash
testfly doctor
```

```text
========================================================
✈  TestFly MCP Environment Doctor — Status: HEALTHY
========================================================

✓ [PASS]  [Runtime] Python Version
          Details: Python 3.13.15 (/Users/.../.local/share/uv/tools/testfly-mcp/bin/python3)

✓ [PASS]  [Dependencies] Selenium Package
          Details: Version 4.49.0

✓ [PASS]  [Dependencies] Model Context Protocol SDK
          Details: Installed (mcp >= 1.2.0)

✓ [PASS]  [Browser] Google Chrome
          Details: Detected at /Applications/Google Chrome.app/Contents/MacOS/Google Chrome

✓ [PASS]  [IDE / AI Assistant] Claude Code Registration
          Details: Registered in ~/.claude/settings.json

✓ [PASS]  [Project] testfly.yml in Working Directory
          Details: Found at /workspace/testfly.yml

========================================================
```

---

## Connecting to AI Agents & IDEs

The TestFly MCP server runs seamlessly over `stdio` and connects to any MCP-compliant AI client.

### 1. Cursor IDE (`.cursor/mcp.json`)
Add the server definition to `.cursor/mcp.json` in your workspace or global configuration:

```json
{
  "mcpServers": {
    "testfly": {
      "command": "testfly",
      "args": ["mcp"]
    }
  }
}
```

### 2. Claude Code CLI (`~/.claude/settings.json`)
Register TestFly directly using the Claude CLI:

```bash
claude mcp add testfly testfly mcp
```

Or manually configure `~/.claude/settings.json`:
```json
{
  "mcpServers": {
    "testfly": {
      "command": "testfly",
      "args": ["mcp"]
    }
  }
}
```

### 3. VS Code Extension
TestFly provides an official VS Code extension (`vscode-extension`) that adds a dedicated **TestFly QA Hub** in the activity bar with:
- **Interactive Tools Explorer:** View and execute all 88 MCP tools with one-click forms.
- **One-Click Recorder Launch:** Start live recording directly from the editor.
- **Environment Doctor Status:** Real-time health badge.

---

## Tool Catalog Summary (88 Tools)

The tools are organized into 5 primary functional domains:

### 1. Browser Lifecycle & Navigation (12 Tools)
- `start_browser`: Initializes an isolated WebDriver session (Chrome/Firefox/Edge) with headless or visible desktop flags.
- `navigate`: Navigates to a target URL (supports alias `navigate_to`).
- `take_screenshot`: Captures base64 PNG screenshots for multi-modal AI review.
- `get_page_source`: Fetches raw or formatted DOM HTML.
- `check_accessibility`: Audits page against WCAG 2.1 contrast, labels, and aria rules.
- `inspect_page`: Discovers and categorizes all interactive buttons, inputs, links, and forms.
- `close_browser`: Terminates the active WebDriver session.

### 2. Element Interaction & Gestures (20 Tools)
- `find_element` / `find_elements`: Locates elements using CSS, XPath, ID, name, testid, or accessible role.
- `click`: Performs clicks with automated element scrolling into view.
- `type_text`: Clears and types into input elements with configurable delay.
- `select_option`: Selects dropdown items by value, visible text, or index.
- `hover`, `double_click`, `right_click`, `drag_and_drop`: Mouse gesture handling.
- `upload_file`: Bypasses OS file dialogs to attach files directly to file input elements.

### 3. Web-First Assertions & State Verifications (18 Tools)
- `assert_element_visible` / `assert_element_hidden`: Auto-waiting visibility verifications.
- `assert_element_enabled` / `assert_element_disabled`: Form control state checks.
- `assert_text_contains` / `assert_text_equals`: Content validations.
- `assert_title` / `assert_url`: Page navigation validations.
- `assert_attribute`: Verifies specific HTML attributes (e.g. `aria-expanded="true"`).

### 4. TestFly Java Codegen Engine (24 Tools)
- `detect_testfly`: Detects existing TestFly dependencies in `pom.xml` or `build.gradle`.
- `generate_java_page_object`: Emits clean `BasePage` classes with accessibility-first locators.
- `generate_java_testng`: Emits complete TestNG test classes extending `BaseTest`.
- `generate_java_junit5`: Emits complete JUnit 5 test classes extending `BaseJUnit5Test`.
- `generate_gherkin`: Emits Cucumber feature files, step definitions, and test runners.
- `generate_testfly_config`: Generates standard `testfly.yml`.

### 5. Smart Test Sharder & CI Quality Gates (14 Tools)
- `shard_test_suite`: Divides test suites dynamically across parallel CI worker nodes based on historical execution weights.
- `analyze_failure`: Performs AI root-cause analysis on test failure stack traces.

---

## Related Guides
- [Interactive Recorder & Chrome Companion](./recorder.md)
- [Interactive Web Studio](./interactive-studio.md)
- [ADR-001: Recorder Architecture](./adr-001-mcp-recorder-architecture.md)
- [Agentic Testing with AI Assistants](./agentic-testing.md)
