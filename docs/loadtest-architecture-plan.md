# TestFly Load Testing Module — Architecture Plan

> **Status:** Draft
> **Author:** TestFly Team
> **Date:** 2026-09-07
> **Target Version:** 1.1.0

---

## 1. Vision

TestFly'a **bağımsız bir Load Testing modülü** eklemek. API ve WebUI testlerinden ayrı, kendi başına bir disiplin. Kullanıcı **Gatling öğrenmek zorunda değil** — TestFly'ın fluent API'si yeterli. Gatling motor olarak arkada çalışır, kullanıcı asla görmez.

Selenium nasıl `DriverManager` + `DriverProvider` ile sarmalanmışsa, Gatling de `LoadTestRunner` + `GatlingBridge` ile sarmalanır.

---

## 2. Design Principles

| Prensip | Açıklama |
|---------|----------|
| **Zero Gatling knowledge** | Kullanıcı sadece TestFly API'si öğrenir: `load()`, `step()`, `feed()`, `run()`, `assert*()` |
| **Convention over configuration** | `testfly.yml` defaults → `@LoadTest` override → fluent override |
| **Optional dependency** | Gatling `<optional>true</optional>` — sadece load test kullanan projeye çeker |
| **Graceful degradation** | Gatling yoksa JDK HttpClient + ExecutorService fallback |
| **Existing patterns** | `BaseApiTest` → `BaseLoadTest`, `DriverManager` → `LoadTestRunner`, `PerformanceAssert` → `LoadTestAssert` |
| **No core changes** | Mevcut Selenium/API/Report altyapısına sıfır müdahale, sadece yeni paket + config block + ReportAdapter |

---

## 3. Package Structure

```
src/main/java/io/testfly/
├── loadtest/
│   ├── BaseLoadTest.java              ← Kullanıcı extend eder
│   ├── LoadTest.java                  ← @LoadTest annotation
│   ├── LoadScenario.java              ← Senaryo tanımı (fluent builder)
│   ├── LoadStep.java                  ← Tek bir HTTP adımı
│   ├── LoadTestRunner.java            ← Execution engine (DriverManager gibi)
│   ├── LoadTestMetrics.java           ← Sonuçlar (throughput, latency, errors)
│   ├── LoadTestAssert.java            ← Fluent assertion API
│   ├── LoadTestFeeder.java            ← Data feeder (CSV, JSON, random)
│   ├── LoadTestReportAdapter.java     ← ReportAdapter SPI impl
│   └── internal/
│       ├── GatlingBridge.java         ← Class.forName + reflection guard
│       ├── GatlingEngine.java         ← Gatling motor adapter
│       ├── JdkLoadEngine.java         ← JDK HttpClient fallback engine
│       └── LoadTestEngine.java        ← Engine interface (strategy)
├── config/
│   └── TestFlyConfig.java             ← +LoadTest nested class
└── test/support/
    └── LoadTestSupport.java           ← Support interface mixin
```

---

## 4. User Experience — 3 Katman

### 4.1 testfly.yml — Basit Defaults

```yaml
loadtest:
  baseUrl: https://api.example.com
  users: 50
  rampUp: 30s
  hold: 60s
  cooldown: 10s
  engine: auto                # auto | gatling | jdk
  resultsDir: target/loadtest
  reportEnabled: true
```

Sadece temel değerler. Senaryo tanımı YAML'da **yok**.

### 4.2 @LoadTest — Annotation Override

```java
@LoadTest(users = 200, rampUp = "30s", hold = "2m")
public class ProductLoadTest extends BaseLoadTest {

    @Test
    public void searchUnderLoad() {
        load("/api/products?search=laptop")
            .assertStatus(200)
            .assertP95Below(300)
            .assertErrorRateBelow(0.02);
    }

    @Test
    @LoadTest(users = 500)  // method-level override
    public void heavySearch() {
        load("/api/products?search=phone")
            .assertThroughputAbove(1000);
    }
}
```

