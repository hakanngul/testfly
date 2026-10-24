---
name: testfly-mobile-locator-finder
description: Scan a native iOS or Android app screen on a running simulator, emulator or device with the mobilecli CLI (snapshot, dump ui, io tap) and extract TestFly MobileLocator selectors as @MobileFind page-object fields, with iOS and Android selectors side by side. Use when asked to find, list or generate mobile locators, element ids or MobilePage fields; not for writing full mobile tests (use testfly-mobile-write) or for web pages (use testfly-locator-finder).
---

# TestFly mobile locator finder

Turns "scan this app screen, give me locators" into ready-to-paste `@MobileFind` fields for a `MobilePage`. Read the screen from a real device; never guess an id.

## Preconditions

Commands follow the [mobilecli README](https://github.com/mobile-next/mobilecli/blob/main/README.md).

1. mobilecli is installed: `mobilecli --version` (1.0.20 or newer).
2. The device is online: `mobilecli devices` → note its id (Android emulator: the AVD name; iOS: the simulator UDID). An offline emulator or simulator: `mobilecli devices --include-offline`, then `mobilecli device boot --device <id>`.
3. With one online device `--device` can be left out; with several, `export MOBILECLI_DEVICE=<id>` (an explicit `--device` still wins). The commands below keep `--device` for clarity.
4. **iOS only**: the on-device agent is required for UI tree inspection and taps: `mobilecli agent status --device <id>`, else `mobilecli agent install --device <id>`. Android needs no agent.
5. The app is open on the screen to scan: ask the user, or `mobilecli apps launch <bundle-id> --device <id>`. `mobilecli apps foreground --device <id>` shows the app in front, `mobilecli apps list --device <id>` the installed bundle ids (useful for `mobile.app.bundleId`).

If any step fails, report it and stop.

## Scan a screen

1. **Overview (text)**: `mobilecli snapshot --device <id>` (same as `dump ui --format text`). It prints the tree with a ref per element (`@e5`). Use it to understand the screen and to navigate; refs change on every dump, so **a ref is never a locator**.
2. **Ids (JSON)**: `mobilecli dump ui --device <id> > /tmp/<screen>.json`. Each element has `type`, `text`, `label`, `name`, `value`, `identifier`, `placeholder`, `rect` and, on Android, often `resourceId`. The first call on a fresh iOS simulator can take a minute (DeviceKit start); retry once before giving up.
3. Add `--full` to either command when an element is missing (it includes elements such as the on-screen keyboard).

## Pick one selector per element

In this order, and only if it matches exactly one element on the screen (count it in the JSON):

| Priority | Source | `@MobileFind` selector |
|---|---|---|
| 1 | `identifier` (iOS accessibility id) or `resourceId`/`identifier` (Android `pkg:id/name`) | bare string: `LoginScreen.loginButton`, `com.app:id/btnLogin` |
| 2 | role from `type` + accessible name (`label`, else `text`) | `role=button;name=Sign in` (roles: `MobileRole` — BUTTON, TEXTFIELD, TEXT, IMAGE, SWITCH, CHECKBOX, …) |
| 3 | `label` | `label=Password` |
| 4 | `placeholder` | `placeholder=Username` |
| 5 | `text` | `text=Welcome back` (depends on the device language; say which language you read) |
| last | `type` | `type=XCUIElementTypeSwitch` only when nothing else is unique |
| fallback | structure, when nothing above is unique (e.g. repeated ids in list rows) | `xpath=//*[@resource-id='com.app:id/row'][2]`, ``chain=**/Cell[`label == 'Orders'`]/StaticText``, `predicate=label BEGINSWITH 'Sign'`; build them from the `dump ui` JSON, not from Appium Inspector paths (mobilecli prunes unlabeled containers) |

Typed attributes are equivalent and may be used when the user prefers Appium-style fields: `@MobileFind(iosAccessibility = …, androidId = …)`, also `iosId`, `iosXpath`, `iosClassChain`, `iosPredicate`, `iosClassName`, `androidAccessibility`, `androidXpath`, `androidClassName`. One selector per platform.

Strict mode: a selector that matches two elements fails at run time. A typical iOS case is a row Button that holds a StaticText with the same label: prefer the Button's `role=button;name=…` or its id.

## Navigate to the next screen

Tap by ref from the latest snapshot, then scan again:

```bash
mobilecli snapshot --device <id>          # find the ref, e.g. @e12 for "Log in"
mobilecli io tap --device <id> @e12
mobilecli snapshot --device <id>          # the new screen; refs are new
```

To reach a screen behind a form, `mobilecli io fill --device <id> 'text'` replaces the focused field's text and `mobilecli io button --device <id> HOME` presses a hardware button. Do not type real credentials unless the user provides them for this purpose; never write them into files.

A screenshot helps to pair elements visually: `mobilecli screenshot --device <id> --output /tmp/<screen>.png`.

## Both platforms

Scan the iOS app and the Android app separately and pair elements by meaning (same role, label or position in the flow). Never copy an iOS id to Android or the reverse. An element found on one platform only gets only that attribute.

## Output

One `MobilePage` per screen, fields private, with `@WaitFor` on one or two elements that prove the screen is shown:

```java
public class LoginPage extends MobilePage {
    @MobileFind(ios = "LoginScreen.nameTextField", android = "com.app:id/etUsername")
    @WaitFor
    private MobileLocator username;

    @MobileFind(ios = "LoginScreen.loginButton", android = "com.app:id/btnLogin")
    private MobileLocator loginButton;
}
```

Then a short table: field → iOS selector → Android selector → evidence (element `type` and text/label) → note (unique, language-dependent, not found on a platform). List elements you could not identify separately. Write files only when asked; page-object rules are in `testfly-mobile-write`.
