---
id: browser-inspector
title: Browser Inspector (VS Code)
sidebar_position: 11
description: "The TestFly Browser Inspector for VS Code: open a page in Chrome or Edge, pick elements on the screenshot or in the DOM tree, and (shadow roots and iframes too), copy ranked TestFly locators with their match counts, record a page object, and read the page's console and network."
---

# Browser Inspector

The Browser Inspector is part of the TestFly VS Code extension, next to the [Mobile Inspector](../mobile/inspector.md). It opens a page in a Chromium-based browser installed on your machine (Chrome, Edge or Chromium) and helps you write page objects:

- the page as a full-length screenshot, with the element under the cursor outlined;
- the DOM tree, with test ids, roles and texts at a glance;
- ranked TestFly locators for the selected element (`getByTestId`, `getByRole`, `getByLabel`, `getByPlaceholder`, `findById`, `getByText`, `find`) and how many elements each one matches on the page;
- a ready-to-paste `Locator` field for a `BasePage` subclass;
- clicking and typing in the browser, back, forward and reload;
- **Record** mode: use the page and get a `BasePage` with the steps you took;
- the page's console messages and network requests;
- match counts above the locators of your page objects in the editor.

## Install

The Browser Inspector ships in the same package as the Mobile Inspector:

```bash
code --install-extension tools/vscode-mobile-inspector/dist/testfly-mobile-inspector-0.4.0.vsix
```

It needs VS Code 1.85 or newer and Chrome, Edge or Chromium. The browser is found in its usual install location; set `testflyInspector.browserPath` for any other location. No WebDriver, Node package or browser extension is needed: the inspector drives the browser over the DevTools protocol through a pipe.

## Open

Click the **TestFly** icon in the activity bar and switch to **Browser**:

| Field | Meaning |
|---|---|
| Page URL | the page to open; `example.com` becomes `https://example.com`, `localhost:3000` becomes `http://localhost:3000` |
| Browser | the browsers found on this machine; **Rescan** looks again |
| Viewport | Desktop 1440 × 900, Laptop 1280 × 800, Tablet 768 × 1024 or Mobile 390 × 844 |
| Show browser window | off (default): the browser runs without a window and the page shows only in the inspector; on: a browser window opens too, to log in or use the page by hand |
| Keep session | cookies and logins stay between runs in a TestFly profile (never your own browser profile); off uses a throw-away profile. **Clear saved session** deletes the kept profile (close the browser first) |

**Launch and inspect** opens the panel. The last pages are listed under **Recent**. From the command palette, **TestFly: Open Browser Inspector** asks for a URL and opens it without a window and with a kept session.

The inspector always needs a running browser to render the page; with **Show browser window** off you never see it.

## The panel

| Area | What it shows |
|---|---|
| Toolbar | browser and version, back / forward / reload, the address bar (type a URL and press Enter), the viewport, **Refresh**, the click mode (Inspect / Click / Record), **Close browser** and the status |
| Page (top) | the whole page as one screenshot; hover outlines an element, a click selects it (Inspect) or clicks it in the browser (Click) |
| **DOM Tree** (bottom left) | the element tree with tag, text, test id, id and classes, and a find box (see below); arrow keys move and expand |
| **Recorded**, **Console**, **Network** | the code of Record mode, the page's console and its requests (see below), in tabs next to the DOM Tree |
| **Locators** (bottom right) | the TestFly locators for the selected element, the page object field, and Click / Type on that element |
| **Attributes** | every attribute, the role, accessible name, text, bounds and state |

Drag the border between the page and the drawer, or between the tree and the details, to resize them; the sizes are remembered. With **Show browser window** on you can also use the browser window itself and press **Refresh** to read the page again.

## Finding an element

The box above the tree works like **Find by string, selector, or XPath** in Chrome DevTools. What you type is evaluated in the page as