**Öncelik:** Method `@LoadTest` > Class `@LoadTest` > `testfly.yml` defaults

### 4.3 Fluent API — Tam Kontrol

#### Basit (tek endpoint)

```java
@Test
public void healthCheck() {
    load("/api/health")
        .users(100)
        .rampUp(Duration.ofSeconds(10))
        .hold(Duration.ofMinutes(1))
        .run()
        .assertP95Below(100)
        .assertErrorRateBelow(0.01);
}
```

#### Karmaşık (multi-step scenario)

```java
@Test
public void checkoutFlow() {
    loadScenario("Checkout Flow")
        .users(500)
        .rampUp(Duration.ofSeconds(30))
        .hold(Duration.ofMinutes(2))

        .step("Login")
            .post("/api/auth/login")
            .body(Map.of("user", "${username}", "pass", "${password}"))
            .extract("token", "$.accessToken")

        .step("Add to Cart")
            .post("/api/cart")
            .header("Authorization", "Bearer ${token}")
            .body(Map.of("productId", "${productId}"))
            .check(status().is(201))

        .step("Checkout")
            .post("/api/orders")
            .header("Authorization", "Bearer ${token}")
            .body(Map.of("cartId", "${cartId}"))
            .check(status().is(200))

        .feed(csv("users.csv"))
        .thinkTime(500, 2000)

        .run()
        .assertP95Below(500)
        .assertErrorRateBelow(0.01)
        .assertThroughputAbove(200);
}
```

---

## 5. Architecture Details

### 5.1 BaseLoadTest

`BaseApiTest` pattern'ini takip eder — support interface mixin'leri ile yetenekler:

```java
@TestFlyApi(since = "1.1.0")
@Listeners({ SuiteExecutionListener.class, TestExecutionListener.class })
public abstract class BaseLoadTest implements
        LoadTestSupport,      // load(), loadScenario()
        ApiSupport,           // apiClient() — fonksiyonel API testleri için
        ContextSupport,       // ctx(), suiteCtx()
        SoftAssertSupport,    // softAssert()
        StepSupport,          // step() — HTML report timeline
        TestDataSupport {     // @TestData

    // Boş gövde — tüm logic support interface default method'larında
}
```

**Selenium yok, WebDriver yok, browser yok.** `@NoBrowser` semantics permanently active.

### 5.2 LoadTestSupport (Mixin Interface)

```java
public interface LoadTestSupport {

    default LoadScenario load(String path) {
        return LoadScenario.single(path);
    }

    default LoadScenario loadScenario(String name) {
        return LoadScenario.named(name);
    }

    default LoadTestMetrics lastLoadMetrics() {
        return LoadTestRunner.lastMetrics();
    }
}
```

### 5.3 LoadScenario (Fluent Builder)

```java
public final class LoadScenario {

    // ── Config ──
    public LoadScenario users(int users);
    public LoadScenario rampUp(Duration duration);
    public LoadScenario hold(Duration duration);
    public LoadScenario cooldown(Duration duration);

    // ── Steps ──
    public LoadStep step(String name);        // yeni step başlatır
    public LoadScenario get(String path);     // shortcut: tek step
    public LoadScenario post(String path);
    public LoadScenario put(String path);
    public LoadScenario delete(String path);
    public LoadScenario patch(String path);

    // ── Data ──
    public LoadScenario feed(LoadTestFeeder feeder);
    public LoadScenario feedCsv(String path);
    public LoadScenario feedJson(String path);
    public LoadScenario thinkTime(long minMs, long maxMs);
    public LoadScenario thinkTime(Duration fixed);

    // ── Execution ──
    public LoadTestAssert run();

    // ── Shortcuts (tek step + run + assert) ──
    public LoadTestAssert assertStatus(int expected);
    public LoadTestAssert assertP95Below(double ms);
    public LoadTestAssert assertThroughputAbove(double rps);
    public LoadTestAssert assertErrorRateBelow(double rate);
}
```

### 5.4 LoadStep (Per-Request Builder)

