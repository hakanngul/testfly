# AREA A — Core Framework & Architecture (audit findings)

Scope: `src/main/java/io/testfly/` (driver, lifecycle, listeners, hooks, precondition, session, internal, test, junit5, cucumber, execution, reporting registry, retry).
Baseline: branch `chore/docs-cloudflare-workers`, `mvn test` = 1361 run / 0 F / 0 E / 0 S (cited from `build-test.md`, not re-run). **Passing tests do not cover the defects below** (see ARCH-020).
Method: full read of `DriverManager`, `TestFlyContext`, `FrameworkBootstrap`, `TestExecutionListener`, `SuiteExecutionListener`, `RetryListener`, `HookRegistry`, `MultiSessionManager`, `PreCondition*`, `TestFlyExtension`, `CucumberHooks` (key parts), driver providers, registries; targeted reads elsewhere. Several findings were reproduced with throw-away programs in `target/audit-scratch/area-a-tmp/` (outside `src/`; no repo files changed, no git mutation).

Classification legend: DOĞRULANDI (verified by code reading and/or repro) · GÜÇLÜ ŞÜPHE (strong code evidence, not executed end-to-end) · İYİLEŞTİRME ÖNERİSİ (design/maintainability suggestion).

## Summary table

| ID | Pri | Class | Title |
|---|---|---|---|
| ARCH-001 | P1 | DOĞRULANDI | "loadtest" substring heuristic misclassifies `*UploadTest` / `*DownloadTest` and blocks the browser |
| ARCH-002 | P1 | DOĞRULANDI | Driver self-healing is a no-op under `per-suite` lifecycle (returns the dead driver) |
| ARCH-003 | P1 | DOĞRULANDI | Session-permit leak when `driver.quit()` throws → 30 s timeout cascade |
| ARCH-004 | P1 | DOĞRULANDI | `quitAllSuiteDrivers()` (global) is called from per-class hooks (JUnit 5 `afterAll`, Cucumber `@AfterAll`) |
| ARCH-005 | P1 | DOĞRULANDI | `@BeforeMethod` runs before the driver exists, `@AfterMethod` after it is quit (TestNG ordering) |
| ARCH-006 | P1 | DOĞRULANDI | TestNG retries never recorded → `ci.maxFlakyTests` gate inert; retried attempt reported as SKIPPED |
| ARCH-007 | P1 | DOĞRULANDI | Fixed 30 s slot wait contradicts "tests wait for a slot"; no `threadCount` vs `maxActiveSessions` check |
| ARCH-008 | P2 | GÜÇLÜ ŞÜPHE | `TestExecutionListener` cleanup has no try/finally (JUnit 5 extension has one) |
| ARCH-009 | P2 | DOĞRULANDI | Thread-count hard cap `2×CPU` applied to remote/cloud modes |
| ARCH-010 | P2 | DOĞRULANDI | SPI file ships Allure/ReportPortal adapters: Allure always runs; duplicated when enabled |
| ARCH-011 | P2 | DOĞRULANDI | `@Retryable` semantics broken/dead (class-level ignored, no opt-in, contradictory Javadoc, default retries everything) |
| ARCH-012 | P2 | DOĞRULANDI | `jsErrorsLogged` ThreadLocal stays `true` after success→failure redirect |
| ARCH-013 | P2 | GÜÇLÜ ŞÜPHE | `@PreCondition` cookie/localStorage restore on a fresh (blank) browser silently does nothing |
| ARCH-014 | P2 | DOĞRULANDI | Class-level `@PreCondition` ignored by TestNG path (supported by JUnit 5 path) |
| ARCH-015 | P2 | DOĞRULANDI | `BrowserContext` override (browser matrix / `testfly.browser`) ignored by Remote/BrowserStack/SauceLabs providers |
| ARCH-016 | P2 | DOĞRULANDI | `ExecutionMetrics` keyed by qualified method name → DataProvider / `invocationCount` collisions |
| ARCH-017 | P2 | GÜÇLÜ ŞÜPHE | `getDriver()` hides health-check + recreate (extra RPC per call; pending alert ⇒ browser replaced) |
| ARCH-018 | P2 | GÜÇLÜ ŞÜPHE | Suite lifecycle: once-per-JVM init vs per-suite teardown; partial init is unrecoverable; `onFinish` not exception-safe |
| ARCH-019 | P2 | DOĞRULANDI | `@TestFlyApi(since=…)` metadata inconsistent with project version; internal helpers frozen as "stable" |
| ARCH-020 | P2 | DOĞRULANDI | Real `DriverManager` lifecycle has zero tests (22 `mockStatic` uses only) |
| ARCH-021 | P2 | İYİLEŞTİRME ÖNERİSİ | SRP/DRY: `TestExecutionListener` god-class, 3× cleanup copy, 4× load-test heuristic, layering inversion |
| ARCH-022 | P3 | İYİLEŞTİRME ÖNERİSİ | Logging fragmentation (156 `System.out/err`, 25 JUL files, 3 SLF4J) and ~120 swallowed exceptions |
| ARCH-023 | P3 | GÜÇLÜ ŞÜPHE | Browser process leak if provider fails after construction; `DRIVER.set` before post-steps that can throw (double release) |
| ARCH-024 | P3 | DOĞRULANDI | `clearCurrentTestId()` leaves test class/method ThreadLocals stale |
| ARCH-025 | P3 | İYİLEŞTİRME ÖNERİSİ | Static registries: unsynchronized reads of mutable collections; locks on public `Class` objects |
| ARCH-026 | P2 | GÜÇLÜ ŞÜPHE | JVM shutdown hook only knows `per-suite` drivers; per-test/remote sessions orphaned on abnormal exit |
| ARCH-027 | P3 | İYİLEŞTİRME ÖNERİSİ | `SmartLocator` does not wait; other ThreadLocal state not cleared on worker threads |

