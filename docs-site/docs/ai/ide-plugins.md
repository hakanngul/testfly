---
id: ide-plugins
title: IDE Plugins & Extensions
sidebar_label: IDE Plugins (VS Code & IntelliJ)
sidebar_position: 3
description: Modern visual extensions for VS Code and IntelliJ IDEA featuring interactive sidebars, Action Cache explorers, and AI self-healing patch reviewers.
---

:::info Source availability and setup limitation (2026-10-04)
The [separate source repository](https://github.com/hakanngul/testfly-mcp) contains `vscode-extension` and `jetbrains-plugin` projects. This page describes their source features and local packaging; it does not confirm marketplace publication or an end-to-end IDE installation. Their current registration code still emits npm references to the unavailable `@testfly/mcp`, and the VS Code registrar emits the wrong Playwright package. Inspect and correct generated MCP configuration using the [manual source setup](/docs/cli). The setup action alone is not sufficient at present.
:::


# IDE Plugins & Studio Extensions

TestFly provides official extensions for both **Visual Studio Code** and **JetBrains IDEs (IntelliJ IDEA, Aqua)**, transforming your development environment into an interactive AI test studio.

Zero Python. Native IDE performance.

---

## 1. Visual Studio Code Extension (`testfly-vscode`)

The **TestFly Studio** extension integrates TestFly with **Cursor**, **GitHub Copilot**, and **Claude Desktop**.

```
┌────────────────────────────────────────────────────────┐
│ TESTFLY STUDIO (Activity Bar)                          │
│                                                        │
│ ▶ Quick Actions                                        │
│   • ⚡ 1-Click MCP Setup (Cursor, Claude, Copilot)     │
│   • 🎯 Open Action Cache Explorer                      │
│   • 🩹 AI Self-Healing Patch Reviewer                  │
│   • ⚙️ Visual testfly.yml Editor                       │
│                                                        │
│ ▶ Environment & Project Status                         │
│   • JDK 21+: Ready                                     │
│   • Maven: Ready                                       │
│   • testfly.yml: Present (✓)                           │
│   • Action Cache: 3 goals cached                       │
│   • Self-Healing Patches: 1 pending                     │
└────────────────────────────────────────────────────────┘
```

### Key Capabilities
- **⚡ 1-Click Multi-Assistant Setup:** Configures Playwright MCP and TestFly Bridge across Cursor (`~/.cursor/mcp.json`), Claude Desktop (`claude_desktop_config.json`), VS Code Native (`.vscode/mcp.json`), and Claude Code with a single click.
- **🎯 Action Cache Explorer Webview:** Visual card layout displaying all compiled `act("Goal")` plans, steps, locators, and createdAt timestamps. Invalidate or clear goals with one click.
- **🩹 AI Self-Healing Patch Reviewer Webview:** Opens git diff `.patch` files synthesized by `AiHealingEngine` in a syntax-highlighted diff viewer with an **"Apply Patch to Java Code"** button.
- **⚙️ Visual `testfly.yml` Editor:** In-editor form interface to configure timeouts, parallel thread sliders, and AI Provider credentials.

### Packaging & Installation
To build the latest VSIX package from source:
```bash
cd testfly-mcp/vscode-extension
npm ci
npm run compile
npx @vscode/vsce package
```
In VS Code:
1. Open the Extensions view (`Cmd + Shift + X` / `Ctrl + Shift + X`).
2. Click the `...` menu in the top right.
3. Select **Install from VSIX...** and choose `the generated `.vsix` file`.

---

## 2. IntelliJ IDEA Plugin (`testfly-mcp-jetbrains`)

The **TestFly Studio** plugin for JetBrains IDEs provides an interactive **Tool Window** on the right sidebar, mirroring the full functionality of the VS Code extension.

### Interactive Tool Window Tabs
1. **📊 Dashboard Tab:**
   - Real-time environment and project status badges (`testfly.yml`, `pom.xml`, Java 21, MCP registration).
   - Quick action buttons: 1-Click MCP Setup, Initialize testfly.yml, Refresh, and Documentation.
2. **⚡ Action Cache Tab:**
   - Lists autonomous `act()` plans stored in `.testfly/action-cache.json`.
   - **Invalidate Goal** button to force LLM re-compilation on next test run.
   - **Clear All** button to purge the action cache.
3. **🩹 AI Patches Tab:**
   - Lists pending self-healing `.patch` files from `target/remediations/`.
   - Built-in split diff viewer showing code changes.
   - **`[ ✅ Apply Patch to Java Code ]`** button executing `git apply` directly inside the IDE.

### Packaging & Installation
To build the plugin distribution archive from source:
```bash
cd testfly-mcp/jetbrains-plugin
./gradlew buildPlugin
```
In IntelliJ IDEA / Aqua:
1. Open **Settings / Preferences** (`Cmd + ,` on macOS, `Ctrl + Alt + S` on Windows/Linux).
2. Navigate to **Plugins**.
3. Click the **Gear icon (⚙️)** and select **Install Plugin from Disk...**.
4. Select the generated ZIP under `testfly-mcp/jetbrains-plugin/build/distributions/`.
5. Restart the IDE. The **TestFly** tool window icon will appear on the right sidebar stripe.
