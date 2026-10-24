---
id: locators
title: Locators
sidebar_position: 2
description: "Find native iOS and Android elements with MobileLocator: getBy… queries, roles, refinements, strict mode, selector strings, XPath, iOS class chain and predicate."
---

# Locators

A `MobileLocator` is a **lazy query** for elements of the device screen. Creating or refining a locator does not touch the device. Every action, read or assertion evaluates the query against a fresh view tree from mobilecli and, for actions and assertions, retries until the element is ready or the timeout runs out.

```java
MobileLocator signIn = screen().getByRole(MobileRole.BUTTON, "Sign in");   // nothing happens yet
signIn.tap();                                                             // reads the screen, waits, taps
```

## Finding elements

`screen()` (from `BaseMobileTest`, `BaseJUnit5MobileTest`, `MobilePage` or any `MobileSupport` class) returns the screen of the current test's device.

| Method | Matches |
|---|---|
| `getByTestId("login")` | iOS accessibility identifier, or Android `resource-id`. A short id (`login`) also matches the full one (`com.app:id/login`). |
| `getByRole(MobileRole.BUTTON)` | every element of the role (native classes of both platforms, see [Roles](#roles)) |
| `getByRole(MobileRole.BUTTON, "Sign in")`, `getByRole(role, Pattern)` | elements of the role whose accessible name (label, else text) equals the name or matches the regex |
| `getByText("Save")` | elements whose text (else label, else value) equals `Save` exactly |
| `getByText("sav", false)` | case-insensitive substring |
| `getByText(Pattern.compile("^Sav"))` | regular expression (`find()`) |
| `getByLabel("Password")`, `getByLabel("pass", false)` | accessibility label (iOS `label`, Android `content-desc`) |
| `getByPlaceholder("Username")`, `getByPlaceholder("user", false)` | placeholder / hint text |
| `getByType("android.widget.Switch")` | the raw native class (`XCUIElementTypeSwitch`, `Switch`, …) |
| `locate("role=button;name=Sign in")` | a [selector string](#selector-strings) |

**Which one to use:** test ids first. They are stable across device languages and redesigns (`accessibilityIdentifier` on iOS, `android:id` on Android). Then roles with a name, then labels. Texts last: they change with the language and with copy edits.

## Roles {#roles}

`getByRole` maps one role to the native classes of both platforms, so the same query works on iOS and Android:

| `MobileRole` | iOS | Android |
|---|---|---|
| `BUTTON` | Button | Button, ImageButton, FloatingActionButton, MaterialButton |
| `TEXTFIELD` | TextField, SecureTextField, SearchField | EditText, TextInputEditText, ReactEditText |
| `TEXT` | StaticText | TextView, MaterialTextView, ReactTextView |
| `IMAGE` | Image | ImageView, ShapeableImageView, ReactImageView |
| `SWITCH` | Switch, Toggle | Switch, ToggleButton |
| `CHECKBOX` | — | CheckBox |
| `RADIO` | — | RadioButton |
| `SLIDER` | Slider | SeekBar |
| `PROGRESSBAR` | ProgressIndicator, ActivityIndicator | ProgressBar |
| `ALERT` | Alert, Sheet | — |
| `COMBOBOX` | Picker, PickerWheel | Spinner |
| `LIST` | Table, CollectionView, ScrollView | ListView, RecyclerView, ReactScrollView |
| `LISTITEM` | Cell, Other | LinearLayout, RelativeLayout |
| `TAB` | Tab, TabBar | Tab, TabBar |
| `LINK` | Link | — |
| `HEADER` | NavigationBar | Toolbar |

## Refining a locator

Refinements return a new locator; the original is unchanged.

| Method | Result |
|---|---|
| `first()`, `last()`, `nth(i)` | one match by position (0-based; a negative index counts from the end) |
| `within(container)` | matches that lie inside a match of `container` (by tree or, for flat lists, by bounds) |
| `or(other)` | matches of either locator, in screen order (a fallback for two designs) |
| `and(other)` | elements matched by both |
| `filterHasText("In stock")`, `filterHasText(Pattern)` | matches whose subtree contains the text (case-insensitive) |
| `filterHasNotText("Sold out")` | matches whose subtree does not contain the text |
| `filterHas(inner)`, `filterHasNot(inner)` | matches that contain / do not contain an element of `inner` |
| `withTimeout(Duration.ofSeconds(15))` | the same query with another wait budget for its actions, reads and assertions |

```java
MobileLocator rows = screen().getByRole(MobileRole.LISTITEM);
screen().getByRole(MobileRole.BUTTON, "Delete").within(rows.filterHasText("Pixel 8")).tap();
screen().getByTestId("newCheckout").or(screen().getByTestId("checkout")).tap();
```

## Strict mode

Actions, reads that wait (`getText`, `getAttribute`, …) and single-element assertions need **exactly one** match. When the query matches several elements they fail at once with a `MobileLocatorException` that lists the matches:

```
Strict mode: getByText("General") matched 2 elements for tap. Use first(), last(), nth(i) or a narrower query.
```

Narrow the query (a role, a test id, `within(...)`) or pick one with `first()`, `last()` or `nth(i)`. `count()`, `nodes()`, `allTexts()`, `all()`, `toHaveCount(n)` and `toBeHidden()` look at every match and are not strict.

## Locators in code {#locators-in-code}

`MobileLocator` also has static factories named like Appium's and the web `Locator`'s. Unlike `screen().getBy…`, they bind to **the device of the thread that uses them**, so a `static final` field is safe in parallel runs:

```java
private static final MobileLocator SIGN_IN = MobileLocator.id("com.example:id/btnLogin");
private static final MobileLocator TERMS = MobileLocator.xpath("//*[@text='Terms']");

SIGN_IN.tap();
```

| Factory | Same as |
|---|---|
| `MobileLocator.id(id)` | `getByTestId(id)` (Appium `id`) |
| `MobileLocator.accessibilityId(id)` | iOS identifier, name or label; Android content-desc (Appium `accessibilityId`) |
| `MobileLocator.xpath(expr)` | [XPath](#xpath) over the view tree |
| `MobileLocator.iosClassChain(chain)`, `iosPredicate(predicate)` | [class chain / predicate](#class-chain-and-predicate) |
| `MobileLocator.className(type)` | `getByType(type)` |
| `byTestId`, `byText`, `byLabel`, `byPlaceholder`, `byRole(role[, name])` | the `getBy…` methods |
| `MobileLocator.of("text=Sign in")` | a selector string |

Used on a thread without an open device they fail with `MobileException`.

## Selector strings {#selector-strings}

`@MobileFind`, YAML locator files and `screen().locate(...)` take one string per element:

| String | Same as |
|---|---|
| `LoginScreen.loginButton` (no `=`) | `getByTestId("LoginScreen.loginButton")` |
| `testid=com.app:id/btnLogin`, `id=com.app:id/btnLogin` | `getByTestId("com.app:id/btnLogin")` |
| `text=Sign in` | `getByText("Sign in")` (exact) |
| `label=Password` | `getByLabel("Password")` |
| `placeholder=Username` | `getByPlaceholder("Username")` |
| `type=XCUIElementTypeSwitch` | `getByType("XCUIElementTypeSwitch")` |
| `role=button`, `role=button;name=Sign in` | `getByRole(MobileRole.BUTTON[, "Sign in"])` |
| `accessibility=loginButton` | `MobileLocator.accessibilityId("loginButton")` |
| `xpath=//android.widget.Button[@text='Sign in']` | XPath |
| ``chain=**/XCUIElementTypeCell[`label == 'Orders'`]`` | iOS class chain |
| `predicate=label BEGINSWITH 'Sign'` | iOS NSPredicate |

An unknown key, an unknown role or an empty value fails with `MobileLocatorException`, listing the valid formats.

## XPath {#xpath}

mobilecli has no XPath; TestFly evaluates XPath 1.0 itself on the tree mobilecli returns. Elements are named `XCUIElementType…` on iOS and by their Android class (`android.widget.Button`) under a `hierarchy` root.

Attributes: `resource-id`, `content-desc`, `text`, `name`, `label`, `value`, `placeholder`, `type`, `class`, `enabled`, `visible`, `selected`, `checked`, `focused`, `x`, `y`, `width`, `height`.

```java
MobileLocator.xpath("//android.widget.Button[@resource-id='com.app:id/save']");
MobileLocator.xpath("//XCUIElementTypeCell[.//XCUIElementTypeStaticText[@label='Orders']]");
MobileLocator.xpath("(//android.widget.EditText)[2]");
```

mobilecli drops unlabelled container views, so long absolute XPaths copied from Appium Inspector may not match. XPaths by id, text or type do.

## Class chain and predicate {#class-chain-and-predicate}

Both are evaluated by TestFly (common subsets), on iOS and Android trees alike.

- **Class chain:** `/`-separated segments, `**/` for any depth, a type (with or without `XCUIElementType`) or `*`, and filters ``[`predicate`]`` or `[n]` (1-based, negative from the end). Example: ``**/XCUIElementTypeCell[`name BEGINSWITH 'order'`][1]``.
- **Predicate:** `==`, `!=`, `BEGINSWITH`, `ENDSWITH`, `CONTAINS`, `LIKE`, `MATCHES`, with `[c]` (case-insensitive) and `[d]` (diacritic-insensitive), joined by `AND`, `OR`, `NOT` and parentheses. Keys: `name`, `label`, `value`, `type`, `identifier`, `text`, `placeholderValue` and the state flags (`enabled`, `visible`, `selected`, …). Example: `type == 'XCUIElementTypeButton' AND label CONTAINS[c] 'save'`.

## Finding selectors

The [Mobile Inspector](inspector) (VS Code and IntelliJ) shows the screen, the UI tree and ranked selectors with their match count on the current screen, and copies them as `@MobileFind` fields. Without an IDE, `mobilecli dump ui --device <id>` prints the same tree as JSON.