---

## Group 1 — Driver lifecycle, resource cleanup, thread safety

### ARCH-001 — "loadtest" name heuristic blocks browser for ordinary tests (P1, DOĞRULANDI)
- **Problem / root cause:** Four copies of a substring heuristic decide "this is a load test, never create a WebDriver". The check is `contains("loadtest")` on the lower-cased class name/package/tag. `"fileuploadtest"`, `"downloadtest"`, and a package like `com.acme.uploadtests` all contain `loadtest`.
- **Locations:**
  - `driver/DriverManager.java:439` (`testClass.getName().toLowerCase().contains("loadtest")`) → `getDriver()`/`createDriver()` throw `IllegalStateException`.
  - `listeners/TestExecutionListener.java:410-411` (`pkg.contains("loadtest") || simpleName.contains("loadtest")`) → `skipBrowser()==true`, so no driver is created at all.
  - `junit5/TestFlyExtension.java:518`
  - `cucumber/CucumberHooks.java:432` (tag `@…loadtest…`, e.g. `@uploadtest`).
- **Evidence (repro, scratch program):**
  ```
  loadtest-heuristic: true / true / pkg com.acme.uploadtests: true
  ("FileUploadTest" / "DownloadTest" / package)
  ```
  A `FileUploadTest extends BaseTest` therefore gets no driver (TestNG) and then fails with `[TestFly] WebDriver is not available during Load Testing` on first `open()`.
- **Impact:** File upload/download tests are a core UI scenario; failure is baffling (message blames load testing). No error is raised at class-load time.
- **Fix:** Remove name-based inference. Use only explicit signals (`BaseLoadTest`, `LoadTestSupport`, `@LoadTest`, `@NoBrowser`, `LoadTestRunner.isExecuting()`). Centralise in one `LoadTestDetector` used by all four call sites. Add negative unit tests (`UploadTest`, `DownloadTest`).
- **Effort:** S. **Deps / regression risk:** users who relied on naming convention for load tests (docs should be checked) must add `@LoadTest`/base class; low risk otherwise.
- **Repro:** compile any class `com.x.FileUploadTest extends BaseTest` with one `open()` test and run `mvn test`.

### ARCH-002 — Self-healing recreate does nothing for `per-suite` lifecycle (P1, DOĞRULANDI)
- **Problem / root cause:** `getDriver()` (L300-305) calls `recreateDriver()` (L316) = `quitDriver()` + `createDriver()`. In `per-suite` mode `quitDriver()` returns immediately (L356-358, "driver intentionally kept alive"), so `DRIVER` is still set and `createDriver()` returns at its idempotence guard (L116-118). The dead driver is returned.
- **Evidence (repro with Mockito mock driver whose `getTitle()` throws `NoSuchSessionException`, custom `NamedDriverProvider`):**
  ```
  RESULT lifecycle=per-test  providerCalls=2 getDriver()==deadFirstDriver:false
  RESULT lifecycle=per-suite providerCalls=1 getDriver()==deadFirstDriver:true
  ```
  (`[TestFly] Driver session invalid. Recreating...` is printed in both cases.)
- **Impact:** In `per-suite` mode a crashed session poisons every subsequent test on the thread; log claims recreation happened.
- **Fix:** `recreateDriver()` must use a `forceQuitDriver()`-style teardown (also remove from `SUITE_DRIVERS`, release permit once) before `createDriver()`.
- **Effort:** S. **Deps:** ARCH-003, ARCH-020 (add tests). **Risk:** low.

