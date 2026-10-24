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

### Locators in code

`MobileLocator` has static factories named like Appium's and the web `Locator`'s: `id(...)`, `accessibilityId(...)`, `xpath(...)`, `iosClassChain(...)`, `iosPredicate(...)`, `className(...)`, `byTestId`, `byText`, `byLabel`, `byPlaceholder`, `byRole(role[, name])` and `of(selectorString)`. They bind to the current test's device each time they are used, so a `static final` field is safe in parallel runs. Used on a thread without an open device they fail with `MobileException`.

```java
private static final MobileLocator SIGN_IN = MobileLocator.xpath("//*[@text='Sign in']");
SIGN_IN.tap();
```

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
| `id=com.app:id/btnLogin` | same as `testid=` (Appium `id`) |
| `accessibility=loginButton` | iOS identifier, name or label; Android content-desc (Appium `accessibilityId`) |
| `xpath=//android.widget.Button[@text='Sign in']` | XPath 1.0 over the view tree |
| ``chain=**/XCUIElementTypeCell[`label == 'Orders'`]`` | iOS class chain (common subset) |
| `predicate=label BEGINSWITH 'Sign'` | iOS NSPredicate (common subset) |

XPath, class chain and predicate run in TestFly on the tree mobilecli returns; mobilecli has no such queries. Limits:

- **XPath:** elements are named `XCUIElementType…` (iOS) or by their Android class under a `hierarchy` root. Attributes: `resource-id`, `content-desc`, `text`, `name`, `label`, `value`, `placeholder`, `type`, `class`, `enabled`, `visible`, `selected`, `checked`, `focused`, `x`, `y`, `width`, `height`. mobilecli drops unlabeled containers, so full-path XPaths copied from Appium Inspector may not match; XPaths by id, text or type do.
- **Class chain:** `/`-separated segments, `**/` for any depth, a type (with or without `XCUIElementType`) or `*`, and filters ``[`predicate`]`` or `[n]` (1-based, negative from the end).
- **Predicate:** `==`, `!=`, `BEGINSWITH`, `ENDSWITH`, `CONTAINS`, `LIKE`, `MATCHES` with `[c]`/`[d]`, joined by `AND`, `OR`, `NOT` and parentheses, on `name`, `label`, `value`, `type`, `identifier`, `text`, `placeholderValue` and the state flags.

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
- Instead of a selector string, a field can use one typed attribute per platform, as Appium's `@iOSXCUITFindBy(accessibility = …)` does: `iosAccessibility`, `iosId`, `iosXpath`, `iosClassChain`, `iosPredicate`, `iosClassName`, `androidAccessibility`, `androidId`, `androidXpath`, `androidClassName`. Setting two for one platform (say `android` and `androidId`) fails when the page is created.

  ```java
  @MobileFind(iosAccessibility = "LoginScreen.loginButton", androidId = "com.example:id/btnLogin")
  private MobileLocator login;
  ```
- Plain classes work too: `page(Type.class)` (from `MobileSupport`) or `MobilePages.init(page, screen)` fill `@MobileFind` fields without the wait and `onLoad()`.

The example pages are `src/test/java/io/testfly/examples/mobile/HomePage.java` and `LoginPage.java`.

## YAML locator files {#yaml-locators}

