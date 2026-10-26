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

    // ── SLA, partial and step P99 on the load-test card (plan item 3) ────
    //
    // Marker strings the template must contain (exact source text, as in the tests above):
    private static final String M_SLA_RULES = "const slaRules = Array.isArray(lt.sla) ? lt.sla : [];";
    private static final String M_SLA_FAILED = "const slaFailed = slaRules.filter(r => r.passed === false).length;";
    private static final String M_IS_SUCCESS = "const isSuccess = (lt.failedRequests || 0) === 0 && slaFailed === 0;";
    private static final String M_OLD_IS_SUCCESS = "const isSuccess = (lt.failedRequests || 0) === 0;";
    private static final String M_IS_PARTIAL = "const isPartial = lt.partial === true;";
    private static final String M_CARD_STATUS = "const cardStatus = !isSuccess ? 'FAILED' : isPartial ? 'WARN' : 'PASSED';";
    private static final String M_PARTIAL_BADGE = "partialBadge.textContent = 'PARTIAL';";
    private static final String M_PARTIAL_TOOLTIP = "partialBadge.title = ";
    private static final String M_AGGREGATE_IF = "if (lt.aggregate) {";
    private static final String M_AGGREGATE_BADGE = "aggregateBadge.textContent = 'Aggregate';";
    private static final String M_SLA_HEADER = "slaHeader.textContent = 'SLA';";
    private static final String M_SLA_COLUMNS =
            "['Scope', 'Metric', 'Comparison', 'Threshold', 'Actual', 'Result', 'Reason']";
    private static final String M_SLA_ACTUAL = "ltMeasured(r.actual)";
    private static final String M_SLA_RESULT = "r.passed ? 'PASS' : 'FAIL'";
    private static final String M_STEP_P99_HEADER = "{ text: 'P99 Latency', align: 'right' }";
    private static final String M_STEP_P99_CELL = "tdP99.textContent = ltMs(st.p99LatencyMs);";
    private static final String M_SUMMARY_SLA_FAILED =
            "const slaFailedScenarios = scenarioTests.filter(lt => (lt.sla || []).some(r => r.passed === false)).length;";

    /** One partial scenario with a failed global P95 rule, a passed step rule and an unmeasured P90 rule. */
    private static io.testfly.loadtest.LoadTestMetrics slaPartialMetrics() {
        io.testfly.loadtest.SlaRule p95 = new io.testfly.loadtest.SlaRule(io.testfly.loadtest.SlaRule.GLOBAL,
                io.testfly.loadtest.SlaRule.Metric.P95, io.testfly.loadtest.SlaRule.Comparison.BELOW, 100);
        io.testfly.loadtest.SlaRule loginErr = new io.testfly.loadtest.SlaRule("login",
                io.testfly.loadtest.SlaRule.Metric.ERROR_RATE, io.testfly.loadtest.SlaRule.Comparison.BELOW, 0.05);
        io.testfly.loadtest.SlaRule p90 = new io.testfly.loadtest.SlaRule(io.testfly.loadtest.SlaRule.GLOBAL,
                io.testfly.loadtest.SlaRule.Metric.P90, io.testfly.loadtest.SlaRule.Comparison.BELOW, 200);
        List<io.testfly.loadtest.SlaResult> sla = List.of(
                new io.testfly.loadtest.SlaResult(p95, 120, false, "p95 120 ms is not below 100 ms"),
                new io.testfly.loadtest.SlaResult(loginErr, 0.0, true, ""),
                new io.testfly.loadtest.SlaResult(p90, Double.NaN, false, "p90 was not measured"));
        Map<String, io.testfly.loadtest.LoadTestMetrics.StepMetrics> steps = new LinkedHashMap<>();
        steps.put("login", new io.testfly.loadtest.LoadTestMetrics.StepMetrics("login", 40, 40, 0, 90, 0.0, 140));
        return new io.testfly.loadtest.LoadTestMetrics("SlaPartial", 40, 40, 0, 8.0, 60, 50, Double.NaN, 120, 140,
                10, 150, 0.0, Map.of(200, 40L), 5000, 4, "gatling", steps, 70, true, sla);
    }

    /** Writes a metrics file with the given load-test maps, generates the report, returns {html, data}. */
    private static String[] generateReport(Path testDir, List<Map<String, Object>> loadTests) throws Exception {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("totalTests", 1);
        metrics.put("passedTests", 1);
        metrics.put("failedTests", 0);
        metrics.put("skippedTests", 0);
        metrics.put("totalTimeMs", 1000L);
        metrics.put("loadTests", loadTests);
        File jsonFile = new File(testDir.toFile(), "testfly-metrics.json");
        new ObjectMapper().writeValue(jsonFile, metrics);

        HtmlReportGenerator.generate(jsonFile);

        String html = Files.readString(new File(testDir.toFile(), "testfly-report.html").toPath(),
                StandardCharsets.UTF_8);
        String data = Files.readString(new File(testDir.toFile(), "testfly-report-data.json").toPath(),
                StandardCharsets.UTF_8);
        return new String[] {html, data};
    }

    @Test
    public void generate_withSlaAndPartial_exportsDataAndRendersSlaTableAndPartialBadge() throws Exception {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                Path testDir = Files.createTempDirectory("html-loadtest-sla-");
                try {
                    String[] out = generateReport(testDir,
                            List.of(ExecutionMetrics.loadTestMetricsToMap(slaPartialMetrics())));
                    String html = out[0];
                    String data = out[1];

                    // data keys (already exported by ExecutionMetrics; guard that the report passes them through)
                    assertTrue(data.matches("(?s).*\"partial\"\\s*:\\s*true.*"), "partial flag in report data");
                    assertTrue(data.matches("(?s).*\"sla\"\\s*:\\s*\\[\\s*\\{.*"), "sla rows in report data");
                    assertTrue(data.matches("(?s).*\"passed\"\\s*:\\s*false.*"), "failed SLA row in report data");
                    assertTrue(data.matches("(?s).*\"passed\"\\s*:\\s*true.*"), "passed SLA row in report data");
                    assertTrue(data.matches("(?s).*\"actual\"\\s*:\\s*null.*"), "unmeasured actual stays null");
                    assertTrue(data.contains("p95 120 ms is not below 100 ms"), "SLA reason in report data");

                    // the card renders them
                    assertTrue(html.contains(M_SLA_RULES), "card must read lt.sla defensively: " + M_SLA_RULES);
                    assertTrue(html.contains(M_SLA_HEADER), "SLA table header: " + M_SLA_HEADER);
                    assertTrue(html.contains(M_SLA_COLUMNS), "SLA table columns: " + M_SLA_COLUMNS);
                    assertTrue(html.contains(M_SLA_ACTUAL), "an unmeasured actual must render as n/a: " + M_SLA_ACTUAL);
                    assertTrue(html.contains(M_SLA_RESULT), "PASS/FAIL cell: " + M_SLA_RESULT);
                    assertTrue(html.contains(M_IS_PARTIAL), "partial flag read on the card: " + M_IS_PARTIAL);
                    assertTrue(html.contains(M_PARTIAL_BADGE), "PARTIAL badge: " + M_PARTIAL_BADGE);
                    assertTrue(html.contains(M_PARTIAL_TOOLTIP), "PARTIAL badge needs a tooltip: " + M_PARTIAL_TOOLTIP);
                } finally {
                    deleteRecursively(testDir);
                }
            }
        }
    }

    @Test
    public void cardStatus_failsOnAFailedSlaRule_andWarnsOnAPartialRun() throws Exception {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                Path testDir = Files.createTempDirectory("html-loadtest-status-");
                try {
                    String html = generateReport(testDir,
                            List.of(ExecutionMetrics.loadTestMetricsToMap(slaPartialMetrics())))[0];

                    assertTrue(html.contains(M_SLA_FAILED), "card must count failed SLA rules: " + M_SLA_FAILED);
                    assertTrue(html.contains(M_IS_SUCCESS),
                            "a failed SLA rule must fail the card even without failed requests: " + M_IS_SUCCESS);
                    assertFalse(html.contains(M_OLD_IS_SUCCESS),
                            "the card status may not depend on failedRequests only");
                    assertTrue(html.contains(M_CARD_STATUS), "FAILED, else WARN when partial, else PASSED: "
                            + M_CARD_STATUS);
                    assertTrue(html.contains(M_SUMMARY_SLA_FAILED),
                            "summary must count scenarios with a failed SLA, aggregates excluded: "
                                    + M_SUMMARY_SLA_FAILED);
                } finally {
                    deleteRecursively(testDir);
                }
            }
        }
    }

    @Test
    public void card_showsStepP99_andLabelsAggregateEntries() throws Exception {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                Path testDir = Files.createTempDirectory("html-loadtest-p99-");
                try {
                    ExecutionMetrics.reset();
                    ExecutionMetrics.recordLoadTest(simMetrics("Browse", 30, 10.0));
                    ExecutionMetrics.recordLoadTestAggregate(simMetrics("Shop (global)", 30, 10.0));
                    List<Map<String, Object>> maps = new java.util.ArrayList<>(ExecutionMetrics.getLoadTests().stream()
                            .map(ExecutionMetrics::loadTestMetricsToMap).toList());
                    maps.add(ExecutionMetrics.loadTestMetricsToMap(slaPartialMetrics()));
                    String[] out = generateReport(testDir, maps);
                    String html = out[0];
                    String data = out[1];

                    assertTrue(data.matches("(?s).*\"p99LatencyMs\"\\s*:\\s*140\\.0.*"), "step p99 in report data");
                    assertTrue(html.contains(M_STEP_P99_HEADER), "step table P99 column: " + M_STEP_P99_HEADER);
                    assertTrue(html.contains(M_STEP_P99_CELL), "step P99 cell renders n/a when null: "
                            + M_STEP_P99_CELL);
                    assertTrue(html.contains(M_AGGREGATE_IF), "aggregate entries are marked: " + M_AGGREGATE_IF);
                    assertTrue(html.contains(M_AGGREGATE_BADGE), "Aggregate label: " + M_AGGREGATE_BADGE);
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
