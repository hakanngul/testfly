# AREA D — Test Quality & Reliability (TestFly audit)

Scope: `src/test/java/io/testfly/**` (163 files; 123 test classes run by default; ~23.7k LOC excluding examples), `pom.xml` surefire/jacoco/failsafe config, `.github/workflows/testfly-ci.yml`, `ci/Jenkinsfile`.
Method: static reading + heuristics (scripts in `target/audit-scratch/`: `noassert.py`, `untested.txt`, `alltests.txt`). No repo file was modified. The full suite was NOT re-run; counts below are cited from `target/audit-scratch/build-test.md` / `test.log`.

## Cited build/test baseline (from setup run)
- `mvn test`: BUILD SUCCESS, **Tests run: 1361, Failures: 0, Errors: 0, Skipped: 0**, total 50.3 s (test.log tail).
- test.log: `[INFO] --- jacoco:0.8.12:report (report) @ testfly ---` → **`Skipping JaCoCo execution due to missing execution data file.`** (see TST-001). `target/jacoco.exec` does not exist.
- test.log: `AnnotationTransformer already set` ×134 (one per forked JVM; see TST-014).
- Reconciliation: 1399 `@Test` methods found by regex outside `examples/`; minus 39 in `integration/` (excluded from default run) = 1360, ≈ 1361 run (1 data-provider expansion). Consistent, so nothing is silently skipped in the default run. Passing ≠ correct: the findings below show green tests that assert nothing or that cover nothing.

## Summary table

| ID | Title | Class. | Pri | Effort |
|---|---|---|---|---|
| TST-001 | JaCoCo never collects data; coverage gate is a no-op | DOĞRULANDI | P1 | S |
| TST-002 | `DriverManager` lifecycle has no direct unit tests | DOĞRULANDI | P1 | M |
| TST-003 | Session-permit leak when `driver.quit()` throws (found because of TST-002) | DOĞRULANDI (static read; not dynamically reproduced) | P1 | S |
| TST-004 | `RetryListener` contract contradicts its Javadoc/`@Retryable`; tests miss it | DOĞRULANDI | P1 | S |
| TST-005 | `WaitEngine`: core waits (`waitForVisible/Clickable/…`), timeout and heal paths untested | DOĞRULANDI | P1 | M |
| TST-006 | Cross-JVM race on shared `target/testfly-reports/*` under `forkCount=4` | GÜÇLÜ ŞÜPHE | P2 | S |
| TST-007 | No test runs real TestNG/JUnit5 with the framework listeners wired (retry→skip→metrics) | DOĞRULANDI | P2 | M |
| TST-008 | Tests with no assertion / "does not throw" masquerading as behaviour tests | DOĞRULANDI | P2 | M |
| TST-009 | `ReportPortalReportAdapterTest` "valid" case uses malformed JSON and asserts nothing | DOĞRULANDI | P2 | S |
| TST-010 | `ConfigurationLoaderTest` is shallow and fixture-coupled; validation/priority/env paths untested | DOĞRULANDI | P2 | M |
| TST-011 | 38 production classes have no test reference (incl. `RetryAnnotationTransformer`, `SmartLocator`, `BrowserSessionCache`, `PerformanceCollector`, `ApiMockServer`) | DOĞRULANDI | P2 | L |
| TST-012 | CI "integration" job silently skips everything without `AI_API_KEY`; never runs a real browser | DOĞRULANDI | P2 | S |
| TST-013 | Parallel-isolation weaknesses: non-`singleThreaded` classes touching global statics; try/finally gaps | GÜÇLÜ ŞÜPHE | P3 | S |
| TST-014 | Surefire config: no timeouts/rerun policy, thread oversubscription, `reuseForks=false` masks static leaks, duplicate transformer registration | İYİLEŞTİRME ÖNERİSİ | P3 | S |
| TST-015 | Timing- and `Thread.sleep`-based tests | GÜÇLÜ ŞÜPHE | P3 | S |
| TST-016 | Assertion-style defects (swapped expected/actual, `assertNotNull` on fluent return, swallowed catches) | İYİLEŞTİRME ÖNERİSİ | P3 | S |
| TST-017 | `-Pquality` (SpotBugs/Checkstyle/PMD) and examples never run in CI | DOĞRULANDI | P3 | S |

---

## Group 1 — Build/coverage infrastructure