### ARCH-003 — Session permit leaked when `quit()` throws (P1, DOĞRULANDI)
- **Root cause:** `DriverManager.java:364-365`: `driver.quit(); getOrInitSemaphore().release();` inside one `try`. If `quit()` throws (grid/cloud unreachable, session already gone) the `release()` is skipped; `finally` only removes the ThreadLocals. Same pattern in `forceQuitDriver` (L385-386).
- **Evidence (repro, `maxActiveSessions=1`, mock `quit()` throws):**
  ```
  [TestFly] Driver quit failed: grid unreachable
  RESULT second createDriver FAILED: Timed out waiting for an available session slot after 30s...
  RESULT waited ms=30001
  ```
- **Impact:** After a single failed quit the slot is gone forever; with `maxActiveSessions=5` five such events make every later test block 30 s then fail — a cascade that looks like an infrastructure problem. Most likely exactly when a grid/cloud misbehaves.
- **Fix:** `try { driver.quit(); } catch … finally { release(); DRIVER.remove(); … }` (release exactly once, tracked per driver, e.g. a small `Holder{driver, permitHeld}` ThreadLocal). Same in `quitAllSuiteDrivers` (releases `total` even for failed quits — fine — but ThreadLocals of other threads remain).
- **Effort:** S. **Risk:** low; test with mock throwing `quit()`.

### ARCH-004 — Global `quitAllSuiteDrivers()` invoked from per-class hooks (P1, DOĞRULANDI by code reading)
- **Root cause:** `quitAllSuiteDrivers()` quits **every** driver in the JVM-wide `SUITE_DRIVERS` set (`parallelStream`, L408-412) and clears the set, but is called from:
  - `junit5/TestFlyExtension.java:347-353` (`afterAll`, i.e. at the end of **each test class**),
  - `cucumber/CucumberHooks.java:445-463` (`@AfterAll`, plus TestNG `SuiteExecutionListener.onFinish:250` for the same runner → also double report generation, see ARCH-010).
- **Impact:**
  1. Under JUnit 5 parallel class execution, class A finishing quits class B's live browser mid-test.
  2. Even sequentially, `per-suite` degenerates to per-class (driver recreated for every class), contradicting docs.
  3. Other threads' `DRIVER` ThreadLocals still reference the quit drivers (only the calling thread is `remove()`d, L418) → combined with ARCH-002 the dead driver is never replaced.
- **Fix:** Track suite drivers per owning thread and only quit those created after JVM/suite scope begins; call global teardown from the launcher-level hook (`TestFlyLauncherListener.testPlanExecutionFinished`) / `ExtensionContext.Store` `CloseableResource` on the root context, not `afterAll`. In `afterAll` only release the current thread's driver when lifecycle=per-class semantics are wanted.
- **Effort:** M. **Risk:** medium (behaviour change for per-suite JUnit users; add parity tests).
- **Not executed:** requires JUnit parallel run with a real/mocked driver.

### ARCH-005 — TestNG callback order: driver unavailable in `@BeforeMethod`, already quit in `@AfterMethod` (P1, DOĞRULANDI)
- **Root cause:** The driver is created in `TestExecutionListener.onTestStart` (L96-135) and quit in `onTestSuccess/Failure/Skipped` (L208/296/322). TestNG fires `@BeforeMethod` **before** `onTestStart` and `@AfterMethod` **after** `onTestSuccess`.
- **Evidence (scratch TestNG run, framework not involved):**
  ```
  EVT @BeforeMethod
  EVT onTestStart
  EVT @Test
  EVT onTestSuccess
  EVT @AfterMethod
  ```
  `DriverManager.getDriver()` throws `WebDriver not initialized for current thread.` when `DRIVER` is null (L289-292).
- **Impact:** Any user `@BeforeMethod` that opens a page / logs in via `getDriver()` / `open()` fails; `@AfterMethod` logout/screenshot code fails. The docs actively show this pattern as "the problem it solves" (`docs-site/docs/guides/precondition.md:19-24`) and `MockSupport.resetMocks()` is an `@AfterMethod` default method. No doc states the constraint. `BasePage()` is lazy so page-object construction is OK.
- **Fix:** (a) create the driver lazily on first `getDriver()` when a test context is active (TestNG `beforeInvocation` for configuration methods), or create it in `IInvokedMethodListener.beforeInvocation` for `@BeforeMethod` and quit in `afterInvocation` of `@AfterMethod`; (b) at minimum document + give an actionable exception message ("driver is only available inside @Test; use @PreCondition").
- **Effort:** M (a) / S (b). **Risk:** medium: lifecycle change affects metrics ordering; guard with config flag.

