---
description: "Catch JavaScript errors in Selenium tests: ConsoleErrorCollector captures browser console errors so silent JS failures fail the test."
id: console-errors
title: Console Error Collector
sidebar_position: 11
---

# Console Error Collector

`ConsoleErrorCollector` captures JavaScript console errors during test execution. Use it to catch silent JS failures that don't cause visible test failures.

---

## Configuration

```yaml title="testfly.yml"
browser:
  captureConsoleErrors: true    # collect JS errors (default: false)
  failOnConsoleErrors: false    # fail test on any JS error (default: false)
```

---

## Manual collection in a test

```java
import io.testfly.browser.ConsoleErrorCollector;

@Test
public void noJsErrorsOnLogin() {
    open("/login");
    ConsoleErrorCollector.injectShim();   // start capturing

    type(By.id("username"), "admin");
    type(By.id("password"), "secret");
    click(By.id("submit"));

    List<String> errors = ConsoleErrorCollector.getErrors();
    softAssert().that(errors.isEmpty(), "Unexpected JS errors: " + errors);
}
```

---

## Clear between interactions

Isolate which action produced errors:

```java
open("/checkout");
ConsoleErrorCollector.injectShim();

click(By.id("add-to-cart"));
List<String> addErrors = ConsoleErrorCollector.getErrors();

ConsoleErrorCollector.clear();   // reset before next action

click(By.id("proceed-to-payment"));
List<String> paymentErrors = ConsoleErrorCollector.getErrors();
```

---

## Chrome vs Firefox

| Browser | Method | Notes |
|---|---|---|
| Chrome, Edge | WebDriver browser logs | Works automatically, no shim needed. Errors raised while a page loads, and on pages reached by a link, are caught. |
| Firefox | JS console shim | Call `injectShim()` after page load. Only `console.error` calls made after that are seen. |
| Firefox, with `browser.bidi: true` | WebDriver BiDi log events | Every error from the moment a page starts loading, including uncaught exceptions and pages reached by a link. |

Chrome collects errors automatically via `LogType.BROWSER`. Firefox does not expose browser logs through
WebDriver, so without BiDi it needs the JS shim, which can only see errors raised after it was injected.
That means `failOnConsoleErrors` can pass on Firefox although the page raised an error while loading.

### Using BiDi

```yaml title="testfly.yml"
browser:
  captureConsoleErrors: true
  failOnConsoleErrors: true
  bidi: true                    # open a WebDriver BiDi connection (default: false)
```

With `bidi: true` the browser starts with a BiDi connection, and TestFly listens to its log events from
the moment the test gets its browser, so no shim is needed. Errors from earlier tests are forgotten when
a test starts, also when the browser is reused (`browser.lifecycle: per-suite`). Check which capture is in
use with `ConsoleErrorCollector.isBiDiActive()`.

- It works with a locally started Chrome, Edge or Firefox. Safari has no BiDi, and TestFly does not open
  a BiDi connection on remote drivers (Grid, BrowserStack, Sauce Labs); there it logs one warning and
  uses the usual capture.
- Events arrive over a websocket, a moment after the page ran, so read errors after the page has settled.
- The BiDi capture is checked against Chrome in the project's integration tests. Firefox support depends
  on its BiDi implementation and is exercised by the experimental Firefox CI job.

---

## API

### `ConsoleErrorCollector.injectShim()`
Injects a JavaScript shim that intercepts `console.error` calls. Call after page load on Firefox or for cross-browser consistency.

### `ConsoleErrorCollector.collect()`
Returns all JS errors captured since the last `clear()`. Reads from WebDriver logs (Chrome) or the JS shim buffer.

### `ConsoleErrorCollector.getErrors()`
Alias for `collect()`.

### `ConsoleErrorCollector.clear()`
Resets the JS shim error buffer on the current page.

### `ConsoleErrorCollector.isEnabled()`
Returns `true` if `browser.captureConsoleErrors: true` is set in config.
