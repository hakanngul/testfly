# Waiting, assertions, and healing

## Automatic waiting

Locator resolution polls the full chain and retries stale or late DOM matches. Terminal behavior is specific:

- `click()` waits for a clickable element.
- `clear`, `robustClear`, `type`, `append`, `getText`, `getAttribute`, `hover`, `scrollIntoView`, `jsClick`, and `element` wait for visibility.
- `isVisible`, `isHidden`, `isEnabled`, `count`, and `elements` resolve immediately. Use them for observation, not synchronization.
- `inputValue()` resolves the input and reads its `value` attribute.

Do not add a wait before an action or assertion that already expresses the same condition. Add an explicit wait for application transitions such as URL change, a loading indicator disappearing, window creation, frame availability, or a custom browser condition.

## Explicit waits

Use static `WaitEngine` methods for TestFly conditions, for example:

```java
WaitEngine.waitForInvisible(By.cssSelector("[data-testid='spinner']"));
WaitEngine.waitForMinimumCount(By.cssSelector("[data-testid='result']"), 3);
WaitEngine.waitForPageLoad();
```

Verified methods also cover visible/clickable elements, staleness, enabled/disabled/selected state, text and attributes, title/URL, window count, frames, Angular stability, React hydration, alerts, and generic Selenium `ExpectedCondition<T>`.

`getWait()` returns Selenium `WebDriverWait`:

```java
getWait().until(ExpectedConditions.urlContains("/dashboard"));
```

Never write `getWait().waitForVisible(...)`. Avoid `waitMillis`, `waitSeconds`, and `Thread.sleep`; they are fixed delays rather than synchronization.

## Retrying assertions

Use `assertThat(Locator)` or `assertThat(By)` for element state and `assertThatPage()` for page state. Locator assertions automatically retry until `timeouts.explicit`, with optional `.within(Duration)`/`.within(int)` and `.as(...)`:

```java
assertThat(page.heading()).hasText("Welcome");
assertThat(page.error()).within(Duration.ofSeconds(3)).isVisible();
assertThat(page.error()).containsText("Invalid credentials");
assertThat(page.rememberMe()).isChecked();
assertThat(page.spinner()).isHidden();
assertThatPage().urlContains("/dashboard");
```

Element assertions include visible/hidden, enabled/disabled, checked, exact/containing text, value, attribute, CSS value, focus, class, and count. Page assertions cover title and URL equality/containment/regex.

For multiple independent diagnostics use `softAssert()`/`SoftAssertionCollector` or `.softly()` only when continuing after one failure is valuable. TestFly lifecycle flushes and clears thread-local soft assertions; do not store collectors statically. Prefer hard assertions for prerequisites.

## Exact self-healing boundary

Self-healing is optional and feature/configuration-gated (`locators.selfHealing` and AI provider settings where AI healing is desired). After normal Locator polling times out, TestFly can try cached, deterministic, fuzzy, then configured AI alternatives.

Current eligibility is deliberately narrow:

- Eligible: an unrefined CSS or Selenium `By` root created through `find(...)`/`Locator.of(...)`.
- Ineligible: semantic locator kinds (`byRole`, text, label, placeholder, test id, alt text, title), direct `WebElement`, or locators refined by `filter`, `withText`, `within`, `nth`/`first`/`last`, or accessible-name constraints.

Immediate state queries may attempt healing immediately rather than after an auto-wait. Healing is never guaranteed and must not hide unstable selectors or replace explicit synchronization. Never claim that all semantic/chained locators heal.
