# Locators and actions

## Preferred entry points

`LocatorSupport` supplies:

- `find(String css)`, `find(By)`, `find(WebElement)`
- `findById`, `findByName`, `findByClassName`, `findByXpath`
- `getByRole(Role)` and `getByRole(Role, String accessibleName)`
- `getByText`, `getByLabel`, `getByPlaceholder`, `getByTestId`, `getByAltText`, `getByTitle`

Use `getByRole(Role.BUTTON, "Sign in")`, `getByLabel("Email")`, or a stable test id before structural CSS. `Locator.setTestIdAttribute(...)` changes the process-wide test-id attribute, so configure it once before parallel execution rather than inside tests. `byIntent(String)`/`act(String)` require AI configuration and are not the deterministic default.

The role enum includes common roles such as `BUTTON`, `LINK`, `CHECKBOX`, `RADIO`, `SWITCH`, `TEXTBOX`, `SEARCHBOX`, `COMBOBOX`, `OPTION`, `HEADING`, `IMG`, `LIST`, `TABLE`, `DIALOG`, `TAB`, and `MENUITEM`. There is no `RoleOptions` type.

## Locator factories and refinements

Direct factories include `Locator.of(By)`, `of(WebElement)`, `id`, `name`, `className`, `xpath`, `cssSelector`, `byRole`, `byText`, `byLabel`, `byPlaceholder`, `byTestId`, `byAltText`, and `byTitle`. Static `Locator.css(...)` and `LocatorSupport.$(...)` are deprecated; use `cssSelector(...)` or `find(...)`.

Supported refinements:

```java
Locator row = find("[data-testid='user-row']")
        .withText("Ada Lovelace")
        .filter(".active")
        .first();

Locator save = getByRole(Role.BUTTON, "Save").exact();
Locator field = getByLabel("Email").within(By.id("profile-form"));
```

- `filter(String)` takes descendant CSS, not a predicate.
- `withText(String)` performs a text refinement; do not write `.filter(hasText(...))`.
- `within(By)` scopes beneath a Selenium locator.
- `nth(int)` is zero-based. `first()` and `last()` are conveniences.
- `toBy()` returns only the root Selenium locator and cannot preserve TestFly chain refinements. Pass the `Locator` itself to TestFly assertions.

## Interactions and reads

Supported Locator terminals are `click`, `clear`, `robustClear`, `type`, `append`, `getText`, `getAttribute`, `inputValue`, `isVisible`, `isHidden`, `isEnabled`, `hover`, `scrollIntoView`, `jsClick`, `count`, `element`, and `elements`.

```java
getByLabel("Email").type(email);              // clear, then type
getByLabel("Search").append(" automation");  // preserve current value
getByRole(Role.BUTTON, "Save").click();
String value = getByLabel("Email").inputValue();
getByLabel("Search").element().sendKeys(Keys.ENTER); // localized escape hatch
```

There is no `.fill()`, `.val()`, Locator `press`, `check`, `uncheck`, or `selectOption`.

For checkbox/radio controls, inspect selection only when an action needs it, then click conditionally:

```java
Locator remember = getByRole(Role.CHECKBOX, "Remember me");
if (remember.element().isSelected() != desired) {
    remember.click();
}
```

For native `<select>`, a `BasePage` subclass can call protected `selectByText(By, String)`, `selectByValue`, `selectByIndex`, and `getSelectedOption`. Non-native comboboxes should be modeled with their actual role/option interactions. Do not pretend they are native selects.

Prefer ordinary `click()` and `type()`. `jsClick()` and BasePage JavaScript helpers are fallbacks for a diagnosed browser/UI boundary, not a way to bypass interactability.
