# TestFly Load Testing Module — Sprint Plan

> **Status:** Draft
> **Date:** 2026-09-07
> **Target Version:** 1.1.0
> **Total Sprints:** 6
> **Estimated Duration:** 3-4 weeks

---

## Sprint Overview

| Sprint | Focus | Deliverable | Est. |
|--------|-------|-------------|------|
| **1** | Foundation | Config, annotation, BaseLoadTest, support interface | 2-3 days |
| **2** | JDK Engine | Fallback engine, LoadScenario builder, LoadTestRunner | 3-4 days |
| **3** | Gatling Engine | GatlingBridge, GatlingEngine, reflection integration | 3-4 days |
| **4** | Assertions & Metrics | LoadTestAssert, LoadTestMetrics, percentile calculation | 2-3 days |
| **5** | Reporting | LoadTestReportAdapter, HTML tab, ExecutionMetrics integration | 2-3 days |
| **6** | Docs & Examples | Documentation (EN+TR), consumer examples, final polish | 2-3 days |

---

## Sprint 1 — Foundation

**Goal:** Kullanıcı `BaseLoadTest` extend edip `@LoadTest` annotation kullanabilsin. Engine henüz yok — sadece iskelet.

### Tasks

| # | Task | File(s) | Priority |
|---|------|---------|----------|
| 1.1 | `TestFlyConfig.LoadTest` nested class — YAML binding, defaults, duration parsing | `config/TestFlyConfig.java` | P0 |
| 1.2 | `TestFlyDefaults` — loadtest default values | `config/TestFlyDefaults.java` | P0 |
| 1.3 | `@LoadTest` annotation — class + method level, resolution priority | `loadtest/LoadTest.java` | P0 |
| 1.4 | `LoadTestSupport` interface — `load()`, `loadScenario()` default methods | `test/support/LoadTestSupport.java` | P0 |
| 1.5 | `BaseLoadTest` — support interface mixin'leri, `@Listeners` | `loadtest/BaseLoadTest.java` | P0 |
| 1.6 | `LoadTestConfig` — resolved config (annotation + YAML merge) | `loadtest/LoadTestConfig.java` | P0 |
| 1.7 | Unit tests — config binding, annotation resolution, defaults | `unit/loadtest/` | P0 |

### Acceptance Criteria

- [ ] `testfly.yml`'e `loadtest:` block eklendiğinde SnakeYAML bind eder
- [ ] `@LoadTest(users=200)` annotation'ı class ve method level'da çalışır
- [ ] `BaseLoadTest` extend eden bir sınıf compile olur
- [ ] `load("/api/health")` çağrısı `LoadScenario` builder döner (henüz run yok)
- [ ] Config resolution priority: method > class > YAML > defaults
- [ ] Duration parsing: `"30s"`, `"2m"`, `"1h"` → `Duration`
- [ ] Unit testler geçer (`mvn test -Dtest=LoadTestConfigTest`)

### Definition of Done

```java
// Bu kod compile olmalı ve annotation resolve edilmeli:
@LoadTest(users = 200, rampUp = "30s")
public class MyLoadTest extends BaseLoadTest {
    @Test
    public void test() {
        load("/api/health");  // LoadScenario döner, henüz run() yok
    }
}
```

---

## Sprint 2 — JDK Engine (Fallback)

**Goal:** Gatling olmadan çalışan hafif load test engine. `ExecutorService` + JDK `HttpClient`.

### Tasks

