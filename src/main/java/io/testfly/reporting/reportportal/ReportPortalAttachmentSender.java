package io.testfly.reporting.reportportal;

import java.io.File;
import java.util.Locale;

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

        io.testfly.config.TestFlyConfig cfg;
        try {
            if (!io.testfly.internal.TestFlyContext.isInitialized()) {
                return;
            }
            cfg = io.testfly.internal.TestFlyContext.getConfig();
        } catch (IllegalStateException e) {
            return;
        }
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

    /**
     * Sends formatted Gatling load test summary metrics and the subprocess execution log
     * to ReportPortal for {@code testId}. Must be called while the RP test item is open.
     *
     * @param testId  fully-qualified test id
     * @param metrics the load test metrics
     */
    public static void sendLoadTestMetrics(String testId, io.testfly.loadtest.LoadTestMetrics metrics) {
        if (!ReportPortalLogger.isAvailable() || metrics == null) {
            return;
        }

        io.testfly.config.TestFlyConfig cfg;
        try {
            if (!io.testfly.internal.TestFlyContext.isInitialized()) {
                return;
            }
            cfg = io.testfly.internal.TestFlyContext.getConfig();
        } catch (IllegalStateException e) {
            return;
        }
        if (cfg == null || cfg.getReporting() == null
                || cfg.getReporting().getReportPortal() == null
                || !cfg.getReporting().getReportPortal().isEnabled()) {
            return;
        }
        String key = io.testfly.config.DotEnvLoader.resolve(cfg.getReporting().getReportPortal().getApiKey());
        if (key == null || key.trim().isEmpty() || key.startsWith("${")) {
            return;
        }

        String markdown = buildLoadTestMarkdown(metrics);
        boolean logged = ReportPortalLogger.log(markdown, "INFO");
        if (logged) {
            System.out.println("[TestFly] ReportPortal load test metrics sent for " + testId);
        }

        File logFile = findSubprocessLog(metrics);
        if (logFile != null && logFile.exists()) {
            ReportPortalLogger.logWithAttachment("📋 Gatling Subprocess Execution Log", "INFO", logFile);
        }
    }

    private static String buildLoadTestMarkdown(io.testfly.loadtest.LoadTestMetrics metrics) {
        StringBuilder sb = new StringBuilder();
        sb.append("⚡ **Gatling Load Test Metrics Summary**\n\n");
        sb.append("• **Scenario:** `").append(metrics.scenarioName()).append("`\n");
        sb.append("• **Engine:** ").append(metrics.engine() != null ? metrics.engine().toUpperCase(Locale.ROOT) : "GATLING").append("\n");
        sb.append("• **Concurrent Users:** ").append(metrics.users()).append(" VUs\n");
        sb.append("• **Duration:** ").append(String.format(Locale.ROOT, "%.1fs", metrics.durationMs() / 1000.0)).append("\n");
        sb.append("• **Total Requests:** ").append(metrics.totalRequests())
          .append(" (OK: ").append(metrics.successfulRequests())
          .append(", KO: ").append(metrics.failedRequests()).append(")\n");
        sb.append("• **Throughput:** ").append(String.format(Locale.ROOT, "%.1f req/s", metrics.throughputRps())).append("\n");
        sb.append("• **Error Rate:** ").append(String.format(Locale.ROOT, "%.2f%%", metrics.errorRate() * 100)).append("\n\n");

        sb.append("**Latency Breakdown:**\n");
        sb.append("• P50: ").append(Math.round(metrics.p50LatencyMs())).append(" ms | ")
          .append("P90: ").append(Math.round(metrics.p90LatencyMs())).append(" ms | ")
          .append("P95: ").append(Math.round(metrics.p95LatencyMs())).append(" ms | ")
          .append("P99: ").append(Math.round(metrics.p99LatencyMs())).append(" ms\n");
        sb.append("• Min: ").append(Math.round(metrics.minLatencyMs())).append(" ms | ")
          .append("Mean: ").append(Math.round(metrics.meanLatencyMs())).append(" ms | ")
          .append("Max: ").append(Math.round(metrics.maxLatencyMs())).append(" ms\n\n");

        if (metrics.steps() != null && !metrics.steps().isEmpty()) {
            sb.append("**Scenario Steps:**\n\n");
            sb.append("| Step | Total | OK | KO | P95 | Error % |\n");
            sb.append("| :--- | ---: | ---: | ---: | ---: | ---: |\n");
            for (io.testfly.loadtest.LoadTestMetrics.StepMetrics st : metrics.steps().values()) {
                sb.append("| `").append(st.name()).append("` | ")
                  .append(st.totalRequests()).append(" | ")
                  .append(st.successfulRequests()).append(" | ")
                  .append(st.failedRequests()).append(" | ")
                  .append(Math.round(st.p95LatencyMs())).append(" ms | ")
                  .append(String.format(Locale.ROOT, "%.1f%%", st.errorRate() * 100)).append(" |\n");
            }
            sb.append("\n");
        }

        File loadTestDir = io.testfly.reporting.ReportPaths.loadTestDir();
        if (loadTestDir.exists()) {
            java.util.List<File> reps = io.testfly.loadtest.LoadTestReportAdapter.findGatlingReports(loadTestDir);
            if (!reps.isEmpty()) {
                sb.append("📊 **Gatling Native HTML Report:** `").append(reps.get(0).getPath()).append("`\n");
            }
        }

        return sb.toString();
    }

    private static File findSubprocessLog(io.testfly.loadtest.LoadTestMetrics metrics) {
        File loadTestDir = io.testfly.reporting.ReportPaths.loadTestDir();
        if (loadTestDir.exists()) {
            java.util.List<File> reps = io.testfly.loadtest.LoadTestReportAdapter.findGatlingReports(loadTestDir);
            for (File rep : reps) {
                File p = rep.getParentFile();
                if (p != null) {
                    File direct = new File(p, "gatling-subprocess.log");
                    if (direct.exists()) return direct;
                    File gp = p.getParentFile();
                    if (gp != null) {
                        File up = new File(gp, "gatling-subprocess.log");
                        if (up.exists()) return up;
                    }
                }
            }
        }
        File def = new File("target/reports/loadtest/gatling-subprocess.log");
        if (def.exists()) return def;
        return null;
    }
}
