package io.testfly.driver;

import io.testfly.browser.DownloadManager;
import io.testfly.config.TestFlyConfig;
import org.mockito.MockedStatic;
import org.openqa.selenium.chrome.ChromeOptions;
import org.testng.annotations.Test;

import java.io.File;
import java.util.Map;

import static org.mockito.Mockito.mockStatic;
import static org.testng.Assert.*;

@Test(singleThreaded = true)
public class LocalChromeDriverProviderTest {

    @Test
    public void passwordManagerAndLeakDetectionAreDisabled() {
        TestFlyConfig config = new TestFlyConfig();

        TestFlyConfig.Browser browser = new TestFlyConfig.Browser();
        browser.setName("chrome");
        browser.setHeadless(false);
        browser.setArguments(null);
        browser.setCapabilities(null);
        config.setBrowser(browser);

        TestFlyConfig.Execution execution = new TestFlyConfig.Execution();
        execution.setMode("local");
        execution.setBaseUrl("https://example.com");
        execution.setMaxActiveSessions(5);
        config.setExecution(execution);

        TestFlyConfig.Timeouts timeouts = new TestFlyConfig.Timeouts();
        timeouts.setPageLoad(30);
        timeouts.setExplicit(10);
        config.setTimeouts(timeouts);

        // DownloadManager.resolveDownloadDir() internally calls TestFlyContext.getConfig()
        // which is cleared by parallel tests. Mock it to avoid global-state dependency.
        File fakeDownloadDir = new File(System.getProperty("java.io.tmpdir"), "testfly-downloads-test");
        try (MockedStatic<DownloadManager> dmMock = mockStatic(DownloadManager.class)) {
            dmMock.when(DownloadManager::resolveDownloadDir).thenReturn(fakeDownloadDir);

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
}
