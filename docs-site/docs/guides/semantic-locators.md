---
description: "Accessibility-first Selenium locators: Playwright-style getByRole, getByText, and getByLabel target the accessibility tree and survive CSS refactors."
id: semantic-locators
title: Accessibility-First Locators
sidebar_position: 10
---

# Accessibility-First Locators

TestFly ships Playwright-style **semantic locators** that target the
**accessibility tree** — what the user actually perceives — instead of brittle
CSS classes or DOM structure. The result: locators that read like the page and
survive redesigns.

```java
getByRole(Role.BUTTON).withName("Submit").click();
getByLabel("Email address").type("a@b.com");
getByPlaceholder("Search…").type("boots");
getByText("Forgot password?").click();
getByTestId("checkout-cta").click();
```

Every semantic locator returns the same chainable, **auto-waiting** `Locator`
used by the `$()` API — no `Thread.sleep`, no explicit waits. They're available
on both `BaseTest` and `BasePage`.

---

## Why semantic locators?

```java
// Brittle — breaks the moment the markup is refactored:
find(By.cssSelector("div.modal > form button.btn-primary")).click();

// Resilient — targets the role + accessible name the user sees:
getByRole(Role.BUTTON).withName("Submit").click();
```

---

## The locators

| Method | Matches |
|---|---|
| `getByRole(Role)` | Elements by ARIA role (implicit HTML element **or** explicit `role="…"`) |
| `getByText(String)` | Elements by visible text |
| `getByLabel(String)` | Form controls by their associated `<label>` text |
| `getByPlaceholder(String)` | Elements by `placeholder` attribute |
| `getByTestId(String)` | Elements by test-id attribute (default `data-testid`) |
| `getByAltText(String)` | Elements (typically `<img>`) by `alt` text |
| `getByTitle(String)` | Elements by `title` attribute |

---

## `getByRole`

`Role` covers 36 WAI-ARIA roles. Each matches both the native HTML elements that
carry the role implicitly and any element with an explicit `role` attribute —
e.g. `Role.BUTTON` matches `<button>`, `<input type="submit">`, `<summary>`, and
`[role="button"]`.

```java
getByRole(Role.BUTTON).withName("Save").click();   // accessible-name match
getByRole(Role.LINK, "Docs").click();              // role + name in one call
getByRole(Role.HEADING).withLevel(1).getText();    // heading level
```

`.withName(...)` matches the element's **accessible name**, computed following
ARIA precedence: `aria-label` → `aria-labelledby` → associated `<label>` →
text content → `value` / `alt` / `title`.

---

## Exact vs. substring

Text, name, and attribute matching is **case-insensitive substring** by default.
Call `.exact()` for a case-sensitive exact match:

```java
getByText("submit");          // matches "Submit", "SUBMIT ORDER", …
getByText("Submit").exact();  // matches only "Submit"
```

---

## Asserting role and name

