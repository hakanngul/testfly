# Area B — Automation Engine & API (audit findings)

Scope: Selenium integration, Playwright/Appium presence, Locator / WaitEngine / assertions / BasePage / SmartLocator,
TestNG / JUnit5 / Cucumber bridges, reporting / logging / screenshots, Load Test, AI/MCP, SPI.
Repo: `/Users/hagul/Projects/TestFramework/testfly` (branch `chore/docs-cloudflare-workers`, pom `io.github.hakanngul:testfly:1.0.7`).
Baseline cited from `build-test.md`: `mvn compile` exit 0, `mvn test` = 1361 tests, 0 F / 0 E / 0 S. (Tests were NOT re-run.)
Audit-only: repo `git status --porcelain` is identical to the pre-audit baseline (only the two pre-existing `.agents/memories` modifications). Scratch code is under `target/audit-scratch/tng/` (XP.java, T.java, J.java, LoadRepro.java).

Classification legend: DOĞRULANDI = verified (code read and/or reproduced) · GÜÇLÜ ŞÜPHE = strong suspicion, not fully reproduced · İYİLEŞTİRME ÖNERİSİ = improvement.
Important caveat: no real browser was launched (no browser tests exist in the repo and I did not start one). Browser-behaviour claims are based on code reading plus JDK-level reproductions and are labelled as such.

---
## 0. Summary table

| ID | Pri | Class | Title |
|---|---|---|---|
| API-001 | P1 | DOĞRULANDI | JDK load engine never records HTTP status codes (`statusCodes()` always `{}`) |
| API-002 | P1 | DOĞRULANDI | JDK load engine counts HTTP 4xx/5xx as success → `assertErrorRateBelow` false positive |
| API-003 | P1 | DOĞRULANDI | `LoadScenario.assertStatus(n)` is semantically wrong (asserts "no 500" / "no n") |
| API-004 | P1 | DOĞRULANDI | `extract()` without a feeder fails every iteration (`UnsupportedOperationException` on immutable map) |
| API-005 | P1 | DOĞRULANDI | Load-test docs describe a non-existent API (`@LoadEngine`, `rampUpSeconds`, `targetRps`, `fromCsv`, …) — real `@LoadTest` contract listed |
| API-006 | P2 | DOĞRULANDI | Javadoc builder chains in `LoadScenario` / `BaseLoadTest` / `LoadTestSupport` do not compile |
| API-007 | P2 | DOĞRULANDI | `load()` exists only in `BaseLoadTest`; docs/changelog claim BaseTest/BaseApiTest/BaseJUnit5Test |
| API-008 | P2 | DOĞRULANDI | Name-based heuristic (`"loadtest"` in class name) forbids WebDriver creation |
| API-009 | P2 | DOĞRULANDI | Load config doc-vs-behaviour: `enabled` no-op, baseUrl fallback only in Gatling, cooldown unimplemented, engine case-sensitive, sub-second truncation |
| API-010 | P2 | DOĞRULANDI | JDK engine robustness: hand-rolled JSON/JSONPath, no URL-encoding, swallowed errors, zero-request runs pass |
| API-011 | P1 | DOĞRULANDI (code) | `Locator` terminal actions do not wait for presence and never re-resolve stale elements |
| API-012 | P1 | DOĞRULANDI (XPath semantics) | `getByText()` XPath `.//*` matches ancestors; first match is `<html>` |
| API-013 | P2 | DOĞRULANDI | `Locator.type()/clear()` sends Cmd+A *and* Ctrl+A on every OS, swallows failures |
| API-014 | P2 | DOĞRULANDI | `Locator extends By` but `findElements(SearchContext)` ignores the context |
| API-015 | P2 | DOĞRULANDI | Locator-based assertions filter on `isDisplayed` and do not retry `LocatorException` |
| API-016 | P3 | DOĞRULANDI | `SmartLocator` does not wait, swallows exceptions, prints to stdout |
| API-017 | P2 | DOĞRULANDI | Javadoc/examples drift: deprecated `$()` in 11 Javadoc lines; BasePage canonical example is `By`-first |
| API-018 | P3 | DOĞRULANDI | `Role` is a CSS approximation, advertised as "targets the accessibility tree" |
| API-019 | P1 | DOĞRULANDI | Docs reference non-existent non-load API (`io.testfly.core.*`, `RoleOptions`, `.fill/.val`, `getWait().waitFor*`) |
| API-020 | P1 | DOĞRULANDI | Retry off-by-one vs docs, and default config retries every failing test once |
| API-021 | P2 | DOĞRULANDI / GÜÇLÜ ŞÜPHE | `@Retryable` semantics inconsistent between TestNG / JUnit5 (+ data-provider counter) |
| API-022 | P2 | GÜÇLÜ ŞÜPHE | Soft-assert flush flips `ITestResult` status inside `onTestSuccess` |
| API-023 | P2 | GÜÇLÜ ŞÜPHE | Cucumber `@After(order=20000)` quits the driver before user `@After` hooks |
| API-024 | P2 | DOĞRULANDI | HTML report embeds JSON in `<script>` without `</script>` escaping |
| API-025 | P2 | DOĞRULANDI | Hard-coded `target/…` paths ignore `ReportPaths`/`testfly.reports.dir`; screenshot temp-file leak |
| API-026 | P3 | DOĞRULANDI | Logging: 156 `System.out/err` vs 3 SLF4J users; unconditional promo banner |
| API-027 | P2 | DOĞRULANDI | AI providers use hand-rolled JSON (no `\uXXXX`, control chars) although Jackson is on the classpath |
| API-028 | P2 | İYİLEŞTİRME | `DomPruner` does not redact input values/tokens before DOM goes to a third-party LLM |
| API-029 | P2 | DOĞRULANDI | No Playwright/Appium/MCP code in repo; README/pom/docs advertise "mobile" and a "first-class MCP server" |
| API-030 | P3 | DOĞRULANDI | SPI robustness: plugin `onLoad` unguarded, version mismatch skipped silently, `FrameworkVersion` wrong group path, unsynchronised `find` |
| API-031 | P2 | DOĞRULANDI | Custom-driver docs wrong (`browser.mode`, ignored in remote/cloud mode, checked exception, wrong yaml example) |
| API-032 | P2 | DOĞRULANDI | `@TestFlyApi(since)` values (up to 3.0.0) contradict the 1.0.7 artifact → stability contract unusable |
| API-033 | P2 | İYİLEŞTİRME | No real-browser tests for Locator/WaitEngine; existing test codifies a wrong behaviour |