```java
public final class LoadStep {

    public LoadStep get(String path);
    public LoadStep post(String path);
    public LoadStep put(String path);
    public LoadStep delete(String path);
    public LoadStep patch(String path);

    public LoadStep header(String name, String value);
    public LoadStep body(Object body);
    public LoadStep body(String json);
    public LoadStep queryParam(String name, Object value);
    public LoadStep formParam(String name, String value);

    public LoadStep check(CheckCondition condition);
    public LoadStep extract(String variable, String jsonPath);

    public LoadScenario step(String name);    // yeni step
    public LoadTestAssert run();              // scenario'yu çalıştır
}
```

### 5.5 LoadTestEngine (Strategy Interface)

```java
public interface LoadTestEngine {

    String name();                            // "gatling" | "jdk"
    boolean isAvailable();                    // classpath probe
    LoadTestMetrics execute(LoadScenario scenario, LoadTestConfig config);
}
```

İki implementasyon:

| Engine | Ne zaman | Nasıl |
|--------|----------|-------|
| `GatlingEngine` | Gatling classpath'te varsa | Reflection ile `io.gatling.app.Gatling.fromMap()` çağırır |
| `JdkLoadEngine` | Gatling yoksa (fallback) | `ExecutorService` + JDK `HttpClient`, basit concurrent simulation |

### 5.6 GatlingBridge (Reflection Guard)

`ReportPortalJUnit5Bridge` pattern'ini takip eder:

```java
public final class GatlingBridge {

    private static final boolean AVAILABLE;
    private static final String GATLING_CLASS = "io.gatling.app.Gatling";

    static {
        boolean ok;
        try {
            Class.forName(GATLING_CLASS, false, GatlingBridge.class.getClassLoader());
            ok = true;
        } catch (ClassNotFoundException e) {
            ok = false;
        }
        AVAILABLE = ok;
    }

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    /**
     * Gatling simulation'ı reflection ile çalıştırır.
     * Kullanıcı Gatling API'sini asla görmez.
     */
    public static GatlingRunResult run(LoadScenario scenario, LoadTestConfig config) {
        if (!AVAILABLE) {
            throw new IllegalStateException(
                "[LoadTest] Gatling not on classpath. Add:\n" +
                "  <dependency>\n" +
                "    <groupId>io.gatling.highcharts</groupId>\n" +
                "    <artifactId>gatling-charts-highcharts</artifactId>\n" +
                "    <version>3.13.5</version>\n" +
                "    <scope>test</scope>\n" +
                "  </dependency>");
        }
        // Reflection: Gatling.fromMap(props) veya programmatic simulation build
        // ...
    }
}
```

### 5.7 LoadTestRunner (Execution Orchestrator)

`DriverManager` gibi — lifecycle yönetir:

```java
public final class LoadTestRunner {

    private static final ThreadLocal<LoadTestMetrics> LAST_METRICS = new ThreadLocal<>();

    public static LoadTestMetrics run(LoadScenario scenario) {
        LoadTestConfig config = resolveConfig(scenario);
        LoadTestEngine engine = selectEngine(config);

        StepLogger.step("Load Test: " + scenario.name(),
            scenario.users() + " users, " + scenario.holdDuration());

        LoadTestMetrics metrics = engine.execute(scenario, config);

        LAST_METRICS.set(metrics);
        ExecutionMetrics.recordLoadTest(scenario.name(), metrics);

        return metrics;
    }

    public static LoadTestMetrics lastMetrics() {
        return LAST_METRICS.get();
    }

    private static LoadTestEngine selectEngine(LoadTestConfig config) {
        return switch (config.getEngine()) {
            case "gatling" -> requireGatling();
            case "jdk" -> new JdkLoadEngine();
            case "auto" -> GatlingBridge.isAvailable()
                ? new GatlingEngine()
                : new JdkLoadEngine();
            default -> throw new IllegalArgumentException("Unknown engine: " + config.getEngine());
        };
    }
}
```

