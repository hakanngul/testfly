package io.testfly.driver;

import io.testfly.ci.CiEnvironmentDetector;
import io.testfly.config.TestFlyConfig;
import io.testfly.internal.TestFlyContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.safari.SafariOptions;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Built-in local Safari driver provider.
 *
 * <p>Safari has fewer runtime-configurable options than Chrome or Firefox:
 * it does not support headless mode via Selenium, and the download directory
 * is controlled by Safari's user preferences rather than driver options.
 * This provider focuses on argument/capability validation, automatic page-load
 * timeout setup, and container-safe defaults.
 */
public class LocalSafariDriverProvider implements DriverProvider {

    @Override
    public WebDriver createDriver() {
        TestFlyConfig config = TestFlyContext.getConfig();

        SafariOptions options = new SafariOptions();
        List<String> arguments = config.getBrowser().getArguments();

        BrowserArgumentValidator.validate("safari", arguments);

        // Safari does not expose command-line arguments through Selenium.
        // Arguments are accepted and validated for config consistency, but they
        // are not forwarded to the driver options here.

        Map<String, Object> capabilities = config.getBrowser().getCapabilities();
        CapabilityValidator.validate("safari", capabilities);

        if (capabilities != null) {
            capabilities.forEach(options::setCapability);
        }

        // Safari does not support headless mode through Selenium.
        if (config.getBrowser().isHeadless()) {
            System.err.println("[TestFly] Safari does not support headless mode via Selenium; ignoring headless=true.");
        }

        // Safari does not have container-specific flags analogous to Chrome's
        // --no-sandbox, but we keep the branch for symmetry and future-proofing.
        if (CiEnvironmentDetector.isContainer()) {
            System.err.println("[TestFly] Running Safari inside a container may require WebDriver+Safari setup; ensure the environment supports it.");
        }

        WebDriver driver = new SafariDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ZERO);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(config.getTimeouts().getPageLoad()));

        return driver;
    }
}