### ARCH-006 — TestNG retries invisible to metrics; retried attempt reported as SKIPPED (P1, DOĞRULANDI)
- **Root cause:** `ExecutionMetrics.recordRetry` (`metrics/ExecutionMetrics.java:241`) has callers only in `CucumberHooks.java:88` and `TestFlyExtension.java:382`; **none in the TestNG path** (grep over `src/main`). `BuildThresholdEnforcer.java:58` counts flaky tests as `retryCount > 0 && PASSED`; HTML/Allure also read `retryCount`.
- **Evidence (scratch TestNG run with retry analyzer returning true once):**
  ```
  EVT onTestStart
  EVT afterInvocation status=2          (FAILURE)
  EVT onTestSkipped wasRetried=true     <- framework onTestSkipped runs
  EVT onTestStart
  EVT afterInvocation status=1
  EVT onTestSuccess
  ```
  `TestExecutionListener.onTestSkipped` (L309-330) therefore records status "SKIPPED", calls `HookRegistry.onTestEnd(..,"SKIPPED")` and pushes `"SKIPPED"` to TestRail/Xray (`TestManagementReporter.onTestResult`) for an attempt that is merely being retried.
- **Impact:** `ci.maxFlakyTests` quality gate and flakiness reporting are silently inert for the primary (TestNG) runner; external systems get spurious SKIPPED results. Failure data of the first attempt (`recordError`, screenshot via `afterInvocation`) remains attached to a later PASSED entry (GÜÇLÜ ŞÜPHE – not confirmed in report output).
- **Fix:** In `onTestSkipped`, `if (result.wasRetried()) { ExecutionMetrics.recordRetry(testId); cleanup only; return; }` — skip hooks/test-management/status. Add unit test using `wasRetried()`.
- **Effort:** S. **Risk:** low.

### ARCH-007 — Slot wait is a fixed 30 s (not "wait"), and config is not cross-validated (P1, DOĞRULANDI)
- **Root cause:** Class Javadoc says tests "wait for a slot rather than failing fast", but `DriverManager.java:131` and `:213` use `tryAcquire(30, SECONDS)` and throw. Default `maxActiveSessions = 5` (`TestFlyConfig.java:218`); `ExecutionValidator` (L38-42) validates `threadCount` only against CPU count, never against `maxActiveSessions`. Named sessions (`MultiSessionManager`) also draw from the same pool, so a test using 2 named sessions with `threadCount=4, maxActiveSessions=5` can deadlock: each thread holds one permit and waits for the second.
- **Impact:** E2E tests commonly last >30 s; any `threadCount > maxActiveSessions` produces "Timed out waiting for an available session slot" failures that depend on timing (flaky by design). Hold-and-wait deadlock possible with multi-session tests.
- **Fix:** Make acquisition timeout configurable (`execution.sessionWaitSeconds`, default large or unbounded), validate `threadCount <= maxActiveSessions` (or auto-raise maxActiveSessions), and acquire all permits for a test atomically or document the sizing rule (`maxActiveSessions >= threadCount * maxPerTest`).
- **Effort:** S–M. **Risk:** low.

### ARCH-008 — No try/finally around TestNG listener cleanup (P2, GÜÇLÜ ŞÜPHE)
- **Evidence:** `onTestSuccess` (L138-217) executes ~20 statements (metrics, recording, performance, hooks, TestRail, `TestClock.autoReset`) and only then `DriverManager.quitDriver()` (L207-208); no `finally`. Compare `TestFlyExtension.afterEach` which wraps cleanup in `try … finally` (L~330). `HookRegistry` catches only `Exception` (not `Error` such as `NoClassDefFoundError` from optional agents); `ConsoleErrorCollector.collect()` (L141-143) calls `DriverManager.getDriver()` unguarded and throws `IllegalStateException` if no driver. Any such throw skips `quitDriver()` → browser + permit leak (ARCH-003 consequence).
- **Fix:** restructure into `try { reporting… } finally { cleanupThreadState(); }` shared by the three callbacks (also fixes ARCH-021 duplication).
- **Effort:** S–M. **Risk:** low. Not reproduced (needs a forced exception in a hook-chain with real TestNG run).

### ARCH-017 — `getDriver()` does a hidden health check and may silently replace the browser (P2, GÜÇLÜ ŞÜPHE)
- **Evidence:** `DriverManager.getDriver()` (L278-312) performs (a) `isLoadTestActive()` (reflection/annotation lookups + string ops) and (b) `isDriverAlive()` → `driver.getTitle()` (L338) on **every** call; every `WaitEngine`/`Locator`/`BasePage` operation goes through it. On any exception the browser is quit and a new, blank one is created ("Recreating…" to stderr only).
- **Impact:** (1) one extra WebDriver round trip per framework call – measurable on remote/cloud grids; (2) `getTitle()` throws `UnhandledAlertException` when an alert is open (the Chrome provider deliberately sets `unhandledPromptBehavior=ignore`, `LocalChromeDriverProvider.java:119`), so a test that is *expecting* an alert has its browser replaced and loses page state; (3) mid-test session loss is masked: the test continues on an empty browser and fails later with misleading "element not found". Cleanup paths (`TestClock.executeReset`, `ScreenshotManager.capture`) can also spin up a brand-new browser just to tear down.
- **Fix:** `getDriver()` = pure accessor; do health checks only at test boundaries or on `NoSuchSessionException` (catch-and-retry at `WaitEngine`/`Locator` level); treat `UnhandledAlertException` as alive; cache `isLoadTestActive` per test.
- **Effort:** M. **Risk:** medium (behaviour change). Not reproduced against a real browser (no browser in audit env).

