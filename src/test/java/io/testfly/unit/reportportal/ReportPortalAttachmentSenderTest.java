package io.testfly.unit.reportportal;

import io.testfly.reporting.reportportal.ReportPortalAttachmentSender;
import org.testng.annotations.Test;

import java.io.File;

/**
 * Unit tests for {@link ReportPortalAttachmentSender}.
 *
 * <p>These tests verify null-safety, graceful degradation when no RP launch is
 * active, and that valid files do not throw. Because the framework unit-test
 * suite has no active ReportPortal launch, {@code sendImmediate} is expected to
 * complete silently without exceptions.
 */
public class ReportPortalAttachmentSenderTest {

    @Test
    public void sendImmediate_noPaths_doesNothing() {
        ReportPortalAttachmentSender.sendImmediate("com.example.Test#method", null, null);
    }

    @Test
    public void sendImmediate_blankPaths_doesNothing() {
        ReportPortalAttachmentSender.sendImmediate("com.example.Test#method", "  ", "   ");
    }

    @Test
    public void sendImmediate_missingScreenshot_doesNotThrow() {
        File missing = new File("/tmp/does-not-exist-" + System.nanoTime() + ".png");
        ReportPortalAttachmentSender.sendImmediate(
                "com.example.Test#method", missing.getAbsolutePath(), null);
    }