| # | Task | File(s) | Priority |
|---|------|---------|----------|
| 2.1 | `LoadTestEngine` interface — `name()`, `isAvailable()`, `execute()` | `loadtest/internal/LoadTestEngine.java` | P0 |
| 2.2 | `LoadScenario` builder — full fluent API (users, rampUp, hold, steps, feed, thinkTime) | `loadtest/LoadScenario.java` | P0 |
| 2.3 | `LoadStep` builder — per-request config (method, path, headers, body, checks, extract) | `loadtest/LoadStep.java` | P0 |
| 2.4 | `JdkLoadEngine` — ExecutorService-based concurrent HTTP simulation | `loadtest/internal/JdkLoadEngine.java` | P0 |
| 2.5 | `LoadTestRunner` — engine selection, config resolution, execution orchestration | `loadtest/LoadTestRunner.java` | P0 |
| 2.6 | `LoadTestFeeder` — CSV, JSON, random, uuid, sequence feeders | `loadtest/LoadTestFeeder.java` | P1 |
| 2.7 | Variable substitution — `${var}` in paths, headers, bodies | `loadtest/internal/VariableResolver.java` | P1 |
| 2.8 | Unit tests — scenario builder, JDK engine (mock HTTP server), feeders | `unit/loadtest/` | P0 |

### Acceptance Criteria

- [ ] `load("/api/health").users(10).hold(Duration.ofSeconds(5)).run()` çalışır
- [ ] JDK engine 10 concurrent user ile mock server'a istek atar
- [ ] Ramp-up phase: users gradually added over `rampUp` duration
- [ ] Hold phase: all users active for `hold` duration
- [ ] Cooldown phase: users gradually removed
- [ ] CSV feeder: `${username}`, `${password}` substitution works
- [ ] Think time: random delay between requests
- [ ] Multi-step scenario: step1 → extract → step2 uses extracted value
- [ ] `LoadTestRunner.lastMetrics()` returns results
- [ ] Unit testler geçer

### Definition of Done

```java
// Bu kod Gatling OLMADAN çalışmalı:
@Test
public void healthCheck() {
    load("/api/health")
        .users(10)
        .rampUp(Duration.ofSeconds(2))
        .hold(Duration.ofSeconds(5))
        .run();  // JdkLoadEngine ile çalışır, metrics döner
}
```

---

## Sprint 3 — Gatling Engine

**Goal:** Gatling classpath'te varsa otomatik olarak Gatling engine kullanılır. Kullanıcı Gatling API'sini görmez.

### Tasks

| # | Task | File(s) | Priority |
|---|------|---------|----------|
| 3.1 | `GatlingBridge` — `Class.forName` probe, `isAvailable()`, error messages | `loadtest/internal/GatlingBridge.java` | P0 |
| 3.2 | `GatlingEngine` — LoadScenario → Gatling Simulation conversion (reflection) | `loadtest/internal/GatlingEngine.java` | P0 |
| 3.3 | Gatling simulation builder — programmatic `Simulation` subclass generation | `loadtest/internal/GatlingSimulationBuilder.java` | P0 |
| 3.4 | Gatling results parser — `simulation.log` → `LoadTestMetrics` conversion | `loadtest/internal/GatlingResultsParser.java` | P0 |
| 3.5 | Engine auto-selection — `auto` mode: Gatling if available, else JDK | `loadtest/LoadTestRunner.java` | P0 |
| 3.6 | pom.xml — Gatling optional dependency | `pom.xml` | P0 |
| 3.7 | Netty version alignment check — Selenium CDP vs Gatling | `pom.xml` | P1 |
| 3.8 | Unit tests — bridge detection, engine selection, results parsing (mocked) | `unit/loadtest/` | P0 |
| 3.9 | Integration test — real Gatling execution against mock server | `integration/loadtest/` | P1 |

### Acceptance Criteria

- [x] Gatling classpath'te yokken `GatlingBridge.isAvailable()` → `false`
- [x] Gatling classpath'te varken `GatlingBridge.isAvailable()` → `true`
- [x] `engine: auto` → Gatling varsa Gatling, yoksa JDK
- [x] `engine: gatling` + Gatling yok → `IllegalStateException` + dependency hint
- [x] `engine: jdk` → her zaman JDK, Gatling olsa bile
- [x] Gatling engine: `LoadScenario` → Gatling `Simulation` dönüşümü çalışır
- [x] Gatling results: `simulation.log` → `LoadTestMetrics` parse edilir
- [x] Percentile values (p50, p95, p99) Gatling'den doğru okunur
- [x] Netty version conflict yok (Selenium CDP + Gatling birlikte çalışır)
- [x] Unit testler geçer

