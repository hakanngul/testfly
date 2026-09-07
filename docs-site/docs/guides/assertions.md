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
2. **Logs each step** automatically into [`StepLogger`](file:///src/main/java/io/testfly/steps/StepLogger.java) and surfaces it in the HTML report.
3. **Works seamlessly** with both Selenium [`By`](file:///src/main/java/io/testfly/assertion/SeleniumAssert.java#L35) and fluent [`Locator`](file:///src/main/java/io/testfly/assertion/SeleniumAssert.java#L43) instances (`$()`, `getByRole()`, etc.).

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

### Element Count

```java
assertThat(By.cssSelector("table tbody tr")).count(5);
assertThat($(".card")).count(3);
```

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

