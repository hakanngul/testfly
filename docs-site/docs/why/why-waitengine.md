---
description: "Why WaitEngine? Because Thread.sleep() and raw WebDriverWait are fragile, noisy, and slow. TestFly's WaitEngine centralizes explicit waits with config-driven timeouts and self-healing fallback."
id: why-waitengine
title: Why WaitEngine?
sidebar_label: Why WaitEngine?
sidebar_position: 5
---

# Why WaitEngine?

The single biggest source of flaky Selenium tests is **timing**. The page is not ready, the element is not clickable yet, the AJAX call has not returned, or the spinner has not disappeared. Teams usually solve this in one of two wrong ways:

1. `Thread.sleep(3000)` — simple, predictable, and reliably slow.
2. A scattered collection of `WebDriverWait` helpers copied into every page object.

`WaitEngine` is TestFly's answer: one centralized, config-driven, explicit-wait API that every test and page object uses.

---

## What is wrong with `Thread.sleep()`

```java title="The wrong way"
Thread.sleep(3000); // hope the page is ready
driver.findElement(By.id("submit")).click();
```

Problems:

- **Slow on fast environments.** You always wait the full 3 seconds even when the element is ready in 100ms.
- **Fast on slow environments.** CI containers, cloud browsers, or busy grids may need 4 seconds, and the test fails.
- **Hides real bugs.** A sleep masks failures that would be caught by a proper wait condition.
- **Scales badly.** Once a suite has 100 sleeps, total runtime balloons and flakes never go away.

---

## What is wrong with scattered `WebDriverWait`

```java title="A little better, still messy"
new WebDriverWait(driver, Duration.ofSeconds(10))
    .until(ExpectedConditions.elementToBeClickable(By.id("submit")))
    .click();
```

This is correct in isolation, but when every page object invents its own timeout, teams end up with:

- Inconsistent timeouts across the suite
- Duplicated wait logic
- Timeouts that are hard to tune globally
- Tests that bypass the framework's config

---

## How WaitEngine fixes it

```java title="The TestFly way"
getWait().waitForClickable(By.id("submit"));
```

One line:

- Reads the timeout from `testfly.yml` (`timeouts.explicit`)
- Polls the condition until it is true
- Fails with a clear message if the condition never becomes true
- Triggers self-healing fallback if the locator fails
- Is available everywhere `BasePage` and `BaseTest` reach

---

## Conditions you do not have to write yourself

`WaitEngine` ships with common conditions out of the box:

| Condition | Use case |
|---|---|
| `waitForVisible(By)` | Element appears |
| `waitForInvisible(By)` | Loader/spinner disappears |
| `waitForClickable(By)` | Element is enabled and not obscured |
| `waitForText(By, String)` | Exact text appears |
| `waitForTextMatches(By, String)` | Text matches a regex |
| `waitForAttribute(By, String, String)` | Attribute equals a value |
| `waitForAttributeContains(...)` | Attribute contains a substring |
| `waitForUrlContains(String)` / `waitForUrlMatches(String)` | Navigation completed |
| `waitForPageLoad()` | `document.readyState === "complete"` |
| `waitForStaleness(WebElement)` | Old DOM node replaced by AJAX |
| `waitForAlert()` | JavaScript alert present |

Need something custom? The escape hatch is always there:

```java
getWait().wait(ExpectedConditions.numberOfWindowsToBe(2));
```

---

## Config-driven, not hard-coded

```yaml title="testfly.yml"
timeouts:
  explicit: 10   # seconds — used by every WaitEngine call
```

Change one number, and the entire suite's wait behavior changes. No page-object edits required.

For a single slow operation, override without touching config:

```java
getWait(30).waitForVisible(By.id("heavy-report"));
```

---

## The real payoff

A suite built on `WaitEngine` is:

- **Faster** — waits only as long as necessary
- **More stable** — polls for real conditions instead of guessing
- **Easier to maintain** — timeouts live in one place
- **More honest** — failures mean the condition was never satisfied, not that the sleep was too short

---

## Next steps

- [WaitEngine Guide](/docs/guides/wait-engine) — full method reference and examples
- [Why not plain Selenium?](/docs/why/why-not-plain-selenium) — the broader boilerplate story
- [Retry](/docs/guides/retry) — what to do when a wait is not enough
