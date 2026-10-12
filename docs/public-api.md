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
  - `click()`, `doubleClick()`, `fill(text)`, `type(text)`, `append(text)` *(passing null/empty gracefully clears without NPE)*, `hover()`, `press(keys...)`, `clear()`, `scrollIntoView()`.
  - `rightClick()`, `jsClick()`, `focus()`, `blur()`, `dragTo(Locator target)`, `upload(paths...)` *(does not wait for visibility; file inputs are often hidden)*.
  - Checkbox / select: `check()`, `uncheck()`, `setChecked(boolean)`, `selectByText(text)`, `selectByValue(value)`, `selectByIndex(index)`.
  - `waitFor(Locator.State)` with `VISIBLE`, `HIDDEN`, `ATTACHED`, `DETACHED`.
- **Getters:**
  - Auto-waiting: `getText()`, `getValue()` / `inputValue()` *(use these for inputs; `getText()` is `""` there)*, `getAttribute(name)`, `getSelectedOption()`, `getCssValue(name)`, `innerHTML()`, `outerHTML()`.
  - Non-waiting: `isVisible()`, `isHidden()`, `isEnabled()`, `isDisabled()`, `isChecked()`, `isSelected()`, `isEditable()`, `isFocused()`, `isPresent()`, `count()`, `allTexts()`.

#### Web-First Polling Assertions & Architectural Boundary
- **Element Assertions:** `assertThat(locator)` / `assertThat(by)`:
  - Verified actions: `click()` (waits, scrolls, hovers, checks the element is not covered, clicks, and asserts the click event reached it), `fill(text)` and `clear()` (type or clear, then assert the value stuck; password values are never logged). They resolve the element like the matching `Locator` actions, including self-healing.
  - State: `isVisible()`, `isHidden()`, `isEnabled()`, `isDisabled()`, `isChecked()`, `isIndeterminate()`, `isSelected()`, `isFocused()`, `isEditable()`, `isReadOnly()`, `isRequired()`, `isInViewport()`, `isInViewport(ratio)`, `isEmpty()`, `isAttached()` / `exists()`.
  - Text: `hasText(text)`, `containsText(text)`, `hasTextMatching(regex)`, `hasTextIgnoringCase(text)`, `hasTexts(texts...)`, `containsTexts(texts...)`.
  - Value & form: `hasValue(val)`, `hasValue()`, `hasEmptyValue()`, `containsValue(text)`, `valueMatches(regex)`, `isPasswordHidden()`, `isPasswordVisible()`, `hasType(type)`, `hasPlaceholder(text)`, `hasMaxLength(n)`, `isValid()`, `isInvalid()`, `hasValidationMessage(text)`.
  - Attributes & selection: `hasAttribute(attr)`, `hasAttribute(attr, val)`, `hasCssValue(prop, val)`, `hasClass(cls)`, `hasId(id)`, `hasRole(role)`, `hasAccessibleName(name)`, `hasSelectedOption(text)`, `hasOptions(texts...)`.
  - Count: `count(n)`, `countAtLeast(n)`, `countAtMost(n)`.
  - Playwright for Java names: `hasCount(n)`, `hasText(Pattern | String[] | Pattern[])`, `containsText(Pattern | String[])`, `hasValue(Pattern)`, `hasValues(...)`, `hasAttribute(name, Pattern)`, `hasClass(Pattern)`, `containsClass(String | List<String>)`, `hasCSS(name, String | Pattern)`, `hasId(Pattern)`, `hasJSProperty(name, value)`, `hasRole(Role)`, `hasAccessibleName(Pattern)`, `hasAccessibleDescription(...)`, `hasAccessibleErrorMessage(...)`. `Pattern` arguments match anywhere in the value.
  - Negation: `not()` negates the next assertion, like Playwright (`assertThat(x).not().hasText("Error")`, `not().isAttached()`). Most negated assertions also pass when no element matches.
