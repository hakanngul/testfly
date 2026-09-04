---
description: "Real-world test automation example on SauceDemo using modern TestFly web-first assertions, soft assertions, and Gemini AI failure analysis."
id: saucedemo-assertions-example
title: "Recipe: SauceDemo Assertions & AI Demo"
sidebar_label: SauceDemo Assertions
---

# Recipe: SauceDemo Assertions & AI Demo

This recipe demonstrates how to write clean, maintainable web tests against [SauceDemo](https://www.saucedemo.com/) using TestFly's modern assertion ecosystem and AI failure analysis.

```java
import io.testfly.test.BaseTest;
import org.openqa.selenium.By;
import org.testng.annotations.Test;
import java.time.Duration;

public class SauceDemoAssertionsExampleTest extends BaseTest {

    private static final String SAUCE_DEMO_URL = "https://www.saucedemo.com/";

    @Test
    public void testLoginPageAssertions() {
        open(SAUCE_DEMO_URL);

        // 1. Custom timeout and descriptive failure context
        assertThat(By.id("login-button"))
                .as("Login button visibility on landing")
                .within(Duration.ofSeconds(5))
                .isVisible();

        // 2. State & property assertions
        assertThat(By.id("login-button"))
                .isEnabled()
                .hasValue("Login")
                .hasCssValue("cursor", "pointer");

        // 3. Attribute presence check
        assertThat(By.id("user-name"))
                .hasAttribute("placeholder");
    }

    @Test
    public void testInventoryWithSoftAssertions() {
        open(SAUCE_DEMO_URL);

        // Login
        find(By.id("user-name")).type("standard_user");
        find(By.id("password")).type("secret_sauce");
        find(By.id("login-button")).click();

        // Fluent soft assertions: verifies all fields and reports all failures at the end
        softAssert(By.className("title")).hasText("Products");
        softAssert(By.id("shopping_cart_container")).isVisible();
        softAssert(By.id("react-burger-menu-btn")).isEnabled();

        // Count items
        assertThat(By.className("inventory_item")).count(6);
    }
}
```

---

## AI Failure Analysis with Google Gemini

When a test fails, TestFly can automatically analyze the root cause and suggest fixes using Google Gemini.

Configure `testfly.yml`:

```yaml
ai:
  failureAnalysis: true
  provider: gemini
  apiKey: ${GEMINI_API_KEY}
  model: gemini-2.0-flash
  language: en
```

On test failure, TestFly sends the error message, stack trace, and execution steps to Gemini, rendering an actionable root-cause report in your HTML test report:

```markdown
**Root Cause:** The locator By.id("checkout-btn") timed out after 10s because the shopping cart was empty.
**Suggested Fix:**
- Add an item to the cart before clicking checkout.
- Verify whether the button ID changed to [data-test="checkout"].
```
