# TestFly — QA Remediation Tasks

> Source: QA architecture review of the repository folder structure.
> Goal: close the test-pyramid gaps and repo-hygiene issues before public release.
> Execute tasks in order (T-01 → T-13). Each task is self-contained and independently mergeable.

---

## Global Constraints (apply to EVERY task)

- Build tool: Maven (`pom.xml`). Never edit the build to silence a failing test.
- Java + TestNG/JUnit 5 conventions already used in `src/test/java/io/testfly/**`. Match the existing test style (naming, assertion library, package layout).
- **No new dependencies** unless the task explicitly allows it. Prefer JDK stdlib, existing test deps, and interfaces already in the codebase. If you believe a new dependency is required, stop and justify it in the PR description first.
- Do not break the public API (`io.testfly.api.TestFlyApi`, anything documented in `docs/public-api.md`) without an explicit deprecation path.
- Every new test must pass locally via `mvn test` (or the narrowed command given in the task) and must be deterministic — no real network, no real browser, no wall-clock sleeps. Use `TestClock`, mocks/stubs, and in-memory fakes.
- Flaky tests are bugs. If a test you write flakes twice, quarantine it via the existing `quarantine` mechanism and report it — never `@Ignore` silently.
- Keep diffs minimal and scoped to the task. Do not refactor unrelated code.

---

## P1 — Critical

### T-01 · Unit tests for the JUnit 5 wiring layer
**Risk:** `io.testfly.junit5` (TestFlyExtension, EnableTestFly, ReportPortalJUnit5Bridge, TestFlyLauncherListener, BaseJUnit5Test) is the framework's entry wiring and has zero unit tests. A regression here breaks every consumer.

**Scope:**
- Create `src/test/java/io/testfly/unit/junit5/` and cover:
  - `TestFlyExtension` — lifecycle callbacks (beforeAll/afterEach/afterAll), driver init/teardown ordering, behavior when driver creation fails.
  - `TestFlyLauncherListener` — suite start/finish events reach registered report adapters (use a fake `ReportAdapter`).
  - `EnableTestFly` — annotation presence/absence toggles extension registration.
  - `ReportPortalJUnit5Bridge` — no-op behavior when ReportPortal is not configured (must not throw).

**Acceptance criteria:**
- [ ] All classes above have direct unit coverage with a mocked/stubbed driver (no real browser).
- [ ] Failure paths covered: driver init failure, adapter throwing an exception (framework must survive and log).
- [ ] `mvn test -Dtest='io.testfly.unit.junit5.*'` passes.

---

### T-02 · Unit tests for framework bootstrap
**Risk:** `io.testfly.lifecycle.FrameworkBootstrap` has no test; mis-ordered initialization is invisible until runtime.

**Scope:**
- Test bootstrap idempotency (second call does not re-initialize).
- Test initialization order: config loaded → SPI registries populated → hooks registered.
- Test behavior with a missing/invalid `testfly.yml` (must fail fast with `ConfigurationException`, not NPE).

**Acceptance criteria:**
- [ ] Bootstrap covered for happy path + missing config + invalid config.
- [ ] No filesystem state leaks between tests (use temp dirs / in-memory config).

---

### T-03 · Unit tests for the recording package
**Risk:** `io.testfly.recording` (GifEncoder, RecordingManager) is untested. A recording failure = lost failure evidence in reports.