### 5.8 LoadTestMetrics

```java
public record LoadTestMetrics(
    String scenarioName,
    int totalRequests,
    int successfulRequests,
    int failedRequests,
    double throughputRps,           // requests per second
    double meanLatencyMs,
    double p50LatencyMs,
    double p90LatencyMs,
    double p95LatencyMs,
    double p99LatencyMs,
    double minLatencyMs,
    double maxLatencyMs,
    double errorRate,               // 0.0 - 1.0
    Map<Integer, Long> statusCodes, // 200 → 15000, 500 → 23
    long durationMs,
    int users,
    Map<String, StepMetrics> steps  // per-step breakdown
) {
    public record StepMetrics(
        String name,
        int totalRequests,
        double p95LatencyMs,
        double errorRate
    ) {}
}
```

### 5.9 LoadTestAssert (Fluent Assertions)

`PerformanceAssert` pattern'ini takip eder:

```java
public final class LoadTestAssert {

    private final LoadTestMetrics metrics;

    public LoadTestAssert assertThroughputAbove(double rps);
    public LoadTestAssert assertThroughputBelow(double rps);

    public LoadTestAssert assertP50Below(double ms);
    public LoadTestAssert assertP90Below(double ms);
    public LoadTestAssert assertP95Below(double ms);
    public LoadTestAssert assertP99Below(double ms);

    public LoadTestAssert assertMeanLatencyBelow(double ms);
    public LoadTestAssert assertMaxLatencyBelow(double ms);

    public LoadTestAssert assertErrorRateBelow(double rate);
    public LoadTestAssert assertSuccessRateAbove(double rate);

    public LoadTestAssert assertStatusCodeCount(int status, long minCount);
    public LoadTestAssert assertNoStatus(int status);

    public LoadTestAssert assertStepP95Below(String stepName, double ms);
    public LoadTestAssert assertStepErrorRateBelow(String stepName, double rate);

    public LoadTestMetrics metrics();  // raw access
}
```

### 5.10 LoadTestFeeder

```java
public abstract class LoadTestFeeder {

    public static LoadTestFeeder csv(String path);
    public static LoadTestFeeder json(String path);
    public static LoadTestFeeder random(int min, int max, String variable);
    public static LoadTestFeeder uuid(String variable);
    public static LoadTestFeeder sequence(String variable, long start, long step);
    public static LoadTestFeeder constant(String variable, String value);

    public abstract Map<String, Object> next();
    public abstract void reset();
}
```

---

## 6. Configuration

### 6.1 TestFlyConfig.LoadTest (Nested Class)

```java
public static final class LoadTest {
    private boolean enabled = false;
    private String baseUrl;
    private String engine = "auto";       // auto | gatling | jdk
    private int users = 10;
    private String rampUp = "10s";
    private String hold = "30s";
    private String cooldown = "5s";
    private int maxUsers = 1000;
    private String resultsDir = "target/loadtest";
    private boolean reportEnabled = true;
    private int requestTimeoutSeconds = 30;
    private double assertionThreshold = 0.95;  // default percentile

    // getters + setters (SnakeYAML binding)
    // Duration parsing: "30s", "2m", "1h" → Duration
}
```

### 6.2 @LoadTest Annotation

```java
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
@TestFlyApi(since = "1.1.0")
public @interface LoadTest {
    int users() default -1;           // -1 = use config default
    String rampUp() default "";       // "" = use config default
    String hold() default "";
    String cooldown() default "";
    String engine() default "";       // "" = use config default
    String baseUrl() default "";      // "" = use config default
}
```

### 6.3 Config Resolution Priority

```
Method @LoadTest  >  Class @LoadTest  >  testfly.yml loadtest:  >  Hardcoded defaults
```

---

## 7. Reporting Integration

### 7.1 LoadTestReportAdapter

