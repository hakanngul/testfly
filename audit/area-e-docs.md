# AREA E — Documentation & Developer Experience (audit-only, no repo changes)

Branch at audit time: `chore/docs-cloudflare-workers`. Version source of truth: `pom.xml:7-9` = `io.github.hakanngul:testfly:1.0.7`.
Audit output only under `target/audit-scratch/` (gitignored). `git status --porcelain` after the audit shows only the two pre-existing `.agents/memories/*` modifications.

## 0. Method and evidence base

| Step | What was done | Artifact (all in `target/audit-scratch/`) |
|---|---|---|
| Extract | 1 213 fenced code blocks from `docs-site/docs` (EN), `docs-site/i18n/tr/.../current` (TR), `README.md`. 597 are `java`. | `extract.py` -> `blocks.json` |
| Import check | Every `import io.testfly...` in Java blocks resolved against `src/main/java`: 204 resolve, 17 do not (8 distinct classes). | `imports.py` |
| Compile | Each Java block wrapped (complete unit / member list / statements-in-method extending `BaseTest`), wildcard imports of all `io.testfly.*` packages added, `javac -Xlint:removal,deprecation` against `target/classes` + `classpath.txt` (JDK 21.0.12). Output in `docsnip/bNNNN/`. | `gen.py`, `runjavac.sh`, `parse.py`, `errs.py`, `sym.py` |
| Load-test re-compile | The 27 loadtest blocks re-compiled with `extends BaseLoadTest` to unmask errors hidden behind the missing `load()` (`lt2/`). | |
| Config check | 229 YAML blocks fed into the real `ConfigurationLoader.load()` (`repro/Y.java`); `gs.yml` reproduces the getting-started failure (`repro/Repro.java`). | `repro/yres.txt` |
| Parity | EN vs TR file lists, fence counts, per-page Java token diff. | inline scripts |
| Links | Markdown links resolved against doc ids, `file:` scheme grep, README/AGENTS relative links, sidebar ids. | inline scripts |
| Registry | Fetched Maven Central metadata for both coordinates. | see DOC-001/002 |

Compile numbers (raw): 597 Java blocks -> 282 compile clean, 315 fail. **The raw failure count is not a defect count.** Most failures are harness artifacts: fragments that refer to undefined placeholder classes (`LoginPage`, `User`, ...), third-party code shown for comparison (Selenide, RestAssured, WebDriverManager, Appium, TestNG listeners), and BasePage-only protected helpers shown as bare statements. Every finding below was triaged by hand against the real source; harness artifacts are excluded and listed in section 4.

Not verified / limitations:
- `npm run build` was not re-run (it writes `docs-site/build`, i.e. inside the repo). The scratchpad and discovery notes record that EN/TR builds pass; that does not validate Java samples, which is exactly what this audit shows.
- Javadoc HTML generation was not re-run; Javadoc was checked by grep of source comments only.
- Kotlin/Groovy/XML/Gherkin blocks were not compiled. External `http(s)` links were not probed.
- Maven Central state was read once through `repo1.maven.org`; CDN/metadata lag cannot be excluded for 1.0.5-1.0.7.

---

## 1. Prior hypotheses: verdict

| # | Hypothesis | Verdict | Evidence / correction |
|---|---|---|---|
| 1 | `io.testfly.core.BasePage` (real `io.testfly.test.BasePage`) | **CONFIRMED** | `ai/prompt-recipes.md:37`; also `io.testfly.core.BaseTest` at `ai/prompt-recipes.md:82`, `guides/video-recording.md:101` (EN+TR). `find src/main/java -path '*core*'` has no such package. |
| 2 | `io.testfly.locators.Role` (real `io.testfly.locator.Role`) | **CONFIRMED** | `ai/prompt-recipes.md:38` (EN+TR). javac: `package io.testfly.locators does not exist`. |
| 3 | `RoleOptions` | **CONFIRMED** | `ai/testfly-mcp.md:110,119-121` (EN+TR). Package `io.testfly.locator` exists but has only `Locator`, `LocatorException`, `Role`. Real API: `getByRole(Role, String)` (`LocatorSupport.java`) or `.withName(...)`. |
| 4 | `.fill()` / `.val()` | **CONFIRMED** | `.fill`: `ai/prompt-recipes.md:49,54`. `.val`: `guides/video-recording.md:110,111,122,123`. `Locator` exposes `type`, `append`, `clear`, `click`... no `fill`/`val`/`setValue`. |
| 5 | `getWait().waitForVisible()` / `waitFor*` | **CONFIRMED, scope narrowed** | `getWait()` returns Selenium `WebDriverWait` (`NavigationSupport.java:47`). EN `guides/wait-engine.md` is correct (uses static `WaitEngine`). Wrong sites listed in DOC-013. |
| 6 | `@LoadEngine` | **CONFIRMED** | `loadtest/annotations.md:10,55,60,68,91`, `loadtest/engines.md:65`. No such type in `io.testfly.loadtest`. Engine is `@LoadTest(engine = "...")`. |
| 7 | Outdated `@LoadTest` params | **CONFIRMED** | Real: `users, rampUp, hold, cooldown, engine, baseUrl` (`LoadTest.java`). Docs use `rampUpSeconds, durationSeconds, targetRps, scenarioName` (`annotations.md:19-50`, `getting-started.md:106`). |
| 8a | `.targetRps()` | **CONFIRMED** | `loadtest/fluent-api.md:96,100`; javac `cannot find symbol targetRps()`. |
| 8b | `.fromCsv()` | **CONFIRMED** | 12 compile errors; `loadtest/feeders.md:25,60,64,76,99`, `examples.md:61`. Real: `LoadTestFeeder.csv(path)`. |
| 8c | `.runAsync()` | **REFUTED as a framework method** | `loadtest/examples.md:106` is `CompletableFuture.runAsync(...)` (JDK). The surrounding sample is still broken (DOC-005, DOC-007). |
| 9 | Deprecated `$()` recommended for new code | **PARTIALLY CONFIRMED / mostly OUTDATED** | `from-selenide.md:23-29,141` already tells users to replace `$` with `find`; most pages use `find()`. Residual new-code use: `video-recording.md:110-124`, `guides/assertions.md:81`, plus prose naming `$()` as the API (DOC-018). `$$()` listed in `junit5.md:102`, `cucumber.md:168` **does not exist** in source (`grep -rn '\$\$' src/main` is empty). |
| 10 | By + manual `WebDriver` presented as the core pattern | **PARTIALLY CONFIRMED (API is valid, guidance is skewed)** | README quick-start uses `BaseTest` (framework-managed). But `BasePage(WebDriver)` + `new XPage(getDriver())` is the page-object pattern in README:296-337, `intro.md:30`, `guides/base-page.md`, `browser-lifecycle.md:89`, `parallel.md:124,137`, `external-test-data.md:34`, `guides/precondition.md:22`. Zero doc blocks use the no-arg `BasePage()` (`grep "super();" docs README` is empty) although `BasePage.java:74` provides it. See DOC-019. |
| 11 | TR lacks `ci/bitbucket-pipelines`, `loadtest/distributed-docker-k8s`, `migration/from-restassured`, `reporting/allure` | **CONFIRMED, list is complete** | `comm` of EN vs TR file lists: exactly those 4 missing, 0 TR-only. Divergences on existing pages are additional (DOC-022). |
| 12 | Invalid `file:///` links | **CONFIRMED** | 3 distinct links, `guides/assertions.md:27-28` in EN and TR (DOC-023). No other `file:` URL in docs. |
| 13 | Version / Maven coordinate inconsistency | **CONFIRMED and worse than assumed** | DOC-001, DOC-002. |
| 14 | Docusaurus build passing does not validate Java | **CONFIRMED** | See section 0 numbers; no CI job builds docs or checks snippets (DOC-027). |

