# TestFly – Video Recording on Test Failure

TestFly provides native, zero-dependency **Web UI video recording** modeled after Playwright's `video: 'retain-on-failure'` capability.

When enabled, TestFly records the live browser interaction during test execution. If the test passes, the buffered frames are discarded immediately from memory without writing to disk. If the test fails, TestFly compiles the captured frames into a standard **H.264 MP4 video** (or animated GIF) and embeds it directly into the interactive HTML report, Allure results, and test traces.

---

## Key Benefits

- **Zero Native Dependencies (Pure-Java MP4 Encoding)**: Powered by an integrated JCodec H.264 encoder. Does **not** require `ffmpeg`, `X11`, or external OS binaries. Runs seamlessly out-of-the-box in headless Alpine / Ubuntu Docker containers, Linux CI, GitHub Actions, macOS, and Windows.
- **Chrome DevTools Protocol (CDP v152) Screencast**: On Chromium browsers (Chrome and Edge), frames are captured asynchronously via CDP `Page.startScreencast` without blocking or slowing down WebDriver interactions.
- **Smart Retention (`retain-on-failure`)**: Only failed tests retain their recording files. Successful tests discard buffered frames instantly, saving runner memory and CI disk storage.
- **Interactive HTML5 Video Player**: Embedded directly into the standalone `target/testfly-report.html` as a Base64 data URI (`data:video/mp4;base64,...`). Features play/pause, time scrubbing, looping, and a full-screen lightbox modal.
- **Native Allure Integration**: Automatically attached as `video/mp4` MIME type, rendering in Allure's native video player with no extra configuration.
- **Headless Viewport Optimization**: When `--start-maximized` is configured, TestFly automatically configures `--window-size=1920,1080` in headless mode so recordings capture full desktop layouts rather than Chromium's default 800x600.
- **Universal Multi-Framework Support**: Works seamlessly across **TestNG** (`BaseTest`), **JUnit 5** (`BaseJUnit5Test`), and **Cucumber 7 BDD** (`@TestFlySession`).

---

## Configuration (`testfly.yml`)

Configure video recording in your `testfly.yml`:

```yaml
recording:
  enabled: true                    # Enable or disable video recording (default: false)
  mode: retain-on-failure          # 'retain-on-failure' (default) | 'on' | 'off'
  format: mp4                      # 'mp4' (default, H.264 video) | 'gif'
  fps: 5                           # Frames per second (1-10 recommended, default: 2)
  maxDurationSeconds: 60           # Maximum recording length hard cap (default: 60)
  cdp: true                        # Use native CDP screencast on Chrome/Edge (default: true)
```

### Configuration Options

| Key | Type | Default | Description |
|---|---|---|---|
| `enabled` | `boolean` | `false` | Master toggle to activate recording. |
| `mode` | `string` | `retain-on-failure` | `retain-on-failure`: Discard frames on pass, compile video on fail.<br/>`on` / `always`: Save recording for all tests.<br/>`off`: Disable recording. |
| `format` | `string` | `mp4` | Video output format: `mp4` (standard H.264 video, default) or `gif` (animated GIF fallback). |
| `fps` | `int` | `2` | Frame capture rate per second (higher values produce smoother videos, recommended 2–5). |
| `maxDurationSeconds` | `int` | `60` | Safety timeout to avoid unbounded memory buffers on long-running tests. |
| `cdp` | `boolean` | `true` | When true, uses CDP `Page.startScreencast` on Chromium; falls back to periodic screenshot sampling on Firefox/Safari. |

---

## Execution Behavior

```
Test Begins  ──►  RecordingSession starts
                        │
                  Browser Actions
                        │
         ┌──────────────┴──────────────┐
         ▼                             ▼
    Test Passes                   Test Fails
         │                             │
Buffered frames discarded       Frames compiled to MP4
(0 bytes disk usage)            (target/recordings/*.mp4)
                                       │
                               Attached to:
                               • target/testfly-report.html (<video> tag)
                               • target/allure-results/ (video/mp4)
                               • target/traces/{TestName}-trace.html
```

### 1. Test Start
- When a Web UI test method begins, TestFly initializes a thread-isolated `RecordingSession`.
- If running on Chrome/Edge with `cdp: true`, it binds to the browser's DevTools session and begins streaming JPEG frames with non-blocking acknowledgments.

### 2. Test Passes (`retain-on-failure` mode)
- All buffered in-memory frames are immediately cleared.
- No video file is written to disk, preserving runner disk space and CI performance.

### 3. Test Fails
- The recording session captures the final state and stops frame streaming.
- Frames are encoded into `target/recordings/{package_ClassName_methodName}.mp4` using JCodec H.264.
- The video is automatically attached to:
  1. `target/testfly-report.html` (embedded as Base64 HTML5 video player in the test details drawer, Flakiness Radar, and Fullscreen Lightbox).
  2. `target/allure-results/` (as an `Execution Video` attachment with MIME type `video/mp4`).
  3. `target/traces/{ClassName}/{methodName}-trace.html` (trace player).

---

## Example Test

Here is an example demonstrating `retain-on-failure` behavior in TestNG:

```java
package io.testfly.examples.testng;

import io.testfly.core.BaseTest;
import org.testng.Assert;
import org.testng.annotations.Test;

public class WebUiRecordingExampleTest extends BaseTest {

    @Test(description = "Passing test: Video recording is discarded automatically")
    public void successfulLoginTest() {
        open("https://www.saucedemo.com/");
        $("#user-name").val("standard_user");
        $("#password").val("secret_sauce");
        $("#login-button").click();
        
        Assert.assertTrue(getDriver().getCurrentUrl().contains("inventory.html"),
                "User should be navigated to inventory page");
        // No video file is created on disk!
    }

    @Test(description = "Failing test: Video recording is compiled and embedded in reports")
    public void failingCheckoutTest() {
        open("https://www.saucedemo.com/");
        $("#user-name").val("standard_user");
        $("#password").val("secret_sauce");
        $("#login-button").click();

        // Deliberate failure:
        Assert.assertEquals(getDriver().getTitle(), "Expected Mismatched Title",
                "Deliberate failure to trigger MP4 video recording");
        // An MP4 video is compiled and attached to testfly-report.html and Allure!
    }
}
```

---

## Headless Browser Viewport Optimization

In CI/CD environments, tests typically run in headless mode (`headless: true`). By default, Chromium uses an `800x600` viewport when running headless, ignoring the traditional `--start-maximized` flag.

TestFly automatically detects headless mode and configures `--window-size=1920,1080` whenever `--start-maximized` is present in your `testfly.yml`:

```yaml
browser:
  name: chrome
  headless: true
  arguments:
    - --start-maximized
    - --disable-notifications
```

This guarantees:
- Video recordings capture the **full 1080p desktop layout** instead of a collapsed mobile/tablet layout.
- Failure screenshots match the full viewport.
- No unexpected responsive menu toggles (hamburger menus) during test runs.

---

## Viewing Recorded Videos

### In TestFly HTML Report
Open `target/testfly-report.html` in any browser:
1. Locate the failed test in the **Suite Explorer** or **Failure Radar**.
2. Expand the test details panel to find the **🎥 Execution Video Recording** section.
3. Use the integrated HTML5 video player:
   - Play, pause, and seek with the timeline scrubber.
   - Adjust volume or mute.
   - Click the video to open the **Fullscreen Lightbox Player**.

### In Allure Report
If Allure reporting is enabled:
```bash
allure serve target/allure-results
```
In the failed test's **Overview** tab, look under **Attachments** for **Execution Video (`.mp4`)**. Click it to play natively inside the Allure web interface.
