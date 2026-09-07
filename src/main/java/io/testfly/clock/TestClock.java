package io.testfly.clock;

import io.testfly.api.TestFlyApi;
import io.testfly.config.TestFlyConfig;
import io.testfly.driver.DriverManager;
import io.testfly.internal.TestFlyContext;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.ChromiumDriver;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controls the browser's perception of time for the current test.
 *
 * <p>
 * Calling {@link #set(String)} injects a {@code Date} override into the active
 * page so that
 * every {@code new Date()} and {@code Date.now()} call in client-side
 * JavaScript returns the
 * mocked time. This lets you test expiry banners, promotional countdowns,
 * trial-period logic,
 * and any other date-sensitive UI without touching the database or the system
 * clock.
 *
 * <p>
 * The clock is reset automatically at the end of each test — no manual cleanup
 * required.
 *
 * <pre>
 * // Test "trial expired" banner without touching the database:
 * clock().set("2030-06-01T00:00:00Z");
 * open("/dashboard");
 * assertThat(By.id("trial-banner")).hasText("Your trial expired 30 days ago");
 * </pre>
 *
 * <p>
 * Instances are obtained via {@code clock()} in
 * {@link io.testfly.test.BaseTest}.
 */
@TestFlyApi(since = "2.2.0")
public final class TestClock {

    private static final Logger LOG = Logger.getLogger(TestClock.class.getName());

    private static final ThreadLocal<Long> MOCK_TIME_MS = new ThreadLocal<>();

    /**
     * CDP script identifier returned by
     * {@code Page.addScriptToEvaluateOnNewDocument}, or null.
     */
    private static final ThreadLocal<String> CDP_SCRIPT_ID = new ThreadLocal<>();

    /**
     * JS that replaces the global {@code Date} with a mock that always returns
     * {@code arguments[0]} (epoch ms) when called with no arguments or via
     * {@code Date.now()}.
     * Stores the real {@code Date} in {@code window.__sbOriginalDate} so
     * {@link #reset()} can
     * restore it. Safe to call multiple times — {@code __sbOriginalDate} is never
     * overwritten.
     */
    private static final String INJECT_JS = "var mockTime = arguments[0];" +
            "window.__sbOriginalDate = window.__sbOriginalDate || Date;" +
            "Date = function MockDate() {" +
            "  if (this instanceof MockDate) {" +
            "    if (arguments.length === 0) { return new window.__sbOriginalDate(mockTime); }" +
            "    var a = [null].concat(Array.prototype.slice.call(arguments));" +
            "    return new (Function.prototype.bind.apply(window.__sbOriginalDate, a))();" +
            "  }" +
            "  return new MockDate();" +
            "};" +
            "Date.prototype = window.__sbOriginalDate.prototype;" +
            "Date.now = function() { return mockTime; };" +
            "Date.parse = window.__sbOriginalDate.parse;" +
            "Date.UTC = window.__sbOriginalDate.UTC;";

    private static final String RESET_JS = "if (window.__sbOriginalDate) { Date = window.__sbOriginalDate; delete window.__sbOriginalDate; }";

    private TestClock() {
    }

    /**
     * Creates a new {@code TestClock} bound to the current thread's WebDriver
     * session.
     */
    public static TestClock create() {
        return new TestClock();
    }

    /**
     * Overrides {@code Date} in the active browser page to the given instant.
     * Every subsequent {@code new Date()} and {@code Date.now()} call in client JS
     * will
     * return this time until {@link #reset()} is called (or the test ends).
     *
     * @param isoDateTime ISO 8601 UTC string, e.g. {@code "2030-01-01T00:00:00Z"}
     * @return {@code this} for chaining
     */
    public TestClock set(String isoDateTime) {
        long epochMs = Instant.parse(isoDateTime).toEpochMilli();
        MOCK_TIME_MS.set(epochMs);
        injectMock(epochMs);
        return this;
    }

    /**
     * Advances the mocked time by {@code duration} from the current mock time.
     * If no mock is active, advances from the real current time.
     *
     * @return {@code this} for chaining
     */
    public TestClock advance(Duration duration) {
        Long current = MOCK_TIME_MS.get();
        if (current == null)
            current = Instant.now().toEpochMilli();
        long newTime = current + duration.toMillis();
        MOCK_TIME_MS.set(newTime);
        injectMock(newTime);
        return this;
    }

    /**
     * Returns the currently mocked time in epoch milliseconds,
     * or {@code null} if no mock is active.
     */
    public Long getMockedTimeMs() {
        return MOCK_TIME_MS.get();
    }

    /**
     * Restores the real {@code Date} implementation in the browser.
     * Called automatically after each test — explicit calls are optional.
     */
    public void reset() {
        if (MOCK_TIME_MS.get() == null)
            return;
        MOCK_TIME_MS.remove();
        executeReset();
        removeCdpScript();
    }

    /**
     * Framework-internal: resets the clock if a mock was active. Called by
     * {@link io.testfly.listeners.TestExecutionListener} after every test.
     */
    public static void autoReset() {
        if (MOCK_TIME_MS.get() != null) {
            MOCK_TIME_MS.remove();
            executeReset();
            removeCdpScript();
        }
    }

    // ── internals ─────────────────────────────────────────────────────────

    private static void injectMock(long timeMs) {
        WebDriver driver = DriverManager.getDriver();
        if (driver == null) {
            throw new IllegalStateException(
                    "[TestClock] No active WebDriver. Call clock().set() after open().");
        }
        if (!(driver instanceof JavascriptExecutor)) {
            throw new UnsupportedOperationException(
                    "[TestClock] Browser does not support JavaScript execution.");
        }

        // Inject into current page
        ((JavascriptExecutor) driver).executeScript(INJECT_JS, timeMs);

        // For Chromium browsers, persist the mock across page navigations using CDP
        if (driver instanceof ChromiumDriver chromiumDriver) {
            persistMockViaCdp(chromiumDriver, timeMs);
        }

        injectHeaderIfNeeded(driver, timeMs);
    }

    private static void executeReset() {
        try {
            WebDriver driver = DriverManager.getDriver();
            if (driver instanceof JavascriptExecutor) {
                ((JavascriptExecutor) driver).executeScript(RESET_JS);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Uses CDP {@code Page.addScriptToEvaluateOnNewDocument} to persist the clock
     * mock
     * across page navigations for Chromium-based browsers. Removes any previously
     * registered
     * CDP script before adding a new one. Falls back silently if CDP is
     * unavailable.
     */
    private static void persistMockViaCdp(ChromiumDriver chromiumDriver, long timeMs) {
        try {
            // Remove any previously registered CDP script to avoid stacking mocks
            removeCdpScript(chromiumDriver);

            // Build a self-contained script with the time baked in (no arguments[0])
            String script = INJECT_JS.replace("arguments[0]", String.valueOf(timeMs));

            Map<String, Object> params = new HashMap<>();
            params.put("expression", script);

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) chromiumDriver.executeCdpCommand(
                    "Page.addScriptToEvaluateOnNewDocument", params);

            Object identifier = result.get("identifier");
            if (identifier != null) {
                CDP_SCRIPT_ID.set(identifier.toString());
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "[TestClock] Failed to register persistent clock mock via CDP; "
                            + "mock will not survive page navigations.",
                    e);
        }
    }

    /**
     * Removes the CDP script registered by {@link #persistMockViaCdp}, if any.
     * Uses the current thread's driver.
     */
    private static void removeCdpScript() {
        try {
            WebDriver driver = DriverManager.getDriver();
            if (driver instanceof ChromiumDriver chromiumDriver) {
                removeCdpScript(chromiumDriver);
            }
        } catch (Exception ignored) {
            // driver not available (e.g. already quit or never initialized)
        }
        CDP_SCRIPT_ID.remove();
    }

    /**
     * Removes the CDP script registered by {@link #persistMockViaCdp}, if any,
     * using the provided Chromium driver.
     */
    private static void removeCdpScript(ChromiumDriver chromiumDriver) {
        String scriptId = CDP_SCRIPT_ID.get();
        if (scriptId == null) {
            return;
        }
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("identifier", scriptId);
            chromiumDriver.executeCdpCommand(
                    "Page.removeScriptToEvaluateOnNewDocument", params);
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "[TestClock] Failed to remove persistent clock mock via CDP.", e);
        } finally {
            CDP_SCRIPT_ID.remove();
        }
    }

    /**
     * When clock-header injection is enabled in config, uses CDP
     * ({@code Network.setExtraHTTPHeaders}) to attach the mocked time as an
     * extra HTTP header on every outgoing browser request. Silently skips
     * non-Chromium browsers and any CDP failure so that tests are never
     * broken by header injection.
     */
    private static void injectHeaderIfNeeded(WebDriver driver, long timeMs) {
        try {
            if (!TestFlyContext.isInitialized()) {
                return;
            }
            TestFlyConfig.Clock clockConfig = TestFlyContext.getConfig().getClock();
            if (clockConfig == null || !clockConfig.isInjectHeader()) {
                return;
            }

            if (!(driver instanceof ChromiumDriver chromiumDriver)) {
                LOG.warning(
                        "[TestClock] Header injection requires a Chromium-based browser; skipping.");
                return;
            }

            String headerName = clockConfig.getHeaderName();
            if (headerName == null || headerName.isEmpty()) {
                headerName = "X-Mock-Date";
            }
            String isoValue = Instant.ofEpochMilli(timeMs).toString();

            // Enable the Network domain so header overrides take effect.
            chromiumDriver.executeCdpCommand("Network.enable", new HashMap<>());

            // Set the extra HTTP header for all subsequent requests.
            Map<String, Object> headers = new HashMap<>();
            headers.put(headerName, isoValue);
            Map<String, Object> params = new HashMap<>();
            params.put("headers", headers);
            chromiumDriver.executeCdpCommand("Network.setExtraHTTPHeaders", params);
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "[TestClock] Failed to inject mock-date header via CDP; continuing without it.",
                    e);
        }
    }
}
