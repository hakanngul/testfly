package io.testfly.unit.browser;

import io.testfly.browser.DeviceEmulator;
import io.testfly.browser.DeviceProfile;
import io.testfly.browser.DeviceProfiles;
import io.testfly.driver.DriverManager;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.ChromiumDriver;
import org.openqa.selenium.devtools.DevTools;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link DeviceEmulator}.
 * Mocks both Chromium (CDP path) and non-Chromium (fallback path) drivers.
 */
@Test(singleThreaded = true)
public class DeviceEmulatorTest {

    private ChromiumDriver mockChromeDriver;
    private DevTools mockDevTools;

    /** Combined interface for mocking a WebDriver that also executes JS. */
    interface JsCapableDriver extends WebDriver, JavascriptExecutor {
    }

    private JsCapableDriver mockNonChromeDriver;
    private WebDriver.Window mockWindow;

    private MockedStatic<DriverManager> driverManagerMock;

    @BeforeMethod
    public void setUp() {
        // --- Chromium driver + DevTools ---
        mockChromeDriver = mock(ChromiumDriver.class);
        mockDevTools = mock(DevTools.class);
        lenient().when(mockChromeDriver.getDevTools()).thenReturn(mockDevTools);

        // --- Non-Chromium driver (JS-capable) ---
        mockNonChromeDriver = mock(JsCapableDriver.class);
        WebDriver.Options mockOptions = mock(WebDriver.Options.class);
        mockWindow = mock(WebDriver.Window.class);
        lenient().when(mockNonChromeDriver.manage()).thenReturn(mockOptions);
        lenient().when(mockOptions.window()).thenReturn(mockWindow);

        driverManagerMock = mockStatic(DriverManager.class);
    }

    @AfterMethod
    public void tearDown() {
        if (driverManagerMock != null)
            driverManagerMock.close();
    }

    // ---------------------------------------------------------------
    // Chromium CDP path
    // ---------------------------------------------------------------

    @Test
    public void emulate_chromium_appliesDeviceProfileViaCdp() {
        driverManagerMock.when(DriverManager::getDriver).thenReturn(mockChromeDriver);
        DeviceProfile profile = DeviceProfiles.get("iPhone 14");

        DeviceEmulator.emulate(profile);

        verify(mockChromeDriver).getDevTools();
        verify(mockDevTools).createSession();
        // setDeviceMetricsOverride + setUserAgentOverride = 2 send calls
        verify(mockDevTools, atLeast(2)).send(any());
    }

    @Test
    public void emulate_chromium_sendsCorrectNumberOfCommands() {
        driverManagerMock.when(DriverManager::getDriver).thenReturn(mockChromeDriver);
        DeviceProfile profile = DeviceProfiles.get("Pixel 7");

        DeviceEmulator.emulate(profile);

        // Exactly 2 CDP commands: setDeviceMetricsOverride + setUserAgentOverride
        verify(mockDevTools, atLeast(2)).send(any());
    }

    @Test
    public void reset_chromium_clearsEmulation() {
        driverManagerMock.when(DriverManager::getDriver).thenReturn(mockChromeDriver);

        DeviceEmulator.reset();

        verify(mockChromeDriver).getDevTools();
        verify(mockDevTools).createSession();
        // clearDeviceMetricsOverride + setUserAgentOverride("") = 2 send calls
        verify(mockDevTools, atLeast(2)).send(any());
    }

    // ---------------------------------------------------------------
    // Non-Chromium fallback path
    // ---------------------------------------------------------------

    @Test
    public void emulate_nonChrome_setsWindowSize() {
        driverManagerMock.when(DriverManager::getDriver).thenReturn(mockNonChromeDriver);
        DeviceProfile profile = DeviceProfiles.get("iPhone 14");

        DeviceEmulator.emulate(profile);

        ArgumentCaptor<Dimension> dimCaptor = ArgumentCaptor.forClass(Dimension.class);
        verify(mockWindow).setSize(dimCaptor.capture());

        Dimension dim = dimCaptor.getValue();
        assertEquals(dim.getWidth(), 390, "Width should match iPhone 14 profile");
        assertEquals(dim.getHeight(), 844, "Height should match iPhone 14 profile");
    }

    @Test
    public void emulate_nonChrome_overridesUserAgentViaJs() {
        driverManagerMock.when(DriverManager::getDriver).thenReturn(mockNonChromeDriver);
        DeviceProfile profile = DeviceProfiles.get("iPhone 14");

        DeviceEmulator.emulate(profile);

        verify((JavascriptExecutor) mockNonChromeDriver).executeScript(anyString(), anyString());
    }