- a CSS selector: `#homepage-layout`, `.widget`, `div.homeWidget > .widget__live`, `[data-testid="email"]`;
- an XPath when it starts with `/`, `(` or `./`: `//div[@id='homepage-middle']`;
- a string, matched case-insensitively against each element's markup and own text: `homepage`, `widget__footer`, `id="content"`, `Popular`.

Selectors also run inside shadow roots and same-origin frames. Matches are highlighted in the tree (it is not filtered) and the box shows *1 of N*; the first match is selected. **Enter** and the ↓ button move to the next match, **Shift+Enter** and ↑ to the previous one; each match is revealed in the tree, outlined on the page and its locators are shown. Hover the count to see whether the query matched as a selector, an XPath or text. The query runs again after every refresh or action.

## Finding a locator

Select an element on the page or in the tree. The **Locators** tab lists the locators that resolve to it, unique ones first and then in TestFly's order of preference:

| Kind | Example | Match count |
|---|---|---|
| Test id | `getByTestId("sign-in")` | elements whose test id attribute equals the value |
| Role | `getByRole(Role.BUTTON, "Sign in")` | elements with the role whose accessible name contains the name |
| Label | `getByLabel("Email")` | form controls whose accessible name contains the label |
| Placeholder | `getByPlaceholder("you@example.com")` | elements whose placeholder contains the text |
| Id | `findById("em")` | elements with that id |
| Text | `getByText("Forgot password?")` | innermost elements whose text contains the text |
| CSS | `find("#login > button")` | the shortest CSS path that is unique on the page |

The counts are computed in the page with the same rules TestFly uses at run time: the role selectors of `Role`, the accessible name of `Locator`, case-insensitive substring matching by default and innermost-only text matching. A count above one means the locator needs narrowing in code (`.within(...)`, `.withText(...)` or `.nth(i)`). The `<title>` of the page counts for `getByText` too, as it does in a test.

The **Page object field** box shows the best locator as a field, for example:

```java
private final Locator signIn = getByTestId("sign-in");
```

It compiles in any `BasePage` subclass (`getByRole` needs `import io.testfly.locator.Role;`). The `<>` button next to each locator copies that locator as a field instead.

If your project reads a different test id attribute (`locators.testIdAttribute`, for example `data-qa`), set `testflyInspector.testIdAttribute` to the same value.

## Shadow roots and iframes

Open shadow roots appear in the tree as `#shadow-root (open)` and same-origin iframes as `#document (url)`, as in DevTools; their elements can be picked on the page and in the tree.

- **In an iframe** the locators are counted in the frame's document, where a test runs them after switching into the frame. The details show the call to wrap them in:

  ```java
  withinFrame(By.cssSelector("#card"), () -> cardNumber.fill("4111 1111 1111 1111"));
  ```

- **In a shadow root** TestFly's locators do not reach the element. The inspector gives the `ShadowDom.pierce` chain from the outermost host to the element, each step a CSS selector unique in its own shadow root, and a page object member for it:

  ```java
  private WebElement payNow() {
      return ShadowDom.pierce("#pw", "#pay");
  }
  ```

A cross-origin iframe shows as *cross-origin: not inspected*; open its URL in the address bar to inspect it.

## Match counts in the editor {#code-lens}

While the Browser Inspector shows a page, Java files get a line above each TestFly web locator with its match count on that page:

```java
✓ 1 match on shop.example.com
private final Locator email = getByTestId("email");

⚠ 3 matches on shop.example.com
private final Locator links = find("header a");
```