### TST-001 — JaCoCo collects nothing; the 40 %/30 % coverage gate can never fail  (P1, DOĞRULANDI, effort S)
- **Problem / root cause:** surefire's `<argLine>` is a literal string, which *replaces* the `argLine` property that `jacoco:prepare-agent` populates. The agent is therefore never attached to the forked JVMs → no `jacoco.exec`.
- **Where:** `pom.xml:~335` (`<argLine>-Dnet.bytebuddy.experimental=true --add-opens java.base/java.lang=ALL-UNNAMED</argLine>`); jacoco plugin `pom.xml:~447-485` (`report` @test, `check` @verify with LINE 0.40 / BRANCH 0.30).
- **Evidence:** test.log line 13: `argLine set to -javaagent:…org.jacoco.agent-0.8.12-runtime.jar=destfile=…/target/jacoco.exec` (prepare-agent ran) but test.log tail: `jacoco:0.8.12:report … Skipping JaCoCo execution due to missing execution data file.` and `ls target/jacoco.exec` → "No such file". The `check` goal likewise skips on missing data, so `mvn verify` and `release.yml` (`mvn -B verify`) pass with *any* coverage. The pom comment "runs on every build, report at target/site/jacoco/" is false. Real coverage is therefore unknown (could not be measured – see "Not audited").
- **Impact:** All claims about coverage are unverifiable; regressions in coverage never block a release.
- **Fix:** `<argLine>@{argLine} -Dnet.bytebuddy.experimental=true --add-opens java.base/java.lang=ALL-UNNAMED</argLine>` (late-binding) and define an empty `<argLine/>` property default so non-jacoco runs work; then run `mvn verify` once and set the thresholds from the *measured* baseline (the 0.40/0.30 may fail or be far too low).
- **Dependencies / risk:** Byte Buddy experimental + JaCoCo 0.8.12 on JDK 21+/newer may need a JaCoCo bump (0.8.13+ for JDK 24/25). Turning on the gate may expose a failing threshold → decide threshold before enabling in release.yml. Low regression risk to product code.
- **Repro:** `mvn test` then `ls target/jacoco.exec target/site/jacoco` (absent); grep the log for "Skipping JaCoCo execution".

---

## Group 2 — Critical-path coverage gaps

### TST-002 — `DriverManager` lifecycle has no direct unit tests  (P1, DOĞRULANDI, effort M)
- **Problem:** `DriverManager` (450 LOC; `createDriver` semaphore + 30 s `tryAcquire`, per-test/per-suite lifecycle, `SUITE_DRIVERS`, `quitDriver`, `forceQuitDriver`, `quitAllSuiteDrivers`, `recreateDriver`, `isDriverAlive`, session-override stack) is only ever *mocked* (`mockStatic(DriverManager.class)` in 24 test files). There is no `DriverManagerTest`.
- **Evidence:** `grep` of tests for `acquirePermit|releasePermit|quitAllSuiteDrivers|forceQuitDriver|recreateDriver|isDriverAlive` → only `verify(...)` calls on a mocked static (e.g. `SuiteExecutionListenerTest.java:248`, `TestFlyExtensionTest.java:187`); `DriverManager.createDriver()` is invoked only in `integration/network/NetworkMockIntegrationTest.java:73` (excluded by default). Only `pushSessionOverride` has a real test (`MultiSessionTest.java:54-86`).
- **Untested behaviours:** permit acquire/release symmetry; session limit blocking and the 30 s timeout path (`DriverManager.java:128-135`); `createDriver` returning null; `createDriver` failure releasing the permit (`:~185`); per-suite `quitDriver` no-op; `quitAllSuiteDrivers` releasing N permits and clearing only the *calling* thread's `DRIVER` ThreadLocal (`:402-424`); load-test guard (`isLoadTestActive`); thread isolation of `DRIVER`.
- **Impact:** Resource leaks (browser processes, hung CI on exhausted semaphore) are the framework's highest operational risk and are entirely unguarded.
- **Fix:** Add `DriverManagerTest` using a registered fake `DriverProvider` (via `DriverProviderRegistry`/`DriverProviderFactory`) returning Mockito `WebDriver`s; test with `maxActiveSessions=1/2`, concurrency via an `ExecutorService`, and a short-timeout seam for the 30 s wait (make timeout configurable or package-private). Mark `singleThreaded`.
- **Dependencies / risk:** needs a timeout seam in `DriverManager` (small production change); tests share static `SESSION_SEMAPHORE` → reset hook needed (reflection acceptable in tests). Regression risk low.
- **Repro:** `grep -rn "DriverManager\.\(createDriver\|quitDriver\)" src/test` outside `integration/`/`examples/` → nothing.

