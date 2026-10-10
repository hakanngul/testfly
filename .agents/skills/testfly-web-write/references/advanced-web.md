# Advanced Web capabilities and status

## Verified capability inventory

| Area | Status | Consumer guidance |
|---|---|---|
| Semantic locators and roles | Supported | Use `getByRole`, label, placeholder, test id, text, alt text, and title with the actual `Role` enum. |
| Locator chaining/filtering | Supported with limits | String CSS filter, text, scope, index, accessible name/heading level, and exact matching; no Playwright predicates/options. |
| CSS/Selenium interoperability | Supported | `find(String/By/WebElement)`, `Locator.of(By/WebElement)`, and localized raw Selenium escape hatches. |
| Element interactions | Supported | Wait-backed click, clear/type/append, hover, scroll, JS click, text/attribute/value reads. |
| Keyboard actions | Partially supported | No Locator `press`; use `locator.element().sendKeys(Keys...)` locally. |
| Dropdowns/checkboxes/radios | Partially supported | BasePage supports native `<select>`; toggle checkable controls by selected state plus click; no generic custom-widget API. |
| Navigation/browser state | Supported | Configured/relative/absolute open, history, refresh, URL/title waits, windows/tabs, zoom and scroll. |
| Automatic/explicit waiting | Supported | Locator terminals and assertions wait selectively; `WaitEngine` and Selenium `WebDriverWait` cover explicit conditions. |
| Element/page assertions | Supported | Retrying `LocatorAssert` and `PageAssert`; AI assertions require configuration. |
| Soft assertions | Supported | Thread-local collectors, lifecycle flush/clear; not for prerequisite checks. |
| Frames | Supported | BasePage frame helpers restore prior/default context; `WaitEngine` supports frame availability. |
| Shadow DOM | Supported | BasePage/`ShadowDom` CSS find/findAll, click/type/text, root access, and nested `pierce`; use only for actual shadow boundaries. |
| JavaScript execution | Supported | BasePage JS helpers and `JavascriptExecutor` boundary; prefer normal interactions first. |
| Screenshots/recording | Supported with configuration | Visual baselines/tolerance and failure screenshots are supported; video/recording depends on browser/grid/configuration. |
| Storage/cookies/downloads | Supported with caveats | Local/session storage and cookies require an active page/domain. Configure `browser.downloadDir`; clear shared download directories between parallel tests. |
| Windows/named sessions | Supported | Tab/window helpers plus thread-local `session`/`withSession`; honor `sessions.maxPerTest`. |
| Network interception/mocking | Chromium only | CDP on Chrome/Edge; non-Chromium logs one warning and skips interception. Check `isInterceptionActive()` when capability is required. |
| Accessibility/visual testing | Supported with environment inputs | axe-core is bundled; visual assertions require stable baselines, viewport, fonts, and rendering environment. |
| Performance metrics | Partially supported | JavaScript Navigation/Paint APIs are broad; LCP/CLS availability varies, with strongest Chrome/Edge support; unavailable values are `-1`. |
| Self-healing | Optional and restricted | Config/feature gated; only eligible roots heal. AI healing needs provider configuration and never guarantees recovery. |
| Parallel execution/thread safety | Supported with discipline | Driver/context/sessions are thread-local; global browser-session cache, filesystem downloads, baselines, and test data need unique ownership. |
| TestNG integration | Supported/core | Extend `BaseTest`; listeners own browser lifecycle. |
| JUnit 5 integration | Optional dependency | Extend `BaseJUnit5Test` or use `@EnableTestFly`; parameterized tests need standard JUnit params dependency. |
| Cucumber integration | Optional dependencies | Extend `BaseCucumberSteps`; add TestFly hook glue and Cucumber runner dependencies. |
| Deprecated APIs | Deprecated | `$()` aliases, `Locator.css`, and `SessionCache`; migrate to `find`, `cssSelector`, and `BrowserSessionCache`. |
| Playwright-like methods | Unsupported | `.fill()`, `.val()`, `.filter(hasText(...))`, `RoleOptions`, `selectOption`, Locator `press/check/uncheck`. |

## Advanced patterns

Use these only when the scenario requires them:

```java
// Browser storage after navigating to the correct origin
open("/login");
localStorage().set("feature-preview", "true");
refresh();

// Chromium network route
mockRoute("GET", "**/api/profile", Response.json(200, "{\"name\":\"Ada\"}"));
open("/profile");
if (!networkMock().isInterceptionActive()) {
    throw new IllegalStateException("This test requires Chromium interception");
}

// Inside a BasePage subclass: nested shadow roots
shadowPierce("app-shell", "user-card", "button.save").click();

// Visual gate (BasePage or framework test base)
assertScreenshot("profile-page", VisualTolerance.of(1));
```

Run accessibility from `BaseTest`, `BaseJUnit5Test`, or `BaseCucumberSteps` after navigation:

```java
accessibility().withTags("wcag2a", "wcag2aa").run();
```

Verify exact overload imports before copying an advanced snippet into a consumer. Network response types are `io.testfly.network.Response`; visual tolerance is `io.testfly.visual.VisualTolerance`. `BasePage` does not implement `AccessibilitySupport`; keep accessibility gates at the test/step lifecycle boundary unless a component calls the public checker deliberately.

## Configuration boundaries

- `execution.baseUrl` drives `open()` and relative paths.
- `timeouts.explicit` drives `getWait`, Locator resolution, and default assertion polling; page-load timeout is separate.
- Browser choice, headless mode, download directory, recording, screenshot/visual baseline paths, and parallel settings belong in TestFly configuration, not scattered test code.
- `locators.selfHealing`/AI settings enable healing. Keep provider keys in environment-backed configuration.
- Named-session count is bounded by `sessions.maxPerTest` (default 2).

Network mocking, full device emulation, and some browser recording features are browser/provider-specific. Accessibility requires JavaScript execution. Visual comparisons are only reliable with controlled environment inputs. Report these as requirements rather than silently weakening the test.
