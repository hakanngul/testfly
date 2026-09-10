---
id: cli
title: TestFly CLI & Scaffolder
sidebar_label: TestFly CLI
sidebar_position: 3
description: "The official TestFly CLI: project scaffolding (TestNG, JUnit 5, Cucumber BDD), environment diagnostics, interactive studio, and MCP server."
---

# TestFly CLI & Scaffolder

The **TestFly CLI** (`testfly`) is the official command-line toolkit for the TestFly framework. It provides instant project scaffolding, environment diagnostics, an interactive web studio, and an integrated Model Context Protocol (MCP) server for AI-driven test automation.

---

## Installation

Install the TestFly CLI via `pip` or `uv`:

```bash
# Via pip
pip install testfly-mcp

# Or if developing locally from the testfly-mcp repository
cd testfly-mcp
pip install -e .
```

Verify your installation:

```bash
testfly --version
# Output: testfly 1.0.0
```

:::info Backwards Compatibility
The CLI can be invoked using `testfly`, `testfly-cli`, or `testfly-mcp`. Existing IDE configurations and MCP client settings referencing `testfly-mcp` continue to work seamlessly.
:::

---

## Core Commands Overview

```bash
testfly --help
```

| Command | Description |
| :--- | :--- |
| `testfly init [name]` | Scaffold a new, ready-to-run TestFly project (TestNG, JUnit 5, Cucumber). |
| `testfly doctor` | Run environment diagnostics (Python, Selenium, Chrome, IDE configurations). |
| `testfly studio` *(or `ui`)* | Launch the interactive visual Web Studio at `http://127.0.0.1:8765`. |
| `testfly mcp` *(or `stdio`)* | Start the MCP server over standard I/O for AI assistants (Claude, Cursor, etc.). |
| `testfly tools` | Browse and filter all 88 available MCP automation and codegen tools. |
| `testfly init-config` | Generate a standard `testfly.yml` configuration in the current directory. |
| `testfly` *(in terminal)* | Launch the interactive terminal menu when run without arguments. |

---

## 1. Project Scaffolding (`testfly init`)

Bootstrap a production-ready TestFly automation project with one command:

```bash
testfly init my-test-suite
```

This generates a complete project structure including `pom.xml`, `testfly.yml`, `.gitignore`, `README.md`, and sample tests extending `BaseTest` or `BaseApiTest`.

### Command Options

```bash
testfly init [name] [options]
```

- `--framework`, `-f`: Test runner framework (`testng` [default], `junit5`, `cucumber`).
- `--type`, `-t`: Test suite type (`web` [default], `api`, `hybrid`).
- `--base-url`, `-u`: Base application URL (default: `https://example.com`).
- `--group-id`, `-g`: Maven groupId (default: `com.example`).
- `--artifact-id`, `-a`: Maven artifactId (default: derived from project name).
- `--dir`, `-d`: Explicit target directory path.

### Examples

#### TestNG Web UI Automation Suite
```bash
testfly init web-regression --framework testng --type web --base-url https://my-app.com
```

#### JUnit 5 API Test Suite
```bash
testfly init payment-api-tests --framework junit5 --type api --base-url https://api.my-app.com
```

#### Cucumber BDD Hybrid Suite (Web + API)
```bash
testfly init e2e-bdd --framework cucumber --type hybrid
```

---

## 2. Environment Diagnostics (`testfly doctor`)

Check that your environment satisfies all prerequisites for test execution, browser automation, and AI tooling:

```bash
testfly doctor
```

Example output:

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

## 3. Interactive Web Studio (`testfly studio`)

Launch a local visual dashboard that allows you to inspect page elements, generate TestFly Page Objects, and test assertions in real time:

```bash
testfly studio
```

Options:
- `--port`, `-p`: Port for the web studio server (default: `8765`).
- `--no-browser`: Do not open the default browser automatically upon launch.

---

## 4. MCP Server for AI Assistants (`testfly mcp`)

Start the JSON-RPC Model Context Protocol server:

```bash
testfly mcp
```

This endpoint exposes 88 tools to Cursor, Claude Code, Windsurf, or JetBrains AI Assistant, enabling AI agents to drive real browsers, validate DOM hierarchies, and generate idiomatic TestFly Java test code.

---

## 5. Tool Catalog Search (`testfly tools`)

List and search available automation tools:

```bash
# List all 88 tools
testfly tools

# Filter tools by keyword
testfly tools --search locator
testfly tools --search assertion
```

---

## Next Steps

- [Getting Started](/docs/getting-started) — Run your first test in under 5 minutes.
- [Configuration Reference](/docs/configuration) — Full `testfly.yml` documentation.
- [AI & MCP Overview](/docs/ai/overview) — Agentic test automation with TestFly.
