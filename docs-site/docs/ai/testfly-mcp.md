---
id: testfly-mcp
title: TestFly MCP Server & CLI
sidebar_label: MCP Server & CLI
sidebar_position: 2
description: Installation, command-line interface, environment diagnostics, and tool catalog of the TestFly MCP server.
---

# TestFly MCP Server & CLI

The **TestFly MCP Server** is the execution engine that powers AI-driven browser automation and code generation. It exposes 88 tools to AI assistants via the standard Model Context Protocol (MCP) JSON-RPC specification.

---

## Installation

### Local Development / From Source
Clone or navigate to the `testfly-mcp` directory and install in editable mode:

```bash
cd testfly-mcp
pip install -e .
```

Verify that the CLI executable is correctly resolved in your environment:
```bash
testfly-mcp --version
# Output: testfly-mcp 1.0.0
```

---

## Command-Line Interface (CLI)

`testfly-mcp` provides a command-line interface with subcommands:

```bash
testfly-mcp --help
```

### Available Commands

| Command | Description |
| :--- | :--- |
| `testfly-mcp --help` | Display options, commands, and usage examples. |
| `testfly-mcp --version` | Output the installed version (`1.0.0`). |
| `testfly-mcp doctor` | Run environment and dependency diagnostic checks. |
| `testfly-mcp tools` | List all 88 available MCP tools with parameter counts and descriptions. |
| `testfly-mcp tools --search <query>` | Filter tools by name or description keyword. |
| `testfly-mcp ui` | Launch the **Interactive Web Studio** in your default browser (`http://127.0.0.1:8765`). |
| `testfly-mcp init-config` | Generate a standard `testfly.yml` template in the current directory. |
| `testfly-mcp stdio` | Run the MCP server over standard I/O (auto-detected when spawned by IDEs). |
| `testfly-mcp` (in terminal) | Interactive TTY mode presenting an interactive terminal menu. |

---

## Environment Diagnostics (`testfly-mcp doctor`)

Run `testfly-mcp doctor` to verify that your environment meets all test automation requirements:

```bash
testfly-mcp doctor
```

Example Output:
```text
========================================================
✈  TestFly MCP Environment Doctor — Status: HEALTHY
========================================================

✓ [PASS]  [Runtime] Python Version
          Details: Python 3.12.14 (/usr/local/bin/python3)

✓ [PASS]  [Dependencies] Selenium Package
          Details: Version 4.48.0

✓ [PASS]  [Dependencies] Model Context Protocol SDK
          Details: Installed (>=2.0.0)

✓ [PASS]  [Browser] Google Chrome
          Details: Detected at /Applications/Google Chrome.app/Contents/MacOS/Google Chrome

✓ [PASS]  [IDE / AI Assistant] Claude Code Registration
          Details: Registered in ~/.claude/settings.json

✓ [PASS]  [Project] testfly.yml in Working Directory
          Details: Found at /workspace/testfly.yml

========================================================
```

---

## Tool Catalog Summary (88 Tools)

The tools are organized into 4 functional domains:

### 1. Browser Lifecycle & Navigation
- `start_browser`: Starts a real browser (Chrome/Firefox), supports headless mode and custom window dimensions.
- `navigate`: Navigates to a target URL (with automatic alias `navigate_to`).
- `take_screenshot`: Captures live page screenshots as base64 PNG images.
- `get_page_source`: Fetches live DOM HTML.
- `check_accessibility`: Audits page for missing labels, contrast, and accessibility violations.
- `inspect_page`: Summarizes interactive form fields, buttons, and links.
- `close_browser`: Tears down the active WebDriver session.

### 2. Element Interaction & Inspection
- `find_element` / `find_elements`: Locates elements using CSS, XPath, ID, name, or accessibility selectors.
- `click`: Performs clicks with auto-scrolling into view.
- `type_text`: Clears and types into input elements.
- `select_option`: Selects dropdown items by value, visible text, or index.
- `hover`, `double_click`, `right_click`, `drag_and_drop`: Mouse gestures.
- `upload_file`: Handles native file chooser dialogs.

### 3. Assertions & Validation
- `assert_element_visible` / `assert_element_hidden`: Auto-waiting visibility checks.
- `assert_text_contains` / `assert_text_equals`: Exact and partial text checks.
- `assert_title` / `assert_url`: Page-level title and URL validations.
- `assert_element_enabled` / `assert_element_disabled`: State verifications.

### 4. Native TestFly Codegen
- `detect_testfly`: Scans project root and `pom.xml` for `io.testfly` dependency.
- `generate_java_page_object`: Produces clean Page Object classes extending `BasePage` and matching test classes.
- `generate_java_testng`: Produces TestNG test classes extending `BaseTest`.
- `generate_java_junit5`: Produces JUnit 5 test classes extending `BaseJUnit5Test`.
- `generate_gherkin`: Produces Cucumber feature files, steps extending `BaseCucumberSteps`, and runner extending `BaseCucumberTest`.
- `generate_testfly_config`: Generates standard `testfly.yml`.
- `generate_testfly_pom`: Generates Maven `pom.xml` with TestFly dependencies.
