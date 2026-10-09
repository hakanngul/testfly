# AREA F — Security, Performance & Maintainability (TestFly 1.0.7)

Auditor scope: `src/main/java` (246 files, ~38k LOC), `src/main/resources/report-template.html`, `pom.xml`, `.github/workflows/*`, `ci/Jenkinsfile`, `.gitignore`, tracked config/test resources.
Branch audited: `chore/docs-cloudflare-workers`. Repo state was not modified (`git status --porcelain` identical before/after; only pre-existing `.agents/memories/*` diffs). All scratch/PoC files live in `target/audit-scratch/` (`secpoc/P.java`, `secpoc/Y.java`, `secpoc/R.java`, `area-f-unref.txt`).

## 0. READ THIS FIRST — limits of the audit

- **NO live dependency scanner ran.** No OWASP Dependency-Check, OSV-Scanner, Trivy, Grype, `mvn dependency-check`, `npm audit` or network CVE lookup was run or installed (none are present on the machine). Section 4 (SEC-016) is **reasoning over `deptree.txt` from memory of public advisories**. Every CVE statement there is a *lead to verify*, not a confirmed result. Remember: absence of a CVE in this report is not evidence of absence.
- Secrets handling: `.env` was read **only to list key names** (`AI_API_KEY`, `AI_API_KEY2`, `REPORTPORTAL_API_KEY`, `REPORTPORTAL_ENDPOINT`); no values are reproduced. `~/.m2/settings.xml` exists but was deliberately **not opened**.
- Findings marked DOĞRULANDI were confirmed by reading code **and**, where noted, by a runnable PoC / bytecode inspection. "Code-read only" means the behaviour was derived from source, not executed against the real service.

Classification legend: **DOĞRULANDI** (verified) · **GÜÇLÜ ŞÜPHE** (strong suspicion, not proven) · **İYİLEŞTİRME ÖNERİSİ** (improvement). Priority P0–P3 per brief. Effort S/M/L.

## 1. Summary table

| ID | Title | Class | Pri | Effort |
|---|---|---|---|---|
| SEC-001 | HTML report data block breakable with `</script>` (stored XSS in report artifact) | DOĞRULANDI (PoC) | **P1** | S |
| SEC-002 | Template placeholders re-substituted inside embedded data (report data corruption) | DOĞRULANDI (PoC) | P2 | S |
| SEC-003 | IMAP over SSL does not verify server identity (jakarta.mail default `false`); no STARTTLS path | DOĞRULANDI (bytecode) | P2 | S |
| SEC-004 | `ApiClient.withCookies()` jar is not host-scoped and lives for the thread | DOĞRULANDI (code) | P2 | M |
| SEC-005 | API logging leaks secrets: query-string credentials, bodies, curl body, narrow header mask list | DOĞRULANDI (code) | P2 | M |
| SEC-006 | AI features ship error text, URLs, DOM and source code to third-party LLM with no redaction; plain-HTTP allowed | DOĞRULANDI (code) | P2 | M |
| SEC-007 | WireMock `ApiMockServer` binds all interfaces with admin API, no auth | DOĞRULANDI (code) / runtime not run | P2 | S |
| SEC-008 | Release workflow: tag-push auto-publishes to Central; `${{ inputs.version }}` shell interpolation; mutable action tags | DOĞRULANDI (code) | P2 | M |
| SEC-009 | No dependency/code scanning, no SBOM; Dependabot omits `github-actions`; `-Pquality` profile never run in CI | DOĞRULANDI | P2 | M |
| SEC-010 | Secret plumbing: `.env` values published as JVM system properties; unresolved `${VAR}` silently used as literal credential | DOĞRULANDI (code) | P3 | S |
| SEC-011 | URLs/CI metadata persisted into reports without userinfo stripping (Jenkins `GIT_URL`, `gridUrl`, `baseUrl`) | GÜÇLÜ ŞÜPHE | P3 | S |
| SEC-012 | Chrome/Edge `--no-sandbox` auto-added in any container | DOĞRULANDI (behaviour) | P3 | S |
| SEC-013 | `DbClient.assertRowCount(table, where, n)` concatenates raw SQL; `db:` test-data runs arbitrary SQL | DOĞRULANDI (by design) | P3 | S |
| SEC-014 | Destructive `clear()` on real IMAP/Outlook mailboxes with no guard | DOĞRULANDI (code) | P3 | S |
| SEC-015 | TestRail/Xray: credentials over any scheme, no timeouts, response bodies (possibly tokens) in exceptions, unredacted stack traces pushed externally | DOĞRULANDI (code) | P3 | S |
| SEC-016 | Dependency CVE reasoning (NO scanner ran) | GÜÇLÜ ŞÜPHE | P2 | M |
| SEC-017 | OAuth2/auth helpers: cache key ignores secret, separate HttpClient ignoring trust config, fake `digest()`, error bodies echoed | DOĞRULANDI (code) | P3 | S |
| SEC-018 | Hand-built JSON in AI/TestRail/Xray clients (model name and control chars unescaped) | DOĞRULANDI (code) | P3 | S |
| SEC-019 | `ReportPortalPropertiesWriter.writeToFile` writes API key in clear with default permissions; keys also set as system properties | DOĞRULANDI (code) | P3 | S |
| SEC-020 | Multipart builder does not escape field/file names (header injection) | DOĞRULANDI (code) | P3 | S |
| SEC-021 | `trustAll` semantics weaker than the warning text implies; warns once per scope only | İYİLEŞTİRME ÖNERİSİ | P3 | S |
| PERF-001 | Base64 screenshots/recordings are held in heap, copied into every metrics-history file and re-read/re-embedded each run | DOĞRULANDI (mechanism) | P2 | M |
| PERF-002 | `reports/testfly-report-*.html` archive is never rotated | DOĞRULANDI | P2 | S |
| PERF-003 | HTTP client hygiene: new `HttpClient` per AI call; no request timeouts on email/TestRail/Xray | DOĞRULANDI | P2 | S |
| PERF-004 | Load engine stores two boxed `double[]` per request in synchronized lists; forked Gatling has no timeout | DOĞRULANDI | P3 | M |
| PERF-005 | `DomPruner` calls `getComputedStyle` on every element and mutates live DOM | DOĞRULANDI (code) | P3 | S |
| PERF-006 | `assertBodyMatches` uses `matches("(?s).*"+regex+".*")` (quadratic / ReDoS-prone) | DOĞRULANDI | P3 | S |
| PERF-007 | Hard-coded `target/...` output dirs ignore `testfly.reports.dir`; screenshot copy leaves a temp file | DOĞRULANDI | P3 | S |
| PERF-008 | ThreadLocals without `remove()` in 8 classes | GÜÇLÜ ŞÜPHE | P3 | S |
| MAINT-001 | Five divergent copies of `${VAR}` resolution with opposite precedence | DOĞRULANDI | P2 | S |
| MAINT-002 | Hand-rolled JSON parsers/builders although Jackson is a core dependency | DOĞRULANDI | P3 | M |
| MAINT-003 | Three logging mechanisms (156 `System.out/err`, JUL in 25 files, SLF4J without binding) | DOĞRULANDI | P3 | M |
| MAINT-004 | God classes / static global state (TestFlyConfig 2473 LOC, ApiClient, Locator, ExecutionMetrics, 3 shutdown hooks) | DOĞRULANDI | P3 | L |
| MAINT-005 | Dead/unused code: 3 exception classes, `writeToFile`, deprecated `SessionCache`, 17 swallowed catches | DOĞRULANDI | P3 | S |
| MAINT-006 | `.testfly/healed-locators.json` tracked while sibling cache files are git-ignored | DOĞRULANDI | P3 | S |
| MAINT-007 | JUnit XML `escapeXml` does not strip XML-illegal control characters | DOĞRULANDI (code) | P3 | S |

