package io.testfly.unit;

import io.testfly.config.ConfigurationLoader;
import io.testfly.config.TestFlyConfig;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Files;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** Verifies that {@code locators.strict} binds from testfly.yml and defaults to false. */
@Test(singleThreaded = true)
public class LocatorsStrictConfigTest {

    private File tmp;

    @AfterMethod
    public void cleanup() {
        System.clearProperty("testfly.config");
        if (tmp != null) {
            tmp.delete();
            tmp = null;
        }
    }

    private TestFlyConfig loadYaml(String locatorsBlock) throws Exception {
        String yaml = String.join("\n",
                "browser:",
                "  name: chrome",
                "execution:",
                "  mode: local",
                "  baseUrl: https://example.com",
                "timeouts:",
                "  explicit: 10",
                "  pageLoad: 30",
                locatorsBlock,
                "");
        synchronized (ConfigurationLoader.class) {
            tmp = File.createTempFile("testfly-locators-", ".yml");
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
    public void strict_bindsFromYaml() throws Exception {
        TestFlyConfig config = loadYaml("locators:\n  strict: true");
        assertTrue(config.getLocators().isStrict());
    }

    @Test
    public void strict_absent_defaultsToFalse() throws Exception {
        TestFlyConfig config = loadYaml("");
        // The locators section is optional and may be absent (null); either way strict is off.
        assertFalse(config.getLocators() != null && config.getLocators().isStrict());
    }
}
