---
tags: [task, docs, release]
date: 2026-10-09
status: done-uncommitted
type: report
---

# Docs: flip Central pins to 1.0.7

Branch: `development` (verified). Nothing committed, pushed or tagged.

## Changed files

Install pins `1.0.4` -> `1.0.7` and "Published release and development" admonition removed (EN + TR mirror each):
- `docs-site/docs/{getting-started,gradle,junit5,cucumber}.md`, `migration/from-selenium-testng.md`
- `docs-site/i18n/tr/docusaurus-plugin-content-docs/current/` same five files
- `migration/from-selenium-testng.md` (EN+TR): Java note now "releases up to 1.0.4 need Java 17+, 1.0.6+ (incl. current 1.0.7) need Java 21"; coordinates -> 1.0.7.

Other:
- `docs-site/docusaurus.config.js`: announcement bar ("These docs cover development (1.0.7). Maven Central release: 1.0.4") removed entirely; no version text remains. `validate-config.js` passes.
- `docs-site/src/pages/index.js`: badge `Maven Central v1.0.4` -> `v1.0.7` (`homeData.js` already 1.0.7).
- `docs-site/docs/loadtest/getting-started.md` (+TR): Availability rewritten (added in 1.0.5, included in current 1.0.7, dependency from Maven Central); local "mvn clean install" prerequisite removed.
- `docs-site/docs/loadtest/examples.md` (+TR): "development build" link text -> TestFly `1.0.7` (setup).
- `docs-site/docs/guides/api-schema-validation.md` (+TR): removed "install the current source artifact locally" claim and "(development)" heading suffix; now "available in the current release 1.0.7". The `since = "1.2.0"` text is untouched (user decision pending).
- TR-only stale versions found and fixed to match EN: `gradle.md` (`testfly:2.6.0` x5 -> 1.0.7), `accessibility.md` (TestFly 2.5.0 -> 1.0.0), `external-test-data.md` (2.2.0 -> 1.0.0), `cloud-execution.md` (3.2.1+ -> 1.0.0+).
- `AGENTS.md`: Version-bump checklist rewritten to match reality (install pins, version prose, badge, homeData, AGENTS "Current version", profile README, TR mirrors, what NOT to bump).
- `.agents/rules/git-release-workflow.md`: step 4 now points at the AGENTS.md checklist (adds docs pins/badge/TR mirrors).

## Verification
- `node .agents/skills/docusaurus-config/scripts/validate-config.js docs-site/docusaurus.config.js`: passed.
- `cd docs-site && npm run build`: EN and TR compiled successfully, no broken links reported.
- `git status --short`: only docs/config/AGENTS.md/.agents rule edits (plus pre-existing `.agents/memories/*` changes and `.agents/tasks/`).

## Leftover hits (intentional)
- `1.0.4` in `migration/from-selenium-testng.md` EN+TR: historical Java-requirement statement ("up to 1.0.4 need Java 17+").
- `1.0.4` in `.agents/memories/log.md` / `scratchpad.md`: agent history/notes (scratchpad TODO "docs 1.0.4 pin'lerini çevir" is now done; not edited here).
- `this checkout` in `CHANGELOG.md` and `docs-site/docs/changelog.md` / TR changelog (1.0.6 "Documentation audit" entry): historical, left per instructions.
- `@testfly/mcp` 1.1.0 "source checkout" wording in `cli.md`, `ai/overview.md`, `ai/testfly-mcp.md` (+TR): npm package, out of scope.
- `package-lock.json` `1.0.4` entries: unrelated npm deps.
