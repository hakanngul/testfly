package io.testfly.unit;

import io.testfly.config.ConfigurationLoader;
import io.testfly.config.TestFlyConfig;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.testng.Assert.*;

/**
 * Unit tests for {@link ConfigurationLoader}.
 * Relies on testfly.yml present in src/test/resources (classpath).
 */
@Test(singleThreaded = true)
public class ConfigurationLoaderTest {

    private static final Object LOCK = ConfigurationLoader.class;

    @BeforeMethod
    public void clearSystemProperties() {
        synchronized (LOCK) {
            System.clearProperty("testfly.config");
            System.clearProperty("testfly.profile");
        }
    }

    @AfterMethod
    public void restoreSystemProperties() {
        synchronized (LOCK) {
            System.clearProperty("testfly.config");
            System.clearProperty("testfly.profile");
        }
    }

    // ----------------------------------------------------------
    // Classpath loading (Priority 3)
    // ----------------------------------------------------------

    @Test
    public void load_defaultProfile_loadsFromClasspath() {
        synchronized (LOCK) {
            System.clearProperty("testfly.config");
            System.clearProperty("testfly.profile");
            TestFlyConfig config = ConfigurationLoader.load();

            assertNotNull(config);
            assertNotNull(config.getBrowser());
            assertEquals("chrome", config.getBrowser().getName());
            assertEquals("local", config.getExecution().getMode());
            assertTrue(config.getTimeouts().getExplicit() > 0);
            assertTrue(config.getTimeouts().getPageLoad() > 0);
        }
    }

    @Test
    public void load_returnsRetryConfig() {
        synchronized (LOCK) {
            System.clearProperty("testfly.config");
            System.clearProperty("testfly.profile");
            TestFlyConfig config = ConfigurationLoader.load();
            assertNotNull(config.getRetry());
        }
    }

    @Test
    public void load_returnsExecutionBaseUrl() {
        synchronized (LOCK) {
            System.clearProperty("testfly.config");
            System.clearProperty("testfly.profile");
            TestFlyConfig config = ConfigurationLoader.load();
            assertNotNull(config.getExecution().getBaseUrl());
            assertFalse(config.getExecution().getBaseUrl().isBlank());
        }
    }

    // ----------------------------------------------------------
    // Profile selection
    // ----------------------------------------------------------

    @Test
    public void load_withProfile_loadsProfileFile() {
        synchronized (LOCK) {
            System.clearProperty("testfly.config");
            System.setProperty("testfly.profile", "prod");
            try {
                TestFlyConfig config = ConfigurationLoader.load();
                assertNotNull(config);
            } finally {
                System.clearProperty("testfly.profile");
            }
        }
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void load_withNonExistentProfile_throwsIllegalState() {
        synchronized (LOCK) {
            System.clearProperty("testfly.config");
            System.setProperty("testfly.profile", "does-not-exist-xyz");
            try {
                ConfigurationLoader.load(); // should throw
            } finally {
                System.clearProperty("testfly.profile");
            }
        }
    }

    // ----------------------------------------------------------
    // Explicit path (Priority 1)
    // ----------------------------------------------------------

    @Test(expectedExceptions = IllegalStateException.class)
    public void load_withExplicitPathThatDoesNotExist_throwsIllegalState() {
        synchronized (LOCK) {
            System.clearProperty("testfly.profile");
            System.setProperty("testfly.config", "/nonexistent/path/config.yml");
            try {
                ConfigurationLoader.load();
            } finally {
                System.clearProperty("testfly.config");
            }
        }
    }

    // ----------------------------------------------------------
    // Validation
    // ----------------------------------------------------------

    @Test
    public void load_configHasPositiveThreadCount() {
        synchronized (LOCK) {
            System.clearProperty("testfly.config");
            System.clearProperty("testfly.profile");
            TestFlyConfig config = ConfigurationLoader.load();
            assertTrue(config.getExecution().getMaxActiveSessions() > 0);
        }
    }

    @Test
    public void load_withoutBaseUrl_failsFast() throws Exception {
        IllegalStateException e = expectThrows(IllegalStateException.class,
                () -> loadExecution("  mode: local\n"));
        assertTrue(e.getMessage().contains("execution.baseUrl"), e.getMessage());
    }

    @Test
    public void load_withBlankBaseUrl_failsFast() throws Exception {
        IllegalStateException e = expectThrows(IllegalStateException.class,
                () -> loadExecution("  mode: local\n  baseUrl: \"  \"\n"));
        assertTrue(e.getMessage().contains("execution.baseUrl"), e.getMessage());
    }

    private static TestFlyConfig loadExecution(String executionBlock) throws Exception {
        Path tmp = Files.createTempFile("testfly-baseurl", ".yml");
        try {
            Files.writeString(tmp, "execution:\n" + executionBlock
                    + "browser:\n  name: chrome\n"
                    + "timeouts:\n  explicit: 10\n  pageLoad: 30\n");
            synchronized (LOCK) {
                System.setProperty("testfly.config", tmp.toString());
                try {
                    return ConfigurationLoader.load();
                } finally {
                    System.clearProperty("testfly.config");
                }
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
