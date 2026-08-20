package io.testfly.driver;

import io.testfly.browser.DownloadManager;
import io.testfly.ci.CiEnvironmentDetector;
import io.testfly.config.TestFlyConfig;
import io.testfly.internal.TestFlyContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Built-in local Edge driver provider.
 *
 * <p>Supports the same options as Chrome where applicable: headless mode,
 * custom arguments, capabilities, container flags, and automatic download
 * directory configuration.
 */
public class LocalEdgeDriverProvider implements DriverProvider {

    @Override
    public WebDriver createDriver() {
        TestFlyConfig config = TestFlyContext.getConfig();
        EdgeOptions options = new EdgeOptions();

        List<String> arguments = config.getBrowser().getArguments();
        BrowserArgumentValidator.validate("edge", arguments);

        Map<String, Object> capabilities = config.getBrowser().getCapabilities();
        CapabilityValidator.validate("edge", capabilities);

        if (capabilities != null) {
            capabilities.forEach(options::setCapability);
        }

        if (config.getBrowser().isHeadless()) {
            options.addArguments("--headless=new");
        }

        // Docker/container: Edge requires these flags to run without a real display
        if (CiEnvironmentDetector.isContainer()) {
            options.addArguments(
                    "--no-sandbox",
                    "--disable-dev-shm-usage",
                    "--disable-gpu",
                    "--window-size=1920,1080"
            );
        }

        if (arguments != null) {
            options.addArguments(arguments);
        }

        // Auto-configure download directory — local mode only.
        if (!"remote".equalsIgnoreCase(config.getExecution().getMode())) {
            String downloadDir = DownloadManager.resolveDownloadDir().getAbsolutePath();
            Map<String, Object> prefs = new HashMap<>();
            prefs.put("download.default_directory", downloadDir);
            prefs.put("download.prompt_for_download", false);
            prefs.put("download.directory_upgrade", true);
            options.setExperimentalOption("prefs", prefs);
        }

        // Keep alerts open until the test explicitly handles them.
        options.setCapability("unhandledPromptBehavior", "ignore");

        WebDriver driver = new EdgeDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ZERO);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(config.getTimeouts().getPageLoad()));

        return driver;
    }
}
