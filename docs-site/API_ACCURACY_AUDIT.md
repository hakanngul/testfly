# TestFly Documentation API Accuracy Audit

Audit date: 2026-10-09

Scope: current `docs-site/` content compared with the current production source tree.

Result: 24 confirmed documentation defects; no Java or product-documentation corrections were made in this phase.

## Executive summary

| Severity | Count |
|---|---:|
| Critical | 0 |
| High | 13 |
| Medium | 6 |
| Low | 5 |
| **Total** | **24** |

| Domain | Count |
|---|---:|
| Web UI | 8 |
| API Testing | 3 |
| Load/Performance Testing | 5 |
| Core and Configuration | 5 |
| Reporting and Integrations | 1 |
| Cross-language inconsistencies | 2 |

| Issue category | Count |
|---|---:|
| Invalid/nonexistent API or configuration syntax | 5 |
| Unsupported or overstated runtime behavior | 11 |
| Incorrect value, default, path, or count | 4 |
| Incomplete reference coverage | 3 |
| Validation-model mismatch | 1 |

Highest-priority corrections are the two invalid homepage Java examples, the unsupported `$$(css)` and locator-filter examples, the false zero-configuration claim, the load-test execution switch and K6 claims, and the ineffective reporting switches.

## Known defect assessment

The supplied chain is invalid.

```java
getByRole(Role.BUTTON, "Add to cart")
    .filter(hasText(itemName))
    .click();
```

- There is no locator predicate/factory named `hasText(String)`. `hasText(String)` is an assertion method on `LocatorAssert` (`src/main/java/io/testfly/assertion/LocatorAssert.java:208`), not a value accepted by `Locator.filter`.
- `Locator.filter` accepts one CSS `String` (`src/main/java/io/testfly/locator/Locator.java:224-235`). `withText(String)` is the locator text filter (`Locator.java:237-249`).
- Therefore `filter(hasText(itemName))` is not valid Java against the current SDK, regardless of imports.
- `getByRole(Role, String)` returns a role locator with an accessible-name chain filter (`src/main/java/io/testfly/test/support/LocatorSupport.java:117-120`). Self-healing is restricted to unfiltered `CSS_OR_BY` locators (`Locator.java:569-585`), so this role/name chain cannot receive the claimed Level-1 or Level-2 healing.
- A valid generic chain is `find("button").withText("Add to cart").click()`. To select the button for a particular item, use a verified stable selector or scope the button with `.within(By ...)`; TestFly has no Playwright-style `hasText` predicate. Do not claim self-healing for a chained or semantic locator. A plain unfiltered `find(By...)`/`find("...")` locator is the supported healing scope.

## 1. Web UI

