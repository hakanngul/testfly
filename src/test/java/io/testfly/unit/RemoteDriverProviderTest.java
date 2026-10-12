package io.testfly.unit;

import io.testfly.browser.BrowserContext;
import io.testfly.config.TestFlyConfig;
import io.testfly.driver.DriverProviderFactory;
import io.testfly.driver.RemoteDriverProvider;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.AbstractDriverOptions;
import org.openqa.selenium.safari.SafariOptions;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Unit tests for Grid session options built by {@link RemoteDriverProvider}
 * and the shared browser resolution in {@link DriverProviderFactory}.
 * No Grid is contacted.
 */
@Test(singleThreaded = true)
public class RemoteDriverProviderTest {

    @AfterMethod
    public void clearContext() {
        BrowserContext.clear();
    }

    private static TestFlyConfig config(String name, boolean headless, List<String> args, Map<String, Object> caps) {
        TestFlyConfig.Browser browser = new TestFlyConfig.Browser();
        browser.setName(name);
        browser.setHeadless(headless);
        browser.setArguments(args);
        browser.setCapabilities(caps);
        TestFlyConfig.Execution execution = new TestFlyConfig.Execution();
        execution.setMode("remote");
        TestFlyConfig config = new TestFlyConfig();
        config.setBrowser(browser);
        config.setExecution(execution);
        return config;
    }

    @SuppressWarnings("unchecked")
    private static List<String> chromiumArgs(AbstractDriverOptions<?> options, String key) {
        Map<String, Object> opts = (Map<String, Object>) options.getCapability(key);
        return (List<String>) opts.get("args");
    }

    @Test
    public void buildOptions_chrome_appliesHeadlessArgsAndKeepsAlerts() {
        AbstractDriverOptions<?> options = RemoteDriverProvider.buildOptions(
                config("chrome", true, List.of("--disable-notifications"), null), "chrome");

        assertTrue(options instanceof ChromeOptions);
        List<String> args = chromiumArgs(options, ChromeOptions.CAPABILITY);
        assertTrue(args.contains("--headless=new"), args.toString());
        assertTrue(args.contains("--disable-notifications"), args.toString());
        assertEquals(options.getCapability("unhandledPromptBehavior"), "ignore");
    }

    @Test
    public void buildOptions_edge_isSupported() {
        AbstractDriverOptions<?> options = RemoteDriverProvider.buildOptions(
                config("edge", true, null, null), "edge");

        assertTrue(options instanceof EdgeOptions);
        assertTrue(chromiumArgs(options, EdgeOptions.CAPABILITY).contains("--headless=new"));
        assertEquals(options.getCapability("unhandledPromptBehavior"), "ignore");
    }

    @Test
    public void buildOptions_firefox_appliesHeadless() {
        AbstractDriverOptions<?> options = RemoteDriverProvider.buildOptions(
                config("firefox", true, null, null), "firefox");

        assertTrue(options instanceof FirefoxOptions);
        assertTrue(options.getCapability("moz:firefoxOptions").toString().contains("-headless"));
    }

    @Test
    public void buildOptions_safari_isSupportedAndIgnoresArguments() {
        AbstractDriverOptions<?> options = RemoteDriverProvider.buildOptions(
                config("safari", true, List.of("--start-maximized"), null), "safari");

        assertTrue(options instanceof SafariOptions);
        assertFalse(options.asMap().toString().contains("--start-maximized"));
        assertEquals(options.getCapability("unhandledPromptBehavior"), "ignore");
    }

    @Test
    public void buildOptions_userCapabilityOverridesPromptDefault() {
        AbstractDriverOptions<?> options = RemoteDriverProvider.buildOptions(
                config("chrome", false, null, Map.of("unhandledPromptBehavior", "accept")), "chrome");

        assertEquals(options.getCapability("unhandledPromptBehavior"), "accept");
    }

    @Test
    public void buildOptions_unknownBrowser_throws() {
        IllegalStateException e = expectThrows(IllegalStateException.class,
                () -> RemoteDriverProvider.buildOptions(config("opera", false, null, null), "opera"));
        assertTrue(e.getMessage().contains("Supported: chrome, edge, firefox, safari"), e.getMessage());
    }

    @Test
    public void resolveBrowserName_matrixContextWinsOverYaml() {
        BrowserContext.set("firefox");
        assertEquals(DriverProviderFactory.resolveBrowserName(config("chrome", false, null, null)), "firefox");
    }

    @Test
    public void resolveBrowserName_matrixOnlyConfigUsesContext() {
        BrowserContext.set("edge");
        assertEquals(DriverProviderFactory.resolveBrowserName(config(null, false, null, null)), "edge");
    }

    @Test
    public void resolveBrowserName_nothingSelected_throwsClearError() {
        IllegalStateException e = expectThrows(IllegalStateException.class,
                () -> DriverProviderFactory.resolveBrowserName(config(null, false, null, null)));
        assertTrue(e.getMessage().contains("No browser selected"), e.getMessage());
    }
}