No P0 found. Single P1: SEC-001.

## 2. Positive / refuted checks (what was tried and found OK)

- **SnakeYAML 2.2 is safe as used.** `new Yaml()` (`QuarantineLoader.java:238`, `TestDataLoader.java:423`) and `new Constructor(TestFlyConfig.class, new LoaderOptions())` (`ConfigurationLoader.java:81-85`). PoC `secpoc/Y.java` loading `!!javax.script.ScriptEngineManager [...]` → `ComposerException: Global tag is not allowed`; a 60-level alias bomb → `YAMLException` (alias limit). The CVE-2022-1471 class of issue does not apply on 2.x defaults.
- **No hardcoded secrets in tracked files.** Regex sweep (`sk-…`, `AKIA…`, `ghp_…`, `xox*-`, `AIza…`, PEM private-key headers, JWT-shaped strings, `password|secret|apiKey|token = <literal>`) over `git grep` of the working tree returned nothing real. `testfly.yml` uses `${AI_API_KEY}`. `.env` is ignored (`.gitignore:68 *.env`) and untracked; `git log --all --diff-filter=A` shows no `.env`, `settings.xml`, `.pem/.jks/.p12`, `.mcp.json` ever added. **Limit:** history was checked by filename and one `-S` probe, not a full-content history scan.
- **JDK `HttpClient` redirects drop `Authorization`/`Cookie` cross-host** (PoC `secpoc/R.java`, JDK 21.0.12 → target saw `null` for both). The suspicion that `followRedirects(NORMAL)` (`ApiTransport.java:72`) forwards credentials is **refuted**.
- Process execution: only one site (`GatlingEngine.java:153`), argument-list form (no shell), run-description sanitized with `[^a-zA-Z0-9_-]`. No `Runtime.exec`, no `ObjectInputStream`, no Jackson default typing, no script engines.
- SQL: `DbClient` validates identifiers (`SAFE_IDENTIFIER`) and uses `PreparedStatement` for values (except SEC-013).
- Path handling: recording ids sanitized (`RecordingManager.java:154`), screenshot names sanitized (`ScreenshotManager.sanitize`), trace file names sanitized; `TestDataLoader` only reads via `ClassLoader.getResourceAsStream` (JDK blocks `..` escape for directory classpath entries). `TraceRecorder` uses `className` unsanitized in the path, but it comes from `Class.getSimpleName()` / a Cucumber **file name** (`CucumberHooks.featureTitle`), not attacker data → no traversal found.
- Report JS side is defensive: renders via `textContent`, has `isSafeReportPath`, `isSafeUrl`, `sanitizeMediaSrc` (`report-template.html` ~2236-2262). The weakness is server-side embedding (SEC-001).
- Defaults are safe: `api.logBody=false`, `logCurl=false` (`TestFlyConfig.java:1830-1835`); `Authorization`, `Cookie`, `X-Api-Key` masked; `ai.failureAnalysis=false`; patch generator writes `target/remediations/*.patch` and never applies it.
- Gemini provider validates the model name and sends the key in a header (not in the URL). TLS `trustAll` keeps HTTPS identity checking (see SEC-021 for caveat).
- GitHub Actions: `permissions: contents: read` (+`checks: write` for CI), fork PRs do not receive secrets, real-backend job skips when `AI_API_KEY` empty.

---

## 3. Security findings

### SEC-001 — HTML report data block can be broken out of with `</script>` (stored XSS)
- **Class / Priority:** DOĞRULANDI (PoC) · **P1** · Effort S
- **Problem & root cause:** `HtmlReportGenerator.buildHtml` substitutes the Jackson-serialized report JSON verbatim into `<script id="testfly-data" type="application/json">{{TESTFLY_DATA_JSON}}</script>`. Jackson does not escape `<`, `/` or `>`. Any string in the data that contains `</script>` (test/step names, Selenium error messages that quote page HTML, console errors, page titles, API response snippets in `ApiException`, AI analysis text, CI branch/commit message) terminates the block and the remainder is parsed as HTML in the report origin. Report is a shared artifact (CI upload, Slack/Teams links, archived copies), so this is stored XSS against whoever opens it.
- **Where:** `src/main/java/io/testfly/reporting/HtmlReportGenerator.java:507` (`.replace("{{TESTFLY_DATA_JSON}}", reportDataJson)`); `src/main/resources/report-template.html:1735`. Also the same `reportDataJson` is written to `testfly-report-data.json` (safe as plain JSON, not the problem).
- **Evidence:** `target/audit-scratch/secpoc/P.java` (Jackson 2.21.6 from the project tree) output:
  `<script id="testfly-data" type="application/json">{"errorMessage":"Unable to locate <div></script><img src=x onerror=alert(1)>"}</script>`
  The attacker-controlled text is emitted raw inside the script element. Client-side sanitizers in the template do not help because the break-out happens at HTML-parse time, before any JS runs.
