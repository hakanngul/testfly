# AREA C — BUILD, CONFIG & DEPENDENCIES (audit-only)

Scope: `pom.xml`, dependency tree, optional-dep scoping, config loading/precedence (`ConfigurationLoader`, `DotEnvLoader`, `TestFlyDefaults`, `-Dtestfly.profile`, `${VAR}`), CI (`.github/workflows/*`, `ci/Jenkinsfile`), release/publishing, and version/coordinate consistency.
Branch audited: `chore/docs-cloudflare-workers` @ 67305e4. No repo files were modified. All scratch output is under `target/audit-scratch/` (git-ignored `target/`).

Baseline taken from `discovery.md` / `build-test.md` / `deptree.txt` (not re-run): `mvn -q compile` exit 0; `mvn test` BUILD SUCCESS, 1361 tests, F0 E0 S0 (≈50 s); JDK = Temurin 21.0.12, Maven 3.9.16.

Classification legend: **DOĞRULANDI** = reproduced or directly proven from files/command output; **GÜÇLÜ ŞÜPHE** = strongly implied by evidence but not executed; **İYİLEŞTİRME ÖNERİSİ** = improvement, not a defect.

Commands I ran beyond the shared artifacts (all read-only / scratch): `curl` to repo1.maven.org metadata, `curl` to api.github.com (repo existence), OSV.dev batch query (only public Maven coordinates sent, no project code), `mvn -o dependency:tree` on the repo with `-Dverbose` (writes to `target/audit-scratch`), `mvn -o dependency:tree` on a scratch consumer pom under `target/audit-scratch/consumer`, and three tiny Java programs under `target/audit-scratch/cfg` run against `target/classes`.

---

## 0. Version / coordinate single source of truth

Single source of truth = `pom.xml:7-9` → `io.github.hakanngul:testfly:1.0.7` (per discovery.md).
BUT Maven Central does **not** contain 1.0.5–1.0.7 (see BLD-001), so the *consumable* truth is currently `1.0.4`.

---

## 1. Findings

### Group V — Version, coordinates, publication

#### BLD-001 — P0 — Documented install version is not resolvable from Maven Central (1.0.5–1.0.7 never published)
- **Classification:** DOĞRULANDI (fact); cause of the missing releases = GÜÇLÜ ŞÜPHE.
- **Problem / root cause:** pom, README, docs-site, landing page all tell users to depend on `io.github.hakanngul:testfly:1.0.7`, but Central only hosts 1.0.0–1.0.4. Tags exist for `v1.0.2`, `v1.0.4`, `v1.0.6` only (no v1.0.5/v1.0.7). The release workflow (`.github/workflows/release.yml`) either never ran, failed, or was not triggered for 1.0.5+. CHANGELOG dates 1.0.5 = 2026-09-12, 1.0.6/1.0.7 = 2026-09-29, while Central metadata `lastUpdated` = 20260907 (≥ 1 month older).
- **Evidence:**
  ```
  $ curl -s https://repo1.maven.org/maven2/io/github/hakanngul/testfly/maven-metadata.xml
  <latest>1.0.4</latest> <release>1.0.4</release> versions: 1.0.0 1.0.1 1.0.2 1.0.3 1.0.4  lastUpdated 20260907122817
  $ curl -o /dev/null -w "%{http_code}" .../1.0.6/testfly-1.0.6.pom  -> 404
  $ curl -o /dev/null -w "%{http_code}" .../1.0.7/testfly-1.0.7.pom  -> 404
  $ git tag --list --sort=creatordate  -> v1.0.2 v1.0.4 v1.0.6
  ```
  `target/` still holds locally-signed `testfly-1.0.6*.jar/.asc` (manual local build, never published).
- **File refs:** `pom.xml:9`; `README.md:5,31,184` (badge + snippets); `docs-site/docs/getting-started.md:52`; `docs-site/src/pages/index.js:459` ("Maven Central v1.0.7"); `CHANGELOG.md:10`.
- **Impact:** A new user following README/Getting-Started gets `Could not find artifact io.github.hakanngul:testfly:jar:1.0.7`. Onboarding is broken. Landing-page badge text "Maven Central v1.0.7" is false.
- **Fix:** (1) Find out why 1.0.5–1.0.7 are not on Central (check Actions run history for `release.yml`, Central Portal deployments dashboard). (2) Publish (or re-tag + publish) the intended release. (3) Until published, make README/docs say the last *published* version, or add a build-time check (BLD-003 fix) that docs version == Central latest. (4) Fix badge/landing text.
- **Effort:** S (publish) – M (root-cause release pipeline). **Dependencies:** BLD-010/BLD-011 (release pipeline fixes) should land before the next tag. **Regression risk:** low.
- **Repro:** `curl -s https://repo1.maven.org/maven2/io/github/hakanngul/testfly/maven-metadata.xml`.

#### BLD-002 — P1 — Wrong `groupId` `io.testfly` documented in many places (does not exist on Central)
- **Classification:** DOĞRULANDI.
- **Root cause:** An earlier rename plan (`io.testfly`) was never applied to Central coordinates; the Java package is `io.testfly` but the Maven groupId is `io.github.hakanngul`. Docs/AGENTS mix the two.
- **Evidence:** `curl https://repo1.maven.org/maven2/io/testfly/testfly/maven-metadata.xml` → HTTP 404 page. Occurrences (file:line):
  - Maven XML `<groupId>io.testfly</groupId>`: `docs-site/docs/getting-started.md:50`, `docs-site/docs/junit5.md:24`, `docs-site/docs/cucumber.md:22`, `docs-site/docs/migration/from-selenium-testng.md:27`, `CONTRIBUTING.md:76`, and the TR mirrors `docs-site/i18n/tr/.../getting-started.md:50`, `junit5.md:24`, `cucumber.md:22`, `migration/from-selenium-testng.md:27`.
  - Gradle `io.testfly:testfly:<v>`: `docs-site/docs/getting-started.md:61,75`; `docs-site/docs/gradle.md:32,49,115,131,277`; `docs-site/docs/junit5.md:51`; TR `getting-started.md:61,75`, `gradle.md:32,49,115,131,277`, `junit5.md:51`.
  - Prose: `docs-site/docs/migration/from-serenity.md:124`, `from-selenide.md:131` (+ TR `:124`, `:131`); `docs-site/docs/changelog.md:206,271`; TR `changelog.md:158,242`; `CHANGELOG.md:180,253`.
  - Badge: `docs-site/docs/intro.md:13` and TR `intro.md:13` use `maven-central/v/io.testfly/testfly` (badge will always show "not found") while the link target is correct.
  - `AGENTS.md:44` (`Group / Artifact: io.testfly:testfly`).
  - Correct usages for contrast: `pom.xml:7`, `README.md:29,182`, `.github/workflows/testfly-ci.yml:181`, `docs-site/docs/loadtest/getting-started.md:31`, `docs-site/docusaurus.config.js:127,161`, `SECURITY.md:23`.
