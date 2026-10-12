package io.testfly.driver;

import io.testfly.config.TestFlyConfig;
import org.openqa.selenium.WebDriver;

import java.time.Duration;

/**
 * Post-launch setup shared by the built-in driver providers.
 */
public final class DriverSetup {

    private DriverSetup() {
    }

    /**
     * Applies the framework timeouts (no implicit wait, configured page-load
     * timeout) to a freshly started session. If that fails, the session is quit
     * before the error is rethrown, so the browser process is not left running.
     */
    public static WebDriver configureTimeouts(WebDriver driver, TestFlyConfig config) {
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ZERO);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(config.getTimeouts().getPageLoad()));
            return driver;
        } catch (RuntimeException e) {
            try {
                driver.quit();
            } catch (RuntimeException quitFailure) {
                e.addSuppressed(quitFailure);
            }
            throw e;
        }
    }
}
