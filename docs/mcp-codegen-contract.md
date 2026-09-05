# MCP Codegen Contract

This document is the **contract between the TestFly framework and the TestFly MCP server** ([`testfly-mcp`](https://github.com/hakanngul/testfly-mcp)).

The MCP server generates framework-native Java code from recorded browser sessions and AI prompt instructions (`framework="testfly"`). To ensure seamless compatibility, it mirrors key parts of the public API — locator factories, the `Role` enum, base-class helpers, and assertion methods.

> **Important**: If you modify any public API listed in this document, you must coordinate and update the MCP server's codegen tools accordingly so that generated Java code continues to compile cleanly.

Everything below is part of the stable public surface.

---

## 1. Accessibility-First Locator Factories

The MCP emits locators in priority order. Two calling contexts exist:

| Context | Base Class | Locator Style Emitted by MCP |
|---|---|---|
| `generate_java_page_object`, `generate_java_testng` | `BasePage` / `BaseTest` | **Instance** methods: `getByRole(...)`, `getByLabel(...)`, `find(...)` |
| `generate_java_junit5`, `generate_gherkin` | `BaseJUnit5Test` / `BaseCucumberSteps` | **Static** factories: `Locator.byRole(...)`, `Locator.byLabel(...)`, `Locator.of(...)` |

### Instance Method ↔ Static Factory Mapping

| Instance (`BaseTest` / `BasePage`) | Static (`Locator`) |
|---|---|
| `getByRole(Role.X, "name")` | `Locator.byRole(Role.X).withName("name")` |
| `getByRole(Role.HEADING, "n").withLevel(k)` | `Locator.byRole(Role.HEADING).withName("n").withLevel(k)` |
| `getByLabel("s")` | `Locator.byLabel("s")` |
| `getByText("s")` | `Locator.byText("s")` |
| `getByPlaceholder("s")` | `Locator.byPlaceholder("s")` |
| `getByTestId("s")` | `Locator.byTestId("s")` |
| `getByAltText("s")` | `Locator.byAltText("s")` |
| `getByTitle("s")` | `Locator.byTitle("s")` |
| `find("css")` / `$("css")` | `Locator.ofCss("css")` |
| `$(By.x(...))` | `Locator.of(By.x(...))` |

### Locator Selection Priority (Highest → Lowest)

The MCP selects the most resilient locator based on the live DOM snapshot:

1. `getByTestId` — matches `data-testid`, `data-test-id`, `data-test`, or `data-cy`
2. `getByRole(Role.BUTTON|LINK|HEADING, name)` — matches ARIA role and accessible name (`aria-label`, text, `title`)
3. `getByLabel` — associated `<label>` text (`for`, enclosing, or `aria-labelledby`)
4. `getByPlaceholder` → `getByAltText` → `getByTitle`
5. `find(By.id(...))`
6. Resilient CSS / XPath selector via `find("...")`

When an element lacks a high-confidence locator, the generator falls back to `smartFind(By primary, By... fallbacks)` on Page Objects.

---

## 2. `Role` Enum

The authoritative list of roles lives in [`Role.java`](../src/main/java/io/testfly/locator/Role.java). Supported roles include:

`BUTTON, LINK, CHECKBOX, RADIO, SWITCH, TEXTBOX, SEARCHBOX, COMBOBOX, OPTION, HEADING, IMG, TAB, MENUITEM, SLIDER, SPINBUTTON`.

---

## 3. `Locator` Terminal Actions Used by Codegen

- `click()`
- `fill(String)` / `type(String)`
- `hover()`
- `scrollIntoView()`
- `press(String)`
- `element()` (retrieves raw `WebElement` for advanced `Actions` / `Select` operations)

---

## 4. `assertThat(...)` Web-First Assertions

The MCP emits fluent assertions:

### Element Assertions (`assertThat(Locator)`)
- `isVisible()`
- `isHidden()`
- `hasText(String)`
- `containsText(String)`
- `hasAttribute(String, String)`
- `hasValue(String)`
- `count(int)`

### Page Assertions (`assertThatPage()`)
- `hasTitle(String)`
- `titleContains(String)`
- `hasUrl(String)`
- `urlContains(String)`
- `urlMatches(String)`

---

## 5. Base-Class Helpers Emitted by Codegen

| Base Class | Helpers Emitted |
|---|---|
| `BaseTest` | `open()`, `open(String)`, `getDriver()`, `getBy*`, `find`, `assertThat`, `assertThatPage()` |
| `BasePage` | `super(driver)`, `click`, `fill`, `hover`, `smartFind`, `getBy*`, `find`, `assertThat` |
| `BaseJUnit5Test` | `open()`, `open(String)`, `getDriver()`, `assertThat`, `assertThatPage()`, static `Locator.by*` |