| ID | Severity | Documentation evidence | Implementation evidence | Defect, root cause, and correction | Status |
|---|---|---|---|---|---|
| WEB-01 | High | `docs-site/src/data/homeData.js:63-69` | `Locator.java:231`, `LocatorAssert.java:208`, `Locator.java:569-585` | Homepage uses nonexistent `filter(hasText(...))` and promises healing for an ineligible semantic/chained locator. Playwright predicate syntax was transferred to TestFly. Replace with `withText(String)`, a stable selector, or `within(By)` as appropriate; remove the healing claim. | Source-signature verified; current chain cannot compile. |
| WEB-02 | Medium | `docs-site/docs/intro.md:105`; Turkish counterpart `.../current/intro.md:105` | `Locator.java:231`, `245`, `271` | The advertised `filter().nth().withText()` calls omit required `String`, `int`, and `String` arguments. Replace with a real example such as `find(".row").filter(".active").nth(0).withText("Save")`. | Source-signature verified. |
| WEB-03 | High | `docs-site/docs/junit5.md:102`, `cucumber.md:172`; Turkish counterparts at the same lines | `LocatorSupport.java:31-45`, `68-98` | Capability tables advertise `$$(css)`, but no such method exists. The only `$` overloads are deprecated single-locator aliases. Use `find(css).elements()` where a list is required, and present `find` as the recommended API. | Repository-wide symbol search verified. |
| WEB-04 | High | `docs-site/docs/guides/self-healing.md:45-47`; Turkish counterpart `.../guides/self-healing.md:32` | `Locator.java:569-585` | Guide says semantic locators heal automatically. Runtime healing only accepts unfiltered `CSS_OR_BY`; role, text, label, placeholder, and chained locators are excluded. Document the exact eligibility boundary and fallback behavior. | Source-behavior verified. |
| WEB-05 | Low | `docs-site/docs/guides/semantic-locators.md:57`; Turkish line 52; EN changelog line 281; TR changelog line 224 | `src/main/java/io/testfly/locator/Role.java:27-104` | Documentation says 38 roles; the enum contains 36 constants. Count drift was copied into both locales and changelogs. Change the count to 36 or generate it from the enum. | Enum counted from production source. |
| WEB-06 | High | `docs-site/src/data/homeData.js:276-280` | `AccessibilitySupport.java:17-20`; `AccessibilityAssert.java:134-160` | Homepage claims automatic WCAG audits on every navigation. The implementation only exposes an explicit `accessibility()` builder followed by `run()`/`collect()`; lifecycle code does not invoke it on navigation. Describe explicit scans, or implement automation in a later SDK phase. | Source-flow and lifecycle-reference search verified. |
| WEB-07 | Medium | `docs-site/src/data/homeData.js:283-287` | `src/main/java/io/testfly/performance/PerformanceMetrics.java:60-75`, `95-139` | Homepage claims FID capture, but the metric model/collector has LCP, FCP, FP, TTFB, CLS, DOM load, page load, and transition duration—no FID. List only collected metrics. | Source-model and collector search verified. |
| WEB-08 | High | `docs-site/src/data/homeData.js:290-294` | `TestData.java:32-51`; `TestDataSupport.java:17-25`; `TestFlyExtension.java:445-455` | Homepage claims `@TestData` maps files directly to strongly typed method/DataProvider parameters. The annotation loads one selected row into `TestDataStore`; tests read a map or typed key. JUnit parameter resolution supports only `WebDriver`. Document `getTestData()`/`getTestData(key, type)`; describe a manually authored TestNG `DataProvider` separately. | Source-flow verified. |

## 2. API Testing

| ID | Severity | Documentation evidence | Implementation evidence | Defect, root cause, and correction | Status |
|---|---|---|---|---|---|
| API-01 | High | `docs-site/src/data/homeData.js:85-100` | `BaseApiTest.java:40-43`; `ApiSupport.java:15-52`; `BrowserSupport.java:24-56`; `Route.java:92-101`; `ApiAuth.java:43-45`; `ApiResponse.java:220-288`, `440-449` | Homepage API example combines nonexistent `network().route`, `Route.fulfill(int,String)`, `api()`, `bearer`, `ApiResponse.assertThat/status/jsonPath(...).exists/durationLessThan` calls. `BaseApiTest` also has no browser/CDP support. Use `apiClient()`, `ApiAuth.bearerToken`, `assertStatus`, `assertJsonExists`, and `assertDurationLessThan`. Use `mockServer()`/API mock rules for API-client mocking; use `BaseTest` plus `mockRoute(..., Response...)` only for browser traffic. | Source-signature verified; example cannot compile or behave as claimed. |
| API-02 | Low | `docs-site/docs/guides/api-auth.md:2,8`; Turkish counterpart line 8 | `ApiAuth.java:43-135` | Guide says there are three built-in strategies, omitting API-key header/query, digest placeholder, HMAC, and OAuth2 password factories. Either call these “three common strategies” or document the full surface and digest limitation. | Public-factory inventory verified. |
| API-03 | Low | API configuration table `docs-site/docs/configuration.md:666-690` and Turkish equivalent omit service routing | `TestFlyConfig.java:1814-1819`, `1864-1875`; `ApiClient.java:236-248` | `api.baseUrls` and `ApiClient.toService` are supported but absent from the configuration reference. Add the map shape, fallback to `api.baseUrl`, and missing-service failure behavior in both locales. | Config/public API verified; docs search found no coverage. |