---
## 1. Re-verification of the prior hypotheses (Codex findings relevant to Area B)

| Prior claim | Verdict | Evidence |
|---|---|---|
| Playwright / Appium integrations exist | **Do NOT exist** | `grep -rniE "playwright|appium" src/main pom.xml` → only Javadoc comments ("Inspired by Playwright…" in `Locator.java:24`, `Role.java:14`, `PageAssert.java:26`, `LocatorAssert.java:28`) and a pom comment `pom.xml:201`. `grep -rn "io.appium\|com.microsoft.playwright\|AppiumDriver" src pom.xml` → 0 hits. Appium is only reachable by user code via `NamedDriverProvider` SPI (docs `extensibility/custom-drivers.md`). |
| `io.testfly.core.BasePage` (wrong) → `io.testfly.test.BasePage` | Confirmed | `ls src/main/java/io/testfly/core` → no such dir; `src/main/java/io/testfly/test/BasePage.java:58`. Docs: `guides/video-recording.md:101` (`io.testfly.core.BaseTest`), `ai/prompt-recipes.md:37,82`. |
| `io.testfly.locators.Role` (wrong) → `io.testfly.locator.Role` | Confirmed | `src/main/java/io/testfly/locator/Role.java`; docs `ai/prompt-recipes.md:38`. |
| `.fill()` / `.val()` / `RoleOptions` don't exist | Confirmed | `Locator.java` public methods (type/append/clear/click/…); `grep -rn "RoleOptions\|\bfill(\|\bval(" src/main/java` → 0. Docs: `guides/video-recording.md:110-123` (`.val`), `ai/prompt-recipes.md:49,54` (`.fill`), `ai/testfly-mcp.md:110-121` (`RoleOptions`). Real: `getByRole(Role)` and `getByRole(Role, String name)` (`LocatorSupport.java:113,118`). |
| `@LoadEngine` does not exist | Confirmed | `grep -rn "LoadEngine" src/main` → only `JdkLoadEngine`/`GatlingEngine` classes, no annotation. Docs: `loadtest/annotations.md:4,10,55-68,91`, `loadtest/engines.md:65` (EN 6 hits, TR 6 hits). |
| `.targetRps()` does not exist | Confirmed | `grep -rn targetRps src/main` → 0. Docs `loadtest/annotations.md:30,50`, `loadtest/fluent-api.md:96,111`. |
| `.fromCsv()` does not exist | Confirmed | Real factory is `LoadTestFeeder.csv(path)` (`LoadTestFeeder.java:97`) and `LoadScenario.feedCsv(path)` (`LoadScenario.java:173`). Docs `loadtest/feeders.md:25,60,64,76,99`, `loadtest/examples.md:61`. `.circular()/.random()/.batch()` also non-existent. |
| `.runAsync()` does not exist | **REFUTED / nuance** | `loadtest/examples.md:106` uses `CompletableFuture.runAsync(...)` — a JDK static, not a TestFly method. The snippet is still broken for other reasons (see API-007/API-008). Do not list `.runAsync()` as a missing TestFly API. |
| `getWait().waitForVisible()` misleading | Confirmed | `NavigationSupport.java:47` `default WebDriverWait getWait()` returns Selenium `WebDriverWait` (has `until`, not `waitFor*`). `WaitEngine.waitForVisible(By)` is a *static* method (`WaitEngine.java:89`). Docs: `why/why-waitengine.md:56,90`, `recipes/oauth-sso.md:74,80,132`, `recipes/infinite-scroll.md:36,69,106` (`getWait().wait(ExpectedConditions…)`, `getWait().waitForPageLoad()`). |
| Deprecated `$()` recommended | Confirmed | `LocatorSupport.java:74,84,95` `@Deprecated(since="1.1.0", forRemoval=true)`; still used in 11 Javadoc lines of `src/main` (API-017) and docs `cloud-execution.md:20`, `guides/video-recording.md:110-123`. |

### 1.1 The REAL `@LoadTest` contract (`src/main/java/io/testfly/loadtest/LoadTest.java:44-83`)
```java
@TestFlyApi(since = "1.1.0") @Retention(RUNTIME) @Target({TYPE, METHOD})
public @interface LoadTest {
    int    users()    default -1;   // >0 overrides
    String rampUp()   default "";   // "30s" / "2m" / "1h"
    String hold()     default "";
    String cooldown() default "";
    String engine()   default "";   // "gatling" | "jdk" | "auto"
    String baseUrl()  default "";
}
```
Resolution (`LoadTestConfig.resolve`, `LoadTestConfig.java:58-98`): YAML `loadtest:` < class `@LoadTest` < method `@LoadTest`; fluent scenario setters (`resolveFor`, line 101) win; `users` clamped to `maxUsers`.
Real fluent API: `LoadScenario` (`single/named/users/rampUp(Duration)/hold(Duration)/cooldown(Duration)/engine/baseUrl/step/get/post/put/delete/patch/feed/feedCsv/feedJson/thinkTime/run/assertStatus/assertP95Below/assertThroughputAbove/assertErrorRateBelow`), `LoadStep` (`get/post/put/delete/patch/header/body/queryParam/formParam/check/extract/and/run`), `LoadTestFeeder` (`csv/json/random/uuid/sequence/constant`), `LoadTestAssert` (`assertThroughputAbove/Below, assertP50/90/95/99Below, assertMean/Max/MinLatencyBelow, assertTotalRequestsAbove, assertSuccessfulRequestsAbove, assertFailedRequestsBelow, assertErrorRateBelow, assertSuccessRateAbove, assertStatus, assertStatusCodeCount, assertNoStatus, assertStepP95Below, assertStepErrorRateBelow, metrics()`).
There is NO: `rampUpSeconds`, `durationSeconds`, `targetRps`, `scenarioName`, `feeders`, `warmUp`, `during()`, `@LoadEngine`, `LoadScenario.get()` without a path, `step(name, Consumer)`.

---
## 2. Load Test findings

Reproduction harness: `target/audit-scratch/tng/LoadRepro.java` (JDK `HttpServer` + `LoadTestRunner.run(...)` using `target/classes`). Raw output:
```
500-endpoint: total=4078 failed=0 errorRate=0.0 statusCodes={}
assertStatus(404) on 500-only endpoint: PASSED (false positive)
extract w/o feeder: total=7255 failed=7255
LoadTestAssert.assertStatus(200) on healthy 200 endpoint: [LoadTest] ok: status 200 count 0 is below minimum 1
```

