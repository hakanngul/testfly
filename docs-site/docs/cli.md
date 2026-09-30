---
id: cli
title: TestFly MCP Bridge & CLI
sidebar_label: MCP Bridge & CLI
sidebar_position: 3
description: "The official TestFly MCP Bridge & CLI: zero-dependency Java 21 project scaffolding via NPX, AI coding assistant integration (Cursor, Claude, Copilot), and Playwright MCP pairing."
---

# TestFly MCP Bridge & CLI

The **TestFly MCP Bridge** (`@testfly/mcp`) is the official Model Context Protocol bridge and command-line toolkit for the TestFly framework. Built on Node.js with **zero Python and zero pip dependencies**, it pairs with **Playwright MCP** for live browser control while providing autonomous **TestFly Java 21 test generation**, project scaffolding, action cache management, and AI self-healing patch remediation.

---

## ⚡ Instant Project Scaffolding via NPX

You can bootstrap a complete, production-ready TestFly Java 21 test automation project with a single command — no prior installation needed:

```bash
npx @testfly/mcp init my-test-suite
```

This immediately scaffolds:
- `testfly.yml` — Pre-configured execution, timeouts, and reporting settings.
- `pom.xml` — Configured with Java 21 (`<maven.compiler.release>21</maven.compiler.release>`) and the latest `io.github.hakanngul:testfly:1.0.7` dependency.
- `src/test/java/com/example/tests/SampleWebTest.java` — A working sample test extending `BaseTest` with web-first assertions.

Run your new suite right away:

```bash
cd my-test-suite
mvn test
```

---

## 🤖 AI Assistant MCP Setup

TestFly MCP connects directly to **Cursor**, **Claude Desktop**, **GitHub Copilot**, and **Claude Code** over standard I/O (JSON-RPC).

### Option 1: 1-Click Setup via VS Code Extension (Recommended)

1. Install the **TestFly Studio** extension (`testfly-vscode-1.1.0.vsix`).
2. Open the Command Palette (`Cmd+Shift+P` / `Ctrl+Shift+P`).
3. Run **`TestFly: 1-Click Multi-Assistant MCP Setup`**.
4. Cursor, Claude Desktop, and VS Code are automatically configured with both **Playwright MCP** and the **TestFly Bridge**.

---

### Option 2: Manual Configuration via NPX

Add the following to your `claude_desktop_config.json` or `.cursor/mcp.json`:

```json
{
  "mcpServers": {
    "playwright": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-playwright"]
    },
    "testfly": {
      "command": "npx",
      "args": ["-y", "@testfly/mcp"]
    }
  }
}
```

:::info Bring Your Own Browser Architecture
- **Browser Driving:** Delegated to the official, high-speed **Playwright MCP** (`@modelcontextprotocol/server-playwright`).
- **Test Generation & Intelligence:** Handled by **TestFly Bridge** (`@testfly/mcp`), which compiles browser actions into idiomatic TestFly Java 21 test code.
:::

---

## 🛠️ Available MCP Tools

When registered with your AI assistant, `@testfly/mcp` provides the following tools:

| Tool | Description |
| :--- | :--- |
| `generate_testfly_code` | Converts recorded browser actions or DOM snapshots into idiomatic TestFly Java 21 tests (`BaseTest`, `BasePage`, `getByRole`, `assertThat`). |
| `init_testfly_project` | Scaffolds a complete TestFly Java 21 Maven test automation suite (`pom.xml`, `testfly.yml`, sample test). |
| `inspect_action_cache` | Reads and inspects autonomous `act("Goal")` plans frozen in `.testfly/action-cache.json` for 0ms replay. |
| `manage_action_cache` | Invalidates or clears cached action plans so the AI re-compiles them upon next run. |
| `list_remediations` | Lists pending AI self-healing git diff `.patch` files generated in `target/remediations/`. |
| `apply_remediation_patch` | Applies a self-healing `.patch` file directly to the Java test source code. |

---

## CLI Commands Reference

When invoked from terminal, `@testfly/mcp` supports:

```bash
# Scaffold a new test automation project
npx @testfly/mcp init [directory-name]

# Check bridge server version
npx @testfly/mcp --version

# View usage help
npx @testfly/mcp --help
```

---

## Next Steps

- [Getting Started](/docs/getting-started) — Run your first test in under 5 minutes.
- [Configuration Reference](/docs/configuration) — Full `testfly.yml` documentation.
- [MCP Bridge & Playwright Architecture](/docs/ai/testfly-mcp) — Deep dive into the codegen engine and action cache.
- [Agentic Testing & Autonomous AI](/docs/ai/agentic-testing) — Goal-oriented execution with `act()` and self-healing.