    @Test
    public void sendImmediate_validScreenshot_doesNotThrow() throws Exception {
        File tempFile = File.createTempFile("rp-attach-test", ".png");
        try {
            java.nio.file.Files.write(tempFile.toPath(),
                    new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
            // No active RP launch → silent no-op, must not throw
            ReportPortalAttachmentSender.sendImmediate(
                    "com.example.Test#method", tempFile.getAbsolutePath(), null);
        } finally {
            tempFile.delete();
        }
    }

    @Test
    public void sendImmediate_validAiAnalysis_doesNotThrow() {
        // No active RP launch → silent no-op, must not throw
        ReportPortalAttachmentSender.sendImmediate(
                "com.example.Test#method", null, "Root cause: element not found");
    }

    @Test
    public void sendImmediate_bothArtifacts_doesNotThrow() throws Exception {
        File tempFile = File.createTempFile("rp-attach-test", ".png");
        try {
            java.nio.file.Files.write(tempFile.toPath(),
                    new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
            ReportPortalAttachmentSender.sendImmediate(
                    "com.example.Test#method",
                    tempFile.getAbsolutePath(),
                    "Root cause: expected title mismatch");
        } finally {
            tempFile.delete();
        }
    }

    @Test
    public void getName_returnsReportPortalAttachments() {
        assert "reportportal-attachments".equals(new ReportPortalAttachmentSender().getName());
    }

    @Test
    public void generate_doesNothing() {
        new ReportPortalAttachmentSender().generate(new File("nonexistent-metrics.json"));
    }

    @Test
    public void sendLoadTestMetrics_nullMetrics_doesNotThrow() {
        ReportPortalAttachmentSender.sendLoadTestMetrics("com.example.Test#method", null);
    }

    @Test
    public void sendLoadTestMetrics_validMetrics_doesNotThrow() {
        io.testfly.loadtest.LoadTestMetrics metrics = new io.testfly.loadtest.LoadTestMetrics(
                "Order API Load Test",
                100, 100, 0,
                20.0, 38.0, 35.0, 45.0, 50.0, 60.0,
                15.0, 80.0, 0.0,
                java.util.Collections.emptyMap(),
                5000L, 5, "gatling",
                java.util.Collections.emptyMap());

        ReportPortalAttachmentSender.sendLoadTestMetrics("com.example.Test#method", metrics);
    }

    @Test
    public void sendLoadTestMetrics_withStepsAndGatlingReport_doesNotThrow() throws Exception {
        File dummyDir = new File("target/reports/loadtest/rp-test");
        dummyDir.mkdirs();
        File dummyLog = new File(dummyDir, "gatling-subprocess.log");
        java.nio.file.Files.writeString(dummyLog.toPath(), "test log");
        File dummyReport = new File(dummyDir, "index.html");
        java.nio.file.Files.writeString(dummyReport.toPath(), "<html></html>");

        try {
            java.util.Map<String, io.testfly.loadtest.LoadTestMetrics.StepMetrics> steps = new java.util.HashMap<>();
            steps.put("step1", new io.testfly.loadtest.LoadTestMetrics.StepMetrics(
                    "GET /items", 50, 50, 0, 40.0, 0.0));

            io.testfly.loadtest.LoadTestMetrics metrics = new io.testfly.loadtest.LoadTestMetrics(
                    "Full Journey",
                    50, 50, 0,
                    10.0, 30.0, 30.0, 38.0, 40.0, 45.0,
                    10.0, 50.0, 0.0,
                    java.util.Collections.emptyMap(),
                    8000L, 10, "gatling",
                    steps);

            ReportPortalAttachmentSender.sendLoadTestMetrics("com.example.Test#method", metrics);
        } finally {
            dummyLog.delete();
            dummyReport.delete();
            dummyDir.delete();
        }
    }

    // ── Load-test markdown: unmeasured latencies are "n/a", never "0 ms" ──

    private static String loadTestMarkdown(io.testfly.loadtest.LoadTestMetrics m) throws Exception {
        java.lang.reflect.Method build = ReportPortalAttachmentSender.class
                .getDeclaredMethod("buildLoadTestMarkdown", io.testfly.loadtest.LoadTestMetrics.class);
        build.setAccessible(true);
        return (String) build.invoke(null, m);
    }

    @Test
    public void loadTestMarkdown_unmeasuredLatencies_renderNa() throws Exception {
        io.testfly.loadtest.LoadTestMetrics m = new io.testfly.loadtest.LoadTestMetrics(
                "RpScenario", 10, 10, 0, 5, Double.NaN, 4, Double.NaN, 9, Double.NaN, Double.NaN, 40, 0,
                java.util.Map.of(200, 10L), 1000, 2, "gatling",
                java.util.Map.of("s", new io.testfly.loadtest.LoadTestMetrics.StepMetrics("s", 10, 10, 0, Double.NaN, 0)),
                Double.NaN);

        String md = loadTestMarkdown(m);

        org.testng.Assert.assertTrue(md.contains("P50: 4 ms"), md);
        org.testng.Assert.assertTrue(md.contains("P75: n/a"), md);
        org.testng.Assert.assertTrue(md.contains("P90: n/a"), md);
        org.testng.Assert.assertTrue(md.contains("P95: 9 ms"), md);
        org.testng.Assert.assertTrue(md.contains("P99: n/a"), md);
        org.testng.Assert.assertTrue(md.contains("Min: n/a"), md);
        org.testng.Assert.assertTrue(md.contains("Mean: n/a"), md);
        org.testng.Assert.assertTrue(md.contains("Max: 40 ms"), md);
        org.testng.Assert.assertTrue(md.contains("| 0 | n/a | 0.0% |"), "step p95 must be n/a: " + md);
        org.testng.Assert.assertFalse(md.contains("NaN"), md);
    }

    @Test
    public void loadTestMarkdown_measuredLatencies_renderMs() throws Exception {
        io.testfly.loadtest.LoadTestMetrics m = new io.testfly.loadtest.LoadTestMetrics(
                "RpScenario2", 10, 10, 0, 5, 6, 4, 8, 9, 12, 1, 40, 0,
                java.util.Map.of(200, 10L), 1000, 2, "jdk", java.util.Map.of(), 5);

        String md = loadTestMarkdown(m);

        org.testng.Assert.assertTrue(md.contains("P50: 4 ms | P75: 5 ms | P90: 8 ms | P95: 9 ms | P99: 12 ms"), md);
        org.testng.Assert.assertTrue(md.contains("Min: 1 ms | Mean: 6 ms | Max: 40 ms"), md);
    }

    @Test
    public void loadTestMarkdown_showsVirtualUsersPartialLineAndSlaTable() throws Exception {
        io.testfly.loadtest.SlaRule p95 = new io.testfly.loadtest.SlaRule(io.testfly.loadtest.SlaRule.GLOBAL,
                io.testfly.loadtest.SlaRule.Metric.P95, io.testfly.loadtest.SlaRule.Comparison.BELOW, 100);
        io.testfly.loadtest.SlaRule loginErr = new io.testfly.loadtest.SlaRule("login",
                io.testfly.loadtest.SlaRule.Metric.ERROR_RATE, io.testfly.loadtest.SlaRule.Comparison.BELOW, 0.05);
        io.testfly.loadtest.SlaRule p90 = new io.testfly.loadtest.SlaRule(io.testfly.loadtest.SlaRule.GLOBAL,
                io.testfly.loadtest.SlaRule.Metric.P90, io.testfly.loadtest.SlaRule.Comparison.BELOW, 200);
        io.testfly.loadtest.LoadTestMetrics m = new io.testfly.loadtest.LoadTestMetrics(
                "RpSla", 10, 10, 0, 5, 6, 4, Double.NaN, 120, 130, 1, 140, 0,
                java.util.Map.of(200, 10L), 1000, 2, "gatling", java.util.Map.of(), 5, true,
                java.util.List.of(
                        new io.testfly.loadtest.SlaResult(p95, 120, false, "p95 120 ms is not below 100 ms"),
                        new io.testfly.loadtest.SlaResult(loginErr, 0.0, true, ""),
                        new io.testfly.loadtest.SlaResult(p90, Double.NaN, false, "p90 was not measured")));

        String md = loadTestMarkdown(m);

        // ADR 0003 decision 2
        org.testng.Assert.assertTrue(md.contains("• **Virtual Users:** 2 VUs"), md);
        org.testng.Assert.assertFalse(md.contains("Concurrent Users"), md);
        org.testng.Assert.assertTrue(md.contains("• **Partial:** yes"), "partial line: " + md);
        org.testng.Assert.assertTrue(md.contains("**SLA:**"), "SLA section: " + md);
        org.testng.Assert.assertTrue(md.contains("| Scope | Metric | Comparison | Threshold | Actual | Result | Reason |"),
                "SLA table header: " + md);
        String failed = mdLine(md, "| global | P95 | BELOW |");
        org.testng.Assert.assertTrue(failed.contains("| FAIL |") && failed.contains("p95 120 ms is not below 100 ms"),
                failed);
        org.testng.Assert.assertTrue(mdLine(md, "| login | ERROR_RATE | BELOW |").contains("| PASS |"), md);
        String unmeasured = mdLine(md, "| global | P90 | BELOW |");
        org.testng.Assert.assertTrue(unmeasured.contains("| n/a |") && unmeasured.contains("| FAIL |"), unmeasured);
        org.testng.Assert.assertFalse(md.contains("NaN"), md);
    }

    @Test
    public void loadTestMarkdown_withoutSla_hasNoSlaSection_andPartialNo() throws Exception {
        io.testfly.loadtest.LoadTestMetrics m = new io.testfly.loadtest.LoadTestMetrics(
                "RpNoSla", 10, 10, 0, 5, 6, 4, 8, 9, 12, 1, 40, 0,
                java.util.Map.of(200, 10L), 1000, 2, "jdk", java.util.Map.of(), 5);

        String md = loadTestMarkdown(m);

        org.testng.Assert.assertTrue(md.contains("• **Partial:** no"), "partial line: " + md);
        org.testng.Assert.assertFalse(md.contains("**SLA:**"), "no SLA section without SLA rules: " + md);
    }

    @Test
    public void loadTestMarkdown_slaReason_escapesBackslashBeforePipe() throws Exception {
        io.testfly.loadtest.SlaRule p95 = new io.testfly.loadtest.SlaRule(io.testfly.loadtest.SlaRule.GLOBAL,
                io.testfly.loadtest.SlaRule.Metric.P95, io.testfly.loadtest.SlaRule.Comparison.BELOW, 100);
        io.testfly.loadtest.LoadTestMetrics m = new io.testfly.loadtest.LoadTestMetrics(
                "RpEscape", 10, 10, 0, 5, 6, 4, 8, 120, 130, 1, 140, 0,
                java.util.Map.of(200, 10L), 1000, 2, "gatling", java.util.Map.of(), 5, false,
                java.util.List.of(new io.testfly.loadtest.SlaResult(p95, 120, false, "a\\|b")));

        String row = mdLine(loadTestMarkdown(m), "| global | P95 | BELOW |");
        // the reason a\|b is written a\\\|b (backslash escaped first, then the pipe): it renders as a\|b in one cell
        org.testng.Assert.assertTrue(row.endsWith("| a\\\\\\|b |"), row);
    }

    private static String mdLine(String text, String prefix) {
        for (String l : text.split("\n")) {
            if (l.startsWith(prefix)) return l;
        }
        org.testng.Assert.fail("no line starting with '" + prefix + "' in:\n" + text);
        return null;
    }
}
