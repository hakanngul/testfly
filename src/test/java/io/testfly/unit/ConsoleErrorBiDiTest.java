package io.testfly.unit;

import io.testfly.browser.ConsoleErrorCollector;
import io.testfly.config.ConfigurationLoader;
import io.testfly.config.TestFlyConfig;
import io.testfly.driver.DriverManager;
import io.testfly.driver.LocalChromeDriverProvider;
import io.testfly.internal.TestFlyContext;
import org.mockito.MockedStatic;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.testng.Assert.*;

/** {@code browser.bidi}: configuration, the capability it requests, and the safe fallback without BiDi. */
@Test(singleThreaded = true)
public class ConsoleErrorBiDiTest {

    private final List<String> warnings = new ArrayList<>();
    private Handler handler;
    private Logger logger;
    private File tmp;

    @BeforeMethod
    public void captureLog() {
        warnings.clear();
        logger = Logger.getLogger(ConsoleErrorCollector.class.getName());
        handler = new Handler() {
            @Override
            public void publish(LogRecord r) {
                if (r.getLevel().intValue() >= Level.WARNING.intValue()) {
                    warnings.add(r.getMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(handler);
    }

    @AfterMethod
    public void cleanup() {
        logger.removeHandler(handler);
        System.clearProperty("testfly.config");
        if (tmp != null) {
            tmp.delete();
            tmp = null;
        }
    }

    private static TestFlyConfig configWith(boolean capture, boolean bidi) {
        TestFlyConfig config = new TestFlyConfig();
        TestFlyConfig.Browser browser = new TestFlyConfig.Browser();
        browser.setName("chrome");
        browser.setCaptureConsoleErrors(capture);
        browser.setBidi(bidi);
        config.setBrowser(browser);
        TestFlyConfig.Execution execution = new TestFlyConfig.Execution();
        execution.setMode("local");
        config.setExecution(execution);
        TestFlyConfig.Timeouts timeouts = new TestFlyConfig.Timeouts();
        timeouts.setExplicit(1);
        timeouts.setPageLoad(1);
        config.setTimeouts(timeouts);
        return config;
    }

    // ---------------------------------------------------------------- config and capability

    @Test
    public void bidi_defaultsToFalse() {
        assertFalse(new TestFlyConfig.Browser().isBidi());
    }

    @Test
    public void bidi_bindsFromYaml() throws Exception {
        tmp = File.createTempFile("testfly-bidi-", ".yml");
        Files.writeString(tmp.toPath(), String.join("\n",
                "browser:", "  name: chrome", "  bidi: true",
                "execution:", "  mode: local", "  baseUrl: https://example.com",
                "timeouts:", "  explicit: 10", "  pageLoad: 30", ""));
        TestFlyConfig config;
        synchronized (ConfigurationLoader.class) {
            System.setProperty("testfly.config", tmp.getAbsolutePath());
            config = ConfigurationLoader.load();
        }
        assertTrue(config.getBrowser().isBidi());
    }

    @Test
    public void chromeOptions_requestAWebSocketUrl_onlyWhenBidiIsOn() {
        TestFlyConfig on = configWith(true, true);
        TestFlyConfig off = configWith(true, false);
        try (MockedStatic<TestFlyContext> ctx = mockStatic(TestFlyContext.class)) {
            ctx.when(TestFlyContext::getConfig).thenReturn(on);
            ChromeOptions withBidi = LocalChromeDriverProvider.buildOptions(on);
            assertEquals(withBidi.getCapability("webSocketUrl"), Boolean.TRUE);

            ctx.when(TestFlyContext::getConfig).thenReturn(off);
            ChromeOptions without = LocalChromeDriverProvider.buildOptions(off);
            assertNull(without.getCapability("webSocketUrl"), "BiDi must stay off by default");
        }
    }

    // ---------------------------------------------------------------- fallback without BiDi

    @Test
    public void beginTest_isANoOp_whenBidiIsOff() {
        TestFlyConfig config = configWith(true, false);
        try (MockedStatic<TestFlyContext> ctx = mockStatic(TestFlyContext.class)) {
            ctx.when(TestFlyContext::getConfig).thenReturn(config);
            ConsoleErrorCollector.beginTest(mock(WebDriver.class));
        }
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    public void beginTest_isANoOp_whenConsoleCaptureIsOff() {
        TestFlyConfig config = configWith(false, true);
        try (MockedStatic<TestFlyContext> ctx = mockStatic(TestFlyContext.class)) {
            ctx.when(TestFlyContext::getConfig).thenReturn(config);
            ConsoleErrorCollector.beginTest(mock(WebDriver.class));
        }
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    public void beginTest_withoutABidiConnection_warnsOncePerSession_andNeverThrows() {
        TestFlyConfig config = configWith(true, true);
        WebDriver noBidi = mock(WebDriver.class); // stands in for Safari or a RemoteWebDriver
        try (MockedStatic<TestFlyContext> ctx = mockStatic(TestFlyContext.class);
                MockedStatic<DriverManager> dm = mockStatic(DriverManager.class)) {
            ctx.when(TestFlyContext::getConfig).thenReturn(config);
            dm.when(DriverManager::getDriver).thenReturn(noBidi);

            ConsoleErrorCollector.beginTest(noBidi);
            ConsoleErrorCollector.beginTest(noBidi);

            assertFalse(ConsoleErrorCollector.isBiDiActive());
        }
        assertEquals(warnings.size(), 1, warnings.toString());
        assertTrue(warnings.get(0).contains("browser.bidi"), warnings.get(0));
        assertTrue(warnings.get(0).contains("no WebDriver BiDi connection"), warnings.get(0));
    }

    @Test
    public void beginTest_withANullDriver_isIgnored() {
        TestFlyConfig config = configWith(true, true);
        try (MockedStatic<TestFlyContext> ctx = mockStatic(TestFlyContext.class)) {
            ctx.when(TestFlyContext::getConfig).thenReturn(config);
            ConsoleErrorCollector.beginTest(null);
        }
        assertTrue(warnings.isEmpty());
    }
}
