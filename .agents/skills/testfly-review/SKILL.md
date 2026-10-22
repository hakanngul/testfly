---
name: testfly-review
description: Review a TestFly Java SDK diff for correctness bugs, public/binary API breakage, thread-safety, optional-dependency leaks, test isolation problems and doc drift, and report ranked findings with evidence. Use for code review of a branch, commit range or working-tree diff; read-only.
---

# TestFly Java review

Review the diff (`git diff <base>...HEAD` or the working tree), then read the surrounding code and tests. Do not edit files.

## Checklist

1. **Correctness:** trace each changed path from a real caller (test base class, listener, fluent chain). Check null handling, exceptions, and cleanup in `finally` and teardown.
2. **Public API:**
   - `@TestFlyApi` types: no removed, renamed or narrowed members; descriptors unchanged.
   - New methods on stable interfaces need a compatible `default`.
   - Config keys and defaults unchanged, or documented.
   - Confirm with `scripts/agent/check-public-api.sh <base>`.
3. **Concurrency:** WebDriver, session and device state stay thread-confined (`ThreadLocal`); no new shared mutable static state; parallel-safe under surefire `parallel=methods`.
4. **Optional dependencies:** JUnit, Cucumber, Gatling, DB drivers and others are not loaded by core paths. No class literal of an optional type in always-loaded code.
5. **Tests:**
   - A test that fails without the change.
   - Nested sample classes are run by surefire because the pom's `<excludes>` replaces the default; put samples in a file that does not match `*Test`.
   - Tests that export reports must not write into the shared `target/reports` (other forks read it); set `testfly.reports.dir`.
   - No real network.
6. **Docs:** EN/TR parity under `docs-site/`; claims backed by code; incubating features labelled.
7. **Security:** no secrets; remote endpoints opt-in; safe path and shell handling.

## Report

Findings, most severe first. Each one has `file:line`, the problem, a concrete failure scenario, a suggested fix, and **blocking** or **optional**. "No findings" is a valid result. Skip style nits that the surrounding code does not follow.
