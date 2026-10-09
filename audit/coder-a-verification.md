# Coder A verification (iteration 1) — worktree testfly-worktrees/audit-phase1, branch `development` (before and after), nothing committed

Skipped as ALREADY FIXED per recheck: none (T1.20, T1.6, T1.7 were STILL PRESENT / CHANGED).

## T1.20 JaCoCo (BLD-009 / TST-001)
- pom.xml: added empty default property `<argLine></argLine>`; surefire argLine is now `@{argLine} -Dnet.bytebuddy.experimental=true --add-opens java.base/java.lang=ALL-UNNAMED` (existing flags preserved). Thresholds unchanged (0.40 line / 0.30 branch).
- RED (original pom, `mvn test -Dtest=OpenApiYamlLoadingTest`): no `target/jacoco.exec`, log says "Skipping JaCoCo execution due to missing execution data file."
- GREEN: `mvn -B verify -Dgpg.skip=true` -> `target/jacoco.exec` (9.9 MB) + `target/site/jacoco/index.html`, "All coverage checks have been met", BUILD SUCCESS.
- Gate enforcement proven: temp `minimum 0.99` + `mvn jacoco:check@check` -> "Rule violated for bundle testfly: lines covered ratio is 0.59, but expected minimum is 0.99", BUILD FAILURE. pom restored byte-identical (cmp).
- Measured coverage (jacoco.csv, whole bundle): LINE 59.55% (8438/14169), BRANCH 47.77% (3403/7123), INSTRUCTION 60.22%.
  Weak packages (line): test 7%, testmanagement 10%, exceptions 12%, email 20%, testdata 21%, driver 26%, test.support 32%, wait 42%, recording 43%, browser 47%, healing 48%.
  Thresholds NOT raised (per instruction); current 40/30 are well below measured 59.6/47.8.

## T1.6 Jackson (BLD-007)
- OSV (coordinates only): jackson-databind 2.21.6 -> GHSA-cxp5-3px4-pw24 (quadratic forward-reference completion; patched 2.21.7); 2.21.7 -> no vulns (`{}`). Maven Central lists 2.21.7 (and 2.22.x, not used).
- pom.xml: property `jackson.version=2.21.7`; new `dependencyManagement` importing `com.fasterxml.jackson:jackson-bom:${jackson.version}`; jackson-databind explicit version removed (BOM-managed).
- `mvn dependency:tree -Dincludes=com.fasterxml.jackson*` after: databind/core 2.21.7, dataformat-yaml 2.21.7 (was 2.17.1), jsr310 2.21.7 (was 2.14.2), jdk8 2.21.7 (was 2.15.2), annotations 2.21 (BOM publishes 2.21 for annotations; same minor).
- No external scanners run; no other dependencies added.
- New test `src/test/java/io/testfly/unit/client/OpenApiYamlLoadingTest` (3 tests): YAMLMapper parses an OpenAPI doc; `ApiResponse.assertOpenApi(<yaml file>)` accepts a conforming response and rejects a non-conforming one. NOTE: this is a guard test, it also passes on the old (skewed) pom, so it is not red-before; the version/BOM change itself is verified by dependency:tree + OSV.

## T1.7 HtmlReportGenerator (SEC-001 / SEC-002 / API-024)
- `HtmlReportGenerator.java`: new package-private `escapeJsonForHtml` (`<`->`\u003c`, U+2028/U+2029 -> `\u2028`/`\u2029`; applied to TESTFLY_DATA_JSON and RUN_HISTORY_JSON) and `renderTemplate` (single pass regex `\{\{([A-Z_]+)\}\}` + `appendReplacement`/`quoteReplacement`; unknown placeholders untouched). `buildHtml` builds a value map instead of the chained `.replace()`. Standalone `testfly-report-data.json` is intentionally still raw JSON.
- New test `src/test/java/io/testfly/unit/HtmlReportGeneratorEscapingTest` (5 tests).
- RED (fix reverted via `git show HEAD:` copy): `Tests run: 5, Failures: 4` (injected `<img onerror>` reached the page; data block truncated -> JsonEOF; helpers missing). Fix restored (cmp identical); GREEN: 5/5.
- Report template reads the block via `JSON.parse(textContent)`-style (`getElementById('testfly-data').textContent`), `\u003c` decodes losslessly (asserted by decode test). Not opened in a real browser.

## Runs
- `mvn -B test -Dtest='HtmlReport*Test,OpenApi*Test'`: Tests run: 19, Failures 0, Errors 0 (Escaping 5, HtmlReportGeneratorTest 7, HtmlReportGeneratorLoadTestTest 3, OpenApiValidatorTest 1, OpenApiYamlLoadingTest 3).
- Full: `mvn -B verify -Dgpg.skip=true` (runs surefire full suite + jacoco report + check): Tests run: 1370, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS. Baseline was 1362 (+8 new tests).
- Pre-existing log noise unrelated: "[ERROR] failed to read resource listing".

## Files changed (uncommitted)
M pom.xml; M src/main/java/io/testfly/reporting/HtmlReportGenerator.java; ?? src/test/java/io/testfly/unit/HtmlReportGeneratorEscapingTest.java; ?? src/test/java/io/testfly/unit/client/OpenApiYamlLoadingTest.java

## Process note
One `pkill -f audit-phase1` was issued after an accidental short-timeout command; it may have killed any other maven process running from that worktree path.
