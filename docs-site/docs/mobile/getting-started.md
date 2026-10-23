---
id: getting-started
title: Native Mobile (Incubating)
sidebar_position: 1
description: "Drive Android and iOS apps from Java through a user-installed mobilecli server: device(), screen().getByRole/Text/TestId, auto-waiting actions and expect() assertions on TestNG, JUnit 5 and Cucumber."
---

# Native Mobile (Incubating)

TestFly can drive native Android and iOS apps without Appium. It talks to [mobilecli](https://github.com/mobile-next/mobilecli), a server that **you install and run**; TestFly does not bundle, download or modify it. The API is inspired by [Mobilewright](https://mobilewright.dev). Design and decisions: ADR 0002 (`docs/adr/0002-native-mobile-via-mobilecli.md`).

:::caution Incubating
Everything in `io.testfly.mobile` is incubating: it is not annotated `@TestFlyApi` and may change in a minor release. It is tested against an in-process fake mobilecli server that replays responses recorded from mobilecli 1.0.20. It has **not yet been validated on real devices, emulators or simulators**.
:::

## Prerequisites

1. Install mobilecli (1.0.20 or newer) and start the server:

   ```bash
   mobilecli server start          # listens on http://localhost:12000
   mobilecli devices               # lists online devices and their ids
   ```

2. Install mobilecli's agent on the device once. UI calls need it:

   ```bash
   mobilecli agent install --device <device-id>
   mobilecli agent status --device <device-id>
   ```

   On an Android emulator the device id is the AVD name (for example `Pixel_8_API_34`), not the adb serial (`emulator-5554`).

## Configuration

Nothing mobile loads unless `testfly.yml` has a `mobile:` section.

```yaml
mobile:
  serverUrl: ${MOBILECLI_URL:-http://localhost:12000}
  platform: android            # android | ios (required)
  deviceId: ""                 # exact id; wins over deviceName
  deviceName: ""               # regex on the device name
  devices: []                  # device pool: ids handed out one per test
  pool: false                  # pool every online device matching deviceName
  app:
    bundleId: com.example.app  # Android package / iOS bundle id
    install: ""                # optional .apk/.ipa/.app path, installed when the device opens
    reset: relaunch            # none | relaunch | clearData
  timeouts:
    actionMs: 5000             # auto-wait budget of one action or assertion
    pollMs: 100                # pause between two reads of the screen
    rpcMs: 30000               # upper bound of one request to mobilecli
    reserveMs: 300000          # wait for a device another test in this JVM holds
  allowRemote: false
  headers: {}                  # e.g. Authorization: Bearer ${MOBILECLI_TOKEN}
  screenshotOnFailure: true
```

- **Device selection.** `deviceId` must match exactly one online device of the platform; otherwise the `deviceName` regex must; otherwise exactly one online device of the platform must exist. Zero or several matches fail with a list of the devices mobilecli reported.
- **Device pool (parallel runs).** List the devices in `devices: [id1, id2]`, or set `pool: true` to use every online device of the platform that matches `deviceName`. Each test gets a free device; when all are in use it waits up to `reserveMs`, then fails with `MobileTimeoutException`. Run at most as many parallel mobile workers as there are devices. `devices` cannot be combined with `deviceId` or `pool`. Offline ids in `devices` are skipped with a warning.
- **App reset.** `relaunch` terminates and launches `bundleId` before each test; `clearData` clears its data and launches it; `none` leaves it alone. Except with `none`, the app is terminated after the test.
- **Security.** mobilecli has no authentication, so only loopback addresses are accepted. A remote server needs `allowRemote: true` and an `https://` URL, for example mobilecli behind a TLS-terminating, authenticating proxy or an SSH tunnel.

## Writing tests

| Engine | How a test becomes a mobile test |
|---|---|
| TestNG | extend `io.testfly.mobile.BaseMobileTest`, or put `@MobileTest` on a class or method that already runs with TestFly's listeners (for example in a `BaseTest` subclass) |
| JUnit 5 | extend `io.testfly.mobile.junit5.BaseJUnit5MobileTest`, or put `@MobileTest` on a class or method that uses `TestFlyExtension` |
| Cucumber | tag the scenario or feature `@mobile`; step classes implement `MobileSupport` |

For a mobile test, TestFly skips the browser, opens the device before the test and closes it afterwards. The device belongs to the test's thread.

```java
import io.testfly.mobile.BaseMobileTest;
import io.testfly.mobile.MobileRole;
import org.testng.annotations.Test;

public class LoginTest extends BaseMobileTest {

    @Test
    public void signsIn() {
        screen().getByTestId("username").fill("ada");
        screen().getByLabel("Password").fill("secret");
        screen().getByRole(MobileRole.BUTTON, "Sign in").tap();

        expect(screen().getByText("Hello, ada")).toBeVisible();
        apiClient().get("/me").send().assertStatus(200);   // backend check in the same test
    }
}
```

The base classes also provide `apiClient()`, `db()`, `mailbox()`, `ctx()`, `step()`, `softAssert()` and `getTestData()`.

Cucumber steps:

```java
public class LoginSteps implements MobileSupport {

    @When("I sign in as {string}")
    public void signIn(String user) {
        screen().getByTestId("username").fill(user);
        screen().getByRole(MobileRole.BUTTON, "Sign in").tap();
    }
}
```

Outside a test runner, open a device yourself:

```java
try (MobileDevice device = Mobile.connect()) {   // reads testfly.yml
    device.screen().getByText("Settings").tap();
}
```

## Examples

Ready-to-run examples drive the built-in Settings app, so no app file is needed:

| Platform | Test | Run |
|---|---|---|
| iOS simulator | `src/test/java/io/testfly/examples/mobile/IosSettingsExampleTest.java` | `mvn test -Dtestfly.profile=mobile-ios -Dtest=IosSettingsExampleTest` |
| Android emulator | `src/test/java/io/testfly/examples/mobile/AndroidSettingsExampleTest.java` | `mvn test -Dtestfly.profile=mobile-android -Dtest=AndroidSettingsExampleTest` |

The profiles are `src/test/resources/testfly-mobile-ios.yml` and `testfly-mobile-android.yml`. Start `mobilecli server start` and boot the simulator or emulator first; the texts assume an English device. The `Mobile Emulator Smoke` workflow runs both examples on GitHub Actions.

## Locators

| Method | Matches |
|---|---|
| `getByRole(MobileRole.BUTTON)`, `getByRole(role, "name")` | native classes of the role on both platforms; `name` is the label, else the text |
| `getByText("Save")`, `getByText("sav", false)`, `getByText(Pattern)` | text, else label, else value (exact, case-insensitive substring, or regex) |
| `getByLabel(...)` | accessibility label |
| `getByTestId("login")` | iOS accessibility identifier or Android resource-id (`login` also matches `com.app:id/login`) |
| `getByPlaceholder(...)` | placeholder text |
| `getByType("android.widget.Switch")` | the raw native class |

Refine with `first()`, `last()`, `nth(i)`, `within(container)`, `or(other)`, `and(other)`, `filterHasText(...)`, `filterHasNotText(...)`, `filterHas(locator)`, `filterHasNot(locator)` and `withTimeout(Duration)`. Locators are evaluated in TestFly against the screen's element tree.

## Actions and assertions

- **Actions:** `tap()`, `doubleTap()`, `longPress()`, `fill(text)` (same as `type(text)`), `append(text)`, `clear()`, `swipe(SwipeDirection)`. They wait until exactly one element matches and it is visible, enabled and stable, then act on the centre of the element. Several matches fail unless `first()`, `last()` or `nth(i)` picks one (strict mode).
- **Reads:** `getText()`, `getValue()`, `node()` wait for a visible element; `count()`, `nodes()`, `isVisible()` read the current screen without waiting.
- **Assertions:** `expect(locator)` with `toBeVisible()`, `toBeHidden()`, `toBeEnabled()`, `toBeDisabled()`, `toBeSelected()`, `toBeChecked()`, `toBeFocused()`, `toHaveText(...)`, `toContainText(...)`, `toHaveValue(...)`, `toHaveCount(n)`, and `not()`. They retry until `timeouts.actionMs` runs out.
- **Device:** `device()` offers `launchApp`, `terminateApp`, `clearAppData`, `installApp`, `openUrl` (deep links), `pressButton("BACK")`, `screenSize()` and `screenshot()`; `screen().swipe(...)` swipes across the screen.

Actions are logged as report steps ("Tap getByText(…)"); typed text is not logged. When a test fails, a device screenshot is attached (`screenshotOnFailure`).

## Errors

| Exception | When |
|---|---|
| `MobileServerUnavailableException` | mobilecli is not reachable: start `mobilecli server start` |
| `MobileVersionException` | mobilecli is older than 1.0.20 |
| `DeviceSelectionException` | the selectors match zero or several online devices, or no pool device is online |
| `DeviceNotFoundException` | mobilecli does not know the device |
| `MobileLocatorException` | strict mode: several elements matched |
| `MobileTimeoutException` | an action or request ran out of time; the message lists the last elements seen |
| `MobileAssertionError` | an `expect(...)` did not pass in time |
| `MobileRpcException` | any other mobilecli error; a failed screen read names the agent check |

## Limits

- One device per test; a JVM-wide reservation lets only one test use a device at a time. Without a pool, parallel workers share one device serially. Separate JVMs or machines sharing devices are not coordinated.
- `launchApp` does not wait for the app to reach the foreground; the first locator call waits instead.
- No WebView, screencast or WebSocket support yet.
- Custom backends can implement the `io.testfly.mobile.spi.MobileDriver` interface and open a device with `Mobile.connect(driver, settings)`.
