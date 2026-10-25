---
id: assertions
title: Assertions
sidebar_position: 5
description: "Retrying mobile assertions with expect(locator): state, text, value and count matchers, negation, custom timeouts and messages, expectPoll for any value, and soft assertions."
---

# Assertions

Mobile assertions are **web-first**: they read the screen again until the condition holds or the timeout (`mobile.timeouts.actionMs`, default 5000 ms) runs out. A screen that is still loading or animating does not fail the test, and you need no sleeps.

```java
import static io.testfly.mobile.MobileAssertions.expect;   // already available in BaseMobileTest, MobilePage and MobileSupport

expect(screen().getByText("Welcome back")).toBeVisible();
expect(screen().getByTestId("balance")).toHaveText(Pattern.compile("^\\d+,\\d{2} ₺$"));
expect(screen().getByRole(MobileRole.LISTITEM)).toHaveCount(3);
```

A failed assertion throws `MobileAssertionError` with the last reason and the elements seen:

```
expect(getByTestId("balance")).toHaveText(/^\d+,\d{2} ₺$/) failed after 5000 ms: text differs: android.widget.TextView id="balance" text="--" [40,300 600x80]
```

## Matchers

| Matcher | Passes when |
|---|---|
| `toBeVisible()` | exactly one match is visible |
| `toBeHidden()` | no match is visible (absent counts as hidden) |
| `toBeEnabled()`, `toBeDisabled()` | the one match is enabled / disabled |
| `toBeChecked()` | the one match is checked (checkbox, radio, switch) |
| `toBeSelected()` | the one match is selected (tab, segment, list row) |
| `toBeFocused()` | the one match has the input focus |
| `toHaveText("Welcome")` | the displayed text (text, else label, else value) equals the string |
| `toHaveText("welcome", true)` | the same, ignoring case and normalizing whitespace (runs of spaces, leading/trailing blanks) |
| `toHaveText(Pattern)` | the displayed text matches the regular expression (`find()`) |
| `toContainText("back")` | the displayed text contains the string, case-insensitive |
| `toHaveValue("ada@example.com")` | the `value` equals the string (text fields, sliders) |
| `toBeEmpty()` | the element has no text and no value; an iOS field that only shows its placeholder counts as empty |
| `toHaveCount(n)` | the query matches exactly `n` elements |

Single-element matchers apply [strict mode](locators#strict-mode); `toBeHidden()` and `toHaveCount(n)` look at every match.

## Negation, timeout and message

```java
expect(screen().getByText("Loading")).not().toBeVisible();          // same as toBeHidden()
expect(screen().getByTestId("username")).not().toBeEmpty();
expect(screen().getByText("Report ready")).withTimeout(Duration.ofSeconds(30)).toBeVisible();
expect(screen().getByTestId("total"), "cart total after the coupon").toHaveText("90,00 ₺");
```

- `not()` negates the next matcher. `not().toBeVisible()` passes when nothing visible matches.
- `withTimeout(Duration)` changes the retry budget of this assertion only. A locator's own `withTimeout` is used by default.
- `expect(locator, message)` puts `message` on the first line of a failure.

In page objects, keep assertions in intent methods (`shouldShowBalance()`), so tests stay readable.

## Retrying any value: `expectPoll` {#expect-poll}

`expectPoll(supplier)` retries any Java value, for checks that are not about one element: device state, a count from several locators, a backend call.

```java
import static io.testfly.mobile.MobileAssertions.expectPoll;

device().setOrientation(MobileOrientation.LANDSCAPE);
expectPoll(device()::orientation).toBe(MobileOrientation.LANDSCAPE);

expectPoll(() -> screen().getByRole(MobileRole.LISTITEM).count())
        .withTimeout(Duration.ofSeconds(10))
        .matches(n -> n >= 20, "at least 20 rows after scrolling");
```

- `toBe(expected)` compares with `equals`; `matches(predicate, description)` takes any condition.
- The default timeout is 5 seconds; `withTimeout(Duration)` changes it. The supplier is called every 200 ms.
- An exception from the supplier counts as a failed attempt; the failure message shows the last value or the last error.

## Soft assertions {#soft-assertions}

A soft assertion records a failure and lets the test continue, so one run reports every wrong field of a screen. Create a collector with `MobileAssertions.softly()`, assert through it, and call `assertAll()` at the end:

```java
MobileSoftAssertions soft = MobileAssertions.softly();
soft.expect(profile.name).toHaveText("Ada Lovelace");
soft.expect(profile.email).toHaveText("ada@example.com");
soft.expect(profile.badge, "premium badge").toBeVisible();
soft.assertAll();
```

- `assertAll()` throws one `MobileAssertionError` that lists every failure (each also attached as a suppressed exception), then clears the list. With no failures it does nothing.
- `failures()` returns the failures collected so far.
- A collector is not thread-safe: create one per test.
- Forgetting `assertAll()` hides the failures; call it in the test, not in a page object.

The `softAssert()` of the base classes is TestFly's web/general soft assertion API; use `MobileAssertions.softly()` for mobile locators.
