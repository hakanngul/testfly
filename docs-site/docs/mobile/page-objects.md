---
id: page-objects
title: Page Objects
sidebar_position: 3
description: "MobilePage page objects with @MobileFind (iOS and Android selectors side by side) and @WaitFor, YAML locator files, and mobile preconditions with MobileConditions."
---

# Page Objects

A page object keeps the selectors of one screen and the steps a user takes on it in one class, so tests read as user intent and a changed selector is fixed in one place. TestFly's mobile page objects hold the **iOS and the Android selector of the same element side by side**: one page class serves both platforms.

## Writing a page

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

Locators can also live in a YAML file, one file for both platforms. Values are [selector strings](locators#selector-strings):

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

## Generating a first page

The [Mobile Inspector](inspector)'s **Record** mode turns taps, typing, swipes and buttons on a real device into a `MobilePage` with `@MobileFind` fields and a `recordedFlow()` method. Use it as a starting point: rename the fields, split the flow into intent methods and add the other platform's selectors.
