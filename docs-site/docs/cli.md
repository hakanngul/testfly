---
id: cli
title: TestFly MCP Bridge & CLI
sidebar_label: MCP Bridge & CLI
sidebar_position: 3
description: "The official TestFly MCP Bridge & CLI: zero-dependency Java 21 project scaffolding via NPX, AI coding assistant integration (Cursor, Claude, Copilot), and Playwright MCP pairing."
---

# TestFly MCP Bridge & CLI

The separate Node.js bridge provides project scaffolding and MCP tools. Browser inspection is handled by [Microsoft Playwright MCP](https://github.com/microsoft/playwright-mcp), whose package is `@playwright/mcp`.

## Install from source

Source checked on 2026-10-04: `package.json` declares `@testfly/mcp` 1.1.0, Node.js 18+, and `bin/testfly-mcp.js`. The public npm registry returned 404 for that package, so this guide uses the source checkout. The package name/version in source is not proof of an npm release.

```bash
git clone https://github.com/hakanngul/testfly-mcp.git
cd testfly-mcp
node bin/testfly-mcp.js --version
node bin/testfly-mcp.js init ../my-test-suite
```

The checkout is the [source repository](https://github.com/hakanngul/testfly-mcp). The bridge script uses Node.js built-ins; Java tests still require JDK 21 and Maven. Review the generated `pom.xml` and `testfly.yml` against the [Getting Started guide](/docs/getting-started), then run `mvn test` from the new project.

## Configure an assistant

Replace `/absolute/path/testfly-mcp` with the actual checkout location. This `mcpServers` shape is for clients that support it, such as Claude Desktop and Cursor; other clients may require a different configuration shape.

```json
{
  "mcpServers": {
    "playwright": {
      "command": "npx",
      "args": ["-y", "@playwright/mcp@latest"]
    },
    "testfly": {
      "command": "node",
      "args": ["/absolute/path/testfly-mcp/bin/testfly-mcp.js"]
    }
  }
}
```

The IDE setup actions currently emit package references that need correction. Check their generated configuration against this example before starting the servers; see [IDE plugins](/docs/ai/ide-plugins).

## MCP tools

| Tool | Purpose |
|---|---|
| `generate_testfly_code` | Generate Java from supplied actions; review and compile the output. |
| `init_testfly_project` | Create a Maven test project. |
| `inspect_action_cache` | Read cached plans. |
| `manage_action_cache` | Invalidate one goal or clear cached plans. |
| `list_remediations` | List generated patch files. |
| `apply_remediation_patch` | Apply a selected patch to the workspace. |

These six tools are declared by the checked bridge source; the bridge does not expose 88 browser automation tools. Obtain live browser observations through Playwright MCP, then pass relevant actions to the code generator.

## CLI commands

```bash
node bin/testfly-mcp.js init ../my-test-suite
node bin/testfly-mcp.js --version
node bin/testfly-mcp.js --help
```

`studio` and `record` belong to the historical Python implementation. They are not commands of this Node bridge. See the [historical recorder architecture](/docs/ai/adr-001-mcp-recorder-architecture).

## Next steps

- [MCP architecture](/docs/ai/testfly-mcp)
- [Agentic testing](/docs/ai/agentic-testing)
- [Prompt recipes](/docs/ai/prompt-recipes)
