package io.testfly.unit;

import io.testfly.browser.BrowserFeatures;
import io.testfly.browser.BrowserFeatures.Feature;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.ChromiumDriver;
import org.openqa.selenium.devtools.HasDevTools;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.mockito.Mockito.mock;
import static org.testng.Assert.*;

/** Unit tests for {@link BrowserFeatures}. No browser needed. */
@Test(singleThreaded = true)
public class BrowserFeaturesTest {

    private final List<String> warnings = new ArrayList<>();
    private Handler handler;
    private Logger logger;

    @BeforeMethod
    public void captureLog() {
        warnings.clear();
        logger = Logger.getLogger(BrowserFeatures.class.getName());
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
    public void releaseLog() {
        logger.removeHandler(handler);
    }

    @Test
    public void chromium_supportsEveryFeature() {
        WebDriver chrome = mock(ChromiumDriver.class);
        for (Feature f : Feature.values()) {
            assertTrue(BrowserFeatures.supports(chrome, f), f + " on Chromium");
        }
    }

    @Test
    public void firefoxAndSafariAndRemote_supportNone() {
        WebDriver firefox = mock(FirefoxDriver.class);
        WebDriver plain = mock(WebDriver.class); // stands in for RemoteWebDriver or SafariDriver
        for (Feature f : Feature.values()) {
            assertFalse(BrowserFeatures.supports(firefox, f), f + " on Firefox");
            assertFalse(BrowserFeatures.supports(plain, f), f + " on a plain driver");
        }
    }

    @Test
    public void recording_needsDevToolsNotChromiumSpecifically() {
        WebDriver withDevTools = mock(WebDriver.class, org.mockito.Mockito.withSettings().extraInterfaces(HasDevTools.class));
        assertTrue(BrowserFeatures.supports(withDevTools, Feature.CDP_RECORDING));
        assertFalse(BrowserFeatures.supports(withDevTools, Feature.NETWORK_MOCK));
    }

    @Test
    public void warnOnce_logsOncePerDriverAndFeature_andNamesTheFallback() {
        WebDriver firefox = mock(FirefoxDriver.class);

        BrowserFeatures.warnOnce(firefox, Feature.NETWORK_MOCK);
        BrowserFeatures.warnOnce(firefox, Feature.NETWORK_MOCK);
        assertEquals(warnings.size(), 1, "second call must stay quiet: " + warnings);
        assertTrue(warnings.get(0).contains("NETWORK_MOCK"), warnings.get(0));
        assertTrue(warnings.get(0).contains("routes are ignored"), warnings.get(0));

        BrowserFeatures.warnOnce(firefox, Feature.GEOLOCATION);
        assertEquals(warnings.size(), 2, "another feature on the same driver warns separately");

        BrowserFeatures.warnOnce(mock(FirefoxDriver.class), Feature.NETWORK_MOCK);
        assertEquals(warnings.size(), 3, "a new browser session warns again");
    }

    @Test
    public void warnOnce_isSilent_whenTheDriverSupportsTheFeature() {
        BrowserFeatures.warnOnce(mock(ChromiumDriver.class), Feature.NETWORK_MOCK);
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    public void warnOnce_handlesAMissingDriver() {
        BrowserFeatures.warnOnce(null, Feature.DEVICE_EMULATION);
        assertEquals(warnings.size(), 1);
        assertTrue(warnings.get(0).contains("missing driver"), warnings.get(0));
    }

    @Test
    public void everyFeature_describesItsFallback() {
        for (Feature f : Feature.values()) {
            assertFalse(f.fallback().isBlank(), f.name());
        }
    }
}
