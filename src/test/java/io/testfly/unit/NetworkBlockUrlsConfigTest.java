package io.testfly.unit;

import io.testfly.config.ConfigurationLoader;
import io.testfly.config.TestFlyConfig;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Files;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Verifies that {@code network.blockUrls} in testfly.yml binds to
 * {@link TestFlyConfig.Network#getBlockUrls()} via SnakeYAML, and defaults to a
 * non-null empty list when absent.
 */
@Test(singleThreaded = true)
public class NetworkBlockUrlsConfigTest {

    private File tmp;

    @AfterMethod
    public void cleanup() {
        System.clearProperty("testfly.config");
        if (tmp != null) { tmp.delete(); tmp = null; }
    }

    private TestFlyConfig loadYaml(String yaml) throws Exception {
        tmp = File.createTempFile("testfly-network-", ".yml");
        Files.writeString(tmp.toPath(), yaml);
        System.setProperty("testfly.config", tmp.getAbsolutePath());
        return ConfigurationLoader.load();
    }

    @Test
    public void blockUrls_bindsFromYamlSequence() throws Exception {
        String yaml = String.join("\n",
                "browser:",
                "  name: chrome",
                "execution:",
                "  mode: local",
                "  baseUrl: https://example.com",
                "timeouts:",
                "  explicit: 10",
                "  pageLoad: 30",
                "network:",
                "  blockUrls:",
                "    - \"**/google-analytics.com/**\"",
                "    - \"**/doubleclick.net/**\"",
                "");
        TestFlyConfig config = loadYaml(yaml);
        assertNotNull(config.getNetwork());
        assertEquals(config.getNetwork().getBlockUrls().size(), 2);
        assertTrue(config.getNetwork().getBlockUrls().contains("**/doubleclick.net/**"));
    }

    @Test
    public void blockUrls_absent_defaultsToEmptyNonNull() throws Exception {
        String yaml = String.join("\n",
                "browser:",
                "  name: chrome",
                "execution:",
                "  mode: local",
                "  baseUrl: https://example.com",
                "timeouts:",
                "  explicit: 10",
                "  pageLoad: 30",
                "network:",
                "  interceptEnabled: false",
                "");
        TestFlyConfig config = loadYaml(yaml);
        assertNotNull(config.getNetwork());
        assertNotNull(config.getNetwork().getBlockUrls(), "must be non-null");
        assertTrue(config.getNetwork().getBlockUrls().isEmpty());
    }

    @Test
    public void network_setBlockUrls_nullBecomesEmpty() {
        TestFlyConfig.Network net = new TestFlyConfig.Network();
        net.setBlockUrls(null);
        assertNotNull(net.getBlockUrls());
        assertTrue(net.getBlockUrls().isEmpty());
    }
}