### API-001 — JDK engine never records status codes — P1 — DOĞRULANDI — Effort S
- Root cause: `JdkLoadEngine.java:102` creates `statusCounts`, line 224 copies it into the metrics, but nothing ever writes to it. The comment at `JdkLoadEngine.java:310-313` ("status code tracking is done at the caller level") is untrue — `executeStep` returns `void` and the caller (lines 155-170) never sees `response.statusCode()`.
- Evidence: repro line 4 above — `LoadTestAssert.assertStatus(200)` fails on a healthy 200 endpoint; `statusCodes={}` for every run. Consequently `assertNoStatus(x)` always passes (false positive), `assertStatusCodeCount` always fails, and `LoadTestMetrics.hasStatusCode()` is always false. The HTML report's "status code distribution" is empty for the JDK engine. (Gatling path populates it: `GatlingResultsParser.java:242`.)
- Impact: documented assertions (`docs/loadtest/assertions.md`, `BaseLoadTest` examples, `K6SimpleLoadTest`) are broken or vacuous on the default fallback engine.
- Fix: make `executeStep` return `int` (status), `statusCounts.computeIfAbsent(code, …).incrementAndGet()` in the caller (also record a synthetic code, e.g. `-1`/"IOException", for transport failures).
- Dependencies/regression: API-002 (shares the same code path). Unit tests in `unit/loadtest/JdkLoadEngineTest` need new assertions on `statusCodes()`.

### API-002 — Non-2xx responses counted as success — P1 — DOĞRULANDI — Effort S
- Root cause: `JdkLoadEngine.java:155-163`: any response that does not throw is a success; only explicit `check(...)` conditions throw. `evaluateCheck` is the only HTTP-level failure path.
- Evidence: repro line 1: 4078 requests to an endpoint returning 500 → `failed=0 errorRate=0.0`. `JdkLoadEngineTest.testErrorEndpoint` (`src/test/java/io/testfly/unit/loadtest/JdkLoadEngineTest.java:156-170`) codifies this ("counts as successful at the HTTP level") and only asserts `totalRequests() > 0`.
- Impact: `.assertErrorRateBelow(0.02)` (the headline example in every doc) passes against a fully broken service. Gatling engine behaves differently (checks/KO), so the verdict depends on which engine `auto` picks.
- Fix: treat `status >= 400` (or configurable) as failed unless the step declared an explicit `status().in(...)`/`is(...)` check; add a test with a 500 endpoint asserting `errorRate == 1.0`.
- Risk: behaviour change — existing suites that "pass" today may start failing (correctly). Mention in CHANGELOG.

### API-003 — `LoadScenario.assertStatus(n)` wrong semantics — P1 — DOĞRULANDI — Effort S
- `LoadScenario.java:221-223`: `return run().assertNoStatus(expected == 200 ? 500 : expected);` while the Javadoc says "asserts all responses returned the given status code". For `expected=404` it asserts that **no** 404 occurred (inverse); for 200 it only asserts "no 500" (and is vacuous because of API-001/002).
- Repro: line 2 above — `assertStatus(404)` on a 500-only endpoint passes.
- Fix: delegate to `LoadTestAssert.assertStatus` + `assertNoStatus` for every other code, or remove the shortcut. Effort S; regression risk low (the method is nearly unusable today).

### API-004 — `extract()` fails every iteration without a feeder — P1 — DOĞRULANDI — Effort S
- Root cause: `JdkLoadEngine.java:146-148` `Map<String,Object> vars = feeder != null && feeder.hasNext() ? feeder.next() : Collections.emptyMap();` then `executeStep` does `vars.put(...)` (line 324) → `UnsupportedOperationException`, caught by the generic `catch (Exception e)` (line 163) and counted as a failed request. Even with a feeder, `feeder.next()` may return a shared/immutable map (`Map.of`) → same failure; and `vars` is mutated across iterations (leaks extracted tokens between loop passes).
- Repro: line 3 above — `failed=7255` of `7255`. The documented "Login → extract token → Order" scenario (BaseLoadTest/LoadScenario Javadoc) therefore reports 100% errors on the JDK engine.
- Fix: `Map<String,Object> vars = new HashMap<>(feederRow)` per iteration; reset per iteration.

### API-005 — Load-test docs describe a non-existent API — P1 — DOĞRULANDI — Effort M
- Evidence (all verified absent from `src/main`, see §1.1): `docs/loadtest/annotations.md:26-50,69,90,100` (`rampUpSeconds`, `durationSeconds`, `targetRps`, `scenarioName`, `@LoadEngine`), `docs/loadtest/getting-started.md:106`, `docs/loadtest/fluent-api.md:20-26,40,65-71,96,111` (no-arg `.get()`, `.post(payload)`, `step(name, lambda)`, `.targetRps`), `docs/loadtest/feeders.md` (`fromCsv`, `.circular()/.random()`), `docs/loadtest/engines.md:65` (`@LoadEngine("gatling")`), `docs/changelog.md:94,92` (`during()`, `warmUp`, `targetRps`). The TR mirror pages are identical (`i18n/tr/.../loadtest/*.md`), and 4 TR pages are missing (reported by the docs team).
- Impact: every copy-paste onboarding path for load testing fails to compile.
- Fix: rewrite the pages from §1.1; add the docs-snippet compile gate (docs team). Dependency: none; effort M (≈9 EN + 8 TR pages).

### API-006 — Javadoc builder chains do not compile — P2 — DOĞRULANDI — Effort S
- `LoadScenario.java:15-33`, `BaseLoadTest.java:56-70`, `LoadTestSupport.java:~50-60` chain `.step("Login").post(..).body(..).extract(..).step("Order")…` — but `step()` is defined only on `LoadScenario` (`LoadScenario.java:120`); `LoadStep` (returns `LoadStep` from `post/body/extract`) has no `step()` / `feed()` / `thinkTime()` (it has `and()` and `run()`). The fluent form requires `.and()` between steps. `.check(status().is(201)).feed(...)` has the same problem.
- Fix: correct the Javadoc (or add `LoadStep.step(String)` delegating to `parent.step(...)` — a small API addition that makes the documented DSL work, `@TestFlyApi` additive/default safe).