## 3. Load/Performance Testing

| ID | Severity | Documentation evidence | Implementation evidence | Defect, root cause, and correction | Status |
|---|---|---|---|---|---|
| LOAD-01 | High | `docs-site/docs/loadtest/configuration.md:39`; main reference `configuration.md:872`; Turkish load-test line 39 | `TestFlyConfig.java:2339-2358`; `LoadTestRunner.java:51-67` | `loadtest.enabled`/`features.loadtest` are documented as execution gates, but `LoadTestRunner.run` never checks the effective flag. The property currently exposes a getter only. State that it is not enforced, or implement enforcement in a later SDK change. | Call-path search verified. |
| LOAD-02 | Medium | `docs-site/docs/loadtest/configuration.md:40`; main reference line 873; Turkish load-test line 40 | `LoadTestConfig.java:58-78`, `101-126`; `JdkLoadEngine.java:80-92`; `GatlingEngine.java:49-56` | Docs promise fallback to `execution.baseUrl`. Only Gatling applies it; the JDK engine rejects a missing load/scenario/annotation URL. Document the engine difference and require explicit load URL until behavior is unified. | Both engine paths verified. |
| LOAD-03 | Medium | `docs-site/docs/loadtest/reporting.md:16,28,62,66`; Turkish counterpart lines 16, 28, 62, 66 | `ReportPaths.java:43-50`, `68-75`; `GatlingEngine.java:61-64`; `TestFlyConfig.java:2348` | Report paths incorrectly add `target/reports/`. Current outputs are `target/testfly-report.html`, `target/loadtest-report.html`, and Gatling data under `target/loadtest/<run-id>` by default. Correct all paths and examples. | Path construction verified. |
| LOAD-04 | Medium | `docs-site/docs/configuration.md:770`; Turkish line 751 (and template line 246) | `TestExecutionListener.java:184-202`, `493-503`; `TestFlyExtension.java:298-307`, `599-608` | `captureOnEveryTest` is described as capture on every `open()`. It is captured once after a successful test. Rename the explanation to “after each passing browser test”; do not imply navigation hooks. | TestNG/JUnit lifecycle verified. |
| LOAD-05 | High | `docs-site/static/diagrams/testfly-k6-dataflow.json:5,7,43-44`; `testfly-architecture.json:44` | `LoadTestRunner.java:98-116` | Diagrams claim a K6 engine and K6-specific data flow. Runtime supports only `auto`, `gatling`, and `jdk`; no K6 adapter exists. Rename the diagram and remove K6, or add a real engine in a separate SDK phase. | Engine registry and source-tree search verified. |

## 4. Core and Configuration

| ID | Severity | Documentation evidence | Implementation evidence | Defect, root cause, and correction | Status |
|---|---|---|---|---|---|
| CORE-01 | High | `docs-site/docs/intro.md:22,45,58`; Turkish counterpart lines 45, 58, 70 | `ConfigurationLoader.java:38-68`; `FrameworkBootstrap.java:41-50`; `TestFlyDefaults.java:67-90` | Docs say `testfly.yml` is optional and `TestFlyDefaults` covers an absent file. Loader throws if no file exists, validates before `applyMissing`, and `TestFlyDefaults` contains only user-registered overrides. Require a configuration file in docs or change bootstrap behavior later. | Startup path verified. |
| CORE-02 | High | `docs-site/docs/configuration.md:494-556`; Turkish lines 477, 496, 536-537 | `TestFlyConfig.java:111-113`, `215-220`, `544-546`; `ConfigurationLoader.java:292-323` | Browser `chrome`, execution `local`, and timeout `10/30` are labeled defaults. Fields initialize to null/zero and validation requires them before defaults are applied. Mark them required values, not runtime defaults. | Object initialization and validation verified. |
| CORE-03 | Medium | `docs-site/docs/configuration.md:94-100,184,199,210,243,256,266-269`; Turkish template line 81 and corresponding values | `TestFlyConfig.java:515`, `588`, `927`, `1124`, `1280`, `1592-1595` | Master template says shown values are framework defaults, but several are recommendations: retry attempts 2 vs 1, AI provider Gemini vs Claude, CI metadata true vs auto/null, recording FPS 5 vs 2, visual tolerance 0.01 vs 0, and nonzero performance thresholds vs 0. Relabel as recommended example values or use exact defaults. | Field initializers compared. |
| CORE-04 | High | `docs-site/docs/configuration.md:886-893`; Turkish line 869 onward | `ConfigurationLoader.java:99-140`, `292-323`; `FrameworkBootstrap.java:45-49` | Docs promise strict schema validation, rejection of unknown keys, aggregated errors, URL validation, and early cloud-credential validation. Unknown keys are warned and ignored; loader validation is a short fail-fast set and does not implement the illustrated aggregate. Document the actual staged validation and remove the fabricated diagnostic. | Loader/validator paths verified. |
| CORE-05 | Low | EN visual reference `docs-site/docs/configuration.md:778-788`; Turkish lines 760-768; API reference lines 666-690 | `TestFlyConfig.java:1277-1321`, `1814-1819`, `1864-1875` | The purported full configuration reference omits supported `visual.failOnNewBaseline` and `api.baseUrls`. Add these properties to both locale tables; avoid duplicating implementation prose elsewhere. | Config-property inventory verified. |

