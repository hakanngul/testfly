package io.testfly.browser;

import io.testfly.api.TestFlyApi;
import io.testfly.driver.DriverManager;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global session cache for authenticated session reuse across tests.
 *
 * <p>Stores cookies and localStorage under a named key so that expensive login
 * flows only run once per suite, regardless of how many tests need that session.
 *
 * <p>Unlike {@code @PreCondition} (which is thread-local and per-condition),
 * {@code BrowserSessionCache} is shared across all threads — any thread can store a session
 * and any other thread can restore it into its own driver.
 *
 * <p>Usage:
 * <pre>
 * // In a @BeforeSuite or the first test that needs login:
 * new LoginPage(getDriver()).login("admin", "secret");
 * BrowserSessionCache.store("adminSession");
 *
 * // In every other test that needs the authenticated state:
 * open("/");
 * BrowserSessionCache.restore("adminSession");
 * // Driver is now authenticated — no login page needed
 * </pre>
 *
 * <p>Cookies are domain-scoped by the browser. Call {@code open("/")} (or navigate
 * to the base URL) before {@code restore()} so the driver is on the correct domain
 * when cookies are applied.
 */
@TestFlyApi(since = "1.0.0")
public final class BrowserSessionCache {

    private static final ConcurrentHashMap<String, SavedSession> CACHE = new ConcurrentHashMap<>();

    private BrowserSessionCache() {}

    // ----------------------------------------------------------
    // Public API
    // ----------------------------------------------------------

    /**
     * Captures the current driver's cookies and localStorage and stores them under {@code name}.
     * Overwrites any previously stored session with the same name.
     *
     * @param name logical session name, e.g. {@code "adminSession"}
     */
    @TestFlyApi(since = "1.0.0")
    public static void store(String name) {
        WebDriver driver = DriverManager.getDriver();
        Set<Cookie> cookies = driver.manage().getCookies();
        Map<String, String> localStorage = captureLocalStorage(driver);
        CACHE.put(name, new SavedSession(cookies, localStorage));
        System.out.println("[BrowserSessionCache] Stored session: '" + name + "' (" + cookies.size() + " cookies)");
    }

    /**
     * Restores cookies and localStorage from the named session into the current driver.
     *
     * <p>The driver must already be on the target domain (i.e. you should call
     * {@code open("/")} before this) so the browser accepts the domain-scoped cookies.
     * The page is refreshed after restoration so the app picks up the new session.
     *
     * @param name logical session name previously passed to {@link #store(String)}
     * @return {@code true} if a stored session was found and applied; {@code false} otherwise
     */
    @TestFlyApi(since = "1.0.0")
    public static boolean restore(String name) {
        SavedSession session = CACHE.get(name);
        if (session == null) {
            System.out.println("[BrowserSessionCache] No session found for: '" + name + "'");
            return false;
        }

        WebDriver driver = DriverManager.getDriver();
        driver.manage().deleteAllCookies();
        int added = 0;
        for (Cookie cookie : session.cookies) {
            try {
                driver.manage().addCookie(cookie);
                added++;
            } catch (Exception ignored) {}
        }
        boolean storageRestored = restoreLocalStorage(driver, session.localStorage);
        if (!session.cookies.isEmpty() && added == 0 && !storageRestored) {
            System.err.println("[BrowserSessionCache] Failed to restore cookies and localStorage for: '" + name + "'");
            return false;
        }
        driver.navigate().refresh();
        System.out.println("[BrowserSessionCache] Restored session: '" + name + "' (" + added + "/" + session.cookies.size() + " cookies)");
        return true;
    }

    /**
     * Returns {@code true} if a session has been stored under {@code name}.
     */
    @TestFlyApi(since = "1.0.0")
    public static boolean exists(String name) {
        return CACHE.containsKey(name);
    }

    /**
     * Removes the stored session for {@code name}.
     * Subsequent calls to {@link #restore(String)} for this name will return {@code false}.
     */
    @TestFlyApi(since = "1.0.0")
    public static void invalidate(String name) {
        CACHE.remove(name);
        System.out.println("[BrowserSessionCache] Invalidated session: '" + name + "'");
    }

    /**
     * Removes all stored sessions. Typically called in a {@code @AfterSuite} teardown.
     */
    @TestFlyApi(since = "1.0.0")
    public static void clear() {
        CACHE.clear();
    }

    // ----------------------------------------------------------
    // Internals
    // ----------------------------------------------------------

    private record SavedSession(Set<Cookie> cookies, Map<String, String> localStorage) {
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> captureLocalStorage(WebDriver driver) {
        Map<String, String> result = new HashMap<>();
        try {
            Object raw = ((JavascriptExecutor) driver).executeScript(
                "var items = {}; " +
                "for (var i = 0; i < localStorage.length; i++) { " +
                "  var k = localStorage.key(i); items[k] = localStorage.getItem(k); " +
                "} return items;"
            );
            if (raw instanceof Map) {
                ((Map<?, ?>) raw).forEach((k, v) -> result.put(String.valueOf(k), String.valueOf(v)));
            }
        } catch (Exception ignored) {}
        return result;
    }

    private static boolean restoreLocalStorage(WebDriver driver, Map<String, String> items) {
        try {
            ((JavascriptExecutor) driver).executeScript(
                "localStorage.clear(); " +
                "var items = arguments[0]; " +
                "if (items) { " +
                "  for (var k in items) { " +
                "    localStorage.setItem(k, items[k]); " +
                "  } " +
                "}",
                items != null ? items : java.util.Collections.emptyMap()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
