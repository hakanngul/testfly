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

    @BeforeMethod
    public void setup() throws Exception {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                System.clearProperty("testfly.reports.dir");
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
                System.clearProperty("testfly.reports.dir");
                cleanFiles();
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
        } finally {
            deleteRecursively(testDir);
        }
    }

    @Test
    public void adapter_generate_createsStandaloneLoadTestReport() throws Exception {
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

    private static void deleteRecursively(Path path) throws Exception {
        if (!Files.exists(path))
            return;
        Files.walk(path)
                .sorted(java.util.Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }
}