`withName(...)` and `hasAccessibleName(...)` read the same accessible name, so a locator that
finds an element by name and an assertion that checks that name always agree. See
[Accessibility assertions](/docs/guides/assertions#accessibility-assertions) for `hasRole`,
`hasAccessibleName`, `hasAccessibleDescription` and ARIA snapshots.

## Configuring the test-id attribute

`getByTestId` uses `data-testid` by default. Override it in `testfly.yml`:

```yaml
locators:
  testIdAttribute: data-qa
```

Or programmatically: `Locator.setTestIdAttribute("data-qa");`

---

## Standard CSS & Chaining Filters

Along with accessibility-first semantic locators, `Locator` provides standard CSS and traditional selector factories with an immutable chaining pipeline:

```java
// Direct CSS selector factory
Locator.cssSelector(".product-card")
    .filter(".in-stock")
    .withText("Headphones")
    .first()
    .click();

// Null-safe typing (passing null or empty string safely clears the element)
getByPlaceholder("Search").type(null);
```

### A whole table column: `withinEach`

`within(container)` searches only the **first** container. `withinEach(containers)` runs the chain separately inside **every** container and joins the results, so `nth()`, `first()`, `last()`, `filter()` and `withText()` apply per container. That turns a table column into one locator, with no `td:nth-child(3)` and no loop over the rows:

```java
// Third cell of every row: ["Apple", "Banana", "Cherry"]
List<String> names = getByRole(Role.CELL).nth(2)
        .withinEach(getByRole(Role.ROW))
        .allTexts();

assertThat(Locator.cssSelector("td").nth(1).withinEach(By.cssSelector("tr")).within(By.id("fruits")))
        .hasTexts("$2", "$3", "$4");
```

Rows with too few matches, such as a header row of `th` cells, are skipped. The result usually holds several elements: read it with `allTexts()`, `count()` or `assertThat(...)`, and use a narrower locator before clicking a single cell. With `locators.strict: true`, an action on the whole column is a strict mode violation, because `nth()` picks one element per row, not one overall.

---

## Strict mode

By default an action on a locator that matches several elements uses the first
match without warning. Set `locators.strict: true` in `testfly.yml` to turn that
into a failure, so a click can never silently land on the wrong button:

```
Strict mode violation: Locator[getByRole(button)] resolved to 3 visible elements;
use first()/nth()/last() or narrow the locator.
Candidates: <button#save "Save">, <button#cancel "Cancel">, <button#help "Help">
```

- Only **visible** matches count, so hidden duplicates (a mobile and a desktop
  nav, a closed modal) do not trigger it. If exactly one match is visible, that
  one is used.
- `first()`, `nth(i)` and `last()` opt out explicitly.
- It applies to actions, `element()`, `isVisible()` and `isEnabled()`. It does
  not apply to `count()`, `elements()` or `assertThat(...)` assertions.
- The check runs once the locator resolves, so it fails immediately instead of
  waiting for the timeout.

---

## Declaring locators in YAML

`LocatorRepository` loads named locators from a YAML file so selectors live in
one place and can be shared between page objects and tests:

```yaml
# src/test/resources/locators/register.yaml
page: register
elements:
  register_form:
    css: "#register-form"
    elements:
      email:
        css: "input[type=email]"
      submit_button:
        role: button
        accessibleName: "Register"
        exact: true
  login_link:
    text: "Already have an account?"
```

```java
LocatorRepository repo = LocatorRepository.load("locators/register.yaml");
repo.get("register_form.email").type("a@b.com");
repo.get("register_form.submit_button").click();
```

`load(String)` checks the classpath first and then the file system.
`load(Path)` and `load(InputStream, String)` are also available.

**Strategies** — every element declares exactly one:
`css`, `xpath`, `id`, `byName`, `className`, `text`, `label`, `placeholder`,
`testId`, `altText`, `title`, `role`.

**Modifiers**

| Key | Applies to | Maps to |
|---|---|---|
| `accessibleName` | `role` | `withName(...)` |
| `level` (1–6) | `role: heading` | `withLevel(...)` |
| `exact` | `text`, `label`, `placeholder`, `altText`, `title`, `role` | `exact()` |
| `nth` (0-based), `first`, `last` | any (at most one) | `nth(...)`, `first()`, `last()` |

Use `byName` for the HTML `name` attribute and `accessibleName` for the
accessible name of a role; they are different things.

**Containers** — an element with `elements:` scopes its children with
`within(...)` so you do not repeat the container selector. A container must use
`css`, `xpath`, `id`, `byName` or `className`, takes no modifiers, and nests one
level only. Children are addressed as `container.child`.

**Fail-fast** — an unknown key, missing or conflicting strategies, an invalid
value, a missing file, or an unknown element key throws `LocatorException`
naming the file and element. There is no `${VAR}` substitution.

> YAML reads unquoted `on`, `off`, `yes`, `no` and numbers as non-strings. Quote
> such element names (`"off":`).

The repository holds no driver state and can be shared across threads.
`testId` uses the attribute configured under `locators.testIdAttribute` when the
locator is resolved.

---

## Escape hatch: `toBy()`

Every semantic locator can hand back its synthesized Selenium `By` for interop
with raw Selenium or [`SmartLocator`](./smart-locator):

```java
By submitBtn = getByRole(Role.BUTTON).toBy();
WebElement el = driver.findElement(submitBtn);
```

> `toBy()` returns the **base selector**. Refinements that can't be expressed as
> a `By` (e.g. `.withName(...)`) are applied only by the terminal actions
> (`click()`, `type()`, …) on the fully-resolved element.