### ARCH-023 — Browser leak on partial provider failure; double permit release risk (P3, GÜÇLÜ ŞÜPHE)
- `LocalChromeDriverProvider.java:23-25`: `new ChromeDriver(options)` then `manage().timeouts()...`; if the latter throws (e.g. invalid `pageLoad`), the started browser is never quit (same shape in Firefox/Edge providers).
- `DriverManager.createDriver` stores `DRIVER.set(driver)` at L157, then runs more code (cloud URL, metrics, `System.out`) inside the same `try` whose `catch` does `semaphore.release()` (L192). If a later statement throws, the permit is released **and** `DRIVER` remains set → the later `quitDriver()` releases again (semaphore over-count, more concurrent browsers than `maxActiveSessions`).
- **Fix:** wrap provider internals in try/catch-quit; set `DRIVER` as the last step or move post-steps outside the permit-release `catch`. **Effort:** S.

### ARCH-026 — Shutdown hook cannot clean per-test drivers (P2, GÜÇLÜ ŞÜPHE)
- `DriverManager.java:56-63` hook calls `quitAllSuiteDrivers()` + `forceQuitDriver()`. Per-test drivers (default lifecycle) are only in a ThreadLocal; the hook thread's `DRIVER` is null, so on SIGTERM/timeout/Ctrl-C they are not quit. Local chromedriver is usually stopped by Selenium's own service hook, but remote/BrowserStack/Sauce sessions stay open until server-side timeout (billable).
- **Fix:** register every driver in the JVM-wide concurrent set (not only per-suite) and drain it in the hook. **Effort:** S. Not reproduced (needs real cloud session).

---

## Group 2 — Retry, timeouts, listeners

### ARCH-009 — Thread cap of `2 × availableProcessors` applies to remote/cloud (P2, DOĞRULANDI)
`execution/ExecutionValidator.java:38-42` throws `Thread count N exceeds safe limit` regardless of `execution.mode`. With Selenium Grid/BrowserStack the JVM only drives HTTP calls, so a 2-core CI container cannot run >4 parallel sessions. **Fix:** apply the CPU cap only to `mode=local`; for remote/cloud validate against `maxActiveSessions`. **Effort:** S.

### ARCH-011 — `@Retryable` / RetryListener semantics (P2, DOĞRULANDI)
- `listeners/RetryListener.java:56-60`: `boolean isGlobalRetry = retryConfig.isEnabled();` after the early return on `!isEnabled()` (L50-53) is always `true`, so `!isGlobalRetry && !isAnnotated` is dead code, and `isAnnotated` only influences the max-attempts override.
- Class Javadoc ("allows per-method opt-in when global retry is off") and `Retryable` Javadoc ("Marks a test method as eligible for retry") are contradicted: `retry.enabled=false` disables `@Retryable` too; `retry.enabled` **defaults to true** with `maxAttempts=1` (`TestFlyConfig.java:497-499`) → every failing test is retried once by default; `@Retryable` has no opt-in effect.
- `@Retryable` has `@Target({METHOD, TYPE})` but only `method.isAnnotationPresent` is consulted → class-level annotation is ignored.
- `maxAttempts` naming: Javadoc says "Total runs = maxAttempts + 1" (retries, not attempts).
- **Fix:** decide intended contract (opt-in vs global), implement `method`/`declaringClass` lookup, remove dead branch, rename or document. Update docs. **Effort:** S. **Risk:** behaviour change → major-version note, since default behaviour (retry-all) is relied upon.

### ARCH-012 — Stale `jsErrorsLogged` flag (P2, DOĞRULANDI)
`TestExecutionListener.java:147` sets `jsErrorsLogged=true` in `onTestSuccess`; the flag is cleared only at the end of the normal path (L217). The two redirect paths (`failOnConsoleErrors`, L150-158; soft assertions) call `onTestFailure(result); return;` without clearing it. The next failing test on the same worker thread then skips console-error collection (L245 `!jsErrorsLogged.get()`). **Fix:** reset in `onTestStart` and in the `finally` (ARCH-008). **Effort:** S.