### Definition of Done

```java
// Gatling classpath'te varken bu kod Gatling engine ile çalışmalı:
@Test
@LoadTest(users = 100, rampUp = "10s", hold = "30s")
public void gatlingLoadTest() {
    load("/api/products")
        .run();  // GatlingEngine devrede, kullanıcı bilmiyor
}
```

---

## Sprint 4 — Assertions & Metrics

**Goal:** Fluent assertion API + detaylı metrics collection.

### Tasks

| # | Task | File(s) | Priority |
|---|------|---------|----------|
| 4.1 | `LoadTestMetrics` record — all fields, per-step breakdown, status codes | `loadtest/LoadTestMetrics.java` | P0 |
| 4.2 | `LoadTestAssert` — fluent assertion chain (throughput, latency, error rate) | `loadtest/LoadTestAssert.java` | P0 |
| 4.3 | Per-step assertions — `assertStepP95Below("Login", 200)` | `loadtest/LoadTestAssert.java` | P1 |
| 4.4 | Status code assertions — `assertStatusCodeCount(200, 1000)`, `assertNoStatus(500)` | `loadtest/LoadTestAssert.java` | P1 |
| 4.5 | Percentile calculation — JDK engine için manual p50/p90/p95/p99 | `loadtest/internal/PercentileCalculator.java` | P0 |
| 4.6 | `ExecutionMetrics.recordLoadTest()` — metrics JSON integration | `metrics/ExecutionMetrics.java` | P0 |
| 4.7 | `TestTiming` extension — load test data in per-test timing | `metrics/TestTiming.java` | P1 |
| 4.8 | Unit tests — all assertion methods, percentile calculation, metrics recording | `unit/loadtest/` | P0 |

### Acceptance Criteria

- [x] `assertP95Below(300)` — pass when p95 < 300ms, fail with detail message
- [x] `assertThroughputAbove(1000)` — pass when rps > 1000
- [x] `assertErrorRateBelow(0.01)` — pass when error rate < 1%
- [x] `assertSuccessRateAbove(0.99)` — pass when success rate > 99%
- [x] `assertNoStatus(500)` — fail if any 500 response
- [x] `assertStepP95Below("Login", 200)` — per-step assertion
- [x] Fluent chaining: `.assertP95Below(300).assertErrorRateBelow(0.01)` works
- [x] `metrics()` returns raw `LoadTestMetrics` for custom assertions
- [x] Percentile calculation correct (sorted array, nearest-rank method)
- [x] `testfly-metrics.json` includes `loadTests[]` array
- [x] Unit testler geçer

### Definition of Done

```java
@Test
public void assertionsWork() {
    load("/api/health")
        .users(50)
        .hold(Duration.ofSeconds(10))
        .run()
        .assertThroughputAbove(100)
        .assertP95Below(200)
        .assertErrorRateBelow(0.01)
        .assertNoStatus(500);
}
```

---

## Sprint 5 — Reporting

**Goal:** Load test sonuçları HTML report'ta görünür. Ayrı Load Test tab'ı.

### Tasks

