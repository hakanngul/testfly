# ADR 0002: Native mobile automation through mobilecli

- Status: Proposed
- Date: 2026-10-14
- Deciders: TestFly maintainers

## Context

TestFly has no native mobile automation. "Mobile" in `src/main` today means browser device emulation (`browser/DeviceProfiles`, `DeviceEmulator`); there is no Appium dependency or iOS/Android driver.

[Mobilewright](https://mobilewright.dev) (Apache-2.0, TypeScript) offers a Playwright-style mobile API without an Appium server. It is a thin client over [mobilecli](https://github.com/mobile-next/mobilecli), a Go daemon that drives devices and exposes JSON-RPC 2.0 over HTTP (`POST /rpc`) and WebSocket (`/ws`) on port 12000:

- Android: `adb` plus an on-device UiAutomation server. It holds the device's only UiAutomation connection, so Appium cannot share the device.
- iOS: an XCUITest runner agent (`com.mobilenext.devicekit-iosUITests.xctrunner`). Real devices need a provisioning profile; simulators need macOS and Xcode.
- UI is read as an accessibility/view tree (`device.dump.ui` → `{elements: [{type, text, label, name, value, identifier, placeholder, rect, visible, enabled, selected, checked, focused, children}]}`). Input is coordinate-based (`device.io.tap {x,y}`, `longpress`, `swipe`, `text`, `button`, `gesture`).

The protocol is language-neutral, so a Java client can use it exactly the way `@mobilewright/driver-mobilecli` does.

Forces from the current TestFly source:

- `locator/Locator` extends Selenium `By` and exposes `WebElement`/`SearchContext` in public signatures. It cannot represent a mobile element.
- `driver/DriverProvider` and `NamedDriverProvider` return `WebDriver`. They are the wrong seam for a non-WebDriver device.
- `driver/DriverManager` keeps one `ThreadLocal<WebDriver>` per thread with a global session semaphore. TestNG listeners, the JUnit 5 extension and the Cucumber hooks create and quit it. Browser creation is skipped by `@NoBrowser`, `BaseApiTest`, or Cucumber `@nobrowser`/`@api` tags.
- `wait/WaitEngine` depends entirely on WebDriver. The only non-WebDriver poller is API-specific (`ApiClient.pollUntil`/`ApiPolling`).
- `TestFlyConfig` is a SnakeYAML JavaBean with nested static section classes. Unknown keys are skipped leniently and `${VAR:-default}` is resolved reflectively, so adding a `mobile:` section is mechanical.
- Jackson and `java.net.http.HttpClient` are already compile dependencies. No new dependency is needed for HTTP JSON-RPC. The JDK also ships `java.net.http.WebSocket`.
- `steps/StepLogger.stepWithScreenshot(name, status, base64)` attaches screenshots without WebDriver. Failure screenshots (`reporting/ScreenshotManager`) are WebDriver-only.
- The build is a single jar. Optional integrations use `<optional>true</optional>` dependencies.
- `@TestFlyApi(since)` freezes a signature for the whole major version (`scripts/agent/check-public-api.sh`). It has no "experimental" level.

Licensing:

- mobilecli is FSL-1.1-ALv2. Each release becomes Apache-2.0 two years after publication. Until then, a "Competing Use" is prohibited: making mobilecli available to others in a commercial product or service that substitutes for it or offers substantially similar functionality.
- Mobilewright is Apache-2.0, so porting its logic is allowed with attribution.

## Decision

Add an incubating, framework-neutral native mobile API in package `io.testfly.mobile`. It talks to a **user-installed** mobilecli server over JSON-RPC.

1. **Separate contract, no Selenium types.** The new types are `MobileDevice` (device and app control), `MobileScreen` (locator entry point), `MobileLocator` (immutable query with actions and reads), `MobileAssertions` (retrying `expect`) and `MobileNode` (a snapshot of one view-tree node). None of them extends or exposes `By`, `WebElement` or `WebDriver`.
2. **Driver seam.** Add `io.testfly.mobile.spi.MobileDriver`, a Java analogue of Mobilewright's `MobilewrightSession`: view hierarchy, tap, long press, text, swipe, button, screenshot, app launch and terminate, `openUrl`, orientation. The first and only built-in implementation is `MobileCliDriver`. A later Appium or cloud adapter can implement the same SPI.
3. **Transport.** Use JSON-RPC 2.0 over HTTP `POST {serverUrl}/rpc` with the existing `java.net.http.HttpClient` and Jackson. Every call auto-injects `deviceId`. Errors map to typed exceptions; for example, `-32010` maps to `DeviceNotFound`. WebSocket is deferred until a streaming feature (logs, screencast) needs it.
4. **Distribution and licensing.**
   - TestFly never bundles, downloads or redistributes mobilecli. Users install it themselves; TestFly connects to `mobile.serverUrl`.
   - `server.info` is checked at connect time against a documented minimum version.
   - Docs credit mobilecli. Ported Mobilewright logic (role table, actionability rules) carries Apache-2.0 attribution in `NOTICE`.
   - The feature is not named after Mobilewright.
5. **Configuration.** Add a `mobile:` section to `TestFlyConfig`, absent by default:
   ```yaml
   mobile:
     serverUrl: ${MOBILECLI_URL:-http://localhost:12000}
     platform: android          # android | ios (required when mobile is used)
     deviceId: ""               # optional exact id
     deviceName: ""             # optional regex
     app: { bundleId: com.example.app, install: "" }
     timeouts: { actionMs: 5000, pollMs: 100 }
   ```
6. **Lifecycle.**
   - Phase 1 is explicit and thread-confined: `try (MobileDevice device = Mobile.connect()) { ... }`. A `ThreadLocal` holder in `io.testfly.mobile.internal` backs it. The phase does not touch existing listeners.
   - Phase 2 adds opt-in automatic lifecycle. Tests mark themselves with `@MobileTest`, which also implies `@NoBrowser`, or with a `MobileSupport` mix-in interface. The TestNG listener, JUnit 5 extension and Cucumber hooks connect and disconnect the device and push a failure screenshot through `StepLogger`.
7. **Auto-wait.**
   - A small internal `Poller` (`BooleanSupplier`/`Supplier<T>` with timeout and interval) is independent of WebDriver.
   - Actions wait until the target exists and is visible, enabled and stable. Stable means the bounds are unchanged across two polls.
   - The defaults are 5 s and 100 ms, matching Mobilewright. Reads wait for visibility only. Assertions retry until timeout.
   - More than one match for an action is an error (strict) unless `first()`, `last()` or `nth()` is used.
8. **Locators.**
   - Locators are evaluated client-side over the dumped tree: `getByRole`, `getByText`, `getByLabel`, `getByTestId` (identifier or resource-id), `getByPlaceholder` and `getByType`.
   - They support `filter`, `nth`, `first`, `last` and container scoping by bounds.
   - The role-to-native-class table is ported from Mobilewright.
9. **Stability marker.**
   - Incubating types are **not** annotated with `@TestFlyApi` until the API has shipped in at least one minor release and has been validated on real Android and iOS devices.
   - They are documented as incubating, so the public-API gate does not freeze them prematurely.
   - Only additive changes are made to existing `@TestFlyApi` types: the `TestFlyConfig.getMobile`/`setMobile` accessors.

Target usage:

```java
try (MobileDevice device = Mobile.connect()) {
    device.launchApp("com.example.app");
    MobileScreen screen = device.screen();
    screen.getByLabel("Email").fill("user@example.com");
    screen.getByRole(MobileRole.BUTTON).withName("Sign In").tap();
    expect(screen.getByText("Welcome back")).toBeVisible();
}
```

## Options considered

### A. Appium java-client adapter

| Dimension | Assessment |
|---|---|
| Complexity | Medium. It reuses WebDriver semantics but needs Appium server and driver setup. |
| Cost | Adds a large dependency (Appium java-client) and server install for users. |
| Fit | Could reuse `DriverProvider`, but `Locator`/`WaitEngine` are web-shaped. |
| Licensing | Apache-2.0, no restrictions. |

**Pros:** mature ecosystem and largest device-cloud support; WebDriver familiarity.
**Cons:** heavy setup, which is the problem Mobilewright exists to solve; slower; pulls mobile into the Selenium-shaped seams.

### B. Native Java client for mobilecli (chosen)

| Dimension | Assessment |
|---|---|
| Complexity | Medium. Locator engine, auto-wait and lifecycle must be built in Java. |
| Cost | No new dependencies; users install one binary. |
| Fit | A clean framework-neutral seam that matches "Selenium is an adapter". |
| Licensing | FSL for mobilecli. Acceptable while TestFly only speaks the protocol and never redistributes it. |

**Pros:** light setup; accessibility-tree model is deterministic and AI/MCP-friendly; proven design to port from (Apache-2.0); a pluggable SPI keeps an Appium path open.
**Cons:**
- The protocol is single-vendor and partly undocumented. The published OpenRPC omits methods that Mobilewright uses (`device.io.keys`, `device.apps.clear`, `device.location.*`, `device.webview.*`).
- Android cannot share a device with Appium.
- FSL restricts commercial competing services around TestFly.

### C. Drive Mobilewright from Java (Node subprocess or bridge)

| Dimension | Assessment |
|---|---|
| Complexity | High. Cross-runtime IPC, Node ≥ 22.12 required on every runner. |
| Fit | Poor. Java tests would control a TypeScript test runner. |

**Pros:** reuses Mobilewright behavior verbatim.
**Cons:** two runtimes; fragile process management; no Java-native types or IDE help; Mobilewright's API is designed around its own test runner.

## Trade-off analysis

B costs more Java code than A, but it keeps TestFly's architecture honest: mobile is a peer domain, not a Selenium extension. It also delivers the setup simplicity that motivates the work. The main risks are protocol drift and licensing.

- **Protocol drift:** the `MobileDriver` SPI isolates it, and a minimum-version check plus contract tests against a recorded fake server catch it early.
- **Licensing:** the no-redistribution rule handles it. Maintainers should still confirm the reading of FSL. A courtesy note to Mobile Next HQ is recommended.

C is rejected because it makes Node a hard dependency of a Java SDK.

## Consequences

- **Easier:**
  - Android and iOS automation from Java without an Appium server.
  - A shared device seam for future AI/MCP features, since view-tree snapshots are cheap to serialize.
- **Harder:**
  - A second locator and wait implementation to maintain beside the web one.
  - CI cannot run real devices here, so unit tests use an in-process fake JSON-RPC server (`com.sun.net.httpserver`) and real-device checks are manual or a separate job.
- **To revisit:**
  - Promote to `@TestFlyApi` after validation.
  - WebSocket transport for logs and screencast.
  - Device pooling for parallel runs (one device per thread, allocation like Mobilewright's `DeviceAllocator`).
  - WebView bridging.
  - Whether mobile should move to a separate artifact if it grows dependencies.

## Action items

1. [ ] Maintainers confirm the licensing position (no redistribution of mobilecli; NOTICE attribution for ported Mobilewright logic).
2. [ ] Spike against a real Android emulator: record `devices.list`, `device.info`, `device.dump.ui` and `device.screenshot` responses as fixtures. Pin the minimum mobilecli version.
3. [ ] Phase 1 (Android MVP):
   - `MobileCliClient` (JSON-RPC), `MobileDriver` SPI, `MobileCliDriver`
   - `Poller`, `MobileLocator` (role/text/label/testId), `tap`/`fill`/`longPress`/`swipe`
   - `expect(...).toBeVisible()`/`toHaveText()`
   - `mobile:` config section
   - Unit tests on the fake server; `scripts/agent/verify.sh code` and `api`
4. [ ] Phase 2:
   - `@MobileTest` lifecycle in the TestNG, JUnit 5 and Cucumber bridges
   - Failure screenshot through `StepLogger`
   - Step logging
   - English and Turkish docs plus sample `testfly.yml`
5. [ ] Phase 3:
   - iOS simulator and real-device validation (macOS runner)
   - Device allocation for parallel runs
   - Deep links, orientation, geolocation
6. [ ] Phase 4:
   - Promote the stable types to `@TestFlyApi`
   - Consider WebSocket, WebView and screencast
