---
description: "Web-first, auto-retrying assertions with assertThat() and fluent softAssert() in TestFly."
id: assertions
title: Web-First Assertions (assertThat)
sidebar_label: Assertions
sidebar_position: 4
---

# Web-First Assertions

TestFly provides web-first assertions inspired by Playwright's `expect()` and AssertJ. All assertions poll the DOM via `WebDriverWait` until the condition is met or the timeout is exceeded. You never need to write manual waits or `Thread.sleep()` before asserting.

```java
import static io.testfly.assertion.SeleniumAssert.assertThat;
// Or directly in BaseTest, BasePage, BaseJUnit5Test, and BaseCucumberSteps:
assertThat(By.id("dashboard")).isVisible();
```

---

## Why Web-First Assertions?

Traditional assertions (like TestNG `Assert.assertTrue(el.isDisplayed())` or JUnit `assertTrue`) evaluate the condition immediately at a single moment in time. If the element is still animating, fetching data, or rendering, the test fails spuriously.

TestFly's `assertThat()`:
1. **Auto-retries** during the configured `timeouts.explicit` (default 10s).
2. **Logs each step** automatically into [`StepLogger`](https://github.com/hakanngul/testfly/blob/development/src/main/java/io/testfly/steps/StepLogger.java) and surfaces it in the HTML report.
3. **Works seamlessly** with both Selenium [`By`](https://github.com/hakanngul/testfly/blob/development/src/main/java/io/testfly/assertion/SeleniumAssert.java) and fluent [`Locator`](https://github.com/hakanngul/testfly/blob/development/src/main/java/io/testfly/locator/Locator.java) instances (`$()`, `getByRole()`, etc.).

---

## Available Matchers

### Visibility

```java
assertThat(By.id("welcome-banner")).isVisible();
assertThat(By.cssSelector(".spinner")).isHidden();
```

### Text

```java
// Exact text match (trimmed)
assertThat(By.tagName("h1")).hasText("Dashboard");

// Substring containment
assertThat(By.id("greeting")).containsText("Welcome back");
```

### Interactability & State

```java
assertThat(By.id("submit-btn")).isEnabled();
assertThat(By.id("delete-btn")).isDisabled();
assertThat(By.id("terms-checkbox")).isChecked();
assertThat(By.id("search-input")).isFocused();
```

### Verified Click

`assertThat(...).click()` clicks the element and asserts that the click really reached it:

```java
assertThat(headerLoginButton).click();
assertThat(submitButton).as("Submit order").click();
```

Behind the scenes it:

1. Finds the element exactly like `locator.click()` does, including chain filters (`nth`, `filter`, `withText`) and self-healing, and waits until it is visible and enabled.
2. Scrolls it to the centre of the viewport and moves the mouse onto it. A submit button at the bottom of a long form is brought into view first.
3. Waits until nothing covers it, such as a cookie banner, a modal or a sticky header.
4. Clicks it and checks that the `click` event was delivered to the element.
5. Logs a passed `Clicked: ...` step in the report.

If a step fails, the error says which one, for example:

```text
Click not performed: element not visible and enabled: By.id: login (No element found for: By.id: login)
Click not performed: By.id: login is covered by <div class='cookie-banner'> (timeout: 10s)
Click did not reach By.id: login (no click event was received)
```

Finding the element uses `timeouts.explicit`, like `locator.click()`. `within(...)` applies to the later steps, such as waiting for a covering element to go away.

In soft mode (`softly()` / `softAssert(...)`) the failure is recorded and the test continues. If the click navigates away or opens an alert, it counts as delivered and the alert is left open.

`assertThat(...).click()` only proves that the click reached the element. To prove the app reacted, assert on the result, for example `assertThatPage().urlContains("/login")` or `assertThat(loginModal).isVisible()`. The plain `locator.click()` action is unchanged.

### Verified Fill & Clear

`assertThat(...).fill(text)` types into a field and asserts that the value really stuck. `assertThat(...).clear()` does the same for an empty value:

```java
assertThat(userNameField).fill("admin");
assertThat(passwordField).fill(System.getenv("APP_PASSWORD"));
assertThat(searchField).clear();
```

Behind the scenes it:

1. Finds the element like `locator.fill()` does (chain filters, self-healing) and waits until it is visible and enabled.
2. Waits until it is not `readonly`, then scrolls it to the centre of the viewport.
3. Clears it and types the text, exactly like `locator.fill()`.
4. Waits until the value equals the text, and checks again after a short pause. This catches values that `maxlength`, an input mask, or the app's own state (React, Vue, ...) changed or reset after typing.
5. Logs a passed `Filled: ... with [admin]` or `Cleared: ...` step.

Example failures:

```text
Fill not performed: element is read-only: By.id: username (timeout: 10s)
Fill did not stick for By.id: code: expected value [abcdef] but was [abc] (timeout: 10s)
Fill did not stick for By.id: password: expected 7 characters but got 0 (password value hidden) (timeout: 10s)
```

For `type="password"` fields the value never appears in the report or in failure messages; only its length does.

The value must match exactly. A field that reformats its input, such as a phone mask that turns `5321234567` into `(532) 123 45 67`, fails this assertion. For such fields use `locator.fill(...)` and then `assertThat(field).hasValue("(532) 123 45 67")`.

### Attributes & CSS

```java
// Attribute value match
assertThat(By.name("username")).hasValue("admin");
assertThat(By.id("link")).hasAttribute("target", "_blank");

// Attribute presence check (regardless of value)
assertThat(By.id("btn")).hasAttribute("disabled");

// CSS property check
assertThat(By.id("error-toast")).hasCssValue("color", "rgb(255, 0, 0)");

// CSS class membership
assertThat(By.id("item")).hasClass("active");
```

### Form Inputs & Validation

Input fields have no visible text, so `hasText()` does not work on them. Assert on their value instead:

```java
// Value checks
assertThat(passwordField).hasValue();          // any non-empty value
assertThat(searchField).hasEmptyValue();
assertThat(emailField).containsValue("@example.com");
assertThat(phoneField).valueMatches("\\+90 \\d{3} \\d{3} \\d{4}");

// Password masking ("show password" toggle)
assertThat(passwordField).isPasswordHidden();  // type="password"
showPasswordToggle.click();
assertThat(passwordField).isPasswordVisible(); // type is no longer "password"

// Input attributes
assertThat(usernameField).hasType("text").hasPlaceholder("Username").hasMaxLength(20);
assertThat(usernameField).isRequired();        // required or aria-required="true"
assertThat(usernameField).isEditable();        // visible, enabled, not readonly
assertThat(referenceField).isReadOnly();

// HTML5 constraint validation
assertThat(emailField).isInvalid().hasValidationMessage("@");
assertThat(emailField).isValid();
```

`hasValidationMessage()` checks the browser's own message, which depends on the browser and its locale.

To read an input's value in your code, use `locator.getValue()`. `getText()` always returns `""` for inputs.

### Text Patterns & Lists

```java
assertThat(By.id("order")).hasTextMatching("Order #\\d+ placed");
assertThat(By.id("status")).hasTextIgnoringCase("active");

// All matching elements, in order / in any order
assertThat($(".breadcrumb li")).hasTexts("Home", "Products", "Shoes");
assertThat($(".tag")).containsTexts("java", "selenium");
```

### Selection

```java
assertThat(By.id("newsletter")).isNotChecked();
assertThat(By.cssSelector("option[value='tr']")).isSelected();
assertThat(By.id("country")).hasSelectedOption("Turkey");
assertThat(By.id("country")).hasOptions("Germany", "Turkey", "USA");
```

### Accessibility & Identity

```java
assertThat(By.id("search")).hasRole("searchbox").hasAccessibleName("Search products");
assertThat(By.cssSelector("main > section")).hasId("results");
assertThat(By.id("footer-cta")).isInViewport();
```

### Negations

Each negation is its own method. A negation still needs the element to be there: only the property must not match. If the element is missing, the assertion keeps retrying and then fails. To assert that an element is absent, use `doesNotExist()` or `isHidden()`.

```java
assertThat(By.id("status")).doesNotHaveText("Error");
assertThat(By.id("status")).doesNotContainText("failed");
assertThat(By.name("username")).doesNotHaveValue("guest");
assertThat(By.id("submit")).doesNotHaveClass("loading");
assertThat(By.id("submit")).doesNotHaveAttribute("disabled");
assertThat(By.id("email")).doesNotHaveAttribute("aria-invalid", "true");
assertThat(By.id("search")).isNotFocused();
assertThat(By.id("error-banner")).doesNotExist(); // no element in the DOM
```

### Element Count & Presence

```java
assertThat(By.cssSelector("table tbody tr")).count(5);
assertThat($(".card")).count(3);
assertThat($(".card")).countAtLeast(1).countAtMost(10);

assertThat(By.id("csrf-token")).exists();         // present, visible or not
assertThat(By.id("error-banner")).doesNotExist(); // absent from the DOM
```

When you assert on a `Locator` (`$()`, `getByRole()`, ...), only visible elements are matched. So `count`, `exists` and `doesNotExist` ignore hidden elements. Pass a `By` if hidden elements must be counted.

### Page & URL (PageAssert)

Assert on page title and URL with automatic wait retry — no manual `getDriver().getTitle()` or `getCurrentUrl()` with raw TestNG/JUnit assertions required:

```java
// Title checks
assertThat(getDriver()).hasTitle("Dashboard");
assertThat(getDriver()).titleContains("Sauce");
assertThatPage().hasTitle("Products");

// URL checks
assertThat(getDriver()).hasUrl("https://example.com/dashboard");
assertThat(getDriver()).urlContains("/inventory");
assertThat(getDriver()).urlMatches(".*\\/orders\\/\\d+");

// With custom message & timeout
assertThat(getDriver())
    .as("Should land on dashboard after login")
    .within(Duration.ofSeconds(5))
    .urlContains("/dashboard");

// Alerts (the alert is left open)
assertThatPage().hasAlert().hasAlertText("Saved successfully");
assertThatPage().hasNoAlert();

// Cookies & localStorage
assertThatPage().hasCookie("session").hasCookie("locale", "tr");
assertThatPage().doesNotHaveCookie("tracking");
assertThatPage().hasLocalStorageItem("theme", "dark");

// Windows / tabs
assertThatPage().hasWindowCount(2);
```

---

## Modifiers & Customization

### Custom Timeout (`within`)

Override the default `timeouts.explicit` duration for slow-loading components (such as report exports or large file uploads), or for quick dismissals:

```java
// Using Duration
assertThat(By.id("export-ready-toast"))
    .within(Duration.ofSeconds(30))
    .isVisible();

// Using seconds shorthand
assertThat(By.id("quick-tooltip"))
    .within(2)
    .isVisible();
```

### Descriptive Failure Messages (`as`)

Attach contextual information to explain *why* an assertion was made:

```java
assertThat(By.id("user-avatar"))
    .as("User profile picture after OAuth login")
    .isVisible();
// If it fails: "[User profile picture after OAuth login] Expected element to be visible: By.id: user-avatar (timeout: 10s)"
```

---

## Soft Assertions (`softAssert`)

When you want to perform multiple checks without terminating the test on the first failure, use **Soft Assertions**. The framework collects all failures and marks the test failed at the end, reporting all issues together.

TestFly supports three fluent ways to use soft assertions:

### 1. Direct `softAssert(locator)`

Available as a built-in helper in `BaseTest`, `BasePage`, `BaseJUnit5Test`, and `BaseCucumberSteps`:

```java
softAssert(By.id("username")).hasValue("johndoe");
softAssert(By.id("email")).hasValue("john@example.com");
softAssert(By.id("status-badge")).hasText("Active");

// Test continues even if username or email fails.
// Framework flushes and reports all failures at test completion.
```

### 2. Fluent `.softly()` Modifier

```java
assertThat(By.id("header")).softly().hasText("Dashboard");
assertThat(By.id("avatar")).softly().isVisible();
```

### 3. Via `softAssert().assertThat(locator)`

```java
softAssert().assertThat(By.id("total")).hasText("$100.00");
```

You can still use raw boolean soft assertions when needed:

```java
softAssert().that(items.size() > 0, "Item list should not be empty");
```

---

## Semantic AI Assertions (`satisfiesAi` & `violatesAi`)

For complex page states, dynamic messages, or visual flows where exact string matching is brittle, TestFly provides LLM-evaluated semantic assertions:

```java
// Page-level semantic check
assertThatPage().satisfiesAi("Shopping cart summary displays total price and at least 1 item");
assertThatPage().violatesAi("Error banner, access denied, or 500 server notice");

// Convenience shortcut in BaseTest/BasePage
assertWithAi("The user profile shows active subscription status");

// Element-level semantic check
assertThat(find(".status-pill")).satisfiesAi("Indicates a successful payment");
```

For full details, anti-throttle guarantees, and examples, see the [Agentic Testing & Autonomous AI Guide](../ai/agentic-testing#2-semantic-assertions-satisfiesai--violatesai).

---

## Assertion Boundary: Primitive Data Assertions

TestFly's `assertThat()` is intentionally specialized for **Web & DOM elements** with automatic retries and polling.

For standard Java primitive data checks (e.g. `assertEquals(user.getAge(), 30)` or list size assertions), we recommend using **AssertJ** or your test runner's built-in assertions (`TestNG` / `JUnit 5`) alongside TestFly. TestFly intentionally does not reinvent general-purpose assertion libraries to prevent API bloat and keep your test architecture clean and modular.