It covers `getByTestId`, `getByRole(Role.…)` and `getByRole(Role.…, "name")`, `getByLabel`, `getByPlaceholder`, `getByText`, `getByAltText`, `getByTitle`, `findById`, `findByName`, `findByClassName`, `findByXpath` and `find("css")`, counted in the top document with the rules above. Only literal arguments are read; a locator built from a constant is skipped. The counts follow every refresh and navigation. Click a line to see its matches highlighted in the tree. Turn it off with `testflyInspector.codeLens`; the [Mobile Inspector](../mobile/inspector.md#code-lens) does the same for `@MobileFind`.

## Acting in the browser

- **Click** mode: a click on the screenshot clicks the same point in the browser; the inspector waits for a navigation it starts and reads the page again.
- **Click** and **Type** in the Locators tab act on the selected element: Type clicks the element and types the text.
- Back, forward and reload use the browser history; the address bar opens any URL.

## Record mode {#record}

Switch to **Record** (`E`; the page gets a red frame). Everything you do runs in the browser **and** becomes a step:

| You do | Recorded as |
|---|---|
| start recording on a page | `open("https://…");` |
| click an element on the screenshot, or **Click** in the Locators tab | `field.click();` |
| **Type** in the Locators tab | `field.fill("text");` |
| type a URL in the address bar | `open("https://…");` |
| back / forward / reload | `back();` / `forward();` / `refresh();` |

The **Recorded** tab shows a `BasePage` with one `Locator` field per element, using its best locator (unique first, then TestFly's order), and the steps in order. A step in an iframe is wrapped in `withinFrame(By.cssSelector("…"), () -> …)`; an element in a shadow root becomes a `ShadowDom.pierce` method.

```java
public class RecordedPage extends BasePage {

    private final Locator email = getByTestId("email");

    private final Locator signIn = getByTestId("sign-in");

    public void recordedFlow() {
        open("http://localhost:8123/login");
        email.fill("ada@example.com");
        signIn.click();
    }
}
```

**Copy** puts it on the clipboard, **Clear** starts over. A click on a point without an element is performed but not recorded. Treat the code as a first draft: rename the class and fields, split the flow into intent methods, add assertions, and replace typed credentials with environment variables.

## Console and network

The **Console** tab lists the page's `console.log`, `info`, `warn` and `error` messages, uncaught errors and the browser's own messages (for example a failed resource), with their source file and line. Filter by level (all, warnings and errors, errors) or text; the tab counts the errors.

The **Network** tab lists the page's requests with status, method, name, type, size and time. Filter by type (Fetch/XHR, Doc, JS, CSS, Img, Font, Other) or URL; failed requests and 4xx/5xx answers are red and counted on the tab. Both lists clear when the page navigates unless **Preserve log** is on, and keep the last 1,000 entries. Click a line to copy the message or the URL.

## Keys

| Key | Action |
|---|---|
| `R` | Refresh |
| `I` / `C` / `E` | Inspect / Click / Record mode |
| `/` | focus the find box; `Enter` / `Shift+Enter` next / previous match |
| `↑` `↓` | move in the tree |
| `←` `→` | collapse / expand |
| `Esc` | clear the selection |

## Settings

| Setting | Default | Meaning |
|---|---|---|
| `testflyInspector.browserPath` | empty | Chrome, Edge or Chromium executable; empty finds an installed one |
| `testflyInspector.testIdAttribute` | `data-testid` | the attribute `getByTestId()` reads; keep it equal to `locators.testIdAttribute` |
| `testflyInspector.codeLens` | `true` | match counts above TestFly locators in Java files while an inspector is open |

## Limits

- Only Chromium-based browsers (Chrome, Edge, Chromium); Firefox and Safari are not supported.
- Cross-origin iframes and closed shadow roots are not inspected.
- The tree holds the first 5,000 elements and the screenshot the first 12,000 CSS pixels of a long page.
- The page is a snapshot taken on refresh and after each action, not a live stream.

## Closing

**Close browser** in the toolbar closes the browser and keeps the panel; **Refresh** starts the browser again on the page you were on. Closing the panel tab closes the browser too. With **Keep session** on, cookies and logins are saved when the browser closes; **Clear saved session** in the TestFly view removes them.

## Troubleshooting

| Symptom | Fix |
|---|---|
| "No Chrome, Edge or Chromium found" | install one, or set `testflyInspector.browserPath` to its executable |
| The browser exits at once | another instance may hold the kept-session profile; close it or turn **Keep session** off |
| A locator shows *N matches* | narrow it in code or pick a more specific element or parent |
| The panel stays on "Starting…" | reload the window after installing or updating the extension |
