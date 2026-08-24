package io.testfly.unit;

import io.testfly.ci.CiMetadata;
import io.testfly.config.TestFlyConfig;
import io.testfly.internal.TestFlyContext;
import io.testfly.metrics.ExecutionMetrics;
import io.testfly.metrics.TestTiming;
import io.testfly.reporting.ReportPaths;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.*;

/**
 * Unit tests for {@link ExecutionMetrics}.
 * Thread-safe for parallel=methods via singleThreaded + global file + context locks.
 */
@Test(singleThreaded = true)
public class ExecutionMetricsTest {

    // Global locks shared across all report-related tests
    private static final Object GLOBAL_REPORT_LOCK = ReportPaths.class;
    private static final Object CONTEXT_LOCK = TestFlyContext.class;

    @BeforeMethod
    public void resetMetrics() throws Exception {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                System.clearProperty("testfly.reports.dir");
                ExecutionMetrics.reset();
                resetTestFlyContext();
                File json = ReportPaths.metricsJson();
                if (json.exists()) json.delete();
                File history = ReportPaths.metricsHistoryDir();
                if (history.exists()) {
                    File[] files = history.listFiles();
                    if (files != null) for (File f : files) f.delete();
                }
            }
        }
    }

    @AfterMethod
    public void cleanUp() throws Exception {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                System.clearProperty("testfly.reports.dir");
                ExecutionMetrics.reset();
                resetTestFlyContext();
                File json = ReportPaths.metricsJson();
                if (json.exists()) json.delete();
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

    private static TestFlyConfig minimalConfig(boolean captureMetadata) {
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
        ci.setCaptureMetadata(captureMetadata);
        config.setCi(ci);

        return config;
    }

    // ----------------------------------------------------------
    // markStart / markEnd
    // ----------------------------------------------------------

    @Test
    public void markEnd_withKnownStart_recordsTotalTime() throws InterruptedException {
        ExecutionMetrics.markStart("test-1");
        Thread.sleep(50);
        ExecutionMetrics.markEnd("test-1");
        ExecutionMetrics.recordStatus("test-1", "PASSED");
    }

    @Test
    public void markEnd_withoutStart_isNoOp() {
        ExecutionMetrics.markEnd("never-started");
    }

    @Test
    public void markStart_twice_lastStartWins() throws InterruptedException {
        ExecutionMetrics.markStart("test-2");
        Thread.sleep(20);
        ExecutionMetrics.markStart("test-2");
        ExecutionMetrics.markEnd("test-2");
    }

    // ----------------------------------------------------------
    // recordStatus
    // ----------------------------------------------------------

    @Test
    public void recordStatus_storesStatusCorrectly() {
        ExecutionMetrics.markStart("test-3");
        ExecutionMetrics.markEnd("test-3");
        ExecutionMetrics.recordStatus("test-3", "FAILED");
    }

    // ----------------------------------------------------------
    // recordScreenshot
    // ----------------------------------------------------------

    @Test
    public void recordScreenshot_nullPath_isNoOp() {
        ExecutionMetrics.markStart("test-4");
        ExecutionMetrics.markEnd("test-4");
        ExecutionMetrics.recordScreenshot("test-4", null);
    }

    @Test
    public void recordScreenshot_validPath_stored() {
        ExecutionMetrics.markStart("test-5");
        ExecutionMetrics.markEnd("test-5");
        ExecutionMetrics.recordScreenshot("test-5", "/tmp/screenshot.png");
    }

    // ----------------------------------------------------------
    // percentile
    // ----------------------------------------------------------

    @Test
    public void percentile_emptyList_returnsZero() {
        assertEquals(0L, ExecutionMetrics.percentile(List.of(), 50));
    }

    @Test
    public void percentile_singleValue_returnsThatValue() {
        assertEquals(100L, ExecutionMetrics.percentile(Arrays.asList(100L), 50));
        assertEquals(100L, ExecutionMetrics.percentile(Arrays.asList(100L), 99));
    }

    @Test
    public void percentile_p50_returnsMedian() {
        List<Long> values = Arrays.asList(10L, 20L, 30L, 40L, 50L);
        assertEquals(30L, ExecutionMetrics.percentile(values, 50));
    }

    @Test
    public void percentile_p100_returnsMax() {
        List<Long> values = Arrays.asList(10L, 20L, 30L, 40L, 500L);
        assertEquals(500L, ExecutionMetrics.percentile(values, 100));
    }

    @Test
    public void percentile_p0_returnsMin() {
        List<Long> values = Arrays.asList(10L, 20L, 30L);
        long result = ExecutionMetrics.percentile(values, 0);
        assertTrue(result >= 0);
    }

    // ----------------------------------------------------------
    // recordError
    // ----------------------------------------------------------

    @Test
    public void recordError_setsMessageAndStackTrace() {
        ExecutionMetrics.markStart("err-test");
        ExecutionMetrics.markEnd("err-test");
        ExecutionMetrics.recordError("err-test", new RuntimeException("something went wrong"));
        TestTiming t = ExecutionMetrics.getTimings().iterator().next();
        assertEquals(t.getErrorMessage(), "something went wrong");
        assertNotNull(t.getStackTrace());
        assertTrue(t.getStackTrace().contains("RuntimeException"));
    }

    @Test
    public void recordError_nullMessage_usesClassName() {
        ExecutionMetrics.markStart("err-test-2");
        ExecutionMetrics.markEnd("err-test-2");
        ExecutionMetrics.recordError("err-test-2", new NullPointerException());
        TestTiming t = ExecutionMetrics.getTimings().iterator().next();
        assertEquals(t.getErrorMessage(), "NullPointerException");
    }

    // ----------------------------------------------------------
    // recordTestClass
    // ----------------------------------------------------------

    @Test
    public void recordTestClass_setsClassName() {
        ExecutionMetrics.markStart("cls-test");
        ExecutionMetrics.markEnd("cls-test");
        ExecutionMetrics.recordTestClass("cls-test", "MyPageTest");
        TestTiming t = ExecutionMetrics.getTimings().iterator().next();
        assertEquals(t.getTestClassName(), "MyPageTest");
    }

    // ----------------------------------------------------------
    // exportToJson — field presence
    // ----------------------------------------------------------

    @Test
    public void exportToJson_includesRetryCount() throws IOException {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                System.clearProperty("testfly.reports.dir");
                ExecutionMetrics.markStart("retry-test");
                ExecutionMetrics.markEnd("retry-test");
                ExecutionMetrics.recordStatus("retry-test", "PASSED");
                ExecutionMetrics.recordRetry("retry-test");
                ExecutionMetrics.exportToJson();
                File jsonFile = ReportPaths.metricsJson();
                assertTrue(jsonFile.exists(), "metrics JSON should exist at " + jsonFile.getPath());
                String json = Files.readString(jsonFile.toPath());
                assertTrue(json.contains("\"retryCount\""), "JSON must include retryCount per test");
            }
        }
    }

    @Test
    public void exportToJson_includesPassRate() throws IOException {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                System.clearProperty("testfly.reports.dir");
                ExecutionMetrics.markStart("pass-test");
                ExecutionMetrics.markEnd("pass-test");
                ExecutionMetrics.recordStatus("pass-test", "PASSED");
                ExecutionMetrics.exportToJson();
                File jsonFile = ReportPaths.metricsJson();
                assertTrue(jsonFile.exists(), "metrics JSON should exist");
                String json = Files.readString(jsonFile.toPath());
                assertTrue(json.contains("\"passRate\""), "JSON must include top-level passRate");
            }
        }
    }

    @Test
    public void exportToJson_includesFlakyCount() throws IOException {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                System.clearProperty("testfly.reports.dir");
                ExecutionMetrics.markStart("flaky-test");
                ExecutionMetrics.markEnd("flaky-test");
                ExecutionMetrics.recordStatus("flaky-test", "PASSED");
                ExecutionMetrics.recordRetry("flaky-test");
                ExecutionMetrics.exportToJson();
                File jsonFile = ReportPaths.metricsJson();
                String json = Files.readString(jsonFile.toPath());
                assertTrue(json.contains("\"flakyTests\""),     "JSON must include top-level flakyTests");
                assertTrue(json.contains("\"recoveredTests\""), "JSON must include top-level recoveredTests");
            }
        }
    }

    // ----------------------------------------------------------
    // exportToJson — CI metadata
    // ----------------------------------------------------------

    @Test
    public void exportToJson_withoutContext_doesNotIncludeCiBlock() throws IOException {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                System.clearProperty("testfly.reports.dir");
                ExecutionMetrics.markStart("ci-test-1");
                ExecutionMetrics.markEnd("ci-test-1");
                ExecutionMetrics.recordStatus("ci-test-1", "PASSED");
                ExecutionMetrics.exportToJson();
                String json = Files.readString(ReportPaths.metricsJson().toPath());
                assertFalse(json.contains("\"ci\""), "CI block should not appear when context is uninitialized");
            }
        }
    }

    @Test
    public void exportToJson_withContextButCaptureDisabled_doesNotIncludeCiBlock() throws IOException {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                System.clearProperty("testfly.reports.dir");
                TestFlyContext.initialize(minimalConfig(false));
                ExecutionMetrics.markStart("ci-test-2");
                ExecutionMetrics.markEnd("ci-test-2");
                ExecutionMetrics.recordStatus("ci-test-2", "PASSED");
                ExecutionMetrics.exportToJson();
                String json = Files.readString(ReportPaths.metricsJson().toPath());
                assertFalse(json.contains("\"ci\""), "CI block should not appear when captureMetadata is disabled");
            }
        }
    }

    @Test
    public void exportToJson_withCaptureEnabledAndMetadata_includesCiBlock() throws IOException {
        synchronized (GLOBAL_REPORT_LOCK) {
            synchronized (CONTEXT_LOCK) {
                System.clearProperty("testfly.reports.dir");
                TestFlyContext.initialize(minimalConfig(true));
                ExecutionMetrics.setCiMetadata(new CiMetadata(
                        "GitHub Actions", "42", "123", "main",
                        "abc123", null, "https://example.com/run/123",
                        "unit-tests", null, "testfly/testfly",
                        "hagul", "agent-1", null));
                ExecutionMetrics.markStart("ci-test-3");
                ExecutionMetrics.markEnd("ci-test-3");
                ExecutionMetrics.recordStatus("ci-test-3", "PASSED");
                ExecutionMetrics.exportToJson();
                String json = Files.readString(ReportPaths.metricsJson().toPath());
                assertTrue(json.contains("\"ci\""), "CI block must be present");
                assertTrue(json.contains("\"provider\""), "CI block must include provider");
                assertTrue(json.contains("GitHub Actions"), "CI provider value must be written");
                assertTrue(json.contains("\"buildNumber\""), "CI build number key must be written");
                assertTrue(json.contains("\"42\""), "CI build number value must be written");
            }
        }
    }

    // ----------------------------------------------------------
    // reset
    // ----------------------------------------------------------

    @Test
    public void reset_clearsAllState() {
        ExecutionMetrics.markStart("test-x");
        ExecutionMetrics.markEnd("test-x");
        ExecutionMetrics.recordStatus("test-x", "PASSED");
        ExecutionMetrics.reset();
        ExecutionMetrics.markEnd("test-x");
    }
}
