package io.testfly.unit;

import io.testfly.config.TestFlyConfig;
import io.testfly.config.TestFlyDefaults;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Unit tests for {@link TestFlyDefaults}.
 */
public class TestFlyDefaultsTest {

    @AfterMethod
    public void reset() {
        TestFlyDefaults.reset();
    }

    // ----------------------------------------------------------
    // set / get
    // ----------------------------------------------------------

    @Test
    public void set_storesValueRetrievableByGet() {
        TestFlyDefaults.set("browser.name", "edge");
        assertEquals(TestFlyDefaults.get("browser.name"), "edge");
    }

    @Test
    public void get_unknownKey_returnsNull() {
        assertNull(TestFlyDefaults.get("nonexistent.key"));
    }

    @Test
    public void reset_clearsAllOverrides() {
        TestFlyDefaults.set("browser.name", "edge");
        TestFlyDefaults.reset();
        assertNull(TestFlyDefaults.get("browser.name"));
    }

    // ----------------------------------------------------------
    // applyMissing — browser.name
    // ----------------------------------------------------------

    @Test
    public void applyMissing_browserName_appliedWhenNull() {
        TestFlyDefaults.set("browser.name", "edge");
        TestFlyConfig config = configWithNullBrowserName();

        TestFlyDefaults.applyMissing(config);

        assertEquals(config.getBrowser().getName(), "edge");
    }

    @Test
    public void applyMissing_browserName_notOverriddenWhenAlreadySet() {
        TestFlyDefaults.set("browser.name", "edge");
        TestFlyConfig config = configWithBrowserName("chrome");

        TestFlyDefaults.applyMissing(config);

        assertEquals(config.getBrowser().getName(), "chrome",
            "YAML value should win over default");
    }

    // ----------------------------------------------------------
    // applyMissing — timeouts
    // ----------------------------------------------------------

    @Test
    public void applyMissing_explicitTimeout_appliedWhenZero() {
        TestFlyDefaults.set("timeouts.explicit", 20);
        TestFlyConfig config = configWithTimeouts(0, 0);

        TestFlyDefaults.applyMissing(config);

        assertEquals(config.getTimeouts().getExplicit(), 20);
    }

    @Test
    public void applyMissing_explicitTimeout_notOverriddenWhenSet() {
        TestFlyDefaults.set("timeouts.explicit", 20);
        TestFlyConfig config = configWithTimeouts(10, 30);

        TestFlyDefaults.applyMissing(config);

        assertEquals(config.getTimeouts().getExplicit(), 10,
            "YAML value should win over default");
    }

    @Test
    public void applyMissing_pageLoadTimeout_appliedWhenZero() {
        TestFlyDefaults.set("timeouts.pageLoad", 60);
        TestFlyConfig config = configWithTimeouts(0, 0);

        TestFlyDefaults.applyMissing(config);

        assertEquals(config.getTimeouts().getPageLoad(), 60);
    }

    // ----------------------------------------------------------
    // applyMissing — execution
    // ----------------------------------------------------------

    @Test
    public void applyMissing_maxActiveSessions_appliedWhenZero() {
        TestFlyDefaults.set("execution.maxActiveSessions", 8);
        TestFlyConfig config = configWithExecution(0, 0);

        TestFlyDefaults.applyMissing(config);

        assertEquals(config.getExecution().getMaxActiveSessions(), 8);
    }

    @Test
    public void applyMissing_threadCount_appliedWhenZero() {
        TestFlyDefaults.set("execution.threadCount", 4);
        TestFlyConfig config = configWithExecution(0, 0);

        TestFlyDefaults.applyMissing(config);

        assertEquals(config.getExecution().getThreadCount(), 4);
    }

    // ----------------------------------------------------------
    // applyMissing — null-safety
    // ----------------------------------------------------------

    @Test
    public void applyMissing_nullBrowserSection_doesNotThrow() {
        TestFlyDefaults.set("browser.name", "edge");
        TestFlyConfig config = new TestFlyConfig(); // browser section is null

        TestFlyDefaults.applyMissing(config); // must not throw
    }

    @Test
    public void applyMissing_nullTimeoutsSection_doesNotThrow() {
        TestFlyDefaults.set("timeouts.explicit", 10);
        TestFlyConfig config = new TestFlyConfig();

        TestFlyDefaults.applyMissing(config); // must not throw
    }

    // ----------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------

    private static TestFlyConfig configWithNullBrowserName() {
        TestFlyConfig config = new TestFlyConfig();
        TestFlyConfig.Browser browser = new TestFlyConfig.Browser();
        // name left null
        config.setBrowser(browser);
        return config;
    }

    private static TestFlyConfig configWithBrowserName(String name) {
        TestFlyConfig config = new TestFlyConfig();
        TestFlyConfig.Browser browser = new TestFlyConfig.Browser();
        browser.setName(name);
        config.setBrowser(browser);
        return config;
    }

    private static TestFlyConfig configWithTimeouts(int explicit, int pageLoad) {
        TestFlyConfig config = new TestFlyConfig();
        TestFlyConfig.Timeouts timeouts = new TestFlyConfig.Timeouts();
        timeouts.setExplicit(explicit);
        timeouts.setPageLoad(pageLoad);
        config.setTimeouts(timeouts);
        return config;
    }

    private static TestFlyConfig configWithExecution(int maxSessions, int threadCount) {
        TestFlyConfig config = new TestFlyConfig();
        TestFlyConfig.Execution execution = new TestFlyConfig.Execution();
        execution.setMaxActiveSessions(maxSessions);
        execution.setThreadCount(threadCount);
        config.setExecution(execution);
        return config;
    }
}
