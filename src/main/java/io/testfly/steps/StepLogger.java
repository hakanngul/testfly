package io.testfly.steps;

import io.testfly.api.TestFlyApi;
import io.testfly.internal.TestFlyContext;
import io.testfly.metrics.ExecutionMetrics;
import io.testfly.reporting.ScreenshotManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * StepLogger allows test authors to log named steps during test execution.
 *
 * <p>Steps appear in the HTML report as a timeline inside each test's detail panel.
 * Screenshots can be captured at any step by passing {@code true} or {@link StepStatus}.
 *
 * <p>Usage:
 * <pre>
 *   StepLogger.step("Navigate to login page");
 *   StepLogger.step("After credentials entered", true);     // with screenshot
 *   StepLogger.step("Verify dashboard visible", StepStatus.PASS);
 * </pre>
 *
 * <p>Thread-safe — safe to use in parallel test execution.
 */
@TestFlyApi(since = "0.7.0")
public final class StepLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(StepLogger.class);

    private StepLogger() {}

    /** Log a step with INFO status and no screenshot. */
    public static void step(String name) {
        log(name, StepStatus.INFO, false);
    }

    /** Log a step with INFO status; optionally capture a screenshot. */
    public static void step(String name, boolean screenshot) {
        log(name, StepStatus.INFO, screenshot);
    }

    /** Log a step with an explicit status and no screenshot. */
    public static void step(String name, StepStatus status) {
        log(name, status, false);
    }

    /** Log a step with an explicit status; optionally capture a screenshot. */
    public static void step(String name, StepStatus status, boolean screenshot) {
        log(name, status, screenshot);
    }

    /** Log a step with an explicit status and a pre-captured base64 screenshot (e.g. diff image). */
    public static void stepWithScreenshot(String name, StepStatus status, String base64Screenshot) {
        logWithBase64(name, status, base64Screenshot);
    }

    // ------------------------------------------------------------------

    private static void logWithBase64(String name, StepStatus status, String base64) {
        String testId = TestFlyContext.getCurrentTestId();
        if (testId == null) {
            LOGGER.warn("[STEP] No active test context — step ignored: {}", name);
            return;
        }
        long startTime = ExecutionMetrics.getTestStartTime(testId);
        long offsetMs  = startTime > 0 ? System.currentTimeMillis() - startTime : 0L;
        ExecutionMetrics.recordStep(testId, new StepRecord(name, offsetMs, status.name(), base64));
        LOGGER.info(formatStep(name, status, offsetMs));
    }

    private static void log(String name, StepStatus status, boolean screenshot) {
        String testId = TestFlyContext.getCurrentTestId();
        if (testId == null) {
            LOGGER.warn("[STEP] No active test context — step ignored: {}", name);
            return;
        }

        long startTime = ExecutionMetrics.getTestStartTime(testId);
        long offsetMs  = startTime > 0 ? System.currentTimeMillis() - startTime : 0L;

        String base64 = null;
        if (screenshot) {
            base64 = ScreenshotManager.captureAsBase64();
        }

        ExecutionMetrics.recordStep(testId, new StepRecord(name, offsetMs, status.name(), base64));
        LOGGER.info(formatStep(name, status, offsetMs));
    }

    private static String formatStep(String name, StepStatus status, long offsetMs) {
        String duration = String.format("%.3fs", offsetMs / 1000.0);
        if (status == StepStatus.INFO) {
            return String.format("[STEP] %s | Duration: %s", name, duration);
        }
        return String.format("[STEP][%s] %s | Duration: %s", status.name(), name, duration);
    }
}