- **Impact:** Copy-paste of 8+ doc pages fails resolution; Gradle users (all Gradle docs) are always broken.
- **Fix:** Replace every `io.testfly:testfly` / `<groupId>io.testfly</groupId>` with `io.github.hakanngul`; fix badge URL; correct AGENTS.md:44. Keep Java package `io.testfly` unchanged. Add a docs lint (grep gate in CI) that fails on `io.testfly:testfly` and on `<groupId>io.testfly</groupId>`.
- **Effort:** S. **Dependencies:** pair with BLD-003 in one docs pass (EN+TR per docusaurus-workflow rule). **Regression risk:** low (docs only).
- **Repro:** `grep -rnE "io\.testfly:testfly|<groupId>io\.testfly</groupId>" docs-site CONTRIBUTING.md AGENTS.md CHANGELOG.md`.

#### BLD-003 — P1 — Version string inconsistent across pom / README / AGENTS / CHANGELOG / docs-site (full enumeration)
- **Classification:** DOĞRULANDI.
- **Root cause:** No single-source mechanism; the AGENTS "version-bump checklist" lists only a subset of files (misses `cucumber.md`, `gradle.md`, `loadtest/getting-started.md`, `from-selenium-testng.md`, `.github/profile/README.md`, all TR mirrors), and the pom version was bumped without updating the rest.
- **Evidence (every inconsistent location; truth = 1.0.7 / Central = 1.0.4):**

| Location | Value | Note |
|---|---|---|
| `pom.xml:9` | 1.0.7 | source of truth |
| `README.md:31`, `:184` | 1.0.7 | OK |
| `README.md:797` | "Current release: **v1.1.0**" | wrong (also describes Gatling/DSL as 1.1.0) |
| `AGENTS.md:45` | `1.1.0` | wrong |
| `AGENTS.md:44` | `io.testfly:testfly` | wrong group (BLD-002) |
| `docs/loadtest-architecture-plan.md:613` (sed ctx of :611) | 1.1.0 | planning doc, stale |
| `docs-site/docs/getting-started.md:52,61,75` (+TR same lines) | 1.0.7 | version OK, group wrong |
| `docs-site/docs/junit5.md:26` (EN+TR) | 1.0.7 | Maven OK |
| `docs-site/docs/junit5.md:51` (EN) | **1.0.4** | Gradle snippet disagrees with Maven snippet 25 lines above |
| TR `junit5.md:51` | **1.0.0** | |
| `docs-site/docs/gradle.md:32,49,115,131,277` | **1.0.0** | |
| TR `gradle.md:32,49,115,131,277` | **2.6.0** | version never existed |
| `docs-site/docs/cucumber.md:22` (+TR) | **1.0.0** | |
| `docs-site/docs/migration/from-selenium-testng.md:27` (+TR) | **1.0.0** | |
| `.github/profile/README.md:89` | **1.0.2** | org profile page |
| `docs-site/docs/loadtest/getting-started.md:31` (+TR) | 1.0.7 | OK |
| `docs-site/docs/cli.md:25` (+TR) | 1.0.7 | OK |
| `docs-site/src/pages/index.js:459` | "Maven Central v1.0.7" | false (BLD-001) |
| `CHANGELOG.md:10,26,54,127,176` | 1.0.7,1.0.6,1.0.5,1.0.4,1.0.0 | 1.0.1–1.0.3 (published!) have no entry; 1.0.5–1.0.7 unpublished; no v1.0.5/v1.0.7 tags |
| `docs-site/docs/changelog.md` / TR `changelog.md` | mirror of CHANGELOG (14,30,58,122,202) | same gaps |
| `docs-site/package.json:3` | 0.0.0 | fine (private) |

  Dependency-version drift documented vs actual (see also BLD-004).
- **Impact:** Users cannot tell which version is current; CI consumer job and docs diverge; TR Gradle users get a non-existent version.
- **Fix:** (1) Decide the canonical released version after BLD-001. (2) One-pass replace using the table. (3) Make `README`/docs derive the version from a single place: either a `docs-site` variable file generated from `pom.xml` in `scripts/build.js`, or a CI step that greps all `<version>`/`testImplementation` TestFly snippets and compares to `mvn help:evaluate -Dexpression=project.version`. (4) Extend the AGENTS.md checklist to the full list above. (5) Backfill/clarify CHANGELOG 1.0.1–1.0.3.
- **Effort:** M. **Dependencies:** BLD-001, BLD-002. **Regression risk:** low.
- **Repro:** `grep -rnE "io\.(github\.hakanngul|testfly)" -A2 docs-site README.md AGENTS.md | grep -E "version|:[0-9]+\.[0-9]+"`; also `target/audit-scratch/versions.txt`.

#### BLD-004 — P2 — Documented dependency/tool versions drifted from pom (AGENTS.md, docs-site)
- **Classification:** DOĞRULANDI.
- **Evidence:**

| Doc location | Doc says | pom says |
|---|---|---|
| `AGENTS.md:74` | Selenium 4.40.0 | `pom.xml:50` 4.48.0 |
| `AGENTS.md:77` | Jackson 2.21.0 | `pom.xml:68` 2.21.6 |
| `AGENTS.md:82` | jakarta.mail 2.0.1 | 2.0.2 |
| `AGENTS.md:83` | POI 5.2.5 | 5.4.0 |
| `AGENTS.md:290` | workflow `.github/workflows/testfly.yml` | actual file `testfly-ci.yml` |
| `AGENTS.md:277` | consumer repo `github.com/testfly/testfly-test` | CI uses `${owner}/testfly-test`; both 404 (BLD-012) |
| `AGENTS.md:326` | "Publishing is currently a manual step" | `release.yml` exists (tag/dispatch) |
| `AGENTS.md:327` | docs deploy "when `docs-site/**` changes on `main`" | no workflow in `.github/workflows/` does this (Cloudflare Git integration is external — unverifiable) |
| `docs-site/docs/external-test-data.md:117`, `gradle.md:246` | `poi-ooxml:5.2.5` | 5.4.0 |
| `docs-site/docs/gradle.md:247` | `jakarta.mail:2.0.1` | 2.0.2 |
| `docs-site/docs/loadtest/getting-started.md:46`, `engines.md:18` | Gatling 3.10.3 / 3.10.x, only `gatling-charts-highcharts` | `pom.xml:220-247` 3.13.5, four Gatling artifacts (core-java, http-java, app, highcharts) |
| `docs-site/docs/ci/jenkins.md:21` (+TR) | `jdk 'JDK17'` | `--release 21` (`pom.xml:322`) → build fails on JDK 17 |
| `CONTRIBUTING.md:101` | "Open a PR against `master`" | branches are `main`/`development` |
| `CONTRIBUTING.md` (Local credentials) | `src/test/resources/testfly.yml` "ignored by Git" | file is tracked (`git ls-files src/test/resources/testfly.yml`) |

- **Impact:** Developers/agents trust stale facts; POI 5.2.5 recommendation is older than the version the framework tests (and carries known OOXML advisories — verify with OSV before citing).
- **Fix:** Update the rows above; prefer "see pom.xml" over hard-coding versions in AGENTS.md. **Effort:** S. **Dependencies:** none. **Risk:** none.
- **Repro:** compare with `mvn help:evaluate` / `grep -n "<version>" pom.xml`.

---

### Group S — Dependency security & resolution

