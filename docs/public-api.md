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
Playwright-inspired, auto-waiting locator API:
- `find(String cssOrXPath)`
- `getByRole(Role role, String name)`
- `getByLabel(String label)`
- `getByPlaceholder(String placeholder)`
- `getByText(String text)`
- `getByTestId(String testId)`
- `getByAltText(String altText)`
- `getByTitle(String title)`
- Actions: `click()`, `fill(text)`, `type(text)`, `hover()`, `press(key)`, `clear()`, `scrollIntoView()`.

#### Fluent Assertions
- **Element Assertions:** `assertThat(locator)` (`isVisible()`, `isHidden()`, `hasText(text)`, `containsText(text)`, `hasAttribute(attr, val)`, `hasValue(val)`, `isEnabled()`, `isDisabled()`, `isChecked()`).
- **Page Assertions:** `assertThatPage()` (`hasTitle(title)`, `titleContains(text)`, `hasUrl(url)`, `urlContains(text)`, `urlMatches(regex)`).

---

### 4. Network Mocking & Interception (`page().route(...)`)

Chrome DevTools Protocol (CDP v152) network control:
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
