# TestFly Inspector

Two inspectors in one VS Code extension. The **Mobile Inspector** is an Appium-Inspector-style view of iOS and
Android screens built on [mobilecli](https://github.com/mobile-next/mobilecli): the device screen, the UI tree with
mobilecli's `@eN` refs, every attribute of the element you pick, and the TestFly selector and `@MobileFind` field to
paste into a page object. The **Browser Inspector** does the same for web pages in Chrome or Edge: the page, the DOM
tree and ranked TestFly `Locator`s with their match counts. [Türkçe](README_tr.md)

The **TestFly** icon in the activity bar opens a view with a **Mobile / Browser** switch.

![Mobile Inspector](media/screenshot.png)

## What it does

- **Screen**: the device screenshot with hover outlines; click to select the smallest element under the cursor. In **Tap** mode a click taps the device and the screen is read again.
- **Record** (codegen): in **Record** mode every tap, typed text (element panel), swipe and device button runs on the device and becomes a step. The **Recorded** tab shows a `MobilePage` with one `@MobileFind` field per element (best unique selector) and a `recordedFlow()` method; **Copy** puts it on the clipboard.
- **UI tree**: the `mobilecli dump ui` tree with types, texts, ids and `@eN` refs; arrow keys to move. The finder works like Appium Inspector's *Search for element*: a strategy (TestFly selector, id, accessibility id, XPath, class name, text, contains text) and a selector, matched with TestFly's own rules; matches are highlighted, *1 of N*, `Enter` / `Shift+Enter` move, and the copy button gives the TestFly selector for `@MobileFind`. The **Snapshot** tab shows the same text as `mobilecli snapshot`.
- **Element**: attributes, state flags and bounds; ranked TestFly selectors (id, `role=…;name=…`, `label=`, `placeholder=`, `text=`, `xpath=`) with how many elements each one matches on this screen (✓ unique or *N matches*), copyable one by one or as an `@MobileFind` field.
- **Actions**: tap, long press, tap and type, Back, Home, swipe; copy the equivalent `mobilecli io tap --device … @eN` command.
- **Server**: the button next to the device list shows whether the mobilecli server answers (green on, red off) and starts or stops it. Stopping works for a server started outside VS Code too (JSON-RPC `server.shutdown`); after a stop the extension does not start the server again until you press the button.

## Browser Inspector

- **Start**: TestFly view → **Browser**: page URL, a detected browser (Chrome, Edge, Chromium), viewport, **Show browser window** (off by default: the page shows only in the inspector) and **Keep session** (cookies and logins kept in a TestFly profile; **Clear saved session** removes them), then **Launch and inspect**. Recent pages are one click away. Command palette → **TestFly: Open Browser Inspector** asks for a URL. **Close browser** in the toolbar, or closing the tab, closes the browser.
- **Page** (top): the whole page as one screenshot with hover outlines; click to select the smallest element under the cursor. In **Click** mode a click clicks the browser at that point and the page is read again. Back, forward, reload and an address bar.
- **DOM tree** (bottom left): tags, texts, test ids, ids and classes. The find box works like Chrome DevTools' *Find by string, selector, or XPath* (`#homepage-layout`, `.widget`, `//div[@id='x']` or text): matches are highlighted, *1 of N*, `Enter` / `Shift+Enter` move between them.
- **Locators** (bottom right): `getByTestId`, `getByRole(Role.…, "name")`, `getByLabel`, `getByPlaceholder`, `findById`, `getByText` and a unique `find("css")`, unique first, each with its match count on the page, computed in the page with TestFly's own rules (`Role` selectors, accessible name, case-insensitive substring, innermost text). Copy one, or copy it as a field: `private final Locator signIn = getByTestId("sign-in");`. Click and Type act on the selected element. **Attributes** lists every attribute, role, accessible name and state.
- No WebDriver or extra package: the browser is driven over the DevTools protocol through a pipe (`--remote-debugging-pipe`). Iframes and shadow roots are not listed.

## Requirements

- [mobilecli](https://github.com/mobile-next/mobilecli) installed (`npm i -g mobilecli` or a release binary). The extension starts `mobilecli server start` when a local server does not answer.
- A booted simulator, emulator or device. iOS needs mobilecli's agent once: `mobilecli agent install --device <udid>`.
- Browser Inspector: Chrome, Edge or Chromium. It is found in its usual install location, or set `testflyInspector.browserPath`.

## Install and use

```bash
code --install-extension testfly-mobile-inspector-0.3.2.vsix
```

Activity bar → **TestFly**, or the command palette → **TestFly: Open Mobile Inspector** / **TestFly: Open Browser Inspector**. Mobile keys: `R` refresh, `I` inspect, `T` tap, `E` record, `/` filter, `Esc` clear. Browser keys: `R` refresh, `I` inspect, `C` click, `/` find, `Esc` clear.

| Setting | Default | |
|---|---|---|
| `testflyInspector.serverUrl` | `http://localhost:12000` | mobilecli server (JSON-RPC at `/rpc`) |
| `testflyInspector.mobilecliPath` | `mobilecli` | used to start the server |
| `testflyInspector.autoStartServer` | `true` | start a local server when it is down |
| `testflyInspector.autoRefreshAfterAction` | `true` | read the screen again after an action |
| `testflyInspector.browserPath` | empty | Chrome, Edge or Chromium executable; empty finds an installed one |
| `testflyInspector.testIdAttribute` | `data-testid` | attribute `getByTestId()` reads; keep it equal to `locators.testIdAttribute` |

## Notes

- Selector match counts follow TestFly's locator rules in a simplified form; XPath counts use the same document shape as TestFly's XPath engine. Check a selector in a test when it matters.
- The screen is a snapshot taken on refresh, not a live stream.
- Taps go to the element centre through `device.io.tap`; mobilecli's JSON-RPC has no tap by ref (only the CLI has), so refs are shown for reference and for the copied CLI command.

## Develop

```bash
npm ci
npm test                 # selector, RPC client and browser tests (node:test); the browser test runs when
                         # Chrome/Edge/Chromium is found or TESTFLY_TEST_BROWSER points at one
node test/preview.js .   # renders the webview with a fake device (needs Playwright + Chromium)
npm run package          # builds testfly-mobile-inspector-<version>.vsix
```
