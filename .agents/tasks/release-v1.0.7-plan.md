# Implementation Plan — TestFly v1.0.7 release preparation

Root: `/Users/hagul/Projects/TestFramework/testfly-worktrees/audit-phase1` (call it `$R`; always use absolute paths).
Branch must stay `development`. **Do not commit, push, tag, merge, or touch `main`. Leave every change UNCOMMITTED.**
Run `git -C $R branch --show-current` before starting and again at the end (expect `development`).

## Findings from exploration (facts the implementer can rely on)

- `development` is 7 commits / 160 files ahead of `origin/main` (HEAD `e47e75a`); working tree was clean at planning time. `testfly/target/audit-scratch/phase1-summary.md` does NOT exist, so the diff/commit log is the source of truth.
- Commits in `origin/main..development`: `299a4c6` OpenApiValidator + LoadScenario fixes, testfly-test-authoring skill, 2026-10-04 docs audit; `8a2eb85` locator test isolation; `d6ca2ff` agent-guidance consolidation; `1d6063c` release.yml hardening / jackson-bom 2.21.7 / JaCoCo / gpg moved to `release` profile; `b904f7f` core fixes (HtmlReport, DriverManager, Locator, load-test); `c6b43ed` docs fixes EN/TR; `e47e75a` memory update.
- `pom.xml` is already `1.0.7` on BOTH `origin/main` and `development`. Remote tags: v1.0.2, v1.0.4, v1.0.6. v1.0.7 does not exist.
- **Maven Central only has 1.0.0–1.0.4** (`https://repo1.maven.org/maven2/io/github/hakanngul/testfly/maven-metadata.xml`, latest/release = 1.0.4). The docs-site install snippets are deliberately pinned to `1.0.4` with an explicit "verified Central release / this checkout is 1.0.7 / do not assume 1.0.7 is on Central" note (written by the 2026-10-04 docs audit; scratchpad D-01 is an open user decision on this). Bumping them now would publish a false claim.
- `CHANGELOG.md` already contains an `## [Unreleased]` section (load-test detection, Locator auto-wait, getByText, load-test status fixes) AND a `## [1.0.7] — 2026-09-29` section (cssSelector, assertion boundary, docs). `docs-site/docs/changelog.md` (EN) and the TR changelog mirror contain `[1.0.7]` but no `[Unreleased]`, so they are missing the audit content entirely.
- `docs-site/build/` exists locally (generated); do not hand-edit it.

## Decisions (made here, do not re-litigate)

1. **Merge `[Unreleased]` into `[1.0.7]`**, date `2026-10-09`, keep the existing style (Keep-a-Changelog headings, bold lead-in + sentence). The `[Unreleased]` heading is removed (nothing is left unreleased). Reason: v1.0.7 does not exist yet, so one section must describe the whole `main..development` diff.
2. **Version bump scope = "current framework version" statements only.** Reason: the version-bump checklist in `AGENTS.md` predates the 2026-10-04 docs audit, and following it literally for the docs-site Central-pinned snippets would claim an unpublished artifact. Those `1.0.4` pins and their notes stay unchanged and are reported to the user as a post-publish follow-up (see "Deliberately NOT changed").
3. **Do not touch** historical CHANGELOG entries (1.0.6 and older), `@TestFlyApi(since = "…")` / `@Deprecated(since = "1.1.0")` annotations, `features/`, `docs/plans/`, `.agents/memories/log.md` history, `.agents/plugins/testfly-agents/plugin.json` (`1.0.0`, separate plugin artifact).
4. Do not decompose into FEAT artifacts: the work is small, sequential and verified once at the end.

## Steps

- [ ] 1. **Re-baseline.** Run `git -C $R branch --show-current` (expect `development`), `git -C $R status --short` (expect clean), `git -C $R ls-remote --tags origin` (expect highest `v1.0.6`). Re-run the straggler greps from step 6 and save the output mentally as the "before" picture.
      Verify: commands return the expected values; if branch is not `development`, STOP and report.