```java
public final class LoadTestReportAdapter implements ReportAdapter {

    @Override
    public String getName() { return "loadtest"; }

    @Override
    public void generate(File metricsJson) {
        // 1. Read load test results from ExecutionMetrics
        // 2. Generate loadtest-report.html in ReportPaths.baseDir()
        // 3. Include: throughput chart, latency distribution, error timeline,
        //    per-step breakdown, status code distribution
    }
}
```

**Registration:** `FrameworkBootstrap`'ta config-gated (NotificationAdapter pattern):

```java
if (config.getLoadTest() != null && config.getLoadTest().isReportEnabled()) {
    ReportAdapterRegistry.register(new LoadTestReportAdapter());
}
```

SPI dosyasına **eklenmez** — double-registration bug'ından kaçınmak için.

### 7.2 HTML Report — Load Test Tab

Mevcut HTML report'a yeni bir **Load Test** tab'ı:

- Throughput over time (line chart)
- Latency distribution (histogram: p50, p90, p95, p99)
- Error rate timeline
- Status code distribution (pie chart)
- Per-step breakdown table
- Scenario config summary (users, rampUp, hold, engine)

### 7.3 ExecutionMetrics Integration

```java
// ExecutionMetrics'e yeni metod
public static void recordLoadTest(String scenarioName, LoadTestMetrics metrics);

// testfly-metrics.json'a yeni alan
{
    "loadTests": [
        {
            "scenario": "Checkout Flow",
            "users": 500,
            "throughputRps": 1234.5,
            "p95LatencyMs": 245.3,
            "errorRate": 0.008,
            "durationMs": 120000,
            "steps": [...]
        }
    ]
}
```

---

## 8. Engine Comparison

### 8.1 GatlingEngine (Primary)

| Özellik | Detay |
|---------|-------|
| **HTTP Motor** | Netty async, non-blocking |
| **Concurrent Users** | 10,000+ (single machine) |
| **Metrics** | Built-in percentile calculation, real-time stats |
| **Report** | Gatling HTML report (Highcharts) |
| **Feeder** | Native CSV/JSON/JDBC feeder support |
| **Dependency** | `gatling-charts-highcharts` (~15MB with Scala) |

**Integration:** Reflection ile `io.gatling.app.Gatling.fromMap(props)` veya programmatic `Simulation` build. Kullanıcı Gatling API'sini asla görmez.

### 8.2 JdkLoadEngine (Fallback)

| Özellik | Detay |
|---------|-------|
| **HTTP Motor** | JDK `HttpClient` (sync per thread) |
| **Concurrent Users** | ~500 (thread-per-user) |
| **Metrics** | Manual percentile calculation |
| **Report** | TestFly HTML report only |
| **Feeder** | Simple CSV/JSON reader |
| **Dependency** | Sıfır (JDK built-in) |

**Ne zaman:** Gatling classpath'te yoksa otomatik fallback. Basit load testler için yeterli.

---

## 9. Dependency Strategy

### pom.xml

```xml
<!-- Load Testing — Gatling (optional) -->
<dependency>
    <groupId>io.gatling.highcharts</groupId>
    <artifactId>gatling-charts-highcharts</artifactId>
    <version>3.13.5</version>
    <optional>true</optional>
    <scope>test</scope>
</dependency>
```

### Kullanıcı pom.xml (load test kullanacaksa)

```xml
<dependency>
    <groupId>io.github.hakanngul</groupId>
    <artifactId>testfly</artifactId>
    <version>1.1.0</version>
    <scope>test</scope>
</dependency>

<!-- Gatling engine (optional — yoksa JDK fallback kullanılır) -->
<dependency>
    <groupId>io.gatling.highcharts</groupId>
    <artifactId>gatling-charts-highcharts</artifactId>
    <version>3.13.5</version>
    <scope>test</scope>
</dependency>
```

---

## 10. Thread Safety & Concurrency

| Component | Thread Model |
|-----------|-------------|
| `LoadTestRunner` | Static, ThreadLocal for last metrics |
| `LoadScenario` | Immutable builder — thread-safe |
| `GatlingEngine` | Gatling kendi thread pool'unu yönetir |
| `JdkLoadEngine` | `ExecutorService` (configurable pool size) |
| `LoadTestFeeder` | `synchronized next()` veya thread-local copy |
| `LoadTestAssert` | Immutable, reads from `LoadTestMetrics` record |