### API-007 — `load()` only exists in `BaseLoadTest` — P2 — DOĞRULANDI — Effort S
- `LoadTestSupport` is implemented only by `BaseLoadTest` (`BaseLoadTest.java:79-85`). `BaseTest` (`BaseTest.java:40-43`), `BaseApiTest` (`BaseApiTest.java:41`), `BaseJUnit5Test`, `BaseJUnit5ApiTest` do not implement it. `docs/changelog.md:~89` says the DSL is "seamlessly available in BaseTest, BaseApiTest, BaseLoadTest, and BaseJUnit5Test"; `docs/loadtest/examples.md:100-118` uses `load()` and `getByRole("heading","System Status")` / `assertVisible(...)` in `extends BaseTest` — none exist.
- Fix: either have `BaseApiTest`/`BaseJUnit5ApiTest` implement `LoadTestSupport` (default methods → source compatible) or correct the docs. Mixed UI+load requires API-008 to be solved first.

### API-008 — Name-based "load test" heuristic blocks WebDriver — P2 — DOĞRULANDI — Effort S
- `DriverManager.java:428-446` `isLoadTestActive()` returns true when the **class name lower-cased contains `"loadtest"`** or the class carries `@LoadTest`; `createDriver()` (line 123) and `getDriver()` (line 284) then throw `IllegalStateException("WebDriver … forbidden during Load Testing")`. The same heuristic is duplicated in `junit5/TestFlyExtension.java:511-517`.
- Impact: a UI test named e.g. `LoadTestDashboardUiTest`, or the docs' own `HybridUiAndLoadTest` example, can never obtain a driver. Behaviour depends on a class name.
- Fix: remove the name check; use only `LoadTestRunner.isExecuting()` + `BaseLoadTest` marker/`@LoadTest` on non-browser bases.

### API-009 — Load config doc-vs-behaviour — P2 — DOĞRULANDI — Effort S/M
- `loadtest.enabled` (`docs/loadtest/configuration.md:~38`, "Enables or disables load testing execution") is never read: `grep -rnE "loadTest[A-Za-z]*\.isEnabled|getLoadTest\(\)\.isEnabled" src/main` → 0 (only tests). 
- Docs say baseUrl "falls back to … `execution.baseUrl`": true only in `GatlingEngine.java:52`; `JdkLoadEngine.java:81-85` throws `IllegalStateException("No baseUrl configured")`. With `engine: auto` the behaviour depends on whether Gatling is on the classpath.
- `cooldown` ("gradual removal after hold") is not implemented in the JDK engine: each user stops at `own start + hold` (`JdkLoadEngine.java:149`) and `cooldown` only extends the safety timeout (line 126).
- `engine` is compared with `equals` on lower-case literals (`LoadTestRunner.java:97-114`): `"JDK"` → `IllegalArgumentException`.
- `LoadTestConfig.resolveFor` converts `Duration` to `toSeconds()+"s"` (`LoadTestConfig.java:109-111`) → `Duration.ofMillis(500)` becomes `"0s"`.
- `LoadTest.users()` Javadoc says "negative = default" but code uses `> 0` so `users=0` is also ignored (minor).
- Fix: implement or delete `enabled`; fallback to `execution.baseUrl` in `LoadTestConfig`; `equalsIgnoreCase`; keep ms precision.

### API-010 — JDK engine robustness/quality — P2 — DOĞRULANDI — Effort M
- `toJson` (`JdkLoadEngine.java:473-495`): hand-rolled, escapes only `"`; no `\\`/newlines/control chars, doesn't escape keys, no `List`/array support → invalid JSON bodies. Jackson is already a dependency.
- `extractJsonPath` (`JdkLoadEngine.java:~347-400`): `indexOf("\"part\"")` over the whole payload, matches keys inside *values*, no arrays/escaped quotes.
- Query params and path variables are not URL-encoded (`buildUrl`), `URI.create` throws on spaces → silently counted as a failed request.
- All exceptions in the VU loop are swallowed (`catch (Exception e)` at line 163) with no log/first-error sample → failures are undiagnosable.
- Run with 0 requests (e.g. `hold: 0s`, or all VUs interrupted) yields all-zero metrics; `assertP95Below`, `assertErrorRateBelow` pass vacuously (`errorRate` defined as 0 when total==0, line 214).
- Latencies stored as `List<double[]>` of 1-element arrays (lines 99-100, 157-160): ~2 allocations per request on a hot path of a load generator (use `DoubleAdder`/HdrHistogram or a primitive buffer). `System.err.println` for timeout warning (line ~205).
- Fix: Jackson for JSON, JsonPath via Jackson `JsonNode.at`, encode params, log first N errors, require `totalRequests > 0` in assertions (or `assertTotalRequestsAbove`) by default.

---
## 3. Locator / WaitEngine / assertions / BasePage

### API-011 — `Locator` terminal actions don't wait for presence, no stale re-resolve — P1 — DOĞRULANDI (code; no browser run) — Effort M
- `Locator.java:358` says "Terminal actions — all auto-wait" and the class Javadoc (line 21-29) promises no explicit `WaitEngine` needed. Implementation: `click()` = `waitForClickable(resolve()).click()` (line 362-364). `resolve()` (line 517-533) calls `resolveAll()` → plain `driver.findElements(...)` **once**; if empty → throws `LocatorException("No element found…")` immediately (line 528). `WaitEngine.waitForVisible(WebElement)/waitForClickable(WebElement)` (`WaitEngine.java:112,149`) only wait on an already-found element. Drivers set `implicitlyWait(Duration.ZERO)` (`LocalChromeDriverProvider.java:24`, all providers) and `WaitEngine.java:30` says "never set implicitlyWait", so there is no other wait layer.
- Consequences: (a) any element rendered after the action starts (SPA, post-navigation, AJAX) → immediate `LocatorException`; (b) the wait uses the *stale-able* element handle found before the wait; `WebDriverWait.ignoring(StaleElementReferenceException)` just keeps polling the same dead reference until timeout instead of re-resolving.
- Evidence of untested behaviour: `LocatorTest.resolve_throwsException_whenHealingDisabled_andElementNotFound` (`src/test/java/io/testfly/unit/LocatorTest.java:~210-216`) asserts an immediate `LocatorException` with mocks; no real-browser test exists (API-033).
- Contrast: `BasePage.click(By)` → `find(by).click()` (same path) so the entire POM helper layer inherits this; whereas `WaitEngine.waitForVisible(By)` (line 89) does poll.
- Fix: implement `resolve()` as a `WebDriverWait(timeouts.explicit)` poll that re-runs `resolveAll()` until non-empty/visible/clickable (re-resolve each iteration → also fixes staleness), then self-heal on timeout. Effort M. Regression risk: moderate (latency of negative checks; keep `count()`/`isVisible()` non-waiting as documented).
- Reproduction (needs a browser): open a page that inserts `<button id=b>` after 2 s via `setTimeout`; `Locator.id("b").click()` fails immediately with `LocatorException` while `WaitEngine.waitForClickable(By.id("b"))` succeeds.