#### BLD-005 — P1 — `jackson-databind:2.21.6` (compile scope, transitive to every consumer) has 2 HIGH advisories
- **Classification:** DOĞRULANDI (advisory data from OSV.dev; applicability to TestFly's usage not analysed).
- **Evidence:** OSV query (`api.osv.dev/v1/querybatch`) for `com.fasterxml.jackson.core:jackson-databind@2.21.6` → `GHSA-cxp5-3px4-pw24` (HIGH, "quadratic forward-reference completion") and `GHSA-wv8q-qhhj-9h54` (HIGH, "retains every unknown raw type ID"); both published 2026-09-30, fixed in 2.21.7 / 2.22.3 (also 2.18.11). `origin/dependabot/maven/development/com.fasterxml.jackson.core-jackson-databind-2.22.3` exists but is unmerged. `pom.xml:66-69`.
- **Impact:** Direct, non-optional compile dependency → exposed in all consumer projects that parse untrusted JSON (ApiClient responses).
- **Fix:** Bump to 2.21.7 (patch, lowest risk for a 1.0.x line) or merge the dependabot 2.22.3 PR after testing; then import `jackson-bom` (see BLD-007).
- **Effort:** S. **Dependencies:** BLD-007. **Regression risk:** low (patch) / medium (2.22 minor).
- **Repro:** `curl -s -d '{"package":{"name":"com.fasterxml.jackson.core:jackson-databind","ecosystem":"Maven"},"version":"2.21.6"}' https://api.osv.dev/v1/query`.

#### BLD-006 — P2 — Dependency mediation downgrades Selenium's Guava and ByteBuddy inside TestFly's own build; `guice` declared `optional` instead of `test`
- **Classification:** DOĞRULANDI (resolution); runtime breakage = GÜÇLÜ ŞÜPHE.
- **Root cause:** `guice:5.1.0` (declared `optional`, compile scope, `pom.xml:195-199`) brings `guava:30.1-jre`, which wins "nearest-wins" over Selenium 4.48.0's needed Guava 33.6.0-jre. `mockito-core:5.11.0` brings `byte-buddy:1.14.12`, which wins over Selenium's 1.18.11, while `byte-buddy-agent` is separately forced to 1.18.12 (`pom.xml:288-293`) → ByteBuddy core/agent version skew. No `dependencyManagement`, no Enforcer `dependencyConvergence`.
- **Evidence (`mvn -o dependency:tree -Dverbose`):**
  ```
  | selenium-remote-driver:4.48.0 -> (com.google.guava:guava:jar:33.6.0-jre:compile - omitted for conflict with 30.1-jre)
  | selenium-support:4.48.0       -> (net.bytebuddy:byte-buddy:jar:1.18.11 - omitted for conflict with 1.14.12)
  +- com.google.inject:guice:jar:5.1.0:compile -> com.google.guava:guava:jar:30.1-jre:compile
  +- org.mockito:mockito-core:jar:5.11.0:test -> net.bytebuddy:byte-buddy:jar:1.14.12:compile ; (byte-buddy-agent 1.14.12 omitted for conflict with 1.18.12)
  ```
  A scratch consumer pom with only TestFly's non-optional deps resolves `guava:33.6.0-jre` and `byte-buddy:1.18.11` (`target/audit-scratch/consumer/tree.txt`). So the 1361 unit tests run against a *different* Guava/ByteBuddy than any consumer. Additionally guice is never imported by `src/main` (grep: 0 hits), and an `optional` dependency is never transitive, so the pom comment ("ReportPortal-enabled consumers have it on the classpath") is incorrect.
  Also: Guava 30.1-jre carries `GHSA-5mg8-w23w-74h3` (LOW) and `GHSA-7g45-4rm6-3mm3` (MODERATE) — present on TestFly's own compile/test classpath.
- **Impact:** Own test suite is not representative; Selenium code paths that use newer Guava APIs (not executed because Selenium is mocked) could throw `NoSuchMethodError` only in TestFly's build, hiding or creating false failures; dependabot's guice 7.0.0 bump would shift this again.
- **Fix:** Change guice to `<scope>test</scope>`; add `<dependencyManagement>` pinning `guava` (33.x) and `byte-buddy`/`byte-buddy-agent` (same version), or exclude guice's guava; add `maven-enforcer-plugin` with `dependencyConvergence` + `requireJavaVersion [21,)` + `requireMavenVersion [3.8,)`. Fix pom comment.
- **Effort:** S–M. **Dependencies:** BLD-008. **Regression risk:** low–medium (test classpath changes; re-run `mvn test`).
- **Repro:** `mvn -o dependency:tree -Dverbose -Dincludes=com.google.guava:guava,net.bytebuddy:byte-buddy`.

#### BLD-007 — P2 — Jackson module version skew (optional modules at 2.14–2.17 vs databind 2.21.6)
- **Classification:** GÜÇLÜ ŞÜPHE (version skew proven; incompatibility not reproduced).
- **Evidence:** `deptree.txt`: `jackson-databind:2.21.6`, `jackson-annotations:2.21`, but `jackson-dataformat-yaml:2.17.1` (via `networknt:json-schema-validator:1.4.3`, optional), `jackson-datatype-jsr310:2.14.2` and `jackson-datatype-jdk8:2.15.2` (via `swagger-request-validator-core:2.39.0`, optional). No `jackson-bom` import. Jackson supports mixing only same-minor modules.
- **Impact:** Consumers who follow docs and add json-schema-validator get yaml module 2.17 on a 2.21 core; `ApiResponse.assertSchema` / OpenAPI validation may fail with `NoSuchMethodError`/`NoClassDefFoundError` after core bumps. Our unit tests pass (1361) but may not exercise these paths with real schema loading.
- **Fix:** Import `com.fasterxml.jackson:jackson-bom:2.21.7` in `dependencyManagement` (applies to our own build); document the same for consumers; add one unit test that really loads a YAML-defined schema/OpenAPI spec.
- **Effort:** S. **Dependencies:** BLD-005. **Regression risk:** low.
- **Repro:** `grep jackson target/audit-scratch/deptree.txt`.

#### BLD-008 — P2 — Mockito dynamic-agent loading + `-Dnet.bytebuddy.experimental=true`: JDK 24+/25 readiness unverified
- **Classification:** GÜÇLÜ ŞÜPHE (only JDK 21 available locally: `ls ~/.sdkman/candidates/java` → `21.0.12-tem`).
- **Evidence:** `test.log`: 47 × "A Java agent has been loaded dynamically (…byte-buddy-agent-1.18.12.jar) … Dynamic loading of agents will be disallowed by default in a future release". `pom.xml:338` sets `-Dnet.bytebuddy.experimental=true` (unneeded on 21 with ByteBuddy ≥1.14.12 → masks real support level). Surefire fork has no `-javaagent:mockito-core.jar` / `-XX:+EnableDynamicAgentLoading`.
- **Impact:** On JDK 24/25 (25 is LTS) the dynamic attach is blocked by default → the whole mocked test suite could fail to start. CI only tests JDK 21 (`testfly-ci.yml:31,91,138`).
- **Fix:** Add Mockito as explicit `-javaagent` via `maven-dependency-plugin:properties` + `@{argLine}`; add a CI matrix `[21, 25]` (allow-failure on 25 initially); drop the experimental flag once ByteBuddy is aligned (BLD-006).
- **Effort:** M. **Dependencies:** BLD-006, BLD-009 (argLine rewrite). **Regression risk:** medium (test infra).
- **Repro:** `grep -c "Java agent has been loaded dynamically" target/audit-scratch/test.log`; run `mvn test` under JDK 25 (not possible here).

---

### Group B — Build, test pipeline, quality gates

#### BLD-009 — P2 — JaCoCo coverage gate is silently inactive (Surefire `argLine` overrides the agent)
- **Classification:** DOĞRULANDI.
- **Root cause:** `pom.xml:338` sets a literal `<argLine>` in Surefire, replacing the `argLine` property that `jacoco:prepare-agent` populates; the literal does not contain `@{argLine}`/`${argLine}`. So the agent is never attached, `target/jacoco.exec` is never written, `jacoco:report` and `jacoco:check` skip.
- **Evidence:** `test.log:13` "argLine set to -javaagent:…jacoco.agent-0.8.12-runtime.jar=destfile=…/target/jacoco.exec"; `test.log:1362` "Skipping JaCoCo execution due to missing execution data file."; `ls target/jacoco* target/site` → no matches. Gate thresholds `pom.xml:480` (LINE 0.40) / `:485` (BRANCH 0.30) in the `verify` phase are therefore never enforced (also in `release.yml`'s `mvn verify`).
- **Impact:** Coverage regressions undetected; `README`/pom comment "report at target/site/jacoco" is false. False sense of quality in release gating.
- **Fix:** `<argLine>@{argLine} -Dnet.bytebuddy.experimental=true --add-opens …</argLine>` and define an empty default `<argLine/>` property so non-JaCoCo runs don't break; verify with `mvn verify -Dgpg.skip` that `target/jacoco.exec` and `target/site/jacoco/` appear; then re-calibrate thresholds (first real numbers unknown).
- **Effort:** S. **Dependencies:** BLD-008. **Regression risk:** low–medium (JaCoCo 0.8.12 + JDK 21 OK; thresholds may start failing once active — measure first).
- **Repro:** `mvn test` then `ls target/jacoco.exec` (absent); check log line "Skipping JaCoCo execution".

#### BLD-010 — P2 — GPG signing is bound to `verify` in the default build; documented/CI `mvn verify` paths fail without a key
- **Classification:** GÜÇLÜ ŞÜPHE (follows from the pom lifecycle; not executed because `verify` would sign/attempt to sign).
- **Evidence:** `pom.xml:414-433` `maven-gpg-plugin:sign` at `verify`, no profile/skip property. Callers that run `verify` without `-Dgpg.skip`: `CONTRIBUTING.md:102` (`mvn clean verify`), `AGENTS.md` build section, `.github/workflows/testfly-ci.yml:106` (`mvn verify -Preal-backends`), `ci/Jenkinsfile:68` (`mvn verify -Preal-backends`). Callers that already work around it: `testfly-ci.yml:143` (`-Dgpg.skip=true` for `install`), `release.yml:51` (`verify -Dgpg.skip=true`). Plugin is 3.1.0 (`pom.xml:415`); `--pinentry-mode loopback` configured (`:424-427`).
- **Impact:** Contributors without a secret key cannot run the documented PR gate; `framework-integration-tests` job and Jenkins "Integration Tests" stage will fail at sign time as soon as `AI_API_KEY` is configured (currently masked by the early `exit 0`, BLD-012).
- **Fix:** Move gpg-plugin, javadoc/source jars (optional) and `central-publishing-maven-plugin` into a `release` profile (activated only in `release.yml` with `-Prelease`); keep default `verify` side-effect free. Alternatively default `<gpg.skip>true</gpg.skip>` and override in release.
- **Effort:** S. **Dependencies:** BLD-011 (release workflow must pass `-Prelease`). **Regression risk:** medium — must test a dry-run release.
- **Repro:** on a machine without a GPG secret key: `mvn -o -DskipTests verify` → expect `gpg: signing failed: No secret key`.

#### BLD-011 — P2 — Release workflow: script injection, unguarded triggers, no tag↔pom check, unpinned plugin resolution, double test run
- **Classification:** DOĞRULANDI (static review of `.github/workflows/release.yml`).
- **Evidence / issues:**
  1. `release.yml:40-41` interpolates `${{ inputs.version }}` straight into a `run:` shell script (`VERSION="${{ inputs.version }}"`) → shell injection by anyone who can dispatch (needs write access, still a classic GHA anti-pattern; secret `GPG_PRIVATE_KEY` is already loaded into the job at `:34`).
  2. Trigger `push: tags: ['v*']` (`:6`) publishes from **any** commit/branch carrying a `v*` tag; no check the tag commit is on `main`, no GitHub `environment:` approval gate for the publishing secrets.
  3. `mvn versions:set -DnewVersion` rewrites `pom.xml` in the runner; nothing verifies `pom.xml`, README, CHANGELOG, docs agree with the tag, and a `workflow_dispatch` run with a version creates **no tag** (explains tag/pom drift: tags v1.0.2/v1.0.4/v1.0.6 vs pom 1.0.7).
  4. `versions:set`, `versions:use-dep-version` (`testfly-ci.yml:180`) and `help:evaluate` (`testfly-ci.yml:148`) are invoked without plugin versions → latest-at-runtime, non-reproducible.
  5. `Verify` step (`:51`) runs the full test suite, then `mvn -B deploy` (`:54`) runs the whole lifecycle again (tests + javadoc + gpg + jacoco) → ~2× time and a second chance to fail after Verify passed (e.g. GPG env, BLD-009 once enabled).
  6. No dependency on the CI workflow succeeding for that commit; `autoPublish=true` + `waitUntil=published` (`pom.xml:442-443`) means a bad tag publishes immediately and irreversibly (Central releases are immutable).
  7. `maven-gpg-plugin` 3.1.0 + setup-java `gpg-passphrase` works with the legacy settings-based flow; plugin ≥3.2.0 expects `MAVEN_GPG_PASSPHRASE` env directly (workflow already exports it in the deploy step, so a future upgrade is compatible; verified from actions/setup-java#668 / maven-gpg-plugin docs).
- **Impact:** Possible secret exfiltration via crafted dispatch input; accidental or unauthorised irreversible release; version drift (BLD-001/003).
- **Fix:** Pass inputs via `env:` and validate with a regex (`^[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9.]+)?$`); add `environment: release` with required reviewers; add a step that fails unless `git merge-base --is-ancestor $GITHUB_SHA origin/main`; assert `pom` version == tag before deploy; pin `versions-maven-plugin` version; use `mvn -B deploy -DskipTests` after the verify step (or only `deploy`); create the git tag/GitHub Release in the workflow.
- **Effort:** M. **Dependencies:** BLD-010. **Regression risk:** medium (needs a dry run on a fork/test namespace).
- **Repro:** read `.github/workflows/release.yml` lines 42-57.

#### BLD-012 — P2 — CI jobs report green while doing nothing (false assurance)
- **Classification:** DOĞRULANDI (workflow logic) + consumer repo absence DOĞRULANDI via GitHub API; whether it is private = unknown.
- **Evidence:**
  - `testfly-ci.yml:101-106` `framework-integration-tests`: if `AI_API_KEY` secret is empty → `exit 0` ("Skipping real backend tests") → job passes. On fork PRs secrets are never available, so the job is always a no-op there.
  - `testfly-ci.yml:154` consumer checkout has `continue-on-error: true`; if missing, only a `::warning::` is printed and all later steps are skipped; the job is still green. `curl https://api.github.com/repos/hakanngul/testfly-test` → **404**, and `testfly/testfly-test` → **404** (AGENTS.md:277), so unless the repo is private the whole "Consumer Integration Tests" job never tests anything. The `mvn install -DskipTests` it runs is wasted time then.
  - `dorny/test-reporter@v1` (`:72,119,208`) needs `checks: write`, unavailable on fork PRs; `fail-on-error: false`, `fail-on-empty: false` hide empty/failed reports.
- **Impact:** Required checks look green but verify nothing for integration/consumer paths; regressions in published-API usage can ship.
- **Fix:** Make skips explicit (`if: ${{ secrets.AI_API_KEY != '' }}` at job level so GitHub shows "skipped"); fail (or mark required-optional) when the consumer repo is unavailable on `main`/`development` pushes; set `fail-on-empty: true` for the unit-test report; run unit-test reporting only for same-repo PRs.
- **Effort:** S. **Dependencies:** none. **Regression risk:** low.
- **Repro:** `curl -s -o /dev/null -w "%{http_code}" https://api.github.com/repos/hakanngul/testfly-test`.

#### BLD-013 — P2 — CI is missing the gates the repo claims: docs build, quality profile, JDK matrix, doc-snippet checks
- **Classification:** DOĞRULANDI (absence) / İYİLEŞTİRME ÖNERİSİ (what to add).
- **Evidence:** `.github/workflows/` contains only `testfly-ci.yml` and `release.yml`. No job runs `npm run build` for `docs-site` (despite AGENTS "verify with `npm run build`" and `onBrokenLinks: 'throw'` at `docusaurus.config.js:33`), none runs `mvn verify -Pquality` (required by `CONTRIBUTING.md:110`), JDK is only `21` (`:31,91,138`), no OS matrix, no `concurrency:` group, no `timeout-minutes` (default 360 min), and the push+pull_request triggers duplicate runs for same-repo PRs. `docs-site` has `package-lock.json` but no workflow caches/uses it. `.github/dependabot.yml` covers `maven` and `npm` only — no `github-actions` ecosystem, and actions are pinned to mutable major tags (`actions/checkout@v5`, `dorny/test-reporter@v1`, etc.).
- **Impact:** Broken docs, Java-snippet drift (the main finding area of this audit) and quality-gate regressions are detectable only manually.
- **Fix:** Add `docs` job (Node 18/20, `npm ci && npm run build` in `docs-site`), a `quality` job (`mvn -B verify -Pquality -Dgpg.skip`), JDK matrix `[21, 25]`, `timeout-minutes`, `concurrency: cancel-in-progress`, `github-actions` dependabot ecosystem; pin third-party actions by SHA.
- **Effort:** M. **Dependencies:** BLD-010, BLD-015. **Regression risk:** low.
- **Repro:** `ls .github/workflows; grep -n "npm\|Pquality\|matrix" .github/workflows/*.yml`.

#### BLD-014 — P3 — `ci/Jenkinsfile` and Jenkins docs target the wrong JDK/branch assumptions
- **Classification:** DOĞRULANDI.
- **Evidence:** `ci/Jenkinsfile:4-6` declares only `maven 'Maven-3'`, no `jdk` tool → relies on the agent's default Java (must be 21+). `docs-site/docs/ci/jenkins.md:21` documents `jdk 'JDK17'` / `maven 'Maven3'` (different tool name than the real file) which fails with `release version 21 not supported`. `Jenkinsfile:95` falls back to `env.BRANCH_NAME ?: 'master'` (repo uses `main`/`development`). The `post { success { build job: 'testfly-test' … } }` (`:92-97`) fires on every branch/PR build and uses `wait:false`, so downstream failures are invisible. `mvn clean compile` then `mvn test` repeats compile; the integration stage runs `mvn verify -Preal-backends` which re-runs unit tests and signs (BLD-010).
- **Impact:** Minor; Jenkins pipeline is secondary to GitHub Actions.
- **Fix:** Add `jdk 'JDK21'`; fix docs; guard the downstream trigger with `when { branch 'main' }`; default branch `main`.
- **Effort:** S. **Dependencies:** BLD-010. **Regression risk:** low.

#### BLD-015 — P2 — `quality` profile (SpotBugs/Checkstyle/PMD) is probably ineffective or broken and is never run
- **Classification:** GÜÇLÜ ŞÜPHE (not executed: offline repo cache lacks plugins and running `verify` signs artifacts).
- **Evidence:** `pom.xml:610-615` Checkstyle 3.4.1 with `google_checks.xml` + `failsOnError=true`: Google checks report at *warning* severity and the plugin's default `violationSeverity=error`, so the gate would not fail on style; the repo style (4-space indent) would produce thousands of warnings anyway. `maven-checkstyle-plugin:3.4.1` bundles Checkstyle 9.3 (no Java 21 syntax support) and `maven-pmd-plugin:3.23.0` defaults to PMD 6.x with `/rulesets/java/*.xml` paths (PMD 6 layout; PMD 6.55 lacks Java 21 grammar). CONTRIBUTING.md:110 makes this a PR requirement; CI never runs it (BLD-013).
- **Impact:** Contributors are told a quality gate exists that either cannot parse Java 21 sources or cannot fail.
- **Fix:** Run `mvn -B verify -Pquality -Dgpg.skip` once; then either (a) upgrade to checkstyle plugin ≥3.5 with `<dependencies>` on Checkstyle 10.x and a project-specific `checkstyle.xml`, PMD plugin with `<pmdVersion>7.x` + new ruleset paths, or (b) remove the profile and CONTRIBUTING claim. Add to CI after it is green.
- **Effort:** M. **Dependencies:** BLD-013. **Regression risk:** low.
- **Repro:** `mvn -Pquality -DskipTests -Dgpg.skip=true verify` (needs network).

#### BLD-016 — P3 — Surefire/profile configuration smells
- **Classification:** İYİLEŞTİRME ÖNERİSİ (items 1-3 DOĞRULANDI statically; item 4 GÜÇLÜ ŞÜPHE).
- **Evidence (`pom.xml`):**
  1. `:341-347` `forkCount=4` + `reuseForks=false` (new JVM per test class) **and** `parallel=methods` with `threadCount=4` + `perCoreThreadCount=true`; `perCoreThreadCount` is a JUnit-provider parameter and is ignored by the TestNG provider (log: "Using configured provider org.apache.maven.surefire.testng.TestNGProvider"). Result: up to 4 JVMs × 4 threads with class-scoped static mocks — the comment says isolation is the goal, but method-level parallelism inside a fork contradicts it and is a flakiness risk (static mocks, `AnnotationTransformer already set` warnings in log).
  2. `:657,676` profile comments say "tests tagged with @Tag(...)" (JUnit 5 annotation) but the filter is TestNG `excludedGroups` (`:350`).
  3. `:688` profile `integration` overrides `<excludes>` and drops the `**/integration/**` and `*IntegrationTest` exclusions (so `-Pintegration` runs real-backend tests inside `surefire`, while `real-backends` uses failsafe).
  4. `:505-508` `<testfly.profile>real-backends</testfly.profile>` as a Maven *property* does not become a JVM system property for Surefire (only failsafe sets it explicitly at `:547`), so `-Preal-backends` unit runs likely still load `testfly.yml`.
  5. Tests write into the source tree (`VisualAssert` "Saved as new baseline …/src/test/resources/baselines/…" in `test.log`; ignored via `.gitignore:78` but still a side effect of `mvn test`).
- **Impact:** Slower/less deterministic test runs; confusing profile semantics.
- **Fix:** Drop `perCoreThreadCount`; decide between method-parallelism and per-class forks; fix comments; point baseline dir to `target/`; pass `testfly.profile` via `systemPropertyVariables` for surefire too.
- **Effort:** S–M. **Dependencies:** BLD-009. **Regression risk:** medium (test ordering/timing).

#### BLD-017 — P3 — POM hygiene (no enforcer, stale plugins, non-optional extras, no reproducible-build stamp)
- **Classification:** İYİLEŞTİRME ÖNERİSİ.
- **Evidence (`pom.xml`):** `:40-42` `java.version`, `maven.compiler.source/target` AND `<release>21` (`:322`) — redundant, `java.version` unused. No `maven-enforcer-plugin`, no `project.build.outputTimestamp`, no `maven-wrapper` (`ls .mvn` → missing) though README requires Maven 3.8+. Plugin ages: gpg 3.1.0, javadoc 3.6.3, surefire/failsafe 3.2.5, jar 3.3.0, compiler 3.13.0 (dependabot proposes 3.16.0, checkstyle 3.6.0). `jcodec` + `jcodec-javase` 0.2.5 (`:203-211`) are **non-optional** compile deps for every consumer although only `recording/Mp4Encoder.java` uses them (2020 release, unmaintained) — contradicts the "optional so not pulled for TestNG-only users" convention used for the other heavy libs. `slf4j-api` 2.0.16 is forced on all consumers (`:265`) while only `steps/` and `locator/` use it. `junit-jupiter-api` 5.10.2 / `launcher` 1.10.2 and `snakeyaml` 2.2 are not current. `selenium-java` umbrella pulls IE/Safari/Edge drivers, 3 devtools versions and the full OpenTelemetry SDK (~25 jars) for all users.
- **Impact:** Larger/riskier consumer classpath, non-reproducible artifacts, harder upgrades.
- **Fix:** Add enforcer (Java ≥21, Maven ≥3.8, dependencyConvergence, banDuplicatePomDependencyVersions), `outputTimestamp`, Maven wrapper; make jcodec optional (or lazy-load with a clear error like other optional libs); consider depending on `selenium-api/-remote-driver/-chrome-driver/...` instead of the umbrella in a later major.
- **Effort:** M. **Dependencies:** BLD-006. **Regression risk:** medium for the jcodec change (public classpath behaviour; do in a minor with a CHANGELOG note).

#### BLD-018 — P2 — Known advisories on optional/transitive libs that docs tell consumers to add (Gatling/Netty, POI/log4j, Rhino)
- **Classification:** DOĞRULANDI (OSV data); exploitability for TestFly consumers not analysed.
- **Evidence (OSV, versions from `deptree.txt`):** `netty-codec-http2:4.1.119.Final` 8 advisories (incl. `GHSA-93wv-jw9v-4972` HIGH, fixed 4.1.136); `netty-handler:4.1.119.Final` 5 (incl. `GHSA-3qp7-7mw8-wx86` HIGH); `netty-codec-http:4.1.119.Final` 18; `log4j-api:2.24.3` `GHSA-qv9r-c865-cp47` MODERATE (via optional POI 5.4.0); `rhino:1.7.7.2` `GHSA-3w8q-xq97-5j7x` LOW (via swagger-request-validator, optional). All of these arrive via `<optional>` deps (Gatling `gatling-*:3.13.5` `pom.xml:218-247`, POI `:143-148`, swagger validator `:306-311`), so consumers are only affected when they add them — which the docs instruct (with *older* versions: Gatling 3.10.3, POI 5.2.5; BLD-004).
- **Impact:** Load-test users get an old Netty stack; dependabot cannot update docs snippets.
- **Fix:** Bump Gatling to a release with Netty ≥4.1.136 when available (or manage `netty-bom` for the optional group); align docs snippets with the tested versions; keep `scan` in CI (OWASP dependency-check or `osv-scanner` on `pom.xml` weekly).
- **Effort:** M. **Dependencies:** BLD-004, BLD-013. **Regression risk:** medium (Gatling upgrades change API).
- **Repro:** same OSV query as BLD-005 with the listed coordinates.

---

### Group C — Configuration loading & precedence

#### BLD-019 — P1 — Documented profile "deep merge" is not implemented (profile file replaces the base file)
- **Classification:** DOĞRULANDI (reproduced).
- **Root cause:** `ConfigurationLoader.load()` (`config/ConfigurationLoader.java:36-62`) picks a single file name (`testfly.yml` OR `testfly-<profile>.yml`) and parses only that one; there is no base+profile merge anywhere (`grep -rn merge src/main/java/io/testfly/config` → only `testfly.merge` for report-merging).
- **Evidence:** `docs-site/docs/configuration.md:70-86` states: "Profile files **only need to declare the properties they wish to override**. TestFly merges the profile file on top of `testfly.yml`" and lists `testfly-staging.yml`, `testfly-prod.yml`, `testfly-ci.yml`. The repo's own `src/test/resources/testfly-stage.yml` is exactly such a partial file (4 lines: `execution.baseUrl/parallel/threadCount`). Reproduction (scratch `P.java` on `target/classes` + `target/test-classes`):
  ```
  -Dtestfly.profile=stage  ->  FAIL: java.lang.IllegalStateException: Browser configuration must be specified
  ```
  Also `docs/public-api.md:124` promises profile/env overriding of YAML "cleanly".
- **Impact:** Anyone following the docs gets a startup failure (or silently loses base settings if the profile happens to be complete). Core configuration contract is wrong.
- **Fix:** Preferred: implement layered loading (base `testfly.yml` → `testfly-<profile>.yml` deep-merge on the YAML `Map` before binding to `TestFlyConfig`, then `${VAR}` resolution, then validation) and keep `-Dtestfly.config` as full override; add tests using the existing `testfly-stage.yml`. Alternative: correct the docs to "profile file must be complete" (breaking for doc readers but zero code risk).
- **Effort:** M. **Dependencies:** BLD-020, BLD-021. **Regression risk:** medium (existing complete profile files keep working; precedence for lists/maps must be defined and documented).
- **Repro:** see above; or `mvn test -Dtestfly.profile=stage` in a consumer using a partial profile.

#### BLD-020 — P1 — Documented per-key overrides (`-Dbrowser.name=…`, `-Dbrowser.headless=…`, `-Dtestfly.browser.headless`, `TESTFLY_*` env) do nothing
- **Classification:** DOĞRULANDI (reproduced).
- **Root cause:** The only system properties read by the config layer are `testfly.profile`, `testfly.config` (+ `testfly.merge`, shard/quality flags). No generic key→property/env overlay exists. The only env use is `TESTFLY_ENV` in `ReportPortalPropertiesWriter.java:356`.
- **Evidence:** Docs: `docs-site/docs/ci/github-actions.md:121` (`mvn test -B -Dbrowser.name=${{ matrix.browser }}` — a browser *matrix* that always runs the YAML browser), `ci/jenkins.md:95,100`, `gradle.md:183`, `ci/bitbucket-pipelines.md:41` (`-Dtestfly.browser.headless=true`), `loadtest/distributed-docker-k8s.md:54-95` (`TESTFLY_LOAD_VUSERS`, `TESTFLY_API_BASEURL`), `docs/public-api.md:124` (`TESTFLY_BROWSER_NAME`). Reproduction: scratch `O.java` with `-Dbrowser.name=firefox` and `-Dtestfly.config=t.yml` → prints `browser.name after -Dbrowser.name=firefox: chrome`. `grep -rn 'TESTFLY_\|"browser.name"' src/main/java` → only `TestFlyDefaults` (programmatic) and the ReportPortal `TESTFLY_ENV`.
- **Impact:** CI matrices in the documentation silently run the same browser N times and report green — a false-positive pipeline for users. Biggest DX trap in the config area.
- **Fix:** Implement a documented overlay in `ConfigurationLoader`: after YAML bind, apply `-Dtestfly.<path>` and `TESTFLY_<PATH>` (path with `_`→`.`) with type coercion and an "unknown key" warning; document precedence `-D` > env > profile > base > defaults. Or remove the claims from docs and provide `${VAR:-default}` examples (`browser.name: ${BROWSER:-chrome}` already works).
- **Effort:** M. **Dependencies:** BLD-019 (same loader refactor), BLD-021. **Regression risk:** medium (new precedence layer; guard with unit tests).
- **Repro:** `target/audit-scratch/cfg/O.java`.

#### BLD-021 — P2 — `${VAR}` resolution skips nested map/list values; unresolved placeholders are passed through silently; 8+ divergent ad-hoc resolvers
- **Classification:** DOĞRULANDI (reproduced).
- **Root cause:** In `ConfigurationLoader.resolveFields` (`config/ConfigurationLoader.java:180,198`) both the `List` and `Map` branches recurse only when `!isJdkType(value.getClass())`; a nested `LinkedHashMap`/`ArrayList` (every YAML-native nested structure) is a JDK type, so it is skipped. `DotEnvLoader.resolveAll` deliberately leaves unresolved tokens intact (`DotEnvLoader.java:204-207`), and `validate()` never checks for leftovers.
- **Evidence (scratch `T.java`, `browser.capabilities` + `ai.apiKey: ${NOPE_UNSET_VAR}`):**
  ```
  top=/Users/hagul                 <- resolved
  nested={userName=${HOME}}        <- NOT resolved (e.g. bstack:options.userName / accessKey)
  list=[${HOME}]                   <- NOT resolved
  apiKey=${NOPE_UNSET_VAR}         <- unset var accepted; literal sent as credential
  ```
  Separate re-implementations that duplicate (and differ from) `DotEnvLoader`: `driver/BrowserStackProvider.java:127-133` (no `.env`, no `:-default`, no embedded tokens), `db/DbConnectionFactory.java:114`, `email/ImapProvider.java:134`, `MailtrapProvider.java:86`, `OutlookProvider.java:187`, `listeners/TestExecutionListener.java:478`, `ai/AiFailureAnalyzer.java:229`, `junit5/TestFlyExtension.java:583`, plus `startsWith("${")` guards in `ReportPortalAttachmentSender.java:83,165` and `SuiteExecutionListener.java:267`.
- **Impact:** BrowserStack/SauceLabs `capabilities` with nested options (the documented way) keep literal `${…}` → auth failures with misleading errors; a typo in an env var name becomes a runtime 401 instead of a startup error.
- **Fix:** Recurse into any `Map`/`List`/`Iterable` regardless of JDK type, resolve `String` leafs; add a post-resolution validation that collects remaining `${…}` tokens and throws `IllegalStateException("Unresolved placeholders: [path → VAR]")` (opt-out via `${VAR:-}`); remove the per-class resolvers.
- **Effort:** M. **Dependencies:** BLD-019/020 loader refactor. **Regression risk:** medium (configs that currently carry literal `${…}` on purpose would start to fail — gate behind a warning for one minor).
- **Repro:** `target/audit-scratch/cfg/T.java` against `target/classes`.

#### BLD-022 — P2 — `TestFlyDefaults` cannot supply the keys it documents (validation runs before defaults; several keys unimplemented)
- **Classification:** DOĞRULANDI (code order proven; not executed end-to-end).
- **Root cause:** `FrameworkBootstrap.java:45-46` calls `ConfigurationLoader.load()` (which `validate()`s and throws for missing `browser.name`/`timeouts.*`) and only afterwards `TestFlyDefaults.applyMissing(config)`. So `TestFlyDefaults.set("browser.name", …)` / `set("timeouts.explicit", …)` — the headline examples in the Javadoc (`TestFlyDefaults.java:21-23`) — can never rescue a config that omits them. Javadoc also lists `browser.headless` and `retry.enabled`, but `applyBrowserDefaults`/`applyRetryDefaults` implement neither. Casts like `(Integer) overrides.get(...)` throw `ClassCastException` for `Long`/`String` values. `BrowserMatrixListener.java:41` reloads config via `ConfigurationLoader.load()` without `applyMissing`/`applyCiOverrides`, and swallows errors (`catch (Exception e) { return; }`), so a broken config silently disables the browser matrix.
- **Impact:** Org-wide default JARs (the documented use case) fail at startup; matrix silently ignored on config errors.
- **Fix:** Apply defaults to the raw bean *before* validation (move `validate` out of `load()` into a `ConfigurationLoader.loadAndValidate(defaults)` step); implement or delete the two unimplemented keys; coerce via `Number`; make the matrix listener log the exception. Add tests for "defaults supply required key".
- **Effort:** S–M. **Dependencies:** BLD-019 loader refactor. **Regression risk:** low.
- **Repro:** read `FrameworkBootstrap.java:45-46`, `ConfigurationLoader.java:289+` (`validate`).

#### BLD-023 — P3 — Precedence/semantics surprises in env resolution and loader
- **Classification:** İYİLEŞTİRME ÖNERİSİ (behaviour is intentional & documented; items 3-5 DOĞRULANDI).
- **Evidence:**
  1. `.env` > shell env > `-D` > default (`DotEnvLoader.java` Javadoc, `configuration.md:~42`). This inverts the 12-factor convention: a CI secret exported in the shell is overridden by a stray `.env` in the working directory, and `-DAI_API_KEY=…` can never win over an exported variable.
  2. `DotEnvLoader.parse` publishes every `.env` key (including secrets) into `System.getProperties()` when the shell lacks it (`DotEnvLoader.java:251-253`), where they leak into any dump of system properties, forked JVMs and some report/diagnostic output.
  3. Classpath is checked before the working directory (`ConfigurationLoader.java:48-56`), whereas README/AGENTS say the file lives at the project root: `src/test/resources/testfly.yml` silently shadows root `testfly.yml` (CONTRIBUTING says to create exactly that file for "overrides", but it replaces, see BLD-019). `-Dtestfly.config` ignores `testfly.profile`.
  4. `FrameworkBootstrap.applyCiOverrides` forces `browser.headless=true` whenever CI is detected (`FrameworkBootstrap.java:118-128`) even if the user set `headless: false`; its Javadoc says "unless the user has explicitly configured them" which a primitive `boolean` cannot detect. (`threadCount==1` is likewise treated as "unset".)
  5. Unknown YAML keys are only printed to `System.err` (`LenientPropertyUtils`), with no strict mode — typos in `testfly.yml` do not fail CI.
- **Fix:** Document precedence in one table; consider `-D` > shell > `.env` (or a `dotenv.override` flag); stop publishing `.env` values as system properties (or only for non-secret keys); add `config.strict: true`; track "explicitly set" with wrapper types for CI override.
- **Effort:** S–M. **Dependencies:** BLD-019/020. **Regression risk:** medium for precedence flip (breaking) — do in a minor with CHANGELOG note, or leave as is and document.

---

## 2. Verified OK (no finding)
- **Optional-dependency scoping is sound.** Every optional lib is reached either behind `Class.forName` probes or `NoClassDefFoundError` guards: `client/SchemaValidator.java:62`, `client/OpenApiValidator.java:62`, `api/mock/ApiMockServer.java:112`, `loadtest/internal/GatlingBridge.java:23-26` (probes with `initialize=false`), `junit5/ReportPortalJUnit5Bridge.java:78`, `cucumber/BaseCucumberTest.java:68`, `testdata/TestDataLoader.java:275,299`, `email/EmailProviderFactory.java:29`, and `listeners/TestExecutionListener.java:652` detects Cucumber reflectively. ReportPortal references in `SuiteExecutionListener` are class-name strings. `org.junit`/`io.cucumber` imports are confined to `junit5/` and `cucumber/` packages. (Not executed: a consumer without these libs actually booting TestNG — static review only.)
- Java baseline: `--release 21` (`pom.xml:322`) matches README/CONTRIBUTING/getting-started "Java 21+".
- Central metadata required fields (name, description, url, license, developers, scm) are present in `pom.xml:11-37`; source/javadoc jars and GPG are configured.
- No hard-coded credentials found in tracked config: `git grep` for key/secret patterns (`sk-…`, `AKIA…`, `ghp_…`, `xox…`, `apiKey:` literals) found none outside placeholders; `testfly.yml` uses `${AI_API_KEY}`; `.env` is git-ignored (`.gitignore:68`), `.env.example` holds no values I inspected beyond key names.
- Compile and unit tests green (1361/0/0) per `build-test.md`; this does not prove the issues above are absent.

## 3. Sub-areas NOT audited / limitations
- **Release pipeline end-to-end:** no access to GitHub Actions run history, Central Portal deployment logs or repository secrets; the reason 1.0.5–1.0.7 are unpublished (BLD-001) is unknown. `mvn deploy`/`verify` were not run (they sign/upload).
- **JDK > 21 behaviour** (BLD-008): only JDK 21.0.12 installed.
- **`quality` profile, javadoc, `real-backends` failsafe run, Gradle consumer build:** not executed (needs network/plugins, would write signed artifacts or require backends/API keys). Findings BLD-010, BLD-015 are static-analysis based.
- **Cloudflare Workers docs deployment:** `wrangler.jsonc` exists, but the deploy trigger is external (no workflow in repo); cannot be verified from the repo.
- **Private consumer repo** `testfly-test`: GitHub API returns 404 unauthenticated; may be private.
- **Dependency CVE data:** from OSV.dev at audit time; applicability to TestFly usage not analysed; transitive-of-transitive reachability not analysed. Local build used a private Nexus mirror from `~/.m2/settings.xml` (outside the repo), so resolution behaviour on a clean CI runner was not independently reproduced.
- **Test-quality / flakiness (Area D), API/docs content (Areas A/E):** out of this area's scope except where they intersect (BLD-016).

## 4. Summary table

| ID | Pri | Class | Title |
|---|---|---|---|
| BLD-001 | P0 | DOĞRULANDI | Documented version 1.0.7 (and 1.0.5/1.0.6) not on Maven Central; latest is 1.0.4 |
| BLD-002 | P1 | DOĞRULANDI | Non-existent groupId `io.testfly` in docs/AGENTS/CONTRIBUTING (EN+TR) |
| BLD-003 | P1 | DOĞRULANDI | Version strings inconsistent across pom/README/AGENTS/CHANGELOG/docs-site (full table) |
| BLD-004 | P2 | DOĞRULANDI | Dependency/tool/process facts in AGENTS/docs drifted from pom & CI |
| BLD-005 | P1 | DOĞRULANDI | jackson-databind 2.21.6 has 2 HIGH advisories (compile scope) |
| BLD-006 | P2 | DOĞRULANDI / GÜÇLÜ ŞÜPHE | Guava 30.1 / ByteBuddy 1.14.12 override Selenium's; guice misdeclared optional |
| BLD-007 | P2 | GÜÇLÜ ŞÜPHE | Jackson module skew (2.14–2.17 vs 2.21.6) |
| BLD-008 | P2 | GÜÇLÜ ŞÜPHE | Dynamic agent loading / experimental flag; JDK 24+/25 untested |
| BLD-009 | P2 | DOĞRULANDI | JaCoCo gate inactive (argLine override) |
| BLD-010 | P2 | GÜÇLÜ ŞÜPHE | GPG signing in default `verify`; `mvn verify` paths fail without key |
| BLD-011 | P2 | DOĞRULANDI | release.yml: injection, unguarded tag trigger, no pom↔tag check, double test run |
| BLD-012 | P2 | DOĞRULANDI | CI integration/consumer jobs pass while skipped; consumer repo 404 |
| BLD-013 | P2 | DOĞRULANDI / İYİLEŞTİRME | CI lacks docs build, quality profile, JDK matrix, pinned actions |
| BLD-014 | P3 | DOĞRULANDI | Jenkinsfile/docs JDK, branch and downstream-trigger issues |
| BLD-015 | P2 | GÜÇLÜ ŞÜPHE | `quality` profile broken/ineffective for Java 21; never run |
| BLD-016 | P3 | İYİLEŞTİRME | Surefire parallel/fork config, profile comments, property propagation |
| BLD-017 | P3 | İYİLEŞTİRME | POM hygiene: enforcer, plugin ages, non-optional jcodec/slf4j, reproducible build |
| BLD-018 | P2 | DOĞRULANDI | Advisories on Netty/log4j/Rhino via optional deps that docs recommend (old versions) |
| BLD-019 | P1 | DOĞRULANDI | Profile "deep merge" documented but not implemented |
| BLD-020 | P1 | DOĞRULANDI | `-Dbrowser.name`, `-Dtestfly.browser.headless`, `TESTFLY_*` overrides documented but not implemented |
| BLD-021 | P2 | DOĞRULANDI | `${VAR}` not resolved in nested maps/lists; unresolved tokens silent; duplicate resolvers |
| BLD-022 | P2 | DOĞRULANDI | `TestFlyDefaults` applied after validation; documented keys unimplemented |
| BLD-023 | P3 | İYİLEŞTİRME / DOĞRULANDI | `.env` precedence inversion, system-property publishing, classpath-first lookup, CI headless override, no strict mode |

## 5. Suggested ordering (dependencies)
1. BLD-001 (decide/publish) → BLD-002 + BLD-003 + BLD-004 (one EN+TR docs pass, add grep gate).
2. BLD-005 (+ BLD-007 BOM) — dependency bump, re-run `mvn test`.
3. BLD-019 + BLD-020 + BLD-021 + BLD-022 + BLD-023 — one `ConfigurationLoader` refactor with tests (layering, overlay, nested resolution, defaults-before-validate).
4. BLD-009 → BLD-008 → BLD-006 → BLD-016 (test-infra argLine, agent, Guava/ByteBuddy, surefire).
5. BLD-010 → BLD-011 → BLD-012/013/014/015 (release profile, hardened release.yml, CI gates).
6. BLD-017/018 as maintenance.