- **Impact:** Arbitrary JS in the report page (read other tests' data, embedded base64 screenshots/videos, history, exfiltrate via `fetch`); also a plain robustness bug — a legitimate error message containing `</script>` blanks the whole report.
- **Fix:** Escape the embedded copy only: replace `<` with `\u003c` (and `\u2028`/`\u2029`) in the JSON string before substitution, e.g. `reportDataJson.replace("<", "\\u003c")`; or configure the mapper with a `CharacterEscapes` that escapes `<`, `>`, `&`. Apply to `RUN_HISTORY_JSON` too. Add a unit test with `</script><img onerror>` in an error message and assert the generated HTML has exactly one closing tag for the data block.
- **Dependencies / regression risk:** None external. The template does `JSON.parse(textContent)`; `\u003c` decodes transparently. Low risk.
- **Reproduce:** `java -cp <jackson-databind-2.21.6>:<core 2.21.6>:<annotations 2.21> target/audit-scratch/secpoc/P.java`; or fail a test with message `x</script><img src=x onerror=alert(1)>` and open `target/testfly-report.html`.

### SEC-002 — Report template placeholders re-substituted inside the already-embedded data
- **Class / Priority:** DOĞRULANDI (PoC) · P2 · Effort S
- **Problem:** `buildHtml` chains ~20 `String.replace` calls; the data JSON is inserted first, then later replacements (`{{PASSED}}`, `{{FAILED}}`, `{{TOTAL_TESTS}}`, `{{METADATA}}`, `{{ROWS}}` …) run over the whole string, **including the data**. Any test text containing a `{{…}}` token is silently rewritten (and `{{METADATA}}` re-injects raw HTML into the data block).
- **Where:** `HtmlReportGenerator.java:507-530`.
- **Evidence:** PoC second line: error message `expected {{PASSED}} items` came out as `expected 42 items`.
- **Impact:** Silent corruption of report content; with `{{METADATA}}` also compounds SEC-001.
- **Fix:** Substitute all small placeholders first and insert `{{TESTFLY_DATA_JSON}}` **last**, or do a single-pass `Matcher.appendReplacement` over the template.
- **Deps/risk:** Combine with SEC-001 in one change; low risk. **Repro:** as above (`secpoc/P.java`).

### SEC-003 — IMAP SSL connection does not verify server identity
- **Class / Priority:** DOĞRULANDI (bytecode) · P2 · Effort S
- **Problem:** `ImapProvider.openStore` only sets `mail.imap.ssl.enable=true`. In `com.sun.mail:jakarta.mail:2.0.2` (the resolved version) `SocketFetcher` reads `<prefix>.ssl.checkserveridentity` with default **`false`** (`javap -c`: `PropUtil.getBooleanProperty(props, prefix+".ssl.checkserveridentity", false)`). Certificate chain is validated but the host name is not, so any valid certificate (for any domain) is accepted → MITM can read the mailbox password and OTP/reset-link e-mails. When `ssl: false` the code uses plain `imap` with no STARTTLS option, sending credentials in clear.
- **Where:** `src/main/java/io/testfly/email/ImapProvider.java:83-93` (`openStore`).
- **Evidence:** bytecode check described above (`jakarta.mail-2.0.2.jar`, class `com.sun.mail.util.SocketFetcher`, constant `.ssl.checkserveridentity` followed by `iconst_0`).
- **Impact:** Credential and e-mail-content exposure on untrusted networks (CI runners on shared networks, VPN-less cloud). Test mailboxes often hold real reset/OTP links.
- **Fix:** Set `mail.imap.ssl.checkserveridentity=true`; add `mail.imap.starttls.enable=true` + `starttls.required` when `ssl=false` unless the user opts out; add optional `ssl.trust` for self-signed internal servers rather than disabling checks.
- **Deps/risk:** Users with self-signed/IP-based IMAP hosts will start to fail; mitigate with config key `email.imap.trust`. Add changelog note.
- **Reproduce:** `javap -c -p -cp <extracted jar> com.sun.mail.util.SocketFetcher | grep -B6 -A4 checkserveridentity`.

### SEC-004 — `ApiClient.withCookies()` cookie jar is not host-scoped and persists for the thread
- **Class / Priority:** DOĞRULANDI (code) · P2 · Effort M
- **Problem:** `COOKIE_JAR` is a `ThreadLocal<Map<String,String>>` keyed only by cookie name. `captureCookies` stores every `Set-Cookie` name/value from any response (ignoring Domain, Path, Secure, HttpOnly, SameSite, Expires, Max-Age deletion). `transport()` then attaches **all** cookies to **every** later request on that thread, including absolute URLs to different hosts. The jar is only cleared by an explicit `clearCookies()`; on pooled TestNG threads it leaks between tests.
- **Where:** `ApiClient.java:76` (jar), `:615-619` (header injection), `:805-815` (`captureCookies`), `:103-107` (`clearCookies`).
- **Impact:** Session/auth cookies from service A sent to service B (credential leakage to a third-party mock/CDN/redirect target); cross-test state leakage producing false passes/failures in parallel runs. Cookies that the server expires are never removed.
- **Fix:** Use `java.net.CookieManager`/`CookieStore` (JDK) on the `HttpClient` per test scope (`HttpClient.Builder.cookieHandler`) so RFC 6265 matching is done for free; clear the jar in test-end hook (`ApiExecution` scope close).
- **Deps/risk:** Behavioural change for users relying on cross-host cookie sharing (rare, arguably a bug). Needs test with two WireMock hosts.
- **Reproduce:** `withCookies()` call to host A that sets `sid=…`, then call host B in same thread; inspect request headers on B (use `logCurl`).

### SEC-005 — API logging leaks secrets (query string, bodies, curl body, narrow mask list)
- **Class / Priority:** DOĞRULANDI (code) · P2 · Effort M
- **Problem (several parts):**
  1. `ApiAuth.apiKeyQuery` puts the key into the **query string** (`applyToClient` → `queryParam`, `ApiClient.java:522`). Every log line prints the full URL unmasked: `[API] Exchange … sent.uri()` (`:641-643`), `logStep` header line (`:832`), error line (`:604`), `ApiException` message (`ApiException.java:22,38`), and `ApiResponse.requestUrl()`.
  2. `toCurl` masks headers only; `--data-binary` prints the raw request body (passwords, tokens in JSON) (`:855-857`).
  3. With `api.logBody=true` the **response** body is logged unredacted (tokens returned by `/login`) (`:840-846`).
  4. Default mask list is only `Authorization`, `Cookie`, `X-Api-Key` (`:821`); `Set-Cookie`, `Proxy-Authorization`, `X-Auth-Token`, `X-CSRF-Token`, `X-Amz-Security-Token` are not masked; matching is by exact header name.
  5. These strings flow to StepLogger → metrics JSON → HTML report → ReportPortal/Allure/Slack and, via exception messages, to AI prompts and TestRail/Xray comments (see SEC-006, SEC-015).
- **Evidence:** code lines above; defaults safe (`logBody=false`) so exposure requires opt-in or query-style auth.
- **Impact:** Credentials persisted in CI logs and shared reports.
- **Fix:** Central `Redactor` (query-param names `api_key|apikey|token|access_token|key|password|secret|sig`, header list incl. above, JSON field names) applied in `logStep`, `toCurl`, exception message builders; allow `api.maskedQueryParams` / `api.maskedBodyFields` config; mask `userinfo` in URLs.
- **Deps/risk:** Changes log output (docs/tests asserting log text). Medium regression risk in `ApiClient` unit tests.
- **Reproduce:** `ApiClient.get("/x").auth(ApiAuth.apiKeyQuery("api_key","S3CR3T"))` against any server and read StepLogger output / `target/testfly-metrics.json`.

### SEC-006 — AI features send sensitive data to third-party LLMs without redaction; plain HTTP permitted
- **Class / Priority:** DOĞRULANDI (code) · P2 · Effort M
- **Problem:**
  - `AiFailureAnalyzer.buildPrompt` sends test id, class, page URL, page title, error message, first 30 stack lines, and **every step name** (`AiFailureAnalyzer.java:~118-160`). Step names include API URLs (SEC-005) and any user text.
  - `AiHealingEngine` / `ActionCompiler` send a pruned page DOM (`DomPruner`) — hidden CSRF tokens, pre-filled `value=` attributes, PII, account names remain; only script/style/svg/comments are stripped (`DomPruner.java:~123-150`).
  - `RemediationPatchGenerator` / `SourceCodeLocator` send ±10 lines of **local source code** to the provider (`SourceCodeLocator.java:~140-170`).
  - Any `baseUrl` is accepted, including `http://`; the bearer key goes over cleartext (`OpenAiCompatibleProvider.buildEndpointUrl`). `ClaudeProvider` sends the key in **both** `x-api-key` and `Authorization: Bearer` (`ClaudeProvider.java:62-63`) so a proxy base URL receives it in two forms.
  - `ai.enabled` defaults to `true` (`TestFlyConfig.java:1105`) — safe only because `failureAnalysis`/`generatePatch` default `false` and an API key is required.
- **Impact:** Unintentional disclosure of customer data/secrets/source to an external vendor; compliance exposure.
- **Fix:** Redaction pass shared with SEC-005; strip `input[type=hidden]`, `value`, `data-*token*`, `<meta name=csrf>` in `DomPruner`; refuse non-HTTPS `baseUrl` unless host is loopback or `ai.allowInsecureHttp=true`; one auth header per provider; document data-egress in `ai` docs and require explicit `ai.sendSourceCode: true` for patch generation.
- **Deps/risk:** Slightly less context → possibly lower analysis quality. Config change needs docs (EN/TR).
- **Reproduce:** Enable `ai.failureAnalysis`, point `baseUrl` at a local echo server, fail a test that logged an API call with a query token; inspect the received JSON.

### SEC-007 — `ApiMockServer` (WireMock) listens on all interfaces with an open admin API
- **Class / Priority:** DOĞRULANDI (code); runtime bind address not executed · P2 · Effort S
- **Problem:** `new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort())` / `.port(port)` with no `bindAddress("127.0.0.1")`. WireMock's default is to bind all interfaces and expose `/__admin` (stub create/delete, request journal). On shared CI agents or developer laptops on open Wi-Fi, other hosts can read recorded requests (which can include auth headers) or inject stubs that alter test outcomes.
- **Where:** `src/main/java/io/testfly/api/mock/ApiMockServer.java:34,46`.
- **Fix:** `.bindAddress("127.0.0.1")` by default; opt-in config for container networking; optionally `.disableRequestJournal()` when not asserting.
- **Deps/risk:** Docker-in-Docker setups where the browser/app container must reach the mock need the opt-in; document it. **Repro:** start mock, `curl http://<lan-ip>:<port>/__admin/mappings` from another host.

### SEC-008 — Release workflow hardening
- **Class / Priority:** DOĞRULANDI (code) · P2 · Effort M
- **Problems:**
  1. Any `v*` tag push triggers `mvn deploy` with `autoPublish=true` and `waitUntil=published` (`pom.xml` central plugin config; `release.yml` `on.push.tags`). Maven Central releases are **irreversible**. No GitHub `environment:` with required reviewers, no check that the tag is reachable from `main`, no test gate beyond `mvn verify -Dgpg.skip=true` in the same job.
  2. `release.yml` interpolates `${{ inputs.version }}` directly into a `run:` script (`VERSION="${{ inputs.version }}"`) → script injection by anyone with `workflow_dispatch` rights (write access). Pass via `env:` and validate `^[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9.]+)?$`.
  3. Actions are pinned to mutable tags (`actions/checkout@v5`, `actions/setup-java@v5`, `actions/upload-artifact@v5`, third-party `dorny/test-reporter@v1` with `checks: write`). GPG private key and Central credentials are available to the job that also runs project tests/build plugins.
  4. CI job `framework-integration-tests` exposes `AI_API_KEY` / `REPORTPORTAL_API_KEY` to code from `pull_request` runs of same-repo branches (fork PRs are safe).
- **Fix:** protected `environment: release` with reviewers holding the secrets; `if: github.event.base_ref == 'refs/heads/main'` guard; env-var + regex validation for version; SHA-pin third-party actions; split build/test (no secrets) and publish (secrets) jobs; keep `autoPublish` only behind the approval.
- **Deps/risk:** Process change for maintainers. No library-code risk.

### SEC-009 — No supply-chain / static-analysis gates
- **Class / Priority:** DOĞRULANDI · P2 · Effort M
- **Evidence:** `grep` for `dependency-check|codeql|cyclonedx|sbom|Pquality` in `.github/`, `ci/`, `pom.xml` → only the doc comment at `pom.xml:581`. `.github/dependabot.yml` covers `maven` and `npm` only (no `github-actions`). SpotBugs/Checkstyle/PMD exist only in the opt-in `quality` profile and are not invoked by CI or Jenkins. No SBOM is produced for a library published to Central.
- **Fix:** add `github-actions` ecosystem to Dependabot; enable CodeQL (java + javascript) or OSV-Scanner in CI; run `mvn verify -Pquality` (or SpotBugs at least) as a non-blocking then blocking job; add `cyclonedx-maven-plugin` for an SBOM; add `maven-enforcer-plugin` with `dependencyConvergence` (see SEC-016 mixed Jackson versions).
- **Deps/risk:** First runs will produce a backlog (expect noise from `google_checks.xml` on this codebase); start as report-only.

### SEC-010 — Secret plumbing in `DotEnvLoader` / placeholder resolution
- **Class / Priority:** DOĞRULANDI (code) · P3 · Effort S
- **Problem:**
  1. `.env` values are copied into JVM **system properties** when the shell does not define them (`DotEnvLoader.java:250-253`). Every dependency/agent in the JVM (and anything dumping `System.getProperties()`, e.g. diagnostics, ReportPortal agent) can read them; surefire forks may print them in `-D` debug output.
  2. `.env` deliberately **wins over the shell** (class Javadoc lines 17-24; `resolve`/`resolveAll`). In CI where secrets are injected as env vars a stale committed/artifact `.env` silently overrides them.
  3. `resolveAll` leaves an unresolved `${VAR}` token untouched (`DotEnvLoader.java:207`); `validate()` does not check for leftovers, so the literal string `${DB_PASS}` is sent as the password/API key and the failure surfaces only as a vendor 401 (and the variable name is echoed in logs).
- **Fix:** do not publish `.env` as system properties (keep private map, expose via accessor); invert precedence (explicit env > `.env`) or make it configurable; fail fast listing unresolved placeholders **by name only** for security-sensitive fields (`apiKey`, `password`, `secret`, `token`, `accessKey`).
- **Deps/risk:** Precedence flip is a behaviour change — call out in changelog. ReportPortal agent reads `rp.*` system properties by design (see SEC-019).

### SEC-011 — Userinfo / tokens in URLs persisted to reports
- **Class / Priority:** GÜÇLÜ ŞÜPHE · P3 · Effort S
- **Problem:** `CiEnvironmentDetector.repository()` returns `GIT_URL` (Jenkins), `CIRCLE_REPOSITORY_URL` (CircleCI) and `CI_REPOSITORY_URL` (default branch) verbatim (`CiEnvironmentDetector.java:205-214`); these can contain `user:token@` when the CI job checks out with credentialed URLs. They are stored in `report.put("ci", …)` (`ExecutionMetrics.java:511-514`), JUnit XML properties, and the HTML "Repository" meta item. `execution.gridUrl` is shown in the "Grid URL" meta item (`HtmlReportGenerator.java:357`) and echoed in `RemoteDriverProvider.java:83` error text; secured grids/Selenoid commonly use `http://user:pass@host`.
- **Why not DOĞRULANDI:** requires a specific CI/URL configuration; not reproduced.
- **Fix:** utility `UrlRedactor.stripUserInfo(String)` applied to all URLs placed into metrics/report/exception messages.

### SEC-012 — `--no-sandbox` enabled automatically in containers
- **Class / Priority:** DOĞRULANDI (behaviour) / İYİLEŞTİRME · P3 · Effort S
- `LocalChromeDriverProvider.java:83`, `LocalEdgeDriverProvider.java:54` add `--no-sandbox` whenever `CiEnvironmentDetector.isContainer()`. Disables Chrome's renderer sandbox for every container run, including when the tests browse untrusted pages.
- **Fix:** opt-in via `browser.noSandbox: true` (default true only when running as root, which is when Chrome actually requires it) and document.

### SEC-013 — Raw SQL in DB helpers
- **Class / Priority:** DOĞRULANDI (by design) · P3 · Effort S
- `DbClient.assertRowCount(String table, String where, int expected)` concatenates `where` into the statement (`DbClient.java:183-186`); `TestDataLoader.loadDb/loadAllDb` run any SQL via `Statement.executeQuery` (`TestDataLoader.java:313-323, 343-354`). Inputs are developer-authored but may come from data files/params. `assertRowExists` failure messages print the whole `conditions` map (can include PII/hashes).
- **Fix:** overload accepting `Object... params` with `?` placeholders and deprecate the raw form; restrict `db:` data sources to `SELECT` (reject `;`, DML/DDL keywords) or run on a read-only connection.

### SEC-014 — Destructive mailbox `clear()`
- **Class / Priority:** DOĞRULANDI (code) · P3 · Effort S
- `ImapProvider.clear()` flags **all** messages `DELETED` and expunges (`ImapProvider.java:~64-77`); `OutlookProvider.clear()` deletes up to 500 messages of the configured mailbox via Graph (`OutlookProvider.java:~88-108`, which also calls `fetchAll()` first needlessly). If a user points the config at a real mailbox, data is irrecoverably lost.
- **Fix:** require `email.allowClear: true` or a configured test folder/label; for IMAP prefer moving to Trash; delete only messages matching the test's criteria/since-timestamp.

### SEC-015 — TestRail / Xray clients
- **Class / Priority:** DOĞRULANDI (code) · P3 · Effort S
- No `HttpRequest.timeout` and no `connectTimeout` (`TestRailClient.java:33-36`, `XrayClient.java:38`) → a stalled endpoint hangs suite teardown. URL scheme not validated (Basic credentials may go over `http://`). Errors include full response bodies (`TestRailClient.java:~70`, `XrayClient.java:84,89,108`); in `XrayClient` the "could not parse auth token" branch prints `response.body()`, which **is** the token. Result comments carry raw error messages/stack traces to the vendor (`TestManagementReporter.java:~156-160`). `buildRunJson` escapes only `"` in `runName` (backslash/newline break JSON). A virtual-thread executor is created per client and never shut down.
- **Fix:** timeouts (connect 10s, request 30s), HTTPS enforcement with explicit opt-out, truncate/omit bodies from exceptions, route comments through the SEC-005 redactor, use Jackson `ObjectNode` for payloads.

### SEC-016 — Dependency CVE reasoning (**NO scanner ran — leads only**)
- **Class / Priority:** GÜÇLÜ ŞÜPHE · P2 (until scanned) · Effort M
- Basis: `target/audit-scratch/deptree.txt` + recollection of public advisories. **Not verified against OSV/NVD.** Run `mvn org.owasp:dependency-check-maven:check` or `osv-scanner --lockfile=pom.xml` / `docs-site/package-lock.json` (`npm audit`) to confirm.
- **Consumer-facing (compile, non-optional) — what end users actually inherit:** `selenium-java 4.48.0` (+ opentelemetry 1.65.0, devtools v150–v152), `testng 7.9.0` (+ webjar jquery 3.7.1), `snakeyaml 2.2`, `jackson-databind 2.21.6` (+core 2.21.6/annotations 2.21), `jcodec 0.2.5`, `slf4j-api 2.0.16`. I know of no critical advisory against these exact versions; `snakeyaml 2.2` is past the 2.0 fix for CVE-2022-1471 (PoC above confirms safe defaults) but is not the newest 2.x; `jcodec 0.2.5` is unmaintained (2019) — supply-chain/maintenance risk rather than a known CVE.
- **Optional (only if the consumer opts in) — leads worth checking:**
  - `netty-* 4.1.119.Final` (via Gatling): I recall advisories fixed in later 4.1.x (HTTP/2 "MadeYouReset" DoS CVE-2025-55163 in `netty-codec-http2`, and others after 4.1.119). **Verify.**
  - `guava 30.1-jre` (via `guice 5.1.0`): CVE-2023-2976 and CVE-2020-8908 (temp-file permissions), fixed in 32.0.0+. Optional, low exposure.
  - `wiremock-standalone 3.5.4`: shades Jetty and other libraries; shaded copies are invisible to normal scanners and lag upstream fixes. Also a `compile`-scope optional dependency of production code (`ApiMockServer`).
  - Swagger validator chain (`swagger-core 1.6.10`, `swagger-parser 1.0.65`, `json-patch 1.13`, `rhino 1.7.7.2`, `joda-time 2.10.5`, `httpclient 4.5.14`, `jackson-datatype-jsr310 2.14.2`, `jackson-datatype-jdk8 2.15.2`) — old parser stack, parses untrusted OpenAPI documents in `OpenApiValidator`.
  - Test scope only: `logback-classic 1.5.8` (I recall a 2024-12 advisory fixed in 1.5.13), `h2 2.2.224`.
- **Inconsistency (verifiable from the tree, not a CVE):** Jackson modules are on mixed versions — `databind/core 2.21.6` with `dataformat-yaml 2.17.1`, `jsr310 2.14.2`, `jdk8 2.15.2` (all optional) → `NoSuchMethodError` risk when optional features are enabled. `byte-buddy 1.14.12` vs `byte-buddy-agent 1.18.12` (test). Fix with `jackson-bom` in `dependencyManagement` and `maven-enforcer` `dependencyConvergence`.
- **Fix:** add the scanners from SEC-009; import `jackson-bom`; upgrade `snakeyaml`, `netty` (via Gatling BOM override), `guava` (managed override), `logback` (test).
- **Regression risk:** Gatling/Netty overrides must be tested with the load-test suite.

### SEC-017 — Auth helper details (`ApiAuth`)
- **Class / Priority:** DOĞRULANDI (code) · P3 · Effort S
- `OAuth2TokenCache` keys are `tokenUrl|clientId` (`ApiAuth.java:149`) and `tokenUrl|clientId|username` (`:168`) — two different secrets/passwords for the same id share a cached token (stale/wrong identity, hides login failures). It uses its own static `HttpClient` (`:143`) that ignores `api.ssl.*`, proxies and timeouts configured for `ApiClient`, and token-endpoint failures throw with `res.body()` (`:229`). `digest()` (`:81`) is a documented placeholder that sends `Authorization: Digest base64(user:pass)` — non-standard and effectively cleartext credentials. `hmac()` signs only the API key string, not the request.
- **Fix:** include a hash of the secret in the cache key; reuse the `ApiTransport` profile; do not echo bodies; remove or implement `digest()`, and mark `hmac()` as non-standard in Javadoc.

### SEC-018 — Hand-built JSON in provider clients
- **Class / Priority:** DOĞRULANDI (code) · P3 · Effort S
- `OpenAiCompatibleProvider.buildRequestBody` interpolates `model` unescaped (`:111-117`); `ClaudeProvider.escapeJson` (`:129`) handles `\ " \n \r \t` only (drops `\r`, leaves other control chars <0x20 such as `\u0000`, `\b`, `\f`, ANSI escapes found in console/stack output) so providers answer HTTP 400 and the analysis is silently lost. Same pattern in `GeminiProvider`, `TestRailClient`, `XrayClient`. Config-controlled values (model, run name) can inject extra JSON members.
- **Fix:** build with Jackson `ObjectNode`/`writeValueAsString` (already a core dependency). Validate model with the Gemini-style regex everywhere.

### SEC-019 — ReportPortal key handling
- **Class / Priority:** DOĞRULANDI (code) · P3 · Effort S
- `ReportPortalPropertiesWriter.writeToFile` (`:114-125`) writes `rp.api.key` in clear text with default umask (typically 0644) and nothing in `src/main` calls it (only a test does). `applyAsSystemProperties`/`reapplyWithRunType` (`:133-155`) publish the key as a JVM system property. `toMaskedString` exists and masks properly.
- **Fix:** delete `writeToFile` or create the file with `PosixFilePermissions` `rw-------` and add the path to `.gitignore`.

### SEC-020 — Multipart header injection
- **Class / Priority:** DOĞRULANDI (code) · P3 · Effort S
- Field names and file names are placed into `Content-Disposition` without escaping `"`, CR, LF (`ApiClient.java:773, 785`); boundary is `TestFlyBoundary + currentTimeMillis` (collision-prone across parallel calls, predictable). Low exploitability (test authors control inputs) but file names taken from data files can contain quotes.
- **Fix:** percent-encode/escape per RFC 7578 §4.2; use `UUID` boundary.

### SEC-021 — `trustAll` semantics
- **Class / Priority:** İYİLEŞTİRME ÖNERİSİ · P3 · Effort S
- `ApiTransport.CertificateTrustOnlyManager` (`:86-110`) pins the presented leaf as the only anchor and requires HTTPS endpoint identification. Good design, but it accepts **any** self-signed cert whose SAN matches the host name, i.e. offers no MITM protection (as intended by "trust all"). The one-time warning text ("hostname verification remains enabled") and `warnTrustAll` (`:116-120`) log once per scope only. No guard prevents `trustAll` with non-local hosts or in CI.
- **Fix:** warn on every profile creation with host list; optionally fail when `ci` detected unless `api.ssl.allowTrustAllInCi: true`.

---

## 4. Performance / resource findings

### PERF-001 — Screenshots/recordings are carried through heap, JSON history and HTML repeatedly
- **Class / Priority:** DOĞRULANDI (mechanism read in code; magnitude not measured) · P2 (escalate to P1 if a large suite shows OOM/slow teardown) · Effort M
- **Chain:** `StepLogger.record` → `ScreenshotManager.captureAsBase64()` (`StepLogger.java:92-94`) → `StepRecord.screenshotBase64` kept in `TestTiming` in static `ExecutionMetrics.TIMINGS` for the whole suite → `timingToMap` copies base64 into every step (`ExecutionMetrics.java:411-413`) → `mapper.writeValue(primary, report)` with `INDENT_OUTPUT` (`:561`) → identical **second full copy** into `metrics-history/testfly-metrics-<ts>.json` (`:570`), retained 30 times by default (`:602-626`) → `HtmlReportGenerator` reads **every** history file fully into a `JsonNode` tree (`:68-84`) and again in `loadRunHistory` (`:414-440`) → then `Files.readAllBytes` + Base64 for each failure screenshot and **each recording MP4/GIF** (`:100-124`, ×1.33 size, as Strings) → the whole payload is built as one `String reportDataJson`, then again inside `html` (template `.replace` chain creates further full copies) and written three times (`testfly-report-data.json`, `testfly-report.html`, archive — PERF-002).
- **Impact:** Heap and disk grow with `tests × steps-with-screenshot × history × runs`; teardown can OOM or take minutes on big suites (screenshot ≈ 100 KB–1 MB base64 each). History copies hold the same images 30×.
- **Fix:** store step screenshots as files under `reports/screenshots/` and keep only a relative path in `StepRecord`/JSON; strip `screenshotBase64`/`recordingBase64` from history copies; read history files with a streaming parser extracting only `testId/status/totalMs/retryCount`; reference videos by `<video src>` relative path instead of data URIs (or cap embed size); free `TestTiming` image data after export.
- **Deps/risk:** Report format/schema change (consumers of metrics JSON, `SmartTestSharder` reads `testClassName` from it), report portability when copying only the HTML. Needs report golden tests.
- **Reproduce:** run a suite with ~200 tests × 10 `step(..., true)`; observe `target/testfly-metrics.json` size and `target/metrics-history/` total, JVM heap at report generation (`-Xmx256m`).

### PERF-002 — Report archive grows without bound
- **Class / Priority:** DOĞRULANDI · P2 · Effort S
- Every run writes `target/reports/testfly-report-<yyyyMMdd-HHmmss>.html` (`HtmlReportGenerator.java:272-283`); only `metrics-history` has rotation (`ExecutionMetrics.rotateHistoryFiles`). With embedded media (PERF-001) each file can be tens/hundreds of MB, on persistent CI workspaces this fills disks. Same-second runs overwrite one another (timestamp precision) in both places.
- **Fix:** apply the same `flakiness.historyRuns` retention to `reports/`; add a configurable `reporting.archive: false` switch.

### PERF-003 — HTTP client hygiene
- **Class / Priority:** DOĞRULANDI · P2 · Effort S
- A new `HttpClient` is built on **every** AI call (`ClaudeProvider.java:59`, `GeminiProvider.java:53`, `OpenAiCompatibleProvider.java:80`); on JDK 21 each client starts a selector thread and is only released by GC (the class is `AutoCloseable` in 21 but never closed). With `healing`/`agent` features this happens per failing locator.
- `HttpClient.newHttpClient()` with no connect timeout and no `HttpRequest.timeout` in `MailhogProvider.java:33`, `MailtrapProvider.java:34`, `OutlookProvider.java:50` (and TestRail/Xray, SEC-015) → indefinite hang.
- `OutlookProvider.fetchAll` returns only the first 100 messages (no `@odata.nextLink` paging); `clear()` calls `fetchAll()` then lists again.
- **Fix:** one shared static `HttpClient` per provider with connect timeout, per-request timeout, and shutdown at suite end.

### PERF-004 — Load-test engine memory/time controls
- **Class / Priority:** DOĞRULANDI · P3 · Effort M
- `JdkLoadEngine.java:103-104` keeps `List<double[]> latencies` and `Map<String,List<double[]>> stepLatencies` as `Collections.synchronizedList` and allocates `new double[]{latencyMs}` per request, twice (`:~160-175`): ~40 bytes + list slot each, plus lock contention from all virtual users. Millions of samples → hundreds of MB. Use `LongAdder` + HDR histogram or a pre-sized primitive ring per user.
- `GatlingEngine.runGatlingForked` calls `process.waitFor()` with no timeout (`GatlingEngine.java:158`) → a stuck fork blocks the suite forever and holds `GATLING_LOCK`.

### PERF-005 — `DomPruner` cost and side effects
- **Class / Priority:** DOĞRULANDI (code) · P3 · Effort S
- JS fast path calls `getComputedStyle` for every element and sets/removes `data-tf-hide` attributes on the **live** DOM (`DomPruner.java:~68-100`): forces style recalculation on large pages and can trigger MutationObservers/framework re-renders in the app under test. The regex stage uses `<script[^>]*>.*?</script>` with DOTALL on the whole string (quadratic on unclosed tags).
- **Fix:** clone first, compute visibility from the clone using `checkVisibility()` or limit to interactive elements; cap input length before regexes.

### PERF-006 — Quadratic regex assertion
- **Class / Priority:** DOĞRULANDI · P3 · Effort S
- `ApiResponse.assertBodyMatches` (`ApiResponse.java:323`) builds `"(?s).*" + regex + ".*"` and calls `String.matches` — leading greedy `.*` backtracks O(n²) on large bodies when no match, and user regex can be catastrophic. Use `Pattern.compile(regex, DOTALL).matcher(body).find()`.

### PERF-007 — Output paths and temp files
- **Class / Priority:** DOĞRULANDI · P3 · Effort S
- `ScreenshotManager.REPORT_DIR = "target/reports/screenshots"` (`:25`), `RecordingManager` (`target/recordings`), `TraceRecorder` (`target/traces`), `HealLog` (`target`), flakiness (`target/flakiness-report.json`) ignore `ReportPaths.baseDir()`/`testfly.reports.dir` (Gradle `build/` layout, dual-engine runs). `getScreenshotAs(OutputType.FILE)` then `Files.copy` (`ScreenshotManager.java:46-50`) leaves Selenium's temp PNG in `java.io.tmpdir` until JVM exit — use `OutputType.BYTES` + `Files.write`.

### PERF-008 — ThreadLocal hygiene
- **Class / Priority:** GÜÇLÜ ŞÜPHE · P3 · Effort S
- Static `ThreadLocal`s with no `remove()` in the same class: `PreconditionSessionCache` (holds cookies + localStorage of logged-in sessions; `clearAll()` clears the map but the entry stays), `BasePage`, `ContextSupport`, `CucumberRetryContext`, `BaseCucumberSteps`, `LoadTestFeeder`, `JdkLoadEngine`, `TestFlyGatlingSimulation`. On pooled/reused threads this retains data and can leak session state between tests. Not verified whether an external owner removes them; check each lifecycle hook.

---

## 5. Maintainability / technical debt

### MAINT-001 — Five divergent `${VAR}` resolvers
- DOĞRULANDI · P2 · S
- `DbConnectionFactory.java:112`, `ImapProvider.java:131`, `MailtrapProvider.java:84`, `OutlookProvider.java:185`, `BrowserStackProvider.java:124` (public, reused by `SauceLabsProvider`) each re-implement whole-string `${VAR}` lookup with **shell env > system property** precedence and no `:-default`; `DotEnvLoader.resolveAll` (used by `ConfigurationLoader`) has **.env > env > sysprop > default**. After `ConfigurationLoader.resolveEnvPlaceholders` they are mostly redundant, but they differ for programmatically built configs and produce inconsistent credentials depending on code path (`AiFailureAnalyzer.resolveApiKey` is a sixth wrapper). Replace with one `Secrets.resolve()` (also the hook for SEC-005/010 redaction).

### MAINT-002 — Hand-rolled JSON
- DOĞRULANDI · P3 · M
- `ClaudeProvider.extractContent`, `GeminiProvider.extractContent`, the string-scanner fallback in `OpenAiCompatibleProvider.extractContent`, `TestRailClient.extractId` (regex `"id"`), `XrayClient.TOKEN_PATTERN`, `escapeJson` ×3, `esc` in Xray. Jackson is already a compile dependency. Replace with `JsonNode` parsing; deletes ~150 lines and fixes SEC-018.

### MAINT-003 — Three logging systems
- DOĞRULANDI · P3 · M
- 156 `System.out/err.print*` calls, `java.util.logging` in 25 files, SLF4J in 3 files (e.g. `StepLogger`). `slf4j-api 2.0.16` is a compile dependency but no binding ships, so on a consumer without Logback/Log4j `StepLogger`'s `LOGGER.info` goes to the NOP logger (SLF4J prints a one-time warning). No single place to apply redaction (SEC-005) or levels. Pick SLF4J (or JUL) and route console output through it.

### MAINT-004 — Oversized classes and static global state
- DOĞRULANDI · P3 · L
- LOC: `TestFlyConfig` 2473 (all nested POJOs; hundreds of getters/setters), `ApiClient` 897 (fluent builder + transport + logging + cookies + 4 ThreadLocals), `Locator` 891, `ExecutionMetrics` 704, `BasePage` 688, `NetworkMock` 669, `TestExecutionListener` 664, `TestFlyExtension` 634, `HtmlReportGenerator` 592. Static `synchronized` in `ApiTransport`, `ConcurrentHashMap`s in `ExecutionMetrics`, three JVM shutdown hooks (`SuiteContext.java:32`, `DriverManager.java:57`, `ApiExecution.java:16`) with undefined relative ordering. `ConfigurationLoader` walks the config tree with reflection and `setAccessible(true)` and swallows `IllegalAccessException`. Split `ApiClient` (builder / transport / logging), move config nested classes into separate files, consolidate shutdown into one ordered hook.

### MAINT-005 — Dead / unused code
- DOĞRULANDI · P3 · S
- `exceptions/ConfigurationException.java`, `DriverException.java`, `ExecutionException.java` have **no reference** in `src/main`, `src/test` or `docs-site/docs` (`target/audit-scratch/area-f-unref.txt`; check they are not part of the published API before deleting). `ReportPortalPropertiesWriter.writeToFile` is used only by its test. `browser/SessionCache` is `@Deprecated(since="1.1.0")` with six deprecated members and `LocatorSupport` has three `forRemoval` methods — AGENTS.md says deprecations live one minor version; 1.0.7 → 1.1.0 housekeeping is pending. 17 empty-ish `catch (...) {}` blocks hide failures (e.g. `HtmlReportGenerator` history parsing `catch (Exception ignored)`, screenshot/recording embedding).

### MAINT-006 — Generated state tracked in git
- DOĞRULANDI · P3 · S
- `.testfly/healed-locators.json` is tracked (added in commit `7f9baf3`) while `.testfly/action-cache.json` and `.testfly/page-knowledge.json` are ignored (`.gitignore:82-83`). The file is written by tests at runtime, so it produces spurious diffs and exposes target-app selectors. Add it to `.gitignore` and `git rm --cached` in a normal commit (do not rewrite history).

### MAINT-007 — JUnit XML can become malformed
- DOĞRULANDI (code) · P3 · S
- `JUnitXmlReporter.escapeXml` (`:163-170`) escapes the five XML entities only; stack traces/messages with ESC (ANSI colours), NUL or other chars outside the XML 1.0 range produce an unparsable report that CI test publishers (`dorny/test-reporter`, Jenkins JUnit) reject. Strip `[^\u0009\u000A\u000D\u0020-\uD7FF\uE000-\uFFFD]` before escaping. Same concern for the Allure adapter.

---

## 6. Groupings / suggested remediation order (Area F only)

1. **Quick, high value (S):** SEC-001 + SEC-002 (one PR in `HtmlReportGenerator`), SEC-003, SEC-007, SEC-010/MAINT-001 (single secrets resolver), PERF-002, PERF-003.
2. **Redaction layer (M):** SEC-005, SEC-006, SEC-011, SEC-015 share one `Redactor`/`UrlRedactor`; SEC-018/MAINT-002 share one Jackson-based JSON helper.
3. **Report data diet (M, riskiest):** PERF-001 (+ MAINT-007); needs golden-file tests on metrics JSON/HTML.
4. **Pipeline/supply chain (M):** SEC-008, SEC-009, SEC-016 (run real scanner first; this report's CVE list is unverified).
5. **Backlog (S/M):** SEC-004, SEC-012–SEC-014, SEC-017, SEC-019–SEC-021, PERF-004–PERF-008, MAINT-003–MAINT-006.

## 7. Sub-areas NOT audited (and why)

- **Live CVE/advisory scan** of Maven and npm trees — no scanner available and installing one was disallowed; `docs-site/package-lock.json` / Docusaurus 3.5.2 chain was not reviewed at all.
- **`~/.m2/settings.xml`, GPG key material, GitHub repository settings** (branch protection, environments, secret scoping, tag protection, Cloudflare/Wrangler deployment credentials) — not accessible/intentionally not read; SEC-008 conclusions are from workflow YAML only.
- **Runtime behaviour against real services** (Outlook Graph, IMAP servers, TestRail, Xray, LLM endpoints, BrowserStack/Sauce) — code-read only; SEC-003 was proven at bytecode level, SEC-007 bind address was not executed.
- **Full-content git history secret scan** — only filename-add and one targeted `-S` search; a dedicated tool (gitleaks/trufflehog) should be run.
- **Concurrency/thread-safety and driver-lifecycle correctness** (listeners, `DriverManager`, `WaitEngine`, sharding) belong to Area A; only the shutdown-hook and ThreadLocal observations are noted here.
- **In-process Gatling path, `network/NetworkMock` CDP interception, `shadow`, `visual`, `clock`, `accessibility` (bundled `axe.min.js` integrity/version)** — skimmed for exec/file/network primitives only; `axe.min.js` version and provenance not checked.
- **Test sources (`src/test`)** — only scanned for credential patterns; quality is Area D.
