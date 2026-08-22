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
 * <p>Calling {@link #set(String)} injects a {@code Date} override into the active page so that
 * every {@code new Date()} and {@code Date.now()} call in client-side JavaScript returns the
 * mocked time. This lets you test expiry banners, promotional countdowns, trial-period logic,
 * and any other date-sensitive UI without touching the database or the system clock.
 *
 * <p>The clock is reset automatically at the end of each test — no manual cleanup required.
 *
 * <pre>
 * // Test "trial expired" banner without touching the database:
 * clock().set("2030-06-01T00:00:00Z");
 * open("/dashboard");
 * assertThat(By.id("trial-banner")).hasText("Your trial expired 30 days ago");
 * </pre>
 *
 * <p>Instances are obtained via {@code clock()} in {@link io.testfly.test.BaseTest}.
 */
@TestFlyApi(since = "2.2.0")
public final class TestClock {

    private static final Logger LOG = Logger.getLogger(TestClock.class.getName());

    private static final ThreadLocal<Long> MOCK_TIME_MS = new ThreadLocal<>();

    /**
     * JS that replaces the global {@code Date} with a mock that always returns
     * {@code arguments[0]} (epoch ms) when called with no arguments or via {@code Date.now()}.
     * Stores the real {@code Date} in {@code window.__sbOriginalDate} so {@link #reset()} can
     * restore it. Safe to call multiple times — {@code __sbOriginalDate} is never overwritten.
     */
    private static final String INJECT_JS =
        "var mockTime = arguments[0];" +
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

    private static final String RESET_JS =
        "if (window.__sbOriginalDate) { Date = window.__sbOriginalDate; delete window.__sbOriginalDate; }";

    private TestClock() {}

    /** Creates a new {@code TestClock} bound to the current thread's WebDriver session. */
    public static TestClock create() {
        return new TestClock();
    }

    /**
     * Overrides {@code Date} in the active browser page to the given instant.
     * Every subsequent {@code new Date()} and {@code Date.now()} call in client JS will
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
        if (current == null) current = Instant.now().toEpochMilli();
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
        if (MOCK_TIME_MS.get() == null) return;
        MOCK_TIME_MS.remove();
        executeReset();
    }

    /**
     * Framework-internal: resets the clock if a mock was active. Called by
     * {@link io.testfly.listeners.TestExecutionListener} after every test.
     */
    public static void autoReset() {
        if (MOCK_TIME_MS.get() != null) {
            MOCK_TIME_MS.remove();
            executeReset();
        }
    }

    // ── internals ─────────────────────────────────────────────────────────

    private static void injectMock(long timeMs) {
        WebDriver driver = DriverManager.getDriver();
        if (driver == null) {
            throw new IllegalStateException(
                "[TestClock] No active WebDriver. Call clock().set() after open()."
            );
        }
        if (!(driver instanceof JavascriptExecutor)) {
            throw new UnsupportedOperationException(
                "[TestClock] Browser does not support JavaScript execution."
            );
        }
        ((JavascriptExecutor) driver).executeScript(INJECT_JS, timeMs);
        injectHeaderIfNeeded(driver, timeMs);
    }

    private static void executeReset() {
        try {
            WebDriver driver = DriverManager.getDriver();
            if (driver instanceof JavascriptExecutor) {
                ((JavascriptExecutor) driver).executeScript(RESET_JS);
            }
        } catch (Exception ignored) {}
    }

    /**
     * When clock-header injection is enabled in config, uses CDP
     * ({@code Network.setExtraHTTPHeaders}) to attach the mocked time as an
     * extra HTTP header on every outgoing browser request.  Silently skips
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

            if (!(driver instanceof ChromiumDriver)) {
                LOG.warning(
                    "[TestClock] Header injection requires a Chromium-based browser; skipping."
                );
                return;
            }

            String headerName = clockConfig.getHeaderName();
            if (headerName == null || headerName.isEmpty()) {
                headerName = "X-Mock-Date";
            }
            String isoValue = Instant.ofEpochMilli(timeMs).toString();

            ChromiumDriver chromiumDriver = (ChromiumDriver) driver;

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