- [ ] 2. **Version sync — files to change (current-version statements → 1.0.7).**
      a. `$R/pom.xml` line 9: already `1.0.7` — no edit, just confirm.
      b. `$R/README.md` lines ~31 and ~184 dependency snippets: already `1.0.7` — confirm only. Line ~801: change `**Current release: v1.1.0** — Load & Performance Testing with Gatling and Lightweight Virtual Thread Engines, …` to `**Current release: v1.0.7** — ` followed by a one-line summary that matches the real diff (audit Phase 1 hardening: HTML report escaping, DriverManager session-permit/per-suite fixes, Locator auto-wait, explicit load-test detection, hardened release workflow). Do not keep the 1.1.0 feature blurb.
      c. `$R/AGENTS.md` line ~45: `- **Current version:** \`1.1.0\`` → `\`1.0.7\``. Also check the same file for any other "current version" claim (e.g. the version-bump checklist is generic — leave it).
      d. `$R/.github/profile/README.md` line ~91: `<version>1.0.2</version>` → `<version>1.0.7</version>` (public org landing snippet; `README.md` already uses 1.0.7 so this keeps the repo consistent; it becomes true on publish).
      e. `$R/docs-site/src/data/homeData.js` lines ~667 (`value: '1.0.7'`) and ~771 (`<version>1.0.7</version>`): already `1.0.7` — confirm only.
      f. `$R/docs-site/docs/loadtest/getting-started.md` line 25 and TR mirror line 25: already `1.0.7` — confirm only.
      g. `$R/docs-site/docusaurus.config.js` lines ~115–116 announcement bar: "development (1.0.7) … Central release: 1.0.4" — already correct, confirm only.
      h. `$R/CHANGELOG.md`, `$R/docs-site/docs/changelog.md`, TR `changelog.md`: handled in steps 3–4.
      i. Do NOT change: `docs-site/docs/{getting-started,junit5,cucumber,gradle,migration/from-selenium-testng}.md` and their TR mirrors (they pin Central `1.0.4` on purpose and already state `This checkout is version 1.0.7`); `docs-site/src/pages/index.js` line ~459 badge `Maven Central v1.0.4` (factually correct); `cli.md`, `ai/overview.md`, `ai/testfly-mcp.md` + TR mirrors (`@testfly/mcp 1.1.0` is the npm MCP package, a different artifact).
      Files: `README.md`, `AGENTS.md`, `.github/profile/README.md`.
      Verify: `git -C $R diff --stat` shows only these files (plus changelog files after steps 3–4); step 6 greps show no remaining `v1.1.0`/`Current version … 1.1.0` meaning "this framework".

