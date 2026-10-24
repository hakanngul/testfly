# TestFly Mobile Inspector

An Appium-Inspector-style view of iOS and Android screens inside VS Code, built on
[mobilecli](https://github.com/mobile-next/mobilecli). It shows the device screen, the UI tree with
mobilecli's `@eN` refs, every attribute of the element you pick, and the TestFly selector and
`@MobileFind` field to paste into a page object. [Türkçe](README_tr.md)

![Mobile Inspector](media/screenshot.png)

## What it does

- **Screen**: the device screenshot with hover outlines; click to select the smallest element under the cursor. In **Tap** mode a click taps the device and the screen is read again.
- **UI tree**: the `mobilecli dump ui` tree with types, texts, ids and `@eN` refs; filter with `/`, arrow keys to move. The **Snapshot** tab shows the same text as `mobilecli snapshot`.
- **Element**: attributes, state flags and bounds; ranked TestFly selectors (id, `role=…;name=…`, `label=`, `placeholder=`, `text=`, `xpath=`) with how many elements each one matches on this screen (✓ unique or *N matches*), copyable one by one or as an `@MobileFind` field.
- **Actions**: tap, long press, tap and type, Back, Home, swipe; copy the equivalent `mobilecli io tap --device … @eN` command.

## Requirements

- [mobilecli](https://github.com/mobile-next/mobilecli) installed (`npm i -g mobilecli` or a release binary). The extension starts `mobilecli server start` when a local server does not answer.
- A booted simulator, emulator or device. iOS needs mobilecli's agent once: `mobilecli agent install --device <udid>`.

## Install and use

```bash
code --install-extension testfly-mobile-inspector-0.1.4.vsix
```

Command palette → **TestFly: Open Mobile Inspector**. Keys: `R` refresh, `I` inspect, `T` tap, `/` filter, `Esc` clear.

| Setting | Default | |
|---|---|---|
| `testflyInspector.serverUrl` | `http://localhost:12000` | mobilecli server (JSON-RPC at `/rpc`) |
| `testflyInspector.mobilecliPath` | `mobilecli` | used to start the server |
| `testflyInspector.autoStartServer` | `true` | start a local server when it is down |
| `testflyInspector.autoRefreshAfterAction` | `true` | read the screen again after an action |

## Notes

- Selector match counts follow TestFly's locator rules in a simplified form; XPath counts use the same document shape as TestFly's XPath engine. Check a selector in a test when it matters.
- The screen is a snapshot taken on refresh, not a live stream.
- Taps go to the element centre through `device.io.tap`; mobilecli's JSON-RPC has no tap by ref (only the CLI has), so refs are shown for reference and for the copied CLI command.

## Develop

```bash
npm ci
npm test                 # selector and RPC client tests (node:test)
node test/preview.js .   # renders the webview with a fake device (needs Playwright + Chromium)
npm run package          # builds testfly-mobile-inspector-<version>.vsix
```
