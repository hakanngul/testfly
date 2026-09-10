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
}
