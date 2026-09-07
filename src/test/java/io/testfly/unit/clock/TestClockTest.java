package io.testfly.unit.clock;

import io.testfly.clock.TestClock;
import io.testfly.driver.DriverManager;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.ChromiumDriver;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

/**
 * Unit tests for {@link TestClock} CDP persistence behavior.
 */
public class TestClockTest {

    private WebDriver mockDriver;
    private ChromiumDriver mockChromiumDriver;

    @BeforeMethod
    public void setUp() {
        mockChromiumDriver = mock(ChromiumDriver.class, withSettings().extraInterfaces(JavascriptExecutor.class));
        mockDriver = mockChromiumDriver;
    }

    @AfterMethod
    public void tearDown() {
        TestClock.autoReset();
    }

    @Test
    public void testSet_usesCdpForChromiumBrowsers() {
        // Arrange
        Map<String, Object> cdpResult = new HashMap<>();
        cdpResult.put("identifier", "script-123");
        when(mockChromiumDriver.executeCdpCommand(eq("Page.addScriptToEvaluateOnNewDocument"), anyMap()))
                .thenReturn(cdpResult);

        try (MockedStatic<DriverManager> driverManagerMock = Mockito.mockStatic(DriverManager.class)) {
            driverManagerMock.when(DriverManager::getDriver).thenReturn(mockChromiumDriver);

            // Act
            TestClock clock = TestClock.create();
            clock.set("2030-01-01T00:00:00Z");

            // Assert - CDP command was called to persist the script
            verify(mockChromiumDriver).executeCdpCommand(
                    eq("Page.addScriptToEvaluateOnNewDocument"),
                    argThat(params -> {
                        String expression = (String) params.get("expression");
                        return expression != null && expression.contains("1893456000000");
                    })
            );

            // Assert - executeScript was also called for the current page
            verify((JavascriptExecutor) mockChromiumDriver).executeScript(anyString(), eq(1893456000000L));
        }
    }

    @Test
    public void testReset_removesCdpScript() {
        // Arrange
        Map<String, Object> cdpResult = new HashMap<>();
        cdpResult.put("identifier", "script-456");
        when(mockChromiumDriver.executeCdpCommand(eq("Page.addScriptToEvaluateOnNewDocument"), anyMap()))
                .thenReturn(cdpResult);

        try (MockedStatic<DriverManager> driverManagerMock = Mockito.mockStatic(DriverManager.class)) {
            driverManagerMock.when(DriverManager::getDriver).thenReturn(mockChromiumDriver);

            TestClock clock = TestClock.create();
            clock.set("2030-01-01T00:00:00Z");

            // Act
            clock.reset();

            // Assert - CDP remove command was called
            verify(mockChromiumDriver).executeCdpCommand(
                    eq("Page.removeScriptToEvaluateOnNewDocument"),
                    argThat(params -> "script-456".equals(params.get("identifier")))
            );
        }
    }

    @Test
    public void testAutoReset_removesCdpScript() {
        // Arrange
        Map<String, Object> cdpResult = new HashMap<>();
        cdpResult.put("identifier", "script-789");
        when(mockChromiumDriver.executeCdpCommand(eq("Page.addScriptToEvaluateOnNewDocument"), anyMap()))
                .thenReturn(cdpResult);

        try (MockedStatic<DriverManager> driverManagerMock = Mockito.mockStatic(DriverManager.class)) {
            driverManagerMock.when(DriverManager::getDriver).thenReturn(mockChromiumDriver);

            TestClock clock = TestClock.create();
            clock.set("2030-01-01T00:00:00Z");

            // Act
            TestClock.autoReset();

            // Assert - CDP remove command was called
            verify(mockChromiumDriver).executeCdpCommand(
                    eq("Page.removeScriptToEvaluateOnNewDocument"),
                    argThat(params -> "script-789".equals(params.get("identifier")))
            );
        }
    }

    @Test
    public void testAdvance_updatesCdpScript() {
        // Arrange
        Map<String, Object> cdpResult1 = new HashMap<>();
        cdpResult1.put("identifier", "script-1");
        Map<String, Object> cdpResult2 = new HashMap<>();
        cdpResult2.put("identifier", "script-2");

        when(mockChromiumDriver.executeCdpCommand(eq("Page.addScriptToEvaluateOnNewDocument"), anyMap()))
                .thenReturn(cdpResult1)
                .thenReturn(cdpResult2);

        try (MockedStatic<DriverManager> driverManagerMock = Mockito.mockStatic(DriverManager.class)) {
            driverManagerMock.when(DriverManager::getDriver).thenReturn(mockChromiumDriver);

            TestClock clock = TestClock.create();
            clock.set("2030-01-01T00:00:00Z");

            // Act
            clock.advance(Duration.ofHours(1));

            // Assert - CDP command was called twice (once for set, once for advance)
            verify(mockChromiumDriver, times(2)).executeCdpCommand(
                    eq("Page.addScriptToEvaluateOnNewDocument"),
                    anyMap()
            );

            // Assert - the second call had the advanced time
            verify(mockChromiumDriver).executeCdpCommand(
                    eq("Page.addScriptToEvaluateOnNewDocument"),
                    argThat(params -> {
                        String expression = (String) params.get("expression");
                        return expression != null && expression.contains("1893459600000");
                    })
            );
        }
    }

    @Test
    public void testSet_handlesCdpFailureGracefully() {
        // Arrange - CDP throws an exception
        when(mockChromiumDriver.executeCdpCommand(eq("Page.addScriptToEvaluateOnNewDocument"), anyMap()))
                .thenThrow(new RuntimeException("CDP not available"));

        try (MockedStatic<DriverManager> driverManagerMock = Mockito.mockStatic(DriverManager.class)) {
            driverManagerMock.when(DriverManager::getDriver).thenReturn(mockChromiumDriver);

            // Act - should not throw
            TestClock clock = TestClock.create();
            clock.set("2030-01-01T00:00:00Z");

            // Assert - executeScript was still called (fallback path)
            verify((JavascriptExecutor) mockChromiumDriver).executeScript(anyString(), eq(1893456000000L));

            // Assert - mocked time is still tracked
            assertEquals(clock.getMockedTimeMs(), Long.valueOf(1893456000000L));
        }
    }

    @Test
    public void testGetMockedTimeMs_returnsNullWhenNotSet() {
        TestClock clock = TestClock.create();
        assertNull(clock.getMockedTimeMs());
    }

    @Test
    public void testGetMockedTimeMs_returnsTimeAfterSet() {
        Map<String, Object> cdpResult = new HashMap<>();
        cdpResult.put("identifier", "script-test");
        when(mockChromiumDriver.executeCdpCommand(anyString(), anyMap())).thenReturn(cdpResult);

        try (MockedStatic<DriverManager> driverManagerMock = Mockito.mockStatic(DriverManager.class)) {
            driverManagerMock.when(DriverManager::getDriver).thenReturn(mockChromiumDriver);

            TestClock clock = TestClock.create();
            clock.set("2030-01-01T00:00:00Z");

            assertEquals(clock.getMockedTimeMs(), Long.valueOf(1893456000000L));
        }
    }
}
