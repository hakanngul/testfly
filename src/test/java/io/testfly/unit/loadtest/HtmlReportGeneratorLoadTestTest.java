package io.testfly.unit.loadtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.testfly.config.TestFlyConfig;
import io.testfly.internal.TestFlyContext;
import io.testfly.loadtest.LoadTestReportAdapter;
import io.testfly.metrics.ExecutionMetrics;
import io.testfly.reporting.HtmlReportGenerator;
import io.testfly.reporting.ReportPaths;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.*;

/**
 * Unit tests verifying that {@link HtmlReportGenerator} renders load-test data,
 * charts, metrics, and Gatling links into {@code testfly-report.html}.
 */
@Test(singleThreaded = true)
public class HtmlReportGeneratorLoadTestTest {

    private static final Object GLOBAL_REPORT_LOCK = ReportPaths.class;
    private static final Object CONTEXT_LOCK = TestFlyContext.class;
    private static final String REPORTS_DIR = "target/reports-isolated/HtmlReportGeneratorLoadTestTest";

    @BeforeMethod
    public void setup() throws Exception {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                // Own directory: surefire forks run other classes that delete target/testfly-metrics.json.
                System.setProperty("testfly.reports.dir", REPORTS_DIR);
                cleanFiles();
                resetTestFlyContext();
                ExecutionMetrics.reset();
                TestFlyContext.initialize(minimalConfig());
            }
        }
    }

    @AfterMethod
    public void cleanup() throws Exception {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                cleanFiles();
                System.clearProperty("testfly.reports.dir");
                resetTestFlyContext();
                ExecutionMetrics.reset();
            }
        }
    }

    private static void cleanFiles() {
        File html = ReportPaths.htmlReport();
        File json = ReportPaths.metricsJson();
        File standalone = ReportPaths.loadTestHtmlReport();
        if (html.exists())
            html.delete();
        if (json.exists())
            json.delete();
        if (standalone.exists())
            standalone.delete();
    }

    private static void resetTestFlyContext() throws Exception {
        Field configField = TestFlyContext.class.getDeclaredField("CONFIG");
        configField.setAccessible(true);
        AtomicReference<?> ref = (AtomicReference<?>) configField.get(null);
        ref.set(null);
        TestFlyContext.clearCurrentTestId();
    }

    private static TestFlyConfig minimalConfig() {
        TestFlyConfig config = new TestFlyConfig();

        TestFlyConfig.Browser browser = new TestFlyConfig.Browser();
        browser.setName("chrome");
        config.setBrowser(browser);

        TestFlyConfig.Execution execution = new TestFlyConfig.Execution();
        execution.setMode("local");
        execution.setBaseUrl("https://example.com");
        config.setExecution(execution);

        TestFlyConfig.Timeouts timeouts = new TestFlyConfig.Timeouts();
        timeouts.setExplicit(10);
        timeouts.setPageLoad(30);
        config.setTimeouts(timeouts);

        TestFlyConfig.LoadTest loadTest = new TestFlyConfig.LoadTest();
        loadTest.setReportEnabled(true);
        config.setLoadTest(loadTest);

        return config;
    }

    @Test
    public void generate_withLoadTestData_rendersLoadTestTabAndContent() throws Exception {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                Path testDir = Files.createTempDirectory("html-loadtest-render-");
                try {
                    File jsonFile = new File(testDir.toFile(), "testfly-metrics.json");

                    Map<String, Object> metrics = new LinkedHashMap<>();
                    metrics.put("totalTests", 1);
                    metrics.put("passedTests", 1);
                    metrics.put("failedTests", 0);
                    metrics.put("skippedTests", 0);
                    metrics.put("totalTimeMs", 5000L);

                    Map<String, Object> lt = new LinkedHashMap<>();
                    lt.put("scenarioName", "CheckoutFlow");
                    lt.put("engine", "gatling");
                    lt.put("users", 50);
                    lt.put("durationMs", 10000L);
                    lt.put("totalRequests", 500);
                    lt.put("successfulRequests", 495);
                    lt.put("failedRequests", 5);
                    lt.put("throughputRps", 49.5);
                    lt.put("meanLatencyMs", 65.0);
                    lt.put("p50LatencyMs", 50.0);
                    lt.put("p75LatencyMs", null); // not measured: must render as n/a, not crash or show 0
                    lt.put("p90LatencyMs", 95.0);
                    lt.put("p95LatencyMs", 120.0);
                    lt.put("p99LatencyMs", 180.0);
                    lt.put("minLatencyMs", 15.0);
                    lt.put("maxLatencyMs", 250.0);
                    lt.put("errorRate", 0.01);
                    lt.put("statusCodes", Map.of("200", 495, "500", 5));

                    Map<String, Object> step1 = new LinkedHashMap<>();
                    step1.put("name", "Step 1 - Login");
                    step1.put("totalRequests", 250);
                    step1.put("successfulRequests", 250);
                    step1.put("failedRequests", 0);
                    step1.put("p95LatencyMs", 110.0);
                    step1.put("errorRate", 0.0);
                    lt.put("steps", Map.of("Step 1 - Login", step1));

                    metrics.put("loadTests", List.of(lt));

                    new ObjectMapper().writeValue(jsonFile, metrics);

                    // Run report generation for this isolated metrics file
                    HtmlReportGenerator.generate(jsonFile);

                    File htmlFile = new File(testDir.toFile(), "testfly-report.html");
                    assertTrue(htmlFile.exists(), "HTML report should be generated");

                    String html = Files.readString(htmlFile.toPath(), StandardCharsets.UTF_8);

                    // Verify Load Test UI elements in HTML
                    assertTrue(html.contains("id=\"tab-loadtest\""), "Report should contain tab-loadtest");
                    assertTrue(html.contains("id=\"nav-loadtest\""), "Report should contain nav-loadtest sidebar item");
                    assertTrue(html.contains("renderLoadTests"), "Report should include renderLoadTests JS function");
                    assertTrue(html.contains("renderLoadTestCharts"), "Report should include renderLoadTestCharts JS function");
                    assertTrue(html.contains("lt-chart-latency"),
                            "Report should contain latency distribution chart element ID");
                    assertTrue(html.contains("lt-chart-status"), "Report should contain status code chart element ID");

                    // Verify embedded JSON contains the scenario data
                    assertTrue(html.contains("CheckoutFlow"), "Report data should contain scenario name");
                    assertTrue(html.contains("Step 1 - Login"), "Report data should contain step name");
                    String data = Files.readString(new File(testDir.toFile(), "testfly-report-data.json").toPath(),
                            StandardCharsets.UTF_8);
                    assertTrue(data.matches("(?s).*\"p75LatencyMs\"\\s*:\\s*null.*"),
                            "unmeasured p75 must stay null in report data");
                    assertTrue(html.contains("function ltMs(v) { return ltMeasured(v) ? Math.round(v) + ' ms' : 'n/a'; }"),
                            "template must render null latencies as n/a");
                    assertTrue(html.contains("value: ltMeasured(lt.p75LatencyMs) ? Math.round(lt.p75LatencyMs) : 'n/a'"),
                            "P75 tile must render null as n/a");
                    assertTrue(html.contains("tdP95.textContent = ltMs(st.p95LatencyMs);"), "step p95 must use n/a");
                    assertFalse(html.contains("LatencyMs || 0"), "no latency may be coerced to 0");
                    // BUG-01: never fabricate a "200" slice when status codes were not measured
                    assertFalse(html.contains("labels = ['200'];"), "status doughnut must not fabricate a 200 slice");
                    assertTrue(html.contains("'Status codes not measured'"), "empty statusCodes must say not measured");
                    assertTrue(html.contains("if (code === '-1') return 'No response';"), "-1 must render as No response");
                } finally {
                    deleteRecursively(testDir);
                }
            }
        }
    }

    @Test
    public void generate_withUnmeasuredThroughput_rendersNaNotZero() throws Exception {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                Path testDir = Files.createTempDirectory("html-loadtest-throughput-");
                try {
                    File jsonFile = new File(testDir.toFile(), "testfly-metrics.json");
                    Map<String, Object> lt = new LinkedHashMap<>(io.testfly.metrics.ExecutionMetrics.loadTestMetricsToMap(
                            new io.testfly.loadtest.LoadTestMetrics("NoDuration", 3, 3, 0, Double.NaN, 4, 4, 4, 4, 4,
                                    4, 4, 0, Map.of(200, 3L), 0, 1, "gatling", Map.of(), 4)));
                    assertTrue(lt.containsKey("throughputRps") && lt.get("throughputRps") == null,
                            "unmeasured throughput must be null in the metrics map, was " + lt.get("throughputRps"));

                    Map<String, Object> metrics = new LinkedHashMap<>();
                    metrics.put("totalTests", 1);
                    metrics.put("passedTests", 1);
                    metrics.put("failedTests", 0);
                    metrics.put("skippedTests", 0);
                    metrics.put("totalTimeMs", 1000L);
                    metrics.put("loadTests", List.of(lt));
                    new ObjectMapper().writeValue(jsonFile, metrics);

                    HtmlReportGenerator.generate(jsonFile);

                    String html = Files.readString(new File(testDir.toFile(), "testfly-report.html").toPath(),
                            StandardCharsets.UTF_8);
                    String data = Files.readString(new File(testDir.toFile(), "testfly-report-data.json").toPath(),
                            StandardCharsets.UTF_8);
                    assertTrue(data.matches("(?s).*\"throughputRps\"\\s*:\\s*null.*"),
                            "unmeasured throughput must stay null in report data");
                    assertFalse(html.contains("throughputRps || 0"), "throughput may not be coerced to 0");
                    assertTrue(html.contains("value: ltMeasured(lt.throughputRps) ? Number(lt.throughputRps).toFixed(1) : 'n/a'"),
                            "throughput tile must render null as n/a");
                    assertTrue(html.contains("throughputMeasured ? Math.round(totalThroughput).toLocaleString() + ' rps' : 'n/a'"),
                            "summary throughput must render n/a when no scenario measured it");
                } finally {
                    deleteRecursively(testDir);
                }
            }
        }
    }

    @Test
    public void generate_withSimulationAggregate_summaryCountsScenariosOnce() throws Exception {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                Path testDir = Files.createTempDirectory("html-loadtest-aggregate-");
                try {
                    io.testfly.loadtest.LoadTestMetrics browse = simMetrics("Browse", 30, 10.0);
                    io.testfly.loadtest.LoadTestMetrics buy = simMetrics("Buy", 12, 4.0);
                    io.testfly.loadtest.LoadTestMetrics global = simMetrics("Shop (global)", 42, 14.0);
                    ExecutionMetrics.reset();
                    ExecutionMetrics.recordLoadTest(browse);
                    ExecutionMetrics.recordLoadTest(buy);
                    ExecutionMetrics.recordLoadTestAggregate(global);
                    // a listener re-recording lastMetrics() (the global) must not add a second entry
                    ExecutionMetrics.recordLoadTest(global);

                    assertEquals(ExecutionMetrics.getLoadTests().size(), 3);
                    List<Map<String, Object>> maps = ExecutionMetrics.getLoadTests().stream()
                            .map(ExecutionMetrics::loadTestMetricsToMap).toList();
                    assertEquals(maps.get(0).get("aggregate"), false, "a scenario entry is not an aggregate");
                    assertEquals(maps.get(1).get("aggregate"), false);
                    assertEquals(maps.get(2).get("aggregate"), true, "the simulation's global entry is an aggregate");

                    Map<String, Object> metrics = new LinkedHashMap<>();
                    metrics.put("totalTests", 1);
                    metrics.put("passedTests", 1);
                    metrics.put("failedTests", 0);
                    metrics.put("skippedTests", 0);
                    metrics.put("totalTimeMs", 1000L);
                    metrics.put("loadTests", maps);
                    File jsonFile = new File(testDir.toFile(), "testfly-metrics.json");
                    new ObjectMapper().writeValue(jsonFile, metrics);

                    HtmlReportGenerator.generate(jsonFile);

                    String html = Files.readString(new File(testDir.toFile(), "testfly-report.html").toPath(),
                            StandardCharsets.UTF_8);
                    String data = Files.readString(new File(testDir.toFile(), "testfly-report-data.json").toPath(),
                            StandardCharsets.UTF_8);
                    assertTrue(data.matches("(?s).*\"aggregate\"\\s*:\\s*true.*"), "aggregate flag in report data");
                    assertTrue(html.contains("const scenarioTests = loadTests.filter(lt => !lt.aggregate);"),
                            "summary must skip aggregate entries");
                    assertTrue(html.contains("scenarioTests.forEach(lt => {\n                totalRequests +="),
                            "KPI sums must run over scenario entries only");
                    assertTrue(html.contains("elScenarios.innerText = scenarioTests.length;"),
                            "scenario count must exclude aggregates");
                    assertTrue(html.contains("navCount.innerText = scenarioTests.length;"),
                            "sidebar count must exclude aggregates");
                    assertFalse(html.contains("loadTests.forEach(lt => {\n                totalRequests +="),
                            "no KPI sum over all entries (it would count the aggregate twice)");
                } finally {
                    ExecutionMetrics.reset();
                    deleteRecursively(testDir);
                }
            }
        }
    }

    private static io.testfly.loadtest.LoadTestMetrics simMetrics(String name, int total, double rps) {
        return new io.testfly.loadtest.LoadTestMetrics(name, total, total, 0, rps, 5, 5, 5, 5, 5, 5, 5, 0,
                Map.of(200, (long) total), 3000, 1, "gatling", Map.of(), 5);
    }

    @Test
    public void adapter_generate_createsStandaloneLoadTestReport() throws Exception {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                Path testDir = Files.createTempDirectory("adapter-loadtest-standalone-");
                try {
                    File jsonFile = new File(testDir.toFile(), "testfly-metrics.json");

                    Map<String, Object> metrics = new LinkedHashMap<>();
                    metrics.put("totalTests", 1);
                    metrics.put("passedTests", 1);

                    Map<String, Object> lt = new LinkedHashMap<>();
                    lt.put("scenarioName", "ApiHealth");
                    lt.put("engine", "gatling");
                    lt.put("users", 10);
                    lt.put("totalRequests", 100);
                    lt.put("successfulRequests", 100);
                    lt.put("failedRequests", 0);
                    lt.put("throughputRps", 10.0);
                    lt.put("p95LatencyMs", 50.0);
                    lt.put("errorRate", 0.0);
                    metrics.put("loadTests", List.of(lt));

                    new ObjectMapper().writeValue(jsonFile, metrics);

                    LoadTestReportAdapter adapter = new LoadTestReportAdapter();
                    adapter.generate(jsonFile);

                    File standalone = new File(testDir.toFile(), "loadtest-report.html");
                    assertTrue(standalone.exists(), "Standalone load test report (loadtest-report.html) should be created");

                    String content = Files.readString(standalone.toPath(), StandardCharsets.UTF_8);
                    assertTrue(content.contains("testfly-active-tab', 'loadtest'"),
                            "Standalone report should configure default tab to loadtest");
                } finally {
                    deleteRecursively(testDir);
                }
            }
        }
    }

    @Test
    public void generate_withGatlingReportDirectory_correlatesAndRendersGatlingButton() throws Exception {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                Path testDir = Files.createTempDirectory("html-loadtest-gatling-");
                try {
                    File jsonFile = new File(testDir.toFile(), "testfly-metrics.json");
                    File loadTestDir = new File(testDir.toFile(), "loadtest");
                    File simDir = new File(loadTestDir, "checkoutflow-sim/run123");
                    assertTrue(simDir.mkdirs());
                    File gatlingIndex = new File(simDir, "index.html");
                    Files.writeString(gatlingIndex.toPath(), "<html>Gatling</html>");

                    Map<String, Object> metrics = new LinkedHashMap<>();
                    metrics.put("totalTests", 1);
                    metrics.put("passedTests", 1);

                    Map<String, Object> lt = new LinkedHashMap<>();
                    lt.put("scenarioName", "CheckoutFlow");
                    lt.put("engine", "gatling");
                    lt.put("users", 20);
                    lt.put("totalRequests", 200);
                    lt.put("successfulRequests", 200);
                    lt.put("failedRequests", 0);
                    lt.put("throughputRps", 40.0);
                    metrics.put("loadTests", List.of(lt));

                    new ObjectMapper().writeValue(jsonFile, metrics);

                    HtmlReportGenerator.generate(jsonFile);

                    File htmlFile = new File(testDir.toFile(), "testfly-report.html");
                    assertTrue(htmlFile.exists());
                    String html = Files.readString(htmlFile.toPath(), StandardCharsets.UTF_8);
                    assertTrue(html.contains("Open Gatling Report"), "Report should contain Open Gatling Report button");
                } finally {
                    deleteRecursively(testDir);
                }
            }
        }
    }

    private static void deleteRecursively(Path path) throws Exception {
        if (!Files.exists(path))
            return;
        Files.walk(path)
                .sorted(java.util.Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }
}
