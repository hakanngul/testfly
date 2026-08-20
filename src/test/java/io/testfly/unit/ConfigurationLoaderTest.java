package io.testfly.unit;

import io.testfly.config.ConfigurationLoader;
import io.testfly.config.TestFlyConfig;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Unit tests for {@link ConfigurationLoader}.
 * Relies on testfly.yml present in src/test/resources (classpath).
 */
public class ConfigurationLoaderTest {

    @BeforeMethod
    public void clearSystemProperties() {
        System.clearProperty("testfly.config");
        System.clearProperty("testfly.profile");
    }

    @AfterMethod
    public void restoreSystemProperties() {
        System.clearProperty("testfly.config");
        System.clearProperty("testfly.profile");
    }

    // ----------------------------------------------------------
    // Classpath loading (Priority 3)
    // ----------------------------------------------------------

    @Test
    public void load_defaultProfile_loadsFromClasspath() {
        TestFlyConfig config = ConfigurationLoader.load();

        assertNotNull(config);
        assertNotNull(config.getBrowser());
        assertEquals("chrome", config.getBrowser().getName());
        assertEquals("local", config.getExecution().getMode());
        assertTrue(config.getTimeouts().getExplicit() > 0);
        assertTrue(config.getTimeouts().getPageLoad() > 0);
    }

    @Test
    public void load_returnsRetryConfig() {
        TestFlyConfig config = ConfigurationLoader.load();
        assertNotNull(config.getRetry());
    }

    @Test
    public void load_returnsExecutionBaseUrl() {
        TestFlyConfig config = ConfigurationLoader.load();
        assertNotNull(config.getExecution().getBaseUrl());
        assertFalse(config.getExecution().getBaseUrl().isBlank());
    }

    // ----------------------------------------------------------
    // Profile selection
    // ----------------------------------------------------------

    @Test
    public void load_withProfile_loadsProfileFile() {
        System.setProperty("testfly.profile", "prod");
        TestFlyConfig config = ConfigurationLoader.load();
        assertNotNull(config);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void load_withNonExistentProfile_throwsIllegalState() {
        System.setProperty("testfly.profile", "does-not-exist-xyz");
        ConfigurationLoader.load(); // should throw
    }

    // ----------------------------------------------------------
    // Explicit path (Priority 1)
    // ----------------------------------------------------------

    @Test(expectedExceptions = IllegalStateException.class)
    public void load_withExplicitPathThatDoesNotExist_throwsIllegalState() {
        System.setProperty("testfly.config", "/nonexistent/path/config.yml");
        ConfigurationLoader.load();
    }

    // ----------------------------------------------------------
    // Validation
    // ----------------------------------------------------------

    @Test
    public void load_configHasPositiveThreadCount() {
        TestFlyConfig config = ConfigurationLoader.load();
        assertTrue(config.getExecution().getMaxActiveSessions() > 0);
    }
}
