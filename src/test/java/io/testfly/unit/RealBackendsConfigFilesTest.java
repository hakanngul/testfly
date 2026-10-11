package io.testfly.unit;

import io.testfly.config.ConfigurationLoader;
import io.testfly.config.TestFlyConfig;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/** The browser integration CI job loads these files through {@code -Dtestfly.config}; keep them loadable. */
@Test(singleThreaded = true)
public class RealBackendsConfigFilesTest {

    @AfterMethod
    public void cleanup() {
        System.clearProperty("testfly.config");
    }

    private static TestFlyConfig load(String path) {
        synchronized (ConfigurationLoader.class) {
            System.setProperty("testfly.config", path);
            try {
                return ConfigurationLoader.load();
            } finally {
                System.clearProperty("testfly.config");
            }
        }
    }

    @Test
    public void chromeConfig_loads() {
        TestFlyConfig c = load("src/test/resources/testfly-real-backends.yml");
        assertEquals(c.getBrowser().getName(), "chrome");
        assertTrue(c.getBrowser().isHeadless());
    }

    @Test
    public void firefoxConfig_loads_withoutChromeOnlyArguments() {
        TestFlyConfig c = load("src/test/resources/testfly-real-backends-firefox.yml");
        assertEquals(c.getBrowser().getName(), "firefox");
        assertTrue(c.getBrowser().isHeadless());
        assertTrue(c.getBrowser().getArguments() == null || c.getBrowser().getArguments().isEmpty(),
                "Chrome launch arguments such as --remote-allow-origins must not reach Firefox");
    }
}