### TST-003 — Session-permit leak when `driver.quit()` throws  (P1, DOĞRULANDI by static read, effort S)
- **Problem:** In `quitDriver()` the permit release sits *after* `driver.quit()` in the same `try`; if `quit()` throws (common: crashed/closed browser), the catch only prints and the permit is never returned. `forceQuitDriver()` has the same shape (`releasePermit()` after `driver.quit()`).
- **Where:** `src/main/java/io/testfly/driver/DriverManager.java:354-372` (`driver.quit(); getOrInitSemaphore().release();`) and `:380-396`.
- **Evidence:** code above; no test exercises a throwing `quit()` (see TST-002). Consequence: after N such failures `createDriver` blocks 30 s then throws "Timed out waiting for an available session slot" – cascading failures in later tests.
- **Impact:** CI cascades; presents as flaky/timeouts. (Also cross-reference to Area A, driver lifecycle.)
- **Fix:** release in `finally` (only if the driver was non-null); add regression test: provider returns driver whose `quit()` throws → `activeSessions()` returns to 0 and a subsequent `createDriver` succeeds with `maxActiveSessions=1`.
- **Dependencies / risk:** none beyond TST-002 harness; low risk.
- **Repro (proposed):** mock `WebDriver` with `doThrow(new WebDriverException()).when(d).quit()`, `maxActiveSessions=1`, call `createDriver(); quitDriver(); createDriver();` → second call blocks 30 s today.

### TST-004 — `RetryListener` behaviour contradicts its documented contract; tests assert the contradicted behaviour only partially  (P1, DOĞRULANDI, effort S)
- **Problem:** Javadoc (`RetryListener.java:~21`) says `@Retryable` "allows per-method opt-in when global retry is off". Code: lines 48-52 return `false` whenever `retry.enabled=false` ("master kill switch … including @Retryable"), and line 56 `isGlobalRetry = retryConfig.isEnabled()` is then always `true`, making the `!isGlobalRetry && !isAnnotated` guard (line 59) dead code. Net effect: `@Retryable` is *only* a maxAttempts override, never an opt-in. `docs-site/docs/intro.md:95` advertises "Global, per-method `@Retryable`…" and `configuration.md:557` "override global retry settings" (consistent only with the override reading). Also the repo's own `src/test/resources/testfly.yml:28` has `retry.enabled: false`, i.e. any consumer copying that file and using `@Retryable` gets no retries.
- **Test gaps (RetryListenerTest.java, 5 tests):** present: not-initialised, disabled, global N attempts, N=1, `@Retryable` with global enabled (`:97-105`). **Missing:** `@Retryable` with `enabled=false` (the contract in dispute), `@Retryable(maxAttempts=k)` overriding the config value (`RetryListener.java:62-67`), `maxAttempts=0`, the Cucumber override branch (`:70-77`), per-instance attempt isolation. `retry_retryableAnnotation_withGlobalEnabled_retries` uses `initContext(true,1)` so the annotation is never the deciding factor — it passes identically without the annotation (false-positive style test).
- **Impact:** Public API (`@Retryable`, `@TestFlyApi since 0.5.0`) behaviour ambiguous; users can believe flaky tests are retried when they are not.
- **Fix:** Decide the contract (product decision), align code+Javadoc+docs, delete dead branch, add the missing tests above.
- **Dependencies / risk:** behaviour change if we make `@Retryable` opt-in (user-visible) → needs release note; keep the doc-only fix as the zero-risk option.
- **Repro:** set `retry.enabled=false`, annotate a failing test with `@Retryable(maxAttempts=2)`; observe a single run. Or unit: `initContext(false,3); listener.retry(mockResult(retryableMethod()))` → `false`.

