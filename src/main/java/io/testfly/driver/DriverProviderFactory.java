package io.testfly.driver;

import io.testfly.browser.BrowserContext;
import io.testfly.config.TestFlyConfig;
import io.testfly.internal.TestFlyContext;

public final class DriverProviderFactory {
    private DriverProviderFactory() {}

    public static DriverProvider getProvider() {
        TestFlyConfig config = TestFlyContext.getConfig();
        // BrowserContext override (set by BrowserMatrixListener) takes precedence over YAML browser.name
        String contextBrowser = BrowserContext.get();
        String browser = (contextBrowser != null && !contextBrowser.isEmpty())
                ? contextBrowser
                : config.getBrowser().getName();
        String executionMode = config.getExecution().getMode();

        if ("remote".equalsIgnoreCase(executionMode)) {
            return new RemoteDriverProvider();
        }

        if ("browserstack".equalsIgnoreCase(executionMode)) {
            return new BrowserStackProvider();
        }

        if ("saucelabs".equalsIgnoreCase(executionMode)) {
            return new SauceLabsProvider();
        }

        // Custom providers registered via SPI or programmatically take precedence
        DriverProvider custom = DriverProviderRegistry.find(browser);
        if (custom != null) {
            return custom;
        }

        if ("chrome".equalsIgnoreCase(browser)) {
            return new LocalChromeDriverProvider();
        }

        if ("firefox".equalsIgnoreCase(browser)) {
            return new LocalFirefoxDriverProvider();
        }

        if ("edge".equalsIgnoreCase(browser)) {
            return new LocalEdgeDriverProvider();
        }

        if ("safari".equalsIgnoreCase(browser)) {
            return new LocalSafariDriverProvider();
        }

        throw new IllegalStateException(
            "Unsupported browser: " + browser +
            ". Register a NamedDriverProvider via SPI or DriverProviderRegistry.register()."
        );
    }
}
