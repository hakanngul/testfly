package io.testfly.unit;

import io.testfly.config.ConfigurationLoader;
import io.testfly.config.TestFlyConfig;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Files;

import static org.testng.Assert.*;

/** Verifies the {@code actionability} section binds from testfly.yml and is validated. */
@Test(singleThreaded = true)
public class ActionabilityConfigTest {

    private File tmp;

    @AfterMethod
    public void cleanup() {
        System.clearProperty("testfly.config");
        if (tmp != null) {
            tmp.delete();
            tmp = null;
        }
    }

    private TestFlyConfig loadYaml(String actionabilityBlock) throws Exception {
        String yaml = String.join("\n",
                "browser:",
                "  name: chrome",
                "execution:",
                "  mode: local",
                "  baseUrl: https://example.com",
                "timeouts:",
                "  explicit: 10",
                "  pageLoad: 30",
                actionabilityBlock,
                "");
        synchronized (ConfigurationLoader.class) {
            tmp = File.createTempFile("testfly-actionability-", ".yml");
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
    public void absent_usesDefaults() throws Exception {
        TestFlyConfig.Actionability a = loadYaml("").getActionability();
        assertTrue(a.isEnabled());
        assertEquals(a.getStabilityIntervalMs(), 100);
        assertTrue(a.isHitTargetCheck());
        assertTrue(a.isAutoScroll());
    }

    @Test
    public void bindsAllKeys() throws Exception {
        TestFlyConfig.Actionability a = loadYaml(String.join("\n",
                "actionability:",
                "  enabled: false",
                "  stabilityIntervalMs: 250",
                "  hitTargetCheck: false",
                "  autoScroll: false")).getActionability();
        assertFalse(a.isEnabled());
        assertEquals(a.getStabilityIntervalMs(), 250);
        assertFalse(a.isHitTargetCheck());
        assertFalse(a.isAutoScroll());
    }

    @Test
    public void intervalOutOfRange_failsValidation() {
        IllegalStateException ex = expectThrows(IllegalStateException.class,
                () -> loadYaml("actionability:\n  stabilityIntervalMs: 5"));
        assertTrue(ex.getMessage().contains("actionability.stabilityIntervalMs"), ex.getMessage());
        expectThrows(IllegalStateException.class,
                () -> loadYaml("actionability:\n  stabilityIntervalMs: 6000"));
    }
}
