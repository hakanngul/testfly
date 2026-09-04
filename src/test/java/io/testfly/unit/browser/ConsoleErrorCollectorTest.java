package io.testfly.unit.browser;

import io.testfly.browser.ConsoleErrorCollector;
import io.testfly.config.TestFlyConfig;
import io.testfly.driver.DriverManager;
import io.testfly.internal.TestFlyContext;
import org.mockito.MockedStatic;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.Logs;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link ConsoleErrorCollector}.
 * Uses mocked WebDriver + JavascriptExecutor — no real browser needed.
 */
@Test(singleThreaded = true)
public class ConsoleErrorCollectorTest {

    /** Combined interface for mocking a WebDriver that also executes JS. */
    interface JsCapableDriver extends WebDriver, JavascriptExecutor {
    }

    private JsCapableDriver mockDriver;
    private WebDriver.Options mockOptions;
    private Logs mockLogs;
    private MockedStatic<DriverManager> driverManagerMock;
    private MockedStatic<TestFlyContext> contextMock;

    @BeforeMethod
    public void setUp() {
        mockDriver = mock(JsCapableDriver.class);
        mockOptions = mock(WebDriver.Options.class);
        mockLogs = mock(Logs.class);

        lenient().when(mockDriver.manage()).thenReturn(mockOptions);
        lenient().when(mockOptions.logs()).thenReturn(mockLogs);

        driverManagerMock = mockStatic(DriverManager.class);
        driverManagerMock.when(DriverManager::getDriver).thenReturn(mockDriver);

        TestFlyConfig.Browser browserConfig = new TestFlyConfig.Browser();
        TestFlyConfig config = new TestFlyConfig();
        config.setBrowser(browserConfig);

        contextMock = mockStatic(TestFlyContext.class);
        contextMock.when(TestFlyContext::getConfig).thenReturn(config);
    }

    @AfterMethod
    public void tearDown() {
        if (contextMock != null)
            contextMock.close();
        if (driverManagerMock != null)
            driverManagerMock.close();
    }

    // ---------------------------------------------------------------
    // Strategy 1: WebDriver browser logs
    // ---------------------------------------------------------------

    @Test
    public void collect_capturesSevereBrowserErrors() {
        LogEntry severeEntry = new LogEntry(Level.SEVERE, System.currentTimeMillis(),
                "Uncaught TypeError: x is not a function");
        LogEntries entries = new LogEntries(Arrays.asList(severeEntry));
        when(mockLogs.get(LogType.BROWSER)).thenReturn(entries);

        List<String> errors = ConsoleErrorCollector.collect();

        assertEquals(errors.size(), 1);
        assertTrue(errors.get(0).contains("TypeError"));
    }

    @Test
    public void collect_filtersOutNonSevereEntries() {
        LogEntry severe = new LogEntry(Level.SEVERE, System.currentTimeMillis(), "SEVERE error");
        LogEntry warning = new LogEntry(Level.WARNING, System.currentTimeMillis(), "just a warning");
        LogEntry info = new LogEntry(Level.INFO, System.currentTimeMillis(), "info message");
        LogEntries entries = new LogEntries(Arrays.asList(severe, warning, info));
        when(mockLogs.get(LogType.BROWSER)).thenReturn(entries);

        List<String> errors = ConsoleErrorCollector.collect();

        assertEquals(errors.size(), 1, "Only SEVERE entries should be collected");
        assertTrue(errors.get(0).contains("SEVERE error"));
    }

    @Test
    public void collect_multipleSevereErrors_allCaptured() {
        LogEntry err1 = new LogEntry(Level.SEVERE, System.currentTimeMillis(), "Error 1");
        LogEntry err2 = new LogEntry(Level.SEVERE, System.currentTimeMillis(), "Error 2");
        LogEntries entries = new LogEntries(Arrays.asList(err1, err2));
        when(mockLogs.get(LogType.BROWSER)).thenReturn(entries);

        List<String> errors = ConsoleErrorCollector.collect();

        assertEquals(errors.size(), 2);
    }

    // ---------------------------------------------------------------
    // Empty log
    // ---------------------------------------------------------------

    @Test
    public void collect_emptyLog_returnsEmptyList() {
        LogEntries entries = new LogEntries(Collections.emptyList());
        when(mockLogs.get(LogType.BROWSER)).thenReturn(entries);
        when(((JavascriptExecutor) mockDriver).executeScript(anyString()))
                .thenReturn(Collections.emptyList());

        List<String> errors = ConsoleErrorCollector.collect();

        assertTrue(errors.isEmpty());
    }

