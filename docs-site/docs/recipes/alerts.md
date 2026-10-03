---
description: "Handle browser alerts, confirms, and prompts in Selenium: accept, dismiss, read text, and type into prompts with TestFly's wait-backed helpers."
id: alerts
title: Handle alerts
sidebar_label: Alerts
---

# Handle alerts

The snippets below run in `BaseTest` using `getWait().until(alertIsPresent())`. The short `acceptAlert`/`dismissAlert` helpers are protected `BasePage` methods; use them inside a page object.

Browser alerts (`alert()`, `confirm()`, `prompt()`) block the WebDriver command queue. You cannot interact with the page until the alert is dismissed. TestFly's `BasePage` helpers wait for the alert to appear, then accept, dismiss, read, or type into it — all in one call.

---

## Accept an alert

```java title="DeleteTest.java"
import io.testfly.test.BaseTest;
import org.testng.annotations.Test;

public class DeleteTest extends BaseTest {

    @Test
    public void deleteAccountShowsConfirmation() {
        open("/account");
        find("#delete-account").click();

        // Clicks OK on the browser confirm()
        getWait().until(org.openqa.selenium.support.ui.ExpectedConditions.alertIsPresent()).accept();

        assertThat(find("#toast")).hasText("Account deleted");
    }
}
```

---

## Dismiss an alert

```java
find("#cancel-order").click();
getWait().until(org.openqa.selenium.support.ui.ExpectedConditions.alertIsPresent()).dismiss();   // clicks Cancel — keeps the order
```

---

## Assert the alert text

```java
find("#submit").click();
org.openqa.selenium.Alert alert = getWait().until(
    org.openqa.selenium.support.ui.ExpectedConditions.alertIsPresent());
String message = alert.getText();
softAssert().that(message.equals("Are you sure you want to submit?"), "Alert text should match");
getWait().until(org.openqa.selenium.support.ui.ExpectedConditions.alertIsPresent()).accept();
```

Or accept and capture the text in one step:

```java
org.openqa.selenium.Alert alert = getWait().until(
    org.openqa.selenium.support.ui.ExpectedConditions.alertIsPresent());
String message = alert.getText();
alert.accept();
softAssert().that(message.equals("Item added to cart"), "Alert text should match");
```

---

## Type into a prompt

```java
find("#rename").click();
org.openqa.selenium.Alert alert = getWait().until(
    org.openqa.selenium.support.ui.ExpectedConditions.alertIsPresent());
alert.sendKeys("new-name");
alert.accept();   // types and clicks OK
```

---

## Page-object helper

Encapsulate the alert interaction in a page object so tests read at intent level:

```java title="AccountPage.java"
import io.testfly.test.BasePage;
import org.openqa.selenium.By;

public class AccountPage extends BasePage {

    private static final By DELETE_BUTTON = By.id("delete-account");

    public void deleteAccount() {
        click(DELETE_BUTTON);
        acceptAlert();
    }

    public String confirmTextThenAccept() {
        click(DELETE_BUTTON);
        return getAndAcceptAlert();
    }
}
```

---

## What if the alert is unexpected?

If an alert appears but your test does not expect it, every subsequent WebDriver command will throw `UnhandledAlertException`. The framework's failure handling captures a screenshot, but the alert itself blocks further interaction.

To make tests robust:

- Always trigger and handle alerts in the same page-object method.
- Use `dismissAlert()` in a cleanup hook if a test is known to leave stray alerts.
- Avoid JavaScript that calls `alert()` for non-blocking notifications — use in-page toasts instead.

---

**Deeper reference:** [BasePage](/docs/guides/base-page) — all alert, hover, scroll, and JavaScript helpers.