- **Page Assertions:** `assertThatPage()` (`hasTitle(title)`, `titleContains(text)`, `hasUrl(url)`, `urlContains(text)`, `urlMatches(regex)`, `hasAlert()`, `hasAlertText(text)`, `hasNoAlert()`, `hasCookie(name[, value])`, `doesNotHaveCookie(name)`, `hasLocalStorageItem(key[, value])`, `hasWindowCount(n)`).
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

### 5. REST API Client (`ApiClient`, `ApiResponse`, `ApiAuth`)

Entry points: `apiClient()`, `apiGet/apiPost/apiPut/apiPatch/apiDelete/apiHead/apiOptions(path)`, `apiTo(baseUrl)` and `apiToService(name)` from `ApiSupport` (implemented by `BaseTest`, `BaseApiTest`, `BaseJUnit5ApiTest`, `BaseCucumberSteps`), or the static `ApiClient` factories of the same names.

- Request builder: `path`, `header(s)`, `contentType`, `body(Object | byte[])`, `queryParam(s)` (replace), `addQueryParam(s)` (repeat), `pathParam(s)`, `formParam(s)`, `multipart`, `field`, `auth(ApiAuth)`, `spec(ApiRequestSpec)`, `timeout(int)` / `requestTimeout(Duration)`, `retry(..)` / `retryOn(..)` / `retryOnException(..)` / `noRetry()`, `proxy(host, port)` / `noProxy()`, `trustAllCerts()` / `trustStore(..)`, `withCookies()`, `interceptor(ApiInterceptor)`, `mockRule(ApiMockRule)`.
- Execution: `send()`, `sendAsync()`, `pollUntil(condition, timeout[, interval])`, `pollUntil(condition, ApiPolling)`; batches through `ApiBatchRunner.fire(n).concurrently(k).request(..).execute()` / `executeAll()` (`ApiBatchResult`).
- Thread and global state: `ApiClient.setGlobalAuth` / `setTestSpec` / `addInterceptor` / `addMockRule` are per test thread and cleared after each test; `setGlobalSpec` and the legacy `addRequestInterceptor` / `addResponseInterceptor` hooks are JVM-global.
- `ApiResponse`: `status()`, `body()`, `asBytes()`, `saveTo(Path)`, `header(s)`, `headerValues`, `contentType()`, `charset()`, `isSuccessful()`, `durationMs()`, `json(path[, type])`, `jsonList`, `jsonNode`, `asObject`, `asList`, `prettyBody()`; JSONPath subset `$`, `.a`, `['a.b']`, `[0]`, `[*]`, `.*`.
- Assertions (fluent, throw `ApiException`): `assertStatus`, `assertStatusIn`, `assertStatusRange`, `assertSuccessful`, `assertBodyContains`, `assertBodyMatches`, `assertHeader`, `assertHeaderPresent`, `assertJson`, `assertJsonEquals`, `assertJsonArrayContains`, `assertJsonArraySize`, `assertJsonExists`, `assertJsonNull`, `assertJsonTrue/False`, `assertJsonGreaterThan/LessThan`, `assertJsonContains`, `assertDurationLessThan`, soft variants `assertStatusSoft` / `assertJsonSoft` / `assertHeaderSoft`, `expect(ApiResponseSpec)`. Contract checks `assertSchema(path)` and `assertOpenApi(spec)` throw `ApiAssertionError` (an `AssertionError`); both error types implement `ApiFailure`.
- Auth (`ApiAuth`): `bearerToken`, `basicAuth`, `apiKey`, `apiKeyQuery`, `digest` (RFC 7616), `hmacSigner()` (`ApiHmacSigner`), deprecated `hmac`, `oauth2`, `oauth2Password`, `oauth2Refresh`, `clearTokenCache()`; custom challenge/signing schemes implement `ApiAuth.Intercepting`. Config-based strategies: `@UseAuth("name")` with `api.auth.<name>`.
- Middleware and mocking: `ApiInterceptor` / `ApiRequest`, `ApiMockRule`, WireMock-based `ApiMockServer` (`MockSupport.mockServer()`).

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