| # | Task | File(s) | Priority |
|---|------|---------|----------|
| 5.1 | `LoadTestReportAdapter` — `ReportAdapter` SPI impl | `loadtest/LoadTestReportAdapter.java` | P0 |
| 5.2 | FrameworkBootstrap registration — config-gated (NotificationAdapter pattern) | `lifecycle/FrameworkBootstrap.java` | P0 |
| 5.3 | HTML report — Load Test tab (throughput chart, latency histogram, error timeline) | `reporting/HtmlReportGenerator.java` | P0 |
| 5.4 | Report template — `report-template.html` load test section | `resources/report-template.html` | P0 |
| 5.5 | Per-step breakdown table in report | `reporting/HtmlReportGenerator.java` | P1 |
| 5.6 | Status code distribution (pie chart) | `reporting/HtmlReportGenerator.java` | P1 |
| 5.7 | Scenario config summary in report (users, rampUp, hold, engine) | `reporting/HtmlReportGenerator.java` | P1 |
| 5.8 | Gatling native report integration — link to Gatling HTML if available | `loadtest/LoadTestReportAdapter.java` | P2 |
| 5.9 | Unit tests — adapter registration, report generation, metrics JSON | `unit/loadtest/` | P0 |

### Acceptance Criteria

- [ ] `loadtest.reportEnabled: true` → adapter registered in FrameworkBootstrap
- [ ] `loadtest.reportEnabled: false` → adapter NOT registered
- [ ] HTML report'ta "Load Test" tab'ı görünür
- [ ] Throughput over time line chart render edilir
- [ ] Latency distribution histogram (p50, p90, p95, p99) render edilir
- [ ] Error rate timeline render edilir
- [ ] Per-step breakdown table shows step name, requests, p95, error rate
- [ ] Status code pie chart shows distribution
- [ ] Scenario config summary shows users, rampUp, hold, engine used
- [ ] Gatling native report link (if Gatling engine used)
- [ ] Report generation failure does NOT fail the build (try/catch)
- [ ] Unit testler geçer

### Definition of Done

```
target/
├── testfly-report.html          ← Load Test tab visible
├── testfly-metrics.json         ← loadTests[] array present
├── loadtest/
│   └── gatling-results/         ← Gatling native output (if used)
└── loadtest-report.html         ← Standalone load test report (optional)
```

---

## Sprint 6 — Documentation & Examples

**Goal:** Docs (EN+TR), consumer project examples, final polish.

### Tasks

| # | Task | File(s) | Priority |
|---|------|---------|----------|
| 6.1 | Getting started guide (EN) | `docs-site/docs/loadtest/getting-started.md` | P0 |
| 6.2 | Configuration reference (EN) | `docs-site/docs/loadtest/configuration.md` | P0 |
| 6.3 | Fluent API guide (EN) | `docs-site/docs/loadtest/fluent-api.md` | P0 |
| 6.4 | Annotations guide (EN) | `docs-site/docs/loadtest/annotations.md` | P0 |
| 6.5 | Feeders guide (EN) | `docs-site/docs/loadtest/feeders.md` | P1 |
| 6.6 | Assertions guide (EN) | `docs-site/docs/loadtest/assertions.md` | P0 |
| 6.7 | Reporting guide (EN) | `docs-site/docs/loadtest/reporting.md` | P1 |
| 6.8 | Engine comparison (Gatling vs JDK) (EN) | `docs-site/docs/loadtest/engines.md` | P1 |
| 6.9 | Examples page (EN) | `docs-site/docs/loadtest/examples.md` | P0 |
| 6.10 | Turkish translations (all pages) | `docs-site/i18n/tr/docs/loadtest/` | P1 |
| 6.11 | Consumer project examples | `testfly-test/src/test/java/.../loadtest/` | P0 |
| 6.12 | CHANGELOG.md — 1.1.0 entry | `CHANGELOG.md` | P0 |
| 6.13 | docs-site/docs/changelog.md — 1.1.0 entry | `docs-site/docs/changelog.md` | P0 |
| 6.14 | README.md — Load Testing feature mention | `README.md` | P1 |
| 6.15 | Version bump — pom.xml 1.0.4 → 1.1.0 | `pom.xml` | P0 |
| 6.16 | Version bump checklist (all files per AGENTS.md) | multiple | P0 |
| 6.17 | Sidebar configuration — docs-site sidebars.js | `docs-site/sidebars.js` | P0 |
| 6.18 | Final integration test — end-to-end load test with real server | `integration/` | P1 |

