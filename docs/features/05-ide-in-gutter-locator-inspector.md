# Feature Plan: IDE In-Gutter Live Locator Inspector & DOM Highlighting

- **Feature Name**: `testfly-ide-inspector`
- **Target Version**: `v1.3.0`
- **Status**: Proposed / Architecture Design
- **Author**: TestFly Core Team

---

## 1. Executive Summary

Writing robust UI locators is historically one of the most tedious parts of test automation. Engineers constantly switch between their code editor and Chrome DevTools, copying CSS selectors, manually evaluating XPath queries in the browser console, and hoping the selector doesn't break at runtime.

The **TestFly IDE Locator Inspector** brings the browser directly into the developer's code editor (IntelliJ IDEA and VS Code). By connecting the editor to the active TestFly browser session via a lightweight JSON-RPC bridge and `testfly-mcp`, an interactive icon appears in the editor's gutter next to every `find()`, `getByRole()`, or `getByTestId()` call. Clicking the gutter icon instantly highlights matching elements in the live browser with a pulsing border, shows match counts directly inline, warns of ambiguous multiple matches, and suggests resilient alternative selectors when elements cannot be found.

---

## 2. Motivation & Problem Statement

### The Problem Today
1. **Endless Context Switching**: Developers spend up to 40% of test authoring time flipping between IntelliJ/VS Code and browser DevTools to test selectors.
2. **Ambiguous Selectors Discovered Too Late**: A selector that matches 3 elements instead of 1 is often only caught when a test intermittently clicks the wrong element during CI.
3. **No Interactive Feedback During Debugging**: In standard Selenium, you cannot interactively verify whether a locator matches the current page without evaluating expressions in the debugger evaluation window.

### The Solution: One-Click In-Gutter Highlighting & Validation
- **Gutter Icon**: A small icon appears on lines containing TestFly locators.
- **Live Pulse**: Clicking the icon causes the matched element in the live Chrome/Firefox window to pulse with a bright green outline.
- **Match Counter Tooltip**: Hovering shows: `1 match: <button class="btn-primary">Submit</button>`.
- **Ambiguity Warning**: If >1 match: `⚠️ 3 matches found! Click to cycle through elements`.
- **Instant Healing Suggestion**: If 0 matches: `❌ Not found on current page. Suggested: getByRole(BUTTON, "Save") [Click to Apply]`.

---

## 3. Architecture & Technical Design

```
+-----------------------------------------------------------------------------------+
|                        Code Editor (IntelliJ IDEA / VS Code)                      |
|                                                                                   |
|  Line 42:  [🔍] getByRole(Role.BUTTON, "Save").click();                            |
|                  |                                                                |
|                  | (Click Gutter Icon / CodeLens)                                 |
+------------------|----------------------------------------------------------------+
                   |
                   v (JSON-RPC over WebSocket :9876)
+-----------------------------------------------------------------------------------+
|                       io.testfly.ide.IdeBridgeServer                              |
|  - Embedded lightweight WebSocket server running in active test process           |
|  - Enabled via: `testfly.debug=true` or `-Dtestfly.ide=true`                      |
|  - Handles requests: `evaluateLocator`, `highlightElement`, `pickElement`        |
+-----------------------------------------------------------------------------------+
                   |
                   v
+-----------------------------------------------------------------------------------+
|                           Active WebDriver Session                                |
|  1. Evaluates locator against live DOM                                            |
|  2. Injects temporary pulse overlay CSS:                                          |
|     `outline: 3px solid #10b981; box-shadow: 0 0 12px #10b981;`                   |
|  3. Returns match count, tag name, text content, bounding box, and HTML snippet   |
+-----------------------------------------------------------------------------------+
                   |
                   v (Response)
+-----------------------------------------------------------------------------------+
|                        Editor Feedback Display                                    |
|  - Inline CodeLens: "✓ 1 match" or "⚠️ 4 matches"                                |
|  - Tooltip with element preview & quick-fix suggestions                           |
+-----------------------------------------------------------------------------------+
```

### Core Components

1. **`io.testfly.ide.IdeBridgeServer`**:
   - A zero-dependency embedded WebSocket/HTTP daemon started when tests are launched in debug mode or when `testfly.ide: true` in `testfly.yml`.
   - Listens on `http://127.0.0.1:9876`.
   - Exposes JSON-RPC endpoints:
     - `locator/evaluate`: Receives locator string and strategy; returns count, coordinates, and DOM attributes.
     - `locator/highlight`: Scrolls element into view and animates pulsing border for 2 seconds.
     - `element/pick`: Enters element picker mode in the browser; clicks in browser stream code snippet back to IDE cursor.

