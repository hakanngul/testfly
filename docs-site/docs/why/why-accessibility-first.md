---
description: "Why accessibility-first locators? Tests that target roles, labels, and visible text survive CSS and DOM refactors, fail with meaningful messages, and mirror how real users experience the page."
id: why-accessibility-first
title: Why accessibility-first locators?
sidebar_label: Why accessibility-first?
sidebar_position: 4
---

# Why accessibility-first locators?

The most expensive part of a UI test suite is not writing it — **it's keeping it alive** while the application changes. Every CSS class rename, every layout shuffle, every framework migration breaks tests that pin themselves to implementation details.

Accessibility-first locators change the target from *how the page is built* to *what the page means*. They ask the same question a screen reader or a human user asks: "Where is the button labeled 'Submit'?" instead of "Where is the element with class `.btn-primary`?"

---

## The refactor problem

A typical Selenium test looks like this:

```java title="Before"
click(By.cssSelector(".login-form .btn-primary"));
```

Then the design system changes `.btn-primary` to `.btn--brand`, or the login form is rebuilt in a modal, or a button becomes a link. The test fails — not because the feature is broken, but because the locator was coupled to structure.

With accessibility-first locators:

```java title="After"
click(getByRole(Role.BUTTON).withName("Sign in"));
```

The button can move, change CSS, or change tag, and the test still finds it — as long as it still presents itself as a button named "Sign in" to the accessibility tree.

---

## What "accessibility-first" means in TestFly

TestFly exposes the same locator family popularized by Playwright and modern testing tools:

| Locator | Targets |
|---|---|
| `getByRole(Role.BUTTON)` | ARIA role — button, link, heading, textbox, checkbox |
| `getByLabel("Password")` | Form control associated with a `<label>` or `aria-label` |
| `getByText("Add to cart")` | Visible text content |
| `getByPlaceholder("Search…")` | Input placeholder |
| `getByTestId("checkout")` | `data-testid` attribute — the one implementation-detail escape hatch |
| `getByAltText("Product photo")` | Image alt text |

These locators are not a replacement for `By.cssSelector` or `By.xpath`; they are the **first choice**, and raw Selenium locators remain available when you genuinely need them.

---

## Failures become readable

A failure on `By.cssSelector(".cart .btn")` tells you:

```
NoSuchElementException: no such element: .cart .btn
```

A failure on `getByRole(Role.BUTTON).withName("Add to cart")` tells you:

```
Locator could not find a button with accessible name "Add to cart"
```

The second message is actionable. It points at intent, not markup.

---

## Better tests, better accessibility

Accessibility-first locators create a virtuous cycle:

1. Tests rely on the accessibility tree.
2. The accessibility tree is only correct when the UI is semantically meaningful.
3. Teams therefore keep labels, roles, and names correct.
4. The application becomes more usable for screen-reader users and keyboard navigators as a side effect.

In other words, the same discipline that makes tests stable also makes the product more inclusive.

---

## When to use `data-testid`

`getByTestId` is intentionally the fallback, not the default. Use it when:

- The visible text or role would make the test brittle (e.g., translated strings).
- The element has no accessible name or role by design.
- You are testing a component in isolation where semantic markup is not available.

Avoid using `data-testid` for every element. It is still an implementation detail — just a stable one.

---

## Next steps

- [Semantic Locators](/docs/guides/semantic-locators) — full API reference for `getByRole`, `getByLabel`, and the rest
- [Why not plain Selenium?](/docs/why/why-not-plain-selenium) — the cost of boilerplate-locator tests
- [Smart Locator](/docs/guides/smart-locator) — fallback strategies when one locator is not enough