### API-012 — `getByText()` XPath matches ancestors (first match = `<html>`) — P1 — DOĞRULANDI (XPath semantics reproduced in JDK, not in a browser) — Effort S
- `Locator.java:737-745` builds `.//*[contains(translate(normalize-space(.), …), 'text')]` (exact: `.//*[normalize-space(.)='text']`). `normalize-space(.)` is the string-value of the whole subtree, so every ancestor of the matching element matches too. `resolve()` returns `candidates.getFirst()` (line 538) = first in document order = the outermost element.
- Reproduction (`target/audit-scratch/tng/XP.java`, JDK XPath on `<html><body><div id='app'><form><button>Sign In</button></form></div></body></html>`) → matches `html, body, div, form, button` (5 nodes; `html` first). In a browser, `driver.findElements(By.xpath(".//*[…]"))` evaluates from the document node, so the same ordering applies.
- Existing tests only assert the generated XPath string (`SemanticLocatorTest.java:63-85`), not what it selects.
- Impact: `getByText("Sign In").click()` clicks the centre of `<html>`/`<body>`/outer container; `getText()` returns the whole page text; `exact()` has the same problem for containers whose entire text equals the label.
- Fix: select the innermost matches, e.g. `//*[contains(normalize-space(text-nodes…))]` using `text()` nodes (`//*[text()[contains(...)]]`) or add `[not(.//*[contains(…)])]`; prefer last/deepest candidate. Add browser-level test.

### API-013 — `Locator.type()/clear()` platform-agnostic chord storm — P2 — DOĞRULANDI — Effort S
- `Locator.java:386-399` `robustClear`: always sends `Keys.chord(Keys.COMMAND,"a")+BACK_SPACE`, then `Keys.chord(Keys.CONTROL,"a")+BACK_SPACE`, then `el.clear()` — 3 WebDriver round-trips for every `type()`; Javadoc claims "Command+A (macOS) and Control+A (Windows/Linux)" (platform-selected) but the code sends both. On Linux/Windows `Keys.COMMAND` is the Meta/Win key chord (may trigger OS/browser shortcuts). All exceptions are swallowed with a Turkish-language warning (`"Robust clear işlemi sırasında hata oluştu"`, line ~396) so a failed clear leaves old text and `sendKeys` appends → wrong data silently.
- `type(null)`/`type("")` clears the field (Javadoc: "types the given text") — acceptable but undocumented.
- Fix: detect target platform once (`Platform.getCurrent()` / `driver` capabilities), use `el.clear()` first and the chord only for controlled inputs when value remains; log in English via SLF4J and rethrow if the value is still non-empty.

### API-014 — `Locator extends By` ignores `SearchContext` — P2 — DOĞRULANDI — Effort S/M
- `Locator.java:508-511`: `public List<WebElement> findElements(SearchContext context) { return resolveAll(); }` — `resolveAll()` uses `driver()` / `DriverManager.getDriver()` and never the supplied context. `element.findElement(locator)` / `shadowRoot.findElement(locator)` / `WebDriverWait` over another driver therefore search the *whole page of the thread's driver*, violating the `By` contract. Also breaks multi-session/frames when a different driver is passed.
- Fix: when `context` is non-null use `context.findElements(effectiveRoot)`; keep the thread-driver fallback only for the `driver()` default.

### API-015 — Locator-based assertions: displayed-only filtering and no retry on `LocatorException` — P2 — DOĞRULANDI — Effort S
- `SeleniumAssert.LocatorBy.findElements` returns `locator.elements()` (`SeleniumAssert.java:136-141`), and `Locator.elements()` filters `WebElement::isDisplayed` (`Locator.java:498-502`). Therefore `assertThat(locator).count(n)` counts only visible elements (differs from `Locator.count()`), `assertThat(locator).isChecked()/hasAttribute()` on visually-hidden checkboxes/inputs (opacity:0/`display:none` custom controls) can never pass, while the same assertion with a `By` works.
- `LocatorException` is a `RuntimeException` (`LocatorException.java:6`), not a `NotFoundException`, so `WebDriverWait.until(...)` (default ignored = `NotFoundException`) aborts on the first `nth(i)`/`within(x)` miss (`Locator.java:~560-565`) instead of retrying — contradicting "auto-retrying" in `LocatorAssert` Javadoc.
- `LocatorAssert.poll` dereferences `TestFlyContext.getConfig().getTimeouts()` without a null guard (`LocatorAssert.java:433`) → NPE outside a bootstrapped run (WaitEngine guards this; inconsistent).
- Fix: make `LocatorException extends NoSuchElementException` (source compatible: still unchecked), don't filter by visibility inside `LocatorBy`, null-guard timeout lookup. `SeleniumAssert.extractBy` comments (lines ~100-125) describe "parsing toString" but the code wraps — stale comments (P3).

### API-016 — `SmartLocator` — P3 — DOĞRULANDI — Effort S
- `SmartLocator.java:46-63` uses `driver.findElement` once per strategy (no wait; implicitWait is 0), swallows every `Exception`, prints via `System.out.println("[SmartLocator] Resolved using: …")` for every call. `BasePage.smartFind` (`BasePage.java:423-431`) advertises it as resilient across environments but it fails on elements that appear a moment later. Fix: wrap in `WaitEngine`/`WebDriverWait` over the list, use SLF4J; keep as a documented boundary (By) API.

### API-017 — Javadoc / example drift — P2 — DOĞRULANDI — Effort S
- Deprecated `$()` is used in 11 Javadoc lines in `src/main`: `Locator.java:228,242,255,268`, `LocatorAssert.java:38`, `SeleniumAssert.java:27`, `SessionSupport.java:35,40`, `MultiSessionManager.java:22`, `BaseCucumberTest.java:37`, `LocatorSupport.java:11` (`grep -rnE '^\s*\*.*[^a-zA-Z]\$\(' src/main/java`).
- `BasePage.java:35-49` class Javadoc teaches `private static final By USERNAME = By.id("username"); type(USERNAME, user)` as the canonical POM — contradicts the semantic-first guidance (use `getByRole/getByLabel/getByTestId`, then `find(String)`; `By` only at boundaries: `WaitEngine`, frame/shadow/upload helpers, `SmartLocator`, raw Selenium interop — all of which the `BasePage` protected API legitimately keeps).
- `BasePage.waitMillis/waitSeconds` Javadoc points to `WaitEngine#waitForVisible(By)`; fine, but `getWait()` guidance is absent.
- Fix: replace examples with `getByLabel("Username").type(user)` / `find("#x")`; keep `By` only where the signature requires it.

