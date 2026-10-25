---
id: inspector
title: Mobile Inspector (VS Code and IntelliJ)
sidebar_position: 9
description: "The TestFly Mobile Inspector for VS Code and IntelliJ IDEA: live device screen, UI tree, element attributes, ranked TestFly selectors, @MobileFind fields, device controls and Record mode code generation."
---

# Mobile Inspector

The Mobile Inspector is an Appium-Inspector-style tool built for TestFly and mobilecli. It runs inside your IDE, as a **VS Code extension** and an **IntelliJ IDEA plugin** with the same interface, and helps you write page objects:

- the device screen, with the element under the cursor outlined;
- the UI tree with mobilecli's `@eN` refs, and mobilecli's text snapshot;
- every attribute of the selected element;
- ranked TestFly selectors with their match count on the current screen, ready to paste as `@MobileFind` fields;
- tap, type, swipe and hardware buttons on the device, or hold and drag on the screenshot;
- **Live**: the screen is read again every few seconds while you use the app;
- **Record** mode: use the device and get a page object with the steps you took;
- in VS Code, match counts above the `@MobileFind` fields of your page objects.

![Mobile Inspector](/img/mobile/inspector.png)

## Install

Both packages are in the repository, so no marketplace access is needed.

**VS Code** (1.85 or newer):

```bash
code --install-extension tools/vscode-mobile-inspector/dist/testfly-mobile-inspector-0.4.0.vsix
```

or Extensions view → `…` → **Install from VSIX…**.

**IntelliJ IDEA** (2024.1 or newer, Community or Ultimate, and other IntelliJ-based IDEs): Settings → Plugins → ⚙ → **Install Plugin from Disk…** → `tools/intellij-mobile-inspector/dist/testfly-mobile-inspector-0.3.0.zip`, then restart the IDE. Uninstall an older version first.

**Both need** mobilecli 1.0.20 or newer and a booted simulator, emulator or connected device. On iOS install mobilecli's agent once per device (`mobilecli agent install --device <udid>`). The inspector starts `mobilecli server start` itself when no server answers on `http://localhost:12000`. The **Server** button next to the device list shows whether the server answers and starts or stops it, also a server you started yourself; after a stop it is not started again until you press the button.

## Open

- **VS Code:** the **TestFly** icon in the activity bar (left) → **Mobile** → **Open Mobile Inspector**, or the command palette → **TestFly: Open Mobile Inspector**. The **Browser** side of the same view opens the [Browser Inspector](../guides/browser-inspector.md) for web pages.
- **IntelliJ:** the **TestFly Mobile** tool window (left) → **Open Mobile Inspector**, or **Tools → TestFly: Open Mobile Inspector**. The inspector opens as an editor tab.

With one online device it is selected and read at once; otherwise choose it in the device picker.

## The window

| Area | What it shows |
|---|---|
| Toolbar | device picker with platform badge, **Refresh**, the click mode (Inspect / Tap / Record), device controls, status and the last read time |
| **Screen** | the screenshot; hover outlines an element, a click selects (Inspect) or taps (Tap, Record). **Live** reads it again every few seconds, **Bounds** outlines every element |
| **UI Tree** | the element tree with types, texts and `@eN` refs, and an Appium-style finder; arrow keys move and expand |
| **Snapshot** | mobilecli's text snapshot of the screen (`mobilecli snapshot`), with **Copy** |
| **Recorded** | the code of Record mode (see below) |
| **Element** | the selected element: actions, TestFly selectors, page object field, attributes and state |

The panes can be resized by dragging their borders; the sizes, mode and expanded tree nodes are remembered.

## Finding a selector

Select an element on the screen or in the tree. The **Element** pane lists every selector TestFly can use for it, best first:

1. test id (`resource-id` / accessibility identifier), full and short form;
2. role with name (`role=button;name=Sign in`);
3. label, placeholder, text;
4. XPath by id or text, or by position as a last resort.