**Scope:**
- `GifEncoder` — encode a small synthetic frame sequence (in-memory `BufferedImage`s), assert output bytes are a valid GIF (magic bytes, frame count), assert behavior on empty frame list (defined: throw or no-op — document the choice in the test name).
- `RecordingManager` — start/stop lifecycle, output file naming via `ReportPaths`, recording disabled config = zero side effects, disk-write failure degrades gracefully (test fails, framework doesn't crash).

**Acceptance criteria:**
- [ ] All tests use temp directories (`@TempDir`), no real browser.
- [ ] Graceful-degradation path explicitly tested.

---

### T-04 · Integration suite skeleton + email provider integration tests
**Risk:** `src/test/java/io/testfly/integration/` contains exactly one test. `email/` ships 4 providers (Imap, Mailhog, Mailtrap, Outlook) with no integration coverage.

**Scope:**
- Establish `integration/email/` with a greenmail-style in-memory SMTP/IMAP server **only if an embedded mail-server test dependency already exists in `pom.xml`**; otherwise implement against a hand-rolled fake server socket, or reduce scope to Mailhog/Mailtrap via their HTTP APIs against a local stub (WireMock-style if already on classpath).
- Cover `ImapProvider` end-to-end: send mail → poll via `MailboxClient` with `EmailCriteria` → assert match + timeout path.
- Mailhog/Mailtrap providers: verify request construction (auth headers, query params) against the stub.

**Acceptance criteria:**
- [ ] Integration tests live under `integration/`, are tagged/profiled so they do NOT run in the default `mvn test` (follow the tagging mechanism already used for `examples/` — if none exists, introduce a JUnit tag `integration` and configure surefire excludes in `pom.xml`, documenting it in `docs/ci-execution.md`).
- [ ] Suite is green when enabled explicitly.

---

### T-05 · Integration tests for db and testmanagement clients
**Risk:** `DbClient`/`DbConnectionFactory` and `TestRailClient`/`XrayClient` are untested network/IO clients — classic silent-breakage zone.

**Scope:**
- `db`: if Testcontainers is already a dependency, spin up PostgreSQL (or H2 in PG mode as a zero-dep fallback); otherwise use H2 in-memory. Cover: connection factory config parsing, query execution, `DbAssertException` on assertion mismatch, connection cleanup on failure.
- `testmanagement`: stub TestRail/Xray REST APIs (HTTP stub already on classpath, else plain `HttpServer` from JDK). Cover: result payload mapping, 4xx/5xx handling (must surface a clear exception, not swallow), auth header construction.

**Acceptance criteria:**
- [ ] Same `integration` tag/profile as T-04.
- [ ] Error paths (server down, 401, 500) each have a test.

---

## P2 — Major

### T-06 · Fill unit-test holes per package
**Rule:** match the existing test style; one test class per production class; boundary + negative cases mandatory, not just happy path.

| Package | Classes to cover | Notes |
|---|---|---|
| `ai` | `ClaudeProvider`, `AiProviderRegistry`, `AiFailureAnalyzer` (unit-level) | Mock HTTP layer. Registry: duplicate-registration and unknown-provider behavior. |
| `assertion` | `LocatorAssert`, `SoftAssertions`, `SoftAssertionCollector` | Soft assertions: multiple failures aggregate into one error, order preserved. |
| `browser` | `ConsoleErrorCollector`, `DownloadManager`, `DeviceEmulator`, `browser.SessionCache` | `DownloadManager` with `@TempDir`; ConsoleErrorCollector with fake driver/CDP events. |
| `healing` | `HealingCache`, `HealLog` | Cache: hit/miss/expiry/eviction. A wrong cached locator must be detectable — test invalidation explicitly. |
| `precondition` | `ApiHealthChecker`, `PreConditionRunner`, `PreConditionRegistry`, `precondition.SessionCache` | Runner: failing precondition skips dependent test; registry: unknown condition error. |
| `client` | `ApiAuth`, `SchemaValidator` | Auth: each `UseAuth` strategy builds correct headers. SchemaValidator: valid/invalid/missing-field JSON cases. |
| `cucumber` | `CucumberRetryContext`, `CucumberStepLogger`, `CucumberContext` | Glue code is testable without running a feature file — instantiate directly. |

**Acceptance criteria:**
- [ ] Every class listed above has a corresponding `*Test` in the matching `unit/` package.
- [ ] `mvn test` fully green.

---

### T-07 · Resolve the duplicate `SessionCache`
**Risk:** `io.testfly.browser.SessionCache` and `io.testfly.precondition.SessionCache` are two unrelated classes with the same name — divergence/confusion risk.

**Scope:**
1. Read both classes and determine whether they share semantics.
2. If different: rename to `BrowserSessionCache` / `PreconditionSessionCache`. If either is part of the documented public API, keep a `@Deprecated` subclass/alias for one release and note it in `CHANGELOG.md`.
3. If same: merge into one (prefer `io.testfly.browser` or a neutral package) and update all usages.

**Acceptance criteria:**
- [ ] Exactly one `SessionCache` name exists per semantic concept.
- [ ] `CHANGELOG.md` entry written; no unannounced breaking change.

---

### T-08 · Isolate `examples/` and `tdd/` demo tests from the default build
**Risk:** saucedemo-based UI tests and AI demos inside `src/test` are environment-dependent → flaky default builds.

**Scope:**
- Tag all classes under `unit/…/examples/**` and `unit/…/tdd/**` (e.g. `@Tag("demo")`) or move them to a dedicated source set.
- Configure `pom.xml` so default `mvn test` excludes them; add a `-Pdemo` profile (and/or the existing CI profile) that includes them.
- Document how to run demos in `docs/getting-started.md` (one short section).

**Acceptance criteria:**
- [ ] `mvn test` passes with no network/browser dependency.
- [ ] Demo suite is runnable via an explicit, documented command.

---

## P3 — Minor / Hygiene

### T-09 · Verify `.gitignore` coverage
- [ ] Confirm `node_modules/`, `docs-site/build/`, `target/` are ignored. If any are currently tracked, untrack them (`git rm -r --cached`) and add ignore rules. Report the repo-size impact in the PR.

---

### T-10 · Fix documentation drift
- [x] Rename `implementation-status.md`; update all references. (done)
- [ ] Root `CONTRIBUTING.md` vs `docs/CONTRIBUTING.md`: keep one source of truth, replace the other with a link.
- [ ] Check `docs/ci/github-actions.md`: if `.github/workflows/` does not exist, either add the documented workflow or mark the page as "planned". Doc and repo must agree.
- [ ] `AGENTS.md` and `CLAUDE.md`: align content so both agents behave identically — keep one canonical file and make the other reference it, if tooling allows.

---

### T-11 · Define visual-baseline policy
- [ ] `src/test/resources/baselines/` is empty. Decide and document in `docs/` (or `docs-site/docs/`): are baselines committed? How are they updated (`-Dvisual.updateBaselines=true`-style flag)? Add the flag to `VisualAssert` if it doesn't exist, with a unit test.

---

### T-12 · Add coverage measurement
- [ ] Add JaCoCo to `pom.xml` (report-only, no threshold yet), wire `mvn verify` to emit `target/site/jacoco/index.html`.
- [ ] Document in `docs/ci-execution.md` how to read the report.
- [ ] After one full run, paste the per-package coverage table into `implementation-status.md` so the gaps from T-06 become measured, not estimated.

---

### T-13 · Follow-up: quality gate
Depends on T-12.
- [ ] Wire the existing `BuildThresholdEnforcer` to JaCoCo output: fail the build if line coverage of `io.testfly.junit5`, `io.testfly.lifecycle`, `io.testfly.recording`, `io.testfly.healing` drops below an agreed floor (suggest starting at current measured value, ratchet up, never down).

---

## Definition of Done (whole batch)

1. `mvn test` green with zero network/browser dependencies.
2. Integration suite green when explicitly enabled.
3. No duplicate `SessionCache`; CHANGELOG updated.
4. Repo free of tracked build artifacts.
5. Coverage report generated and baseline recorded.
6. Every task's checkboxes ticked in a single PR per task (or stacked PRs in order).
