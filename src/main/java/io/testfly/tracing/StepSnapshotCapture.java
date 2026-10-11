package io.testfly.tracing;

import io.testfly.accessibility.AriaSnapshot;
import io.testfly.ai.DomPruner;
import io.testfly.config.TestFlyConfig;
import io.testfly.driver.DriverManager;
import io.testfly.internal.TestFlyContext;
import io.testfly.metrics.ExecutionMetrics;
import io.testfly.metrics.TestTiming;
import io.testfly.steps.StepRecord;
import org.openqa.selenium.WebDriver;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Captures the accessibility tree (and optionally the page HTML) for the step trace.
 *
 * <p>Capturing never fails a test and never touches a browser that is not already running: when
 * tracing is off, no live driver exists, or the page cannot be read, the step simply has no
 * snapshot.
 */
public final class StepSnapshotCapture {

    private static final Logger LOG = Logger.getLogger(StepSnapshotCapture.class.getName());

    /** What was captured for one step; either part may be {@code null}. */
    public record Snapshots(String aria, String dom) {
        static final Snapshots NONE = new Snapshots(null, null);
    }

    private StepSnapshotCapture() {
    }

    /** Captures what {@code tracing.snapshots} asks for, as of now. */
    public static Snapshots captureForStep() {
        TestFlyConfig.Tracing tracing = tracing();
        if (tracing == null || !tracing.isEnabled()) {
            return Snapshots.NONE;
        }
        String mode = tracing.getSnapshots();
        boolean aria = mode.equals("aria") || mode.equals("both");
        boolean dom = mode.equals("dom") || mode.equals("both");
        if (!aria && !dom) {
            return Snapshots.NONE;
        }
        WebDriver driver = liveDriver();
        if (driver == null) {
            return Snapshots.NONE;
        }
        int maxKb = tracing.getSnapshotMaxKb();
        return new Snapshots(aria ? captureAria(driver, maxKb) : null, dom ? captureDom(driver, maxKb) : null);
    }

    /**
     * Records an accessibility snapshot on the last step of a test that did not pass, showing the
     * page as the test left it. Does nothing unless tracing and {@code snapshotOnFailureAfter} are on.
     */
    public static void captureAfterFailure(TestTiming timing) {
        TestFlyConfig.Tracing tracing = tracing();
        if (timing == null || tracing == null || !tracing.isEnabled() || !tracing.isSnapshotOnFailureAfter()) {
            return;
        }
        String mode = tracing.getSnapshots();
        if (mode.equals("none") || mode.equals("dom")) {
            return;
        }
        List<StepRecord> steps = timing.getSteps();
        if (steps.isEmpty()) {
            return;
        }
        WebDriver driver = liveDriver();
        if (driver == null) {
            return;
        }
        String yaml = captureAria(driver, tracing.getSnapshotMaxKb());
        if (yaml != null) {
            steps.get(steps.size() - 1).setAfterAriaSnapshot(yaml);
        }
    }

    /** Same as {@link #captureAfterFailure(TestTiming)} for a test id. */
    public static void captureAfterFailure(String testId) {
        captureAfterFailure(ExecutionMetrics.getTiming(testId));
    }

    static String captureAria(WebDriver driver, int maxKb) {
        try {
            String yaml = AriaSnapshot.toYaml(AriaSnapshot.capture(driver, null));
            return yaml.isBlank() ? null : truncate(yaml, maxKb);
        } catch (RuntimeException e) {
            LOG.log(Level.FINE, "[Trace] ARIA snapshot skipped: " + e.getMessage(), e);
            return null;
        }
    }

    static String captureDom(WebDriver driver, int maxKb) {
        try {
            String html = DomPruner.prune(driver);
            return html == null || html.isBlank() ? null : truncate(html, maxKb);
        } catch (RuntimeException e) {
            LOG.log(Level.FINE, "[Trace] DOM snapshot skipped: " + e.getMessage(), e);
            return null;
        }
    }

    static String truncate(String text, int maxKb) {
        int max = maxKb * 1024;
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "\n… truncated (" + (text.length() - max) + " more characters)";
    }

    private static TestFlyConfig.Tracing tracing() {
        try {
            TestFlyConfig config = TestFlyContext.getConfig();
            return config == null ? null : config.getTracing();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The current thread's driver, only if its session is alive; never creates or recreates one. */
    private static WebDriver liveDriver() {
        try {
            return DriverManager.isDriverAlive() ? DriverManager.getDriver() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
