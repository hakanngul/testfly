# TestFly Inspector

Two inspectors in one VS Code extension. The **Mobile Inspector** is an Appium-Inspector-style view of iOS and
Android screens built on [mobilecli](https://github.com/mobile-next/mobilecli): the device screen, the UI tree with
mobilecli's `@eN` refs, every attribute of the element you pick, and the TestFly selector and `@MobileFind` field to
paste into a page object. The **Browser Inspector** does the same for web pages in Chrome or Edge: the page, the DOM
tree and ranked TestFly `Locator`s with their match counts. [Türkçe](README_tr.md)

The **TestFly** icon in the activity bar opens a view with a **Mobile / Browser** switch.

![Mobile Inspector](media/screenshot.png)

## What it does

- **Screen**: the device screenshot with hover outlines; click to select the smallest element under the cursor. In **Tap** mode a click taps the device, a press held for half a second long-presses, and a drag swipes from where it starts to where it ends; the screen is read again after each.
- **Live**: the **Live** switch above the screen reads it again every 2, 3, 5 or 10 seconds while you use the app on the device, keeping the selection, the open branches and the finder query. Off by default; `L` toggles it. A read waits for the previous one, pauses while the panel is hidden or you are typing into an element, and Live turns itself off after an error.
- **Record** (codegen): in **Record** mode every tap, long press, drag, typed text (element panel), swipe and device button runs on the device and becomes a step (a drag that starts on an element becomes `element.swipe(SwipeDirection.UP)`). The **Recorded** tab shows a `MobilePage` with one `@MobileFind` field per element (best unique selector) and a `recordedFlow()` method; **Copy** puts it on the clipboard.
- **UI tree**: the `mobilecli dump ui` tree with types, texts, ids and `@eN` refs; arrow keys to move. The finder works like Appium Inspector's *Search for element*: a strategy (TestFly selector, id, accessibility id, XPath, class name, text, contains text) and a selector, matched with TestFly's own rules; matches are highlighted, *1 of N*, `Enter` / `Shift+Enter` move, and the copy button gives the TestFly selector for `@MobileFind`. The **Snapshot** tab shows the same text as `mobilecli snapshot`.
- **Element**: attributes, state flags and bounds; ranked TestFly selectors (id, `role=…;name=…`, `label=`, `placeholder=`, `text=`, `xpath=`) with how many elements each one matches on this screen (✓ unique or *N matches*), copyable one by one or as an `@MobileFind` field.
- **Actions**: tap, long press, tap and type, Back, Home, swipe; copy the equivalent `mobilecli io tap --device … @eN` command.
- **Server**: the button next to the device list shows whether the mobilecli server answers (green on, red off) and starts or stops it. Stopping works for a server started outside VS Code too (JSON-RPC `server.shutdown`); after a stop the extension does not start the server again until you press the button.

## Match counts in the editor

While an inspector is open, Java files show a line above each TestFly locator with how many elements it matches on the inspected screen or page: `✓ 1 match on Pixel 8`, `⚠ 3 matches on shop.example.com`, `✗ no match`, or `no iOS selector` for an `@MobileFind` without one for the device's platform. It covers `@MobileFind` fields (as `MobilePages` picks the platform's selector), `screen().getByTestId/getByText/getByLabel/getByPlaceholder/getByRole(MobileRole…)` calls, and web `getByTestId`, `getByRole(Role…)`, `getByLabel`, `getByPlaceholder`, `getByText`, `getByAltText`, `getByTitle`, `findById`, `findByName`, `findByClassName`, `findByXpath` and `find("css")`. Only literal arguments are read; a locator built from a constant is skipped. The counts follow each refresh (and Live). Clicking a line shows the matches in the inspector's finder. `testflyInspector.codeLens` turns it off.

## Browser Inspector

