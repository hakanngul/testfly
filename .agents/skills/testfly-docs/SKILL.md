---
name: testfly-docs
description: Update TestFly product documentation and examples while preserving English/Turkish localization parity and validating the Docusaurus site. Use for README, docs, docs-site, examples, release text, or configuration documentation; not for code-only changes.
---

# TestFly documentation workflow

1. Verify claims against current source, tests, `pom.xml`, and configuration defaults. Do not use the retired agent wiki as evidence.
2. Keep root maintainer documents concise. User-facing product documentation belongs in `docs-site/docs/`.
3. When an English page under `docs-site/docs/` has a Turkish counterpart under `docs-site/i18n/tr/docusaurus-plugin-content-docs/current/`, update both. Preserve intentional locale-only files.
4. Keep examples consumer-oriented and compilable with Java 21. Prefer examples already exercised under `src/test/java/io/testfly/examples/`.
5. For release/version text, distinguish the Maven artifact from the separate `@testfly/mcp` package.
6. Run `scripts/agent/verify.sh docs`. Report any examples that were reviewed but not executable by an automated test.
