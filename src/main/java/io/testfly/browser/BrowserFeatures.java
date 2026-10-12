package io.testfly.browser;

import io.testfly.api.TestFlyApi;
import org.openqa.selenium.HasCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.ChromiumDriver;
import org.openqa.selenium.devtools.HasDevTools;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Logger;

/**
 * Tells which browser features need the Chrome DevTools Protocol (CDP), whether the current
 * driver provides it, and what TestFly does instead when it does not.
 *
 * <p>CDP is available when TestFly starts Chrome or Edge locally. It is not available on Firefox
 * or Safari, nor on a remote driver (Selenium Grid, BrowserStack, Sauce Labs), even if that remote
 * browser is Chrome. Features that need it degrade, and each logs one warning per browser session
 * naming what changed.
 */
@TestFlyApi(since = "1.1.0")
public final class BrowserFeatures {

    private static final Logger LOG = Logger.getLogger(BrowserFeatures.class.getName());

    /** A feature that depends on the Chrome DevTools Protocol. */
    public enum Feature {
        /** {@code NetworkMock} routes and stubs, {@code network.blockUrls} and {@code NetworkAssert}. */
        NETWORK_MOCK("Network interception requires a local Chrome/Edge session (Selenium Grid and cloud "
                + "sessions are not supported yet). Network mocking, URL blocking and network assertions are not "
                + "available; routes are ignored and requests reach the real network."),
        /** Video recording through the DevTools screencast. */
        CDP_RECORDING("Video recording uses periodic screenshots instead of the DevTools screencast, so "
                + "frames are less frequent."),
        /** {@code DeviceEmulator}. */
        DEVICE_EMULATION("Device emulation only resizes the window and overrides navigator.userAgent in the "
                + "current page; it is lost on navigation and does not emulate pixel ratio or touch."),
        /** {@code GeoLocation}. */
        GEOLOCATION("The location is overridden through JavaScript in the current page only and is lost on "
                + "navigation.");

        private final String fallback;

        Feature(String fallback) {
            this.fallback = fallback;
        }

        /** What happens on a driver without the DevTools Protocol. */
        public String fallback() {
            return fallback;
        }
    }

    private static final Map<WebDriver, Set<Feature>> WARNED = Collections.synchronizedMap(new WeakHashMap<>());

    private BrowserFeatures() {
    }

    /** Whether the driver provides what the feature needs, so that the feature works fully. */
    public static boolean supports(WebDriver driver, Feature feature) {
        return switch (feature) {
            case NETWORK_MOCK, DEVICE_EMULATION, GEOLOCATION -> driver instanceof ChromiumDriver;
            case CDP_RECORDING -> driver instanceof HasDevTools;
        };
    }

    /**
     * Logs, once per browser session and feature, that the feature is degraded on this driver and
     * what happens instead. Does nothing when the driver supports the feature.
     */
    public static void warnOnce(WebDriver driver, Feature feature) {
        if (driver != null && supports(driver, feature)) {
            return;
        }
        boolean first;
        synchronized (WARNED) {
            first = WARNED.computeIfAbsent(driver, d -> EnumSet.noneOf(Feature.class)).add(feature);
        }
        if (first) {
            LOG.warning("[TestFly] " + feature + " is degraded on " + describe(driver) + ": " + feature.fallback()
                    + " See the browser support page in the documentation.");
        }
    }

    private static String describe(WebDriver driver) {
        if (driver == null) {
            return "a missing driver";
        }
        if (driver instanceof HasCapabilities caps) {
            try {
                return caps.getCapabilities().getBrowserName() + " (" + driver.getClass().getSimpleName() + ")";
            } catch (RuntimeException ignored) {
                // fall through to the class name
            }
        }
        return driver.getClass().getSimpleName();
    }
}