### API-018 — `Role` is a CSS approximation — P3 — DOĞRULANDI — Effort S (doc)
- `LocatorSupport.java:~104` says `getByRole` "targets the accessibility tree rather than DOM structure". `Role.java:26-73` maps roles to CSS lists (e.g. `LINK="a[href], area[href], [role='link']"`, `BANNER="header, …"`, `REGION="section, …"`, `TEXTBOX` and `SPINBUTTON` both include `input[type=number]`). No computed role, no `aria-hidden`/`role="presentation"` handling, `<a role=button>` matches both LINK and BUTTON. `Role.java:~21` Javadoc `@since 3.1.0` vs `@TestFlyApi(since="1.0.0")`.
- Fix: reword docs ("CSS/ARIA-attribute approximation"); optionally evaluate `computedRole` via CDP `Accessibility.getFullAXTree` for Chromium.

### API-019 — Docs reference non-existent non-load APIs — P1 — DOĞRULANDI — Effort M
See §1 table: `io.testfly.core.*`, `io.testfly.locators.Role`, `RoleOptions`, `.fill()`, `.val()`, `getWait().waitFor*()`/`getWait().wait(ExpectedConditions)`/`getWait().waitForPageLoad()`; real signatures listed there. The docs team owns snippet compilation; this finding records the *real* signatures for the rewrite: `io.testfly.test.BaseTest/BasePage/BaseApiTest`, `io.testfly.locator.{Locator,Role}`, `getByRole(Role[, String])`, `Locator.type(String)`, `WaitEngine.waitForXxx(By)` (static) or `getWait().until(ExpectedConditions…)`.

---
## 4. Bridges (TestNG / JUnit5 / Cucumber) and retry

### API-020 — Retry off-by-one vs docs + default retries everything — P1 — DOĞRULANDI — Effort S
- Docs: `docs/configuration.md:182` "total attempts per test (1 = no retry, 2 = 1 initial + 1 retry)" and `:554` "default `1` … `1` means no retry".
- Code: `RetryListener.java:82` `if (attempt < maxAttempts) { attempt++; return true; }` ⇒ `maxAttempts=1` → one retry (2 executions). `TestFlyConfig.Retry` defaults `enabled=true`, `maxAttempts=1` (`TestFlyConfig.java:497-499`). The unit test `RetryListenerTest.retry_globalEnabled_singleAttempt_retriesOnce` (`RetryListenerTest.java:89-97`) codifies the code behaviour. JUnit5 does the same (`TestFlyExtension.java:365-381`, `maxRetries = maxAttempts`).
- Impact: with *zero* configuration every failing test is run twice, masking flakiness and distorting CI gates (`ci.maxFlakyTests`); the documented value semantics are wrong by one.
- Fix (choose one and align docs+tests): treat `maxAttempts` as total attempts (`attempt + 1 < maxAttempts`), and/or default `retry.enabled=false` so retry is opt-in (`@Retryable`) as AGENTS.md/README describe. Regression risk: behaviour change for all consumers → needs a CHANGELOG/migration note.

### API-021 — `@Retryable` semantics inconsistent — P2 — DOĞRULANDI (a,b,c) / GÜÇLÜ ŞÜPHE (d) — Effort M
- (a) `RetryListener.java:56-60`: `isGlobalRetry = retryConfig.isEnabled()` is always true after the earlier `return false` (line ~44), so `!isGlobalRetry && !isAnnotated` is dead code; `@Retryable` adds nothing except overriding the count, contrary to the comment "Method-level: only @Retryable tests retry".
- (b) TestNG: `retry.enabled=false` is a kill switch for `@Retryable` too; JUnit5 (`TestFlyExtension.java:420-427`) honours `@Retryable` even when `enabled=false`.
- (c) JUnit5: `catch (Throwable t)` (line ~410) retries `TestAbortedException`/assumption failures; user `@BeforeEach` isn't re-run before a retry, only driver/`@PreCondition` are recreated, so state set in `@BeforeEach` (login, navigation, page-object fields) is lost on retry.
- (d) TestNG `RetryListener` keeps `attempt` as an instance field; TestNG shares one analyzer per method across `@DataProvider` rows/`invocationCount`, so retries get consumed across rows (not reproduced).
- Fix: single `RetryPolicy` resolver shared by TestNG/JUnit5/Cucumber; skip `TestAbortedException`; document hook ordering.

