# Review: TestFly v1.0.7 release preparation (uncommitted working tree on `development`)

The change merges `[Unreleased]` into a single `[1.0.7] — 2026-10-09` changelog section in all three changelog files (root, EN docs-site, TR docs-site). It also corrects three stale "current version" statements: `README.md`, `AGENTS.md` and `.github/profile/README.md`. Nothing is committed, pushed or tagged. The Maven Central–pinned `1.0.4` install snippets are deliberately left alone, which is a documented deviation from the `AGENTS.md` bump checklist.

Watch for: nothing blocking. The `1.0.4` docs pins and the `@TestFlyApi(since="1.1.0")` annotations are open user decisions, not defects (confirmed, both are called out in the report).

**Verdict**: APPROVED

## High-level view

Current-version strings are consistent. `pom.xml` and the README dependency snippets were already 1.0.7. The three that were wrong (`README.md:801` said v1.1.0, `AGENTS.md:45` said 1.1.0, `.github/profile/README.md:91` said 1.0.2) are fixed. Every remaining non-1.0.7 hit falls into one of four groups: Central-pinned `1.0.4` snippets, `@testfly/mcp` 1.1.0 (a separate npm package), historical or "since" statements, and `@TestFlyApi(since="1.1.0")` annotations. The report justifies each group.

The 1.0.7 changelog section covers the real `origin/main...development` diff (7 commits, 160 files). I spot-checked the bullets against code and found none invented. The EN and TR changelogs carry the same content, so locale parity holds.

Git state is clean of release actions. `origin/development..HEAD` is empty, no tag points at HEAD, the branch is `development`, and `main` equals `origin/main`. Verification evidence is present: 1444 tests with 0 failures, and an EN+TR docs build with 0 "broken" matches.

<details>
<summary>Issues (3)</summary>

1. **Version-bump checklist deviation (non-blocking, confirmed)** — `getting-started.md`, `junit5.md`, `cucumber.md`, `gradle.md`, `migration/from-selenium-testng.md`, `src/pages/index.js:459` and their TR mirrors still say `1.0.4`. This is intentional because Central only has up to 1.0.4. Flip them after publishing, and refresh the stale checklist in `AGENTS.md`.
2. **`since = "1.1.0"` annotations (non-blocking, confirmed)** — 50 lines in 21 files claim an API version that never shipped. The user decides whether to retro-correct them in a separate metadata-only change.
3. **Test-count baseline (non-blocking, likely)** — "grew from 1362 to 1444" uses the prior-session baseline recorded in the scratchpad, not a measured count at `origin/main`. Reword if exactness matters.

</details>

<details>
<summary>Details</summary>

### Stale-version sweep

`git grep` for `1\.1\.0` across the checklist files leaves only `@testfly/mcp` 1.1.0 in `docs-site/docs/{ai/overview.md:22,ai/testfly-mcp.md:21,cli.md:15}` and the TR mirrors. Those lines say explicitly that the package name and version come from the MCP source checkout and are not an npm release. They are a different artifact, so leaving them is correct (confirmed).

`git grep` for `1\.0\.[0-9]` outside changelogs shows only `1.0.0` "requires/bundles since" statements (`accessibility.md:11`, `cloud-execution.md:12-13`, `external-test-data.md:10`), the "pre-1.0.5 behaviour" note (`configuration.md:428`), and the `1.0.4` Central pins. The first two groups are historical. The third is explained in the report and in each page's own "this checkout is 1.0.7" note. This follows plan decision 2, and publishing 1.0.7 snippets before the artifact exists would break copy-paste installs. I accept the deviation from the checklist.

### CHANGELOG accuracy

I checked these bullets against the tree and the diff:

- `execution.sessionWaitSeconds` defaults to 300 (`TestFlyConfig.java:219`).
- `ExecutionValidator.crossCheckWarnings` exists (`ExecutionValidator.java:64`).
- `LoadTestDetector` is new (`A` in the name-status diff).
- `GEMINI.md`, `PRODUCT.md` and `features/features-report.md` are deleted.
- The `testfly-test-authoring` skill is added.
- `jackson-bom` and `jackson.version` 2.21.7 appear in the pom diff. `origin/main` pinned only `jackson-databind`.
- The `argLine` is `@{argLine} ...`, and `maven-gpg-plugin` moved into the release profile.
- `release.yml` now has the ancestor check against `origin/main`, the `environment: release` gate, `deploy -Prelease`, and no tag-triggered pom rewrite.
- `LoadScenario.assertStatus` loops over `statusCodes` keys.

The old `[Unreleased]` heading is gone from all three files. The date is 2026-10-09 in all three. The 1.0.7 section also keeps the previously released-in-draft bullets (cssSelector, assertion boundary, deprecation, docs sync), which is correct since 1.0.7 was never tagged. I found no invented entries and no missing major change.

### EN/TR parity

Both locale changelogs changed (EN +35, TR +31 lines). The TR section uses the existing TR headings and carries the same bullets, including the Security and Tests sections. The other three modified files are not locale-specific. The report records that both `[en]` and `[tr]` builds compiled with 0 "broken" matches.

### Git state

`git log origin/development..HEAD` is empty. `git tag --points-at HEAD` is empty. The branch is `development`. `main` and `origin/main` share SHA `ab5c5b6…`. `git status` shows only the 6 modified files plus untracked `.agents/tasks/`. The report warns not to commit `.agents/tasks/`.

### Report completeness

The report contains the files changed, the verbatim changelog section, the test and build numbers (1444/0/0/0, jacoco.exec present, EN+TR build OK), the 1.1.0 decision list, and next commands. The commands are consistent with `release.yml`: a `v*` tag push or `workflow_dispatch`, the commit reachable from `main`, the `release` environment gate, and the secrets listed. The tag is created on the `origin/main` merge commit, as the workflow requires.

</details>

<details>
<summary>File map</summary>

- `CHANGELOG.md`: `[Unreleased]` merged into `[1.0.7]`, dated 2026-10-09.
- `docs-site/docs/changelog.md`: same 1.0.7 content (EN).
- `docs-site/i18n/tr/.../current/changelog.md`: Turkish 1.0.7 section.
- `README.md`: Project Status line now v1.0.7.
- `AGENTS.md`: Current version 1.0.7.
- `.github/profile/README.md`: dependency snippet 1.0.7.

Full diff: `git diff` in the working tree, plus `git diff origin/main...development` for the release content.

</details>