---

## 2. Findings

Legend: classification DOĞRULANDI = reproduced with real output; GÜÇLÜ ŞÜPHE = strong indication, not fully proven; İYİLEŞTİRME = improvement suggestion. Effort S/M/L. Paths are repo-relative; TR = `docs-site/i18n/tr/docusaurus-plugin-content-docs/current/` (same line numbers unless noted).

### Group A: Install, coordinates, versions, onboarding

#### DOC-001 — Docs publish a Maven/Gradle coordinate that does not exist on Maven Central (P1, DOĞRULANDI, S/M)
- Problem / root cause: the artifact is `io.github.hakanngul:testfly` (`pom.xml:7`; README:29,182; `loadtest/getting-started.md:31`; `docs-site/src/data/homeData.js:769`). Pages written before/after the group rename still use `io.testfly`.
- Where (EN; TR identical unless noted): `getting-started.md:50,61,75`; `gradle.md:32,49,115,131,277`; `junit5.md:24,51`; `cucumber.md:22`; `migration/from-selenium-testng.md:27`; `migration/from-serenity.md:124`; `migration/from-selenide.md:131`; `intro.md:13` (badge URL `maven-central/v/io.testfly/testfly` while link text points to `io.github.hakanngul`); `changelog.md:206,271`; `CONTRIBUTING.md:76`; `CHANGELOG.md:180,253`; `AGENTS.md:44-45`.
- Evidence: `GET https://repo1.maven.org/maven2/io/testfly/testfly/maven-metadata.xml` -> HTTP 404. `.../io/github/hakanngul/testfly/maven-metadata.xml` -> 200.
- Impact: the primary "Getting Started" Maven and Gradle tabs fail dependency resolution for every new user. Highest onboarding risk in the repo.
- Fix: single source of truth `io.github.hakanngul:testfly` for all install snippets; fix `intro.md` badge; keep historical `io.testfly` only inside changelog history entries marked as historical. Drive snippets from one constant (see DOC-027).
- Dependencies/regression: depends on DOC-002 for the version. Docs-only; no runtime risk. EN+TR must change together.
- Repro: `grep -rn "io.testfly:testfly\|<groupId>io.testfly" docs-site/docs docs-site/i18n README.md CONTRIBUTING.md`; `curl -I https://repo1.maven.org/maven2/io/testfly/testfly/maven-metadata.xml`.

#### DOC-002 — Version numbers contradict each other and point to releases that are not on Central (P1, DOĞRULANDI, S)
- Evidence:
  - Central metadata for `io.github.hakanngul:testfly`: versions 1.0.0-1.0.4, `<release>1.0.4`, `lastUpdated 20260907122817`. `.../1.0.7/testfly-1.0.7.pom` -> 404. Central's 1.0.4 POM compiles for Java 17 (`<java.version>17`) whereas HEAD requires 21 (`pom.xml:40-42`, `<release>21` at `pom.xml:322`).
  - In-repo: `pom.xml` 1.0.7; `target/testfly-1.0.6.jar` (stale local artifact); README 1.0.7; `getting-started.md` 1.0.7; `loadtest/getting-started.md` 1.0.7; `cli.md:25` 1.0.7; `junit5.md` Maven 1.0.7 vs Gradle **1.0.4** (`:51`) (TR Gradle **1.0.0**); `cucumber.md:24` and `migration/from-selenium-testng.md:29` **1.0.0**; `gradle.md` **1.0.0** x5; `ai/testfly-mcp.md:91` "TestFly 1.0.6"; **TR `gradle.md:32,49,115,131,277` = 2.6.0**; `AGENTS.md:45` **1.1.0**, Selenium 4.40.0 (pom: 4.48.0).
  - Code metadata: `@TestFlyApi(since=...)` has 1.10.0 (22x), 1.9.0, 2.0.0, 2.2.0, 2.4.0, 2.5.0, 3.0.0, 1.1.0 (41x) while the project is 1.0.7 (DOC-026).
- Impact: users pin a version that either does not exist, is older than the documented features (e.g. load testing, `@TestFlyApi(since="1.1.0")` APIs documented against 1.0.0), or is unpublished.
- Fix: decide the publish state first (is 1.0.5-1.0.7 on Central? Maven Central metadata says no). Then update all occurrences per the version-bump checklist in `AGENTS.md` plus the files it omits (`gradle.md`, `cucumber.md`, `migration/*`, `loadtest/getting-started.md`, `cli.md`, `ai/testfly-mcp.md`, `homeData.js:667,771`, `pages/index.js:459`). Add a CI check that all `<version>` for the TestFly artifact equal `pom.xml`.
- Dependencies: release decision (owner). Regression: none at runtime.
- Repro: `target/audit-scratch/versions.txt`; `curl https://repo1.maven.org/maven2/io/github/hakanngul/testfly/maven-metadata.xml`.

