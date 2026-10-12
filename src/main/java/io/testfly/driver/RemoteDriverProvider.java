package io.testfly.driver;

import io.testfly.config.TestFlyConfig;
import io.testfly.internal.TestFlyContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.chromium.ChromiumOptions;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.AbstractDriverOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.safari.SafariOptions;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Selenium Grid provider ({@code execution.mode: remote}) for Chrome, Edge,
 * Firefox and Safari. The browser follows the same resolution as local runs,
 * so {@code browser.matrix} works on the Grid too.
 */
public class RemoteDriverProvider implements DriverProvider {
    @Override
    public WebDriver createDriver() {
        TestFlyConfig config = TestFlyContext.getConfig();

        String gridUrl = config.getExecution().getGridUrl();

        if (gridUrl == null || gridUrl.isEmpty()) {
            throw new IllegalArgumentException(String.format("Invalid gridUrl: %s", gridUrl));
        }

        AbstractDriverOptions<?> options = buildOptions(config, DriverProviderFactory.resolveBrowserName(config));

        try {
            URL url = URI.create(gridUrl).toURL();
            WebDriver driver = new RemoteWebDriver(url, options);
            DriverSetup.configureTimeouts(driver, config);
            return driver;
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Invalid Grid URL: " + gridUrl, e);
        }
    }

    /**
     * Builds the Grid session options for {@code browser}. Exposed so it can be
     * unit-tested without a Grid.
     */
    public static AbstractDriverOptions<?> buildOptions(TestFlyConfig config, String browser) {
        TestFlyConfig.Browser browserConfig = config.getBrowser();
        List<String> arguments = browserConfig.getArguments();
        Map<String, Object> capabilities = browserConfig.getCapabilities();

        BrowserArgumentValidator.validate(browser, arguments);
        CapabilityValidator.validate(browser, capabilities);

        AbstractDriverOptions<?> options;
        switch (browser.toLowerCase(Locale.ROOT)) {
            case "chrome" -> options = chromium(new ChromeOptions(), browserConfig.isHeadless(), arguments);
            case "edge" -> options = chromium(new EdgeOptions(), browserConfig.isHeadless(), arguments);
            case "firefox" -> {
                FirefoxOptions firefox = new FirefoxOptions();
                if (browserConfig.isHeadless()) {
                    firefox.addArguments("-headless");
                }
                if (arguments != null) {
                    firefox.addArguments(arguments);
                }
                options = firefox;
            }
            case "safari" -> {
                // Safari has no headless mode and takes no arguments; the validator already warned.
                if (browserConfig.isHeadless()) {
                    System.err.println("[TestFly] Safari does not support headless mode via Selenium; ignoring headless=true.");
                }
                options = new SafariOptions();
            }
            default -> throw new IllegalStateException(
                    "Unsupported browser for remote execution: " + browser
                            + ". Supported: chrome, edge, firefox, safari.");
        }

        // Keep alerts open until the test explicitly handles them; user capabilities may override.
        options.setCapability("unhandledPromptBehavior", "ignore");

        if (capabilities != null) {
            capabilities.forEach(options::setCapability);
        }
        return options;
    }

    private static AbstractDriverOptions<?> chromium(ChromiumOptions<?> options, boolean headless, List<String> arguments) {
        if (headless) {
            options.addArguments("--headless=new");
        }
        if (arguments != null) {
            options.addArguments(arguments);
        }
        return options;
    }
}
