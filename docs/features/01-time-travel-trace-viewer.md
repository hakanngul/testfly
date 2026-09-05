# Feature Plan: TestFly Time-Travel & Interactive Trace Viewer

- **Feature Name**: `testfly-trace-viewer`
- **Target Version**: `v1.2.0`
- **Status**: Proposed / Architecture Design
- **Author**: TestFly Core Team

---

## 1. Executive Summary

Playwright's most praised feature is its **Trace Viewer**, which allows QA engineers and developers to debug failed tests by "traveling back in time" to any step of a test run. 

This proposal introduces the **TestFly Trace Viewer**: a lightweight, framework-native time-travel execution timeline. During test execution, TestFly captures sequential DOM snapshots, console logs, network activity, and micro-screenshots on every step. When a test finishes (especially on failure), TestFly packages this metadata into a standalone, zero-dependency interactive HTML viewer (`target/testfly-trace/trace.html` or `.testfly-trace.zip`).

---

## 2. Motivation & Problem Statement

### The Problem Today
1. When a test fails in CI, developers only get a static screenshot of the end state and a textual stack trace.
2. The root cause (e.g., an animated modal blocking the button for 250ms, a 500 error on a background AJAX call, or a race condition) is often invisible in the final screenshot.
3. Teams waste hours trying to reproduce transient/flaky failures locally.

### The Solution
A visual step-by-step scrubber where engineers can:
- Drag a timeline slider across each step (`open`, `type`, `click`, `assertThat`).
- View the DOM state, browser console logs, and HTTP traffic at that exact millisecond.
- Inspect element attributes without needing to run the browser live again.

---

## 3. Architecture & Technical Design

```
+-----------------------------------------------------------------------+
|                             Test Execution                            |
|                                                                       |
|  [Test Code] ---> [StepLogger] ---> [WaitEngine] ---> [Locator.click] |
|                         |                 |                  |        |
+-------------------------|-----------------|------------------|--------+
                          v                 v                  v
                 +------------------------------------------------------+
                 |               TestFlyTraceCollector                  |
                 | - Action Events (timestamp, action, selector)        |
                 | - Viewport Screencast (lightweight JPEG frames)     |
                 | - DOM Snapshots (HTML string + inline styles)       |
                 | - Console Logs & Network Events (via BiDi/CDP)       |
                 +------------------------------------------------------+
                                            |
                                            v (on test finish)
                 +------------------------------------------------------+
                 |                  TracePackager                       |
                 | Output: target/testfly-traces/{testId}.trace.zip     |
                 | - manifest.json                                      |
                 | - dom-snapshots/                                     |
                 | - frames/                                            |
                 +------------------------------------------------------+
                                            |
                                            v (view)
                 +------------------------------------------------------+
                 |          Interactive Web Trace Viewer                |
                 | Embedded in HTML Report & accessible via:            |
                 | `testfly-mcp trace target/testfly-traces/{id}.zip`   |
                 +------------------------------------------------------+
```

### Core Components
1. **`io.testfly.trace.TraceCollector`**:
   - Registered via `TestExecutionListener`.
   - Listens to `StepLogger` events and `WebDriverListener` hooks.
   - Collects action metadata: timestamp, action type, target locator, duration, success/failure.
2. **`io.testfly.trace.DomSnapshotter`**:
   - Executes a lightweight script via CDP / `JavascriptExecutor` to capture the current DOM tree and scroll positions without heavy overhead (<15ms per snapshot).
3. **`io.testfly.trace.NetworkCaptureListener`**:
   - Hooks into Selenium 4 BiDi / CDP network interception to record request URL, method, status code, response time, and payload headers.
4. **`io.testfly.trace.TraceViewerGenerator`**:
   - Single-page offline Vue/VanillaJS viewer template embedded inside `testfly.jar`.
   - Embeds into TestFly HTML Report as an interactive modal, or opens via `testfly-mcp trace <zip-file>`.

---

## 4. User-Facing Configuration & API

### Configuration (`testfly.yml`)
```yaml
tracing:
  enabled: true                 # false | true | on-failure-only (default: on-failure-only)
  screenshots: true             # capture frame at each step
  domSnapshots: true            # capture DOM hierarchy
  network: true                 # record HTTP network requests
  consoleLogs: true             # record console.log / console.error
  retentionDays: 7
```

### Programmatic Inspection (Optional)
```java
public class CheckoutTest extends BaseTest {

    @Test
    public void complexCheckoutFlow() {
        // Tracing is automatic based on YAML, but steps can be annotated
        step("Navigate to store", () -> open("/store"));
        step("Add item to cart", () -> {
            getByRole(Role.BUTTON, "Add to Cart").click();
            assertThat(find(".cart-badge")).hasText("1");
        });
        // On failure, trace is automatically saved and linked in target/testfly-report.html
    }
}
```

---

## 5. Phased Implementation Plan

### Phase 1: Data Collection & Storage (Sprint 1)
- [ ] Create `io.testfly.trace` package in `testfly`.
- [ ] Implement `TraceEvent` model (Step, Action, DomSnapshot, NetworkEntry, ConsoleEntry).
- [ ] Implement `TraceCollector` with ThreadLocal isolation for parallel tests.
- [ ] Add `tracing` block parsing in `TestFlyConfig`.
- [ ] Save `.trace.zip` in `target/testfly-traces/` containing raw JSON and image frames.

### Phase 2: Web Viewer UI (Sprint 2)
- [ ] Create standalone `trace-viewer.html` single-page application (vanilla HTML5/CSS/JS, zero NPM runtime dependencies).
- [ ] Implement timeline slider with scrubber.
- [ ] Left pane: Action list (steps, clicks, assertions).
- [ ] Center pane: Interactive iframe rendering DOM snapshot or visual frame.
- [ ] Right pane: Tabs for DOM Tree, Console Logs, Network Requests, and System Metrics.

### Phase 3: Reporting & MCP Integration (Sprint 3)
- [ ] Embed direct "View Trace" button in `HtmlReportGenerator` and Allure reports.
- [ ] Add CLI subcommand in `testfly-mcp`: `testfly-mcp trace <file.zip>` which spins up local server and opens browser.
- [ ] Add tool `inspect_trace` in `testfly-mcp` so AI agents can examine traces of failed CI runs.

---

## 6. Performance & Overhead Considerations

- **Memory**: Snapshots are buffered on disk (`/tmp/testfly-trace-...`) or streamed to a compressed zip to avoid JVM heap bloat during 1000+ test parallel runs.
- **CPU Overhead**: `on-failure-only` mode keeps memory ring-buffer of last 20 actions; discards data on test pass, writes to disk only when test fails. Overhead is < 3% execution time.