### TST-005 — `WaitEngine`: the primary waits are untested; no timeout/negative/heal case at all  (P1, DOĞRULANDI, effort M)
- **Problem:** `WaitEngine` (471 LOC, 27 public methods) is the centre of the "auto-waiting" promise but `WaitEngineTest` (148 LOC, 12 tests) covers only the "newer conditions" with *happy paths* (class Javadoc says so).
- **Evidence:** grep of all non-example tests: `waitForVisible`→2 hits (both inside a stack-trace string in `SourceCodeLocatorTest.java:14,40`), and **0 hits** for `waitForClickable`, `waitForInvisible`, `waitForStaleness`, `waitForText(`, `waitForAttributeContains`, `waitForTitle`, `waitForUrlContains`, `waitForAngular`, `waitForReactHydration`, `waitForAlert`, `waitMillis/Seconds`, `tryHeal`. No test expects a `TimeoutException`; the `catch (TimeoutException | NoSuchElementException) → tryHeal` branch (`WaitEngine.java:89-98`) is never executed; `waitMillis` interrupt handling (`:66-75`) untested. Weak assertions: `WaitEngineTest.java:136-146` (`waitForPageLoad_*`) have no assertion at all.
- **Impact:** Highest-traffic code with zero regression protection for failure semantics (error message, healing fallback, timeout value taken from `timeouts.explicit`).
- **Fix:** Extend `WaitEngineTest` with: timeout → `TimeoutException` within ~explicit seconds (set explicit=1); heal fallback returns healed element / rethrows; invisibility/staleness/clickable positive+negative; `waitMillis` interrupt flag; `waitForPageLoad` verifying `executeScript` was invoked (`verify`).
- **Dependencies / risk:** tests with 1 s timeouts add ~1 s each; keep `explicit=1`. Mockito static mocks already used (`singleThreaded`). Low risk.
- **Repro:** `grep -c "waitForClickable" target/audit-scratch/alltests.txt` → 0.

---

## Group 3 — Flakiness / parallel isolation

