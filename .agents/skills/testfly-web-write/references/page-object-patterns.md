# Page Objects, lifecycle, and state

## Page Object shape

Extend `BasePage` and normally use its protected no-argument constructor. It resolves `DriverManager.getDriver()` lazily on the calling thread. Use the explicit `WebDriver` constructor only when the page intentionally belongs to a custom driver/session.

Keep a Page Object cohesive:

- Define semantic or stable CSS Locators once per page/component instance.
- Expose intent-level actions such as `signInAs`, `selectTenant`, or `openDetails`.
- Return the same page for fluent mutations and the destination page after navigation when useful.
- Expose a focused Locator or state query when the test owns the business assertion.
- Keep page-readiness checks near navigation; keep scenario outcomes in tests.
- Extract a component only when multiple pages share both its DOM boundary and behavior. Do not wrap every Locator in another generic abstraction.

```java
public final class HeaderComponent extends BasePage {
    private final Locator accountMenu = getByRole(Role.BUTTON, "Account");

    public HeaderComponent openAccountMenu() {
        accountMenu.click();
        return this;
    }

    public Locator signOutAction() {
        return getByRole(Role.MENUITEM, "Sign out");
    }
}
```

Avoid cached `WebElement` values: they become stale after rendering or navigation and skip Locator re-resolution. Avoid static mutable driver, page, Locator configuration, scenario data, session, and soft-assertion state.

## Navigation and browser state

`BasePage`, `BaseTest`, `BaseJUnit5Test`, and `BaseCucumberSteps` share `NavigationSupport`:

- `open()` uses `execution.baseUrl`; `open(path)` normalizes a relative path or accepts an absolute URL.
- `refresh()` waits for page load; `back()` and `forward()` only issue navigation, so wait for the resulting application condition when needed.
- Page/URL/title helpers include `waitForPageLoad`, `waitForUrlContains`, `waitForUrlMatches`, `waitForTitle`, and `waitForTitleContains`.
- Window helpers include `switchToNewTab`, `switchToLastTab`, `switchToMainTab`, `switchToTab(int)`, title/URL switches, window count, and `closeCurrentTabAndSwitchBack`.

Storage, cookies, console errors, geolocation, clipboard, network mocking, visual checks, accessibility, and performance are exposed through support interfaces on the base classes. Load [advanced-web.md](advanced-web.md) before authoring those features.

## Framework lifecycle

- TestNG: extend `BaseTest`; its listeners create and quit the per-thread driver and clear framework state.
- JUnit 5: extend `BaseJUnit5Test` (already extension-enabled), or use `@EnableTestFly` when inheritance is unsuitable. Do not apply both merely for redundancy.
- Cucumber: extend `BaseCucumberSteps`; include `io.testfly.cucumber` in glue so framework hooks own setup, cleanup, screenshots, and scenario context.

Do not call `new ChromeDriver()`, `DriverManager.setDriver(...)`, or `quit()` in ordinary consumer tests.

## Parallel safety

Drivers, browser context, named sessions, and lifecycle state are thread-confined. Construct Page Objects inside the test/scenario that uses them, and keep data immutable or local. Named sessions are limited by `sessions.maxPerTest` and are cleaned up by framework lifecycle; `withSession(name, action)` restores the prior active driver even for nested usage.

`BrowserSessionCache` is a global concurrent cache of cookies/local storage, not isolated test data. Use unique keys, restore only after navigating to the correct domain, refresh after restore, and clear/invalidate shared authentication state deliberately. Deprecated `SessionCache` is not the browser-session API for new code.

Cucumber scenario state must remain in thread-local framework context or scenario-local objects. Do not share mutable step fields across parallel scenarios unless the runner creates separate glue instances and the ownership is explicit.
