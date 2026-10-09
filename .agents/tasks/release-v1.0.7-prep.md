# TestFly v1.0.7 — Release Preparation Report

Branch: `development` (HEAD `e47e75a`, == `origin/development`). Nothing committed, pushed, tagged; `main` untouched.
`git log origin/development..HEAD` is empty; `git tag --points-at HEAD` is empty.

## Files changed (all UNCOMMITTED)

| File | Change |
|------|--------|
| `CHANGELOG.md` | `[Unreleased]` merged into `[1.0.7]`, rewritten to cover the whole `origin/main..development` diff, dated 2026-10-09 |
| `docs-site/docs/changelog.md` | `[1.0.7]` section replaced with the same text as `CHANGELOG.md` |
| `docs-site/i18n/tr/docusaurus-plugin-content-docs/current/changelog.md` | Turkish `[1.0.7]` section (same bullets, existing TR headings, 2026-10-09) |
| `README.md` | line 801 "Current release: v1.1.0 ..." -> "v1.0.7" with an accurate one-line summary |
| `AGENTS.md` | line 45 "Current version" `1.1.0` -> `1.0.7` |
| `.github/profile/README.md` | line 91 dependency snippet `1.0.2` -> `1.0.7` |
| `.agents/tasks/` (untracked) | `release-v1.0.7-plan.md` (pre-existing) and this report; not release content, do not commit |

Already `1.0.7`, confirmed only: `pom.xml:9`, `README.md:31,184`, `docs-site/src/data/homeData.js:667,771`, `docs-site/docs/loadtest/getting-started.md:25` and its TR mirror, `docusaurus.config.js:115-116`.

## Deliberately NOT bumped (follows the plan's decision 2)

The docs-site install snippets are pinned to Maven Central `1.0.4` on purpose (verified: Central only has 1.0.0-1.0.4) and each already says "this checkout is 1.0.7". Bumping them now would claim an unpublished artifact. Left unchanged (EN + TR): `getting-started.md`, `junit5.md`, `cucumber.md`, `gradle.md`, `migration/from-selenium-testng.md`, `src/pages/index.js:459` badge `Maven Central v1.0.4`, and the `docusaurus.config.js` announcement bar. `cli.md` and `ai/testfly-mcp.md` carry `@testfly/mcp 1.1.0`, a separate npm package. This conflicts with the AGENTS.md version-bump checklist, which predates the 2026-10-04 docs audit. After v1.0.7 is on Central, flip the pins and the "do not assume 1.0.7 is on Central" notes in a follow-up commit.

Other non-1.0.7 hits, all intentional: older CHANGELOG entries; `1.0.0` "requires/bundles since" statements in `accessibility.md`, `cloud-execution.md`, `external-test-data.md`; "pre-1.0.5 behaviour" in `configuration.md`; `1.0.5` load-testing "added in" notes; release.yml comment examples (`v1.0.2`, `1.0.0`); `plugin.json` / diagram `v1.0.0`; `.agents/rules/git-release-workflow.md` examples (`v1.0.6` -> `v1.0.7`).

## 1.1.0 references left for your decision

- `@TestFlyApi(since = "1.1.0")` / `@Deprecated(since = "1.1.0")`: 50 lines in 21 files under `src/main/java/io/testfly/` (loadtest, client, junit5/BaseJUnit5ApiTest, browser/SessionCache). The load-test module actually shipped under 1.0.5 and 1.1.0 never existed. Decide: leave, or retro-correct `since` in a separate metadata-only change.
- `docs-site/docs/{cli.md:15,ai/overview.md:22,ai/testfly-mcp.md:21}` + TR mirrors: `@testfly/mcp` 1.1.0 npm package, a different artifact. Left.
- `docs-site/i18n/tr/.../changelog.md:~473+` historical `## [1.1.0] — 2026-03-25` entry (pre-rebrand, absent from EN). Left.
- `.agents/wiki/api-testing.md:39` ("1.1.0 API sözleşmesi") and `.agents/skills/testfly-workflow/SKILL.md:125` (generic example). Left.

## Final CHANGELOG 1.0.7 section (verbatim)

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

## Verification (actually run)

- `mvn -B clean verify -Dgpg.skip=true` (repo root): BUILD SUCCESS, `Tests run: 1444, Failures: 0, Errors: 0, Skipped: 0` (matches scratchpad baseline 1444). `target/jacoco.exec` present (9.7 MB). Only log noise: an expected "Base url for ReportPortal server is not set!" from a ReportPortal bridge test.
- `cd docs-site && npm run build` (node_modules already present, no `npm install`): exit 0; `[en]` and `[tr]` both compiled successfully and generated static files; 0 occurrences of "broken" (no broken links/anchors).
- `docusaurus.config.js` was not edited, so `validate-config.js` was not needed.
- Greps run via `git grep` (tracked files only): `1\.0\.[0-9]+`, `1\.1\.0`, `<version>`, `testfly:` coordinates, `1362|1405|1444` (no doc states a test count besides the changelog).
- `git status --short`: only the 6 modified files above plus untracked `.agents/tasks/`. `target/` and `docs-site/build/` are git-ignored.

## Next steps for you (NOT executed)

Check the branch before every git command: `git branch --show-current` must print `development`.

```bash
cd /Users/hagul/Projects/TestFramework/testfly-worktrees/audit-phase1
git branch --show-current
git add CHANGELOG.md README.md AGENTS.md .github/profile/README.md docs-site/docs/changelog.md docs-site/i18n/tr/docusaurus-plugin-content-docs/current/changelog.md
git commit -m "chore(release): prepare v1.0.7"      # only if GPG terminal fails: git -c commit.gpgsign=false commit ...
git push origin development

# main is protected: open and merge a PR development -> main
gh pr create --base main --head development --title "Release v1.0.7" --body "Audit Phase 1 hardening + v1.0.7 release prep. See CHANGELOG.md."
# after the PR is merged:
git fetch origin
git rev-parse origin/main                          # merge commit SHA
git tag v1.0.7 origin/main                         # tag the merge commit on main
git push origin v1.0.7
```

`release.yml` triggers on `v*` tag pushes, or run it manually (`workflow_dispatch`, run from `main`; empty version = pom version). It fails unless the tagged commit is an ancestor of `origin/main`, the version matches `X.Y.Z[-qualifier]`, and the pom version equals the release version (already `1.0.7`). The publish job needs the `release` GitHub environment with required reviewers (approve the run there), plus the `MAVEN_USERNAME`, `MAVEN_PASSWORD`, `GPG_PRIVATE_KEY` and `GPG_PASSPHRASE` secrets. Scratchpad notes a pre-T1.3 dry run of the workflow on a fork is still pending. The workflow creates the GitHub Release itself if it does not exist.

After Central shows 1.0.7: flip the pinned 1.0.4 docs snippets and notes, set `LATEST_VERSION` in the separate `testfly/website` repo, and refresh the AGENTS.md version-bump checklist.