#### DOC-003 — Getting-started `testfly.yml` fails at startup (P1, DOĞRULANDI, S)
- Problem: `getting-started.md:98-109` (EN+TR) omits `execution.mode`. `ConfigurationLoader.validate` (`ConfigurationLoader.java:304-306`) throws before `TestFlyDefaults.applyMissing` runs (`FrameworkBootstrap.java:45-46` loads first, applies defaults second).
- Evidence (real run, `target/audit-scratch/repro`):
  ```
  gs.yml -> IllegalStateException: Execution mode must be specified
  readme.yml -> OK        (README quick-start yaml, which includes mode: local)
  ```
- Impact: the page titled "first test in under 5 minutes" produces an exception on the first run.
- Fix: add `mode: local` under `execution`; also state in docs that `execution.mode`, `browser.name`, `timeouts.explicit/pageLoad` are mandatory (README says it, getting-started does not). Alternatively make `mode` default to `local` in code (behavior change, discuss before doing).
- Also flagged by the YAML harness as "full-file-looking but missing mode" (GÜÇLÜ ŞÜPHE, fragment vs complete file not individually verified): `guides/testfly-yml-guide.md:50`, `migration/from-selenium-testng.md:220`, `migration/from-serenity.md:64`, `migration/from-webdrivermanager.md:69`, `guides/video-recording.md:143`, `extensibility/custom-drivers.md:61,105`, `junit5.md:371`. Missing `timeouts`: README:679, `cucumber.md:108`, `gradle.md:152`.
- Dependencies: none. Regression: none.
- Repro: `java -Dtestfly.config=<file> -cp target/classes:$(cat classpath.txt):. Repro gs.yml`.

#### DOC-028 — Getting-started build/run details (P3, GÜÇLÜ ŞÜPHE / İYİLEŞTİRME, S)
- `getting-started.md` Step 4 creates `testng.xml` but never wires it (no Surefire `suiteXmlFiles`, no Gradle `suites`), and shows no `maven-compiler-plugin`/`release 21` or Surefire configuration (README:20-50 does). Not executed end-to-end, so classification is suspicion.
- `gradle.md` JUnit 5 block (`:112-135`) lacks `junit-platform-launcher`, which `junit5.md:38,53` and `pom.xml` say consumers must add; no Java toolchain/`sourceCompatibility=21` in Gradle snippets.
- Fix: one canonical minimal project (pom + testfly.yml + one test) validated in CI (DOC-027).

### Group B: Load-test documentation (EN, TR identical)

#### DOC-005 — Every load-test sample extends `BaseTest`, which has no `load()` (P1, DOĞRULANDI, S)
- Root cause: `load()/loadScenario()` live in `LoadTestSupport` (`test/support/LoadTestSupport.java:33,49`), implemented by `BaseLoadTest` only (`loadtest/BaseLoadTest.java:79-85`). `BaseTest` implements `LocatorSupport ... ClockSupport` (`BaseTest.java:40-43`), not `LoadTestSupport`; `BaseJUnit5Test` neither.
- Where: `loadtest/annotations.md:23,65,92`; `examples.md:25,57,101`; `fluent-api.md:60`; `getting-started.md:68,103`; `assertions.md`, `engines.md:67`, `feeders.md:78` (blocks); changelog claim `changelog.md:92` ("available in BaseTest, BaseApiTest, BaseLoadTest, BaseJUnit5Test") is false for three of the four.
- Evidence: 35 javac errors `cannot find symbol: method load(String)` in `docsnip/`. After switching the base class to `BaseLoadTest` (`lt2/`), those errors disappear and the real DSL errors of DOC-006/007/008 surface.
- Impact: none of the load-test samples compile as written.
- Fix: either (a) correct docs to `extends BaseLoadTest`, or (b) decide that `BaseTest` should implement `LoadTestSupport` (API addition, `@TestFlyApi` rule: default methods only) and then correct the changelog. Docs must follow whichever is chosen. `examples.md:101` (hybrid UI+load) needs (b) or a different structure because `BaseLoadTest` has no `open()`/`getByRole`.
- Dependencies: product decision (a)/(b). Regression: low.

#### DOC-006 — `@LoadTest` / `@LoadEngine` contract is fictional (P1, DOĞRULANDI, S)
- Real annotation (`loadtest/LoadTest.java`, `@TestFlyApi(since="1.1.0")`): `int users; String rampUp, hold, cooldown, engine, baseUrl` (duration strings like `"10s"`).
- Docs: `annotations.md:19-50` (`rampUpSeconds`, `durationSeconds`, `targetRps`, `scenarioName` attribute table), `annotations.md:55-102` (`@LoadEngine("gatling")`, `import io.testfly.loadtest.LoadEngine`), `engines.md:65`, `getting-started.md:106`, `changelog.md:94` (lists `duration`, `targetRps`, `feeders`, `warmUp`, `.during()`).
- Evidence: javac `cannot find symbol: method durationSeconds() / rampUpSeconds() / targetRps() | location: @interface LoadTest`; `cannot find symbol: class LoadEngine | location: package io.testfly.loadtest`.
- Also wrong: precedence statement `annotations.md:36` ("fluent calls take precedence") is correct per `LoadTestConfig.resolveFor` (scenario > method > class > YAML) but the attribute table default values (`users=1`, `durationSeconds=10`) are wrong: real defaults are `users=-1` (inherit) and empty strings.
- Fix: rewrite the table and samples: `@LoadTest(users = 50, rampUp = "10s", hold = "30s", engine = "gatling")`. Remove `@LoadEngine` and the TR counterparts.
- Dependencies: DOC-005. Regression: none.

