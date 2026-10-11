package io.testfly.unit;

import io.testfly.config.ConfigurationLoader;
import io.testfly.config.TestFlyConfig;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Files;

import static org.testng.Assert.*;

/** Verifies the {@code tracing} snapshot keys bind from testfly.yml and are validated. */
@Test(singleThreaded = true)
public class TracingConfigTest {

    private File tmp;

    @AfterMethod
    public void cleanup() {
        System.clearProperty("testfly.config");
        if (tmp != null) {
            tmp.delete();
            tmp = null;
        }
    }

    private TestFlyConfig loadYaml(String tracingBlock) throws Exception {
        String yaml = String.join("\n",
                "browser:",
                "  name: chrome",
                "execution:",
                "  mode: local",
                "  baseUrl: https://example.com",
                "timeouts:",
                "  explicit: 10",
                "  pageLoad: 30",
                tracingBlock,
                "");
        synchronized (ConfigurationLoader.class) {
            tmp = File.createTempFile("testfly-tracing-", ".yml");
            Files.writeString(tmp.toPath(), yaml);
            System.setProperty("testfly.config", tmp.getAbsolutePath());
            try {
                return ConfigurationLoader.load();
            } finally {
                System.clearProperty("testfly.config");
            }
        }
    }

    @Test
    public void defaults_areAriaSnapshotsWithAfterCaptureOnFailure() {
        TestFlyConfig.Tracing t = new TestFlyConfig.Tracing();
        assertEquals(t.getSnapshots(), "aria");
        assertEquals(t.getSnapshotMaxKb(), 256);
        assertTrue(t.isSnapshotOnFailureAfter());
    }

    @Test
    public void bindsAllKeysFromYaml() throws Exception {
        TestFlyConfig.Tracing t = loadYaml(String.join("\n",
                "tracing:",
                "  enabled: true",
                "  snapshots: BOTH",
                "  snapshotMaxKb: 64",
                "  snapshotOnFailureAfter: false")).getTracing();
        assertTrue(t.isEnabled());
        assertEquals(t.getSnapshots(), "both", "the mode is normalised to lower case");
        assertEquals(t.getSnapshotMaxKb(), 64);
        assertFalse(t.isSnapshotOnFailureAfter());
    }

    @Test
    public void unknownMode_isRejected() {
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> new TestFlyConfig.Tracing().setSnapshots("html"));
        assertTrue(e.getMessage().contains("tracing.snapshots"), e.getMessage());
        expectThrows(IllegalArgumentException.class, () -> new TestFlyConfig.Tracing().setSnapshots(null));
    }

    @Test
    public void nonPositiveMaxKb_isRejected() {
        expectThrows(IllegalArgumentException.class, () -> new TestFlyConfig.Tracing().setSnapshotMaxKb(0));
    }

    @Test
    public void badModeInYaml_failsTheLoad() {
        Throwable e = expectThrows(RuntimeException.class,
                () -> loadYaml("tracing:\n  snapshots: html"));
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        assertTrue(String.valueOf(e.getMessage()).contains("tracing.snapshots")
                || String.valueOf(root.getMessage()).contains("tracing.snapshots"), e.toString());
    }
}
