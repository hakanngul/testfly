---
id: device
title: Device and Apps
sidebar_position: 6
description: "MobileDevice: app lifecycle and locales, deep links, hardware buttons, orientation, location, foldables, appearance, clipboard, files, keyboard, screenshots, view tree, screen recording and crash reports."
---

# Device and Apps

`device()` returns the `MobileDevice` of the current test: the app, the system settings and everything that is not one element of the screen. TestFly opens it before a mobile test and closes it afterwards; [Configuration](configuration) chooses which device and app.

```java
device().openUrl("myapp://orders/42");
device().setOrientation(MobileOrientation.LANDSCAPE);
expect(screen().getByText("Order #42")).toBeVisible();
```

Changes that would leak into the next test (orientation, location) are undone when the device closes.

## Device information

| Method | Returns |
|---|---|
| `id()` | mobilecli device id (Android emulator: AVD name; iOS: UDID) |
| `platform()` | `MobilePlatform.IOS` or `ANDROID` |
| `info()` | `MobileDeviceInfo`: id, name, platform, type (simulator, emulator, real device), state, OS version, model |
| `screenSize()` | `MobileScreenSize(width, height, scale)` in screen points |
| `bundleId()` | the configured app (`mobile.app.bundleId`), or empty |
| `actionTimeout()` | the default wait budget of actions and assertions |
| `keepsAppState()` | whether the app keeps its state between tests (`mobile.app.reset: none`) |

## Apps

| Method | What it does |
|---|---|
| `launchApp(bundleId)` | starts the app (Android: the configured `mobile.app.activity`, if any) |
| `launchApp(bundleId, List.of("fr-FR", "en-US"))` | starts the app with these locales for this launch only (localization tests) |
| `terminateApp(bundleId)` | stops the app |
| `clearAppData(bundleId)` | clears its data; on Android also its runtime permissions |
| `installApp(path)` | installs an `.apk`, `.app` or `.ipa` reachable from the mobilecli server |
| `uninstallApp(bundleId)` | removes the app |
| `installedApps()` | bundle ids / package names of the installed apps |
| `isAppInstalled(bundleId)` | whether one is installed |
| `foregroundApp()` | the bundle id / package of the app in front |
| `backgroundApp(Duration)` | home screen, wait, bring the configured app back (resume tests) |

```java
device().terminateApp(device().bundleId());
device().launchApp(device().bundleId(), List.of("de-DE"));
expect(screen().getByText("Anmelden")).toBeVisible();
```

`launchApp` does not wait for the app to reach the foreground; the next locator call waits for its screen instead. How the app is prepared before each test (`relaunch`, `clearData`, `none`) is set by `mobile.app.reset` ([Configuration](configuration)).

## Deep links and buttons

- `openUrl(url)` opens a URL or deep link (`myapp://…`, `https://…` universal/app links) as the system would.
- `pressButton(name)`: `HOME`, `BACK` (Android), `ENTER`, `VOLUME_UP`, `VOLUME_DOWN`, `POWER`, and on Android `APP_SWITCH` and the `DPAD_*` keys.

## Orientation, location and foldables

| Method | What it does |
|---|---|
| `orientation()`, `setOrientation(MobileOrientation.LANDSCAPE)` | reads / rotates the screen; the orientation before the first change is restored when the device closes |
| `setLocation(latitude, longitude)` | overrides the GPS location (degrees); cleared when the device closes |
| `clearLocation()` | restores the real location |
| `setFoldState("half-open")` | sets the hinge of a foldable simulator or emulator: `folded`, `half-open` or `open` |

## Appearance and animations

- `setAppearance("dark")` / `setAppearance("light")` switches the system theme.
- `setAnimations(false)` turns system animations off: screens settle sooner and waits get shorter. Turn them back on with `setAnimations(true)`.

Both can also be set for every test in the config (`mobile.appearance`, `mobile.animations`).

## Keyboard {#keyboard}

- `isKeyboardShown()` tells whether the on-screen keyboard is open.
- `hideKeyboard()` closes it: Android presses BACK; iOS taps the keyboard's `Done`, `Return` or `Hide keyboard` key and fails when the keyboard has none (tap outside the field instead).

## Clipboard and files

| Method | What it does |
|---|---|
| `clipboard()`, `setClipboard(text)`, `clearClipboard()` | read / write the device clipboard (copy-paste flows, one-time codes) |
| `pushFile(localPath, "/sdcard/Download/photo.jpg")` | copies a local file to the device (Android, iOS simulator), at most 1 MB |
| `pullFile(remotePath, localPath)` | copies a device file to a local path and returns it, at most 1 MB |

File transfer is not available on real iPhones.

## Screenshots and the view tree

| Method | Returns |
|---|---|
| `device().screenshot()`, `screen().screenshot()` | a PNG of the whole screen |
| `locator.screenshot()` | a PNG of one element ([Actions](actions#reads)) |
| `screen().viewTree()` | the current tree as `MobileNode`s (type, text, label, identifier, bounds, state, children) |
| `device().viewTreeText()` | the tree as mobilecli's indented text snapshot, one line per element |

```
@e1 [Window]
  @e2 [NavigationBar] "Settings"
  @e3 [Button] "General"
  @e4 [Switch] "Wi-Fi"
```

A failed test saves the screenshot and this text automatically ([Failure Reports](reports)).

## Screen recording

`startRecording(path)` starts recording the screen to an MP4 file and `stopRecording()` stops it and returns the path. To record every test and keep the videos of failed ones, use `mobile.recordVideo: failures` instead ([Failure Reports](reports#videos)). Recording needs a local mobilecli server.

## Crash reports

`crashReports()` lists the crash reports on the device (`MobileCrashReport(id, processName, timestamp)`) and `crashReport(id)` returns one as text. When a mobile test fails, the crash reports written during the test are saved automatically ([Failure Reports](reports#crash-reports)).

## Webviews

`webViews()` and `webView()` return the embedded webviews of the app in front: see [WebViews](webviews).

## Opening a device yourself

Outside a test runner, or for a second device, open one with `Mobile.connect`. It reads the `mobile:` block of `testfly.yml` and closes with try-with-resources:

```java
try (MobileDevice device = Mobile.connect()) {                       // mobile.platform
    device.screen().getByText("Settings").tap();
}
try (MobileDevice ios = Mobile.connect(MobilePlatform.IOS)) { … }    // the mobile.ios block laid over mobile
try (MobileDevice d = Mobile.connect(mobileConfig)) { … }            // a TestFlyConfig.Mobile you built
```
