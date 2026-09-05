package io.testfly.unit;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.testfly.config.TestFlyConfig;
import io.testfly.internal.TestFlyContext;
import io.testfly.metrics.ExecutionMetrics;
import io.testfly.reporting.HtmlReportGenerator;
import io.testfly.reporting.ReportPaths;

/**
 * Unit tests for {@link HtmlReportGenerator}.
 * Thread-safe for parallel=methods via singleThreaded + global report + context locks.
 */
@Test(singleThreaded = true)
public class HtmlReportGeneratorTest {

    private static final Object GLOBAL_REPORT_LOCK = ReportPaths.class;
    private static final Object CONTEXT_LOCK = TestFlyContext.class;

    @BeforeMethod
    public void setup() throws Exception {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                System.clearProperty("testfly.reports.dir");
                File html = ReportPaths.htmlReport();
                File json = ReportPaths.metricsJson();
                if (html.exists()) html.delete();
                if (json.exists()) json.delete();
                resetTestFlyContext();
                ExecutionMetrics.reset();
            }
        }
    }

    @AfterMethod
    public void cleanup() throws Exception {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                System.clearProperty("testfly.reports.dir");
                File html = ReportPaths.htmlReport();
                File json = ReportPaths.metricsJson();
                if (html.exists()) html.delete();
                if (json.exists()) json.delete();
                resetTestFlyContext();
                ExecutionMetrics.reset();
            }
        }
    }

    private static void resetTestFlyContext() throws Exception {
        Field configField = TestFlyContext.class.getDeclaredField("CONFIG");
        configField.setAccessible(true);
        AtomicReference<?> ref = (AtomicReference<?>) configField.get(null);
        ref.set(null);
        TestFlyContext.clearCurrentTestId();
        try {
            Field ciField = ExecutionMetrics.class.getDeclaredField("ciMetadata");
            ciField.setAccessible(true);
            ciField.set(null, null);
        } catch (Exception ignored) {}
    }

    private static TestFlyConfig minimalConfig() {
        TestFlyConfig config = new TestFlyConfig();

        TestFlyConfig.Browser browser = new TestFlyConfig.Browser();
        browser.setName("chrome");
        config.setBrowser(browser);

        TestFlyConfig.Execution execution = new TestFlyConfig.Execution();
        execution.setMode("local");
        execution.setBaseUrl("https://example.com");
        execution.setMaxActiveSessions(5);
        config.setExecution(execution);

        TestFlyConfig.Timeouts timeouts = new TestFlyConfig.Timeouts();
        timeouts.setExplicit(10);
        timeouts.setPageLoad(30);
        config.setTimeouts(timeouts);

        TestFlyConfig.Ci ci = new TestFlyConfig.Ci();
        ci.setCaptureMetadata(true);
        config.setCi(ci);

        return config;
    }

    private static void writeMetricsWithCi() throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("totalTests", 1);
        root.put("passedTests", 1);
        root.put("failedTests", 0);
        root.put("skippedTests", 0);
        root.put("passRate", 100.0);
        root.put("flakyTests", 0);
        root.put("recoveredTests", 0);
        root.put("totalTimeMs", 250L);
        root.put("averageTimeMs", 250L);

        Map<String, Object> ci = new LinkedHashMap<>();
        ci.put("provider", "GitHub Actions");
        ci.put("buildNumber", "42");
        ci.put("buildId", "123");
        ci.put("branch", "main");
        ci.put("commitSha", "abc123");
        ci.put("repository", "testfly/testfly");
        ci.put("actor", "hagul");
        ci.put("jobName", "unit-tests");
        ci.put("buildUrl", "https://github.com/hakanngul/testfly/actions/runs/123");
        root.put("ci", ci);

        ObjectMapper mapper = new ObjectMapper();
        File json = ReportPaths.metricsJson();
        json.getParentFile().mkdirs();
        mapper.writeValue(json, root);
    }

    @Test
    public void generate_createsHtmlReport() throws IOException {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                System.clearProperty("testfly.reports.dir");
                TestFlyContext.initialize(minimalConfig());
                writeMetricsWithCi();
                HtmlReportGenerator.generate();
                File html = ReportPaths.htmlReport();
                assertTrue(html.exists(), "HTML report file should be created");
            }
        }
    }

    @Test
    public void generate_includesBuildMetadata() throws IOException {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                System.clearProperty("testfly.reports.dir");
                TestFlyContext.initialize(minimalConfig());
                writeMetricsWithCi();
                HtmlReportGenerator.generate();
                File htmlFile = ReportPaths.htmlReport();
                assertTrue(htmlFile.exists(), "HTML report file should be created — generate() may have failed due to concurrent TestFlyContext clear: " + htmlFile.getPath());
                String html = Files.readString(htmlFile.toPath());
                assertTrue(html.contains("Build Metadata"), "Report must contain build metadata section");
                assertTrue(html.contains("GitHub Actions"), "CI provider must be rendered");
                assertTrue(html.contains("42"), "Build number must be rendered");
                assertTrue(html.contains("main"), "Branch must be rendered");
                assertTrue(html.contains("testfly/testfly"), "Repository must be rendered");
            }
        }
    }

    @Test
    public void generate_buildUrlIsLinked() throws IOException {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                System.clearProperty("testfly.reports.dir");
                TestFlyContext.initialize(minimalConfig());
                writeMetricsWithCi();
                HtmlReportGenerator.generate();
                String html = Files.readString(ReportPaths.htmlReport().toPath());
                assertTrue(html.contains("https://github.com/hakanngul/testfly/actions/runs/123"),
                        "Build URL must be rendered as a link");
            }
        }
    }

    @Test
    public void generate_noMetricsJson_isNoOp() {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                System.clearProperty("testfly.reports.dir");
                TestFlyContext.initialize(minimalConfig());
                File json = ReportPaths.metricsJson();
                if (json.exists()) json.delete();
                HtmlReportGenerator.generate();
                assertFalse(ReportPaths.htmlReport().exists(),
                        "HTML report should not be created when metrics JSON is missing");
            }
        }
    }

    @Test
    public void generate_archivesTimestampedReport() throws IOException {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                System.clearProperty("testfly.reports.dir");
                TestFlyContext.initialize(minimalConfig());
                writeMetricsWithCi();
                HtmlReportGenerator.generate();

                File reportsDir = ReportPaths.reportsHistoryDir();
                assertTrue(reportsDir.exists(), "Reports history directory should exist");
                File[] archives = reportsDir.listFiles((d, name) -> name.startsWith("testfly-report-") && name.endsWith(".html"));
                assertNotNull(archives);
                assertTrue(archives.length > 0, "At least one timestamped archive report should be created");
            }
        }
    }

    @Test
    public void generate_embedsRunHistoryAndCopyButtons() throws IOException {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                System.clearProperty("testfly.reports.dir");
                TestFlyContext.initialize(minimalConfig());

                // Create metrics with a failed test and stack trace
                Map<String, Object> root = new LinkedHashMap<>();
                root.put("totalTests", 1);
                root.put("passedTests", 0);
                root.put("failedTests", 1);
                root.put("skippedTests", 0);
                root.put("passRate", 0.0);
                root.put("totalTimeMs", 500L);

                Map<String, Object> testEntry = new LinkedHashMap<>();
                testEntry.put("testId", "com.example.SampleTest.failedMethod");
                testEntry.put("status", "FAILED");
                testEntry.put("errorMessage", "Assertion failed: expected true but was false");
                testEntry.put("stackTrace", "java.lang.AssertionError: expected true but was false\n\tat com.example.SampleTest.failedMethod(SampleTest.java:42)");
                testEntry.put("aiAnalysis", "The boolean flag was false. Check configuration initialization.");
                root.put("tests", java.util.List.of(testEntry));

                ObjectMapper mapper = new ObjectMapper();
                File json = ReportPaths.metricsJson();
                json.getParentFile().mkdirs();
                mapper.writeValue(json, root);

                HtmlReportGenerator.generate();
                String html = Files.readString(ReportPaths.htmlReport().toPath());

                assertTrue(html.contains("run-selector-wrap"), "Must contain run selector dropdown container");
                assertTrue(html.contains("runSelect"), "Must contain run selection element");
                assertTrue(html.contains("copyStackTrace"), "Must contain copy stack trace handler");
                assertTrue(html.contains("AI Failure Analysis") || html.contains("TestFly AI Analysis"), "Must contain AI analysis card");
                assertTrue(html.contains("tab-history"), "Must contain run history tab");
            }
        }
    }

    @Test
    public void generate_exportsJsonDataAndCumulativeTotals() throws IOException {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                System.clearProperty("testfly.reports.dir");
                TestFlyContext.initialize(minimalConfig());
                writeMetricsWithCi();

                HtmlReportGenerator.generate();

                // 1. Verify testfly-report-data.json was created
                File dataJson = ReportPaths.reportDataJson();
                assertTrue(dataJson.exists(), "testfly-report-data.json should be created");
                String jsonData = Files.readString(dataJson.toPath());
                assertTrue(jsonData.contains("cumulativeTotals"), "JSON report data must contain cumulativeTotals");
                assertTrue(jsonData.contains("totalTests"), "JSON report data must contain totalTests");

                // 2. Verify HTML report embeds JSON data and has Allure styling (no purple)
                String html = Files.readString(ReportPaths.htmlReport().toPath());
                assertTrue(html.contains("id=\"testfly-data\""), "HTML must contain embedded testfly-data script");
                assertTrue(html.contains("cumulativeTotals"), "Embedded data must include cumulativeTotals");
                assertTrue(html.contains("id=\"nav-history\""), "HTML must contain id='nav-history' on navigation item");
                assertTrue(html.contains("id=\"tab-history\""), "HTML must contain id='tab-history' pane");
                assertTrue(html.contains("id=\"tab-flakiness\""), "HTML must contain tab-flakiness panel");
                assertTrue(html.contains("Flakiness Radar"), "HTML must contain Flakiness Radar navigation");
                assertTrue(html.contains("inspectTestCase"), "HTML must contain inspectTestCase function");
                assertTrue(html.contains("api-step-details"), "HTML must contain api-step-details style for tracing");
                assertTrue(html.contains("#97cc64"), "HTML must use Allure green (#97cc64)");
                assertTrue(html.contains("#fd5a3e"), "HTML must use Allure red (#fd5a3e)");
                assertFalse(html.contains("#6366f1"), "HTML must not contain purple (#6366f1)");
                assertFalse(html.contains("#4f46e5"), "HTML must not contain purple (#4f46e5)");
            }
        }
    }
}
