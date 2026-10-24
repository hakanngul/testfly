---
id: webviews
title: WebViews (Hybrid Apps)
sidebar_position: 7
description: "Drive the web content of hybrid apps (WKWebView, Android WebView, Cordova, Capacitor, Ionic) and Chrome/Safari tabs: DOM locators, page navigation and JavaScript."
---

# WebViews (Hybrid Apps)

Many apps show part of their UI as web content: a payment page, terms, a help centre, or the whole app in Cordova, Capacitor or Ionic. The native view tree only shows such a webview as one element. TestFly reaches **the page's DOM** through mobilecli's webview commands, so you can click, fill and read HTML elements with CSS selectors and roles.

```java
MobileWebView pay = device().webView();
pay.getByRole("textbox", "Card number").fill("4111 1111 1111 1111");
pay.getByRole("button", "Pay").click();
expect(screen().getByText("Payment received")).toBeVisible();     // back on the native screen
```

## Requirements {#requirements}

- **Android:** the app's webview must be debuggable (`WebView.setWebContentsDebuggingEnabled(true)`, the default in debug builds). Chrome tabs work as well.
- **iOS simulator:** the app's `WKWebView` must be inspectable (`isInspectable = true` on iOS 16.4+, the default in debug builds). Safari tabs work as well.
- **Real iPhone:** additionally **Settings > Apps > Safari > Advanced > Web Inspector** on.

## Finding the webview

| Method | Returns |
|---|---|
| `device().webViews()` | every webview of the app in front, plus visible Chrome/Safari tabs, as listed now |
| `device().webView()` | the one visible webview; fails when there is none or more than one |

`MobileWebView` has `id()`, `isVisible()`, and the listed URL and title in `toString()`. Only a visible tab answers scripts.

## Locators in the page

| Method | Matches |
|---|---|
| `locator(css)` | elements matching a CSS selector: `locator("#email")`, `locator("form .total")` |
| `getByTestId("pay")` | `[data-testid="pay"]` |
| `getByText("Pay now")` | the innermost elements whose visible text (or `aria-label`, placeholder, input value) equals the text, whitespace normalized |
| `getByRole("button")` | elements of an ARIA role, native HTML included |
| `getByRole("button", "Pay")` | elements of the role whose name (text, `aria-label` or placeholder) equals `Pay` |

Roles and the HTML they cover:

| Role | Elements |
|---|---|
| `button` | `button`, `[role=button]`, `input[type=button]`, `[type=submit]`, `[type=reset]` |
| `link` | `a[href]`, `[role=link]` |
| `textbox` | text-like `input`s (text, email, password, search, tel, url, number, no type), `textarea`, `[role=textbox]`, `[contenteditable=true]` |
| `checkbox`, `radio` | `input[type=checkbox]`, `input[type=radio]`, `[role=checkbox]`, `[role=radio]` |
| `heading` | `h1`…`h6`, `[role=heading]` |
| `img` | `img`, `[role=img]` |
| `combobox` | `select`, `[role=combobox]` |
| any other | `[role=<name>]` |

## Actions and reads

`MobileWebViewLocator` waits (within `mobile.timeouts.actionMs`) until **exactly one visible** element matches, then:

| Method | What it does |
|---|---|
| `click()` | scrolls the element into view and clicks it |
| `fill(text)` | focuses the field, sets its value and fires `input` and `change` (works with React/Vue/Angular controlled inputs and `contenteditable`) |
| `getText()` | the visible text, whitespace normalized |
| `getValue()` | the `value` of an input, `null` when it has none |
| `getAttribute(name)` | an attribute, `null` when missing |
| `waitFor()` | only waits |

Without waiting: `count()` (number of matches) and `isVisible()` (exactly one visible match). When nothing or several elements match in time, a `MobileTimeoutException` says which.

```java
MobileWebView terms = device().webView();
terms.getByText("I agree").click();
assertEquals(terms.locator("h1").getText(), "Terms of Service");
assertEquals(terms.getByTestId("accept").getAttribute("aria-disabled"), "false");
```

Native assertions (`expect(...)`) work on native locators only. For DOM state, read with these methods and assert with your test framework, or retry with [`expectPoll`](assertions#expect-poll):

```java
expectPoll(() -> pay.getByTestId("status").getText()).toBe("Paid");
```

## Page navigation and JavaScript

| Method | What it does |
|---|---|
| `url()`, `title()`, `content()` | the current URL, title and HTML |
| `navigate(url)`, `reload()`, `goBack()`, `goForward()` | navigation |
| `waitForLoad()` | waits for the page's `load` event |
| `query(css)` | matching elements as maps (`tag`, `text`, `id`, `class`, `value`, `href`) |
| `evaluate("document.title")` | runs an expression and returns its value as plain Java values (`Map`, `List`, `String`, numbers, booleans) |
| `evaluate(body, args...)` | runs `body` as a function body with `arguments`: `evaluate("return document.querySelector(arguments[0]).value", "#email")` |

Prefer the locators; use `evaluate` for what they do not cover (local storage, a canvas, calling an app function).
