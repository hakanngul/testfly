---
id: testfly-mcp
title: TestFly MCP Bridge
sidebar_label: MCP Bridge & Playwright
sidebar_position: 2
description: Architecture, tools catalog, and 1-click configuration for the TestFly MCP Bridge paired with Playwright MCP.
---

# TestFly MCP Bridge & Playwright

TestFly's current source bridge separates browser automation from Java tooling:

```text
AI assistant
  ├─ Playwright MCP (@playwright/mcp): browser + DOM observations
  └─ TestFly Node bridge: Java generation + project/cache/patch tools
                            ↓
                      Java 21 TestFly tests
```

Source checked on 2026-10-04: `package.json` declares `@testfly/mcp` 1.1.0, Node.js 18+, and `bin/testfly-mcp.js`. The public npm registry returned 404 for that package, so this guide uses the source checkout. The package name/version in source is not proof of an npm release.

## Source installation and configuration

Follow [MCP Bridge & CLI](/docs/cli) for source checkout, CLI commands and the complete assistant configuration. Do not use `@modelcontextprotocol/server-playwright`: Microsoft's package is [`@playwright/mcp`](https://github.com/microsoft/playwright-mcp).

## Bridge tools

| Tool | Purpose |
|---|---|
| `generate_testfly_code` | Generate Java from supplied actions; review and compile the output. |
| `init_testfly_project` | Create a Maven test project. |
| `inspect_action_cache` | Read cached plans. |
| `manage_action_cache` | Invalidate one goal or clear cached plans. |
| `list_remediations` | List generated patch files. |
| `apply_remediation_patch` | Apply a selected patch to the workspace. |

The source exposes six tools over stdio JSON-RPC. Its `generate_testfly_code` schema accepts `testng`, `junit5`, and `page_object`, but the checked implementation currently emits a TestNG `BaseTest` class without branching on `framework`. Treat generated code as a starting point: do not assume a JUnit, Page Object, or Cucumber output is implemented just because a schema value or earlier guide mentions it.

Browser actions come from the separate browser MCP server. Reusing an action plan can avoid a new LLM request, but browser actions, cache reads and validations still take time; replay is not guaranteed to take 0 ms.

## Availability and verification

The [source repository](https://github.com/hakanngul/testfly-mcp) contains the Node bridge and IDE subprojects. The public npm package was unavailable at the verification date. A source checkout is the supported path shown here; publication and IDE marketplace availability must be checked separately. This guide does not claim that external IDE installation or browser execution was tested end to end.

The [Python recorder ADR](/docs/ai/adr-001-mcp-recorder-architecture), [Web Studio](/docs/ai/interactive-studio) and [recorder](/docs/ai/recorder) describe a historical implementation; those commands are not shipped by the Node CLI.
