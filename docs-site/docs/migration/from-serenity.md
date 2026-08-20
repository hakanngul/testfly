---
description: "Migrate from Serenity BDD to TestFly: keep your Screenplay/Page Object patterns, replace the heavy reporting/lifecycle stack with lightweight YAML-driven config, and simplify CI integration."
id: from-serenity
title: Migrate from Serenity BDD
sidebar_label: From Serenity BDD
sidebar_position: 5
---

# Migrate from Serenity BDD

Serenity BDD and TestFly both sit on top of Selenium and aim to produce readable, reportable tests. Serenity leans heavily into BDD, Screenplay, and its own detailed living documentation. TestFly is lighter: it gives you the same readable tests and solid reporting, but with less annotation ceremony, a single YAML config file, and a smaller dependency footprint.

---

## What's familiar

### Page Objects / Screenplay

If you use Serenity Page Objects, the move is straightforward:

| Serenity | TestFly |
|---|---|
| `PageObject` | `BasePage` |
| `@FindBy(id = "email")` | `By.id("email")` field or inline `find("#email")` |
| `element(email).type("...")` | `type(EMAIL, "...")` or `find("#email").type("...")` |
| `WebElementFacade` | `Locator` / `WebElement` via `find(...)` |
| `Assert.assertThat(...)` with Serenity matchers | `assertThat(find(...)).hasText(...)` |

### Step reporting

Serenity records every `@Step` method in its report. TestFly uses `StepLogger` for the same purpose:

```java title="Serenity"
@Step("Enter credentials")
public void entersCredentials(String user, String pass) { ... }
```

```java title="TestFly"
StepLogger.step("Enter credentials");
find("#email").type(user);
```

Both produce a human-readable timeline in the HTML report.

### BDD / Cucumber

Serenity's Cucumber integration is a major draw. TestFly has a Cucumber bridge too:

```java
public class MySteps extends BaseCucumberSteps { ... }
```

See [Cucumber](/docs/cucumber) for the full setup.

---

## What's different

### Configuration model

Serenity uses `serenity.conf` / `serenity.properties` plus many JVM properties. TestFly uses a single `testfly.yml`:

```yaml title="testfly.yml"
browser:
  name: chrome
  headless: false

execution:
  baseUrl: https://your-app.com
  parallel: methods
  threadCount: 4

timeouts:
  explicit: 10

retry:
  enabled: true
  maxAttempts: 2
```

### Driver lifecycle

Serenity manages drivers through its own `WebDriverManager` / `WebDriverFacade`. TestFly uses `DriverManager` with thread-local isolation:

```java
protected WebDriver getDriver() { ... }   // from BaseTest / BasePage
```

No `@Managed` annotations, no `PageFactory`, no driver field injection.

### Assertions

Serenity wraps Hamcrest/Fest assertions and adds auto-waiting. TestFly provides `LocatorAssert`:

```java title="Serenity"
loginButton.shouldBeVisible();
loginButton.shouldContainText("Sign in");
```

```java title="TestFly"
assertThat(find("#login")).isVisible();
assertThat(find("#login")).hasText("Sign in");
```

### Reporting philosophy

Serenity generates very detailed living documentation. TestFly generates a focused HTML dashboard:

- Pass-rate gauge and suite summary
- Per-test timeline with screenshots
- Flakiness radar
- Retry badges
- JUnit XML for CI ingestion
- Optional Allure / Slack / Teams / ReportPortal adapters

If your organisation depends on Serenity's narrative living-documentation reports, TestFly's report is intentionally simpler. Evaluate whether the simplified format meets stakeholder needs before migrating.

---

## Migration checklist

1. **Replace dependencies**
   - Remove `net.serenity-bdd:*` artifacts
   - Add `io.testfly:testfly`

2. **Move configuration**
   - Convert `serenity.conf` / `serenity.properties` to `testfly.yml`
   - `webdriver.driver` → `browser.name`
   - `serenity.take.screenshots` → screenshot config is automatic on failure
   - `serenity.timeout` → `timeouts.explicit`
   - `serenity.restart.browser.for.each` → `browser.lifecycle`

3. **Update page objects**
   - Extend `BasePage` instead of `PageObject`
   - Replace `@FindBy` fields with `By` constants or inline `find(...)` calls
   - Use `BasePage` helpers: `click(By)`, `type(By, String)`, `select(By, String)`

4. **Replace Serenity steps**
   - `@Step` annotated methods → `StepLogger.step("...")` calls
   - Or keep methods and add `StepLogger.step(...)` at their entry points

5. **Update assertions**
   - Replace Serenity `shouldBeVisible`, `shouldContainText`, etc. with `assertThat(find(...)).*`

6. **Update test base**
   - Replace `SerenityRunner` / `SerenityJUnit5Extension` with TestFly's `BaseTest` or `BaseJUnit5Test`

7. **Cucumber steps (if used)**
   - Replace `Serenity Cucumber` step definitions with `BaseCucumberSteps`

---

## Side-by-side example

### Serenity Page Object

```java
public class LoginPage extends PageObject {

    @FindBy(id = "email")
    private WebElementFacade email;

    @FindBy(id = "password")
    private WebElementFacade password;

    @FindBy(id = "login")
    private WebElementFacade loginButton;

    @Step("Login as {0}")
    public void login(String user, String pass) {
        email.type(user);
        password.type(pass);
        loginButton.click();
    }
}
```

### TestFly Page Object

```java
public class LoginPage extends BasePage {

    private static final By EMAIL = By.id("email");
    private static final By PASSWORD = By.id("password");
    private static final By LOGIN = By.id("login");

    public void login(String user, String pass) {
        StepLogger.step("Login as " + user);
        type(EMAIL, user);
        type(PASSWORD, pass);
        click(LOGIN);
    }
}
```

---

## When to stay with Serenity

Serenity is a strong fit when:

- You rely on its rich living-documentation reports for stakeholder sign-off.
- Your team is fully committed to Screenplay pattern and `@Step`-driven BDD.
- You have a large existing investment in Serenity-specific annotations and plugins.

TestFly is a better fit when you want a lighter, YAML-configured, Selenium-native framework with modern locators and simpler CI integration.

---

## Next steps

- [Getting Started](/docs/getting-started) — first TestFly test in 5 minutes
- [BasePage](/docs/guides/base-page) — page-object helpers
- [Step Logging](/docs/guides/step-logging) — named steps and screenshots
- [Cucumber](/docs/cucumber) — BDD bridge in TestFly
- [Configuration Reference](/docs/configuration) — full `testfly.yml`
