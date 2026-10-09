---
id: overview
title: AI & MCP Automation Overview
sidebar_label: Overview & Architecture
sidebar_position: 1
description: How TestFly integrates with AI coding assistants using the Model Context Protocol (MCP) for real-browser test automation and validated code generation.
---

# AI & MCP Automation Overview

TestFly has two distinct AI paths:

1. **Java runtime:** `act()`, `byIntent()`, AI assertions, action caching and patch generation run inside TestFly tests. Configure the Java framework's AI provider separately from your IDE assistant.
2. **External MCP tooling:** an assistant uses Playwright MCP for browser inspection and a separate Node.js TestFly bridge for project/code generation and cache/patch operations.

```text
Assistant → Playwright MCP → browser inspection
          → TestFly Node bridge → Java source / cache / patch tools
Java tests → configured AI provider → runtime actions / assertions
```

Source checked on 2026-10-04: `package.json` declares `@testfly/mcp` 1.1.0, Node.js 18+, and `bin/testfly-mcp.js`. The public npm registry returned 404 for that package, so this guide uses the source checkout. The package name/version in source is not proof of an npm release.

## Workflow

Inspect the real application DOM before choosing locators. Prefer semantic locators supported by `io.testfly.locator`. Review generated code and compile it against your TestFly dependency; code generation does not prove that a test works. Run the test against a controlled environment before using it in CI.

## Components

| Component | Where to start |
|---|---|
| Java runtime AI features | [Agentic testing](/docs/ai/agentic-testing) |
| Six-tool Node MCP bridge | [MCP Bridge & CLI](/docs/cli) |
| Live browser inspection | [Microsoft Playwright MCP](https://github.com/microsoft/playwright-mcp) |
| IDE source projects | [IDE plugins](/docs/ai/ide-plugins) |
| Generation prompts | [Prompt recipes](/docs/ai/prompt-recipes) |

The earlier Python/Selenium server and 88-tool recorder model are historical. The [ADR](/docs/ai/adr-001-mcp-recorder-architecture), [Web Studio](/docs/ai/interactive-studio) and [recorder](/docs/ai/recorder) pages preserve that design without advertising those commands as current Node features.