#### DOC-007 — Fluent load DSL shown does not exist (P1, DOĞRULANDI, M)
- Real `LoadScenario` (`loadtest/LoadScenario.java`): `single(path)`, `named(name)`, `users`, `rampUp(Duration)`, `hold(Duration)`, `cooldown`, `engine`, `baseUrl`, `step(String)` returning `LoadStep`, `get/post/put/delete/patch(String path)`, `feed(LoadTestFeeder)`, `feedCsv`, `feedJson`, `thinkTime`, `run()`. `LoadStep` carries `header/body/queryParam/formParam/check/extract`.
- Docs use: `step(name, req -> req.get(...))` (`fluent-api.md:65`, `examples.md:64`, `assertions.md:78`), `load(LoadScenario)` (`fluent-api.md:73`, `examples.md:73`, `assertions.md:81`), `.get()` no-arg and `.post(jsonPayload)` as body, `.header()`/`.queryParam()` directly on `LoadScenario` (`fluent-api.md:19-45`), `.targetRps(250)` (`fluent-api.md:96`), method summary table `fluent-api.md:107-122`, `examples.md:116-118` `getByRole("heading","System Status")` (no String overload; `Role.HEADING` is an enum) and nonexistent `assertVisible(...)`, `click(Locator)`.
- Evidence: lt2 javac: `method step in class LoadScenario cannot be applied to given types`; `incompatible types: LoadScenario cannot be converted to String`; `method get in class LoadScenario cannot be applied to given types`; `cannot find symbol targetRps(int)`.
- Fix: rewrite from `BaseLoadTest` Javadoc (`BaseLoadTest.java:30-70` shows the real usage) and compile the result. `targetRps` is either removed from docs or implemented (product decision).
- Dependencies: DOC-005. Regression: none for docs.

#### DOC-008 — Feeder API wrong (P2, DOĞRULANDI, S)
- Real: `LoadTestFeeder.csv(path)`, `json(path)`, `random(var,min,max)`, `uuid`, `sequence`, `constant`; abstract `next/reset/hasNext`.
- Docs: `fromCsv` (x6), `fromList`, `fromSupplier`, strategy chain `.circular()/.random()/.batch()` (`feeders.md:25-99`, `examples.md:61`).
- Fix: rewrite `feeders.md` with `csv/json/random/uuid/sequence/constant` and `LoadScenario.feedCsv/feedJson`.

#### DOC-009 — Distributed load page promises behavior that does not exist (P2, DOĞRULANDI for env vars/enforcer, GÜÇLÜ ŞÜPHE for live streaming, S) — EN only page
- `loadtest/distributed-docker-k8s.md:46-70` uses `TESTFLY_LOAD_VUSERS`, `TESTFLY_API_BASEURL`, `REPORTPORTAL_ENABLED`. `grep -rn "TESTFLY_" src/main/java` finds only `TESTFLY_ENV` (ReportPortal) and a report placeholder. `ConfigurationLoader` only resolves `${VAR}` placeholders inside YAML (`ConfigurationLoader.java:88,146`), so these variables are inert unless `testfly.yml` references them.
- `:71-77` claims `BuildThresholdEnforcer` verifies "error rates under 0.1% and P99". The class gates only pass-rate and flaky count (`ci/BuildThresholdEnforcer.java:41-66`). "Each pod streams live percentiles to ReportPortal" is unsubstantiated in `reporting/reportportal/*` (not exhaustively verified).
- Also marketing numbers "100,000+ RPS" with no benchmark source.
- Fix: replace with a `testfly.yml` using `${LOAD_USERS:-10}` placeholders (supported syntax), remove or qualify the enforcer/streaming claims, and translate (DOC-021).

### Group C: Wrong imports, nonexistent members, wrong base-class assumptions

#### DOC-010 — Whole pages that cannot compile: `ai/prompt-recipes.md`, `guides/video-recording.md` (P1, DOĞRULANDI, S)
- `ai/prompt-recipes.md:37-38,82-83` imports `io.testfly.core.BasePage`, `io.testfly.locators.Role`, `io.testfly.core.BaseTest`, `io.testfly.examples.pages.LoginPage`; `:43` `public LoginPage open()` clashes with `NavigationSupport.open()` (javac: `open() in LoginPage cannot implement open() in NavigationSupport`); `:49,54` `.fill()`.
- `guides/video-recording.md:101` `io.testfly.core.BaseTest`; `:110-124` `$("...").val(...)` (no `val`; `$` is deprecated for removal: 12 javac removal warnings from this file alone).
- This page is the AI prompt template that users paste into LLMs, so errors propagate into generated code.
- Fix: `io.testfly.test.BasePage/BaseTest`, `io.testfly.locator.Role`, `find("#x").type("...")`, rename `open()` to `openLogin()`/`goTo()`.
- Regression: none. TR identical, fix both.

#### DOC-011 — Package/class names not in public API: `io.testfly.context.TestFlyContext`, TR `io.testfly.api.ApiResponse` (P2, DOĞRULANDI, S)
- `extensibility/plugins.md:71-72` imports `io.testfly.context.TestFlyContext`; the class is `io.testfly.internal.TestFlyContext` (`internal/TestFlyContext.java`), an internal, unstable package per `AGENTS.md` ("Internal classes ... may change freely"). Docs steer plugin authors to an unstable type.
- TR `recipes/oauth-sso.md:21` `io.testfly.api.ApiResponse` (real `io.testfly.client.ApiResponse`; EN is correct) - an EN/TR divergence.
- `extensibility/plugins.md:35` `config.getBrowser().getBaseUrl()`: `Browser` has no `baseUrl` (it is `getExecution().getBaseUrl()`, `TestFlyConfig.java:214`).
- Fix: expose a stable accessor (e.g. the `TestFlyConfig` already passed to `onLoad`) in the sample and avoid `TestFlyContext`.

#### DOC-012 — `RoleOptions` (P2, DOĞRULANDI, S)
- `ai/testfly-mcp.md:107-121` shows the MCP-generated code using `new RoleOptions().setName("Username")`. This is the documented "compile-ready" output of the codegen bridge; if the bridge really emits this, the generator (`@testfly/mcp`, separate repo, not audited) is wrong too. Real: `getByRole(Role.TEXTBOX, "Username")`.
- Also `:107` the block starts with `package ...;` inside a list item (formatting).

