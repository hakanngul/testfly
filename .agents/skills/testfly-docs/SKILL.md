---
name: testfly-docs
description: Audit or update TestFly product documentation, examples, release text, and configuration guidance while keeping English and Turkish content synchronized. Use for README, docs, docs-site, changelog, or documentation-sync work; not for code-only changes.
---

# TestFly documentation workflow

## Determine the documentation impact

1. Derive claims from current source, focused tests, `pom.xml`, configuration defaults, and workflows. Report source/document conflicts instead of repeating stale text.
2. From the code or release diff, identify affected public APIs, configuration keys/defaults, extension contracts, supported engines, examples, and migration notes.
3. Keep maintainer guidance concise in root files. Put user-facing product guidance in `docs-site/docs/`; do not create a second documentation hierarchy.

## Synchronize content

- Update the Turkish counterpart for every changed English page under `docs-site/docs/`. Preserve intentional locale-only files.
- Synchronize semantics—not only paths, headings, and versions. Commands, keys, defaults, API names, and limitations must agree across locales.
- Keep examples consumer-oriented and Java 21 compatible. Prefer examples exercised under `src/test/java/io/testfly/examples/`; label non-executable snippets.
- For release text, distinguish the Maven SDK artifact from the separate `@testfly/mcp` package and state compatibility or migration impact.
- Do not claim native mobile, external backend, or integration support without executable source or workflow evidence.

## Validate

Run `scripts/agent/verify.sh docs`. Also run the relevant code, API, SPI, or consumer gate when documentation includes executable behavior changed in the same task. Report reviewed examples that have no automated compile or runtime check.
