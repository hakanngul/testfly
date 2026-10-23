---
id: troubleshooting
title: Mobile Troubleshooting
sidebar_position: 5
description: "Exceptions of TestFly's native mobile module, common problems with mobilecli, devices and locators, and the current limits."
---

# Mobile Troubleshooting

## Exceptions

| Exception | When |
|---|---|
| `MobileServerUnavailableException` | mobilecli is not reachable and could not be started: mobilecli is missing (`mobilecliPath`), exited, or did not answer within `serverStartMs`; the message names the log |
| `MobileVersionException` | mobilecli is older than 1.0.20 |
| `DeviceSelectionException` | the selectors match zero or several online devices, or no pool device is online |
| `DeviceNotFoundException` | mobilecli does not know the device |
| `MobileLocatorException` | strict mode: several elements matched; an invalid selector string; a page field without a selector for the running platform |
| `MobileTimeoutException` | an action, `@WaitFor` or request ran out of time; the message lists the last elements seen |
| `MobileAssertionError` | an `expect(...)` did not pass in time |
| `MobileRpcException` | any other mobilecli error; a failed screen read names the agent check |

## Common problems

| Symptom | Cause and fix |
|---|---|
| `mobilecli not found at 'mobilecli'` | mobilecli is not on the PATH of the test JVM (IDEs often have a shorter PATH). Set `mobile.mobilecliPath` to the full path (`which mobilecli`). |
| `did not answer within 20000 ms` | the auto-started server is slow or failing; read `target/mobilecli-autostart.log`, or raise `timeouts.serverStartMs`. |
| `Native mobile needs a 'mobile:' section` | the active profile has no `mobile:` block; pass `-Dtestfly.profile=<name>` or add the block. |
| The first `tap` or `@WaitFor` times out on iOS ("reading the view tree timed out") | DeviceKit is still starting on a fresh simulator. Read the screen once with a long timeout (see [CI](ci#steps-of-a-job)), or raise `timeouts.rpcMs`. |
| Every screen read fails, the message names the agent | the mobilecli agent is missing: `mobilecli agent install --device <id>`. |
| `DeviceSelectionException` on an Android emulator | the id is the AVD name (`Pixel_8_API_34`), not `emulator-5554`. |
| `Strict mode: … matched 2 elements` | the query is ambiguous, e.g. a Settings row is a Button that also holds a StaticText with the same label. Use a role (`getByRole(BUTTON, "General")`), a test id, or `first()`. |
| A page object field is `null` | the field has an initial value (`= null`) that overwrote the locator; remove it. |
| `LoginPage.x: no android selector` | the `@MobileFind` has no `android` (or `value`) selector; add the Android id. |
| A Cucumber step times out creating a page | the page's screen is already gone; keep the page instance from an earlier step. |
| Texts do not match | the device language differs from the texts; prefer test ids. |

## Limits

- One device per test; a JVM-wide reservation lets only one test use a device at a time. Without a pool, parallel workers share one device serially. Separate JVMs or machines sharing devices are not coordinated.
- `@PreCondition` session caching is browser-only; for mobile setup use page methods, `@BeforeMethod`/`@BeforeEach`, a Cucumber `Background`, and `@DependsOnApi` for backend health.
- `launchApp` does not wait for the app to reach the foreground; the first locator call waits instead.
- No orientation, geolocation, screen recording, WebView or WebSocket support yet.
- Custom backends can implement the `io.testfly.mobile.spi.MobileDriver` interface and open a device with `Mobile.connect(driver, settings)`.
