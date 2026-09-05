---
id: ide-plugins
title: IDE Plugins & Extensions
sidebar_label: IDE Plugins (IDEA & VS Code)
sidebar_position: 3
description: Seamlessly connect JetBrains AI Assistant, Claude Code, and GitHub Copilot to TestFly using dedicated IDE plugins.
---

# IDE Plugins & Extensions

TestFly provides official plugins for both **IntelliJ IDEA** and **Visual Studio Code** to automate MCP server registration, environment health checks, and project bootstrapping without manual configuration.

---

## 1. IntelliJ IDEA Plugin (`testfly-mcp-jetbrains`)

The IntelliJ IDEA plugin integrates TestFly with **JetBrains AI Assistant**.

### Key Features
- **Zero-Config AI Registration:** Automatically adds `testfly-mcp` to `aiAssistantMcpServers.xml`.
- **Dedicated Menu:** Adds a top-level **`Tools → TestFly MCP`** menu.
- **Diagnostics Action:** Instant health check reporting Python, Selenium, and AI Assistant status.
- **Studio Launcher:** One-click launch of the **Interactive Web Studio** (`testfly-mcp ui`).
- **Template Initializer:** Creates a production-ready `testfly.yml` configuration file in your project root.

### Installation Steps
1. Locate the distribution archive:
   ```text
   testfly-mcp/jetbrains-plugin/build/distributions/testfly-mcp-jetbrains-1.0.0.zip
   ```
2. In IntelliJ IDEA, open **Settings / Preferences** (`Cmd + ,` on macOS, `Ctrl + Alt + S` on Windows/Linux).
3. Navigate to **Plugins**.
4. Click the **Gear icon (⚙️)** in the top right and select **Install Plugin from Disk...**.
5. Select `testfly-mcp-jetbrains-1.0.0.zip` and click **OK**.
6. Restart the IDE when prompted.

---

## 2. Visual Studio Code Extension (`testfly-mcp`)

The VS Code extension integrates TestFly with **Claude Code** and **GitHub Copilot**.

### Key Features
- **Interactive Status Bar Item:** Shows `$(radio-tower) TestFly MCP` in the lower-left status bar.
- **QuickPick Actions Menu:** Click the status bar item to run diagnostics, register AI assistants, launch the web studio, or generate `testfly.yml`.
- **Auto-Registration:** Writes the MCP server configuration into `~/.claude/settings.json` and workspace `.mcp.json`.
- **Pip Management:** One-click install and upgrade of the underlying `testfly-mcp` Python package.

### Installation Steps
1. Locate the compiled VSIX package:
   ```text
   testfly-mcp/vscode-extension/testfly-mcp-1.0.0.vsix
   ```
2. In VS Code, open the **Extensions** view (`Cmd + Shift + X` or `Ctrl + Shift + X`).
3. Click the `...` (More Actions) menu in the top right of the Extensions panel.
4. Select **Install from VSIX...** and choose `testfly-mcp-1.0.0.vsix`.
5. The extension activates automatically on startup.

---

## 3. Verifying the Connection

Once installed:
1. Open the AI chat panel in your IDE (JetBrains AI Assistant or Claude Code / Copilot).
2. Enter the prompt:
   > *"What MCP tools do you have for TestFly?"*
3. The AI assistant will list the active tools (`start_browser`, `navigate`, `generate_java_page_object`, etc.), confirming that the connection is active.
