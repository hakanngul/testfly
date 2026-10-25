---
description: "TestFly release notes and version history: new features, fixes, and breaking changes across every release."
id: changelog
title: Changelog
sidebar_position: 99
---

# Changelog

All notable changes to TestFly are documented here.

---

## [Unreleased]

### Added
- **Mobile Inspector (`tools/`, outside the Maven artifact)**: a VS Code extension (0.3.2) and an IntelliJ IDEA plugin for mobilecli devices. They show the screen, the view tree and TestFly selectors ranked by match count, and generate `@MobileFind` fields. Record mode turns taps, typing, swipes and buttons into a `MobilePage` class.
- **Browser Inspector (VS Code extension 0.3.2, `tools/`)**: opens a page in Chrome, Edge or Chromium and shows it with its DOM tree; the selected element gets ranked TestFly locators (`getByTestId`, `getByRole`, `getByLabel`, `getByPlaceholder`, `findById`, `getByText`, `find`) with match counts computed by TestFly's own rules, and a `Locator` field for a `BasePage`. The tree has a Chrome-like find box (string, CSS selector or XPath); the browser runs without a window unless asked and closes from the toolbar. The Mobile Inspector's tree gets an Appium-style finder (strategy + selector, matched with TestFly's `SelectorParser` rules), and its role table now equals `MobileRole`, so it no longer suggests `role=` selectors that TestFly does not resolve. The extension's activity-bar view switches between Mobile and Browser.
- **Mobile launcher activity**: `mobile.app.activity` (Android) picks the launcher activity for apps that have several, such as debug builds with LeakCanary. The `mobile.ios` and `mobile.android` blocks can override it.
- **Android login example**: the JUnit 5 example also checks the account summary and the popup confirmation after sign-in (`AndroidLoginJUnit5ExampleTest`).
- **Native mobile (incubating, `io.testfly.mobile`)**: drives Android and iOS apps through a user-installed [mobilecli](https://github.com/mobile-next/mobilecli) server, without Appium (ADR 0002). `screen().getByRole/Text/Label/TestId/Placeholder/Type` locators with auto-waiting actions (`tap`, `fill`, `swipe`, …), strict mode and `expect(...)` assertions; `device()` for apps, deep links and screenshots. Mobile tests run on TestNG (`BaseMobileTest`, `@MobileTest`), JUnit 5 (`BaseJUnit5MobileTest`) and Cucumber (`@mobile`); the browser is skipped and the device is opened per test. Configured with the new `mobile:` block. Not `@TestFlyApi` yet; may change in a minor release.
- **Mobile platform per test**: `@MobileTest(platform = MobilePlatform.IOS)` (next to `@Test`) runs a test on that platform, overriding `mobile.platform`; new `mobile.ios:` / `mobile.android:` blocks hold each platform's device and app, so one config serves both. New `Mobile.connect(MobilePlatform)` and `TestFlyConfig.Mobile.forPlatform(...)`.
- **Mobile Appium parity**: `mobile.alerts` also answers Android runtime permission dialogs (like `autoGrantPermissions`). `MobileLocator.pinchIn()`, `pinchOut()` and `dragTo(target)`; `MobileDevice` clipboard (`clipboard`, `setClipboard`, `clearClipboard`), files (`pushFile`, `pullFile`, up to 1 MB), crash reports (`crashReports`, `crashReport`; new crashes are saved to `target/reports/crashes/` when a test fails), apps (`isAppInstalled`, `foregroundApp`, `uninstallApp`, `backgroundApp`), keyboard (`isKeyboardShown`, `hideKeyboard`), settings (`setAnimations`, `setAppearance`, and `mobile.animations` / `mobile.appearance`) and webviews (`webViews()`, `webView()`, new `MobileWebView`). New optional `MobileDriver` default methods and `MobileCrashReport`.
- **Mobile Mobilewright parity**: `MobileLocator.check()` / `uncheck()` / `setChecked()`, `waitForEnabled()` / `waitForDisabled()`, `all()`, `bounds()` and `screenshot()` (just the element); `screen().tap(x, y)`, `doubleTap(x, y)`, `longPress(x, y)`; `MobileDevice.installedApps()`, `setFoldState(...)`, `launchApp(id, locales)` and `viewTreeText()`. Assertions `toBeEmpty()`, `toHaveText(text, ignoreCase)`, `MobileAssertions.expectPoll(supplier)` and soft assertions (`MobileAssertions.softly()`, new `MobileSoftAssertions`). `mobile.viewTreeOnFailure` (default `true`) saves the screen's view tree as text next to the failure screenshot. Webview DOM locators: `MobileWebView.locator(css)`, `getByTestId`, `getByText`, `getByRole` (new `MobileWebViewLocator` with `click`, `fill`, `getText`, `getValue`, `getAttribute`, `count`, `isVisible`, `waitFor`) and `evaluate(body, args...)`. New optional `MobileDriver` default methods `screenshot(clip, …)`, `setFoldState`, `launchApp(…, locales, …)` and `viewTreeText`.
- **Mobile system alerts**: `mobile.alerts: none | accept | dismiss` accepts or dismisses iOS system alerts (tracking, permissions) that cover the app before each read of the screen, like Appium's `autoAcceptAlerts` / `autoDismissAlerts`. `mobile.app.reset: clearData` no longer fails on an iOS simulator app that was never launched.
- **Mobile XPath, iOS class chain and predicate selectors**: `xpath=`, `chain=`, `predicate=`, `id=` and `accessibility=` selector strings, evaluated by TestFly on mobilecli's view tree (common subsets; full-path Appium XPaths may not match mobilecli's pruned tree). Typed `@MobileFind` attributes `iosAccessibility`, `iosId`, `iosXpath`, `iosClassChain`, `iosPredicate`, `iosClassName`, `androidAccessibility`, `androidId`, `androidXpath`, `androidClassName`. `MobileLocator` static factories (`id`, `accessibilityId`, `xpath`, `iosClassChain`, `iosPredicate`, `className`, `byText`, …, `of`) bind to the current test's device when used, so they can be `static final`. New reads `isPresent`, `isHidden`, `isEnabled`, `isDisabled`, `isSelected`, `isChecked`, `isFocused`, `allTexts`, `getAttribute`, and `scrollIntoView()`.
- **Mobile page objects**: `MobilePage` base class with `@MobileFind(ios = …, android = …, value = …)` fields (one element, both platforms), `@WaitFor` (the constructor waits for these elements), `onLoad()` and `tapIfShown(...)`, so tests read as `new HomePage().openLogin().signIn(u, p)`. Selector strings (`testid=`, `text=`, `label=`, `placeholder=`, `type=`, `role=button;name=…`) also work with `screen().locate(...)`; plain classes use `page(Type.class)` or `MobilePages.init(...)`.
- **Mobile-only profiles**: a config with a `mobile:` block no longer needs `browser`, `execution` or `timeouts`; missing web values get inert defaults.
- **Mobile preconditions**: `@PreCondition` works on TestNG and JUnit 5 mobile tests. Providers extend the new `MobileConditions` (page objects, `@ConditionProvider`) and are registered through `META-INF/services/io.testfly.mobile.MobileConditions` or `MobileConditions.register`. A condition runs once per device when `mobile.app.reset: none` keeps the app state, otherwise before every test; a retry reruns it. New `MobileDevice.keepsAppState()`.
- **Mobile device settings and screen recording**: `MobileDevice.setOrientation(MobileOrientation)` / `orientation()`, `setLocation(lat, lon)` / `clearLocation()` (both undone when the device closes), and `startRecording(path)` / `stopRecording()`. `mobile.recordVideo: off | failures | always` records every mobile test and keeps the video of failed tests (or all) in `target/reports/videos/`. New optional `MobileDriver` default methods; other drivers report them as unsupported.
- **Mobile YAML locator files**: `MobileLocators.load("mobile-locators/login.yml").get("login.username")` reads iOS/Android selectors from YAML (`ios:`, `android:`, `mobile:` blocks or a plain value for both platforms, nested groups with dot keys), checks every selector at load time and returns the running platform's locator.
- **Mobile device pool**: `mobile.devices: [ids]` or `mobile.pool: true` hand each parallel test its own device; tests wait up to `mobile.timeouts.reserveMs` for a free one.
- **mobilecli server auto-start**: `mobile.autoStartServer` (default `true`) runs `<mobile.mobilecliPath> server start` when a loopback `mobile.serverUrl` does not answer, once per JVM, logs to `target/mobilecli-autostart.log` and stops the server at JVM exit.
- **Mobile Emulator Smoke workflow**: runs the mobile examples on Android emulators (API 30, 34) and an iOS simulator on GitHub Actions; examples for the iOS and Android Settings apps and an iOS login flow with page objects (TestNG and Cucumber).
- **Test-writer subagents and skills**: `mobile-locator-finder`, iOS/Android, Cucumber, TestNG, JUnit 5, TDD, API and k6 writers under `.claude/agents/`, with the `testfly-mobile-write` and `testfly-k6-write` skills.
- **Verified fill and clear `assertThat(...).fill(text)` / `assertThat(...).clear()`**: resolve the element like `Locator.fill()`, wait until it is editable, type, and assert that the value then equals the text and stays that way. Password values are never logged, only their length.
- **Verified click `assertThat(...).click()`**: resolves the element like `Locator.click()` (including self-healing), waits until it is visible and enabled, scrolls it to the centre and hovers it, waits until nothing covers it, clicks, and asserts that the `click` event reached the element. Logs a passed `Clicked: ...` step; supports `as(...)`, `within(...)` and soft mode. `Locator.click()` is unchanged.
- **Form and input assertions on `LocatorAssert`**: `hasValue()` (any non-empty value), `hasEmptyValue()`, `containsValue`, `valueMatches`, `isPasswordHidden()` / `isPasswordVisible()`, `hasType`, `hasPlaceholder`, `hasMaxLength`, `isEditable`, `isReadOnly`, `isRequired`, and HTML5 validation through `isValid()`, `isInvalid()` and `hasValidationMessage`.
- **Playwright-style `not()` on `LocatorAssert`**: `assertThat(x).not().hasText("Error")` negates the next assertion. As in Playwright, most negated assertions also pass when no element matches. Cannot be combined with `click()`, `fill()` or `clear()`.
- **Playwright for Java parity on `LocatorAssert`**: `isAttached`, `isEmpty`, `isIndeterminate`, `isInViewport(ratio)`, `hasCount`, `hasText(Pattern | String[] | Pattern[])`, `containsText(Pattern | String[])`, `hasValue(Pattern)`, `hasValues`, `hasAttribute(name, Pattern)`, `hasClass(Pattern)`, `containsClass`, `hasCSS`, `hasId(Pattern)`, `hasJSProperty`, `hasRole(Role)`, `hasAccessibleName(Pattern)`, `hasAccessibleDescription`, `hasAccessibleErrorMessage`. `Pattern` arguments match anywhere in the value, as in Playwright.
- **More `LocatorAssert` matchers**: `hasTextMatching`, `hasTextIgnoringCase`, `hasTexts`, `containsTexts`, `isSelected`, `hasSelectedOption`, `hasOptions`, `countAtLeast`, `countAtMost`, `exists`, `isInViewport`, `hasRole`, `hasAccessibleName`, `hasId`.
- **`Locator` actions and getters**: `getValue()` (reads an input's value; `getText()` is empty for inputs), `fill`, `check` / `uncheck` / `setChecked`, `selectByText` / `selectByValue` / `selectByIndex`, `getSelectedOption`, `doubleClick`, `rightClick`, `press`, `dragTo`, `upload` (works with hidden file inputs), `focus`, `blur`, `waitFor(Locator.State)`, `getCssValue`, `innerHTML`, `outerHTML`, `allTexts`, and the non-waiting checks `isChecked`, `isSelected`, `isDisabled`, `isEditable`, `isFocused`, `isPresent`.
- **`PageAssert` matchers**: `hasAlert`, `hasAlertText`, `hasNoAlert`, `hasCookie`, `doesNotHaveCookie`, `hasLocalStorageItem`, `hasWindowCount`, and `not()` to negate the next page assertion (`assertThatPage().not().urlContains("/login")`).
- **API binary bodies and response accessors**: responses are read as bytes and decoded lazily with the `Content-Type` charset. New `ApiResponse.asBytes()`, `saveTo(Path)`, `headers()`, `headerValues(name)`, `contentType()`, `charset()`, `isSuccessful()`, `prettyBody()`; request side `ApiClient.body(byte[])` and `ApiResponse.Builder.body(byte[])`.
- **JSONPath wildcards and quoted keys**: `$.items[*].id`, `$.map.*` and `$['key.with.dots']` without a new dependency. Filter expressions, slices and `..` are rejected with a clear `IllegalArgumentException`.
- **API assertions**: `assertStatusIn(int...)`, `assertStatusRange(from, to)`, `assertSuccessful()`, semantic `assertJsonEquals(json)` / `assertJsonEquals(path, json)` (key order and `1` vs `1.0` ignored, optional array-order-insensitive mode, first-difference message) and `assertJsonArrayContains(path, value)`.
- **API requests**: `ApiClient.head(..)` / `options(..)` (plus `apiHead` / `apiOptions` in `ApiSupport`), repeated query keys with `addQueryParam` / `addQueryParams`, per-request retry `retry(n)`, `retry(n, backoff)`, `retryOn(statuses...)`, `retryOnException(boolean)`, `noRetry()`, and request bodies in the step log when `api.logBody` is on (binary bodies as a size summary).
- **API proxy**: `api.proxy.host`, `api.proxy.port`, `api.proxy.nonProxyHosts` and per-request `ApiClient.proxy(host, port)` / `noProxy()`.
- **Test-scoped request spec**: `ApiClient.setTestSpec(spec)` / `clearTestSpec()` apply a spec to the current test thread only and are cleared after each test.
- **Polling options `ApiPolling`**: `ApiClient.pollUntil(condition, ApiPolling.within(timeout).interval(..).failFastOn(..).maxAttempts(..).rethrowPredicateErrors(..))`. The timeout error now carries the last error as its cause.
- **Partial batch results**: `ApiBatchRunner.executeAll()` returns `ApiBatchResult` with a response or an error for every request in submission order; `execute()` is unchanged.
- **Real Digest authentication** (RFC 7616: MD5, SHA-256, SHA-512-256, `-sess`, `qop=auth`/`auth-int`) through `ApiAuth.digest(username, password)`, with per-test challenge caching and stale-nonce handling.
- **HMAC request signing** `ApiAuth.hmacSigner()` / `ApiHmacSigner`: signs method, path with query, timestamp and body hash; configurable headers, algorithm, encoding and canonical string.
- **`ApiAuth.Intercepting`**: auth strategies that need the final request or the response run as the innermost interceptor.
- **OAuth2 refresh tokens**: `ApiAuth.oauth2Refresh(...)`, automatic refresh-token grant for expired client-credentials and password tokens, and `ApiAuth.clearTokenCache()`.
- **New `@UseAuth` types**: `digest`, `hmac-signed`, `oauth2-refresh`, with the YAML fields `refreshToken`, `keyHeader`, `signatureHeader`, `timestampHeader`, `encoding`.
- **`ApiFailure` and `ApiAssertionError`**: one interface for status, URL, method and body on every API failure. Schema and OpenAPI failures now report the request.
- **WireMock helpers**: `ApiMockServer.stubFor(MappingBuilder)`, `stubJson(method, urlPattern, status, json)` and `resetOwnStubs()` for parallel tests.

### Changed
- **ReportPortal JUnit 5 agent** is upgraded from 5.3.1 to 5.5.8.
- **Schema and OpenAPI failures** throw `ApiAssertionError`, a subclass of `AssertionError`, so existing `catch (AssertionError)` code keeps working.
- **`ApiAuth.digest(...)`** now performs RFC 7616 challenge/response. It previously sent `Digest base64(user:password)`, which no server accepted.
- **Cookie jar** (`withCookies()`): cookies are sent only to their host or `Domain`, honour `Path`, `Secure`, `Max-Age` and `Expires`, and foreign `Domain` cookies are rejected. A cookie without `Path` still applies to `/`. Previously every cookie went to every host.
- **OAuth2 token cache** is cleared at suite end, so tokens are no longer reused across suites in the same JVM.
- **`ApiAuth.basicAuth`** always encodes credentials as UTF-8.
- **A `byte[]` passed to `body(Object)`** is sent raw instead of as a Base64 JSON string.
- **Default `@UseAuth` resolution** is shared by the TestNG listener and the JUnit 5 extension (no behaviour change for existing types).

### Deprecated
- **`ApiAuth.hmac(apiKey, secret, algorithm)`**: signs only the API key. Use `ApiAuth.hmacSigner()`. Its headers are unchanged.

### Fixed
- **Mobile iOS first view-tree read**: on a freshly started simulator the first read could time out while DeviceKit was still starting. The driver now calls `device.apps.foreground` first, so DeviceKit is ready before the app launches.
- **JUnit 5 ReportPortal test items**: the test item was not started through the agent's lifecycle before the test ran, so results were not reported correctly. `TestFlyExtension` now calls `ReportPortalJUnit5Bridge.startTestItem`, which invokes the agent's `interceptTestMethod`, before the test body runs.
- **`ApiResponseSpec` content-type failures** named the request as `null null`; they now include the method and URL.
- **JSON paths with keys containing `/` or `~`** resolved incorrectly through JSON Pointer; they now work with bracket notation.
- **A lower-case `content-type` header** could be duplicated by a request spec's content type.
- **Docs**: README suggested `ApiClient.setGlobalAuth` in `@BeforeSuite`, which cannot work because global auth is per test thread and cleared after each test. `docs/public-api.md` described a non-existent `api()` client. The configuration page mentioned a non-existent `apiClient().withAuth(name)`.

## [1.0.7] — 2026-10-09

### Added
- **Canonical `Locator.cssSelector(String)`**: Introduced `Locator.cssSelector(String)` as the primary, memorable factory method matching Selenium's `By.cssSelector` naming conventions.
- **Assertion Boundary Architecture**: Formalized the separation between TestFly's auto-retrying, DOM-polling Web UI assertions (`LocatorAssert`, `PageAssert`) and general-purpose primitive assertions (delegated to AssertJ / TestNG).
- **`execution.sessionWaitSeconds`** (default `300`, `0` = do not wait, validated `>= 0`): controls how long a test waits for a free browser slot when `execution.maxActiveSessions` is exhausted. It replaces the previously hard-coded 30-second wait.
- **`ExecutionValidator.crossCheckWarnings`**: prints a warning when parallel execution uses `execution.threadCount` greater than `execution.maxActiveSessions`, because the surplus threads queue for a browser slot.
- **`LoadTestDetector`** (internal): single place that decides whether a test is a load test (see *Changed*).
- **`testfly-test-authoring` agent skill** with per-area references (WebUI, API, TestNG, JUnit 5, Cucumber, load testing).

### Changed

- **Load-test detection is explicit (behavior change).** The four name-based `contains("loadtest")` heuristics (`DriverManager`, `TestExecutionListener`, `TestFlyExtension`, `CucumberHooks`) were replaced by a single `LoadTestDetector`. A test is now treated as a load test (no WebDriver) only when it extends `BaseLoadTest`, implements `LoadTestSupport`, is annotated with `@LoadTest` / `@NoBrowser`, or carries the exact Cucumber tag `@loadtest`. Classes such as `FileUploadTest`, `DownloadTest` or anything in a package like `com.acme.uploadtests` are no longer silently denied a WebDriver. A one-time WARN is logged for classes/tags that matched the old name heuristic but not the new rules.
- `Locator` actions (`click`, `fill`, `text`, ...) now auto-wait up to `timeouts.explicit`, re-resolving the element on each poll (also recovers from stale references) and self-healing after the timeout. `isVisible()`, `isEnabled()` and `count()` stay non-waiting.
- `DriverManager.getDriver()` now explains why no driver exists (outside `@Test` / `@PreCondition` / `@ConditionProvider`, e.g. in `@BeforeMethod`) and where to move the setup.
- The session-slot timeout error now names both `execution.maxActiveSessions` and `execution.sessionWaitSeconds`.
- Jackson modules are pinned through `jackson-bom` 2.21.7 (previously only `jackson-databind` was pinned, at 2.21.6), so transitive and optional Jackson modules stay on one version.
- The JaCoCo agent is now attached to the Surefire JVM (`argLine` is `@{argLine} ...`), so `target/jacoco.exec` and the coverage report are produced by `mvn verify`.
- GPG signing moved from the default build into the `release` Maven profile: `mvn verify` / `mvn install` need no key; releases use `-Prelease`.

### Deprecated
- **`Locator.css(String)`**: Deprecated in favor of `Locator.cssSelector(String)`. It continues to delegate seamlessly to prevent breaking existing code.

### Fixed

- `getByText()` returned the outermost ancestor (`html`/`body`/wrapper `div`) instead of the element holding the text; it now returns only the innermost match. `exact()` uses the same logic.
- Load tests: HTTP status codes are now recorded in `statusCodes` (transport failures use the synthetic code `-1`), `LoadScenario.assertStatus(n)` now fails when any other status occurs (it previously asserted against `assertNoStatus` of a different code), and `extract()` works for scenarios without a feeder.
- HTML report: the report data embedded in the page is escaped for a `<script>` context (`<`, U+2028, U+2029), so failure messages and test data can no longer inject markup or close the script block; template placeholders are now substituted in a single pass, so substituted values are never re-scanned.
- `DriverManager` session permits: a permit is now returned exactly once per driver, including when `driver.quit()` throws and on every failure path of driver creation (a permit leak that could exhaust `maxActiveSessions`).
- `DriverManager.recreateDriver()` now also replaces a dead `per-suite` driver (it was kept in the suite registry), and `quitAllSuiteDrivers()` releases only the permits that were actually held.
- `OpenApiValidator` passes the URI path (not the full URL) and the response `Content-Type` header to the validator, which fixes "No API path found" failures and response body validation.

### Security

- `.github/workflows/release.yml` hardened: the release version is validated as a whole string (no newline or script injection) and read only through the environment, never interpolated into scripts; the release commit must be reachable from `main`; publishing is gated on the `release` GitHub environment; the pom version must equal the release version (tag pushes are no longer rewritten); tests run once, GPG signing is enabled only through `-Prelease`, and the tag/GitHub Release is created at the verified commit.

### Documentation & Specifications
- **Engineering Specs Modernization**: Modernized root `docs/` specifications (`internals.md`, `public-api.md`, `architecture.md`, `testng-listeners.md`) to reflect the Java 21 LTS baseline, `SmartTriageEngine`, `FuzzyHealingEngine`, and assertion boundaries.
- **Documentation Site Sync**: Updated guides for semantic locators, assertions, and self-healing across both English and Turkish documentation locales.
- **Agent Knowledge Graph Sync**: Registered `[[wiki/assertion-system]]`, updated architecture and WebUI wikis, and synchronized `MAP.md`.
- **Documentation audit (EN + TR)**: corrected the Maven coordinate to `io.github.hakanngul` throughout, rewrote examples that did not compile against the real API, aligned the load-testing docs with the real DSL (`load(path).users().rampUp().hold()`, `LoadTestFeeder`, `@LoadTest` attributes), and corrected claims about `WaitEngine`, Kubernetes/distributed load tests, Allure and CI setup. Install guides now state which version is on Maven Central and which version this checkout is.
- **Agent guidance consolidated**: `AGENTS.md` is the single entry point (`GEMINI.md`, `PRODUCT.md` and `features/features-report.md` removed), `ROADMAP.md` and `CONTRIBUTING.md` updated, and obsolete planning documents under `docs/` retired.

### Tests

- Unit suite grew from 1362 to 1444 tests, adding regression coverage for HTML report escaping, `DriverManager` (permit accounting, per-suite recreate, slot wait), `LoadTestDetector`, `JdkLoadEngine`, `OpenApiValidator`, `ExecutionValidator`, `Locator` auto-wait and test-id configuration isolation.

---

## [1.0.6] — 2026-09-29

### Added
- **Smart Flakiness Triage (`SmartTriageEngine`)**: Local heuristic analyzer integrated into `TestExecutionListener` that inspects exception hierarchies to categorize failures (`SYSTEM_FLAKY`, `APPLICATION_BUG`, `NEEDS_INVESTIGATION`) without LLM token cost.
- **Fuzzy Self-Healing (`FuzzyHealingEngine`)**: Local, zero-token heuristic fallback healing using Levenshtein distance against DOM candidates and attribute tokens before escalating to external LLMs; no fixed latency is guaranteed.
- **Auto-POM Page Knowledge System**: Autonomous page element discovery and local semantic intent resolution with continuous learning cache (`PageKnowledge`).
- **Sequenced Collections on Locators**: Added `first()` and `last()` navigation methods to `Locator` leveraging Java 21 sequenced collections.
- **`BaseJUnit5ApiTest`**: Native REST API testing base class with full `api()` client support for JUnit 5 Jupiter suites.
- **`NavigationSupport` Helpers**: Added comprehensive window management, multi-tab switching, and browser navigation helpers.
- **HTML Report Log Filters**: Interactive INFO and DEBUG log level visibility toggles in the report timeline.

### Changed
- **Java 21 LTS Modernization**: Framework baseline and compiler upgraded to Java 21 LTS (`--release 21`), adopting Pattern Matching for `instanceof`, exhaustive switch expressions, and stream `.toList()` across the repository.
- **Native Selenium By Interoperability (`Locator extends By`)**: `Locator` now directly extends Selenium's `org.openqa.selenium.By`, enabling 100% transparent interoperability with native Selenium APIs (`driver.findElement(locator)`).
- **Clean API Refactor**: Refactored `Locator` to remove redundant prefix methods (`ofCss`, `inputValue`, `getValue`) to embrace a unified, zero-boilerplate API.
- **Support Interface Completeness**: Ensured 100% method delegation parity for all `Support` interfaces (`ApiSupport`, `BrowserSupport`, `LocatorSupport`), fully wrapping the internal engines.
- **Null-Safe Actions**: `Locator.type()` and `Locator.append()` are completely null-safe; passing `null` or `""` gracefully clears the element without throwing exceptions.

### Performance
- **Virtual Threads in Load Testing**: Leveraged Project Loom `Executors.newVirtualThreadPerTaskExecutor()` in `JdkLoadEngine` for ultra-scalable, lightweight load testing.
- **Video Recording Memory Optimization**: Video recorder stores raw byte frames and performs lazy decoding upon test failure, significantly reducing heap overhead during long runs.

### Fixed & Hardened
- **Guaranteed Driver Cleanup**: Added JVM shutdown hooks and `@AfterAll` listener execution to guarantee browser process termination even on abnormal exits.
- **Thread-Safe Bootstrap**: Hardened framework bootstrap initialization against race conditions during concurrent suite startup.

---

## [1.0.5] — 2026-09-12

### Added — Interactive Web Recorder & Web Studio (`testfly record`)

- **Chrome Companion & Injected Extension-less Recording**:
  - Launch isolated Google Chrome with automated Chrome DevTools Protocol (CDP) script injection (`testfly record <url>`).
  - Real-time Server-Sent Events (SSE) streaming of clicks, coalesced keystrokes, and assertions directly to local web studio (`:8765`).
  - Interactive toolbar for one-click visual assertions (`isVisible`, `isEnabled`, `hasText` with exact and contains matching).
- **4-in-1 Multi-Framework Java Codegen**:
  - Live code compilation for Page Object Model (`BasePage`), standalone TestNG (`BaseTest`), JUnit 5 (`BaseJUnit5Test`), and Cucumber BDD (`.feature`).
  - Compiler safeguards preventing syntax errors from Java reserved keywords (e.g. `continueElement`).
  - 1-click "Save to Project" writes production-ready classes directly into `src/test/java/`.
- **Architectural Decision Record (ADR-001)**:
  - Documented design decisions, protocol boundaries, and security model for the companion studio and MCP integration.

### Added — Model Context Protocol Server (`testfly mcp`)

- **Protocol-Native AI Browser Automation**:
  - Built-in MCP server exposing 88 live browser automation tools to AI coding assistants (Claude Code, Cursor, GitHub Copilot, JetBrains AI).
  - Enables LLMs to inspect live DOM trees, query the accessibility tree, verify layouts, and author robust TestFly tests without hallucinated selectors.

### Added — Smart Test Sharder (`SmartTestSharder`)

- **LPT Bin-Packing Test Distribution**:
  - Implements the Longest Processing Time (LPT) bin-packing algorithm to balance test suite execution across distributed CI worker nodes.
  - Minimizes pipeline idle time and achieves optimal parallelization based on historical test duration metrics.

### Added — Load & Performance Testing Module

- **Dual-Engine Load Testing Architecture**:
  - **Gatling Engine**: High-concurrency subprocess execution, automatic simulation generation, stdout/log parsing, and full interactive Gatling HTML report generation linked directly in TestFly reports.
  - **Lightweight Virtual Thread Engine**: Zero-dependency JDK virtual thread engine for developer machines and fast CI/CD feedback loops.
- **Fluent Load Testing DSL**:
  - Declarative scenario builder via `load(path).users(n).rampUp(duration).hold(duration).run()` or `loadScenario("name").step(...).run()`.
  - Available in `BaseLoadTest` (the load-test base class; it starts no browser).
- **Annotation-Driven Execution (`@LoadTest`)**:
  - Configure load test parameters (`users`, `rampUp`, `hold`, `cooldown`, `engine`, `baseUrl`) directly at test class or method level.
- **Data Feeders (`LoadTestFeeder`)**:
  - `LoadTestFeeder` factories `csv`, `json`, `random`, `uuid`, `sequence`, and `constant`, plus `feedCsv`/`feedJson` shortcuts on the scenario.
- **SLA & Latency Assertions (`LoadTestAssert`)**:
  - Fluent assertions for percentiles (P50, P90, P95, P99), max response time, min throughput (RPS), error rate thresholds, and HTTP status distributions.
- **Unified Multi-Channel Reporting**:
  - **TestFly HTML Report**: Dedicated "Load Testing" tab with interactive KPI cards (Users, RPS, Total Requests, P95, Error Rate), response time percentiles breakdown, status code distribution, and clickable link to Gatling interactive HTML reports.
  - **Allure Report Adapter**: Adds load test parameters (Users, Engine, RPS, P95, Error Rate), interactive link to Gatling report, summary markdown attachment, and full subprocess execution log attachment.
  - **ReportPortal Adapter**: Sends load test Markdown summary log entries and attaches subprocess execution logs to the test step.

### Added — Feature Switchboard

- **`features:` master on/off panel** — master switchboard in `testfly.yml` to effortlessly enable/disable optional subsystems (`ai`, `recording`, `tracing`, `network`, `healing`, `visual`, `performance`, `flakiness`, `quarantine`, `testManagement`, `notifications`, `consoleErrors`).
- **`FeatureGate`** (`io.testfly.config.FeatureGate`) — unified programmatic gate for feature status.
- **`ai.enabled` master AI switch** (default `true`) — single switch to gate all AI capabilities.

### Changed — HTML Report & Security Hardening

- **HTML Report Cupertino Lab Redesign** — Modernized report layout with Cupertino Lab aesthetic, improved table density, responsive failure triage cards, and dark mode optimization.
- **Flakiness Radar** — Visual stability scoring across consecutive test runs.
- **Security Sanitization** — Added safe DOM rendering and XSS prevention helpers (`escapeHtml`, `sanitizeUrl`, `safeText`) in HTML report generation.
- **Repository Security Attributes** — Added `.gitattributes` to mark vendored libraries and generated files, preventing false positive scanner alerts.
- **Tolerant `testfly.yml` parsing** — Unrecognized configuration keys are logged as warnings rather than crashing startup.
- **Fail-fast on disabled features** — Direct programmatic invocations of disabled features fail clearly rather than silently skipping.
- **Homepage Modularization** — Separated static data and snippets into `homeData.js` to ensure low code entropy and clean React architecture.

---

## [1.0.4] — 2026-09-07

### Added — Agentic Testing & Autonomous AI

- **AI-Driven Advanced Self-Healing** — `DomPruner` compresses complex web DOM trees to under 8K tokens by stripping non-semantic and decorative nodes. `AiHealingEngine` synthesizes replacement locators with LLM reasoning when static fallbacks are exhausted and caches healed locators in `.testfly/healed-locators.json`; cache hits avoid another AI request but do not guarantee zero execution time.
- **AI-Powered Self-Remediation & Auto-PR Patches** — `SourceCodeLocator` maps runtime failures back to consumer test and page object sources. `RemediationPatchGenerator` generates clean, unified git diff `.patch` files into `target/remediations/` for single-command `git apply` resolution.
- **Semantic Natural Language Assertions** — `satisfiesAi(condition)` and `violatesAi(condition)` on `PageAssert` and `LocatorAssert`, plus `assertWithAi(condition)` convenience method in `AssertionSupport`. Features single-evaluation anti-throttle guard and soft-assertion compatibility.
- **Goal-Oriented Dynamic Steps (`act`) & Compile & Freeze Caching** — High-level natural language goal execution via `act(String goal)` in `ActionSupport` (`BaseTest`, `BasePage`, `BaseJUnit5Test`, `BaseCucumberSteps`) and `byIntent(String intent)` in `LocatorSupport`. Compiles user intents into deterministic Selenium action plans and freezes them into `.testfly/action-cache.json` for instant replay.
- **Dropdown & Navigation Action Primitives** — `ActionType.SELECT` picks a `<select>` option by visible text through Selenium's `Select`; `ActionType.NAVIGATE` calls `driver.get()`, passing absolute URLs through and resolving relative paths against `execution.baseUrl`. `NAVIGATE` bypasses locator parsing, so agent plans can move between pages without a target element. Both are advertised to the LLM by `ActionCompiler.buildPrompt`.

### Added — Video Recording

- **Playwright-style retain-on-failure recording** — Web UI tests are recorded to MP4 and kept only when a test fails; passing tests discard the video automatically
- **Real MP4 encoding** — videos encoded as H.264 MP4 and embedded as HTML5 `<video>` players in both TestFly HTML report and Allure report
- **Headless viewport auto-config** — 1920×1080 viewport automatically applied for headless Chrome/Edge when `startMaximized` is set

### Added — Network Mocking Framework

- **Comprehensive network mocking and assertions** — CDP-based request interception with pattern matching, response stubbing, and assertion API for verifying captured requests

### Changed

- **SessionCache rename** — `browser.SessionCache` → `BrowserSessionCache`, `precondition.SessionCache` → `PreconditionSessionCache` to eliminate naming ambiguity
- **`.env` resolution priority** — `DotEnvLoader` now resolves `${VAR}` placeholders with explicit priority: `.env` file > shell environment > system property (`-D`). Added `DotEnvLoader.fromDotEnv(key)` to query `.env` values directly. Removed `dotenv-java` dependency; `.env` parsing is handled internally.

### Added — API Testing Improvements

- **HTTP-level retry** (`api.retry.*`) — config-driven retry for transient failures and status codes (502/503/504); exponential backoff; step-logged with `WARN`
- **Per-request timeout override** — `apiClient().get("/report").timeout(120).send()`
- **Query parameter builder** — `.queryParam("page", 1).queryParam("limit", 10)` with URL encoding
- **Request/Response interceptors** — `ApiClient.addRequestInterceptor()` and `addResponseInterceptor()`; global, thread-safe
- **Cookie jar** — `.withCookies()` captures and auto-sends cookies across requests; thread-local
- **Response time assertion** — `res.assertDurationLessThan(500)` and `assertDurationLessThan(2, TimeUnit.SECONDS)`
- **Header assertions** — `assertHeader("Content-Type", "application/json")`, `assertHeaderPresent("X-Request-Id")`
- **Body regex assertion** — `assertBodyMatches("\\d{4}-\\d{2}-\\d{2}")` with dotall mode
- **JSON structure assertions** — `assertJsonExists("$.path")`, `assertJsonNull("$.field")`, `assertJsonArraySize("$.items", 3)`
- **Configurable truncation limit** — `api.truncationLimit: 1000` (default 300)
- **Self-healing locator cache** — healed locators persisted across runs for faster recovery

```yaml
api:
  retry:
    enabled: true
    maxAttempts: 3
    backoffMs: 500
    retryOnStatus: [502, 503, 504]
    retryOnException: true
```

### Added — Report Portal Enhancements

- **Auto run type detection** — suite test classes scanned; `BaseApiTest` → "API", otherwise → "Web"; `reporting.reportportal.type: api|web|auto`
- **Enriched launch name** — `<name> — <API|Web> | <env> | <timestamp>`
- **Enriched description** — run type, context-aware base URL (Web→execution.baseUrl, API→api.baseUrl), environment, user@hostname, CI platform + build info
- **CI platform detection** — GitHub Actions, Jenkins, GitLab CI, CircleCI, Travis CI, Bitbucket Pipelines, Azure Pipelines
- **JUnit 5 → Report Portal** — `agent-java-junit5` dependency + `ReportPortalJUnit5Bridge` reflection bridge; JUnit 5 tests push results to RP automatically

```yaml
reporting:
  reportPortal:
    enabled: true
    launch: "Demo Web - Dev"
    type: auto
```

### Fixed

- **OAuth2 token cache race condition** — double-checked locking prevents thundering herd on expired tokens
- **RP launch naming** — context-aware base URL selection based on run type (Web vs API)
- **Dark theme text visibility** — CSS safety nets for all Prism token types; fixed invisible code block text; dark mode overrides for search dropdown, tabs, collapsible, badges, footer
- **AI model defaults and validation** — `ai.model` is optional and defaults per provider (`claude-haiku-4-5-20251001` for Claude, `gemini-2.5-flash` for Gemini); sanitized Gemini model names against injection
- **Session cache isolation & script safety** — `BrowserSessionCache` and `PreconditionSessionCache` clear localStorage before restore and pass items safely via WebDriver script arguments
- **PreCondition retry vs DataProvider isolation** — fixed premature session cache invalidation during multi-row `@DataProvider` runs; cache is now only invalidated on true retries (`result.wasRetried()`)
- **LocatorAssert failure propagation** — restrict caught wait exceptions to `TimeoutException` so unexpected browser/WebDriver faults are not masked
- **Locator withText matching** — `Locator.withText` defaults to case-insensitive substring matching and respects the exact-match parameter
- **ReportPortal credential guard** — skip RP registration when credentials are missing instead of failing the suite
- **Headless viewport** — automatically configure 1920×1080 viewport for headless Chrome/Edge when `startMaximized` is set

---

## [1.0.0] — 2026-08-20

### Changed
- **Project rebrand to TestFly** — complete identity migration:
  - Maven coordinates: `io.testfly:testfly:1.0.0` *(historical entry: legacy group ID; the current coordinate is `io.github.hakanngul:testfly`)*
  - Java namespace: `io.testfly`
  - Config file: `testfly.yml`
  - Public API annotation: `@TestFlyApi`
  - Report artifacts: `testfly-report.html` and `testfly-metrics.json`

### Security
- API credentials moved to `${ENV_VAR}` placeholders sourced from environment or `.env` file.

### Build
- Integration tests split into `src/test/java/io/testfly/integration/` with `maven-failsafe-plugin`
- `quality` Maven profile: JaCoCo, SpotBugs, Checkstyle, PMD

---

## [0.24.0] — 2026-08-15

### Fixed
- **`execution.parallel` validation** — now delegates to TestNG's `XmlSuite.ParallelMode`; `tests` and `instances` modes accepted

---

## [0.23.0] — 2026-07-18

### Added
- **`waitForAttribute(By, attribute, value)`** — exact attribute match
- **`waitForUrlMatches(String regex)`** — URL regex match
- **`waitForTextMatches(By, String regex)`** — element text regex match

---

## [0.22.0] — 2026-06-26

### Fixed
- **Report overwrite with multiple test engines** — `testfly.reports.dir` system property honored by all report outputs

---

## [0.21.0] — 2026-06-25

### Added
- **Accessibility-first locators** — `getByRole`, `getByText`, `getByLabel`, `getByPlaceholder`, `getByTestId`, `getByAltText`, `getByTitle`
- **`getByRole(Role)`** — 36 WAI-ARIA roles with implicit + explicit matching; `.withName()`, `.withLevel()` (corrected from the original count of 38)
- **`toBy()` escape hatch** — returns synthesized Selenium `By`

```java
getByRole(Role.BUTTON).withName("Submit").click();
getByText("Welcome").isVisible();
getByTestId("checkout-btn").click();
```

---

## [0.20.0] — 2026-06-21

### Added
- **TestRail** — `@TestRailCase("C1234")` auto-pushes results; multiple IDs; auto-creates runs
- **Xray** — `@XrayTest("PROJ-123")` for Cloud (OAuth2) and Server/DC (Basic auth)
- Zero extra dependencies — both use `java.net.http.HttpClient`

---

## [0.19.0] — 2026-06-20

### Added
- **Gradle build support** — `testImplementation 'io.testfly:testfly'` + `test { useTestNG() }` *(historical entry: legacy group ID; the current coordinate is `io.github.hakanngul:testfly`)*
- JUnit XML auto-detects Maven vs Gradle directory layout
- `FrameworkVersion.get()` reads `MANIFEST.MF` (works with both build tools)

---

## [0.18.0] — 2026-06-20

### Added
- **Accessibility assertions (axe-core)** — `accessibility().withTags("wcag2a","wcag21aa").withLevel(Impact.SERIOUS).run()`
- axe-core 4.10.2 bundled in JAR — no CDN dependency

---

## [0.17.0] — 2026-05-19

### Added
- **Performance assertions (Core Web Vitals)** — `assertPerformance().lcp().isBelow(2500).cls().isBelow(0.1)`
- `performance.captureOnEveryTest: true` — ⚡ metrics strip in HTML report

---

## [0.16.0] — 2026-05-17

### Added
- **Test quarantine** — `testfly-quarantine.yml` for permanent test skipping; TestNG, JUnit 5, Cucumber support

---

## [0.15.0] — 2026-05-12

### Added
- **External `@TestData` sources** — `csv:`, `excel:` (Apache POI), `db:` (JDBC) prefixes
- **`TestClock`** — `clock().set("2030-01-01T00:00:00Z")` freezes browser time; `clock().advance()` fast-forwards

---

## [0.14.0] — 2026-05-04

### Added
- **BrowserStack** — `execution.mode: browserstack`; W3C capabilities; mobile devices; session URL in report
- **Sauce Labs** — `execution.mode: saucelabs`; three regions

---

## [0.13.0] — 2026-05-04

### Added
- **Email verification** — `mailbox().waitForEmail(to("user@example.com"))`; Mailhog, Mailtrap, Outlook, IMAP
- `email.assertSubject()`, `email.assertBodyContains()`, `email.extractLink()`

---

## [0.12.0] — 2026-05-03

### Added
- **`@NoBrowser`** — skip WebDriver creation; ideal for DB/API-only tests

---

## [0.11.0] — 2026-05-03

### Added
- **Multi-session testing** — `withSession("alice", () -> { ... })`; named browser sessions
- **Database assertions** — `db().assertRowExists()`, `db().query()`, `db().scalar()`; plain JDBC

---

## [0.10.0] — 2026-05-03

### Added
- **`@Retryable` for JUnit 5** — `InvocationInterceptor` with driver recreation
- **`@Retryable` for Cucumber** — full scenario rerun from step 1

---

## [0.9.0] — 2026-05-02

### Added
- **JUnit 5 support** — `TestFlyExtension` (`@ExtendWith`); full lifecycle management
- `WebDriver` injectable as test method parameter
- `BaseJUnit5Test` base class; `TestFlyLauncherListener` via ServiceLoader

---

## [0.8.0] — 2026-05-02

### Added
- **Cucumber integration** — `BaseCucumberTest` + `BaseCucumberSteps`; auto driver lifecycle per scenario
- `CucumberStepLogger` pipes Gherkin steps into HTML report

---

## [0.7.0] — 2026-04-16

### Added
- **Self-healing locators** — fallback through `id`, `name`, `text`, `class`, `data-testid`; `⚠ healed` badge
- **AI failure analysis** — root-cause + fix suggestion in HTML report via AI model
- **Flakiness prediction** — STABLE/WATCH/HIGH classification; Flakiness Radar card

---

## [0.6.0] — 2026-04-16

### Added
- **Trace viewer** — self-contained HTML trace per failed test; step timeline with screenshots
- **Visual regression** — `VisualAssert.assertScreenshot()` pixel comparison; auto-baseline
- **Device emulation** — `DeviceEmulator.emulate("iPhone 14")`; 6 built-in profiles
- **Network interception** — `NetworkMock.stub(pattern)` via CDP
- **Fluent Locator API** — `find(css)` chainable; `filter()`, `withText()`, `nth()`
- **Web-first assertions** — `assertThat(By)` with `isVisible`, `hasText`, `count`

---

## [0.5.0] — 2026-04-07

### Added
- **Shadow DOM helpers** — `shadowFind`, `shadowClick`, `shadowPierce`
- **Angular/React waits** — `waitForAngular()`, `waitForReactHydration()`
- **Enhanced HTML report** — pass rate gauge, donut chart, dark mode, search
- **Allure adapter** — opt-in Allure 2 JSON results
- **Slack / Teams notifications** — webhook-based post-suite summary
- **`@DependsOnApi`** — skip test if endpoint unreachable

---

## [0.4.0] — 2026-03-28

### Added
- **Schema validation** — `res.assertSchema("schemas/user.json")`
- **`@UseAuth` annotation** — apply named auth strategy from config
- **OAuth2 client credentials** — `ApiAuth.oauth2()` with token caching

---

## [0.3.0] — 2026-03-25

### Added
- **`BaseApiTest`** — pure API testing without browser
- **`ApiClient`** — fluent HTTP client (JDK `HttpClient`)
- **`ApiResponse`** — JSONPath extraction, fluent assertions
- **`ApiAuth`** — Bearer token and Basic auth strategies
- **`ScenarioContext`** / **`SuiteContext`** — thread-local and global state stores

---

## [0.2.0] — 2026-03-22

### Added
- **`@TestData`** — annotation-driven test data from JSON/YAML; env overrides
- **Browser matrix** — run every test on every browser in one `mvn test`
- **`SessionCache`** — cross-thread session reuse
- **Soft assertions** — collect failures, flush at test end

---

## [0.1.0] — 2026-03-16

### Added
- **`BaseTest`** — test base class with framework lifecycle
- **`BasePage`** — page object base: `click`, `type`, `getText`, `withinFrame`
- **`WaitEngine`** — centralized explicit waits
- **`Locator`** — fluent auto-waiting locator chain
- **`StepLogger`** — named steps with timestamps and screenshots
- **`DriverManager`** — thread-local WebDriver lifecycle
- **`testfly.yml`** — convention-over-configuration YAML
- **HTML report** — tabbed dashboard, pass rate gauge, retry badges
- **JUnit XML** + **CI auto-detection** (GitHub Actions, Jenkins, CircleCI, GitLab CI)
- **Build quality gates** — pass-rate and flaky-test thresholds
- **Plugin system** — SPI-based `TestFlyPlugin`, custom driver providers, report adapters
- **`@PreCondition`** — session-aware pre-conditions with cookie caching
- **`@Retryable`** — per-method retry with config
- Chrome + Firefox providers with auto download directory
- Console error collector, download manager, iFrame helpers, alert handling
