---
description: "Migrate from Selenide to TestFly: keep the fluent Selenium ergonomics you like while gaining framework-managed lifecycle, config-driven waits, accessibility-first locators, and enterprise reporting."
id: from-selenide
title: Migrate from Selenide
sidebar_label: From Selenide
sidebar_position: 4
---

# Migrate from Selenide

Selenide and TestFly share the same goal: **make Selenium tests concise and stable**. If your team already uses Selenide, most of the migration is a vocabulary change — fluent locators, automatic waits, and concise assertions map closely. TestFly adds framework-managed lifecycle, a single YAML config, accessibility-first locators, and built-in enterprise reporting.

---

## What's familiar

### Fluent element API

Selenide's `$` and `$$` have direct TestFly equivalents:

| Selenide | TestFly |
|---|---|
| `$("#login").click()` | `find("#login").click()` |
| `$("#email").setValue("a@b.com")` | `find("#email").type("a@b.com")` |
| `$(".msg").shouldHave(text("Welcome"))` | `assertThat(find(".msg")).hasText("Welcome")` |
| `$("button").shouldBe(visible)` | `assertThat(find("button")).isVisible()` |
| `$("#submit").shouldBe(enabled)` | `assertThat(find("#submit")).isEnabled()` |
| `$$('.item').first()` | `find(".item").nth(0)` |
| `$("[data-testid='x']")` | `getByTestId("x")` |

### Automatic waiting

Both frameworks wait automatically before interacting. In Selenide the timeout is global and implicit; in TestFly it lives in `testfly.yml` and is applied by `WaitEngine`:

```yaml title="testfly.yml"
timeouts:
  explicit: 10
```

```java
// Both frameworks auto-wait for clickable before clicking
find("#login").click();
```

### Page Objects

Selenide page objects extend `com.codeborne.selenide.SelenidePageObject` or are plain classes. TestFly page objects extend `BasePage`:

```java title="LoginPage.java"
import io.testfly.test.BasePage;
import org.openqa.selenium.By;

public class LoginPage extends BasePage {

    private static final By EMAIL = By.id("email");
    private static final By PASSWORD = By.id("password");
    private static final By SUBMIT = By.id("submit");

    public void login(String email, String password) {
        type(EMAIL, email);
        type(PASSWORD, password);
        click(SUBMIT);
    }
}
```

---

## What's different

### Driver lifecycle

Selenide manages its own static driver and configuration via `Configuration`. TestFly manages lifecycle per thread through `DriverManager`, driven by `testfly.yml`:

| Concern | Selenide | TestFly |
|---|---|---|
| Browser choice | `Configuration.browser = "chrome"` | `browser.name: chrome` in `testfly.yml` |
| Headless | `Configuration.headless = true` | `browser.headless: true` in `testfly.yml` |
| Base URL | `Configuration.baseUrl = "..."` | `execution.baseUrl: "...` in `testfly.yml` |
| Timeout | `Configuration.timeout = 4000` | `timeouts.explicit: 10` in `testfly.yml` |
| Parallel | TestNG/Surefire XML + Selenide static config | TestNG parallel + `execution.parallel`/`threadCount` |

TestFly's approach keeps environment-specific settings out of Java code.

### Assertions

Selenide's `shouldHave` / `shouldBe` are conditions on `SelenideElement`. TestFly's `assertThat(...)` returns a `LocatorAssert`:

```java title="Selenide"
$("#status").shouldHave(text("Active"));
$("#status").shouldBe(visible, Duration.ofSeconds(5));
```

```java title="TestFly"
assertThat(find("#status")).hasText("Active");
assertThat(find("#status"), 5).isVisible();   // 5-second override
```

### Accessibility-first locators

Selenide relies mostly on CSS / XPath. TestFly encourages `getByRole`, `getByLabel`, `getByText`, etc.:

```java
getByRole(Role.BUTTON).withName("Save").click();
getByLabel("Email address").type("a@b.com");
```

These survive CSS refactors and read closer to user intent.

### Reporting

Selenide captures screenshots and page source on failure. TestFly adds:

- Self-contained HTML report with pass-rate gauge and timeline
- JUnit XML output
- Flakiness analyzer
- Allure, Slack, Teams, and ReportPortal adapters
- Step logging with per-step screenshots

```java
StepLogger.step("Enter credentials");
find("#email").type("a@b.com");
```

---

## Migration checklist

1. **Replace dependency**
   - Remove `com.codeborne:selenide`
   - Add `io.testfly:testfly`

2. **Move configuration to `testfly.yml`**
   - `Configuration.browser` → `browser.name`
   - `Configuration.headless` → `browser.headless`
   - `Configuration.baseUrl` → `execution.baseUrl`
   - `Configuration.timeout` → `timeouts.explicit`

3. **Update page objects**
   - Extend `BasePage` instead of using Selenide page-object conventions
   - Replace `$("...")` with `find("...")` or `getByRole(...)`
   - Use `BasePage` helpers: `click(By)`, `type(By, String)`, `upload(By, String)`

4. **Update assertions**
   - Replace `.shouldHave(...)` / `.shouldBe(...)` with `assertThat(...).hasText(...)`, `.isVisible()`, `.isEnabled()`, etc.

5. **Delete Selenide static setup**
   - Remove `Configuration.*` calls from test base classes
   - Remove `Selenide.open(...)`, `Selenide.closeWebDriver()`, and driver imports

6. **Extend `BaseTest`**
   - Replace Selenide test base with `public class MyTest extends BaseTest`
   - Use `open()` to navigate to `execution.baseUrl`

7. **Add reporting if needed**
   - HTML report is automatic
   - Optional adapters can be enabled via config or SPI

---

## Side-by-side example

### Selenide

```java
public class LoginTest {

    @Test
    public void login() {
        open("/login");
        $("#email").setValue("admin@testfly.io");
        $("#password").setValue("secret");
        $("#login").click();
        $("h1").shouldHave(text("Dashboard"));
    }
}
```

### TestFly

```java
public class LoginTest extends BaseTest {

    @Test
    public void login() {
        open("/login");
        find("#email").type("admin@testfly.io");
        find("#password").type("secret");
        find("#login").click();
        assertThat(find("h1")).hasText("Dashboard");
    }
}
```

---

## When to stay with Selenide

Selenide is a mature, excellent choice. Stay with it if:

- Your whole codebase is already fluent Selenide and you have no reporting/lifecycle gaps.
- You prefer a single, statically configured tool.
- You don't need TestFly's accessibility-first locators, step logging, or built-in CI/enterprise adapters.

Choose TestFly when you want those additions without leaving the Selenium / Java / TestNG stack.

---

## Next steps

- [Getting Started](/docs/getting-started) — first TestFly test in 5 minutes
- [BasePage](/docs/guides/base-page) — page-object helpers
- [Semantic Locators](/docs/guides/semantic-locators) — `getByRole`, `getByLabel`, etc.
- [Configuration Reference](/docs/configuration) — full `testfly.yml`