Locators can also live in a YAML file, one file for both platforms. Values are [selector strings](#selector-strings):

```yaml
# src/test/resources/mobile-locators/login.yml
login:
  username:
    ios: LoginScreen.nameTextField
    android: com.example:id/etUsername
  signIn: role=button;name=Sign in     # a plain value: both platforms
  welcome:
    mobile: text=Welcome back           # mobile: both platforms
```

```java
MobileLocators locators = MobileLocators.load("mobile-locators/login.yml");   // classpath, else file path
locators.get("login.username").fill("ada");                                    // the running platform's selector
```

- Groups nest; keys join with dots (`login.username`). A map with only `ios`, `android` and `mobile` keys is one locator; `ios`/`android` win over `mobile`.
- Every selector is checked when the file loads; an invalid one fails naming its key. An unknown key fails listing the keys.
- A key without a selector for the running platform yields a locator that fails when used, as with `@MobileFind`.
- `get(key)` uses the current test's device; `get(key, screen)` takes a screen explicitly. Load a file once (for example in a `static final` field) and share it: it holds no device state.
- Example: `src/test/resources/mobile-locators/login.yml`.

## Preconditions {#preconditions}

Shared setup, such as signing in, belongs in a precondition instead of every test. Write the provider with page objects in a `MobileConditions` subclass and ask for it with `@PreCondition`:

```java
public class LoginConditions extends MobileConditions {
    @ConditionProvider("loggedIn")
    public void loggedIn() {
        new HomePage().openLogin().signIn(System.getenv("MOBILE_LOGIN_USER"), System.getenv("MOBILE_LOGIN_PASSWORD"));
    }
}

@PreCondition("loggedIn")
@Test
public void showsTheBalance() { /* starts signed in */ }
```

- Register providers in `src/test/resources/META-INF/services/io.testfly.mobile.MobileConditions` (one class name per line) or call `MobileConditions.register(new LoginConditions())` before the suite.
- When to run: app state cannot be cached like browser cookies. With `mobile.app.reset: none` the app keeps its state, so a condition runs once per device; with `relaunch` or `clearData` it runs before every test that asks for it. A retry always runs it again.
- TestNG reads `@PreCondition` from the test method; JUnit 5 from the method, then the class. Cucumber does not read it: use a `Background`.
- On a mobile test, `@PreCondition` names refer to `MobileConditions` providers only; web `BaseConditions` providers serve browser tests.
- Example: `src/test/java/io/testfly/examples/mobile/LoginConditions.java`.

## Actions and assertions

- **Actions:** `tap()`, `doubleTap()`, `longPress()`, `fill(text)` (same as `type(text)`; clears first), `append(text)`, `clear()`, `swipe(SwipeDirection)`, `pinchOut()` / `pinchIn()` (zoom in / out, centred on the element) and `dragTo(target)`. They wait until exactly one element matches and it is visible, enabled and stable, then act on the centre of the element. Several matches fail unless `first()`, `last()` or `nth(i)` picks one (strict mode).
- **Scrolling:** `scrollIntoView()` swipes up (or `scrollIntoView(SwipeDirection)`) until exactly one match is visible; it fails at the end of the list or when `timeouts.actionMs` runs out.
- **Reads:** `getText()`, `getValue()`, `getAttribute(name)` and `node()` wait for a visible element; `getAttribute` takes `resource-id`, `content-desc` and `class` as aliases and returns `null` for a missing attribute. `count()`, `nodes()`, `allTexts()`, `isVisible()`, `isPresent()`, `isHidden()`, `isEnabled()`, `isDisabled()`, `isSelected()`, `isChecked()` and `isFocused()` read the current screen without waiting; the `is…` state reads fail on several matches (strict). `waitForVisible()` and `waitForHidden()` wait explicitly.
- **Assertions:** `expect(locator)` with `toBeVisible()`, `toBeHidden()`, `toBeEnabled()`, `toBeDisabled()`, `toBeSelected()`, `toBeChecked()`, `toBeFocused()`, `toHaveText(...)`, `toContainText(...)`, `toHaveValue(...)`, `toHaveCount(n)`, and `not()`. They retry until `timeouts.actionMs` runs out.
- **Device:** `device()` offers `launchApp`, `terminateApp`, `clearAppData`, `installApp`, `openUrl` (deep links), `pressButton("BACK")`, `screenSize()` and `screenshot()`; `screen().swipe(...)` swipes across the screen.
- **Device settings:** `device().setOrientation(MobileOrientation.LANDSCAPE)` and `orientation()`; `setLocation(latitude, longitude)` and `clearLocation()`. Both are undone when the device closes, so the next test starts in the original orientation with the real location. `startRecording(path)` / `stopRecording()` record the screen by hand; automatic recording is [`recordVideo`](configuration#screen-recording). `setAnimations(false)` and `setAppearance("dark")` (also as [`animations` / `appearance`](configuration) in the config).
- **Clipboard and files:** `clipboard()`, `setClipboard(text)`, `clearClipboard()`; `pushFile(localPath, "/sdcard/Download/photo.jpg")` and `pullFile(remotePath, localPath)` (Android and iOS simulator, at most 1 MB per file).
- **Apps and keyboard:** `isAppInstalled(id)`, `foregroundApp()`, `uninstallApp(id)`, `backgroundApp(Duration)` (home screen, wait, back to the app); `isKeyboardShown()` and `hideKeyboard()` (Android presses BACK; iOS taps the keyboard's Done/Return key).
- **Crash reports:** `crashReports()` and `crashReport(id)`. When a mobile test fails, the crash reports written during the test are saved to `target/reports/crashes/` and logged as a step.
- **Webviews:** `device().webViews()` lists the webviews of the app in front (and visible Chrome/Safari tabs); `webView()` returns the one visible webview. `MobileWebView` offers `url()`, `title()`, `content()`, `evaluate(js)`, `query(css)`, `navigate(url)`, `reload()`, `goBack()`, `goForward()` and `waitForLoad()`.

Actions are logged as report steps ("Tap getByText(…)"); typed text is not logged. When a test fails, a device screenshot is attached (`screenshotOnFailure`).