- **Start**: TestFly view → **Browser**: page URL, a detected browser (Chrome, Edge, Chromium), viewport, **Show browser window** (off by default: the page shows only in the inspector) and **Keep session** (cookies and logins kept in a TestFly profile; **Clear saved session** removes them), then **Launch and inspect**. Recent pages are one click away. Command palette → **TestFly: Open Browser Inspector** asks for a URL. **Close browser** in the toolbar, or closing the tab, closes the browser.
- **Page** (top): the whole page as one screenshot with hover outlines; click to select the smallest element under the cursor. In **Click** mode a click clicks the browser at that point and the page is read again. Back, forward, reload and an address bar.
- **DOM tree** (bottom left): tags, texts, test ids, ids and classes. Open shadow roots and same-origin iframes show as `#shadow-root` and `#document` nodes, as in DevTools; a cross-origin iframe shows as not inspected. The find box works like Chrome DevTools' *Find by string, selector, or XPath* (`#homepage-layout`, `.widget`, `//div[@id='x']` or text), selectors also inside shadow roots and frames: matches are highlighted, *1 of N*, `Enter` / `Shift+Enter` move between them.
- **Locators** (bottom right): `getByTestId`, `getByRole(Role.…, "name")`, `getByLabel`, `getByPlaceholder`, `findById`, `getByText` and a unique `find("css")`, unique first, each with its match count on the page, computed in the page with TestFly's own rules (`Role` selectors, accessible name, case-insensitive substring, innermost text). Copy one, or copy it as a field: `private final Locator signIn = getByTestId("sign-in");`. Click and Type act on the selected element. **Attributes** lists every attribute, role, accessible name and state.
  - In an iframe, the counts are within the frame's document and a `withinFrame(By.cssSelector("#card"), () -> cardNumber.click());` snippet shows how to reach it.
  - In a shadow root, TestFly locators do not reach the element; the inspector gives `ShadowDom.pierce("#host", "#target")` (CSS only) and a `private WebElement payNow() { return ShadowDom.pierce(…); }` member.
- **Record**: in **Record** mode a click on the page clicks the browser and becomes a step, and so do Type, the address bar, back, forward and reload. The **Recorded** tab shows a `BasePage` with one `Locator` field per element (best unique locator), `open("…")` and `recordedFlow()`; steps in a frame are wrapped in `withinFrame(...)`.
- **Console** and **Network** tabs: the page's console messages and uncaught errors (level filter, text filter, error count on the tab) and its requests with status, method, type, size and time (type and URL filters; failed and 4xx/5xx requests in red). Both clear when the page navigates unless **Preserve log** is on; click a line to copy it.
- No WebDriver or extra package: the browser is driven over the DevTools protocol through a pipe (`--remote-debugging-pipe`).

## Requirements

- [mobilecli](https://github.com/mobile-next/mobilecli) installed (`npm i -g mobilecli` or a release binary). The extension starts `mobilecli server start` when a local server does not answer.
- A booted simulator, emulator or device. iOS needs mobilecli's agent once: `mobilecli agent install --device <udid>`.
- Browser Inspector: Chrome, Edge or Chromium. It is found in its usual install location, or set `testflyInspector.browserPath`.

## Install and use

```bash
code --install-extension testfly-mobile-inspector-0.4.0.vsix
```

Activity bar → **TestFly**, or the command palette → **TestFly: Open Mobile Inspector** / **TestFly: Open Browser Inspector**. Mobile keys: `R` refresh, `I` inspect, `T` tap, `E` record, `L` live, `/` find, `Esc` clear. Browser keys: `R` refresh, `I` inspect, `C` click, `E` record, `/` find, `Esc` clear.

| Setting | Default | |
|---|---|---|
| `testflyInspector.serverUrl` | `http://localhost:12000` | mobilecli server (JSON-RPC at `/rpc`) |
| `testflyInspector.mobilecliPath` | `mobilecli` | used to start the server |
| `testflyInspector.autoStartServer` | `true` | start a local server when it is down |
| `testflyInspector.autoRefreshAfterAction` | `true` | read the screen again after an action |
| `testflyInspector.browserPath` | empty | Chrome, Edge or Chromium executable; empty finds an installed one |
| `testflyInspector.testIdAttribute` | `data-testid` | attribute `getByTestId()` reads; keep it equal to `locators.testIdAttribute` |
| `testflyInspector.codeLens` | `true` | match counts above TestFly locators in Java files while an inspector is open |

## Notes

- Selector match counts follow TestFly's locator rules in a simplified form; XPath counts use the same document shape as TestFly's XPath engine. Check a selector in a test when it matters.
- The screen is a snapshot taken on refresh (or every few seconds with Live), not a video stream.
- Taps go to the element centre through `device.io.tap`; mobilecli's JSON-RPC has no tap by ref (only the CLI has), so refs are shown for reference and for the copied CLI command.

## Develop

```bash
npm ci
npm test                 # selector, RPC client and browser tests (node:test); the browser test runs when
                         # Chrome/Edge/Chromium is found or TESTFLY_TEST_BROWSER points at one
node test/preview.js .   # renders the webview with a fake device (needs Playwright + Chromium)
npm run package          # builds testfly-mobile-inspector-<version>.vsix
```
