---
description: "Explicit waits in Selenium without Thread.sleep(): WaitEngine gives fluent, auto-configured waits driven by your testfly.yml timeout."
id: wait-engine
title: Selenium Waits (WaitEngine)
sidebar_label: WaitEngine
sidebar_position: 3
---

# WaitEngine

`WaitEngine` is a static utility class available via `WaitEngine.waitForXxx(...)` calls. It is pre-configured with the timeout from `testfly.yml` (`timeouts.explicit`).

```java
import io.testfly.wait.WaitEngine;
```

---

## Available methods

### Element visibility

```java
WaitEngine.waitForVisible(By.id("modal"));
WaitEngine.waitForInvisible(By.cssSelector(".spinner"));  // wait for loaders to disappear
```

### Clickability

```java
WaitEngine.waitForClickable(By.id("submit"));
```

### Actionability

`Locator` actions (`click`, `hover`, `type`, `clear`, `append`) wait until the
element is ready for that interaction, like Playwright's actionability checks.
You can also call it directly:

```java
WaitEngine.waitForActionable(By.id("submit"));                       // CLICK
WaitEngine.waitForActionable(By.id("email"), ActionKind.EDIT);
```

| Check | `CLICK` | `HOVER` | `EDIT` |
|---|:-:|:-:|:-:|
| Visible | yes | yes | yes |
| Enabled | yes | no | yes |
| Stable (bounding box unchanged between two polls) | yes | yes | yes |
| Receives events (not covered by another element) | yes | yes | no |

The element is scrolled into view when its centre is off-screen or covered. If
it never becomes actionable, the failure names the element and the reason:

```
NotActionableException: Element <button#submit> is not actionable for CLICK after 10s:
obscured by <div.modal-backdrop>
```

When the page re-renders between resolving and acting, or an overlay appears
after the check, the locator chain is resolved again (up to three attempts).

Escape hatches, from least to most invasive:

- `find("#x").force().click()` skips the stable and not-covered checks. The
  element must still be visible and enabled.
- `find("#x").jsClick()` clicks through JavaScript, even when covered.
- `actionability.enabled: false` restores the previous waits (visible and enabled only).

The checks fail open: if the script cannot run, the action proceeds. They add
about one polling interval (`actionability.stabilityIntervalMs`, 100 ms) per
action.

Not covered: `BasePage.jsType`, `upload` (file inputs are often hidden) and
`withinFrame`.

### Enabled / disabled

```java
WaitEngine.waitForEnabled(By.id("submit"));   // ready to interact
WaitEngine.waitForDisabled(By.id("submit"));  // button is greyed out
```

### Selected

```java
WaitEngine.waitForSelected(By.id("terms"));   // checkbox or radio is checked
```

### Text content

```java
WaitEngine.waitForText(By.cssSelector("h1"), "Welcome back");
```

### Attribute value

```java
WaitEngine.waitForAttributeContains(By.id("status"), "class", "active");  // substring
WaitEngine.waitForAttribute(By.id("status"), "aria-expanded", "true");    // exact match
```

### Text matches (regex)

```java
// Wait until the element's visible text matches a regular expression
WaitEngine.waitForTextMatches(By.cssSelector(".total"), "\\$\\d+\\.\\d{2}");
```

### URL matches (regex)

```java
WaitEngine.waitForUrlContains("/orders");            // substring
WaitEngine.waitForUrlMatches(".*/orders/\\d+");      // regular expression
```

### DOM staleness

```java
WebElement old = getDriver().findElement(By.id("row-1"));
WaitEngine.waitForStaleness(old);  // wait for DOM replacement / AJAX reload
```

### Page load

```java
WaitEngine.waitForPageLoad();  // waits until document.readyState === "complete"
```

### Windows and frames

```java
WaitEngine.waitForNumberOfWindowsToBe(2);   // new tab opened
WaitEngine.waitForFrameAvailableAndSwitchToIt(By.id("payment-iframe"));
```

### Minimum element count

Useful for lists and infinite-scroll feeds that grow asynchronously:

```java
WaitEngine.waitForMinimumElementCount(By.cssSelector(".product-card"), 10);
```

### Custom condition

```java
// Escape hatch — pass any ExpectedCondition
WaitEngine.wait(ExpectedConditions.numberOfWindowsToBe(2));
```

---

## Custom timeout

`WaitEngine` always uses the global timeout from `testfly.yml`. If you need a one-off custom timeout, create a `WebDriverWait` directly:

```java
// Custom timeout — create a WebDriverWait directly
new WebDriverWait(getDriver(), Duration.ofSeconds(30))
    .until(ExpectedConditions.visibilityOfElementLocated(By.id("slow-element")));
```

---

## Configuration

```yaml title="testfly.yml"
timeouts:
  explicit: 10   # seconds — default for all WaitEngine calls
  pageLoad: 30   # seconds — browser page load timeout

actionability:
  enabled: true              # stable + not-covered checks before click/hover/type
  stabilityIntervalMs: 100   # polling interval while waiting for the element to settle
```

---

## Anti-patterns to avoid

```java
// ❌ never do this
Thread.sleep(3000);

// ✅ do this instead
WaitEngine.waitForVisible(By.id("result"));
```

```java
// ❌ raw WebDriverWait — bypasses framework timeout config
new WebDriverWait(getDriver(), Duration.ofSeconds(10))
    .until(ExpectedConditions.visibilityOfElementLocated(By.id("result")));

// ✅ use WaitEngine — reads timeout from config
WaitEngine.waitForVisible(By.id("result"));
```
