---
name: testfly-mobile
description: Work on TestFly's incubating native mobile module (io.testfly.mobile) that drives Android and iOS through a user-installed mobilecli server - JSON-RPC transport, locators, assertions, engine bridges, fake-server tests and the emulator smoke workflow. Use for any change in io.testfly.mobile or the mobile branches of the TestNG, JUnit 5 and Cucumber bridges.
---

# TestFly native mobile

Design and status: `docs/adr/0002-native-mobile-via-mobilecli.md`. Everything is incubating: not annotated `@TestFlyApi`. Promotion follows ADR D9 and the phase 4 checklist.

## Map

- **Public API** (`io.testfly.mobile`): `Mobile.connect()`, `MobileDevice`, `MobileScreen`, `MobileLocator`, `MobileAssertions`/`MobileLocatorAssert`, `MobileRole`, value records, the `MobileException` subtypes, `MobileSupport`, `BaseMobileTest`, `@MobileTest` (`@Inherited`), `junit5.BaseJUnit5MobileTest`; page objects via `@MobileFind(ios, android, value)` fields, `MobilePages` and `MobileSupport.page(Class)`.
- **SPI:** `spi.MobileDriver`; every call receives its timeout.
- **Internal:** `JsonRpcClient` (HTTP `POST /rpc`), `MobileCliDriver` (mobilecli 1.0.20 methods), `LocatorEngine` (ported from Mobilewright; attribution in NOTICE), `SelectorParser` (selector strings of `@MobileFind`/`MobileScreen.locate`), `Poller`, `DeviceSelector`, `DeviceReservations`, `MobileSessions` (thread-confined), `MobileTestDetector`.
- **Config:** `TestFlyConfig.Mobile`; the defaults live in its field initialisers.
- **Bridges:** `TestExecutionListener`, `TestFlyExtension`, `CucumberHooks` (`@mobile`): skip the browser, open and close the device, take the failure screenshot.

## Rules (ADR D6/D7)

- Every request is bounded by `min(rpcMs, time left in the action budget)`.
- Read-only calls may be retried once on I/O failure; state-changing calls (tap, text, launch) never are.
- A connection closed without an answer is a `MobileRpcException`. A slow connect is a `MobileTimeoutException`; a refused one is `MobileServerUnavailableException`.
- Strict mode: several matches fail unless `first`/`last`/`nth` is used. Actions wait for visible, enabled and stable.
- Only loopback servers by default; a remote server needs `allowRemote` and `https`.
- One session per device at a time in a JVM (`DeviceReservations.acquireAny`). A pool comes from `mobile.devices` or `mobile.pool`. Never share sessions across threads.
- TestFly never bundles or runs the mobilecli binary. Users install it, plus `mobilecli agent install --device <id>`.
- Names follow web `Locator` where both exist (`type` clears, `append` adds, `getText`, `getValue`).

## Tests

- Unit and integration without a device: `src/test/java/io/testfly/unit/mobile/`.
  - `FakeMobilecliServer`: JDK `HttpServer` replaying the recorded responses in `src/test/resources/mobile/mobilecli-1.0.20/`.
  - `FakeApp`: a login/home app.
  - `MobileEngineBridgeTest`: real TestNG, JUnit 5 and Cucumber runs. Its samples live in `MobileBridgeSamples` because the pom's `<excludes>` does not skip nested classes, and it sets `testfly.reports.dir` to a temp dir.
- Real emulator: `AndroidEmulatorSmokeIntegrationTest` (skipped without `-Dtestfly.mobile.smoke=true`) via `.github/workflows/mobile-emulator-smoke.yml` and `scripts/ci/android-mobile-smoke.sh`.
- The cloud container has no KVM; never claim device verification from there.

Gates: `mvn -Dtest='Mobile*Test' test`, then `scripts/agent/verify.sh api <baseline>` (full suite); `verify.sh docs` when docs change.