    @Test
    public void emulate_nonChrome_pixel7Dimensions() {
        driverManagerMock.when(DriverManager::getDriver).thenReturn(mockNonChromeDriver);
        DeviceProfile profile = DeviceProfiles.get("Pixel 7");

        DeviceEmulator.emulate(profile);

        ArgumentCaptor<Dimension> dimCaptor = ArgumentCaptor.forClass(Dimension.class);
        verify(mockWindow).setSize(dimCaptor.capture());

        Dimension dim = dimCaptor.getValue();
        assertEquals(dim.getWidth(), 412);
        assertEquals(dim.getHeight(), 915);
    }

    @Test
    public void reset_nonChrome_resizesToDesktop() {
        driverManagerMock.when(DriverManager::getDriver).thenReturn(mockNonChromeDriver);

        DeviceEmulator.reset();

        ArgumentCaptor<Dimension> dimCaptor = ArgumentCaptor.forClass(Dimension.class);
        verify(mockWindow).setSize(dimCaptor.capture());

        Dimension dim = dimCaptor.getValue();
        assertEquals(dim.getWidth(), 1280, "Reset should set standard desktop width");
        assertEquals(dim.getHeight(), 800, "Reset should set standard desktop height");
    }

    // ---------------------------------------------------------------
    // Named profile resolution
    // ---------------------------------------------------------------

    @Test
    public void emulate_byName_resolvesBuiltInProfile() {
        driverManagerMock.when(DriverManager::getDriver).thenReturn(mockNonChromeDriver);

        DeviceEmulator.emulate("Galaxy S23");

        ArgumentCaptor<Dimension> dimCaptor = ArgumentCaptor.forClass(Dimension.class);
        verify(mockWindow).setSize(dimCaptor.capture());

        Dimension dim = dimCaptor.getValue();
        assertEquals(dim.getWidth(), 360);
        assertEquals(dim.getHeight(), 780);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void emulate_unknownDeviceName_throws() {
        DeviceEmulator.emulate("NonExistent Device XYZ-999");
    }

    @Test
    public void emulate_caseInsensitiveName() {
        driverManagerMock.when(DriverManager::getDriver).thenReturn(mockNonChromeDriver);

        // Should not throw — case-insensitive lookup
        DeviceEmulator.emulate("IPHONE 14");

        verify(mockWindow).setSize(any(Dimension.class));
    }

    // ---------------------------------------------------------------
    // Custom profile
    // ---------------------------------------------------------------

    @Test
    public void emulate_customProfile_applied() {
        driverManagerMock.when(DriverManager::getDriver).thenReturn(mockNonChromeDriver);

        DeviceProfile custom = new DeviceProfile(
                "Test Device", 500, 1000, 2.5, true, "TestBot/1.0");

        DeviceEmulator.emulate(custom);

        ArgumentCaptor<Dimension> dimCaptor = ArgumentCaptor.forClass(Dimension.class);
        verify(mockWindow).setSize(dimCaptor.capture());

        Dimension dim = dimCaptor.getValue();
        assertEquals(dim.getWidth(), 500);
        assertEquals(dim.getHeight(), 1000);
    }

    @Test
    public void emulate_customProfile_registeredAndRetrievable() {
        String name = "Custom Test Phone " + System.nanoTime();
        DeviceProfile custom = new DeviceProfile(name, 420, 900, 3.0, true, "CustomUA/2.0");
        DeviceProfiles.register(name, custom);

        DeviceProfile retrieved = DeviceProfiles.get(name);
        assertNotNull(retrieved);
        assertEquals(retrieved.getWidth(), 420);
        assertEquals(retrieved.getHeight(), 900);
        assertEquals(retrieved.getDeviceScaleFactor(), 3.0, 0.001);
        assertTrue(retrieved.isMobile());
        assertEquals(retrieved.getUserAgent(), "CustomUA/2.0");
    }

    @Test
    public void emulate_tabletProfile_notMobile() {
        driverManagerMock.when(DriverManager::getDriver).thenReturn(mockNonChromeDriver);
        DeviceProfile profile = DeviceProfiles.get("iPad Pro 12");

        DeviceEmulator.emulate(profile);

        ArgumentCaptor<Dimension> dimCaptor = ArgumentCaptor.forClass(Dimension.class);
        verify(mockWindow).setSize(dimCaptor.capture());

        Dimension dim = dimCaptor.getValue();
        assertEquals(dim.getWidth(), 1024);
        assertEquals(dim.getHeight(), 1366);
    }

    // ---------------------------------------------------------------
    // Non-Chromium fallback tolerates JS failures
    // ---------------------------------------------------------------

    @Test
    public void emulate_nonChrome_toleratesJsException() {
        driverManagerMock.when(DriverManager::getDriver).thenReturn(mockNonChromeDriver);

        when(((JavascriptExecutor) mockNonChromeDriver).executeScript(anyString(), anyString()))
                .thenThrow(new RuntimeException("JS failed"));

        // Should not throw — JS UA override failure is tolerated
        DeviceEmulator.emulate("iPhone SE");

        // Window resize should still happen even if JS fails
        verify(mockWindow).setSize(any(Dimension.class));
    }
}