## 5. Reporting and Integrations

| ID | Severity | Documentation evidence | Implementation evidence | Defect, root cause, and correction | Status |
|---|---|---|---|---|---|
| REPORT-01 | High | `docs-site/docs/reporting/html-report.md:131-140`; Turkish counterpart lines 131-140 | `TestFlyConfig.java:713-735`; no production reads outside these getters; failure capture at `TestExecutionListener.java:163-172` and `TestFlyExtension.java:223-224` | Docs say `reporting.htmlReport` and `reporting.screenshotOnFailure` control generation/capture. Both fields deserialize but are not consumed; reports and failure screenshots are generated independently. State that the switches are currently ineffective, or wire them in a later SDK change. | Getter-usage and lifecycle search verified. |

## 6. Cross-language inconsistencies

| ID | Severity | Documentation evidence | Implementation evidence | Defect, root cause, and correction | Status |
|---|---|---|---|---|---|
| I18N-01 | High | Turkish `docs-site/i18n/tr/docusaurus-plugin-content-docs/current/guides/screenshots.md:47-59`; English counterpart `docs-site/docs/guides/screenshots.md:47-51` | No `screenshots` block in `TestFlyConfig`; unknown keys ignored by `ConfigurationLoader.java:111-140`; unconditional capture paths cited in REPORT-01 | Turkish guide invents `screenshots.onFailure`; English correctly says failure capture cannot be disabled through configuration. Remove the Turkish YAML and synchronize the actual behavior. | Config-schema and locale-diff verified. |
| I18N-02 | Low | Turkish changelog `.../current/changelog.md:473`; no equivalent current-behavior list in EN | `Locator.java:440-461`; `resolveAll` begins at line 588 | Turkish text calls `isVisible()`, `count()`, and `elements()` auto-wait terminal actions. `isVisible()` explicitly does not wait; collection queries resolve immediately. Correct the historical entry or label the exact actions that wait. | Source behavior and locale-diff verified. |

## Compilation and validation results

| Measure | Result |
|---|---:|
| Markdown/MDX files examined | 202 (101 English, 101 Turkish) |
| All fenced snippets inventoried | 1,134 |
| Java fenced snippets | 558 (280 English, 278 Turkish) |
| Additional logical homepage Java panels | 9 |
| Java SDK examples indexed/static-screened | 567 |
| English self-contained compilation candidates | 55 |
| Candidates compiling standalone | 48 |
| Candidates compiling with their documented companion source | 1 additional (49 total) |
| Candidates requiring manual/contextual verification | 6 |
| Distinct confirmed invalid SDK example patterns | 4, affecting 8 documentation locations |