    // ---------------------------------------------------------------
    // Strategy 2: JS shim fallback (Firefox)
    // ---------------------------------------------------------------

    @Test
    public void collect_fallsBackToJsShim_whenLogsUnsupported() {
        // Strategy 1 fails (e.g. Firefox doesn't support browser logs)
        when(mockLogs.get(LogType.BROWSER))
                .thenThrow(new UnsupportedOperationException("not supported"));

        // Strategy 2 returns errors via JS shim
        List<String> jsErrors = Arrays.asList("JS error 1", "JS error 2");
        when(((JavascriptExecutor) mockDriver).executeScript(anyString()))
                .thenReturn(jsErrors);

        List<String> errors = ConsoleErrorCollector.collect();

        assertEquals(errors.size(), 2);
        assertTrue(errors.contains("JS error 1"));
        assertTrue(errors.contains("JS error 2"));
    }

    // ---------------------------------------------------------------
    // Shim injection & clearing
    // ---------------------------------------------------------------

    @Test
    public void injectShim_executesScript() {
        ConsoleErrorCollector.injectShim();

        verify((JavascriptExecutor) mockDriver).executeScript(anyString());
    }

    @Test
    public void clear_executesResetScript() {
        ConsoleErrorCollector.clear();

        verify((JavascriptExecutor) mockDriver).executeScript(anyString());
    }

    @Test
    public void injectShim_toleratesJsException() {
        when(((JavascriptExecutor) mockDriver).executeScript(anyString()))
                .thenThrow(new RuntimeException("JS execution failed"));

        // Should not throw — exceptions are silently ignored
        ConsoleErrorCollector.injectShim();
    }

    @Test
    public void clear_toleratesJsException() {
        when(((JavascriptExecutor) mockDriver).executeScript(anyString()))
                .thenThrow(new RuntimeException("JS execution failed"));

        // Should not throw
        ConsoleErrorCollector.clear();
    }

    // ---------------------------------------------------------------
    // getErrors alias
    // ---------------------------------------------------------------

    @Test
    public void getErrors_delegatesToCollect() {
        LogEntry severe = new LogEntry(Level.SEVERE, System.currentTimeMillis(), "error via getErrors");
        LogEntries entries = new LogEntries(Arrays.asList(severe));
        when(mockLogs.get(LogType.BROWSER)).thenReturn(entries);

        List<String> errors = ConsoleErrorCollector.getErrors();

        assertEquals(errors.size(), 1);
        assertTrue(errors.get(0).contains("error via getErrors"));
    }

    // ---------------------------------------------------------------
    // isEnabled
    // ---------------------------------------------------------------

    @Test
    public void isEnabled_true_whenConfigEnabled() {
        TestFlyConfig.Browser browserConfig = new TestFlyConfig.Browser();
        browserConfig.setCaptureConsoleErrors(true);
        TestFlyConfig config = new TestFlyConfig();
        config.setBrowser(browserConfig);

        // Re-stub the existing mock with the new config
        contextMock.when(TestFlyContext::getConfig).thenReturn(config);
        assertTrue(ConsoleErrorCollector.isEnabled());
    }

    @Test
    public void isEnabled_false_whenConfigDisabled() {
        TestFlyConfig.Browser browserConfig = new TestFlyConfig.Browser();
        browserConfig.setCaptureConsoleErrors(false);
        TestFlyConfig config = new TestFlyConfig();
        config.setBrowser(browserConfig);

        contextMock.when(TestFlyContext::getConfig).thenReturn(config);
        assertFalse(ConsoleErrorCollector.isEnabled());
    }

    @Test
    public void isEnabled_false_whenContextThrows() {
        contextMock.when(TestFlyContext::getConfig)
                .thenThrow(new RuntimeException("not initialized"));
        assertFalse(ConsoleErrorCollector.isEnabled(),
                "isEnabled should return false when context is unavailable");
    }

    // ---------------------------------------------------------------
    // collect returns unmodifiable list
    // ---------------------------------------------------------------

    @Test
    public void collect_returnsUnmodifiableList() {
        LogEntries entries = new LogEntries(Collections.emptyList());
        when(mockLogs.get(LogType.BROWSER)).thenReturn(entries);
        when(((JavascriptExecutor) mockDriver).executeScript(anyString()))
                .thenReturn(Collections.emptyList());

        List<String> errors = ConsoleErrorCollector.collect();

        try {
            errors.add("should not be allowed");
            assertTrue(false, "collect() should return an unmodifiable list");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }
}
