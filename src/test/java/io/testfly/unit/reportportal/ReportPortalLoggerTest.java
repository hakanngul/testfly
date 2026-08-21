package io.testfly.unit.reportportal;

import io.testfly.reporting.reportportal.ReportPortalLogger;
import org.testng.annotations.Test;

import java.io.File;

import static org.testng.Assert.*;

/**
 * Unit tests for {@link ReportPortalLogger}.
 *
 * <p>These tests verify the null-safety guards and graceful degradation
 * when ReportPortal is not actively running. Since no RP launch is active
 * during unit tests, {@code emitLog} calls return {@code false} — that
 * is the expected behavior and is asserted accordingly.
 */
public class ReportPortalLoggerTest {

    @Test
    public void isAvailable_returnsTrueWhenClientOnClasspath() {
        // agent-java-testng → client-java is compile-scoped (optional),
        // so it should be available during framework unit tests.
        assertTrue(ReportPortalLogger.isAvailable(),
                "ReportPortal client-java should be on the test classpath");
    }

    // ---------- log() null-safety ----------

    @Test
    public void log_nullMessage_returnsFalse() {
        assertFalse(ReportPortalLogger.log(null, "INFO"));
    }

    @Test
    public void log_nullLevel_doesNotThrow() {
        // No active RP launch → emitLog returns false, but must not throw
        boolean result = ReportPortalLogger.log("test message", null);
        assertFalse(result);
    }

    @Test
    public void log_validMessage_doesNotThrow() {
        // No active RP launch → false is expected, no exception
        boolean result = ReportPortalLogger.log("Hello from test", "INFO");
        assertFalse(result);
    }

    @Test
    public void log_allLevels_doNotThrow() {
        for (String level : new String[]{"TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL"}) {
            ReportPortalLogger.log("level=" + level, level);
        }
    }

    // ---------- logWithAttachment() null-safety ----------

    @Test
    public void logWithAttachment_nullMessage_returnsFalse() {
        assertFalse(ReportPortalLogger.logWithAttachment(null, "INFO", new File("dummy.png")));
    }

    @Test
    public void logWithAttachment_nullFile_returnsFalse() {
        assertFalse(ReportPortalLogger.logWithAttachment("msg", "INFO", null));
    }

    @Test
    public void logWithAttachment_nonExistentFile_returnsFalse() {
        File missing = new File("/tmp/does-not-exist-" + System.nanoTime() + ".png");
        assertFalse(missing.exists());
        assertFalse(ReportPortalLogger.logWithAttachment("msg", "INFO", missing));
    }

    @Test
    public void logWithAttachment_validFile_doesNotThrow() throws Exception {
        File tempFile = File.createTempFile("rp-test", ".png");
        try {
            java.nio.file.Files.write(tempFile.toPath(), new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});
            // No active RP launch → false is expected, no exception
            boolean result = ReportPortalLogger.logWithAttachment(
                    "📸 Test screenshot", "INFO", tempFile);
            assertFalse(result);
        } finally {
            tempFile.delete();
        }
    }

    // ---------- Idempotency ----------

    @Test
    public void isAvailable_consistentAcrossMultipleCalls() {
        boolean first = ReportPortalLogger.isAvailable();
        boolean second = ReportPortalLogger.isAvailable();
        assertEquals(first, second, "isAvailable() must return the same value on every call");
    }
}