### ARCH-016 — Metrics keyed by `getQualifiedName()` (P2, DOĞRULANDI key; downstream impact inferred)
`TestExecutionListener.java:99` uses `result.getMethod().getQualifiedName()` as `testId`; `ExecutionMetrics.START_TIMES/TIMINGS` are `ConcurrentHashMap<String,…>` (L18-20) and `markEnd` does `START_TIMES.remove(testId)` (L286). `@Test(dataProvider=…)` rows and `invocationCount>1` share one id, so rows overwrite each other's status/time, parallel invocations corrupt start times, and pass-rate/threshold calculations (`BuildThresholdEnforcer`) count one test per method. **Fix:** include `result.getParameters()` hash / `getCurrentInvocationCount()` / `getInstanceName()` in the id. **Effort:** M (report schema + history files keyed by id). **Risk:** medium (flakiness history keys change).

### ARCH-018 — Suite lifecycle mismatches (P2, GÜÇLÜ ŞÜPHE)
1. `FrameworkBootstrap.initialize()` is once per JVM (early return if `TestFlyContext.isInitialized()`, L38-40), but `SuiteExecutionListener.onFinish` is per suite and calls `PluginRegistry.unloadAll()` (clears plugins, `extension/PluginRegistry.java:65`), `PreConditionRunner.clearAll()`, `DriverManager.quitAllSuiteDrivers()`, exports metrics. A second `<suite>` in the same JVM (multi-suite testng.xml / surefire `suiteXmlFiles`) runs without plugins, with accumulated metrics (`ExecutionMetrics` is not reset between suites).
2. `TestFlyContext.initialize(config)` is called at `FrameworkBootstrap.java:50`, **before** `DriverProviderRegistry/HookRegistry/ReportAdapterRegistry/PluginRegistry` loading (L58-66). If any of those throws, `isInitialized()` is already true; subsequent `initialize()` returns early, leaving the framework half-configured (and other threads can observe an initialized context before registries are loaded).
3. `onFinish` (L235-258) is a straight line of calls without `try/finally`: an exception from `exportToJson`/`JUnitXmlReporter.export`/`FlakinessAnalyzer` skips `quitAllSuiteDrivers`, `HookRegistry.onSuiteEnd`, `PluginRegistry.unloadAll` and the build-threshold gate.
- **Fix:** initialise into a local, publish context last; make teardown steps individually guarded; scope plugin/adapter lifetime to JVM or reload per suite. **Effort:** M. Not reproduced (requires multi-suite run / injected failure).

### ARCH-010 — Report-adapter SPI duplicates and unconditional Allure (P2, DOĞRULANDI)
- `src/main/resources/META-INF/services/io.testfly.reporting.ReportAdapter` lines 3-4 list `AllureReportAdapter` and `ReportPortalReportAdapter`. `ReportAdapterRegistry.loadAll()` loads them via `ServiceLoader` for every consumer.
- Repro (`ServiceLoader.load(ReportAdapter.class)` on `target/classes`): `SPI adapter: io.testfly.reporting.AllureReportAdapter` and `…ReportPortalReportAdapter`.
- `AllureReportAdapter.generate` (L~50) has **no** `reporting.allure.enabled` check → writes `target/allure-results` always. When enabled, `FrameworkBootstrap.java:74` registers a second instance → two result files per test (random `UUID` per call, L85) and duplicate attachments. `ReportPortalReportAdapter` checks `enabled` but runs twice and prints twice.
- Same duplication in Cucumber/TestNG runs: `BaseCucumberTest` attaches `SuiteExecutionListener` (`onFinish` generates reports) **and** `CucumberHooks.afterAllScenarios` (`@AfterAll`) repeats `exportToJson/generateAll/onSuiteEnd` → reports and hooks executed twice (GÜÇLÜ ŞÜPHE, depends on cucumber-testng calling `@AfterAll`).
- **Fix:** delete lines 3-4 from the SPI file (keep programmatic opt-in), or make adapters self-gating + `register` de-duplicate by `getName()`; make suite teardown idempotent (flag). **Effort:** S.

---

## Group 3 — PreCondition / sessions / providers

### ARCH-013 — `@PreCondition` restore on blank browser (P2, GÜÇLÜ ŞÜPHE)
`PreconditionSessionCache.restore` (L56-70): `deleteAllCookies()`, `addCookie(...)` in `try { } catch (Exception ignored)`, then localStorage via JS in another swallow-all try. In the default `per-test` lifecycle each test starts on `about:blank`; Selenium rejects `addCookie` for a domain different from the current page (`InvalidCookieDomainException`) and `localStorage` access on `about:blank` throws `SecurityError`. The Javadoc says it "Navigates to the current URL to apply cookies" but no navigation exists. Result: "[PreCondition] Restoring cached session" is printed, nothing is restored, the test runs unauthenticated, with no log of the swallowed errors. Unit tests mock the driver, so they cannot see this.
- **Fix:** record the origin URL in `SavedSession` and navigate (or CDP `Network.setCookies`) before restoring; log failures; fall back to re-running the provider when restore failed. **Effort:** M. Needs real-browser verification (not available in this audit).

