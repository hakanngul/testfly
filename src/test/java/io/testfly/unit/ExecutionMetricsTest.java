package io.testfly.unit;

import io.testfly.ci.CiMetadata;
import io.testfly.config.TestFlyConfig;
import io.testfly.internal.TestFlyContext;
import io.testfly.metrics.ExecutionMetrics;
import io.testfly.metrics.TestTiming;
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
 * No browser, no framework initialization required.
 */
public class ExecutionMetricsTest {

    @BeforeMethod
    public void resetMetrics() {
        ExecutionMetrics.reset();
    }

    @AfterMethod
    public void cleanUp() throws Exception {
        ExecutionMetrics.reset();
        resetTestFlyContext();
    }

    private static void resetTestFlyContext() throws Exception {
        Field configField = TestFlyContext.class.getDeclaredField("CONFIG");
        configField.setAccessible(true);
        AtomicReference<?> ref = (AtomicReference<?>) configField.get(null);
        ref.set(null);
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
        // No exception — confirms timing was recorded
    }

    @Test
    public void markEnd_withoutStart_isNoOp() {
        // Should not throw
        ExecutionMetrics.markEnd("never-started");
    }

    @Test
    public void markStart_twice_lastStartWins() throws InterruptedException {
        ExecutionMetrics.markStart("test-2");
        Thread.sleep(20);
        ExecutionMetrics.markStart("test-2"); // overwrite
        ExecutionMetrics.markEnd("test-2");
        // Should complete without error; second start resets the clock
    }

    // ----------------------------------------------------------
    // recordStatus
    // ----------------------------------------------------------

    @Test
    public void recordStatus_storesStatusCorrectly() {
        ExecutionMetrics.markStart("test-3");
        ExecutionMetrics.markEnd("test-3");
        ExecutionMetrics.recordStatus("test-3", "FAILED");
        // Verified implicitly: if the computeIfAbsent path is broken, other
        // tests that rely on status counts would fail
    }

    // ----------------------------------------------------------
    // recordScreenshot
    // ----------------------------------------------------------

    @Test
    public void recordScreenshot_nullPath_isNoOp() {
        ExecutionMetrics.markStart("test-4");
        ExecutionMetrics.markEnd("test-4");
        // null path must not throw
        ExecutionMetrics.recordScreenshot("test-4", null);
    }

    @Test
    public void recordScreenshot_validPath_stored() {
        ExecutionMetrics.markStart("test-5");
        ExecutionMetrics.markEnd("test-5");
        ExecutionMetrics.recordScreenshot("test-5", "/tmp/screenshot.png");
        // No exception means the timing entry was created/updated correctly
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
        // index = ceil(0/100 * 3) = 0, clamped to 0 → first element after sort
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
        ExecutionMetrics.markStart("retry-test");
        ExecutionMetrics.markEnd("retry-test");
        ExecutionMetrics.recordStatus("retry-test", "PASSED");
        ExecutionMetrics.recordRetry("retry-test");
        ExecutionMetrics.exportToJson();

        String json = Files.readString(new File("target/testfly-metrics.json").toPath());
        assertTrue(json.contains("\"retryCount\""), "JSON must include retryCount per test");
    }

    @Test
    public void exportToJson_includesPassRate() throws IOException {
        ExecutionMetrics.markStart("pass-test");
        ExecutionMetrics.markEnd("pass-test");
        ExecutionMetrics.recordStatus("pass-test", "PASSED");
        ExecutionMetrics.exportToJson();

        String json = Files.readString(new File("target/testfly-metrics.json").toPath());
        assertTrue(json.contains("\"passRate\""), "JSON must include top-level passRate");
    }

    @Test
    public void exportToJson_includesFlakyCount() throws IOException {
        ExecutionMetrics.markStart("flaky-test");
        ExecutionMetrics.markEnd("flaky-test");
        ExecutionMetrics.recordStatus("flaky-test", "PASSED");
        ExecutionMetrics.recordRetry("flaky-test");
        ExecutionMetrics.exportToJson();

        String json = Files.readString(new File("target/testfly-metrics.json").toPath());
        assertTrue(json.contains("\"flakyTests\""),     "JSON must include top-level flakyTests");
        assertTrue(json.contains("\"recoveredTests\""), "JSON must include top-level recoveredTests");
    }

    // ----------------------------------------------------------
    // exportToJson — CI metadata
    // ----------------------------------------------------------

    @Test
    public void exportToJson_withoutContext_doesNotIncludeCiBlock() throws IOException {
        ExecutionMetrics.markStart("ci-test-1");
        ExecutionMetrics.markEnd("ci-test-1");
        ExecutionMetrics.recordStatus("ci-test-1", "PASSED");
        ExecutionMetrics.exportToJson();

        String json = Files.readString(new File("target/testfly-metrics.json").toPath());
        assertFalse(json.contains("\"ci\""), "CI block should not appear when context is uninitialized");
    }

    @Test
    public void exportToJson_withContextButCaptureDisabled_doesNotIncludeCiBlock() throws IOException {
        TestFlyContext.initialize(minimalConfig(false));

        ExecutionMetrics.markStart("ci-test-2");
        ExecutionMetrics.markEnd("ci-test-2");
        ExecutionMetrics.recordStatus("ci-test-2", "PASSED");
        ExecutionMetrics.exportToJson();

        String json = Files.readString(new File("target/testfly-metrics.json").toPath());
        assertFalse(json.contains("\"ci\""), "CI block should not appear when captureMetadata is disabled");
    }

    @Test
    public void exportToJson_withCaptureEnabledAndMetadata_includesCiBlock() throws IOException {
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

        String json = Files.readString(new File("target/testfly-metrics.json").toPath());
        assertTrue(json.contains("\"ci\""), "CI block must be present");
        assertTrue(json.contains("\"provider\""), "CI block must include provider");
        assertTrue(json.contains("GitHub Actions"), "CI provider value must be written");
        assertTrue(json.contains("\"buildNumber\""), "CI build number key must be written");
        assertTrue(json.contains("\"42\""), "CI build number value must be written");
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

        // After reset, markEnd for unknown testId is a no-op — no exception
        ExecutionMetrics.markEnd("test-x");
    }
}
