package io.testfly.unit.driver;

import io.testfly.config.TestFlyConfig;
import io.testfly.driver.DriverSetup;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.testng.annotations.Test;

import java.time.Duration;

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.expectThrows;

/**
 * Unit tests for {@link DriverSetup}: a session whose post-launch setup fails
 * must be quit so the browser process does not leak.
 */
public class DriverSetupTest {

    private static TestFlyConfig config() {
        TestFlyConfig.Timeouts timeouts = new TestFlyConfig.Timeouts();
        timeouts.setExplicit(10);
        timeouts.setPageLoad(30);
        TestFlyConfig config = new TestFlyConfig();
        config.setTimeouts(timeouts);
        return config;
    }

    @Test
    public void configureTimeouts_appliesTimeoutsAndKeepsSession() {
        WebDriver driver = mock(WebDriver.class, RETURNS_DEEP_STUBS);

        assertSame(DriverSetup.configureTimeouts(driver, config()), driver);

        verify(driver.manage().timeouts()).implicitlyWait(Duration.ZERO);
        verify(driver.manage().timeouts()).pageLoadTimeout(Duration.ofSeconds(30));
        verify(driver, never()).quit();
    }

    @Test
    public void configureTimeouts_failure_quitsSessionAndRethrows() {
        WebDriver driver = mock(WebDriver.class, RETURNS_DEEP_STUBS);
        WebDriver.Timeouts timeouts = driver.manage().timeouts();
        WebDriverException failure = new WebDriverException("timeouts rejected");
        when(timeouts.pageLoadTimeout(Duration.ofSeconds(30))).thenThrow(failure);

        WebDriverException thrown = expectThrows(WebDriverException.class,
                () -> DriverSetup.configureTimeouts(driver, config()));

        assertSame(thrown, failure);
        verify(driver).quit();
    }
}