- [ ] 3. **Author the CHANGELOG `[1.0.7]` section** in `$R/CHANGELOG.md`: delete the `## [Unreleased]` block (lines ~8–21 incl. its trailing `---`) and change the heading to `## [1.0.7] — 2026-10-09`. Keep the existing bullets under `### Added`, `### Deprecated`, `### Documentation & Specifications`; add/merge the bullets below (verified against the real diff — re-check each against `git diff origin/main...development` while writing; drop anything you cannot confirm).
      Content (English, existing style):
      - `### Added`: (keep the existing two bullets); **`execution.sessionWaitSeconds`** (default 300, `0` = do not wait, validated `>= 0`) controls how long a test waits for a free slot when `maxActiveSessions` is exhausted; **`ExecutionValidator.crossCheckWarnings`** warns when `threadCount > maxActiveSessions`; **`LoadTestDetector`**; **`testfly-test-authoring`** agent skill.
      - `### Changed`: the two existing bullets (load-test detection behavior change — keep the BREAKING-ish wording; `Locator` auto-wait); `DriverManager` throws a clear message when a driver is requested outside a test thread (e.g. in `@BeforeMethod` before the driver exists); Jackson modules pinned through `jackson-bom` 2.21.7 (was 2.21.6 on `jackson-databind` only); JaCoCo coverage reporting enabled in the Maven build (`argLine` is now `@{argLine} …`); GPG signing moved from the default build into the `release` Maven profile, so `mvn verify`/`install` need no key (release uses `-Prelease`).
      - `### Fixed`: existing `getByText()` innermost-match and load-test status-code bullets (keep); `HtmlReportGenerator` now HTML-escapes error messages (XSS) and renders in a single pass; `DriverManager` always releases the session permit (permit leak), recreates dead `per-suite` drivers; `OpenApiValidator` passes the URI path and the `Content-Type` header (fixes "No API path found" and body validation); `LoadScenario.assertStatus(expected)` called `assertNoStatus` — fixed (already covered by the existing load-test bullet, merge, don't duplicate).
      - `### Security`: `.github/workflows/release.yml` hardening — release version input validated (no newline/injection; read via environment, never interpolated), release commit must be reachable from `main`, publishing gated on the `release` GitHub environment, pom version must equal the release version, tag pinned to the verified commit.
      - `### Documentation & Specifications`: keep existing bullets; add: docs corrected to `io.github.hakanngul` groupId, uncompilable examples rewritten against the real API, load-test docs aligned with the real DSL (`load(path).users().rampUp().hold()`, `LoadTestFeeder`, `@LoadTest` attributes), Maven Central vs development version notes, WaitEngine/Kubernetes/Allure claims corrected (EN + TR); consolidated agent guidance (`AGENTS.md`, `GEMINI.md`, `PRODUCT.md`, `ROADMAP.md`, `.agents/*`) and retired obsolete plans.
      - `### Tests`: unit suite grew 1362 → 1444 tests (regression tests for HTML escaping, DriverManager, LoadTestDetector, JdkLoadEngine, OpenApiValidator, locator test-id isolation).
      Also leave the historical edits already in the diff (older entries annotated as legacy `io.testfly` coordinates) untouched.
      Files: `$R/CHANGELOG.md`.
      Verify: `grep -n '^## \[' $R/CHANGELOG.md | head -4` shows `[1.0.7] — 2026-10-09` first and no `[Unreleased]`; `grep -c 'Unreleased' $R/CHANGELOG.md` = 0.

- [ ] 4. **Mirror the changelog into the docs-site (bilingual).** Per `.agents/rules/docusaurus-workflow.md` (skill `.agents/skills/docusaurus-config/SKILL.md`, read it first): update the `## [1.0.7]` section of `$R/docs-site/docs/changelog.md` (EN, same text as step 3, date `2026-10-09`) and of `$R/docs-site/i18n/tr/docusaurus-plugin-content-docs/current/changelog.md` (Turkish translation of the same bullets, using that file's headings `### Eklenenler`, `### Değiştirilenler`/`### Düzeltilenler`/`### Güvenlik`/`### Dokümantasyon ve Spesifikasyonlar` — copy the exact heading words already used elsewhere in that file). Keep code identifiers untranslated. The TR file's historical `[1.1.0] — 2026-03-25` entry (~line 473) stays unchanged.
      Files: the two changelog.md files above.
      Verify: step 5 docs build passes; `grep -n '^## \[1.0.7\]' ` in all three changelogs shows the date `2026-10-09`.

- [ ] 5. **Full verification.**
      a. `cd $R && mvn clean verify -Dgpg.skip=true` — expect BUILD SUCCESS, **1444 tests, 0 failures, 0 errors** (baseline from scratchpad; the number must not drop), and `$R/target/jacoco.exec` present. (GPG is now in the `release` profile so `-Dgpg.skip` is harmless.) Do not use `-DskipTests`.
      b. `cd $R/docs-site && npm run build` — expect success for BOTH `en` and `tr` locales, zero errors, zero broken links. If `node_modules` is missing run `npm install` first (note it in the report). If `docusaurus.config.js` is edited (it should not be) also run `node $R/.agents/skills/docusaurus-config/scripts/validate-config.js $R/docs-site/docusaurus.config.js`.
      c. `git -C $R branch --show-current` → `development`; `git -C $R log origin/development..development --oneline` → empty (nothing committed); `git -C $R status --short` → only the files from steps 2–4; confirm `docs-site/build/` and `target/` are ignored (`git status` must not list them).
      Verify: all three commands succeed with the numbers above. If tests fail, diagnose (do not weaken tests) and report.

- [ ] 6. **Straggler greps** (run from `$R`, excluding `node_modules .git target build .docusaurus package-lock.json`):
      - `grep -rnIE '1\.0\.[0-9]+' . …` — every hit must be one of: current `1.0.7`; Central-pinned `1.0.4` in the docs files listed in step 2.i (intentional); older version history (CHANGELOG, `log.md`, `@TestFlyApi(since=…)`, "pre-1.0.5 behaviour", "added in 1.0.5", "Java 17+ for 1.0.4 / Java 21 from 1.0.6"); `release.yml` examples (`v1.0.2`, `1.0.0` in comments); `plugin.json`/diagram `v1.0.0`. Anything else (e.g. a stale `1.0.2`/`1.0.3`) is a straggler → fix if it denotes the current version.
      - `grep -rnIE '1\.1\.0' …` — classify each hit (below).
      - `grep -rnI -B2 -A2 '<version>' README.md .github docs-site/docs docs-site/i18n docs-site/src | grep -A3 -B3 testfly` — every `io.github.hakanngul`/`testfly` `<version>` is 1.0.7 (README, homeData, loadtest getting-started) or the intentional 1.0.4.
      - `grep -rnIE 'testfly:[0-9]' …` and `grep -rnI 'io\.testfly:testfly' …` — Gradle coordinates are `io.github.hakanngul:testfly:1.0.4` (intentional) or 1.0.7; the only `io.testfly:testfly` hits allowed are the two historical CHANGELOG entries annotated as legacy (+ EN/TR changelog mirrors).
      - `grep -rnIE '1362|1405|1444' README.md AGENTS.md docs-site/docs` — if a doc states a test count, update it to 1444.

## `1.1.0` references — classification (confirm during step 6)

Current-version meaning (FIX): `README.md:801` ("Current release: v1.1.0"), `AGENTS.md:45` ("Current version: 1.1.0").

Different thing (DO NOT REWRITE; report to the user for a decision):
- `@TestFlyApi(since = "1.1.0")` and `@Deprecated(since = "1.1.0")` across `src/main/java/io/testfly/{loadtest,client,junit5/BaseJUnit5ApiTest,browser/SessionCache}` (≈60 hits). These claim an API-introduction version that never shipped as 1.1.0 (the load-test module's CHANGELOG entry is under 1.0.5; versions went 1.0.x). Needs a user call: leave, or retro-correct to the real introduction version in a separate change (public-API stability contract applies — annotation `since` edits are metadata-only but affect docs/javadoc).
- `@testfly/mcp` 1.1.0 npm package in `docs-site/docs/{cli.md,ai/overview.md,ai/testfly-mcp.md}` and TR mirrors — separate artifact.
- TR `changelog.md` historical `[1.1.0] — 2026-03-25` block (pre-rebrand entry, absent from EN) — historical, leave.
- `.agents/wiki/api-testing.md:39` ("1.1.0 API sözleşmesi") and `.agents/skills/testfly-workflow/SKILL.md:125` (generic example "1.0.0 -> 1.1.0") — agent docs, leave; mention in report.

## Deliberately NOT changed (report as follow-ups for the user)

- Docs-site install snippets pinned to Central `1.0.4` (getting-started, junit5, cucumber, gradle, migration/from-selenium-testng, EN+TR), `index.js` badge `Maven Central v1.0.4`, and the `docusaurus.config.js` announcement bar's "Maven Central release: 1.0.4". After v1.0.7 is actually published to Central, flip these (and the "do not assume 1.0.7 is on Central" notes) in a follow-up commit. Also note Central currently has no 1.0.5/1.0.6 (v1.0.6 tag exists but was apparently never published — scratchpad D-01 root cause is still open).
- `AGENTS.md` version-bump checklist is out of date relative to this reality (lists files that are intentionally pinned); suggest the user refresh it.

## Final report requirements (last message of the implementing/reviewing steps)

1. Branch confirmation (`development`) and proof nothing was committed/pushed/tagged (`git status --short` file list, `git log origin/development..development` empty).
2. Table of every file changed with a one-line reason.
3. The final CHANGELOG `[1.0.7]` section text (or its headings + bullet count) and confirmation EN/TR docs changelogs match it.
4. Verification evidence: `mvn clean verify` result with exact test counts (expect 1444/0/0), JaCoCo exec present, `npm run build` result for en+tr.
5. Straggler-grep outcome: list of remaining non-1.0.7 references with category (historical / Central-pinned intentional / different artifact).
6. The `1.1.0` classification above with the open decisions for the user (`@TestFlyApi(since="1.1.0")`, Central-pin flip after publish, checklist refresh).
7. Final instructions for the user to release (do NOT execute): (a) review & commit on `development` — `git branch --show-current` then `git add <files>` and `git commit -m "chore(release): prepare v1.0.7"` (use `-c commit.gpgsign=false` only if the GPG terminal fails); `git push origin development`; (b) open a PR `development` → `main` and merge it (main is protected; `release.yml` requires the tagged commit to be reachable from `main`); (c) tag the merge commit on `main`: `git tag v1.0.7 <merge-sha> && git push origin v1.0.7` (or run the workflow via `workflow_dispatch` from `main`); (d) approve the run in the GitHub `release` environment (required reviewers must be configured; scratchpad notes a pre-T1.3 dry-run on a fork is still pending); (e) after Central shows 1.0.7, flip the pinned docs snippets and set `LATEST_VERSION` in the separate `testfly/website` repo.

## Review checklist for the independent reviewer

- Diff touches only the files listed above; no commit/tag exists; branch `development`.
- No `[Unreleased]` left; `[1.0.7]` dated 2026-10-09 and every bullet is traceable to `git diff origin/main...development`; no invented claims.
- Older changelog entries byte-identical to `HEAD` (`git diff` shows no changes outside the top 1.0.7 section in the three changelog files).
- No `1.1.0` current-version claim remains; `@TestFlyApi(since)` untouched; Central-pinned `1.0.4` docs untouched.
- EN/TR changelog parity; `npm run build` green; `mvn clean verify` green with ≥1444 tests.