### TST-006 — Cross-JVM race on the shared report directory  (P2, GÜÇLÜ ŞÜPHE – not reproduced, effort S)
- **Problem:** Surefire runs `forkCount=4`, `reuseForks=false` (pom `:~341-342`): up to 4 test-class JVMs run concurrently. Several classes read/write/*delete* the **same relative default files** `target/testfly-reports/{metrics.json, report.html, …}` through `ReportPaths` and protect them with `synchronized (ReportPaths.class)` – a lock that only exists *inside one JVM*, so it gives no protection between forks.
- **Evidence:** `HtmlReportGeneratorTest.java:33,36-59` (`GLOBAL_REPORT_LOCK = ReportPaths.class`; `@BeforeMethod/@AfterMethod` do `System.clearProperty("testfly.reports.dir")` then `html.delete(); json.delete()`), `JUnitXmlReporterTest.java:33-53`, `ExecutionMetricsTest.java:257-270` (`exportToJson()` then reads `ReportPaths.metricsJson()` and asserts content), `ReportPathsTest`, plus several loadtest/junit5/cucumber tests listed by `grep ReportPaths|exportToJson`. One class deleting/rewriting `metrics.json` while another JVM asserts on it can fail `jsonFile.exists()` / content assertions. 1361/0 in the baseline run shows it is not deterministic.
- **Side effect:** the suite deletes artefacts under the framework's *real* default report location, which `testfly-ci.yml` later uploads ("Upload HTML report and metrics"), so the uploaded report is whatever test ran last.
- **Fix:** every test that touches reports must set `testfly.reports.dir` to a per-class/per-test temp dir (`Files.createTempDirectory`) and restore it; remove the in-JVM `GLOBAL_REPORT_LOCK` reliance. Alternatively set `-Dtestfly.reports.dir=${project.build.directory}/surefire-reports-${surefire.forkNumber}` in the surefire `argLine`.
- **Dependencies / risk:** test-only change; low. Combine with TST-001 argLine edit.
- **Repro:** run two of the classes concurrently in separate JVMs in a loop: `for i in $(seq 20); do (mvn -q test -Dtest=HtmlReportGeneratorTest &) ; mvn -q test -Dtest=ExecutionMetricsTest#exportToJson_includesRetryCount; wait; done` and watch for failures.

### TST-013 — Other parallel-isolation weaknesses  (P3, GÜÇLÜ ŞÜPHE, effort S)
`parallel=methods` runs methods of one class concurrently on a shared test-instance. Observed:
- `ApiClientFeaturesTest.java:19` is **not** `singleThreaded` yet mutates the JVM-global `REQUEST_INTERCEPTORS`/`RESPONSE_INTERCEPTORS` lists (`ApiClient.java:79-82`) and calls `ApiClient.clearInterceptors()` in `@AfterMethod` (`:21-26`) and inside tests (`:101-114`). Today no sibling test sends requests, so it is latent only.
- `CloudProviderTest.java:68-75` sets/clears a system property without `try/finally` (a failed assertion leaves `TEST_BS_KEY` set) and has no `singleThreaded`.
- `SmartTestSharderTest.java:22` (no `singleThreaded`) sets `testfly.shard.total/index` system properties (`:123-138`, properly in `try/finally`) while other methods of the same class run in parallel; they currently don't read these properties.
- `TestClockTest`, `DbAssertTest`, `MailboxClientTest`, `ApiResponseAdvancedTest` touch static helpers without `singleThreaded` (no evidence of actual conflict).
- **Fix:** annotate with `@Test(singleThreaded = true)` or use temp state; wrap system-property mutations in `try/finally`. **Risk:** none.

### TST-015 — Timing and sleep dependent tests  (P3, GÜÇLÜ ŞÜPHE, effort S)
- `ExecutionMetricsTest.java:116,129` use `Thread.sleep(50/20)` with **no assertions** (see TST-008) – slow and pointless.
- `DependsOnApiTest.java:73-78,123-129` prove caching via wall-clock `elapsed < 1000` ms instead of counting probes; under CPU oversubscription (TST-014: `perCoreThreadCount=true` ×4 threads ×4 forks) this can flap.
- `PageAssertTest.java:85-160` / `LocatorAssertTest.java:338` use `.within(Duration.ofMillis(200/500))` for *negative* cases (the failure message is asserted) – robust, but couple suite time to the polling interval.
- `RecordingManagerTest.java:207,269` poll with a 2 s deadline loop (acceptable).
- Only 3 production-adjacent `Thread.sleep` in the non-example tests (`ExecutionMetricsTest` ×2, `JdkLoadEngineTest.java:49` inside a local HTTP handler, fine). `AGENTS.md` bans `Thread.sleep` in framework code, not tests.
- **Fix:** inject a clock / count probes (`ApiHealthChecker` call counter or local `HttpServer` hit counter); remove the sleeps.

---

## Group 4 — Weak assertions / false-positive potential

### TST-008 — Tests without assertions  (P2, DOĞRULANDI, effort M)
- Heuristic script (`target/audit-scratch/noassert.py`, @Test bodies with no `assert|verify|fail|expectedExceptions`) found **69 of 1399** test methods with no assertion at all (≈5 %). Many are legitimate "must not throw" checks, but a subset have behaviour-claiming names and verify nothing:
  - `ExecutionMetricsTest.java:113-160`: `markEnd_withKnownStart_recordsTotalTime` (sleeps 50 ms, no assert), `markStart_twice_lastStartWins`, `recordStatus_storesStatusCorrectly` (calls `recordStatus(…,"FAILED")` and ends), `recordScreenshot_validPath_stored`, `reset_clearsAllState` (`:373`).
  - `ApiClientFeaturesTest.java:86-134`: `addRequestInterceptor_registersInterceptor`, `multipleInterceptors_allRegistered` ("No exception = success" comment; adds three interceptors then clears – proves nothing about registration or ordering).
  - `BuildThresholdEnforcerTest.java:67-175`: `passRate_exactlyAtThreshold_passes`, `flakyGate_exactlyAtLimit_passes` etc. only "don't throw" – acceptable for pass cases, but their FAIL twins exist, so boundary behaviour is covered; fine.
  - `WaitEngineTest.java:136-146`, `ReportPortalReportAdapterTest.java:68-120`, `ReportPortalAttachmentSenderTest.java` (9 tests), `ReportPortalJUnit5BridgeTest.java` (9 tests: `*_doesNotThrow_whenRpServerNotConfigured`) – test.log shows `[TestFly] ReportPortal 'beforeAll' failed: null` etc., i.e. these tests pass while the code under test is failing internally and swallowing the exception.
- **Impact:** inflated test count (1361) overstates safety; regressions in these units cannot fail.
- **Fix:** assert on observable state (metrics JSON content, interceptor effect via a local `HttpServer`, `verify()` on collaborators, captured log). Where "no throw" is the contract, name it so and keep.
- **Risk:** none (tests only); some require exposing a read accessor (`ExecutionMetrics.getTiming(id)`).

### TST-009 — "Valid config" ReportPortal test uses malformed JSON and asserts nothing  (P2, DOĞRULANDI, effort S)
- `ReportPortalReportAdapterTest.java:102-122` builds the metrics string ending `…"]}" + "}"` → `…]}}` (one closing brace too many = invalid JSON), writes it and calls `adapter.generate(metricsFile)` with **no assertion**. Name claims `…_logsSummary`. The test therefore passes on the error path (parse failure swallowed), never reaching the summary logic. Same file `:68-100` (three `…doesNothing` tests) also assert nothing.
- **Fix:** correct the JSON, capture the log/launch call (inject a `ReportPortalClient` seam or assert on a spy) and assert.
- **Repro:** `python3 -c "import json;json.loads('{\"a\":[{\"b\":1}]}}')"` → error; same shape as the test's string.

### TST-010 — Config loading: shallow, fixture-coupled, paths untested  (P2, DOĞRULANDI, effort M)
- `ConfigurationLoaderTest` has 7 tests (`ConfigurationLoaderTest.java:40-136`). Three call `ConfigurationLoader.load()` against the repo's own `src/test/resources/testfly.yml` and assert only that values are non-null/positive; `load_withProfile_loadsProfileFile` (`:81-93`) loads profile `prod` and only does `assertNotNull(config)` – it cannot tell if `testfly-prod.yml` or the default file was loaded.
- **Untested** (`ConfigurationLoader.java:35-322`): priority order explicit-path > profile > classpath > cwd (only the "explicit path missing" error is tested); explicit path *success*; malformed YAML; every `validate()` branch (`:289-322`: null browser, missing `browser.name` without matrix, bad `execution.mode`, non-positive timeouts); `browser.matrix` exemption; `${VAR}`/`${VAR:-default}` resolution through the whole tree (only `DotEnvLoader` unit-tested in `config/DotEnvLoaderTest`); `LenientPropertyUtils` unknown-field behaviour; defaults application (`TestFlyDefaultsTest` has 2 no-assert tests, see TST-008).
- Also `assertEquals("chrome", config.getBrowser().getName())` (`:51`) has swapped expected/actual (TST-016).
- **Fix:** temp-dir YAML fixtures + `-Dtestfly.config=` per test; table-driven negative validation tests; one end-to-end placeholder test.
- **Risk:** none; tests use system properties → keep `singleThreaded` + try/finally.

### TST-016 — Assertion-style defects  (P3, İYİLEŞTİRME ÖNERİSİ, effort S)
- Swapped `assertEquals(expected, actual)` (TestNG expects `(actual, expected)`) e.g. `ConfigurationLoaderTest.java:50-51` (4 literal-first occurrences repo-wide by grep `assertEquals("`).
- `PageAssertTest.java:70-75` `hasTitle_passesWhenTitleMatches` asserts `assertNotNull(assertion)` on the fluent return value – always true; pass is implicit only if no exception.
- 15 swallowed `catch (Exception|Throwable e) {}` blocks in unit tests (script `try{…}catch(…){}`), and 26 `catch (Exception ignored)` occurrences overall; e.g. `NetworkMockIntegrationTest.java:84` hides teardown errors.
- **Fix:** adopt consistent `assertEquals(actual, expected, msg)`; replace swallowed catches with `expectThrows` or log; low-effort lint rule (PMD `EmptyCatchBlock` is already in `-Pquality`, see TST-017).

---

## Group 5 — Structural coverage gaps

### TST-007 — Listeners/bridges are only tested against mocks; no real TestNG or JUnit Platform run  (P2, DOĞRULANDI, effort M)
- `grep "new TestNG(" / LauncherFactory` in tests → none. `SuiteExecutionListenerTest.java:296` mocks `XmlSuite`; `TestFlyExtensionTest`, `TestFlyLauncherListenerTest`, `CucumberHooksTest` use `mockStatic`. Consequences:
  - `RetryAnnotationTransformer` (`listeners/RetryAnnotationTransformer.java`, 31 LOC) has **no test reference** (it is wired only via the surefire `listener` property, `pom.xml:~352`).
  - The interplay "RetryListener retries → TestNG reports attempt as SKIPPED → `TestExecutionListener.onTestSkipped` (`:308-331`) records `SKIPPED`/pushes to `TestManagementReporter` → final attempt overwrites" is untested; `onTestStart` (`:96-135`) has no test either (`TestExecutionListenerTest` covers only success/failure/skipped callbacks, 23 tests). Whether retried attempts are mis-reported as skips to TestRail/Xray, and whether failure screenshots are taken for retried attempts (the retried attempt arrives as `onTestSkipped`, which takes none), cannot be answered by the current suite (GÜÇLÜ ŞÜPHE; see Area B).
  - Parallel execution (`parallel`/`threadCount` → driver ThreadLocal isolation) is never exercised end-to-end.
- **Fix:** add 3–5 "mini-suite" tests that run a tiny fixture class through `TestNG` programmatically with `RetryAnnotationTransformer`, `TestExecutionListener`, `SuiteExecutionListener`, a fake driver provider, and `parallel=methods threadCount=2`; assert result counts, `retryCount` in metrics, driver count == 0 at end. Same for JUnit5 via `LauncherFactory`.
- **Dependencies / risk:** depends on TST-002 (fake provider) and must run in an isolated fork; medium complexity, low regression risk.

### TST-011 — Production classes with no test reference  (P2, DOĞRULANDI, effort L)
Name-based scan (`target/audit-scratch/untested.txt`): 38 of 246 main classes are never referenced (by simple name) in any non-example test. Largest/most relevant (LOC): `performance/PerformanceCollector` (350), `loadtest/internal/TestFlyGatlingSimulation` (225), `email/OutlookProvider` (194), `browser/BrowserSessionCache` (170) + `SessionCache`, `testmanagement/XrayClient` (158), `loadtest/internal/GatlingRunConfig` (157), `testdata/ExcelDataReader` (150), `email/ImapProvider` (141), `browser/GeoLocation`, `api/mock/ApiMockServer` (124), `extension/FrameworkVersion` (121 – drives `minFrameworkVersion()` plugin gating), `recording/GifEncoder`/`Mp4Encoder`, `browser/ClipboardHelper`, `cucumber/BaseCucumberTest`/`BaseCucumberSteps`, `test/SmartLocator` (82), `browser/BrowserMatrixListener` (78), `context/SuiteContext`, `client/SchemaValidator`/`OpenApiValidator`/`ApiRequestSpec`/`ApiResponseSpec`, `driver/CapabilityValidator`, `listeners/RetryAnnotationTransformer`, `reporting/HtmlReportAdapter`, `extension/IncompatiblePluginException`.
- Caveat: name-based; a class may be exercised indirectly (e.g. via a facade) – coverage cannot be confirmed because JaCoCo is dead (TST-001). Classes like `FrameworkVersion`, `CapabilityValidator`, `SchemaValidator`, `OpenApiValidator`, `BrowserMatrixListener`, `ExcelDataReader` are pure/deterministic and cheap to unit-test.
- **Fix order:** first TST-001 to get real numbers; then add tests for pure-logic classes (S each), defer provider clients to mock-HTTP tests (`HttpServer` like `JdkLoadEngineTest`).

### TST-012 — Integration tests: silently skipped and no real-browser verification  (P2, DOĞRULANDI, effort S)
- `.github/workflows/testfly-ci.yml:95-106` ("Run real backend tests") runs `mvn verify -Preal-backends` only if `AI_API_KEY` is set; otherwise prints "Skipping real backend tests." and `exit 0` – the job is **green while running nothing**, including DB/Mailhog/Gatling/network tests that need no AI key. Fork PRs never have secrets, so they never run it. The reporter step has `fail-on-error: false`, `fail-on-empty: false` (`:117-126`).
- Of 6 integration classes (39 `@Test`, excluded from default via `pom.xml:~348-351`), `NetworkMockIntegrationTest.java:95-101` and `GatlingEngineIntegrationTest.java:46` throw `SkipException` when Chromium/Gatling is missing – they can "pass" as skipped everywhere.
- No test anywhere launches a real browser through `BaseTest`+`DriverManager`; the consumer-test job (`testfly-test` repo) runs **API demo tests only** (per AGENTS.md), so Selenium integration (driver providers, `Locator` against real DOM, `WaitEngine` timing) is unverified in CI.
- **Fix:** split the job: services-based tests (Mailhog/DB containers, Gatling) run unconditionally; only AI tests gated by the secret, and surface "skipped" as a warning/summary. Add one headless-Chrome smoke test against a local static page (`HttpServer`) in a `-Pbrowser` profile that CI runs on `ubuntu-latest` (Chrome preinstalled).
- **Risk:** CI minutes and a new flaky surface; mitigate with a single smoke test.

---

## Group 6 — Configuration hygiene

### TST-014 — Surefire configuration observations  (P3, İYİLEŞTİRME ÖNERİSİ, effort S)
- **`reuseForks=false` + `forkCount=4` = one JVM per test class** (≈122 JVMs; test.log shows 134 per-fork "AnnotationTransformer already set" lines). This hides cross-class static-state leakage (`TestFlyContext.CONFIG`, `ApiClient` interceptors, registries): tests are green *because* each class has a clean JVM, whereas consumers run everything in one JVM. The in-class `synchronized(TestFlyContext.class)` + reflection resets (e.g. `RetryListenerTest.java:34-53`) show leakage is a known hazard. Consider a periodic job with `reuseForks=true` to prove isolation.
- **No timeouts:** no `forkedProcessTimeoutInSeconds`, no `@Test(timeOut)`; `DriverManager.createDriver` can wait 30 s and a hung test would stall CI indefinitely.
- **Thread oversubscription:** `parallel=methods`, `threadCount=4`, `perCoreThreadCount=true` ⇒ 4×cores threads per fork × 4 forks – amplifies timing flakiness (TST-015).
- **Duplicate listener registration:** `AnnotationTransformer already set` ×134 means the transformer is registered both through surefire's `listener` property and another path (e.g. `BaseTest @Listeners`/SPI); harmless warning but indicates ambiguous wiring (the pom property is also a test-only crutch that consumers do not have).
- **No rerun-for-diagnosis:** no `rerunFailingTestsCount`/flaky detection in CI, so a flake in TST-006 would just fail a build.
- **Fix:** add `forkedProcessTimeoutInSeconds`, reduce `perCoreThreadCount`, document/justify reuseForks; optional nightly `reuseForks=true` run.

### TST-017 — Quality profile and examples never exercised in CI  (P3, DOĞRULANDI, effort S)
- `-Pquality` (SpotBugs, Checkstyle google_checks, PMD incl. `multithreading.xml`) is defined (`pom.xml:~583-657`) but no workflow/Jenkins stage invokes it (`testfly-ci.yml` uses `mvn test`, `mvn verify -Preal-backends`, `mvn install -DskipTests`; `release.yml` uses `mvn verify`; `ci/Jenkinsfile:33,39,68`). Same for `-Pexamples` (33 example files compiled but never run, so drift between examples and API is only caught at compile time).
- **Fix:** add a non-blocking `-Pquality` job first, then ratchet. Note google_checks with 2-space indent will flood a codebase that is formatted with 4 spaces; start with SpotBugs/PMD only.

---

## Edge/error-case coverage observations (not separate IDs)
- Positive observations: `ApiHealthChecker` (49 references, incl. connection refused/timeout/cache/invalid-URL), `FrameworkBootstrap` (351-line test), `Suite/TestExecutionListener` success/failure/skip flows, `LocatorAssert`/`PageAssert` negative messages, `QuarantineTest`, `PreConditionRunner` retry/`wasRetried` handling (`PreConditionRunnerTest.java:177-196`) are reasonably deep.
- `TestExecutionListenerTest` lacks: `onTestStart`, `quit()` throwing during cleanup (cleanup chain in `onTestSkipped` at `TestExecutionListener.java:308-331` is not exception-guarded between steps – if `DriverManager.quitDriver()` or `MultiSessionManager.clearAll()` throws, later cleanups like `DbConnectionFactory.closeAll()`/`BrowserContext.clear()` are skipped; untested), `SkipException` from quarantine/`@DependsOnApi` (`checkQuarantine`, `checkApiDependencies`) being routed through `onTestSkipped`.
- Test logs show "No active test context — step ignored" (WARN, ~35 lines) from `ApiResponseAssertionsTest` – the StepLogger path is bypassed there, so assertion-step logging for API assertions is not verified.

## Sub-areas NOT audited (and why)
- **Actual line/branch coverage:** impossible – JaCoCo produced no data (TST-001); I did not re-run the suite with a fixed `argLine` (audit-only, no pom change, full run disallowed).
- **Flake rate:** only one suite run was available (1361/0/0); flakiness findings (TST-006, 013, 015) are static-analysis suspicions, not observed failures.
- **`src/test/java/io/testfly/examples/**` (33 files) and `src/test/resources/features/*.feature`:** excluded by surefire; only skimmed for classification, not reviewed for quality.
- **Integration tests execution** (`-Preal-backends`, needs `AI_API_KEY`, Mailhog, DB, Chromium, Gatling): not run; read only for skip logic.
- **JUnit 5 / Cucumber / ReportPortal / loadtest (17 files) test internals:** sampled (grep-level and a few files), not read line-by-line.
- **Test-data/CSV/Excel loaders, healing, AI remediation, recording, visual diff test depth:** not individually assessed.
- **docs-site Java snippet compile-tests:** belongs to Area E.
- **Mutation testing / PIT:** not available in the project; would be the right tool to quantify TST-008.