Compilation used production classes plus Maven test-scope dependencies, without invented imports, methods, or stubs. `mvn -q -DskipTests test-compile` passed. The 55 candidates had imports, a type declaration, balanced delimiters, and no ellipsis. The six non-standalone cases are intentional/contextual fragments or require a preceding page object, project fixture, placeholder service, Slack client, or optional Appium dependency. The two adjacent prompt-recipe sources compile together.

The four invalid example patterns are WEB-01, WEB-02, WEB-03, and API-01. Homepage component snippets are outside Markdown fences and were signature-checked manually. Turkish Java was token/symbol-compared with English but not redundantly compiled where semantically identical.

The complete master YAML block in `configuration.md` parsed successfully through `ConfigurationLoader`; successful parsing does not validate the truth of documented defaults or whether flags are consumed. Browser-, cloud-, IDE/MCP-, and external-service behavior was not executed in this audit and remains manual where source evidence was insufficient.

Final gates:

| Command/check | Result |
|---|---|
| `mvn -q -DskipTests test-compile` | PASS |
| Extracted `javac -proc:none` candidate compilation | PASS for 49 contextual/standalone candidates; 6 classified manual |
| Master YAML load through `ConfigurationLoader` | PASS |
| `scripts/agent/verify.sh docs` | PASS: 101/101 locale parity and optimized EN/TR builds |
| `git diff --no-index --check /dev/null docs-site/API_ACCURACY_AUDIT.md` | PASS: no diagnostics; exit 1 is expected for a new file |

## Coverage and exclusions

- Searched every in-scope EN/TR Markdown/MDX file, all non-generated `docs-site/src` JavaScript components, Docusaurus configuration/navigation files, and all five diagram JSON payloads.
- Compared locale path sets (101/101; exact parity), Java/import/API tokens, YAML keys, configuration tables, and code-fence distributions. Natural-language-only translation variation was ignored.
- Compared claims with production source first, then tests, Maven dependencies, and public declarations. A repeated documentation claim was never treated as implementation evidence.
- Excluded `docs-site/build`, caches, `node_modules`, lockfile internals, generated diagram HTML, binary images/fonts, and external dependencies. The separate `@testfly/mcp`, marketplace/IDE integrations, live cloud providers, and external SaaS behavior are unverifiable from this repository and were not reported as confirmed defects.
- Unsupported timing/absolute marketing guarantees such as “under 5 ms,” “under 50 ms,” “zero flakiness,” and “100% feature parity” lack reproducible benchmarks in this repository. They should receive a separate evidence/benchmark review, but are not counted above as confirmed behavioral defects.

## Documentation correction priority

1. Fix homepage examples and capability cards (WEB-01, WEB-06–08, API-01) because they are prominent copy/paste entry points.
2. Remove nonexistent APIs and narrow healing claims (WEB-02–04).
3. Correct bootstrap/configuration behavior and misleading switches (CORE-01–04, REPORT-01, I18N-01).
4. Correct load-engine support, enablement, fallback, and output paths (LOAD-01–05).
5. Complete low-risk counts and reference omissions (WEB-05, API-02–03, CORE-05, I18N-02).

## Recommended automated validation

1. Extract fenced Java and Java-bearing website component strings into generated temporary sources. Classify complete units separately from fragments; compile complete units against the built SDK and declared optional-dependency profiles.
2. Add a small consumer-fixture suite for homepage/getting-started examples. It should compile exact copied examples and forbid test-source-only packages.
3. Generate a public-symbol index from compiled `@TestFlyApi` classes and flag undocumented method calls, unsupported overloads, deprecated recommendations, enum-count drift, and invalid imports.
4. Reflect `TestFlyConfig` into a machine-readable key/default catalog. Test documentation YAML for known keys, types, defaults, validation stage, and whether each advertised control has a production read.
5. Normalize code/config tokens across EN/TR pairs and fail on semantic drift while allowing prose translation differences.
6. Keep `scripts/agent/verify.sh docs` as the final path-parity and dual Docusaurus build gate. Add the extraction/compiler/config checks before that build rather than creating a separate documentation hierarchy or memory system.