### Acceptance Criteria

- [ ] All docs pages render correctly in Docusaurus (EN + TR)
- [ ] Sidebar navigation includes Load Testing section
- [ ] Consumer project has 3+ working load test examples:
  - Simple single-endpoint load test
  - Multi-step scenario with feeder
  - Annotation-driven load test
- [ ] CHANGELOG updated with 1.1.0 entry
- [ ] Version bumped across all files (per AGENTS.md checklist)
- [ ] `mvn clean verify` passes
- [ ] `cd docs-site && npm run build` passes
- [ ] Consumer project `mvn test` passes with load test examples

### Definition of Done

```
docs-site/docs/loadtest/
├── getting-started.md
├── configuration.md
├── fluent-api.md
├── annotations.md
├── feeders.md
├── assertions.md
├── reporting.md
├── engines.md
└── examples.md

docs-site/i18n/tr/docs/loadtest/
├── getting-started.md
├── configuration.md
├── fluent-api.md
├── annotations.md
├── feeders.md
├── assertions.md
├── reporting.md
├── engines.md
└── examples.md
```

---

## Cross-Sprint Concerns

### Code Quality Gate (Every Sprint)

```bash
# Her sprint sonunda:
mvn clean verify                    # compile + test + package
mvn test -Dtest=LoadTest*           # load test unit tests
mvn clean verify -Pquality          # JaCoCo, SpotBugs, Checkstyle, PMD
```

### Backward Compatibility

- Mevcut `BaseTest`, `BaseApiTest`, `BaseJUnit5Test` → **sıfır değişiklik**
- Mevcut `testfly.yml` → `loadtest:` block **opsiyonel**, yoksa hiçbir şey değişmez
- Mevcut HTML report → Load Test tab **sadece load test çalıştırıldığında** görünür
- Mevcut `testfly-metrics.json` → `loadTests[]` **sadece load test çalıştırıldığında** eklenir

### Performance Budget

| Metric | Target |
|--------|--------|
| Framework startup overhead (no load test) | < 1ms |
| `GatlingBridge.isAvailable()` probe | < 5ms |
| JDK engine 100 users, 30s | < 50MB heap |
| Load test report generation | < 2s |

---

## Dependency Graph

```
Sprint 1 (Foundation)
    │
    ├──→ Sprint 2 (JDK Engine)
    │        │
    │        └──→ Sprint 3 (Gatling Engine)
    │                 │
    ├──→ Sprint 4 (Assertions & Metrics) ←──┘
    │        │
    │        └──→ Sprint 5 (Reporting)
    │                 │
    └─────────────────┴──→ Sprint 6 (Docs & Examples)
```

**Critical path:** Sprint 1 → 2 → 3 → 4 → 5 → 6

**Parallelizable:**
- Sprint 4 (Assertions) can start after Sprint 2 (doesn't need Gatling)
- Sprint 6 (Docs) can start partially after Sprint 4 (API is stable)

---

## Release Checklist (v1.1.0)

Per AGENTS.md version-bump checklist:

- [ ] `pom.xml` → `1.1.0`
- [ ] `README.md` → dependency snippet + "Current release" line
- [ ] `CHANGELOG.md` → new 1.1.0 entry
- [ ] `docs-site/docs/getting-started.md` → version
- [ ] `docs-site/docs/junit5.md` → version
- [ ] `docs-site/docs/changelog.md` → version
- [ ] `docs-site/src/pages/index.js` → version
- [ ] Git tag `v1.1.0`
- [ ] GitHub Actions release workflow → Maven Central deploy
- [ ] Consumer project (testfly-test) → pin to 1.1.0, run load test examples
- [ ] `testfly/website` repo → update `LATEST_VERSION`
