package io.testfly.driver;

import io.testfly.browser.BrowserContext;
import io.testfly.config.TestFlyConfig;
import io.testfly.internal.TestFlyContext;

public final class DriverProviderFactory {
    private DriverProviderFactory() {}

    public static DriverProvider getProvider() {
        TestFlyConfig config = TestFlyContext.getConfig();
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

        String browser = resolveBrowserName(config);

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

    /**
     * Browser for the current thread: the {@link BrowserContext} override (set for
     * {@code browser.matrix} runs) wins over YAML {@code browser.name}.
     *
     * @throws IllegalStateException when neither is set
     */
    public static String resolveBrowserName(TestFlyConfig config) {
        String contextBrowser = BrowserContext.get();
        if (contextBrowser != null && !contextBrowser.isBlank()) {
            return contextBrowser;
        }
        String name = config.getBrowser().getName();
        if (name == null || name.isBlank()) {
            throw new IllegalStateException(
                    "No browser selected: set browser.name, or run through browser.matrix.");
        }
        return name;
    }
}
