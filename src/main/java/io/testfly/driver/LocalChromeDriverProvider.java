package io.testfly.driver;

import io.testfly.browser.DownloadManager;
import io.testfly.ci.CiEnvironmentDetector;
import io.testfly.config.TestFlyConfig;
import io.testfly.internal.TestFlyContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LocalChromeDriverProvider implements DriverProvider {

    @Override
    public WebDriver createDriver() {
        TestFlyConfig config = TestFlyContext.getConfig();
        ChromeOptions options = buildOptions(config);

        WebDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ZERO);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(config.getTimeouts().getPageLoad()));

        if (!config.getBrowser().isHeadless()) {
            List<String> args = config.getBrowser().getArguments();
            if (args != null && args.contains("--start-maximized")) {
                try {
                    driver.manage().window().maximize();
                } catch (Exception ignored) {
                }
            }
        }

        return driver;
    }

    /**
     * Builds the {@link ChromeOptions} for a local Chrome session. Exposed as
     * package-private so it can be unit-tested without launching a browser.
     */
    public static ChromeOptions buildOptions(TestFlyConfig config) {
        ChromeOptions options = new ChromeOptions();

//        ChromeOption Arguments Validation
        List<String> arguments = config.getBrowser().getArguments();
        BrowserArgumentValidator.validate("chrome", arguments);

//        ChromeDriver Capabilities validation
        Map<String, Object> capabilities = config.getBrowser().getCapabilities();
        CapabilityValidator.validate("chrome", capabilities);

        if (capabilities != null) {
            capabilities.forEach(options::setCapability);
        }

        boolean hasWindowSize = arguments != null && arguments.stream().anyMatch(a -> a != null && a.startsWith("--window-size"));
        boolean hasStartMaximized = arguments != null && arguments.stream().anyMatch(a -> a != null && a.equals("--start-maximized"));

        if (config.getBrowser().isHeadless()) {
            options.addArguments("--headless=new");
            // In headless mode Chrome has no desktop window manager, so --start-maximized is ignored.
            // Automatically set 1920x1080 viewport if --start-maximized is configured or if no explicit window-size was given.
            if ((hasStartMaximized || CiEnvironmentDetector.isContainer()) && !hasWindowSize) {
                options.addArguments("--window-size=1920,1080");
            }
        }

        // Docker/container: Chrome requires these flags to run without a real display
        if (CiEnvironmentDetector.isContainer()) {
            options.addArguments(
                    "--no-sandbox",
                    "--disable-dev-shm-usage",
                    "--disable-gpu"
            );
            if (!hasWindowSize && !config.getBrowser().isHeadless()) {
                options.addArguments("--window-size=1920,1080");
            }
        }

        if (arguments != null) {
            options.addArguments(arguments);
        }

        // Auto-configure download directory — local mode only.
        // Remote/Grid sessions download to the node's filesystem; DownloadManager
        // polls the local filesystem, so prefs would be ineffective in remote mode.
        if (!"remote".equalsIgnoreCase(config.getExecution().getMode())) {
            String downloadDir = DownloadManager.resolveDownloadDir().getAbsolutePath();
            Map<String, Object> prefs = new HashMap<>();
            prefs.put("download.default_directory", downloadDir);
            prefs.put("download.prompt_for_download", false);
            prefs.put("download.directory_upgrade", true);

            // Disable Chrome password manager and leak-detection warnings so they
            // do not interrupt automated test flows (e.g. data-breach pop-ups).
            prefs.put("profile.password_manager_enabled", false);
            prefs.put("profile.password_manager_leak_detection", false);
            prefs.put("credentials_enable_service", false);

            options.setExperimentalOption("prefs", prefs);
        }

        // Keep alerts open until the test explicitly handles them.
        // W3C default is "dismiss and notify" which auto-dismisses alerts before
        // the next WebDriver command executes, making driver.switchTo().alert() fail.
        options.setCapability("unhandledPromptBehavior", "ignore");

        return options;
    }
}