Each selector shows how many elements it matches on the current screen: **✓ unique**, **N matches** or **no match**. The XPath counts use the same tree TestFly evaluates, so they agree with the framework. The best unique selector is highlighted and shown as a ready field:

```java
@MobileFind(android = "com.demo:id/btnLogin")
private MobileLocator btnLogin;
```

Copy a selector, the field, or any attribute value (click it). Field names come from the id or the label (`Giriş Yap` → `girisYap`). Run the inspector once per platform and merge the `ios` and `android` selectors into one `@MobileFind`.

## Finding elements

The box above the tree works like **Search for element** in Appium Inspector: pick a strategy, type a selector and press Enter.

| Strategy | Example | Same as |
|---|---|---|
| TestFly selector | `btnLogin`, `text=Sign in`, `role=button;name=Go`, `//android.widget.Button` | `MobileScreen.locate(…)` and `@MobileFind`; a value starting with `/` or `(` is read as XPath |
| id | `com.app:id/login` or `login` | `id=…` (resource-id or accessibility identifier, short form too) |
| accessibility id | `Login` | `accessibility=…` (iOS identifier or name, Android content-desc) |
| XPath | `//android.widget.EditText[@text='Email']` | `xpath=…`, on the same tree document TestFly's XPath uses |
| class name | `android.widget.CheckBox`, `XCUIElementTypeButton` | `type=…` (case-insensitive) |
| text | `Sign in` | `text=…` (exact) |
| contains text | `bol` | any part of a text, label, id, type or `@eN` ref; inspector only |

The tree is not filtered: matches are highlighted, the box shows *1 of N* and the first match is selected. **Enter** / **Shift+Enter** (or the arrows) move between matches. The matching follows TestFly's own rules, so a selector that finds an element here finds it in a test: `text=`, `label=` and `placeholder=` are exact, and `role=` uses the native types of `MobileRole`. An invalid selector shows **Invalid** with the reason in its tooltip. The copy button puts the query on the clipboard as a TestFly selector for `@MobileFind`. iOS `chain=` and `predicate=` selectors are not evaluated in the inspector. The strategy is remembered and the query runs again after every refresh.

## Acting on the device

- **Element pane:** **Tap** and **Long press** the element's centre; **Type** taps the element and types the text from the field next to it (Enter also types); **Copy CLI tap** copies the matching `mobilecli io tap` command.
- **Tap mode** (`T`): a click on the screen taps that point on the device. Hold the mouse button for half a second to long-press; drag to swipe from where you pressed to where you let go (a dashed line shows the drag).
- **Toolbar:** Back (Android), Home, and swipe up, down, left and right across the middle of the screen. Swipe up scrolls a list down; swipe left moves a carousel forward.

After every action the screen is read again (setting below). The first read of a device takes longest; later reads fetch the tree and a compressed screenshot in parallel and show the picture as soon as it arrives.

## Live

Turn on **Live** above the screen (`L`) to use the app on the device while the inspector keeps up: the screen and the tree are read again every 2, 3, 5 or 10 seconds (the list next to the switch). The selection, the open tree branches and the finder query are kept; when nothing on the screen changed, only the picture is replaced. A read starts only after the previous one finished, Live pauses while the panel is hidden or you are typing text for an element, and it turns itself off after an error such as a disconnected device. It is off by default and remembered per panel.

## Record mode (code generation) {#record}

Switch to **Record** (`E`; the screen gets a red frame). Now everything you do runs on the device **and** becomes a step:

| You do | Recorded as |
|---|---|
| click an element on the screen | `field.tap();` |
| **Long press** in the Element pane | `field.longPress();` |
| **Type** in the Element pane | `field.fill("text");` |
| click a point without an element | `screen().tap(x, y);` |
| hold on an element (or a point) | `field.longPress();` (`screen().longPress(x, y);`) |
| drag starting on an element | `field.swipe(SwipeDirection.UP);` (the direction of the drag) |
| a swipe button, or a drag on no element | `screen().swipe(SwipeDirection.UP);` |
| Back / Home | `device().pressButton("BACK");` |