#### DOC-013 — `getWait().waitFor*` and `getWait().wait(...)` (P1, DOĞRULANDI, S)
- Root cause: `NavigationSupport.getWait()` returns `org.openqa.selenium.support.ui.WebDriverWait`; the `waitFor*` methods are on the static `WaitEngine` (and a few on `NavigationSupport`: `waitForUrlContains`, `waitForUrlMatches`, `waitForTitle`, `waitForPageLoad`). `getWait().wait(ExpectedCondition)` resolves to `Object.wait(long)`.
- Sites (EN): `why/why-waitengine.md:56,90`; `recipes/oauth-sso.md:74,80,132`; `recipes/infinite-scroll.md:36,69,106`; `migration/from-selenium-testng.md:120-121`. TR additionally: whole `guides/wait-engine.md` (lines 20-143: ~20 `getWait().waitFor*` calls, vs correct EN), `guides/base-page.md:85,97,122`.
- Evidence (javac): `cannot find symbol: method waitForUrlContains(String) | location: class WebDriverWait`; `incompatible types: ExpectedCondition<List<WebElement>> cannot be converted to long`; `ExpectedCondition<Boolean> cannot be converted to long`.
- Fix: `WaitEngine.waitForX(...)`, `waitForUrlContains(...)` (inherited default), `getWait().until(...)`. Make TR `wait-engine.md`/`base-page.md` mirror EN.
- Dependencies: none. Regression: none.

#### DOC-014 — `cucumber.md` / `junit5.md` API samples wrong (P1, DOĞRULANDI, S) (EN+TR)
- `ScenarioContext.put/get(...)` called statically: `cucumber.md:200,206,233`. Real: instance methods (`context/ScenarioContext.java:21-48`), reached through `ctx()` (`ContextSupport.java:18`). Only `clear()` is static. The "Automatic Cleanup" note `cucumber.md:211` itself uses `ScenarioContext.clear()` correctly.
- `apiPost("/api/users", json)` (two args) and `.assertThat().statusCode(201).jsonPath().getString("id")`: `cucumber.md:229-231`, `junit5.md:178-181,212-213`. Real: `apiPost(String path)` returns `ApiClient`; chain `.body(json).send().assertStatus(201).json("$.id")` (`ApiSupport.java:26`, `ApiClient.body`, `ApiResponse.assertStatus/json`).
- `db().table("users").where(...).assertExists()` (`junit5.md:185-187`): `DbClient` has `query/scalar/assertRowExists/assertNoRow/assertRowCount` only.
- `response.assertThat()` and `bodyContains` do not exist (`ApiResponse.assertBodyContains(String)`).
- Fix: rewrite with real chain; keep BDD samples compile-checked.
- Impact: these are the two flagship integration pages (Cucumber, JUnit 5).

#### DOC-015 — `Locator.first()` treated as `WebElement` (P2, DOĞRULANDI, S)
- `recipes/drag-and-drop.md:31,32,51,76,77`, `recipes/tables.md:32`: `WebElement x = find(...).first();` `Locator.first()` returns `Locator` (`Locator.java:278`); use `.element()` (`:493`). javac: `incompatible types: Locator cannot be converted to WebElement` (12 errors).

#### DOC-016 — Protected `BasePage` helpers shown in contexts where they are unavailable (P2, DOĞRULANDI for 2 sites, GÜÇLÜ ŞÜPHE for others, S)
- `recipes/alerts.md:17-28`: `DeleteTest extends BaseTest` calls `acceptAlert()`; that helper is `protected` in `BasePage` (`BasePage.java:231`) only. Same trap for `dismissAlert/getAlertText/getAndAcceptAlert/typeInAlert` fragments (`alerts.md:41-67`), `scrollToBottom`, `withinFrame*`, `shadow*`, `click(By)`, `type(By,..)` (`base-page.md:47-76`, `console-errors.md:34-58`, `download-manager.md:30,45`, `handle-iframes.md:31-35`): fine inside a `BasePage` subclass, but samples are bare statements and `base-page.md:10` says "so you never write raw Selenium calls in your tests" although these helpers cannot be called from tests. (Fragments excluded from the "confirmed" count; only `alerts.md:28` is a complete class that fails.)
- `junit5.md:306-312`: `AppConditions extends BaseConditions` calls `find("#username")`; `BaseConditions implements NavigationSupport` only and offers protected `click(By)/type(By,String)` (`precondition/BaseConditions.java:42-53`). DOĞRULANDI (javac: `cannot find symbol method find(String)`).
- Fix: show the owning class explicitly; expose alert/frame/shadow helpers also through `ActionSupport`/`BrowserSupport` if the intent is test-class usage (API decision).

#### DOC-017 — Misc nonexistent members (P2, DOĞRULANDI, S)
| Site | Wrong | Real |
|---|---|---|
| `README.md:340,636` | `assertThatPage().hasUrlContains("/dashboard")` | `PageAssert.urlContains(String)` (`PageAssert.java:165`) |
| `migration/from-selenide.md:96` | `assertThat(find("#status"), 5)` | no `(Locator,int)` overload; use `.within(5)` pattern from `PageAssert`/`LocatorAssert` (verify per class) |
| `ai/agentic-testing.md:118-119` | `softAssertThatPage()` | `softAssertPage()` (`SoftAssertSupport.java:47`) |
| `migration/from-restassured.md:100` | `.assertMatchesSchema(...)` | `ApiResponse.assertSchema(String)` (`ApiResponse.java:261`) |
| `junit5.md:102`, `cucumber.md:168` | `$$(css)` | does not exist |
| `migration/from-restassured.md` etc. | page itself is EN-only (DOC-021) | |

### Group D: Locator/page-object guidance (DX)

