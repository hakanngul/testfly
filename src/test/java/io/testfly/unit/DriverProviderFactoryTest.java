package io.testfly.unit;

import io.testfly.config.TestFlyConfig;
import io.testfly.driver.*;
import io.testfly.internal.TestFlyContext;
import org.mockito.MockedStatic;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.mockStatic;
import static org.testng.Assert.*;

/**
 * Unit tests for {@link DriverProviderFactory} provider selection.
 *
 * <p>Does not create real browsers; only verifies that the correct provider
 * class is returned for each browser name and execution mode.
 */
public class DriverProviderFactoryTest {

    private MockedStatic<TestFlyContext> contextMock;

    @BeforeMethod
    public void setup() {
        contextMock = mockStatic(TestFlyContext.class);
    }

    @AfterMethod
    public void teardown() {
        contextMock.close();
        DriverProviderRegistry.loadAll(); // refresh SPI state if needed
    }

    // ── Built-in local providers ─────────────────────────────────────────────

    @Test
    public void getProvider_chrome_returnsLocalChromeDriverProvider() {
        contextMock.when(TestFlyContext::getConfig).thenReturn(configWith("chrome", "local"));

        DriverProvider provider = DriverProviderFactory.getProvider();

        assertTrue(provider instanceof LocalChromeDriverProvider,
                "Expected LocalChromeDriverProvider but got " + provider.getClass().getSimpleName());
    }

    @Test
    public void getProvider_firefox_returnsLocalFirefoxDriverProvider() {
        contextMock.when(TestFlyContext::getConfig).thenReturn(configWith("firefox", "local"));

        DriverProvider provider = DriverProviderFactory.getProvider();

        assertTrue(provider instanceof LocalFirefoxDriverProvider,
                "Expected LocalFirefoxDriverProvider but got " + provider.getClass().getSimpleName());
    }

    @Test
    public void getProvider_edge_returnsLocalEdgeDriverProvider() {
        contextMock.when(TestFlyContext::getConfig).thenReturn(configWith("edge", "local"));

        DriverProvider provider = DriverProviderFactory.getProvider();

        assertTrue(provider instanceof LocalEdgeDriverProvider,
                "Expected LocalEdgeDriverProvider but got " + provider.getClass().getSimpleName());
    }

    @Test
    public void getProvider_safari_returnsLocalSafariDriverProvider() {
        contextMock.when(TestFlyContext::getConfig).thenReturn(configWith("safari", "local"));

        DriverProvider provider = DriverProviderFactory.getProvider();

        assertTrue(provider instanceof LocalSafariDriverProvider,
                "Expected LocalSafariDriverProvider but got " + provider.getClass().getSimpleName());
    }

    // ── Execution modes ──────────────────────────────────────────────────────

    @Test
    public void getProvider_remote_returnsRemoteDriverProvider() {
        contextMock.when(TestFlyContext::getConfig).thenReturn(configWith("chrome", "remote"));

        DriverProvider provider = DriverProviderFactory.getProvider();

        assertTrue(provider instanceof RemoteDriverProvider);
    }

    @Test
    public void getProvider_browserstack_returnsBrowserStackProvider() {
        contextMock.when(TestFlyContext::getConfig).thenReturn(configWith("chrome", "browserstack"));

        DriverProvider provider = DriverProviderFactory.getProvider();

        assertTrue(provider instanceof BrowserStackProvider);
    }

    @Test
    public void getProvider_saucelabs_returnsSauceLabsProvider() {
        contextMock.when(TestFlyContext::getConfig).thenReturn(configWith("chrome", "saucelabs"));

        DriverProvider provider = DriverProviderFactory.getProvider();

        assertTrue(provider instanceof SauceLabsProvider);
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
        DriverProviderRegistry.register(customChrome);

        contextMock.when(TestFlyContext::getConfig).thenReturn(configWith("chrome", "local"));

        DriverProvider provider = DriverProviderFactory.getProvider();

        assertSame(provider, customChrome, "Custom provider registered via registry should take precedence");
    }

    // ── Unsupported browser ──────────────────────────────────────────────────

    @Test(expectedExceptions = IllegalStateException.class)
    public void getProvider_unknownLocalBrowser_throws() {
        contextMock.when(TestFlyContext::getConfig).thenReturn(configWith("opera", "local"));
        DriverProviderFactory.getProvider();
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
