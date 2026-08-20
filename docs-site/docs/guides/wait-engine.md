---
description: "Explicit waits in Selenium without Thread.sleep(): WaitEngine gives fluent, auto-configured waits driven by your testfly.yml timeout."
id: wait-engine
title: Selenium Waits (WaitEngine)
sidebar_label: WaitEngine
sidebar_position: 3
---

# WaitEngine

`WaitEngine` provides fluent explicit waits. It is pre-configured with the timeout from `testfly.yml` (`timeouts.explicit`) and is available in every `BasePage` via `getWait()`.

---

## Available methods

### Element visibility

```java
getWait().waitForVisible(By.id("modal"));
getWait().waitForInvisible(By.cssSelector(".spinner"));  // wait for loaders to disappear
```

### Clickability

```java
getWait().waitForClickable(By.id("submit"));
```

### Enabled / disabled

```java
getWait().waitForEnabled(By.id("submit"));   // ready to interact
getWait().waitForDisabled(By.id("submit"));  // button is greyed out
```

### Selected

```java
getWait().waitForSelected(By.id("terms"));   // checkbox or radio is checked
```

### Text content

```java
getWait().waitForText(By.cssSelector("h1"), "Welcome back");
```

### Attribute value

```java
getWait().waitForAttributeContains(By.id("status"), "class", "active");  // substring
getWait().waitForAttribute(By.id("status"), "aria-expanded", "true");    // exact match
```

### Text matches (regex)

```java
// Wait until the element's visible text matches a regular expression
getWait().waitForTextMatches(By.cssSelector(".total"), "\\$\\d+\\.\\d{2}");
```

### URL matches (regex)

```java
getWait().waitForUrlContains("/orders");            // substring
getWait().waitForUrlMatches(".*/orders/\\d+");      // regular expression
```

### DOM staleness

```java
WebElement old = driver.findElement(By.id("row-1"));
getWait().waitForStaleness(old);  // wait for DOM replacement / AJAX reload
```

### Page load

```java
getWait().waitForPageLoad();  // waits until document.readyState === "complete"
```

### Windows and frames

```java
getWait().waitForNumberOfWindowsToBe(2);   // new tab opened
getWait().waitForFrameAvailableAndSwitchToIt(By.id("payment-iframe"));
```

### Minimum element count

Useful for lists and infinite-scroll feeds that grow asynchronously:

```java
getWait().waitForMinimumElementCount(By.cssSelector(".product-card"), 10);
```

### Custom condition

```java
// Escape hatch — pass any ExpectedCondition
getWait().wait(ExpectedConditions.numberOfWindowsToBe(2));
```

---

## Timeout override

Use a custom timeout for a single wait without changing the global config:

```java
getWait(30).waitForVisible(By.id("slow-element"));  // 30-second timeout
```

---

## Configuration

```yaml title="testfly.yml"
timeouts:
  explicit: 10   # seconds — default for all WaitEngine calls
  pageLoad: 30   # seconds — browser page load timeout
```

---

## Anti-patterns to avoid

```java
// ❌ never do this
Thread.sleep(3000);

// ✅ do this instead
getWait().waitForVisible(By.id("result"));
```

```java
// ❌ raw WebDriverWait — bypasses framework timeout config
new WebDriverWait(driver, Duration.ofSeconds(10))
    .until(ExpectedConditions.visibilityOf(...));

// ✅ use getWait() — reads timeout from config
getWait().waitForVisible(By.id("result"));
```
