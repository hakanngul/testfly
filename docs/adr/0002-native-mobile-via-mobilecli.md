# ADR 0002: Native mobile automation through mobilecli

- Status: Proposed
- Date: 2026-10-14
- Deciders: TestFly maintainers

## Context

TestFly has no native mobile automation. "Mobile" in `src/main` today means browser device emulation (`browser/DeviceProfiles`, `DeviceEmulator`); there is no Appium dependency or iOS/Android driver.

[Mobilewright](https://mobilewright.dev) (Apache-2.0, TypeScript) offers a Playwright-style mobile API without an Appium server. It is a thin client over [mobilecli](https://github.com/mobile-next/mobilecli), a Go daemon that drives devices and exposes JSON-RPC 2.0 over HTTP (`POST /rpc`) and WebSocket (`/ws`) on port 12000.

- **Android:** `adb` plus an on-device UiAutomation server. The server holds the device's only UiAutomation connection, so Appium cannot share the device.
- **iOS:** an XCUITest runner agent (`com.mobilenext.devicekit-iosUITests.xctrunner`). Real devices need a provisioning profile; simulators need macOS and Xcode.
- **UI model:** the UI is read as a view tree. `device.dump.ui` returns `{elements: [{type, text, label, name, value, identifier, placeholder, rect, visible, enabled, selected, checked, focused, children}]}`.
- **Input:** input is coordinate-based: `device.io.tap {x,y}`, `longpress`, `swipe`, `text`, `button`, `gesture`.

The protocol is language-neutral. A Java client can use it exactly the way `@mobilewright/driver-mobilecli` does.

### Forces from the current TestFly source

- **Domains are already separated by base class.** Each domain has its own base classes and `*Support` mix-ins:
  - Web: `test/BaseTest`, with `LocatorSupport`, `BrowserSupport` and others.
  - API: `test/BaseApiTest` and `junit5/BaseJUnit5ApiTest`, with `ApiSupport`.
  - Load: `loadtest/BaseLoadTest`.

  Shared capabilities (`StepSupport`, `SoftAssertSupport`, `ApiSupport`, `DbSupport`, `ContextSupport` and others) are mixed into each base class.
- **Engine bridges decide whether to start a browser.**
  - TestNG: `TestExecutionListener.skipBrowser` returns `isApiTest || isNoBrowserTest || isLoadTest` (`listeners/TestExecutionListener.java:409-410`).
  - JUnit 5: `TestFlyExtension.skipBrowser` checks the API base classes and `LoadTestDetector`.
  - Cucumber: `CucumberHooks.skipBrowser` checks the `@nobrowser`, `@api` and load-test tags.

  `@NoBrowser` is not `@Inherited`, so it does not reach subclasses of an annotated base class.
- **The web stack is Selenium-shaped by design.**
  - `locator/Locator` extends Selenium `By`.
  - `driver/DriverProvider` returns `WebDriver`.
  - `wait/WaitEngine` is WebDriver-only.
  - `reporting/ScreenshotManager` takes screenshots through WebDriver.
- **Building blocks already exist.**
  - Jackson and `java.net.http.HttpClient` are compile dependencies.
  - `steps/StepLogger.stepWithScreenshot(name, status, base64)` attaches a screenshot without WebDriver.
  - `TestFlyConfig` is a SnakeYAML JavaBean with nested section classes and reflective `${VAR:-default}` resolution.
- **There is no "incubating" marker.** `@TestFlyApi(since)` freezes a signature for the whole major version, and `scripts/agent/check-public-api.sh` enforces this.

### Licensing

- **mobilecli** uses FSL-1.1-ALv2. Each release also becomes available under Apache-2.0 two years after publication. Until then, a "Competing Use" is prohibited. That means making mobilecli available to others in a commercial product or service that substitutes for it or offers substantially similar functionality.
- **Mobilewright** uses Apache-2.0, so porting its logic is allowed with attribution.

## Decision

Native mobile becomes a **fourth test domain** next to web, API and load. It lives in its own package, has its own base classes, and talks to a **user-installed, unmodified** mobilecli server over JSON-RPC.

### D1. Consume mobilecli as-is; do not fork

TestFly depends on mobilecli's **protocol**, not its code. It never bundles, downloads, patches or redistributes the binary. Users install it themselves, and TestFly connects to `mobile.serverUrl`. Since 2026-10-23 TestFly may also *run* the user-installed binary: with `mobile.autoStartServer` (default on) it starts `mobilecli server start` for a loopback `serverUrl` that does not answer, once per JVM, and stops it at JVM exit; remote servers are never started.

- **Gaps:** a gap in mobilecli is handled in this order:
  1. Solve it in Java. Locators, waiting and gesture composition are TestFly code anyway.
  2. Raise an upstream issue or PR.
  3. Adapt behind the `MobileDriver` SPI.
- **Version check:** at connect time, TestFly checks `server.info` against a documented minimum version.
- **Fallback:** a fork is a contingency only, for the case where upstream is abandoned or relicensed. It becomes cheaper over time because each release turns Apache-2.0 after two years.

**Rationale:** mobilecli bundles a Go daemon, an iOS XCUITest agent with code-signing, and an Android UiAutomation server, all of which break with OS releases. Owning that stack is outside TestFly's scope as a Java SDK. Distributing a modified build would also move TestFly toward the FSL "making available" clause that this design avoids.

### D2. Domain layout

| | Web | API | Load | **Mobile (new)** |
|---|---|---|---|---|
| Package | `driver`, `locator`, `wait` | `api`, `client` | `loadtest` | **`io.testfly.mobile`** |
| TestNG base | `test.BaseTest` | `test.BaseApiTest` | `loadtest.BaseLoadTest` | **`mobile.BaseMobileTest`** |
| JUnit 5 base | `junit5.BaseJUnit5Test` | `junit5.BaseJUnit5ApiTest` | – | **`mobile.junit5.BaseJUnit5MobileTest`** |
| Capability mix-in | `LocatorSupport`, `BrowserSupport`… | `ApiSupport` | `LoadTestSupport` | **`mobile.MobileSupport`** |
| Bridge check | – | `isApiTest` | `isLoadTest` | **`isMobileTest`**: skip browser, open device |
| Cucumber tag | – | `@api` | load tags | **`@mobile`** |
| Config section | `browser:` | `api:` | `loadTest:` | **`mobile:`** |

- `BaseMobileTest` mixes in `MobileSupport` together with the shared capabilities: `StepSupport`, `SoftAssertSupport`, `TestDataSupport`, `ContextSupport`, `ApiSupport`, `DbSupport` and `EmailSupport`. One test can then drive the app and verify the backend. It mixes in no web `*Support` interface.
- **Placement.** All mobile types live under `io.testfly.mobile`, following the `loadtest` precedent. The incubating surface is then one namespace that is easy to exclude from, or later add to, the public-API gate. The JUnit 5 base lives in `io.testfly.mobile.junit5` because JUnit is an optional dependency.
- **Web stack unchanged.** `Locator`, `WaitEngine`, `DriverManager` and `DriverProvider` are not changed. Consumers who do not use mobile see no behavioural change.

### D3. Components

```
 Test code ──► BaseMobileTest / BaseJUnit5MobileTest / Cucumber @mobile
                 │  MobileSupport: device(), screen(), expect(...)
                 ▼
 ┌──────────────────────── io.testfly.mobile ────────────────────────┐
 │ MobileDevice   app/device control, screenshot, openUrl            │
 │ MobileScreen   getByRole/Text/Label/TestId/Placeholder/Type       │
 │ MobileLocator  immutable query: filter/nth/first/last/within      │
 │                actions: tap/fill/longPress/swipe/clear            │
 │ MobileAssertions  expect(locator).toBeVisible()/toHaveText()…     │
 │ MobileNode     immutable snapshot of one view-tree node           │
 │ ─────────────────────────── internal ──────────────────────────── │
 │ MobileSessions  ThreadLocal<MobileDevice> holder, open/close      │
 │ LocatorEngine   flatten tree, match, role table, strict check     │
 │ Poller          Supplier/BooleanSupplier poll with timeout        │
 │ ─────────────────────────── spi ───────────────────────────────── │
 │ MobileDriver    viewTree/tap/longPress/text/swipe/button/         │
 │                 screenshot/apps/openUrl/orientation/info          │
 │   └─ MobileCliDriver ─► JsonRpcClient (JDK HttpClient + Jackson)  │
 └──────────────────────────────────┬────────────────────────────────┘
                                    │ POST {serverUrl}/rpc  (JSON-RPC 2.0)
                                    ▼
                     mobilecli (user-installed) ─► adb/UiAutomation | XCUITest agent
```

The **`MobileDriver` SPI** is the replaceable seam. A later Appium, cloud or recorded-replay adapter can implement it. The built-in `MobileCliDriver` injects the selected `deviceId` only into device-scoped methods (`device.*`). Server and discovery calls (`server.info`, `devices.list`) are sent without it, so connecting by name or with no selector can bootstrap before an id is known.

### D4. Lifecycle and engine integration

- **Explicit use.** `try (MobileDevice d = Mobile.connect()) {...}` works without any base class, for scripts and custom runners.
- **TestNG.** `TestExecutionListener` gains `isMobileTest(result)`, which returns true if:
  - the class is assignable to `BaseMobileTest`, or
  - the class or method carries a new `@MobileTest` annotation.

  A mobile test skips the browser. The listener opens the device before the test and closes it after. On failure, it pushes a device screenshot through `StepLogger.stepWithScreenshot`.
- **JUnit 5.** `TestFlyExtension.skipBrowser` and `beforeEach`/`afterEach` get the same checks.
- **Cucumber.** `CucumberHooks.skipBrowser` treats `@mobile` like `@api`, and the hooks open and close the device.
- **Ownership.** One device session per thread, held in `MobileSessions`, never shared between threads. Cleanup runs in `finally` blocks and in a shutdown hook, like `DriverManager.quitAllTrackedDrivers`.
- **App state.** The default is to launch `mobile.app.bundleId` before each test and terminate it after. `mobile.app.reset: none | relaunch | clearData` controls this.

### D5. Configuration

The `mobile:` section is absent by default. When it is absent, nothing mobile loads.

```yaml
mobile:
  serverUrl: ${MOBILECLI_URL:-http://localhost:12000}
  platform: android            # android | ios — required when a mobile test runs
  deviceId: ""                 # exact id; wins over deviceName
  deviceName: ""               # regex
  app:
    bundleId: com.example.app
    install: ""                # optional .apk/.ipa/.app path
    reset: relaunch            # none | relaunch | clearData
  timeouts:
    actionMs: 5000             # auto-wait budget per action/assertion
    pollMs: 100
    rpcMs: 30000               # upper bound per JSON-RPC HTTP request
    reserveMs: 300000          # wait for a device another session in this JVM holds
  allowRemote: false           # non-loopback serverUrl needs this and https://
  headers: {}                  # e.g. Authorization for a TLS/auth proxy
  screenshotOnFailure: true
```

**Device selection** is deterministic and runs once at connect time, after `server.info` and `devices.list`:

1. If `deviceId` is set, it must match exactly one online device of `platform`.
2. Otherwise, if `deviceName` is set, the regex must match exactly one online device of `platform`.
3. Otherwise, exactly one online device of `platform` must exist.

Zero matches or more than one fails fast with `DeviceSelectionException`. The message lists the candidates (id, name, type) and the selector that was used. TestFly never picks a device arbitrarily.

**Transport security.** mobilecli's JSON-RPC endpoint has no documented authentication, and whoever reaches it can control the device.
- By default, `serverUrl` must resolve to a loopback address. Any other host is rejected with a clear error.
- A remote device lab requires an explicit opt-in, `mobile.allowRemote: true`, plus an `https://` URL; plain `http://` to a non-loopback host is refused.
- Remote setups are documented as "put mobilecli behind a TLS-terminating, authenticating proxy or an SSH tunnel". Optional static headers (`mobile.headers`, for example `Authorization: Bearer ${MOBILECLI_TOKEN}`) are sent on every request to support such a proxy.

The defaults live in one place: `TestFlyConfig.Mobile` field initialisers, plus `TestFlyDefaults.applyMissing`. Validation fails fast when a mobile test runs without `platform`. Unknown keys are lenient, as elsewhere.

### D6. Locators, auto-wait and errors

- **Locators** are evaluated client-side over the dumped tree. Each poll cycle fetches the tree once (`device.dump.ui`), flattens it, and evaluates every pending predicate against that snapshot.
  - Query methods: `getByRole(MobileRole)` with `withName`/`exact`, `getByText`, `getByLabel`, `getByTestId` (iOS identifier, Android resource-id), `getByPlaceholder` and `getByType` (raw class).
  - Refinements: `filter`, `nth`, `first`, `last`, and `within(locator)` (containment by bounds).
  - The role-to-native-class table is ported from Mobilewright, with attribution.
- **Actionability.** Actions wait until the target exists and is visible, enabled and stable. Stable means the bounds are unchanged across two polls. Then the action taps the centre of the bounds.
  - Reads wait for visibility only.
  - `expect` retries until `actionMs` runs out.
  - More than one match for an action is an error (strict mode) unless `first`, `last` or `nth` is used.
- **Errors.** One `MobileException` hierarchy:
  - `MobileServerUnavailableException`: connection refused. The message says to start `mobilecli server start`.
  - `MobileVersionException`: below the minimum version.
  - `DeviceNotFoundException`: JSON-RPC error `-32010` as documented. mobilecli 1.0.20 actually returns `-32000` with `data: "error finding device: device not found: …"`, so the mapping also matches that payload.
  - `DeviceSelectionException`: the selectors match zero or several devices.
  - `MobileTimeoutException`: `-32050`, or the action budget is exhausted. The message includes the last matched nodes.
  - `MobileLocatorException`: no match or strict violation. The message includes the query and the candidate nodes.
  - `MobileRpcException`: other JSON-RPC errors.
- **Deadlines.** Every JSON-RPC HTTP request uses `HttpRequest.timeout(min(rpcMs, remaining action budget))`. A stalled mobilecli call therefore cannot hold an action or assertion past `actionMs`; the `Poller` regains control and fails with `MobileTimeoutException`. A timed-out state-changing call may still complete on the device, and the error message says so.
- **Retries.** Read-only calls (`dump.ui`, `info`, `devices.list`) may be retried once on I/O failure. State-changing calls (tap, text, launch) are **never** retried by the transport, because a duplicate tap is worse than a clear failure.
- **Closed connections.** mobilecli 1.0.20 closes the connection without a JSON-RPC answer when its device daemon times out (60 s). The client reports this as `MobileRpcException`, not a parse error or a hang.
- **Connect timeouts.** A refused connection means no server (`MobileServerUnavailableException`); a connection that is only slow to open is a `MobileTimeoutException`, so polling keeps going until its budget runs out.

### D7. Parallelism and scale

- **Exclusive device reservation.** The Java `ThreadLocal` only isolates handles, not the physical device, so TestFly also reserves the device itself.
  - A JVM-wide registry keyed by `deviceId` grants each device to one session at a time.
  - Any other worker that selects the same device blocks until it is released, with a timeout and a clear error, so access is serialised, not interleaved.
  - Before Phase 3, a suite with more parallel workers than devices therefore runs mobile tests serially. A warning names the setting to change.
  - Reservation is per JVM. Separate JVMs or machines sharing one device are unsupported and documented as such.
- **Pooling.** The first release uses one configured device. Phase 3 adds `mobile.devices` (a list or a selector) and an allocator that hands each worker a free device. Workers block with a timeout when all devices are busy, as Mobilewright's `DeviceAllocator` does.
- **Cost per step.** A `dump.ui` call takes roughly 100–500 ms, depending on platform and tree size. Polling is therefore bounded by `pollMs` and reuses one tree per cycle. The cost is on the device side; JVM-side cost is negligible.

### D8. Licensing and attribution

- TestFly never ships mobilecli (D1).
- Ported Mobilewright logic carries Apache-2.0 attribution in `NOTICE`.
- Docs credit mobilecli and say that the API is inspired by Mobilewright.
- The feature is not named after Mobilewright.
- A courtesy note to Mobile Next HQ is recommended.

### D9. Stability

- New types are **not** annotated with `@TestFlyApi` until they have shipped in at least one minor release and been validated on real Android and iOS devices. Docs mark them as incubating.
- Changes to existing `@TestFlyApi` types are additive only:
  - `TestFlyConfig.getMobile`/`setMobile`
  - the new `@MobileTest` annotation, which stays incubating as well
- The bridge changes are internal and behaviour-neutral for non-mobile tests.

### Target usage

```java
public class LoginMobileTest extends BaseMobileTest {

    @Test
    public void userCanSignIn() {
        screen().getByLabel("Email").fill("user@example.com");
        screen().getByLabel("Password").fill(getTestData("password", String.class));
        screen().getByRole(MobileRole.BUTTON).withName("Sign In").tap();

        expect(screen().getByText("Welcome back")).toBeVisible();
        apiGet("/me").send().assertStatus(200);   // same test, backend check via ApiSupport
    }
}
```

## Options considered

### A. Appium java-client adapter

| Dimension | Assessment |
|---|---|
| Complexity | Medium. It reuses WebDriver semantics but needs Appium server and driver setup. |
| Cost | A large new dependency, plus a server install for users. |
| Fit | Could reuse `DriverProvider`, but `Locator` and `WaitEngine` are web-shaped. |
| Licensing | Apache-2.0, no restrictions. |

**Pros:** a mature ecosystem with the widest device-cloud support; familiar WebDriver model.
**Cons:** heavy setup, which is the problem Mobilewright exists to solve; slower; pulls mobile into the Selenium-shaped seams.

### B. Native Java client for unmodified mobilecli (chosen)

| Dimension | Assessment |
|---|---|
| Complexity | Medium. The locator engine, auto-wait and lifecycle are built in Java. |
| Cost | No new dependencies; users install one binary. |
| Fit | A peer domain, the same pattern as API and load testing. |
| Licensing | Acceptable while TestFly only speaks the protocol. |

**Pros:**
- Light setup.
- The view-tree model is deterministic and AI/MCP-friendly.
- There is a proven design to port from.
- The SPI keeps an Appium path open.

**Cons:**
- The protocol comes from a single vendor and is partly undocumented. The published OpenRPC spec omits `device.io.keys`, `device.apps.clear`, `device.location.*` and `device.webview.*`, all of which Mobilewright uses.
- On Android, mobilecli cannot share a device with Appium.

### B′. Fork mobilecli and ship it with TestFly (rejected)

**Pros:** full control of the device layer and no external install step.
**Cons:**
- TestFly would own Go, XCUITest signing and Android server maintenance across OS releases.
- It would diverge from upstream fixes.
- Redistribution moves toward the FSL competing-use clause.

### C. Drive Mobilewright from Java through a Node subprocess or bridge (rejected)

**Pros:** reuses Mobilewright's behaviour verbatim.
**Cons:**
- Node ≥ 22.12 becomes a hard requirement on every runner.
- Cross-runtime process management.
- No Java-native types.
- Mobilewright's API is shaped around its own test runner.

## Trade-off analysis

B costs more Java code than A. In return, mobile becomes a peer domain built the same way as API and load testing, rather than a Selenium extension, and users get the simple setup that motivates the work. B over B′ trades control for a much smaller maintenance surface and a cleaner licensing position. That trade can be revisited if upstream stalls.

The main risks:

| Risk | Mitigation |
|---|---|
| Protocol drift or undocumented methods | `MobileDriver` SPI isolation; minimum-version check; contract tests against fixtures recorded from a real device |
| Licensing interpretation | No redistribution; confirmed by the maintainer (2026-10-22) |
| Slow or flaky device I/O | One tree per poll cycle; strict actionability; no transport retries for actions; device screenshot on failure |
| No devices in CI | Unit and contract tests on an in-process fake JSON-RPC server (`com.sun.net.httpserver`); real-device runs as a separate, optional job |

## Consequences

- **Easier**
  - Android and iOS automation from Java with no Appium server.
  - One test can combine the app and backend checks, through `ApiSupport` and `DbSupport`.
  - A cheap, serialisable view tree for future AI/MCP features.
- **Harder**
  - A second locator and wait implementation to maintain beside the web one.
  - Three engine bridges gain a mobile branch.
  - Real-device verification needs hardware or emulators that CI does not have today.
- **To revisit**
  - Promotion to `@TestFlyApi`.
  - WebSocket transport for logs and screencast.
  - WebView bridging (`device.webview.*`).
  - Device pools across machines.
  - A separate Maven artifact, if mobile ever needs its own dependencies.

## Action items

**Phase 0: prerequisites (maintainer)**
1. [x] Confirm the licensing position: no redistribution, plus NOTICE attribution. Confirmed by the maintainer on 2026-10-22.
2. [x] Already observed against mobilecli 1.0.20 without a device: `server.info` returns `{name, version}`; `devices.list` returns `{devices: [...]}` (an object, not the bare array the OpenRPC spec shows); an unknown device returns `-32000` rather than `-32010`; an unknown method returns `-32601`. Code must follow observed behaviour, not the published spec.
3. [~] On a real Android emulator, record `server.info`, `devices.list`, `device.info`, `device.dump.ui`, `device.screenshot` and the error responses as JSON fixtures. Pin the minimum mobilecli version.
   - Done (2026-10-22, mobilecli 1.0.20, Android 14 emulator without hardware acceleration): `server.info`, `devices.list`, `device.info` and the error responses are in `src/test/resources/mobile/mobilecli-1.0.20/`. Minimum version: 1.0.20.
   - Observed: the Android emulator id is the AVD name, not the adb serial. A slow device can make the server close the connection after 60 s with **no JSON-RPC response**, so the client must map an empty reply to a transport error.
   - Open: successful `device.dump.ui` and `device.screenshot`. The unaccelerated emulator was too slow; record them on an accelerated emulator or a real device.

**Phase 1: foundation (no device needed; verified with the fake server)**
4. [x] `JsonRpcClient`, `MobileDriver` SPI, `MobileCliDriver`, and the `MobileException` hierarchy; loopback/`allowRemote` validation, deterministic device selection, deadline-bounded requests, and the JVM-wide device reservation.
5. [x] `TestFlyConfig.Mobile`, with defaults and validation.
6. [x] `MobileNode`, `LocatorEngine` (role table), `Poller`, `MobileLocator`, `MobileScreen`, `MobileDevice` and `MobileAssertions`.
7. [x] `Mobile.connect()` for explicit use.
8. [x] Run `scripts/agent/verify.sh code` and `api`.

   Phase 1 notes (2026-10-22):
   - Verified against an in-process fake server (`src/test/java/io/testfly/unit/mobile`) that serves the recorded 1.0.20 responses and a small fake app; no device was used.
   - Added `mobile.timeouts.reserveMs` (D5, D7): how long a session waits for a device that another session in the same JVM holds.
   - Not in Phase 1: the mobilecli **agent**. Mobilewright runs `mobilecli agent status/install --device <id>` before connecting; without the on-device agent, UI calls can fail (likely the cause of the failed `device.dump.ui` recording). Decided in Phase 2 (see below).
   - `launchApp` does not yet wait for the app to reach the foreground (Mobilewright polls `device.apps.foreground`); the first locator call auto-waits instead.

**Phase 2: domain integration**
9. [x] `MobileSupport`, `BaseMobileTest` and `@MobileTest`, plus the `isMobileTest` branch in `TestExecutionListener`.
10. [x] `BaseJUnit5MobileTest` and the `TestFlyExtension` branch; the Cucumber `@mobile` tag in `CucumberHooks`.
11. [x] Failure screenshot and step logging through `StepLogger`.
12. [x] English and Turkish docs (`docs-site/`), a sample `testfly.yml`, and NOTICE.
13. [x] Run `scripts/agent/verify.sh code`, `api` and `docs`.

   Phase 2 notes (2026-10-22):
   - Engine bridges verified in-process with real TestNG, JUnit 5 and Cucumber runs against the fake server (`MobileEngineBridgeTest`): the device opens before and closes after each test, no browser starts, and a failing test gets a device screenshot (`target/reports/screenshots/<test>_mobile_<ts>.png`, also logged as a step).
   - `@MobileTest` is `@Inherited` (unlike `@NoBrowser`), so the base classes carry it and detection never loads a JUnit type.
   - Mobile actions are logged as steps like web actions ("Tap getByText(...)"); typed text is not logged.
   - Agent decision: TestFly does not run `mobilecli agent status/install` itself, because the server may be remote and the CLI may not be on the test machine. The docs list the agent as a prerequisite, and a failed UI dump names the `mobilecli agent status --device <id>` check in its error.

**Phase 3: real devices and scale**
14. [x] An Android emulator smoke suite, as an optional CI job or a documented local run.
15. [~] iOS simulator and real-device validation on a macOS runner.
   - Done (2026-10-23): `IosSettingsExampleTest` passes on an iOS simulator in the `ios-simulator-smoke` job, after a DeviceKit warm-up before the first app launch. Open: real iOS and Android devices.
16. [~] `mobile.devices` pool and allocator for parallel runs; deep links, orientation and geolocation.
   - Done (2026-10-22): device pool. `mobile.devices: [ids]` or `mobile.pool: true` (every online match of `deviceName`). `DeviceReservations.acquireAny` hands each session the first free device and wakes waiters on release (`reserveMs`). Verified with the fake server: two parallel TestNG workers get different devices; 40 sessions on 3 devices never share one. Deep links work through `MobileDevice.openUrl`.
   - Done (2026-10-23): orientation and geolocation (`MobileDevice.setOrientation/orientation`, `setLocation/clearLocation`, undone at close) and screen recording (`startRecording/stopRecording`, `mobile.recordVideo: off|failures|always`), as optional `MobileDriver` default methods.
   - Open: pools across JVMs or machines.

**Phase 4: stabilise**
17. [ ] Promote validated types to `@TestFlyApi`.
18. [ ] Evaluate WebSocket, WebView and screencast support.

   Phase 4 preparation (2026-10-22). Nothing is promoted yet: D9 requires one shipped minor release and validation on real Android **and** iOS devices, and neither has happened.

   **Promotion candidates** (all in `io.testfly.mobile` unless noted):
   - YAML locators (added 2026-10-23): `MobileLocators` (load, get, keys).
   - Preconditions (added 2026-10-23): `MobileConditions`, `MobileDevice.keepsAppState()`; `@PreCondition` routes to them on mobile tests.
   - Page objects (added 2026-10-23): `MobilePage` (fluent base class: fields set, `@WaitFor` awaited, `onLoad()`), `WaitFor`, `MobileFind` (iOS/Android/shared selector per field), `MobilePages`, `MobileSupport.page(Class)`, `MobileScreen.locate(String)`.
   - Entry and session: `Mobile`, `MobileDevice`, `MobileScreen`, `MobileLocator`, `MobileSupport`, `MobileTest`, `BaseMobileTest`, `junit5.BaseJUnit5MobileTest`.
   - Assertions: `MobileAssertions`, `MobileLocatorAssert`, `MobileAssertionError`.
   - Values: `MobileRole`, `MobilePlatform`, `SwipeDirection`, `MobileNode`, `MobileBounds`, `MobileDeviceInfo`, `MobileScreenSize`.
   - Errors: `MobileException` and its subclasses.
   - Kept incubating longer: `spi.MobileDriver`, because a second backend has not shaped it yet; `internal.*` is never public API.

   **Checklist before promotion:**
   - [x] Web naming parity. `MobileLocator.type` now clears like web `Locator.type`; `append` adds text; reads are `getText()` and `getValue()` (changed while incubating).
   - [ ] Records. `MobileNode`, `MobileBounds`, `MobileDeviceInfo` and `MobileScreenSize` are records, and adding a component later breaks their canonical constructor. Either accept that, or turn the ones likely to grow (`MobileNode`, `MobileDeviceInfo`) into final classes before promotion.
   - [ ] `MobileSupport.expect(MobileLocator)` next to web `AssertionSupport` in a class that mixes both: confirm overload resolution in a consumer compile test (`scripts/agent/verify.sh consumer`).
   - [ ] Real-device evidence: Android (the smoke workflow) and iOS (phase 3).
   - [ ] One minor release shipped with the module marked incubating.
   - [ ] Annotate with `@TestFlyApi(since = "<that minor>")`, then run `scripts/agent/verify.sh api` and `consumer`.

   **Evaluation (item 18):**
   - **WebView:** feasible. mobilecli exposes `device.webview.list/evaluate/goto/goBack/goForward/reload/url/title/waitForLoadState`; Mobilewright drives web content through `evaluate` plus an injected DOM query script. A Java port needs that script, which is Mobilewright code (Apache-2.0, attribution). Size: large. Recommendation: a separate ADR when a consumer needs hybrid apps.
   - **Screen recording:** cheap. `device.screenrecord {output, timeLimit}` and `device.screenrecord.stop` already exist; wire them to `recording.mode: retain-on-failure` like web video. Note that `output` is a path on the mobilecli machine, so a remote server needs a way to return the file. Recommendation: do it next, for local servers first.
   - **Live screencast:** `mobilecli screencapture -f mjpeg` streams MJPEG. It is useful for a live view, but tests do not need it. Recommendation: not now.
   - **WebSocket transport (`/ws`):** Mobilewright uses one persistent socket instead of an HTTP request per call. It would save connection setup per poll, but the per-request deadline and no-retry rules (D6) must hold on a multiplexed socket. Recommendation: measure `dump.ui` latency over HTTP on a real device first; switch only if connection setup is a visible share.
