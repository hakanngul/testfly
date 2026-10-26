# TestFly Mobile Inspector for IntelliJ IDEA

The [VS Code Mobile Inspector](../vscode-mobile-inspector/README.md) for IntelliJ-based IDEs (2024.1+): the
same UI (its `media/` files are built into the plugin), running in the IDE's embedded browser (JCEF) and
following the IDE theme. [Türkçe](README_tr.md)

![Mobile Inspector](../vscode-mobile-inspector/media/screenshot.png)

## Install

Settings → Plugins → ⚙ → **Install Plugin from Disk…** → `tools/intellij-mobile-inspector/dist/testfly-mobile-inspector-0.4.0.zip`, then restart the IDE.

## Use

- Left tool window **TestFly Mobile**: every emulator, simulator and device, online or not, with **Inspect**, **Boot** and **Shut down**, unauthorized Android phones, and *Connect Android over Wi‑Fi…*; **Open Mobile Inspector** or **Tools → TestFly: Open Mobile Inspector** opens the inspector as an editor tab.
- While the inspector shows a screen, Java and Kotlin files show above each `@MobileFind` field and `screen().getBy…` call how many elements it matches (code vision; click it to see the matches).
- Screen, UI tree with `@eN` refs and an Appium-style finder (TestFly selector, id, accessibility id, XPath, class name, text), Snapshot tab, element attributes, TestFly selectors with match counts and `@MobileFind` fields, tap / long press / type / Back / Home / swipe, hold and drag on the screenshot, **Live** (the screen is read again every few seconds), the **Server** button that starts or stops mobilecli, and **Record** mode (taps, long presses, drags, typing, swipes and buttons become a `MobilePage` with `@MobileFind` fields): as in the VS Code extension. Keys: `R`, `I`, `T`, `E`, `L`, `/`, `Esc`.
- Settings → Tools → **TestFly Mobile Inspector**: mobilecli server URL (`http://localhost:12000`), mobilecli path, auto-start, refresh after actions.

Needs [mobilecli](https://github.com/mobile-next/mobilecli) and a booted simulator, emulator or device (iOS: `mobilecli agent install --device <udid>` once).

## Build

```bash
./gradlew test buildPlugin    # build/distributions/testfly-mobile-inspector-<version>.zip
```

The UI lives in `../vscode-mobile-inspector/media/`; change it there and both IDEs get it.
