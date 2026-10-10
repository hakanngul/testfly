# Compile-checked framework examples

The Java snippets in this file are consumer-oriented and were compiled against the current SDK. They assume `execution.baseUrl` is configured and the application exposes the stated labels/roles/test ids.

## Shared Page Objects

```java
package example.web;

import io.testfly.locator.Locator;
import io.testfly.locator.Role;
import io.testfly.test.BasePage;
import io.testfly.wait.WaitEngine;
import org.openqa.selenium.By;

public final class LoginPage extends BasePage {
    private final Locator email = getByLabel("Email").exact();
    private final Locator password = getByLabel("Password").exact();
    private final Locator rememberMe = getByRole(Role.CHECKBOX, "Remember me").exact();
    private final Locator submit = getByRole(Role.BUTTON, "Sign in").exact();
    private final Locator error = getByTestId("login-error");

    public LoginPage openPage() {
        open("/login");
        submit.element();
        return this;
    }

    public LoginPage enterCredentials(String username, String secret) {
        email.type(username);
        password.type(secret);
        return this;
    }

    public LoginPage rememberMe(boolean desired) {
        if (rememberMe.element().isSelected() != desired) {
            rememberMe.click();
        }
        return this;
    }

    public LoginPage selectTenant(String visibleText) {
        selectByText(By.id("tenant"), visibleText);
        return this;
    }

    public DashboardPage submitValid() {
        submit.click();
        return new DashboardPage();
    }

    public LoginPage submitInvalid() {
        submit.click();
        WaitEngine.waitForInvisible(By.cssSelector("[data-testid='auth-spinner']"));
        return this;
    }

    public Locator error() { return error; }
    public Locator rememberMeControl() { return rememberMe; }
}
```

```java
package example.web;

import io.testfly.locator.Locator;
import io.testfly.locator.Role;
import io.testfly.test.BasePage;

public final class DashboardPage extends BasePage {
    private final Locator heading = getByRole(Role.HEADING, "Dashboard").exact();

    public Locator heading() { return heading; }
}
```

`type` clears before typing. The explicit spinner wait models an application transition; it is not duplicated before the already-waiting click or assertion. The native `<select>` uses BasePage's protected Selenium-backed helper.

## TestNG with data

```java
package example.web;

import io.testfly.test.BaseTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public final class LoginTest extends BaseTest {
    @DataProvider(name = "invalid-logins", parallel = true)
    public Object[][] invalidLogins() {
        return new Object[][] {
                {"unknown@example.test", "wrong"},
                {"", "missing-email"}
        };
    }

    @Test
    public void userCanSignIn() {
        DashboardPage dashboard = new LoginPage().openPage()
                .enterCredentials("ada@example.test", "env-provided-secret")
                .rememberMe(true)
                .selectTenant("Engineering")
                .submitValid();

        assertThat(dashboard.heading()).hasText("Dashboard");
        assertThatPage().urlContains("/dashboard");
    }

    @Test(dataProvider = "invalid-logins")
    public void invalidCredentialsAreRejected(String username, String password) {
        LoginPage page = new LoginPage().openPage()
                .enterCredentials(username, password)
                .submitInvalid();

        assertThat(page.error()).isVisible().containsText("Invalid credentials");
    }
}
```

Replace the illustrative secret with environment-backed configuration. Do not commit real credentials. Parallel data rows are safe only when backend test data is isolated too.

## JUnit 5

```java
package example.web;

import io.testfly.junit5.BaseJUnit5Test;
import org.junit.jupiter.api.Test;

public final class LoginJunitTest extends BaseJUnit5Test {
    @Test
    void invalidCredentialsAreRejected() {
        LoginPage page = new LoginPage().openPage()
                .enterCredentials("unknown@example.test", "wrong")
                .submitInvalid();

        assertThat(page.error()).isVisible().containsText("Invalid credentials");
    }
}
```

JUnit 5 is optional for consumers. Add `org.junit.jupiter:junit-jupiter-api` and a compatible engine. Standard `@ParameterizedTest` is usable when the consumer also adds `junit-jupiter-params`; TestFly does not provide a separate parameter source API. `@EnableTestFly` is the non-inheritance lifecycle alternative.

## Cucumber

Feature:

```gherkin
Feature: Login
  Scenario Outline: Invalid credentials are rejected
    Given the visitor is on the login page
    When they sign in as "<email>" with password "<password>"
    Then the login error contains "Invalid credentials"

    Examples:
      | email                | password |
      | unknown@example.test | wrong    |
      |                      | missing  |
```

Group Step Definition methods together first:

```java
package example.web.steps;

import example.web.LoginPage;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.testfly.cucumber.BaseCucumberSteps;

public final class LoginSteps extends BaseCucumberSteps {
    private LoginPage page;

    @Given("the visitor is on the login page")
    public void openLoginPage() {
        page = new LoginPage().openPage();
    }

    @When("they sign in as {string} with password {string}")
    public void signIn(String email, String password) {
        page.enterCredentials(email, password).submitInvalid();
    }

    @Then("the login error contains {string}")
    public void verifyError(String message) {
        assertThat(page.error()).isVisible().containsText(message);
    }
}
```

The required Page Object methods are the `openPage`, `enterCredentials`, `submitInvalid`, and `error` methods shown separately in `LoginPage` above. Consumers must add compatible `cucumber-java` and their runner dependency (for example `cucumber-testng`) and include both the project step package and `io.testfly.cucumber` in glue. Hooks, driver setup, teardown, and scenario cleanup remain framework-owned.
