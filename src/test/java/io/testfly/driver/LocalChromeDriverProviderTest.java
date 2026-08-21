package io.testfly.driver;

import io.testfly.config.TestFlyConfig;
import io.testfly.driver.LocalChromeDriverProvider;
import io.testfly.internal.TestFlyContext;
import org.openqa.selenium.chrome.ChromeOptions;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.Assert.*;

public class LocalChromeDriverProviderTest {

    @BeforeMethod
    public void setUp() {
        TestFlyConfig config = new TestFlyConfig();

        TestFlyConfig.Browser browser = new TestFlyConfig.Browser();
        browser.setName("chrome");
        config.setBrowser(browser);

        TestFlyConfig.Execution execution = new TestFlyConfig.Execution();
        execution.setMode("local");
        config.setExecution(execution);

        TestFlyContext.initialize(config);
    }

    @Test
    public void passwordManagerAndLeakDetectionAreDisabled() {
        TestFlyConfig config = TestFlyContext.getConfig();

        TestFlyConfig.Browser browser = new TestFlyConfig.Browser();
        browser.setName("chrome");
        browser.setHeadless(false);
        browser.setArguments(null);
        browser.setCapabilities(null);
        config.setBrowser(browser);

        TestFlyConfig.Execution execution = new TestFlyConfig.Execution();
        execution.setMode("local");
        config.setExecution(execution);

        TestFlyConfig.Timeouts timeouts = new TestFlyConfig.Timeouts();
        timeouts.setPageLoad(30);
        timeouts.setExplicit(10);
        config.setTimeouts(timeouts);

        ChromeOptions options = LocalChromeDriverProvider.buildOptions(config);

        @SuppressWarnings("unchecked")
        Map<String, Object> chromeOptions = (Map<String, Object>) options.asMap().get(ChromeOptions.CAPABILITY);
        assertNotNull(chromeOptions, "goog:chromeOptions should be present");

        @SuppressWarnings("unchecked")
        Map<String, Object> prefs = (Map<String, Object>) chromeOptions.get("prefs");
        assertNotNull(prefs, "prefs should be present");

        assertEquals(prefs.get("profile.password_manager_enabled"), Boolean.FALSE,
                "password manager should be disabled");
        assertEquals(prefs.get("profile.password_manager_leak_detection"), Boolean.FALSE,
                "password leak detection should be disabled");
        assertEquals(prefs.get("credentials_enable_service"), Boolean.FALSE,
                "credentials service should be disabled");
    }
}