### ARCH-014 — Class-level `@PreCondition` ignored for TestNG (P2, DOĞRULANDI)
`PreCondition` has `@Target({METHOD, TYPE})`. `PreConditionRunner.run(ITestResult)` reads only `testMethod.getAnnotation(...)` (L39); the JUnit overload (L88-93) falls back to the declaring class. Same annotation, different behaviour per runner. **Fix:** share one resolver. **Effort:** S.

### ARCH-015 — Browser matrix ignored on Remote/Cloud (P2, DOĞRULANDI)
`DriverProviderFactory.getProvider` computes `browser` from `BrowserContext` (set by `testfly.browser` / matrix), but returns `RemoteDriverProvider`, `BrowserStackProvider`, `SauceLabsProvider` **before** using it; `RemoteDriverProvider.java:20` reads `config.getBrowser().getName()`, `BrowserStackProvider.java:52` reads `bs.getBrowser()`; none reference `BrowserContext` (grep). `ExecutionMetrics.recordBrowser` still labels the test with the matrix browser, so reports claim runs on browsers that were never used. **Fix:** pass resolved browser into providers. **Effort:** S–M.

### ARCH-024 — Stale test-class/method ThreadLocals (P3, DOĞRULANDI)
`TestFlyContext.clearCurrentTestId()` (L72) clears only `CURRENT_TEST`; `CURRENT_TEST_CLASS/METHOD` are cleared only by `clearCurrentTest()` (L88-92). TestNG listener uses the former (L216/305/330), JUnit uses the latter. On pooled worker threads `isLoadTestActive()` can read the class of the previous test (config methods, Cucumber which never sets it). **Fix:** one `clearCurrentTest()` everywhere. **Effort:** S.

---

## Group 4 — API contract, tests, maintainability

### ARCH-019 — `@TestFlyApi` metadata and surface (P2, DOĞRULANDI)
- 189 `@TestFlyApi(since=…)` annotations in `src/main/java`; **146 claim a version newer than `pom.xml` 1.0.7** (values up to `3.0.0`, e.g. `testmanagement/TestRailCase.java`, `XrayTest.java`; `2.x` in `email/*`, `clock/TestClock.java`, `accessibility/*`, `performance/*`). `AGENTS.md` says current 1.1.0. `since` therefore cannot be used to reason about the stability contract or deprecation windows.
- Type-level `@TestFlyApi` on `DriverManager` (and `MultiSessionManager`) freezes internal plumbing as stable public API: `acquirePermit()`, `releasePermit()`, `pushSessionOverride()`, `popSessionOverride()`, `recreateDriver()`, `quitAllSuiteDrivers()`. Fixing ARCH-002/003/004 will want to change these.
- `DriverManager.popSessionOverride()` Javadoc is detached (a second Javadoc block sits between it and `getCloudSessionUrl`, L~236-250).
- Not flagged: `LocatorSupport` `$()` methods are `@Deprecated(since="1.1.0", forRemoval=true)` (L74-95) – supported deprecated API.
- **Fix:** normalise `since` to real release history; move permit/override helpers to a package-private/`internal` class; annotate at method level for the genuinely stable subset. **Effort:** M. **Risk:** low for metadata; moderate for API moves (deprecate first).

### ARCH-020 — Core lifecycle untested (P2, DOĞRULANDI)
No `DriverManagerTest` exists. The 12 test files that mention `DriverManager` use `mockStatic(DriverManager)` (22 occurrences) and never call the real `createDriver/quitDriver/recreateDriver/acquirePermit/releasePermit/quitAllSuiteDrivers` (grep of `src/test` excluding `verify/when/mockStatic` = no hits). That is why ARCH-002/003/023 survived 1361 green tests. **Fix:** add a `DriverManagerTest` with a fake `NamedDriverProvider` + Mockito `WebDriver` (as used for the repros in `target/audit-scratch/area-a-tmp/src/Heal.java`, `Leak.java`); add listener tests for retry/`wasRetried`, `BeforeMethod` ordering and JS-flag reset. **Effort:** M.

### ARCH-021 — SRP / DRY / layering (P2, İYİLEŞTİRME ÖNERİSİ)
- `TestExecutionListener` (664 lines) mixes test lifecycle, quarantine, API-health, auth strategy resolution (`resolveAuthStrategy`, `resolveEnvVar` – duplicates `DotEnvLoader.resolve`), ReportPortal reflection, AI analysis, recording, JS-console policy. Success/failure/skipped callbacks repeat the same ~10-line teardown block three times (L205-217, L294-305, L320-330) – already diverged (`NetworkMock.cleanup()` missing in skipped; `SoftAssertions.clear()` missing in success path ordering).
- The same lifecycle logic is re-implemented in `TestFlyExtension` (634 lines) and `CucumberHooks` (469 lines) with subtle differences (PreCondition class-level, quarantine, retry), the root of ARCH-006/012/014.
- `DriverManager` depends upward on `io.testfly.loadtest.*` and `io.testfly.test.support.LoadTestSupport` (L429-440); `config.TestFlyConfig` is a 2,473-line nested-class file.
- **Fix:** extract a runner-agnostic `TestLifecycle` (begin/end/cleanup) used by the three adapters; move auth-resolution to `client`; invert the load-test dependency (`DriverGuard` interface registered by loadtest module). **Effort:** L. **Risk:** high-touch; do after tests from ARCH-020.

