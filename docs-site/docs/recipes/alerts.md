---
description: "Handle browser alerts, confirms, and prompts in Selenium: accept, dismiss, read text, and type into prompts with TestFly's wait-backed helpers."
id: alerts
title: Handle alerts
sidebar_label: Alerts
---

# Handle alerts

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
        acceptAlert();

        assertThat(find("#toast")).hasText("Account deleted");
    }
}
```

---

## Dismiss an alert

```java
find("#cancel-order").click();
dismissAlert();   // clicks Cancel — keeps the order
```

---

## Assert the alert text

```java
find("#submit").click();
String message = getAlertText();
softAssert().that(message.equals("Are you sure you want to submit?"), "Alert text should match");
acceptAlert();
```

Or accept and capture the text in one step:

```java
String message = getAndAcceptAlert();
softAssert().that(message.equals("Item added to cart"), "Alert text should match");
```

---

## Type into a prompt

```java
find("#rename").click();
typeInAlert("new-name");   // types and clicks OK
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