#### DOC-018 — Deprecated `$()` still used/mentioned in new code and Javadoc (P2, DOĞRULANDI for existence, İYİLEŞTİRME for scope, S)
- `LocatorSupport.java:74-100`: `$()` is `@Deprecated(since="1.1.0", forRemoval=true)`, "scheduled for removal in 2.0.0".
- Docs: `guides/video-recording.md:110-124` (sample code), `guides/assertions.md:81` (`assertThat($(".card"))`), prose in `cloud-execution.md:20`, `guides/semantic-locators.md:24`, `junit5.md:102,436`, `cucumber.md:168`, `ai/adr-001-mcp-recorder-architecture.md:49`.
- Javadoc `<pre>` examples: `Locator.java:228,242,255,268`, `SessionSupport.java:35,40`, `BaseCucumberTest.java:37`, `LocatorAssert.java:38`, `SeleniumAssert.java:27`, `MultiSessionManager.java:22` - 11 places teaching `$()`; `LocatorSupport.java:11` class doc lists it as primary.
- Not an error to *document the deprecation*; the finding is that new-code examples use it. Using `-Xlint:removal` on the whole sample set produced 18 removal warnings (video-recording 12, from-selenide 4, assertions 2).
- Fix: replace with `find()` in samples/Javadoc; keep `$()` only in the Selenide migration "before" column.

#### DOC-019 — Page objects teach `By` constants + manual `WebDriver` injection as the default (P2, İYİLEŞTİRME, M)
- Evidence: `guides/base-page.md:15-37` (`LoginPage(WebDriver)`, `By` constants, `super(driver)`), README:285-337 (declares `private final By usernameInput = getByPlaceholder(...)` - works only because `Locator extends By` (`Locator.java:38`) - plus `new LoginPage(getDriver())`), `intro.md:30-31`, `browser-lifecycle.md:89,97`, `parallel.md:124,137`, `external-test-data.md:34`, `precondition.md:22`, 4 more `super(driver)` in `ai/agentic-testing.md`, `ai/recorder.md`. `BasePage.java` class Javadoc itself shows the `By`-based pattern. By-based vs semantic in Java blocks (EN): 157 `By.*` vs 134 `find/getBy*` occurrences.
- Why it matters: the framework's differentiator (framework-managed per-thread driver, semantic `Locator`) is not the pattern users copy; passing `getDriver()` manually reintroduces the lifecycle/thread-safety footguns the framework removes. The scratchpad's own recommended UI standard (`getByRole/getByLabel/getByTestId` -> `find(String)`, `By` only for WaitEngine/frame/shadow/upload/interop) is not applied.
- Fix: rewrite `base-page.md` around `BasePage()` + `Locator` fields; show `By` helpers in a clearly labelled "interop" section; update README Step 4, `intro.md`, `parallel.md`, etc.
- Dependencies: DOC-016 (decide whether helper methods get `Locator` overloads). Regression: none (docs only).

#### DOC-020 — Landing page snippets use missing API (P2, DOĞRULANDI, S)
- `docs-site/src/data/homeData.js:68` `.filter(hasText(itemName))` - `Locator.filter(String css)` only (`Locator.java:231`), no `hasText` factory; `:432,439` `Role.STATUS` - not in `Role` enum (BUTTON ... SEPARATOR, no STATUS).
- Landing page is the highest-traffic code sample surface. Fix: `getByRole(Role.BUTTON, "Add to cart").withText(itemName)`; use `Role.ALERT` or add `STATUS` to the enum (enum addition is backward compatible).

### Group E: EN/TR parity and links

#### DOC-021 — Four EN pages have no TR translation (P2, DOĞRULANDI, S/M)
`ci/bitbucket-pipelines.md`, `loadtest/distributed-docker-k8s.md`, `migration/from-restassured.md`, `reporting/allure.md` (EN-only; TR locale serves EN fallback). TR-only pages: none. All 4 are in `sidebars.js` (`docs not in sidebar: []`, `sidebar ids without doc: []`).
- `reporting/allure.md` has a config error too (DOC-024) and `from-restassured.md` has API errors (DOC-017).
- Fix: translate after the EN content is corrected, to avoid translating bugs.

#### DOC-022 — Existing EN/TR pages diverge in API and facts (P2, DOĞRULANDI, M)
Java-token diff per page (method-call/import/`new` multiset, comments and strings stripped):
| Page | Divergence |
|---|---|
| `guides/wait-engine.md` | EN `WaitEngine.*`, TR `getWait().*` (wrong, DOC-013); EN has manual `WebDriverWait` section absent in TR; 2 fewer code fences in TR |
| `guides/base-page.md` | TR `getWait()` instead of `WaitEngine` |
| `guides/assertions.md` | TR omits the `assertThat/assertThatPage/assertWithAi/satisfiesAi/violatesAi` blocks (2 fewer fences) |
| `recipes/oauth-sso.md` | TR imports `io.testfly.api.ApiResponse` |
| `ci/github-actions.md:59`, `ci/jenkins.md:70,78` | TR uses `browser.type: chrome` (unknown key -> `browser.name must be specified`, reproduced) vs EN `name:` |
| `changelog.md` | TR pre-1.0.0 history uses a different version scheme (3.3.0 ... 0.5.0, 653 lines) vs EN/`CHANGELOG.md` (0.24.0 ... 0.1.0, 444 lines); headings differ from line 6 |
| `configuration.md`, `guides/screenshots.md`, `changelog.md` | fence-count mismatch (EN 28/TR 26, EN 6/TR 10, EN 6/TR 12) |
Other 80 shared pages: identical API tokens apart from translated test-method names.
- Fix: choose EN as source; regenerate TR code blocks from EN and translate only prose; add a parity script (DOC-027).

#### DOC-023 — Broken/invalid links (P2, DOĞRULANDI, S)
- `file:///` (EN+TR): `guides/assertions.md:27` (`file:///src/main/java/io/testfly/steps/StepLogger.java`), `:28` (`.../assertion/SeleniumAssert.java#L35`, `#L43`; the `#L43` link even targets `Locator`, not that class). Never resolvable on a published site.
- README: `docs/features/README.md` (`:10,819,843`) and RFC files `docs/features/01..05-*.md` (`:813-817`) do not exist (`ls docs/features` -> not found); deleted in commit `7199ae9` ("re-clean resurrected obsolete files"). README also advertises these RFCs as `v1.2.0/v1.3.0` while the version is 1.0.7.
- `AGENTS.md:377-378` references `docs/ci-execution.md`, `docs/configuration.md` (not in `docs/`). `AGENTS.md:277` `github.com/testfly/testfly-test` vs README `github.com/hakanngul/testfly-test`.
- Docusaurus internal links: custom script found 0 broken `.md`/`/docs/...` links in EN or TR (build is configured `onBrokenLinks: 'throw'`; `onBrokenMarkdownLinks: 'warn'` only warns).
- Fix: use `https://github.com/hakanngul/testfly/blob/main/<path>` links; remove or restore the RFC links.

