# TestFly – Video Recording on Test Failure

TestFly provides native, zero-dependency **Web UI video recording** modeled after Playwright's `video: 'retain-on-failure'` capability.

When enabled, TestFly records the live browser interaction during test execution. If the test passes, the buffered frames are discarded immediately. If the test fails, TestFly automatically stitches the frames into an animated recording file (`.gif`) and embeds it directly into the interactive HTML report, Allure results, and test traces.

---

## Key Benefits

- **Zero Native Dependencies**: Does not require `ffmpeg`, `X11`, or external OS binaries. Runs seamlessly in headless Docker containers, Linux CI, GitHub Actions, macOS, and Windows.
- **Chrome DevTools Protocol (CDP v152) Screencast**: On Chromium browsers (Chrome and Edge), frames are captured asynchronously via CDP screencast without blocking or interfering with WebDriver commands.
- **Smart Retention (`retain-on-failure`)**: Only failed tests retain their recording files, saving CI disk storage and runner memory.
- **Universal Multi-Framework Support**: Works out of the box with **TestNG** (`BaseTest`), **JUnit 5** (`BaseJUnit5Test`), and **Cucumber 7 BDD** (`@TestFlySession`).
- **Seamless Reporting**: Embedded directly in `target/testfly-report.html`, Allure attachments, and `target/traces/`.

---

## Configuration (`testfly.yml`)

Configure video recording in your `testfly.yml`:

```yaml
recording:
  enabled: true                    # Enable or disable video recording (default: false)
  mode: retain-on-failure          # 'retain-on-failure' (default) | 'on' | 'off'
  fps: 5                           # Frames per second (1-10 recommended, default: 2)
  maxDurationSeconds: 60           # Maximum recording length hard cap (default: 60)
  cdp: true                        # Use native CDP screencast on Chrome/Edge (default: true)
```

### Configuration Options

| Key | Type | Default | Description |
|---|---|---|---|
| `enabled` | `boolean` | `false` | Master toggle to activate recording. |
| `mode` | `string` | `retain-on-failure` | `retain-on-failure`: Discard on pass, save on fail.<br>`on` / `always`: Save recording for all tests.<br>`off`: Disable recording. |
| `fps` | `int` | `2` | Frame capture rate per second (higher values produce smoother animations). |
| `maxDurationSeconds` | `int` | `60` | Safety timeout to avoid unbounded memory buffers on long tests. |
| `cdp` | `boolean` | `true` | When true, uses CDP `Page.startScreencast` on Chromium; falls back to periodic screenshot sampling on Firefox/Safari. |

---

## Execution Behavior

### 1. Test Start
- When a Web UI test method begins, `TestExecutionListener` (or `TestFlyExtension` in JUnit 5) initializes a thread-isolated `RecordingSession`.
- If running on Chrome/Edge with `cdp: true`, it binds to the browser's DevTools session and begins streaming JPEG frames with non-blocking acknowledgments.

### 2. Test Passes (in `retain-on-failure` mode)
- All buffered frames are immediately cleared from memory.
- No file is written to disk.

### 3. Test Fails
- The recording session captures the final state.
- Frames are compiled into `target/recordings/{package_ClassName_methodName}.gif`.
- The relative recording path is registered in `ExecutionMetrics`.
- The video is automatically attached to:
  1. `target/testfly-report.html` (viewable in the test details drawer and Flakiness Radar).
  2. `target/traces/{ClassName}/{methodName}-trace.html` (viewable in the self-contained trace player).
  3. `target/allure-results/` (as an `Execution Video` attachment).

---

## Viewing Recorded Videos

### In TestFly HTML Report
Open `target/testfly-report.html` in any browser:
1. Click on the failed test in the **Suite Explorer** or **Flakiness Radar**.
2. Expand the test details to view the **🎥 Execution Video Recording** card.
3. Click on the recording image to open the full-screen lightbox player.

### In Allure Report
If Allure is enabled (`reporting.allureEnabled: true`):
```bash
allure serve target/allure-results
```
The recording will appear under the **Attachments** section of the failed test.
