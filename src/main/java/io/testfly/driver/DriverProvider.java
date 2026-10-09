package io.testfly.driver;

import io.testfly.api.TestFlyApi;
import org.openqa.selenium.WebDriver;

/**
 * Strategy interface for WebDriver creation.
 */
@TestFlyApi(since = "0.1.0")
public interface DriverProvider {
    WebDriver createDriver();
}