2. **`testfly-intellij-plugin`** (IntelliJ IDEA Plugin):
   - Implements `LineMarkerProvider` for Java/Kotlin files.
   - Inspects AST calls to `find()`, `getByRole()`, `getByText()`, `getByTestId()`.
   - Renders gutter icon with clickable action to evaluate and pulse the live element.
   - Provides Quick-Fix intention: "Replace with suggested accessible locator".

3. **`testfly-vscode`** (VS Code Extension):
   - Implements `CodeLensProvider` and `TextEditorDecorationType`.
   - Displays inline match count hints next to locator lines.
   - Provides "TestFly: Pick Element in Browser" command.

4. **DOM Highlighter Script (`testfly-highlighter.js`)**:
   - Injected script that draws a floating badge over the element in the browser showing its selector and dimensions without altering page layout.

---

## 4. User Experience & Scenarios

### Scenario 1: Ambiguous Locator Warning
Developer writes:
```java
find(".item-card").click();
```
- The IDE gutter displays an amber warning icon `⚠️`.
- Inline CodeLens reads: `⚠️ 4 elements matched. Use getByIndex(0) or narrow with parent`.
- Clicking the gutter icon scrolls through elements 1 to 4 in the browser with numbers `[1/4]`, `[2/4]` rendered above them.

### Scenario 2: Instant Locator Healing Suggestion
Developer has an outdated locator:
```java
find("#btn-submit").click();
```
- In the active browser session, the ID has changed.
- Gutter displays red `❌ 0 matches`.
- Hovering shows popup:
  > **Element not found on current page**
  > An element with matching text `"Submit Order"` was found:
  > `<button type="submit" data-testid="checkout-submit">Submit Order</button>`
  > **[Replace with `getByRole(Role.BUTTON, "Submit Order")`]** (Alt+Enter)

### Scenario 3: Click-to-Code Element Picker
1. In the IDE, developer places cursor inside test method.
2. Clicks **"Pick Element in Browser"** (or press `Cmd+Shift+P` -> `TestFly: Inspect`).
3. Developer clicks any button or input in the live Chrome browser.
4. TestFly automatically generates the optimal semantic selector and inserts it at cursor:
   ```java
   getByRole(Role.BUTTON, "Add to Cart").click();
   ```

---

## 5. Phased Implementation Plan

### Phase 1: Java Embedded IDE Bridge Server (Sprint 1)
- [ ] Implement `IdeBridgeServer` in `io.testfly.ide` using lightweight standard `com.sun.net.httpserver` and WebSocket handler.
- [ ] Implement JSON-RPC protocol (`evaluate`, `highlight`, `suggest`).
- [ ] Implement DOM highlighter injection script with CSS pulse animation.
- [ ] Enable auto-start in `BaseTest` when running from IDE (detect IntelliJ/Eclipse/VSCode JVM runner properties).

### Phase 2: VS Code Extension (`testfly-vscode`) (Sprint 2)
- [ ] Scaffold TypeScript VS Code extension in `tools/testfly-vscode`.
- [ ] Implement `CodeLensProvider` detecting `find`, `getBy*` patterns.
- [ ] Connect to `localhost:9876` and display inline match counts.
- [ ] Add gutter icons and command palette shortcuts.

### Phase 3: IntelliJ IDEA Plugin (`testfly-intellij`) (Sprint 3)
- [ ] Scaffold Gradle IntelliJ Platform plugin in `tools/testfly-intellij`.
- [ ] Implement `LineMarkerProvider` using IntelliJ PSI (Program Structure Interface) for Java.
- [ ] Add gutter icons with click-to-highlight functionality.
- [ ] Implement quick-fix intention to automatically apply healed locators.

---

## 6. Security & Developer Workflow Isolation

- **Localhost Only**: `IdeBridgeServer` binds strictly to `127.0.0.1`. Remote network connections are rejected.
- **CI Disabled**: Automatically disabled when `CI=true` environment variable is detected; zero overhead or port binding during GitHub Actions / GitLab CI runs.
- **Non-blocking**: If the IDE is closed or the plugin is not installed, the bridge server runs passively with negligible resource consumption (<2MB RAM).
