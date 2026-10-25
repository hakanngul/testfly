---
id: browser-inspector
title: Browser Inspector (VS Code)
sidebar_position: 11
description: "The TestFly Browser Inspector for VS Code: open a page in Chrome or Edge, pick elements on the screenshot or in the DOM tree, and copy ranked TestFly locators and page object fields with their match counts."
---

# Browser Inspector

The Browser Inspector is part of the TestFly VS Code extension, next to the [Mobile Inspector](../mobile/inspector.md). It opens a page in a Chromium-based browser installed on your machine (Chrome, Edge or Chromium) and helps you write page objects:

- the page as a full-length screenshot, with the element under the cursor outlined;
- the DOM tree, with test ids, roles and texts at a glance;
- ranked TestFly locators for the selected element (`getByTestId`, `getByRole`, `getByLabel`, `getByPlaceholder`, `findById`, `getByText`, `find`) and how many elements each one matches on the page;
- a ready-to-paste `Locator` field for a `BasePage` subclass;
- clicking and typing in the browser, back, forward and reload.

## Install

The Browser Inspector ships in the same package as the Mobile Inspector:

```bash
code --install-extension tools/vscode-mobile-inspector/dist/testfly-mobile-inspector-0.3.2.vsix
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
| Toolbar | browser and version, back / forward / reload, the address bar (type a URL and press Enter), the viewport, **Refresh**, the click mode (Inspect / Click), **Close browser** and the status |
| Page (top) | the whole page as one screenshot; hover outlines an element, a click selects it (Inspect) or clicks it in the browser (Click) |
| **DOM Tree** (bottom left) | the element tree with tag, text, test id, id and classes, and a find box (see below); arrow keys move and expand |
| **Locators** (bottom right) | the TestFly locators for the selected element, the page object field, and Click / Type on that element |
| **Attributes** | every attribute, the role, accessible name, text, bounds and state |

Drag the border between the page and the drawer, or between the tree and the details, to resize them; the sizes are remembered. With **Show browser window** on you can also use the browser window itself and press **Refresh** to read the page again.

## Finding an element

The box above the tree works like **Find by string, selector, or XPath** in Chrome DevTools. What you type is evaluated in the page as

- a CSS selector: `#homepage-layout`, `.widget`, `div.homeWidget > .widget__live`, `[data-testid="email"]`;
- an XPath when it starts with `/`, `(` or `./`: `//div[@id='homepage-middle']`;
- a string, matched case-insensitively against each element's markup and own text: `homepage`, `widget__footer`, `id="content"`, `Popular`.

Matches are highlighted in the tree (it is not filtered) and the box shows *1 of N*; the first match is selected. **Enter** and the ↓ button move to the next match, **Shift+Enter** and ↑ to the previous one; each match is revealed in the tree, outlined on the page and its locators are shown. Hover the count to see whether the query matched as a selector, an XPath or text. The query runs again after every refresh or action.

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

## Acting in the browser

- **Click** mode: a click on the screenshot clicks the same point in the browser; the inspector waits for a navigation it starts and reads the page again.
- **Click** and **Type** in the Locators tab act on the selected element: Type clicks the element and types the text.
- Back, forward and reload use the browser history; the address bar opens any URL.

## Keys

| Key | Action |
|---|---|
| `R` | Refresh |
| `I` / `C` | Inspect / Click mode |
| `/` | focus the find box; `Enter` / `Shift+Enter` next / previous match |
| `↑` `↓` | move in the tree |
| `←` `→` | collapse / expand |
| `Esc` | clear the selection |

## Settings

| Setting | Default | Meaning |
|---|---|---|
| `testflyInspector.browserPath` | empty | Chrome, Edge or Chromium executable; empty finds an installed one |
| `testflyInspector.testIdAttribute` | `data-testid` | the attribute `getByTestId()` reads; keep it equal to `locators.testIdAttribute` |

## Limits

- Only Chromium-based browsers (Chrome, Edge, Chromium); Firefox and Safari are not supported.
- Elements inside iframes and shadow roots are not listed.
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
