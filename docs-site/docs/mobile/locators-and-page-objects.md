---
id: locators-and-page-objects
title: Locators and Page Objects
sidebar_position: 2
description: "MobileLocator queries, selector strings, MobilePage page objects with @MobileFind and @WaitFor, auto-waiting actions and expect() assertions."
---

# Locators and Page Objects

## Locators

`screen()` returns locators. A locator is a lazy query: creating it does not touch the device; every action or read evaluates it against a fresh view tree.

| Method | Matches |
|---|---|
| `getByRole(MobileRole.BUTTON)`, `getByRole(role, "name")` | native classes of the role on both platforms; `name` is the label, else the text |
| `getByText("Save")`, `getByText("sav", false)`, `getByText(Pattern)` | text, else label, else value (exact, case-insensitive substring, or regex) |
| `getByLabel(...)` | accessibility label |
| `getByTestId("login")` | iOS accessibility identifier or Android resource-id (`login` also matches `com.app:id/login`) |
| `getByPlaceholder(...)` | placeholder text |
| `getByType("android.widget.Switch")` | the raw native class |
| `locate("role=button;name=Sign in")` | a [selector string](#selector-strings) |

Refine with `first()`, `last()`, `nth(i)`, `within(container)`, `or(other)`, `and(other)`, `filterHasText(...)`, `filterHasNotText(...)`, `filterHas(locator)`, `filterHasNot(locator)` and `withTimeout(Duration)`.

Prefer test ids (`accessibilityIdentifier` on iOS, `resource-id` on Android): they do not change with the device language. Roles with a name come next; texts last.

## Selector strings {#selector-strings}

`@MobileFind` and `screen().locate(...)` take one string per element:

| String | Same as |
|---|---|
| `LoginScreen.loginButton` (no `=`) | `getByTestId("LoginScreen.loginButton")` |
| `testid=com.app:id/btnLogin` | `getByTestId("com.app:id/btnLogin")` |
| `text=Sign in` | `getByText("Sign in")` (exact) |
| `label=Password` | `getByLabel("Password")` |
| `placeholder=Username` | `getByPlaceholder("Username")` |
| `type=XCUIElementTypeSwitch` | `getByType("XCUIElementTypeSwitch")` |
| `role=button` / `role=button;name=Sign in` | `getByRole(MobileRole.BUTTON[, "Sign in"])` |

An unknown key, an unknown role or an empty value fails with `MobileLocatorException`, listing the valid formats.

## Page objects {#page-objects}

Pages extend `MobilePage`. A `@MobileFind` field holds the iOS and the Android selector of one element, like Appium's `@iOSXCUITFindBy` and `@AndroidFindBy` pair. Keep fields private and give tests intent methods; a method that changes the screen returns the next page.

```java
public class HomePage extends MobilePage {
    @MobileFind(ios = "HomeScreen.loginButton", android = "com.example:id/btnHome")
    @WaitFor                                    // new HomePage() waits until it is visible
    private MobileLocator loginButton;

    @MobileFind(ios = "cookieBannerAcceptAllButton")
    private MobileLocator cookieAccept;

    @Override
    protected void onLoad() {                   // runs after the @WaitFor fields are visible
        tapIfShown(cookieAccept);
    }

    public LoginPage openLogin() {
        loginButton.tap();
        return new LoginPage();
    }
}

public class LoginPage extends MobilePage {
    @MobileFind(ios = "LoginScreen.nameTextField", android = "com.example:id/etUser")
    @WaitFor
    private MobileLocator username;

    @MobileFind("role=button;name=Sign in")    // value: same selector on both platforms
    private MobileLocator signIn;

    public LoginPage signIn(String user) { username.fill(user); signIn.tap(); return this; }
    public void shouldBeClosed() { expect(username).toBeHidden(); }
}

new HomePage().openLogin().signIn("ada").shouldBeClosed();
```

What `new SomePage()` does, in order:

1. takes the current test's device (`MobilePage(MobileScreen)` takes a screen explicitly, e.g. outside a test runner);
2. sets every `@MobileFind` field, superclasses included, to a locator for the running platform: `ios`/`android`, else `value`;
3. waits for every `@WaitFor` field to be visible, within `timeouts.actionMs`;
4. calls `onLoad()`.

Rules:

- Do not give `@MobileFind` fields an initial value (`= null`): Java runs subclass field initializers after the base constructor and would overwrite the locator. `onLoad()` runs before those initializers.
- A field without a selector for the running platform still gets a locator, which fails when used ("LoginPage.username: no android selector"). Pages can therefore hold platform-only elements.
- Fields must be non-static `MobileLocator`s; another type fails when the page is created.
- `tapIfShown(locator)` taps an element that is visible now and waits for it to disappear; it returns `false` at once when the element is not shown.
- Creating a page waits for its screen. In Cucumber, keep page instances in step-class fields between steps instead of creating a page whose screen has already gone.
- Plain classes work too: `page(Type.class)` (from `MobileSupport`) or `MobilePages.init(page, screen)` fill `@MobileFind` fields without the wait and `onLoad()`.

The example pages are `src/test/java/io/testfly/examples/mobile/HomePage.java` and `LoginPage.java`.

## Actions and assertions

- **Actions:** `tap()`, `doubleTap()`, `longPress()`, `fill(text)` (same as `type(text)`; clears first), `append(text)`, `clear()`, `swipe(SwipeDirection)`. They wait until exactly one element matches and it is visible, enabled and stable, then act on the centre of the element. Several matches fail unless `first()`, `last()` or `nth(i)` picks one (strict mode).
- **Reads:** `getText()`, `getValue()`, `node()` wait for a visible element; `count()`, `nodes()`, `isVisible()` read the current screen without waiting; `waitForVisible()` and `waitForHidden()` wait explicitly.
- **Assertions:** `expect(locator)` with `toBeVisible()`, `toBeHidden()`, `toBeEnabled()`, `toBeDisabled()`, `toBeSelected()`, `toBeChecked()`, `toBeFocused()`, `toHaveText(...)`, `toContainText(...)`, `toHaveValue(...)`, `toHaveCount(n)`, and `not()`. They retry until `timeouts.actionMs` runs out.
- **Device:** `device()` offers `launchApp`, `terminateApp`, `clearAppData`, `installApp`, `openUrl` (deep links), `pressButton("BACK")`, `screenSize()` and `screenshot()`; `screen().swipe(...)` swipes across the screen.

Actions are logged as report steps ("Tap getByText(…)"); typed text is not logged. When a test fails, a device screenshot is attached (`screenshotOnFailure`).
