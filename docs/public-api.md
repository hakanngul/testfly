# TestFly – Public API Contract

This document defines the **public API surface** of TestFly.
Anything not explicitly listed here is considered **internal** and may change without notice.

---

## Purpose

- Protect users from breaking changes in internal implementations
- Prevent accidental framework misuse or lifecycle tampering
- Formalize long-term supported APIs for human engineers and AI coding agents

---

## Supported Public APIs

### 1. Test Base Classes & Runners

#### `BaseTest` (TestNG)
Standard base class for TestNG tests.
- **Allowed:** Access to `open()`, `find()`, `getBy*()`, `assertThat()`, `assertThatPage()`, `page()`, `getDriver()`, `api()`.
- **Forbidden:** Manual driver instantiation (`new ChromeDriver()`), overriding `@BeforeMethod`/`@AfterMethod` driver lifecycle.

#### `BaseJUnit5Test` (JUnit 5)
Standard base class for JUnit 5 Jupiter tests with per-test extension hooks and parallel execution support.
- **Allowed:** Full access to `open()`, `find()`, `assertThat()`, `assertThatPage()`, `page()`, `getDriver()`.
- **Forbidden:** Managing WebDriver lifecycle manually or tampering with extension contexts.

#### `@TestFlySession` (Cucumber 7 BDD)
Annotation and step definitions for Cucumber BDD feature files.
- **Allowed:** Thread-isolated step execution, driver access via `TestFlyContext.getDriver()`, scenario hooks.
- **Forbidden:** Cross-scenario static state sharing.

---

### 2. Page Object Model (`BasePage`)

Base class for all Page Object classes.
- **Allowed:**
  - Finding elements via `find(...)`, `getByRole(...)`, `getByLabel(...)`, `getByPlaceholder(...)`, `getByTestId(...)`.
  - User interactions (`click()`, `fill()`, `hover()`, `selectOption()`).
  - Standard explicit wait helpers and `smartFind(...)`.
- **Forbidden:**
  - Embedding assertions inside Page Objects (assertions belong in test methods).
  - Driver creation or destruction logic.

---

### 3. Fluent Locators & Assertions

#### `Locator` & `find(...)`
Playwright-inspired, immutable auto-waiting locator API:
- **Static Factory Methods:**
  - `Locator.cssSelector(String css)` *(Standard / Primary)*
  - `Locator.id(String id)`
  - `Locator.name(String name)`
  - `Locator.className(String className)`
  - `Locator.xpath(String xpath)`
  - `Locator.of(By by)` / `Locator.of(WebElement element)`
  - `Locator.byRole(Role role)` / `Locator.byRole(Role role, String name)`
  - `Locator.byLabel(String label)`
  - `Locator.byPlaceholder(String placeholder)`
  - `Locator.byText(String text)`
  - `Locator.byTestId(String testId)`
  - `Locator.byAltText(String altText)`
  - `Locator.byTitle(String title)`
  - `Locator.byIntent(String naturalLanguageIntent)`
- **Immutable Chaining Filters:**
  - `.filter(String css)`
  - `.withText(String text)` / `.exact()`
  - `.within(By container)` / `.within(Locator container)`
  - `.first()` / `.last()` / `.nth(int index)`
- **Actions (Auto-waiting & Null-safe):**
  - `click()`, `doubleClick()`, `fill(text)`, `type(text)`, `append(text)` *(passing null/empty gracefully clears without NPE)*, `hover()`, `press(key)`, `clear()`, `scrollIntoView()`.

#### Web-First Polling Assertions & Architectural Boundary
- **Element Assertions:** `assertThat(locator)` / `assertThat(by)` (`isVisible()`, `isHidden()`, `hasText(text)`, `containsText(text)`, `hasAttribute(attr, val)`, `hasValue(val)`, `isEnabled()`, `isDisabled()`, `isChecked()`).
- **Page Assertions:** `assertThatPage()` (`hasTitle(title)`, `titleContains(text)`, `hasUrl(url)`, `urlContains(text)`, `urlMatches(regex)`).
- **Soft Assertions:** `softAssert(locator)` / `softAssert(by)` thread-isolated collection.
- **Assertion Boundary Decision:**
  > [!NOTE]
  > TestFly's `AssertionSupport` intentionally focuses exclusively on auto-retrying, DOM-polling Web UI assertions (`LocatorAssert`, `PageAssert`). Standard primitive assertions (`assertTrue`, `assertEquals`, `assertNotNull`) are intentionally not reinvented; users are encouraged to use AssertJ or TestNG for general assertion needs.

---

### 4. Network Mocking & Interception (`page().route(...)`)

Chrome DevTools Protocol (CDP v155) network control:
- `page().route(String globOrRegex, RouteHandler handler)`
- `Route.fulfill(...)`: Stub status codes, headers, and JSON/text response bodies.
- `Route.abort(...)`: Abort requests with network failure reasons (`FAILED`, `TIMED_OUT`, `CONNECTION_RESET`).
- `Route.resume()`: Forward request to the live backend.

---

### 5. Unified REST API Testing Client (`api()`)

- `api().baseUrl(url).path(endpoint)`
- HTTP verbs: `get()`, `post(body)`, `put(body)`, `delete()`, `patch(body)`
- `ApiResponse`: `assertThat().statusCode(int)`, `jsonPath(path)`, `durationLessThan(ms)`, `matchesSchema(schema)`
- Async Polling: `api().poll().until(...)`

---

### 6. Mobile Device Emulation (`DeviceEmulator`)

- `DeviceEmulator.emulate(DeviceProfile.IPHONE_15_PRO)`
- Emulates viewport dimensions, pixel scale factor, mobile user-agent, and touch events.
- `DeviceEmulator.reset()`: Restores desktop browsing profile.

---

### 7. Precondition & Session Caching

- `@PreCondition(provider = LoginProvider.class, cache = true)`
- Caches authenticated cookies and local storage to skip repetitive UI logins across parallel tests.

---

### 8. Configuration (`testfly.yml`)

- All documented configuration keys under `execution`, `browser`, `timeouts`, `reporting`, `network`, `retries`, and `grid` are public contracts.
- System properties and environment variables (`TESTFLY_BROWSER_NAME`, etc.) override YAML configurations cleanly.

---

## Explicitly Non-Public APIs

The following internal components are subject to change without notice:
- `DriverManager` (internal ThreadLocal registry and pooling)
- `ExecutionEngine`
- `WaitEngine` internal implementations
- Internal TestNG / JUnit / Cucumber listener implementations
- Raw CDP event dispatchers

Direct usage of these classes from user tests is unsupported.

---

## Compatibility Guarantee

- Public APIs are strictly backwards compatible within the 1.x release train.
- Deprecated methods will be marked with `@Deprecated(since = "...", forRemoval = true)` and preserved for at least one minor release before removal.
