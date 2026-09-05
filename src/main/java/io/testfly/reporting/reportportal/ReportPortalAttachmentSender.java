package io.testfly.reporting.reportportal;

import java.io.File;

/**
 * Sends screenshot and AI failure-analysis artifacts to ReportPortal.
 *
 * <p>The primary path is {@link #sendImmediate(String, String, String)}, invoked
 * from {@link io.testfly.listeners.TestExecutionListener#onTestFailure(org.testng.ITestResult)}
 * while the RP test item is still open. ReportPortal rejects log/attachment
 * calls after the RP listener closes the item, so the attachment must be sent
 * in-flight.
 *
 * <p>This class delegates to {@link ReportPortalLogger}, which uses the official
 * {@code ReportPortal.emitLog()} API. That API talks to the RP client singleton
 * already initialized by the TestNG agent with the correct endpoint, project,
 * API key and launch UUID. The previous direct-REST implementation created a
 * second un-configured client and silently failed authentication.
 *
 * <p>The {@link io.testfly.reporting.ReportAdapter#generate(java.io.File)}
 * implementation is kept as a thin no-op fallback for backward compatibility
 * with the SPI/adapter registry; artifacts are already sent during
 * {@code onTestFailure}.
 */
public final class ReportPortalAttachmentSender implements io.testfly.reporting.ReportAdapter {

    @Override
    public String getName() {
        return "reportportal-attachments";
    }

    @Override
    public void generate(java.io.File metricsJson) {
        // Artifacts are already sent during onTestFailure; post-hoc is too late
        // because RP has closed the test items by suite finish.
    }

    /**
     * Sends the screenshot and/or AI analysis for {@code testId} directly to the
     * current ReportPortal test item. Must be called while the RP item is open.
     *
     * @param testId          fully-qualified TestNG test id, e.g. {@code com.example.MyTest#method}
     * @param screenshotPath  optional path to a PNG screenshot file
     * @param aiAnalysis      optional AI failure-analysis text
     */
    public static void sendImmediate(String testId, String screenshotPath, String aiAnalysis) {
        if (!ReportPortalLogger.isAvailable()) {
            return; // RP client not on classpath
        }

        if (!io.testfly.internal.TestFlyContext.isInitialized()) {
            return;
        }
        io.testfly.config.TestFlyConfig cfg = io.testfly.internal.TestFlyContext.getConfig();
        if (cfg == null || cfg.getReporting() == null
                || cfg.getReporting().getReportPortal() == null
                || !cfg.getReporting().getReportPortal().isEnabled()) {
            return;
        }
        String key = io.testfly.config.DotEnvLoader.resolve(cfg.getReporting().getReportPortal().getApiKey());
        if (key == null || key.trim().isEmpty() || key.startsWith("${")) {
            return;
        }

        if ((screenshotPath == null || screenshotPath.isBlank())
                && (aiAnalysis == null || aiAnalysis.isBlank())) {
            return;
        }

        int sent = 0;
        if (screenshotPath != null && !screenshotPath.isBlank()) {
            File screenshot = new File(screenshotPath);
            if (screenshot.exists()) {
                boolean ok = ReportPortalLogger.logWithAttachment(
                        "\ud83d\udcf8 Screenshot on failure", "INFO", screenshot);
                if (ok) {
                    sent++;
                } else {
                    System.err.println("[TestFly] ReportPortal screenshot not sent for " + testId
                            + " (no active RP test item?)");
                }
            }
        }

        if (aiAnalysis != null && !aiAnalysis.isBlank()) {
            boolean ok = ReportPortalLogger.log(
                    "\ud83e\udd16 AI Failure Analysis:\n" + aiAnalysis, "INFO");
            if (ok) {
                sent++;
            } else {
                System.err.println("[TestFly] ReportPortal AI analysis not sent for " + testId
                        + " (no active RP test item?)");
            }
        }

        if (sent > 0) {
            System.out.println("[TestFly] ReportPortal attachments sent for " + testId + ": " + sent + " log(s)");
        }
    }
}