### API-022 — Soft-assertion flush flips result inside `onTestSuccess` — P2 — GÜÇLÜ ŞÜPHE — Effort S/M
- `TestExecutionListener.java:164-176` sets `result.setStatus(FAILURE)` and calls `onTestFailure(result)` manually from `onTestSuccess`.
- Experiment (`target/audit-scratch/tng/T.java`, TestNG 7.9.0, listener flips status in `onTestSuccess`): `TestNG.getStatus()=1, hasFailure()=true`, built-in summary "Failures: 1", but a `TestListenerAdapter` still reports `passed=1, failed=0`. So different reporters observe different outcomes; `ITestListener#onTestFailure` of other listeners (ReportPortal agent, surefire, Allure) is not invoked by TestNG for this result. I did not run it under surefire, so the effect on `mvn test` exit code/XML is unverified.
- Fix: perform the soft-assert verdict in `IInvokedMethodListener.afterInvocation` (TestNG's supported place to change the status) or throw `AssertJ`-style `MultipleFailuresError` from an `@AfterMethod`-equivalent hook.

### API-023 — Cucumber after-hook ordering — P2 — GÜÇLÜ ŞÜPHE — Effort S
- `CucumberHooks.java:123` `@After(order = 20000)` performs screenshot, metrics **and driver quit**; the Javadoc (lines 55-62) states Cucumber runs higher-order `@After` first, "so this runs before user's `@After(order=10000)`". If that is true, the driver is already quit when user `@After` hooks (default order 10000: logout, cleanup, extra screenshots) run, so any such hook that touches the browser fails. Not run against Cucumber 7.20.1 in this audit.
- Fix: split into capture (high order, runs first) and quit (order below user hooks, e.g. 0) hooks.

---
## 5. Reporting, screenshots, logging

### API-024 — HTML report data block not `</script>`-safe — P2 — DOĞRULANDI — Effort S
- `HtmlReportGenerator.java:244,505-517` serialises report data with a default Jackson `ObjectMapper` and injects it with `String.replace("{{TESTFLY_DATA_JSON}}", …)` into `<script id="testfly-data" type="application/json">…</script>` (`report-template.html:1735`).
- Reproduction (`target/audit-scratch/tng/J.java`): `writeValueAsString(Map.of("error","… </script><img src=x onerror=alert(1)>"))` → output keeps `</script><img src=x onerror=alert(1)>` unescaped. Assertion messages that echo page HTML (very common in UI tests) or test data containing `</script>`/`<!--` terminate the data block → report renders blank/corrupt, and attacker-controlled page content becomes executable HTML in the local report.
- Second-order issue: the chained `.replace(...)` calls substitute `{{TOTAL_TESTS}}`, `{{PASSED}}`, `{{METADATA}}`… *inside already-inserted data* if the data contains those tokens.
- Fix: escape `<`→`\u003c` (custom `CharacterEscapes` or post-process), `\u2028/9`; substitute placeholders in a single pass. Template has two duplicate `escapeHtml` functions (`report-template.html:2240,4497`, P3).

### API-025 — Hard-coded `target/…` output paths — P2 — DOĞRULANDI — Effort M
- `ReportPaths.baseDir()` honours `-Dtestfly.reports.dir` and Gradle (`build`), but 19 sites bypass it: `ScreenshotManager.java:25` (`target/reports/screenshots`), `AllureReportAdapter.java:56`, `TraceRecorder.java:259`, `RecordingManager.java:155`, `HealLog.java:53`, `FlakinessAnalyzer.java:167`, `HtmlReportGenerator.java:188`, `ShardingMethodInterceptor.java:89`, `RemediationPatchGenerator.java:93`, `VisualAssert.java:330`, `TestFlyConfig.java:303,1263,2332`, `JUnitXmlReporter.java:38-41` (own duplicate Gradle detection).
- Impact: Gradle layouts and custom report dirs get screenshots/traces in `target/` while the HTML report lives elsewhere → broken links; CWD-relative paths break when forked JVM CWD differs from module dir (multi-module builds).
- Also `ScreenshotManager.capture` (lines 47-60): `OutputType.FILE` temp file is copied (`Files.copy`, line 55) but never deleted → one leaked temp PNG per screenshot; file name `sanitize(test)_<currentTimeMillis>.png` can collide for parallel data-provider rows (`FileAlreadyExistsException` swallowed → screenshot lost, returns null). Fix: `OutputType.BYTES` + `Files.write` with a unique name (nanoTime/UUID), route all paths through `ReportPaths`.

### API-026 — Logging inconsistency — P3 — DOĞRULANDI — Effort M
- `grep -rn "System\.\(out\|err\)\." src/main/java | wc -l` → 156; `grep -rln "org.slf4j.Logger" src/main/java | wc -l` → 3 (Locator, StepLogger and one more); `slf4j-api` is a declared dependency (`pom.xml:263`). `java.util.logging` is used in `ai/*`. `FrameworkBootstrap.java:68-69` prints a promotional MCP banner on every run. Mixed Turkish/English log messages (`Locator.java:~396`).
- Fix: standardise on SLF4J, make banner opt-out (`-Dtestfly.banner=false`).

---
## 6. AI / MCP / Playwright / Appium / SPI

### API-027 — AI providers hand-roll JSON — P2 — DOĞRULANDI — Effort S
- `ClaudeProvider.escapeJson` (`ClaudeProvider.java:~122-129`) escapes `\\ " \n \t`, **drops `\r`**, ignores other control chars (e.g. ANSI `\u001b` from stack traces) → invalid request JSON → HTTP 400 → `null` (silent); `extractContent` (lines ~96-120) does a first-`"text"` string scan and its `default` branch turns `\u00e7` into `u00e7` (no `\uXXXX` decoding). Same pattern in Gemini/OpenAI-compatible providers. `ActionCompiler` already uses Jackson `ObjectMapper` (`ActionCompiler.java:34`).
- `ClaudeProvider.java:65-66` sends the API key in both `x-api-key` and `Authorization: Bearer` headers (credential duplicated to custom `baseUrl` gateways).
- Fix: Jackson for request/response in all providers; send one auth header per provider.

### API-028 — DOM sent to third-party LLM without redaction — P2 — İYİLEŞTİRME — Effort M
- `DomPruner` (`ai/DomPruner.java:12-60`) strips script/style/svg/comments/events but keeps attributes and text; no redaction of `<input value=…>`, hidden CSRF tokens, `data-*` or visible PII (`grep -in "password\|redact\|mask" DomPruner.java` → 0). Used by `AiHealingEngine`, `LocatorAssert.satisfiesAi/violatesAi` (`LocatorAssert.java:~395-405`), `ActionCompiler`, `AiFailureAnalyzer`. Calls are explicit/opt-in (`ai.failureAnalysis=false`, `generatePatch=false` defaults; `ai.enabled=true` default), so this is a privacy design gap, not an active leak.
- Fix: redact `value`, `type=password|hidden`, tokens; add `ai.redactPatterns`; document data flow.

### API-029 — Playwright/Appium/MCP not in repo — P2 — DOĞRULANDI — Effort S (docs)
- No source integration exists (see §1). Marketing claims: `README.md:3` "web, API, mobile, and AI-powered", `pom.xml:13` description "web, API, mobile, and AI/MCP-powered", `docs/intro.md:2`; `README.md:96-97` "TestFly includes a first-class MCP server" — the server is a separate npm package/repo (`github.com/hakanngul/testfly-mcp`, referenced by `FrameworkBootstrap.java:69` and `TestFlyApi.java` Javadoc); `docs/ai/testfly-mcp.md`, `docs/cli.md` document it as a bridge to Playwright MCP. The in-repo MCP-adjacent surface is only `.testfly/action-cache.json` (`agent/ActionCache`) and `target/remediations/*.patch` (`RemediationPatchGenerator`). "mobile" is achievable only by writing an Appium `NamedDriverProvider` (the `browser/DeviceEmulator` is CDP device emulation, not Appium).
- Fix: reword to "mobile via Appium custom driver provider (BYO)" and "MCP bridge (separate package)"; the MCP bridge itself is **not auditable from this repo** (listed in §8).

### API-030 — SPI robustness — P3 — DOĞRULANDI — Effort S
- `PluginRegistry.loadAll` (`PluginRegistry.java:28-40`): `plugin.onLoad(config)` and `ServiceLoader` iteration (`ServiceConfigurationError`) are not guarded → one bad plugin aborts `FrameworkBootstrap`; `HookRegistry.loadAll` same for iteration (hook *calls* are guarded). `AGENTS.md`/`TestFlyPlugin` Javadoc say `minFrameworkVersion` should "fail fast" (`IncompatiblePluginException`), but `PluginRegistry.checkVersion` (line 74-82) just prints to stderr and skips.
- `FrameworkVersion.loadVersion` fallbacks look for `META-INF/maven/io.testfly/testfly/pom.properties` (`FrameworkVersion.java:90-105`) but the artifact group is `io.github.hakanngul` (`pom.xml:7`), so only the jar manifest (`addDefaultImplementationEntries`, `pom.xml:372`) can supply the version; in IDE/`target/classes`/shaded setups it returns `"0.0.0"` and every plugin with a real `minFrameworkVersion` is skipped.
- `DriverProviderRegistry.registry` is a plain `LinkedHashMap`; `register/loadAll` are `synchronized` but `find()` (line 49) is not.
- Fix: guard/log per-plugin; decide fail-fast vs skip and document; use the real group id (or `Package`/properties resource generated by the build); use `ConcurrentHashMap`.

### API-031 — Custom-driver docs wrong — P2 — DOĞRULANDI — Effort S
- `docs/extensibility/custom-drivers.md`: "Remote mode (`browser.mode: remote`)" — the key is `execution.mode` (`DriverProviderFactory.java:17-27`); in `remote`/`browserstack`/`saucelabs` modes custom providers are **never consulted** (they are only checked after those branches, line 30), so an Appium provider cannot be used with a cloud/grid mode; the example after "Register via SPI" sets `browser.name: browserstack` for an `android` provider; the Appium example does `new AndroidDriver(new URL(...), options)` inside `createDriver()` which declares no checked exception (`DriverProvider.createDriver()` has no `throws`, and `DriverProvider` is not `@TestFlyApi`-annotated) → compile error.
- Fix: correct docs; consider `default WebDriver createDriver() throws Exception` (source-compatible for implementers, not for callers) or document wrapping.

### API-032 — `@TestFlyApi(since)` metadata unusable — P2 — DOĞRULANDI — Effort S/M
- `grep -rhoE '@TestFlyApi\(since = "[0-9.]+"' src/main/java | sort | uniq -c` → values include 1.10.0 (22×), 1.9.0 (18×), 1.6.0 (23×), 2.0.0/2.2/2.4/2.5.0 (11×), 3.0.0 (3×), 1.13.0 — while the artifact is `1.0.7` (`pom.xml:9`; AGENTS.md says 1.1.0; docs mix 1.0.0–2.6.0). `Locator` is `since="1.4.0"`, `LoadTest` `since="1.1.0"`, `@Deprecated(since="1.1.0", forRemoval=true)` on `$()` (`LocatorSupport.java:74,84,95`) i.e. deprecated *for removal* in a version later than the current release.
- Impact: the "stable public API / no breaking within major" contract (`TestFlyApi.java` Javadoc, AGENTS.md) cannot be applied or audited; `forRemoval=true` conflicts with "deprecate ≥ one minor before removal".
- Fix: pick the real release history, rewrite `since` values (script), downgrade `forRemoval` until a removal release is scheduled.

### API-033 — No browser-level coverage; some tests codify wrong behaviour — P2 — İYİLEŞTİRME — Effort L
- `src/test/java/io/testfly/{unit,integration,…}`: integration dirs are `ai, db, email, loadtest, network, testmanagement`; no real-browser test exists for `Locator`, `WaitEngine`, `BasePage`, `LocatorAssert`. `LocatorTest`/`SemanticLocatorTest` use Mockito or string checks (API-011/012 slip through). Tests that encode defects: `JdkLoadEngineTest.testErrorEndpoint` (500 = success, API-002), `RetryListenerTest.retry_globalEnabled_singleAttempt_retriesOnce` (API-020). 1361 passing tests (build-test.md) therefore say nothing about these defects.
- Fix: add a small headless-Chrome (or Selenium-HtmlUnit-free `file://` pages) integration suite: delayed element, stale re-render, nested text, hidden checkbox, scoped `By.findElement`, soft-assert flush; JDK `HttpServer` tests for status codes (reuse `JdkLoadEngineTest` server).

---
## 7. Framework-managed driver vs raw `By` — assessment
- Framework-managed driver is well-founded: `BasePage()` → `DriverManager.getDriver()` lazily (`BasePage.java:74-98`), `BasePage(WebDriver)` for explicit injection; `Locator` uses the thread driver (`Locator.java:~688`). Where `By` appears it is legitimate boundary use: `WaitEngine` statics, `withinFrame*`, `shadow*`, `upload`, `SmartLocator`, `Locator.of(By)/toBy()` interop, `LocatorAssert(By)`. 
- Inconsistencies: public Javadoc/examples teach `By` constants as the primary POM style (API-017), and `Locator` is itself a `By` whose `findElements` ignores context (API-014) — meaning a Locator passed to a Selenium API silently binds to the thread driver.

---
## 8. Sub-areas not audited / limits
- **No real browser run** (no Chrome driver launched; no browser tests exist). API-011/012/013/015/023 are code-reading findings; API-012's XPath behaviour was reproduced with the JDK XPath engine only.
- **Gatling engine internals** (`GatlingEngine`, `GatlingRunConfig`, `TestFlyGatlingSimulation`, subprocess handling) only skimmed (`GatlingBridge`, `GatlingResultsParser` status map). Engine-parity between JDK and Gatling is therefore only partially assessed.
- **Cucumber bridge**: read hook ordering and retry context only; step logger, `BaseCucumberTest`, quarantine tags not audited. **JUnit5**: retry, skip-browser heuristic only; parallel mode / `@PreCondition` ordering not audited.
- **DriverManager lifecycle, thread-safety & cleanup** (Area A) — only the load-test guard and provider selection were read.
- **MCP bridge (`@testfly/mcp`)** lives in another repo (`hakanngul/testfly-mcp`); not auditable here. `ActionCompiler`/`ActionCache`/`ai.remediation` read only superficially.
- `ApiClient`/`BaseApiTest`, `db`, `email`, `network`, `visual`, `recording`, `tracing`, `healing`, `quarantine`, `flakiness`, `sharding`, `testmanagement`, ReportPortal/Allure adapters: not audited in depth (other areas / out of scope).
- Docs snippet compilation is the docs team's task; here only real signatures were confirmed.
