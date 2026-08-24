package io.testfly.unit;

import io.testfly.config.TestFlyConfig;
import io.testfly.internal.TestFlyContext;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.*;

/**
 * Unit tests for {@link TestFlyContext}.
 * Uses reflection to reset the static AtomicReference between tests.
 */
@Test(singleThreaded = true)
public class TestFlyContextTest {

    private static final Object LOCK = new Object();

    @BeforeMethod
    public void setupContext() throws Exception {
        synchronized (LOCK) {
            resetContextInternal();
        }
    }

    @AfterMethod
    public void resetContext() throws Exception {
        synchronized (LOCK) {
            resetContextInternal();
        }
    }

    private static void resetContextInternal() throws Exception {
        Field configField = TestFlyContext.class.getDeclaredField("CONFIG");
        configField.setAccessible(true);
        AtomicReference<?> ref = (AtomicReference<?>) configField.get(null);
        ref.set(null);
        TestFlyContext.clearCurrentTestId();
    }

    // ----------------------------------------------------------
    // initialize / isInitialized
    // ----------------------------------------------------------

    @Test
    public void isInitialized_beforeInit_returnsFalse() {
        synchronized (LOCK) {
            assertFalse(TestFlyContext.isInitialized());
        }
    }

    @Test
    public void initialize_setsConfigAndMarkInitialized() {
        synchronized (LOCK) {
            TestFlyConfig config = minimalConfig();
            TestFlyContext.initialize(config);

            assertTrue(TestFlyContext.isInitialized());
            assertSame(config, TestFlyContext.getConfig());
        }
    }

    @Test
    public void initialize_calledTwice_firstConfigWins() {
        synchronized (LOCK) {
            TestFlyConfig first = minimalConfig();
            TestFlyConfig second = minimalConfig();
            second.getBrowser().setName("firefox");

            TestFlyContext.initialize(first);
            TestFlyContext.initialize(second); // should be ignored

            assertSame(first, TestFlyContext.getConfig());
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void initialize_withNull_throwsIllegalArgument() {
        synchronized (LOCK) {
            TestFlyContext.initialize(null);
        }
    }

    // ----------------------------------------------------------
    // getConfig — uninitialized guard
    // ----------------------------------------------------------

    @Test(expectedExceptions = IllegalStateException.class)
    public void getConfig_whenNotInitialized_throwsIllegalState() {
        synchronized (LOCK) {
            TestFlyContext.getConfig();
        }
    }

    // ----------------------------------------------------------
    // Thread-local test ID
    // ----------------------------------------------------------

    @Test
    public void setAndGetCurrentTestId_returnsSameValue() {
        synchronized (LOCK) {
            TestFlyContext.setCurrentTestId("my.test.Method");
            assertEquals("my.test.Method", TestFlyContext.getCurrentTestId());
        }
    }

    @Test
    public void clearCurrentTestId_removesValue() {
        synchronized (LOCK) {
            TestFlyContext.setCurrentTestId("to-be-cleared");
            TestFlyContext.clearCurrentTestId();
            assertNull(TestFlyContext.getCurrentTestId());
        }
    }

    @Test
    public void getCurrentTestId_whenNotSet_returnsNull() {
        synchronized (LOCK) {
            assertNull(TestFlyContext.getCurrentTestId());
        }
    }

    // ----------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------

    private static TestFlyConfig minimalConfig() {
        TestFlyConfig config = new TestFlyConfig();

        TestFlyConfig.Browser browser = new TestFlyConfig.Browser();
        browser.setName("chrome");
        config.setBrowser(browser);

        TestFlyConfig.Execution execution = new TestFlyConfig.Execution();
        execution.setMode("local");
        execution.setBaseUrl("https://example.com");
        execution.setMaxActiveSessions(5);
        config.setExecution(execution);

        TestFlyConfig.Timeouts timeouts = new TestFlyConfig.Timeouts();
        timeouts.setExplicit(10);
        timeouts.setPageLoad(30);
        config.setTimeouts(timeouts);

        return config;
    }
}
