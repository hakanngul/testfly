---
id: getting-started
title: Native Mobile (Incubating)
sidebar_position: 1
description: "Drive Android and iOS apps from Java through a user-installed mobilecli server: page objects, auto-waiting locators and expect() assertions on TestNG, JUnit 5 and Cucumber."
---

# Native Mobile (Incubating)

TestFly can drive native Android and iOS apps without Appium. It talks to [mobilecli](https://github.com/mobile-next/mobilecli), a server that **you install**; TestFly does not bundle, download or modify it, but it can start your installed binary when the server is not running. The API is inspired by [Mobilewright](https://mobilewright.dev). Design and decisions: ADR 0002 (`docs/adr/0002-native-mobile-via-mobilecli.md`).

:::caution Incubating
Everything in `io.testfly.mobile` is incubating: it is not annotated `@TestFlyApi` and may change in a minor release. The `Mobile Emulator Smoke` workflow runs the examples on Android emulators (API 30 and 34) and an iOS simulator on every relevant change; real phones are not covered by CI yet.
:::

| Page | What it covers |
|---|---|
| This page | setup, the first test on TestNG, JUnit 5 and Cucumber, ready-made examples |
| [Locators and page objects](locators-and-page-objects) | `getBy…` locators, selector strings, `MobilePage`/`@MobileFind`/`@WaitFor`, YAML locator files, preconditions (`@PreCondition` with `MobileConditions`), actions and assertions |
| [Configuration](configuration) | the `mobile:` block, device selection, device pool, app reset, server auto-start, profiles, security |
| [CI](ci) | running on GitHub Actions with an Android emulator or an iOS simulator |
| [Troubleshooting](troubleshooting) | exceptions, common problems, limits |

## Prerequisites

1. Install mobilecli 1.0.20 or newer, for example `npm install -g mobilecli`. Android needs the Android SDK (`adb`); iOS simulators need Xcode on macOS.
2. Boot an emulator or simulator, or connect a phone, and find its id:

   ```bash
   mobilecli devices
   ```

   On an Android emulator the id is the AVD name (for example `Pixel_8_API_34`), not the adb serial (`emulator-5554`). On iOS it is the simulator UDID.
3. **iOS:** install mobilecli's agent on the device once; screen reads and taps need it. Android needs no agent (`agent status` just reports that):

   ```bash
   mobilecli agent install --device <device-id>
   mobilecli agent status --device <device-id>
   ```

You do not need to start the server yourself: when `http://localhost:12000` does not answer, TestFly runs `mobilecli server start` and stops it when the JVM exits (see [server auto-start](configuration#server-auto-start)). Starting it yourself (`mobilecli server start`) also works.

## Minimal configuration

Nothing mobile loads unless the active `testfly.yml` (or profile, `-Dtestfly.profile=<name>` → `testfly-<name>.yml`) has a `mobile:` section:

```yaml
mobile:
  platform: ios                     # android | ios
  deviceId: ${MOBILE_DEVICE_ID:-}   # empty: the only online device of the platform
  app:
    bundleId: com.example.app       # iOS bundle id / Android package
```

All other keys have defaults; see [Configuration](configuration). A mobile-only profile needs no `browser`, `execution` or `timeouts` block.

## Your first test

| Engine | How a test becomes a mobile test |
|---|---|
| TestNG | extend `io.testfly.mobile.BaseMobileTest`, or put `@MobileTest` on a class or method that already runs with TestFly's listeners (for example in a `BaseTest` subclass) |
| JUnit 5 | extend `io.testfly.mobile.junit5.BaseJUnit5MobileTest`, or put `@MobileTest` on a class or method that uses `TestFlyExtension` |
| Cucumber | tag the scenario or feature `@mobile`; step classes implement `MobileSupport` |

For a mobile test, TestFly skips the browser, opens the device before the test and closes it afterwards. The device belongs to the test's thread.

With [page objects](locators-and-page-objects#page-objects), a test reads as one chain:

```java
public class LoginTest extends BaseMobileTest {

    @Test
    public void signsIn() {
        new HomePage()
                .openLogin()
                .signIn(System.getenv("MOBILE_LOGIN_USER"), System.getenv("MOBILE_LOGIN_PASSWORD"))
                .shouldBeClosed();
    }
}
```

Direct locators work too, for short checks:

```java
screen().getByTestId("username").fill("ada");
screen().getByRole(MobileRole.BUTTON, "Sign in").tap();
expect(screen().getByText("Hello, ada")).toBeVisible();
apiClient().get("/me").send().assertStatus(200);   // backend check in the same test
```

The base classes also provide `apiClient()`, `db()`, `mailbox()`, `ctx()`, `step()`, `softAssert()` and `getTestData()`.

Cucumber steps keep page instances between steps, because creating a page waits for its screen:

```java
public class LoginSteps implements MobileSupport {
    private HomePage home;
    private LoginPage login;

    @Given("the app is on the home screen")
    public void onHome() { home = new HomePage(); }

    @When("I open the login form")
    public void openLogin() { login = home.openLogin(); }
}
```

Outside a test runner, open a device yourself:

```java
try (MobileDevice device = Mobile.connect()) {   // reads testfly.yml
    device.screen().getByText("Settings").tap();
}
```

## Examples

| Example | Engine | Run |
|---|---|---|
| `examples/mobile/IosSettingsExampleTest` | TestNG, iOS Settings app | `mvn test -Dtestfly.profile=mobile-ios -Dtest=IosSettingsExampleTest` |
| `examples/mobile/AndroidSettingsExampleTest` | TestNG, Android Settings app | `mvn test -Dtestfly.profile=mobile-android -Dtest=AndroidSettingsExampleTest` |
| `examples/mobile/IosLoginExampleTest` | TestNG, page objects `HomePage`/`LoginPage` | `MOBILE_LOGIN_USER=… MOBILE_LOGIN_PASSWORD=… mvn test -Dtest=IosLoginExampleTest` (selects `testfly-mobile-ios-login.yml` itself) |
| `examples/cucumber/mobile/IosLoginCucumberRunner` | Cucumber, `features-mobile/ios_login.feature` | `mvn test -Dtest=IosLoginCucumberRunner` |

All paths are under `src/test/java/io/testfly/`. The Settings examples need no app file; their texts assume an English device. Credentials come only from environment variables.