### Group F: Configuration docs vs code

#### DOC-024 — YAML keys documented that the loader ignores (P2, DOĞRULANDI, S)
Real run of `ConfigurationLoader` (stderr "Unknown config key ... ignored"):
- `configuration.md:33`, `guides/testfly-yml-guide.md:185-194`: top-level `browserstack:` block with credentials. Real location is `execution.browserstack` (`TestFlyConfig.java:267`; correctly shown in `cloud-execution.md:41-49`). A user following `configuration.md` gets credentials silently ignored.
- `ci/jenkins.md:78` `browser.binaryPath` (no such field; TR also `browser.type`).
- `reporting/allure.md:21` `reporting.allure.resultsDir` (`Allure` has only `enabled`, `TestFlyConfig.java:893-903`); "default" claim unverified.
- `cloud-execution.md:13` caution says cloud modes need 1.0.0 and "config loading rejects any mode other than local or remote" - stale: `ConfigurationLoader.java:309-313` accepts `local/remote/browserstack/saucelabs`.
- Unknown keys are only a warning (`LenientPropertyUtils`), which hides mistakes; consider failing on unknown keys in strict/CI mode (product decision, İYİLEŞTİRME).

#### DOC-025 — Stale factual statements (P3, DOĞRULANDI, S)
- `ci/jenkins.md:21` `jdk 'JDK17'` while the baseline is Java 21 (`pom.xml:322`, docs prerequisites).
- `loadtest/engines.md:18` "Gatling 3.10.x"; classpath resolves Gatling 3.13.5 (`classpath.txt`).
- `AGENTS.md:45,60` "Selenium Java 4.40.0", "Current version 1.1.0".
- `README.md:813-817` roadmap versions ahead of release (see DOC-023).

### Group G: Process / maintainability

#### DOC-026 — `@TestFlyApi(since=...)` values do not match the real version line (P3, DOĞRULANDI, M)
Values 1.9.0, 1.10.0, 1.12.0, 1.13.0, 2.x, 3.0.0 appear in 329 annotations/Javadocs (e.g. `LocatorSupport.java` since 1.10.0, `TestRailCase.java:26` since 3.0.0) while project/CHANGELOG are 1.0.7; TR changelog still uses the legacy numbering (DOC-022). Root cause: version reset to 0.x/1.0.0 on 2026-08-20 without rewriting `since`. Impact: the stability contract in `AGENTS.md` ("Do not change `@TestFlyApi` elements within the same major version") cannot be reasoned about. Fix: map legacy `since` values to the new scheme in one scripted pass (needs maintainer mapping table).