### ARCH-025 — Static registries (P3, İYİLEŞTİRME ÖNERİSİ)
`DriverProviderRegistry.find` (L49) reads a plain `LinkedHashMap` that `register/loadAll` mutate under `synchronized`; `PreConditionRegistry.providers` (`ArrayList`) is iterated in `find` while `register` can run (`synchronized` only on writers); `ReportAdapterRegistry.adapters` fine (all synchronized). `HookRegistry` has no `reset()` (cannot be cleaned between suites/tests). `FrameworkBootstrap.initialize` (L36-37) holds the monitors of the public classes `TestFlyContext.class` and `ConfigurationLoader.class` during file I/O and registry loading (any user `synchronized(TestFlyContext.class)` or `static synchronized` caller blocks; `ConfigurationLoader` has no synchronized members, so that lock is pointless). **Fix:** `ConcurrentHashMap`/`CopyOnWriteArrayList`, private lock object. **Effort:** S.

### ARCH-022 — Logging and error-swallowing (P3, İYİLEŞTİRME ÖNERİSİ)
156 `System.out/err.print*` calls in main code; 25 files use `java.util.logging`, 3 use SLF4J (`TestExecutionListener` mixes all three); 120 `catch (… ignored)` sites (e.g. `TestExecutionListener.java` L~150/250/337/352/..., `DriverManager.java:162-171`). Consumers cannot silence or route logs, and failures (cookie restore, recording start, metrics) disappear. **Fix:** single `TestFlyLog` facade over SLF4J (already on classpath), at least `debug` logging in swallow sites. **Effort:** M (mechanical).

### ARCH-027 — Minor (P3, İYİLEŞTİRME ÖNERİSİ)
- `test/SmartLocator.find` calls `driver.findElement` once per locator with no wait (implicit wait is forced to 0 in providers), so "resilient" lookup fails on any element that is not already displayed; contradicts the "all waits via WaitEngine" rule. Prints with `System.out` on every success.
- `PreConditionRunner.clearAll()` (called from `SuiteExecutionListener.onFinish`) only clears the finishing thread's ThreadLocal map; `PreconditionSessionCache`, `RecordingManager.SESSION`, `BasePage.FRAME_DEPTH` on worker threads are not cleared at suite end (bounded by pool lifetime).
- `RetryListener.attempt` is per analyzer instance; verified by scratch run that TestNG creates a separate analyzer per DataProvider row (no cross-row sharing) – **no defect**.

---

## Checked and found sound (no finding)
- `DriverManager` ThreadLocal ownership + `SESSION_STACK` push/pop with `try/finally` in `MultiSessionManager.withSession`; `clearAll()` releases a permit per named session even if `quit()` throws.
- `TestFlyContext.config` published through `AtomicReference`; `Locator.testIdAttribute` is `volatile`.
- `HookRegistry` isolates per-hook exceptions (only `Exception`, see ARCH-008).
- `TestFlyExtension.afterEach` cleans in `finally`.
- `RetryAnnotationTransformer` is registered through `META-INF/services/org.testng.ITestNGListener` (works; `@Listeners` would not have worked for an annotation transformer).
- Deprecated `$()` API (`LocatorSupport`) not flagged: supported, deprecated with removal notice.

## Not audited / limitations
- No real browser/grid in the audit environment: ARCH-013, 017, 026 and the JUnit-parallel part of ARCH-004 were not executed end-to-end.
- Not covered (owned by other areas): `ApiClient`/`ApiExecution`/`ApiTransport` internals (only cleanup hooks looked at), `loadtest` runner, `reporting` HTML/JUnit XML generators, `locator/Locator` + `WaitEngine` internals (skimmed: timeouts come from config, `Thread.sleep` only in `waitMillis`/Network/Download/Mailbox polling), `config/ConfigurationLoader` parsing, `healing`, `network`, `visual`, `recording` frame capture, `sharding`, `flakiness` scoring, `ai`.
- Concurrency findings are from code reading plus single-thread repros; no stress/parallel run was done (full suite deliberately not re-run).

## Repro artifacts (scratch, outside `src/`)
`target/audit-scratch/area-a-tmp/src/{Order,Retry,Retry2,Spi,Heal,Leak}.java` (+ compiled `out/`). Run with `java -cp out:target/classes:$(cat target/audit-scratch/classpath.txt) <Class>`.
