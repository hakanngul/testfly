package io.testfly.unit;

import io.testfly.browser.BrowserContext;
import io.testfly.config.TestFlyConfig;
import io.testfly.driver.*;
import io.testfly.internal.TestFlyContext;
import org.mockito.MockedStatic;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.mockito.Mockito.mockStatic;
import static org.testng.Assert.*;

/**
 * Unit tests for {@link DriverProviderFactory} provider selection.
 *
 * <p>Does not create real browsers; only verifies that the correct provider
 * class is returned for each browser name and execution mode.
 * Thread-safe for parallel=methods execution.
 */
@Test(singleThreaded = true)
public class DriverProviderFactoryTest {

    @BeforeMethod
    public void setup() {
        BrowserContext.clear();
        clearRegistry();
    }

    @AfterMethod
    public void teardown() {
        BrowserContext.clear();
        clearRegistry();
    }

    @SuppressWarnings("unchecked")
    private static void clearRegistry() {
        try {
            Field f = DriverProviderRegistry.class.getDeclaredField("registry");
            f.setAccessible(true);
            Map<String, DriverProvider> map = (Map<String, DriverProvider>) f.get(null);
            synchronized (DriverProviderRegistry.class) {
                map.clear();
            }
            // reload SPI providers if any (safe to call multiple times)
            DriverProviderRegistry.loadAll();
        } catch (Exception e) {
            // best effort
        }
    }

    // ── Built-in local providers ─────────────────────────────────────────────

    @Test
    public void getProvider_chrome_returnsLocalChromeDriverProvider() {
        try (MockedStatic<TestFlyContext> ctx = mockStatic(TestFlyContext.class)) {
            ctx.when(TestFlyContext::getConfig).thenReturn(configWith("chrome", "local"));
            DriverProvider provider = DriverProviderFactory.getProvider();
            assertTrue(provider instanceof LocalChromeDriverProvider,
                    "Expected LocalChromeDriverProvider but got " + provider.getClass().getSimpleName());
        }
    }

    @Test
    public void getProvider_firefox_returnsLocalFirefoxDriverProvider() {
        try (MockedStatic<TestFlyContext> ctx = mockStatic(TestFlyContext.class)) {
            ctx.when(TestFlyContext::getConfig).thenReturn(configWith("firefox", "local"));
            DriverProvider provider = DriverProviderFactory.getProvider();
            assertTrue(provider instanceof LocalFirefoxDriverProvider,
                    "Expected LocalFirefoxDriverProvider but got " + provider.getClass().getSimpleName());
        }
    }

    @Test
    public void getProvider_edge_returnsLocalEdgeDriverProvider() {
        try (MockedStatic<TestFlyContext> ctx = mockStatic(TestFlyContext.class)) {
            ctx.when(TestFlyContext::getConfig).thenReturn(configWith("edge", "local"));
            DriverProvider provider = DriverProviderFactory.getProvider();
            assertTrue(provider instanceof LocalEdgeDriverProvider,
                    "Expected LocalEdgeDriverProvider but got " + provider.getClass().getSimpleName());
        }
    }

    @Test
    public void getProvider_safari_returnsLocalSafariDriverProvider() {
        try (MockedStatic<TestFlyContext> ctx = mockStatic(TestFlyContext.class)) {
            ctx.when(TestFlyContext::getConfig).thenReturn(configWith("safari", "local"));
            DriverProvider provider = DriverProviderFactory.getProvider();
            assertTrue(provider instanceof LocalSafariDriverProvider,
                    "Expected LocalSafariDriverProvider but got " + provider.getClass().getSimpleName());
        }
    }

    // ── Execution modes ──────────────────────────────────────────────────────

    @Test
    public void getProvider_remote_returnsRemoteDriverProvider() {
        try (MockedStatic<TestFlyContext> ctx = mockStatic(TestFlyContext.class)) {
            ctx.when(TestFlyContext::getConfig).thenReturn(configWith("chrome", "remote"));
            DriverProvider provider = DriverProviderFactory.getProvider();
            assertTrue(provider instanceof RemoteDriverProvider);
        }
    }

    @Test
    public void getProvider_browserstack_returnsBrowserStackProvider() {
        try (MockedStatic<TestFlyContext> ctx = mockStatic(TestFlyContext.class)) {
            ctx.when(TestFlyContext::getConfig).thenReturn(configWith("chrome", "browserstack"));
            DriverProvider provider = DriverProviderFactory.getProvider();
            assertTrue(provider instanceof BrowserStackProvider);
        }
    }

    @Test
    public void getProvider_saucelabs_returnsSauceLabsProvider() {
        try (MockedStatic<TestFlyContext> ctx = mockStatic(TestFlyContext.class)) {
            ctx.when(TestFlyContext::getConfig).thenReturn(configWith("chrome", "saucelabs"));
            DriverProvider provider = DriverProviderFactory.getProvider();
            assertTrue(provider instanceof SauceLabsProvider);
        }
    }

    // ── Custom providers ─────────────────────────────────────────────────────

    @Test
    public void getProvider_customProviderTakesPrecedenceOverBuiltIn() {
        NamedDriverProvider customChrome = new NamedDriverProvider() {
            @Override
            public String browserName() { return "chrome"; }

            @Override
            public org.openqa.selenium.WebDriver createDriver() { return null; }
        };
        synchronized (DriverProviderRegistry.class) {
            DriverProviderRegistry.register(customChrome);
        }

        try (MockedStatic<TestFlyContext> ctx = mockStatic(TestFlyContext.class)) {
            ctx.when(TestFlyContext::getConfig).thenReturn(configWith("chrome", "local"));
            DriverProvider provider = DriverProviderFactory.getProvider();
            assertSame(provider, customChrome, "Custom provider registered via registry should take precedence");
        }
    }

    // ── Unsupported browser ──────────────────────────────────────────────────

    @Test(expectedExceptions = IllegalStateException.class)
    public void getProvider_unknownLocalBrowser_throws() {
        try (MockedStatic<TestFlyContext> ctx = mockStatic(TestFlyContext.class)) {
            ctx.when(TestFlyContext::getConfig).thenReturn(configWith("opera", "local"));
            DriverProviderFactory.getProvider();
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static TestFlyConfig configWith(String browserName, String executionMode) {
        TestFlyConfig.Browser browser = new TestFlyConfig.Browser();
        browser.setName(browserName);

        TestFlyConfig.Execution execution = new TestFlyConfig.Execution();
        execution.setMode(executionMode);

        TestFlyConfig config = new TestFlyConfig();
        config.setBrowser(browser);
        config.setExecution(execution);
        return config;
    }
}
