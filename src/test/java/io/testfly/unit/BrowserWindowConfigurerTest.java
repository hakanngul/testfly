package io.testfly.unit;

import io.testfly.config.ConfigurationLoader;
import io.testfly.config.TestFlyConfig;
import io.testfly.driver.BrowserWindowConfigurer;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Files;

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Unit tests for {@code browser.window}: YAML binding, validation, and how
 * {@link BrowserWindowConfigurer} drives the W3C window commands.
 */
@Test(singleThreaded = true)
public class BrowserWindowConfigurerTest {

    private File tmp;

    @AfterMethod
    public void cleanup() {
        System.clearProperty("testfly.config");
        if (tmp != null) { tmp.delete(); tmp = null; }
    }

    private TestFlyConfig loadYaml(String windowBlock) throws Exception {
        String yaml = String.join("\n",
                "browser:",
                "  name: safari",
                windowBlock,
                "execution:",
                "  mode: local",
                "  baseUrl: https://example.com",
                "timeouts:",
                "  explicit: 10",
                "  pageLoad: 30",
                "");
        synchronized (ConfigurationLoader.class) {
            tmp = File.createTempFile("testfly-window-", ".yml");
            Files.writeString(tmp.toPath(), yaml);
            System.setProperty("testfly.config", tmp.getAbsolutePath());
            try {
                return ConfigurationLoader.load();
            } finally {
                System.clearProperty("testfly.config");
            }
        }
    }

    private static TestFlyConfig.Browser browser(String mode, Integer width, Integer height, boolean headless) {
        TestFlyConfig.Window window = new TestFlyConfig.Window();
        window.setMode(mode);
        window.setWidth(width);
        window.setHeight(height);
        TestFlyConfig.Browser browser = new TestFlyConfig.Browser();
        browser.setWindow(window);
        browser.setHeadless(headless);
        return browser;
    }

    // ----------------------------------------------------------
    // YAML binding and load-time validation
    // ----------------------------------------------------------

    @Test
    public void load_windowAbsent_defaultsToDefaultMode() throws Exception {
        TestFlyConfig config = loadYaml("  headless: false");
        assertNotNull(config.getBrowser().getWindow());
        assertEquals(config.getBrowser().getWindow().getMode(), "default");
        assertNull(config.getBrowser().getWindow().getWidth());
    }

    @Test
    public void load_maximizedWithoutSize_binds() throws Exception {
        TestFlyConfig config = loadYaml("  window:\n    mode: maximized");
        assertEquals(config.getBrowser().getWindow().getMode(), "maximized");
    }

    @Test
    public void load_sizeWithDimensions_binds() throws Exception {
        TestFlyConfig config = loadYaml("  window:\n    mode: size\n    width: 1366\n    height: 768");
        assertEquals(config.getBrowser().getWindow().getWidth(), Integer.valueOf(1366));
        assertEquals(config.getBrowser().getWindow().getHeight(), Integer.valueOf(768));
    }

    @Test
    public void load_maximizedWithWidth_failsFast() {
        IllegalStateException e = expectThrows(IllegalStateException.class,
                () -> loadYaml("  window:\n    mode: maximized\n    width: 1920\n    height: 1080"));
        assertTrue(e.getMessage().contains("only used with mode: size"), e.getMessage());
    }

    @Test
    public void load_sizeWithoutHeight_failsFast() {
        IllegalStateException e = expectThrows(IllegalStateException.class,
                () -> loadYaml("  window:\n    mode: size\n    width: 1366"));
        assertTrue(e.getMessage().contains("requires both width and height"), e.getMessage());
    }

    @Test
    public void validate_nonPositiveSize_throws() {
        expectThrows(IllegalStateException.class,
                () -> BrowserWindowConfigurer.validate(browser("size", 0, 768, false).getWindow()));
    }

    @Test
    public void validate_unknownMode_throws() {
        IllegalStateException e = expectThrows(IllegalStateException.class,
                () -> BrowserWindowConfigurer.validate(browser("maximised", null, null, false).getWindow()));
        assertTrue(e.getMessage().contains("'maximised'"), e.getMessage());
    }

    @Test
    public void validate_modeIsCaseInsensitive() {
        BrowserWindowConfigurer.validate(browser("FullScreen", null, null, false).getWindow());
    }

    // ----------------------------------------------------------
    // Applying to a session
    // ----------------------------------------------------------

    @Test
    public void apply_maximized_headed_callsMaximize() {
        WebDriver driver = mock(WebDriver.class, RETURNS_DEEP_STUBS);
        BrowserWindowConfigurer.apply(driver, browser("maximized", null, null, false));
        verify(driver.manage().window()).maximize();
    }

    @Test
    public void apply_fullscreen_headed_callsFullscreen() {
        WebDriver driver = mock(WebDriver.class, RETURNS_DEEP_STUBS);
        BrowserWindowConfigurer.apply(driver, browser("fullscreen", null, null, false));
        verify(driver.manage().window()).fullscreen();
    }

    @Test
    public void apply_size_setsExactDimensions() {
        WebDriver driver = mock(WebDriver.class, RETURNS_DEEP_STUBS);
        BrowserWindowConfigurer.apply(driver, browser("size", 1366, 768, true));
        verify(driver.manage().window()).setSize(new Dimension(1366, 768));
    }

    @Test
    public void apply_maximized_headless_usesFallbackSize() {
        WebDriver driver = mock(WebDriver.class, RETURNS_DEEP_STUBS);
        BrowserWindowConfigurer.apply(driver, browser("maximized", null, null, true));
        verify(driver.manage().window()).setSize(new Dimension(1920, 1080));
        verify(driver.manage().window(), never()).maximize();
    }

    @Test
    public void apply_defaultMode_leavesWindowUntouched() {
        WebDriver driver = mock(WebDriver.class);
        BrowserWindowConfigurer.apply(driver, browser("default", null, null, false));
        verifyNoInteractions(driver);
    }

    @Test
    public void apply_browserRejectsCommand_doesNotFailSession() {
        WebDriver driver = mock(WebDriver.class, RETURNS_DEEP_STUBS);
        WebDriver.Window window = driver.manage().window();
        doThrow(new UnsupportedOperationException("no window manager")).when(window).maximize();
        BrowserWindowConfigurer.apply(driver, browser("maximized", null, null, false));
        // no exception expected
    }
}
