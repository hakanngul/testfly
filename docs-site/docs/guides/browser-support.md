---
description: "Which TestFly features work on which browsers. Locators, assertions and traces run everywhere; network mocking, device emulation and geolocation need a local Chrome or Edge."
id: browser-support
title: Browser Support
sidebar_position: 9
---

# Browser Support

TestFly runs on Chrome, Edge, Firefox and Safari. Most features use only WebDriver and JavaScript and
behave the same everywhere. A few need the **Chrome DevTools Protocol (CDP)**, which Selenium offers
only when TestFly starts **Chrome or Edge locally**. On any other driver those features degrade, and
each logs **one warning per browser session** that names what changed.

:::warning CDP is not available on remote drivers
A browser on **Selenium Grid, BrowserStack or Sauce Labs** is driven through a `RemoteWebDriver`, which
has no DevTools connection in TestFly today. That is true **even when the remote browser is Chrome**.
:::

## What works where

| Feature | Local Chrome / Edge | Firefox, Safari | Remote (Grid, cloud) |
|---|---|---|---|
| Locators, actions, [actionability](/docs/guides/wait-engine#actionability), waits | Full | Full | Full |
| [Assertions](/docs/guides/assertions), [ARIA snapshots](/docs/guides/assertions#accessibility-assertions) | Full | Full | Full |
| [Step traces](/docs/guides/tracing), screenshots | Full | Full | Full |
| Accessibility scans (axe-core) | Full | Full | Full |
| [Network mocking](/docs/configuration#clock), `network.blockUrls`, `NetworkAssert` | Full | **Not available.** Routes are ignored and requests reach the real network. `NetworkAssert` fails. | **Not available** |
| [Video recording](/docs/guides/video-recording) | DevTools screencast | Periodic screenshots, so frames are less frequent | Periodic screenshots |
| `DeviceEmulator` | Full: viewport, pixel ratio, touch, user agent, kept across navigation | Resizes the window and overrides `navigator.userAgent` in the current page. Lost on navigation. | Same as Firefox |
| `GeoLocation` | Full | Overridden in the current page only. Lost on navigation. | Same as Firefox |
| [Clock mocking](/docs/clock-mocking) | Survives refresh and navigation | Current page only | Current page only |
| [Console errors](/docs/guides/console-errors) | The browser log: errors raised while a page loads and on pages reached by a link are caught | Only `console.error` calls made after the test called `open()`. Errors raised while a page loads, uncaught exceptions and pages reached by a link are missed. With [`browser.bidi: true`](/docs/guides/console-errors#using-bidi) on Firefox they are caught; Safari has no BiDi. | Not verified |
| Performance LCP and CLS | Full | Reported as `-1`, assertions are skipped | Not verified |

"Full" for Chrome and Edge, and the console error row, were checked against a real browser in the
project's integration tests. The Firefox, Safari and remote columns come from the source and have
not been run against those browsers by the project, apart from the experimental Firefox job
described below.

## What this means for your tests

- If a test relies on a **network mock**, run it on local Chrome or Edge. On other browsers the mock is
  silently absent, so the test talks to the real backend.
- On Firefox and Safari, `failOnConsoleErrors` can pass although the page raised errors while it loaded.
- Where a feature falls back, a warning like this appears once per browser session:

```
[TestFly] NETWORK_MOCK is degraded on FirefoxDriver: Network mocking, URL blocking and network
assertions are not available; routes are ignored and requests reach the real network.
```

## Testing on several browsers

`browser.matrix` runs the suite once per listed browser. The project's own CI runs its browser
integration tests on Chrome, and on Firefox as an **experimental** job: it can fail without failing the
build, so you can see where Firefox differs. To reproduce it locally:

```bash
mvn verify -Preal-backends -Dtestfly.config=src/test/resources/testfly-real-backends-firefox.yml \
  -Dit.test=ConsoleErrorIntegrationTest
```

## Device emulation tip

`DeviceEmulator` on Chrome sets a mobile viewport. A page without `<meta name="viewport"
content="width=device-width">` is laid out at 980 pixels, as on a real phone, so `window.innerWidth`
is 980 rather than the device width.