The **Recorded** tab (its badge counts the steps) shows a page object with one `@MobileFind` field per element, using the best unique selector, and the steps in order:

![Record mode](/img/mobile/inspector-record.png)

```java
public class RecordedPage extends MobilePage {

    @MobileFind(android = "com.demo:id/etUserName")
    private MobileLocator etUserName;

    @MobileFind(android = "com.demo:id/btnSubmit")
    private MobileLocator btnSubmit;

    public void recordedFlow() {
        etUserName.fill("12345");
        btnSubmit.tap();
        screen().swipe(SwipeDirection.UP);
    }
}
```

**Copy** puts it on the clipboard, **Clear** starts over. Treat it as a first draft: rename the class and fields, split `recordedFlow()` into intent methods that return the next page, add `@WaitFor` to a field that marks the screen, add assertions, and add the other platform's selectors. Typed text is recorded as you typed it; replace credentials with environment variables.

## Match counts in the editor (VS Code) {#code-lens}

While the Mobile Inspector shows a screen, Java files get a line above each `@MobileFind` field and each `screen().getByTestId(…)`, `getByText`, `getByLabel`, `getByPlaceholder`, `getByType` and `getByRole(MobileRole.…, "name")` call:

```java
✓ 1 match on Pixel 8
@MobileFind(android = "com.demo:id/btnLogin", ios = "loginButton")
private MobileLocator login;

✗ no match on Pixel 8
@MobileFind("text=Cancel")
private MobileLocator cancel;
```

The selector is the one TestFly uses on the device's platform: a typed attribute (`androidId`, `iosXpath`, …), else `android` / `ios`, else `value`; a field without one for the platform shows *no iOS selector*. Only literal strings are read; a selector built from a constant is skipped. The counts follow every refresh and Live. Click a line to see its matches in the finder. The [Browser Inspector](../guides/browser-inspector.md#code-lens) does the same for web locators. Turn it off with `testflyInspector.codeLens`.

## Keys

| Key | Action |
|---|---|
| `R` | refresh the screen |
| `I`, `T`, `E` | Inspect, Tap, Record mode |
| `L` | Live on / off |
| `/` | focus the finder; `Enter` / `Shift+Enter` next / previous match |
| `↑` `↓` `←` `→` | move in the tree, collapse / expand |
| `Esc` | clear the selection |

## Settings

| VS Code (`settings.json`) | IntelliJ (Settings → Tools → TestFly Mobile Inspector) | Default | Meaning |
|---|---|---|---|
| `testflyInspector.serverUrl` | mobilecli server URL | `http://localhost:12000` | the mobilecli server (JSON-RPC at `/rpc`) |
| `testflyInspector.mobilecliPath` | mobilecli path | `mobilecli` | binary used to start the server; set the full path when the IDE's PATH lacks it |
| `testflyInspector.autoStartServer` | start the server automatically | `true` | start a local server when none answers (never for remote URLs) |
| `testflyInspector.autoRefreshAfterAction` | refresh after actions | `true` | read the screen again after tap, type, swipe and buttons |
| `testflyInspector.codeLens` | — | `true` | match counts above locators in Java files while an inspector is open |

A server the inspector started is stopped when the inspector closes; a server that was already running is left alone.

## Troubleshooting

| Symptom | Fix |
|---|---|
| "mobilecli server … is not reachable" | mobilecli is not installed or not on the IDE's PATH: set the mobilecli path setting, or start `mobilecli server start` yourself |
| No device in the picker | boot a simulator/emulator (`mobilecli devices` must list it as online), then the reload button next to the picker |
| iOS: every read fails naming the agent | `mobilecli agent install --device <udid>` |
| IntelliJ: "needs JCEF" | the IDE runs on a runtime without the embedded browser; switch the boot runtime to a JetBrains Runtime with JCEF (Help → Find Action → Choose Boot Java Runtime) |
| The extension is missing from the activity bar | reload the window (VS Code) or restart the IDE (IntelliJ) after installing |
