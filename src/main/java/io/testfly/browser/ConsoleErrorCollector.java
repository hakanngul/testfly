package io.testfly.browser;

import io.testfly.api.TestFlyApi;
import io.testfly.driver.DriverManager;
import io.testfly.internal.TestFlyContext;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;

import org.openqa.selenium.bidi.HasBiDi;
import org.openqa.selenium.bidi.log.LogLevel;
import org.openqa.selenium.bidi.module.LogInspector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Captures browser JavaScript console errors during test execution.
 *
 * <p>
 * Enable in {@code testfly.yml}:
 * 
 * <pre>
 * browser:
 *   captureConsoleErrors: true      # collect JS errors per test
 *   failOnConsoleErrors: false      # set true to fail test on any JS error
 * </pre>
 *
 * <p>
 * Usage — manual capture in a test:
 * 
 * <pre>
 * ConsoleErrorCollector.clear();
 * click(By.id("submit"));
 * List&lt;String&gt; errors = ConsoleErrorCollector.getErrors();
 * softAssert().that(errors.isEmpty(), "Unexpected JS errors: " + errors);
 * </pre>
 *
 * <p>
 * When {@code captureConsoleErrors: true}, errors are automatically appended
 * to the test's step timeline in the HTML report.
 *
 * <p>
 * <b>Note:</b> Browser log capture requires Chrome with logging preferences
 * configured.
 * Firefox does not expose browser logs via WebDriver.
 */
@TestFlyApi(since = "0.8.0")
public final class ConsoleErrorCollector {

    private static final Logger LOG = Logger.getLogger(ConsoleErrorCollector.class.getName());

    /** Errors seen through WebDriver BiDi, per browser session. See {@link #beginTest(WebDriver)}. */
    private static final Map<WebDriver, BiDiCapture> BIDI = Collections.synchronizedMap(new WeakHashMap<>());

    /** Sessions for which BiDi was asked for but could not be used, so the warning is logged once. */
    private static final Map<WebDriver, Boolean> BIDI_UNAVAILABLE = Collections.synchronizedMap(new WeakHashMap<>());

    private ConsoleErrorCollector() {
    }

    /** Listens to the browser's BiDi log events and keeps the errors. */
    private static final class BiDiCapture {
        final List<String> errors = new CopyOnWriteArrayList<>();
        // Held so the connection's listeners stay registered for the life of the session.
        final LogInspector inspector;

        BiDiCapture(WebDriver driver) {
            this.inspector = new LogInspector(driver);
            inspector.onConsoleEntry(e -> {
                if (e.getLevel() == LogLevel.ERROR) {
                    errors.add(e.getText());
                }
            });
            inspector.onJavaScriptException(e -> errors.add(e.getText()));
        }
    }

    /**
     * Framework-internal: called whenever a test gets its browser. With {@code browser.bidi} and
     * {@code browser.captureConsoleErrors} on, it starts listening to the browser's BiDi log events
     * (once per browser session) and forgets errors from earlier tests, so errors are seen from the
     * very start of a page load. Without BiDi support it logs one warning and the existing capture
     * is used. Never throws.
     */
    public static void beginTest(WebDriver driver) {
        try {
            if (driver == null || !isEnabled() || !TestFlyContext.getConfig().getBrowser().isBidi()) {
                return;
            }
            BiDiCapture capture = BIDI.get(driver);
            if (capture == null && !BIDI_UNAVAILABLE.containsKey(driver)) {
                if (driver instanceof HasBiDi bidi && bidi.maybeGetBiDi().isPresent()) {
                    capture = new BiDiCapture(driver);
                    BIDI.put(driver, capture);
                } else {
                    BIDI_UNAVAILABLE.put(driver, Boolean.TRUE);
                    LOG.warning("[TestFly] browser.bidi is on, but " + driver.getClass().getSimpleName()
                            + " has no WebDriver BiDi connection (Safari and remote drivers do not provide one). "
                            + "Console errors are captured the usual way, so errors raised while a page loads "
                            + "may be missed on Firefox and Safari.");
                }
            }
            if (capture != null) {
                capture.errors.clear();
            }
        } catch (RuntimeException e) {
            LOG.warning("[TestFly] Could not start BiDi console capture: " + e.getMessage());
        }
    }