#### DOC-027 — No automated guard for docs correctness (P2, İYİLEŞTİRME, M/L)
- `.github/workflows/` has `release.yml` and `testfly-ci.yml` only; no job builds `docs-site`, checks links, EN/TR parity, YAML validity, or compiles snippets (`grep docs-site .github/workflows` is empty). `wrangler.jsonc` serves `./docs-site/build`; the deployment trigger described in `AGENTS.md` is not present in the repo (cannot verify; likely Cloudflare Workers Builds - GÜÇLÜ ŞÜPHE).
- Prototype already exists in this scratch dir and found 30+ distinct framework-level defects in ~1 hour: `extract.py` + `gen.py` + javac, `Y.java` (real `ConfigurationLoader` on YAML blocks), `imports.py`, parity script.
- Proposal: (1) mark compilable blocks with a meta tag (e.g. ```` ```java compile ````) and compile them in CI against `target/classes`; (2) YAML blocks tagged `testfly-yml` validated through `ConfigurationLoader`; (3) EN/TR file-list and fence-count parity check; (4) version-consistency check (all `<version>` for the artifact == `pom.xml`); (5) `onBrokenMarkdownLinks: 'throw'`.
- Dependencies: DOC-001..017 first, otherwise the gate is red on day one. Regression: CI time +1-2 min.

#### DOC-029 — Snippets are not self-contained (P3, İYİLEŞTİRME, M)
Many samples reference undefined `LoginPage`, `User`, `res`, `driver`, `DashboardPage` (about 60 javac `cannot find symbol` of kind variable/class in README, `intro.md`, `step-logging.md`, `recipes/*`). Acceptable for fragments, but it blocks automated checking (DOC-027) and copy-paste. Suggest a small "companion samples" module under `src/test/` or `examples/` that compiles the canonical page objects referenced by docs.

---

## 3. Public-API vs docs quick map (verified signatures)

| Concept | Real API (source) | Common wrong form in docs |
|---|---|---|
| Page object base | `io.testfly.test.BasePage` (`protected BasePage()`, `protected BasePage(WebDriver)`) | `io.testfly.core.BasePage` |
| Roles | `io.testfly.locator.Role` enum (no `STATUS`) | `io.testfly.locators.Role`, `Role.STATUS` |
| Semantic locators | `getByRole(Role)`, `getByRole(Role,String)`, `getByText/Label/Placeholder/TestId/AltText/Title`, `Locator.withName/withLevel/exact/nth/first/last/within/filter(css)/withText` | `RoleOptions`, `getByRole("heading","x")`, `filter(hasText(..))` |
| Locator actions | `click, type, append, clear, getText, getAttribute, inputValue, isVisible/Hidden/Enabled, hover, scrollIntoView, jsClick, count, element, elements, toBy` | `fill`, `val`, `setValue` |
| Waits | static `WaitEngine.waitFor*`; `getWait()` -> `WebDriverWait`; `waitForUrlContains/Matches/Title/PageLoad` default methods on `NavigationSupport` | `getWait().waitForX`, `getWait().wait(cond)` |
| Load test | `BaseLoadTest` + `LoadTestSupport.load(String)/loadScenario(String)`; `@LoadTest(users, rampUp, hold, cooldown, engine, baseUrl)`; `LoadTestFeeder.csv/json/random/uuid/sequence/constant` | `BaseTest`, `rampUpSeconds`, `durationSeconds`, `targetRps`, `@LoadEngine`, `fromCsv` |
| API calls | `apiPost(path)` -> `ApiClient` -> `.body().send()` -> `ApiResponse.assertStatus/json/assertSchema` | `apiPost(path, body)`, `.assertThat().statusCode()` |
| Context | `ctx().put/get(key[,Class])` | `ScenarioContext.put(...)` static |
| Page assertion | `assertThatPage().hasTitle/hasUrl/urlContains/urlMatches/titleContains/satisfiesAi/violatesAi`, `softAssertPage()` | `hasUrlContains`, `softAssertThatPage` |

## 4. Triage notes: reported by the harness but NOT defects

- `quarantine.md`/`junit5.md:421`/`cucumber.md:337` YAML `ConstructorException ... quarantine`: those blocks are `testfly-quarantine.yml` (a list-valued file), not `testfly.yml`.
- `plugins.md:84,95`, `README.md:771` "method does not override": snippets are member fragments wrapped in `BaseTest`; the real `TestFlyPlugin` interface has `getName/minFrameworkVersion/onLoad/onUnload` defaults (`extension/TestFlyPlugin.java`).
- `handle-shadow-dom.md:34,41`: `shadowPierce(String...)`, `shadowExists(By,String)` exist in `BasePage`; errors only because the wrapper class was `BaseTest`.
- `custom-drivers.md` Appium/`ChromeOptions` import errors: Appium client is not on the project classpath by design (user-side dependency).
- `from-restassured.md` `given()`, `from-selenide.md` `$().shouldHave`, `from-serenity.md`, `from-webdrivermanager.md`, `from-selenium-testng.md` TestNG listener samples: third-party "before" code.
- `accessibility.md:63-65`, `clock-mocking.md:67-70`, `api-requests.md:10-14`: illustrative lists, not statements.
- `README.md:548` `.body(...)` ellipsis placeholder.

## 5. Summary table

| ID | Title | Pri | Class. | Effort |
|---|---|---|---|---|
| DOC-001 | Nonexistent `io.testfly` coordinate in install docs | P1 | DOĞRULANDI | S/M |
| DOC-002 | Version chaos; 1.0.5-1.0.7 not on Central | P1 | DOĞRULANDI | S |
| DOC-003 | getting-started `testfly.yml` missing `execution.mode` fails | P1 | DOĞRULANDI | S |
| DOC-005 | Load samples extend `BaseTest` (no `load()`) | P1 | DOĞRULANDI | S |
| DOC-006 | `@LoadTest`/`@LoadEngine` fictional | P1 | DOĞRULANDI | S |
| DOC-007 | Fluent load DSL fictional | P1 | DOĞRULANDI | M |
| DOC-010 | prompt-recipes / video-recording uncompilable | P1 | DOĞRULANDI | S |
| DOC-013 | `getWait().waitFor*` / `.wait(cond)` | P1 | DOĞRULANDI | S |
| DOC-014 | cucumber/junit5 API samples wrong | P1 | DOĞRULANDI | S |
| DOC-008 | Feeder API wrong | P2 | DOĞRULANDI | S |
| DOC-009 | Distributed load page: inert env vars, false enforcer claims | P2 | DOĞRULANDI / GÜÇLÜ ŞÜPHE | S |
| DOC-011 | Internal/nonexistent packages and getters in plugin docs | P2 | DOĞRULANDI | S |
| DOC-012 | `RoleOptions` in MCP codegen sample | P2 | DOĞRULANDI | S |
| DOC-015 | `Locator.first()` as `WebElement` | P2 | DOĞRULANDI | S |
| DOC-016 | Protected BasePage helpers used from tests/conditions | P2 | DOĞRULANDI (2) / GÜÇLÜ ŞÜPHE | S |
| DOC-017 | Misc nonexistent members | P2 | DOĞRULANDI | S |
| DOC-018 | Deprecated `$()` in new code and Javadoc | P2 | DOĞRULANDI | S |
| DOC-019 | By + manual WebDriver as default POM pattern | P2 | İYİLEŞTİRME | M |
| DOC-020 | Landing page `Role.STATUS`, `hasText` | P2 | DOĞRULANDI | S |
| DOC-021 | 4 pages missing in TR | P2 | DOĞRULANDI | S/M |
| DOC-022 | EN/TR content divergence | P2 | DOĞRULANDI | M |
| DOC-023 | `file:///`, README `docs/features/*`, AGENTS dead refs | P2 | DOĞRULANDI | S |
| DOC-024 | Unknown/misplaced YAML keys in docs | P2 | DOĞRULANDI | S |
| DOC-027 | No CI guard for docs/snippets | P2 | İYİLEŞTİRME | M/L |
| DOC-025 | Stale facts (JDK17, Gatling 3.10, Selenium 4.40) | P3 | DOĞRULANDI | S |
| DOC-026 | `@TestFlyApi(since)` out of sync with versions | P3 | DOĞRULANDI | M |
| DOC-028 | Getting-started build wiring gaps | P3 | GÜÇLÜ ŞÜPHE | S |
| DOC-029 | Snippets not self-contained | P3 | İYİLEŞTİRME | M |

(DOC-004 intentionally unused: merged into DOC-003.)

## 6. Suggested remediation order (docs scope)

1. Release decision and coordinates (DOC-001, DOC-002), then DOC-003 (single canonical quick-start).
2. Rewrite load-test section from `BaseLoadTest` Javadoc (DOC-005..008), drop or implement `targetRps` (owner decision), trim DOC-009.
3. Mechanical fixes with compile check: DOC-010, 011, 012, 013, 014, 015, 017, 020, 024.
4. Pattern change: Locator-first POM, `BasePage()`, remove `$()` from samples and Javadoc (DOC-016, 018, 019).
5. Parity and links: DOC-021..023, then translate.
6. Add the CI guard (DOC-027) and fix `@TestFlyApi(since)` mapping (DOC-026).
