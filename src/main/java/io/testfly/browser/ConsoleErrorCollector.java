package io.testfly.browser;

import io.testfly.api.TestFlyApi;
import io.testfly.driver.DriverManager;
import io.testfly.internal.TestFlyContext;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Captures browser JavaScript console errors during test execution.
 *
 * <p>Enable in {@code testfly.yml}:
 * <pre>
 * browser:
 *   captureConsoleErrors: true      # collect JS errors per test
 *   failOnConsoleErrors: false      # set true to fail test on any JS error
 * </pre>
 *
 * <p>Usage — manual capture in a test:
 * <pre>
 * ConsoleErrorCollector.clear();
 * click(By.id("submit"));
 * List&lt;String&gt; errors = ConsoleErrorCollector.getErrors();
 * Assert.assertTrue(errors.isEmpty(), "Unexpected JS errors: " + errors);
 * </pre>
 *
 * <p>When {@code captureConsoleErrors: true}, errors are automatically appended
 * to the test's step timeline in the HTML report.
 *
 * <p><b>Note:</b> Browser log capture requires Chrome with logging preferences configured.
 * Firefox does not expose browser logs via WebDriver.
 */
@TestFlyApi(since = "0.8.0")
public final class ConsoleErrorCollector {

    private static final ConcurrentHashMap<String, List<String>> errorsByTest = new ConcurrentHashMap<>();

    private ConsoleErrorCollector() {}

    /**
     * Captures current browser console errors for the calling thread's active test.
     * Reads from WebDriver browser logs (Chrome) or via injected JS shim (Firefox).
     *
     * @return list of error messages captured, empty if none
     */
    public static List<String> collect() {
        WebDriver driver = DriverManager.getDriver();
        List<String> errors = new ArrayList<>();

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
            } catch (Exception ignored) {}
        }

        return Collections.unmodifiableList(errors);
    }

    /**
     * Injects a JS console error shim into the current page.
     * Call this after page load if using Firefox or browsers without WebDriver log support.
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
                "}"
            );
        } catch (Exception ignored) {}
    }

    /**
     * Clears the JS shim error buffer on the current page.
     * Call between interactions to isolate which action produced errors.
     */
    public static void clear() {
        try {
            ((JavascriptExecutor) DriverManager.getDriver())
                .executeScript("if (window.__testFlyErrors) window.__testFlyErrors = [];");
        } catch (Exception ignored) {}
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
