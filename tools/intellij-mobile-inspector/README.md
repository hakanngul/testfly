# TestFly Mobile Inspector for IntelliJ IDEA

The [VS Code Mobile Inspector](../vscode-mobile-inspector/README.md) for IntelliJ-based IDEs (2024.1+): the
same UI (its `media/` files are built into the plugin), running in the IDE's embedded browser (JCEF) and
following the IDE theme. [Türkçe](README_tr.md)

![Mobile Inspector](../vscode-mobile-inspector/media/screenshot.png)

## Install

Settings → Plugins → ⚙ → **Install Plugin from Disk…** → `tools/intellij-mobile-inspector/dist/testfly-mobile-inspector-0.1.0.zip`, then restart the IDE.

## Use

- Left tool window **TestFly Mobile** → **Open Mobile Inspector**, or **Tools → TestFly: Open Mobile Inspector**. The inspector opens as an editor tab.
- Screen, UI tree with `@eN` refs, Snapshot tab, element attributes, TestFly selectors with match counts and `@MobileFind` fields, tap / long press / type / Back / Home / swipe: as in the VS Code extension. Keys: `R`, `I`, `T`, `/`, `Esc`.
- Settings → Tools → **TestFly Mobile Inspector**: mobilecli server URL (`http://localhost:12000`), mobilecli path, auto-start, refresh after actions.

Needs [mobilecli](https://github.com/mobile-next/mobilecli) and a booted simulator, emulator or device (iOS: `mobilecli agent install --device <udid>` once).

## Build

```bash
./gradlew test buildPlugin    # build/distributions/testfly-mobile-inspector-<version>.zip
```

The UI lives in `../vscode-mobile-inspector/media/`; change it there and both IDEs get it.
