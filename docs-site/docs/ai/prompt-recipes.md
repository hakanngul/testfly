---
id: prompt-recipes
title: AI Prompt Recipes for TestFly
sidebar_label: AI Prompt Recipes
sidebar_position: 5
description: Ready-to-use prompt templates for generating TestFly Page Objects, TestNG tests, JUnit 5 tests, Cucumber BDD, and self-healing tests with AI.
---

# AI Prompt Recipes for TestFly

Use these tested prompt templates with **JetBrains AI Assistant**, **Claude Code**, or **GitHub Copilot** to produce clean, compilable, and idiomatic TestFly Java test automation code.

---

## The Golden Rule for AI Prompting

> **Always instruct the AI to drive the browser first.**
> Ask the AI assistant to navigate to the target URL, inspect the real DOM elements via TestFly MCP tools, and call the `generate_*` codegen tools. Never ask the AI to write Java locators from imagination.

---

## Recipe 1: Page Object Model (`BasePage`)

Use this prompt to generate a Page Object adhering to TestFly v1.0.0 conventions:

```text
Navigate to https://www.saucedemo.com.
Inspect the login form elements using TestFly MCP tools (preferring accessibility attributes).
Generate a TestFly Page Object named 'LoginPage' in package 'io.testfly.examples.pages' extending BasePage.
Include fluent action methods for entering username, password, and clicking the login button.
```

### Emitted Code Example
```java
package io.testfly.examples.pages;

import io.testfly.core.BasePage;
import io.testfly.locators.Role;
import org.openqa.selenium.By;

public class LoginPage extends BasePage {

    public LoginPage open() {
        super.open("https://www.saucedemo.com");
        return this;
    }

    public LoginPage enterUsername(String username) {
        find(getByPlaceholder("Username")).fill(username);
        return this;
    }

    public LoginPage enterPassword(String password) {
        find(getByPlaceholder("Password")).fill(password);
        return this;
    }

    public void clickLogin() {
        find(getByRole(Role.BUTTON, "Login")).click();
    }
}
```

---

## Recipe 2: TestNG Test (`BaseTest`)

Use this prompt to create an automated TestNG test:

```text
Write a TestFly TestNG test for SauceDemo login:
1. Extend BaseTest in package io.testfly.examples.testng.
2. Log in with username 'standard_user' and password 'secret_sauce'.
3. Assert that the page title is 'Swag Labs' using assertThat(getDriver()).hasTitle(...).
4. Assert that the products header has text 'Products' using assertThat(...).hasText(...).
```

### Emitted Code Example
```java
package io.testfly.examples.testng;

import io.testfly.core.BaseTest;
import io.testfly.examples.pages.LoginPage;
import org.openqa.selenium.By;
import org.testng.annotations.Test;

import static io.testfly.assertion.SeleniumAssert.assertThat;

public class SauceDemoLoginTest extends BaseTest {

    @Test(description = "Verify successful login to SauceDemo inventory")
    public void testSuccessfulLogin() {
        new LoginPage()
            .open()
            .enterUsername("standard_user")
            .enterPassword("secret_sauce")
            .clickLogin();

        // Auto-waiting page assertion
        assertThat(getDriver()).hasTitle("Swag Labs");
        assertThat(getDriver()).urlContains("/inventory.html");

        // Element assertion
        assertThat(By.cssSelector(".title")).hasText("Products");
    }
}
```

---

## Recipe 3: JUnit 5 Test (`BaseJUnit5Test`)

Use this prompt to generate JUnit 5 tests:

```text
Generate a JUnit 5 test extending BaseJUnit5Test for SauceDemo login verification in package io.testfly.examples.junit5.
Use @Test and @DisplayName annotations, and verify that the shopping cart icon is visible upon login.
```

---

## Recipe 4: Cucumber BDD Feature & Steps

Use this prompt to generate complete Gherkin specifications and glue code:

```text
Generate a Cucumber BDD suite for SauceDemo login:
1. Create a Gherkin .feature file for valid login.
2. Create step definitions extending BaseCucumberSteps in package io.testfly.examples.cucumber.steps.
3. Create a TestNG Cucumber runner class extending BaseCucumberTest.
```

---

## Recipe 5: Self-Healing & Diagnostic Analysis

If a locator breaks or a test fails unexpectedly:

```text
The test failed with NoSuchElementException on '#login-btn'.
Navigate to the page, inspect the live DOM to find what changed, and propose a self-healing TestFly locator using getByRole or getByTestId.
```
