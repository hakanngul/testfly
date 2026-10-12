package io.testfly.unit;

import io.testfly.browser.GeoLocation;
import io.testfly.driver.DriverManager;
import org.mockito.MockedStatic;
import org.openqa.selenium.chromium.ChromiumDriver;
import org.testng.annotations.Test;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A location mocked in one test must not leak into the next test on a reused browser. */
@Test(singleThreaded = true)
public class GeoLocationAutoResetTest {

    @Test
    public void autoReset_clearsTheOverrideOnce_whenATestSetOne() {
        ChromiumDriver driver = mock(ChromiumDriver.class);
        try (MockedStatic<DriverManager> dm = mockStatic(DriverManager.class)) {
            dm.when(DriverManager::getDriver).thenReturn(driver);

            GeoLocation.instance().set(51.5, -0.12);
            GeoLocation.autoReset();
            GeoLocation.autoReset(); // second call must be a no-op

            verify(driver, times(1)).executeCdpCommand(eq("Emulation.clearGeolocationOverride"), anyMap());
        }
    }

    @Test
    public void autoReset_doesNothing_whenNoTestSetALocation() {
        ChromiumDriver driver = mock(ChromiumDriver.class);
        try (MockedStatic<DriverManager> dm = mockStatic(DriverManager.class)) {
            dm.when(DriverManager::getDriver).thenReturn(driver);

            GeoLocation.autoReset();

            verify(driver, never()).executeCdpCommand(eq("Emulation.clearGeolocationOverride"), anyMap());
        }
    }

    @Test
    public void autoReset_doesNotRepeat_afterAnExplicitClear() {
        ChromiumDriver driver = mock(ChromiumDriver.class);
        try (MockedStatic<DriverManager> dm = mockStatic(DriverManager.class)) {
            dm.when(DriverManager::getDriver).thenReturn(driver);

            GeoLocation.instance().set(1, 2);
            GeoLocation.instance().clear();
            GeoLocation.autoReset();

            verify(driver, times(1)).executeCdpCommand(eq("Emulation.clearGeolocationOverride"), anyMap());
        }
    }

    @Test
    public void autoReset_neverThrows_whenTheBrowserIsGone() {
        ChromiumDriver driver = mock(ChromiumDriver.class);
        when(driver.executeCdpCommand(eq("Emulation.clearGeolocationOverride"), anyMap()))
                .thenThrow(new org.openqa.selenium.WebDriverException("session deleted"));
        try (MockedStatic<DriverManager> dm = mockStatic(DriverManager.class)) {
            dm.when(DriverManager::getDriver).thenReturn(driver);

            GeoLocation.instance().set(1, 2);
            GeoLocation.autoReset(); // must not throw

            GeoLocation.autoReset(); // and the flag must be gone, so this does nothing
            verify(driver, times(1)).executeCdpCommand(eq("Emulation.clearGeolocationOverride"), anyMap());
        }
    }
}
