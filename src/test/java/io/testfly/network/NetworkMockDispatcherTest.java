package io.testfly.network;

import io.testfly.driver.DriverManager;
import org.mockito.MockedStatic;
import org.openqa.selenium.chromium.ChromiumDriver;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.Event;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * A reused driver (browser.lifecycle: per-suite) must not collect one Fetch.requestPaused listener
 * per test: Selenium cannot remove a single listener, so every network event would run through all
 * earlier tests' listeners.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
@Test(singleThreaded = true)
public class NetworkMockDispatcherTest {

    @AfterMethod
    public void cleanup() {
        NetworkMock.cleanup();
    }

    @Test
    public void reusedDriver_getsOneListenerNoMatterHowManyTestsMockTheNetwork() {
        ChromiumDriver driver = mock(ChromiumDriver.class);
        DevTools devTools = mock(DevTools.class);
        when(driver.getDevTools()).thenReturn(devTools);

        try (MockedStatic<DriverManager> dm = mockStatic(DriverManager.class)) {
            dm.when(DriverManager::getDriver).thenReturn(driver);

            for (int test = 0; test < 3; test++) {
                NetworkMock mock = NetworkMock.get();
                mock.mockRoute("**/api/x", Response.status(200));
                assertTrue(mock.isInterceptionActive(), "interception is active in test " + test);
                NetworkMock.cleanup();
            }
        }

        verify(devTools, times(1)).addListener(any(Event.class), any(Consumer.class));
        // Fetch.enable once per test, and Fetch.disable once per cleanup.
        verify(devTools, times(6)).send(any());
    }

    @Test
    public void aFreshDriver_getsItsOwnListener() {
        ChromiumDriver first = mock(ChromiumDriver.class);
        ChromiumDriver second = mock(ChromiumDriver.class);
        DevTools firstTools = mock(DevTools.class);
        DevTools secondTools = mock(DevTools.class);
        when(first.getDevTools()).thenReturn(firstTools);
        when(second.getDevTools()).thenReturn(secondTools);

        try (MockedStatic<DriverManager> dm = mockStatic(DriverManager.class)) {
            dm.when(DriverManager::getDriver).thenReturn(first);
            NetworkMock.get().mockRoute("**/a", Response.status(200));
            NetworkMock.cleanup();

            dm.when(DriverManager::getDriver).thenReturn(second);
            NetworkMock.get().mockRoute("**/b", Response.status(200));
            assertTrue(NetworkMock.get().isInterceptionActive());
        }

        verify(firstTools, times(1)).addListener(any(Event.class), any(Consumer.class));
        verify(secondTools, times(1)).addListener(any(Event.class), any(Consumer.class));
    }

    @Test
    public void nonChromiumDriver_neverTouchesDevTools() {
        org.openqa.selenium.WebDriver driver = mock(org.openqa.selenium.WebDriver.class);
        try (MockedStatic<DriverManager> dm = mockStatic(DriverManager.class)) {
            dm.when(DriverManager::getDriver).thenReturn(driver);
            NetworkMock mock = NetworkMock.get();
            mock.mockRoute("**/a", Response.status(200));
            assertFalse(mock.isInterceptionActive());
        }
    }
}