**ApiClient uyumu:** `ApiClient`'ın static global'leri (`GLOBAL_SPEC`, interceptors) load test sırasında **kullanılmaz**. Load test engine kendi HTTP client'ını yönetir.

---

## 11. Error Handling

| Durum | Davranış |
|-------|----------|
| Gatling classpath'te yok, engine=gatling | `IllegalStateException` + dependency hint |
| Gatling classpath'te yok, engine=auto | JDK fallback, warning log |
| Load test timeout | `LoadTestTimeoutException`, partial metrics returned |
| Tüm requestler fail | Metrics collected, assertions fail with detail |
| Feeder dosyası yok | `IllegalArgumentException` at scenario build time |
| Config invalid (users <= 0) | `IllegalArgumentException` with clear message |

---

## 12. Testing Strategy

### Unit Tests (`src/test/java/io/testfly/unit/loadtest/`)

- `LoadScenarioTest` — builder chain, config resolution, validation
- `LoadTestRunnerTest` — engine selection, metrics recording
- `LoadTestAssertTest` — all assertion methods, pass/fail cases
- `LoadTestMetricsTest` — percentile calculation, status code aggregation
- `GatlingBridgeTest` — classpath detection, error messages
- `JdkLoadEngineTest` — mock HTTP server, concurrent execution
- `LoadTestConfigTest` — YAML binding, defaults, duration parsing
- `LoadTestFeederTest` — CSV/JSON parsing, variable substitution

**No real Gatling dependency in unit tests** — mock `GatlingBridge.isAvailable()`.

### Integration Tests (`src/test/java/io/testfly/integration/`)

- `GatlingEngineIntegrationTest` — real Gatling execution against mock server
- Requires `-Preal-backends` profile

---

## 13. Documentation Plan

| Doc | Path |
|-----|------|
| Getting Started | `docs-site/docs/loadtest/getting-started.md` |
| Configuration | `docs-site/docs/loadtest/configuration.md` |
| Fluent API | `docs-site/docs/loadtest/fluent-api.md` |
| Annotations | `docs-site/docs/loadtest/annotations.md` |
| Feeders | `docs-site/docs/loadtest/feeders.md` |
| Assertions | `docs-site/docs/loadtest/assertions.md` |
| Reporting | `docs-site/docs/loadtest/reporting.md` |
| Gatling Engine | `docs-site/docs/loadtest/gatling-engine.md` |
| JDK Fallback | `docs-site/docs/loadtest/jdk-engine.md` |
| Examples | `docs-site/docs/loadtest/examples.md` |
| Turkish translations | `docs-site/i18n/tr/...` |

---

## 14. Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Gatling Scala version conflict | Build failure | `<optional>true</optional>` + shade plugin if needed |
| Netty version clash with Selenium CDP | Runtime error | Gatling engine isolated in separate classloader veya Netty version alignment |
| High memory under 10K users | OOM | `maxUsers` config cap + JdkLoadEngine fallback for simple tests |
| Gatling API changes between versions | Reflection breakage | Pin Gatling version + integration test gate |
| User expects browser-level load test | Confusion | Clear docs: "HTTP-level load testing, not browser simulation" |

---

## 15. Success Criteria

- [ ] `BaseLoadTest` extend eden bir test, sıfır Gatling bilgisiyle çalışır
- [ ] `testfly.yml` defaults + `@LoadTest` annotation + fluent API — 3 katman çalışır
- [ ] Gatling yoksa JDK fallback otomatik devreye girer
- [ ] HTML report'ta Load Test tab'ı görünür
- [ ] `mvn test` ile unit testler geçer (Gatling mock)
- [ ] Documentation EN + TR tamamlanır
- [ ] Consumer project'te (testfly-test) örnek load test çalışır