    /**
     * Whether console errors for the current browser session are captured through WebDriver BiDi.
     */
    @TestFlyApi(since = "1.1.0")
    public static boolean isBiDiActive() {
        try {
            return BIDI.containsKey(DriverManager.getDriver());
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Captures current browser console errors for the calling thread's active test.
     * Reads from WebDriver browser logs (Chrome) or via injected JS shim (Firefox).
     *
     * @return list of error messages captured, empty if none
     */
    @SuppressWarnings("null")
    public static List<String> collect() {
        WebDriver driver = DriverManager.getDriver();
        List<String> errors = new ArrayList<>();

        // Strategy 0: WebDriver BiDi log events, when browser.bidi is on and the browser provides them
        BiDiCapture bidi = BIDI.get(driver);
        if (bidi != null) {
            return Collections.unmodifiableList(new ArrayList<>(bidi.errors));
        }

        // Strategy 1: WebDriver browser logs (Chrome)
        try {
            driver.manage().logs().get(LogType.BROWSER).getAll().stream()
                    .filter(e -> e.getLevel().intValue() >= Level.SEVERE.intValue())
                    .map(LogEntry::getMessage)
                    .forEach(errors::add);
        } catch (Exception ignored) {
            // Firefox and some drivers don't support browser logs
        }

        // Strategy 2: JS console shim (works on Firefox if pre-installed)
        if (errors.isEmpty()) {
            try {
                Object result = ((JavascriptExecutor) driver)
                        .executeScript("return window.__testFlyErrors || []");
                if (result instanceof List) {
                    ((List<?>) result).forEach(e -> errors.add(String.valueOf(e)));
                }
            } catch (Exception ignored) {
            }
        }

        return Collections.unmodifiableList(errors);
    }

    /**
     * Injects a JS console error shim into the current page.
     * Call this after page load if using Firefox or browsers without WebDriver log
     * support.
     *
     * <pre>
     * open("/dashboard");
     * ConsoleErrorCollector.injectShim();
     * // ... interact with page ...
     * List&lt;String&gt; errors = ConsoleErrorCollector.collect();
     * </pre>
     */
    public static void injectShim() {
        try {
            ((JavascriptExecutor) DriverManager.getDriver()).executeScript(
                    "if (!window.__testFlyErrors) {" +
                            "  window.__testFlyErrors = [];" +
                            "  var _orig = console.error;" +
                            "  console.error = function() {" +
                            "    window.__testFlyErrors.push(Array.from(arguments).join(' '));" +
                            "    _orig.apply(console, arguments);" +
                            "  };" +
                            "}");
        } catch (Exception ignored) {
        }
    }

    /**
     * Clears the JS shim error buffer on the current page.
     * Call between interactions to isolate which action produced errors.
     */
    public static void clear() {
        try {
            BiDiCapture bidi = BIDI.get(DriverManager.getDriver());
            if (bidi != null) {
                bidi.errors.clear();
            }
        } catch (RuntimeException ignored) {
        }
        try {
            ((JavascriptExecutor) DriverManager.getDriver())
                    .executeScript("if (window.__testFlyErrors) window.__testFlyErrors = [];");
        } catch (Exception ignored) {
        }
    }

    /**
     * Returns all errors collected so far via the JS shim.
     * Shorthand for {@link #collect()} when using the shim-based approach.
     */
    public static List<String> getErrors() {
        return collect();
    }

    /**
     * Returns {@code true} if console error capture is enabled in the config.
     */
    public static boolean isEnabled() {
        try {
            return TestFlyContext.getConfig().getBrowser().isCaptureConsoleErrors();
        } catch (Exception e) {
            return false;
        }
    }
}
