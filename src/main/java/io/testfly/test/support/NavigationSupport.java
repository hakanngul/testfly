package io.testfly.test.support;

import io.testfly.api.TestFlyApi;
import io.testfly.browser.ConsoleErrorCollector;
import io.testfly.driver.DriverManager;
import io.testfly.internal.TestFlyContext;
import io.testfly.steps.StepLogger;
import io.testfly.wait.WaitEngine;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;


/**
 * Provides navigation helpers ({@code open()}, {@code open(String)}, {@code navigateTo(String)},
 * {@code getCurrentUrl()}, {@code getTitle()}, {@code refresh()}, {@code back()}, {@code forward()},
 * {@code getDriver()}, {@code getWait()}) as interface default methods so {@code BaseTest},
 * {@code BaseJUnit5Test}, {@code BaseCucumberSteps}, {@code BaseConditions}, and {@code BasePage}
 * share a single implementation.
 *
 * <p>Canonical behaviour:
 * <ul>
 *   <li>{@code open()} navigates to {@code execution.baseUrl} from {@code testfly.yml}</li>
 *   <li>{@code open(path)} concatenates {@code baseUrl + path} with slash-normalisation</li>
 *   <li>Both log via {@link StepLogger} and inject {@link ConsoleErrorCollector} shim when enabled</li>
 *   <li>{@code getDriver()} delegates to {@link DriverManager#getDriver()}</li>
 *   <li>{@code getWait()} builds a {@link WebDriverWait} from {@code timeouts.explicit}</li>
 * </ul>
 *
 * @since 1.10.0
 */
@TestFlyApi(since = "1.10.0")
public interface NavigationSupport {

    /** Returns the framework-managed {@link WebDriver} for the calling thread. */
    default WebDriver getDriver() {
        return DriverManager.getDriver();
    }

    /** Returns a {@link WebDriverWait} using the explicit timeout from {@code testfly.yml}. */
    default WebDriverWait getWait() {
        int timeout = TestFlyContext.getConfig().getTimeouts().getExplicit();
        return new WebDriverWait(getDriver(), Duration.ofSeconds(timeout));
    }

    /** Navigates to {@code execution.baseUrl} from {@code testfly.yml}. */
    default void open() {
        String url = baseUrl();
        io.testfly.network.NetworkMock.get().activateBlocklistIfConfigured();
        StepLogger.step("Open " + url);
        getDriver().get(url);
        if (ConsoleErrorCollector.isEnabled()) ConsoleErrorCollector.injectShim();
    }

    /** Navigates to {@code baseUrl + path} with slash normalisation, or directly to {@code path} if it is an absolute URL. */
    default void open(String path) {
        String url;
        if (path.startsWith("http://") || path.startsWith("https://")) {
            url = path;
        } else {
            String base = baseUrl();
            if (base.endsWith("/") && path.startsWith("/")) {
                url = base.substring(0, base.length() - 1) + path;
            } else if (!base.endsWith("/") && !path.startsWith("/")) {
                url = base + "/" + path;
            } else {
                url = base + path;
            }
        }
        io.testfly.network.NetworkMock.get().activateBlocklistIfConfigured();
        StepLogger.step("Open " + url);
        getDriver().get(url);
        if (ConsoleErrorCollector.isEnabled()) ConsoleErrorCollector.injectShim();
    }

    /** Navigates to the given path or absolute URL (alias for {@link #open(String)}). */
    default void navigateTo(String pathOrUrl) {
        open(pathOrUrl);
    }

    /** Returns the current URL of the active browser page. */
    default String getCurrentUrl() {
        return getDriver().getCurrentUrl();
    }

    /** Returns the title of the active browser page. */
    default String getTitle() {
        return getDriver().getTitle();
    }

    /** Refreshes the active page and waits for it to finish loading. */
    default void refresh() {
        StepLogger.step("Refresh page");
        getDriver().navigate().refresh();
        waitForPageLoad();
    }

    /** Navigates back in browser history. */
    default void back() {
        StepLogger.step("Navigate back");
        getDriver().navigate().back();
    }

    /** Navigates forward in browser history. */
    default void forward() {
        StepLogger.step("Navigate forward");
        getDriver().navigate().forward();
    }

    /** Waits until the page is fully loaded (document.readyState === 'complete'). */
    default void waitForPageLoad() {
        WaitEngine.waitForPageLoad(getDriver());
    }

    /** Waits until the current URL contains the given substring. */
    default boolean waitForUrlContains(String partialUrl) {
        return WaitEngine.waitForUrlContains(partialUrl);
    }

    /** Waits until the current URL matches the given regex pattern. */
    default boolean waitForUrlMatches(String urlRegex) {
        return WaitEngine.waitForUrlMatches(urlRegex);
    }

    /** Waits until the page title matches the expected title. */
    default boolean waitForTitle(String title) {
        return WaitEngine.waitForTitle(title);
    }

    /** Waits until the page title contains the given substring. */
    default boolean waitForTitleContains(String partialTitle) {
        return getWait().until(ExpectedConditions.titleContains(partialTitle));
    }

    // ----------------------------------------------------------
    // Window & Tab helpers
    // ----------------------------------------------------------

    /**
     * Switches to the most recently opened browser window or tab.
     * If currently only 1 window is open, it briefly waits for the second window to appear.
     */
    default void switchToNewTab() {
        StepLogger.step("Switch to new tab/window");
        WebDriver driver = getDriver();
        String current = driver.getWindowHandle();
        Set<String> handles = driver.getWindowHandles();
        if (handles.size() == 1) {
            try {
                WaitEngine.waitForNumberOfWindowsToBe(2);
                handles = driver.getWindowHandles();
            } catch (Exception ignored) {
                // proceed with whatever handles are available
            }
        }
        for (String handle : handles) {
            if (!handle.equals(current)) {
                driver.switchTo().window(handle);
                return;
            }
        }
    }

    /**
     * Switches to the last window handle in the iteration order.
     */
    default void switchToLastTab() {
        StepLogger.step("Switch to last tab/window");
        WebDriver driver = getDriver();
        List<String> handles = new ArrayList<>(driver.getWindowHandles());
        if (!handles.isEmpty()) {
            driver.switchTo().window(handles.getLast());
        }
    }

    /**
     * Switches to the first (root/main) window handle.
     */
    default void switchToMainTab() {
        StepLogger.step("Switch to main tab/window");
        WebDriver driver = getDriver();
        List<String> handles = new ArrayList<>(driver.getWindowHandles());
        if (!handles.isEmpty()) {
            driver.switchTo().window(handles.getFirst());
        }
    }

    /**
     * Closes the active tab/window and switches back to the main (first) tab.
     */
    default void closeCurrentTabAndSwitchBack() {
        StepLogger.step("Close current tab and switch back to main tab");
        WebDriver driver = getDriver();
        driver.close();
        switchToMainTab();
    }

    /**
     * Switches to the tab/window at the specified 0-based index.
     *
     * @param index zero-based index of the target window
     */
    default void switchToTab(int index) {
        StepLogger.step("Switch to tab index " + index);
        WebDriver driver = getDriver();
        List<String> handles = new ArrayList<>(driver.getWindowHandles());
        if (index < 0 || index >= handles.size()) {
            throw new IllegalArgumentException(
                    "Window index out of bounds: " + index + ", total open windows: " + handles.size());
        }
        driver.switchTo().window(handles.get(index));
    }

    /**
     * Switches to the window whose title contains the given substring.
     *
     * @param titleSubstring substring to search for in page title
     * @return {@code true} if a matching window was found and switched to, {@code false} otherwise
     */
    default boolean switchToTabByTitle(String titleSubstring) {
        StepLogger.step("Switch to tab with title containing: " + titleSubstring);
        WebDriver driver = getDriver();
        String current = driver.getWindowHandle();
        for (String handle : driver.getWindowHandles()) {
            driver.switchTo().window(handle);
            if (driver.getTitle() != null && driver.getTitle().contains(titleSubstring)) {
                return true;
            }
        }
        driver.switchTo().window(current);
        return false;
    }

    /**
     * Switches to the window whose current URL contains the given substring.
     *
     * @param urlSubstring substring to search for in current URL
     * @return {@code true} if a matching window was found and switched to, {@code false} otherwise
     */
    default boolean switchToTabByUrl(String urlSubstring) {
        StepLogger.step("Switch to tab with URL containing: " + urlSubstring);
        WebDriver driver = getDriver();
        String current = driver.getWindowHandle();
        for (String handle : driver.getWindowHandles()) {
            driver.switchTo().window(handle);
            if (driver.getCurrentUrl() != null && driver.getCurrentUrl().contains(urlSubstring)) {
                return true;
            }
        }
        driver.switchTo().window(current);
        return false;
    }

    /**
     * Returns the total count of currently open windows/tabs.
     */
    default int getWindowCount() {
        return getDriver().getWindowHandles().size();
    }

    /**
     * Zooms the page in or out by setting the CSS zoom property on {@code document.body}.
     *
     * <pre>
     * zoom(80);  // zoom to 80%
     * zoom(100); // reset to normal
     * </pre>
     *
     * @param percentage zoom percentage (e.g. 80, 100, 125)
     */
    default void zoom(int percentage) {
        StepLogger.step("Zoom page to " + percentage + "%");
        JavascriptExecutor js = (JavascriptExecutor) getDriver();
        js.executeScript("document.body.style.zoom = '" + percentage + "%';");
    }

    /**
     * Scrolls the window by the specified horizontal and vertical pixel deltas.
     *
     * @param x horizontal pixel delta (positive = right, negative = left)
     * @param y vertical pixel delta (positive = down, negative = up)
     */
    default void scrollBy(int x, int y) {
        StepLogger.step("Scroll by (" + x + ", " + y + ")");
        JavascriptExecutor js = (JavascriptExecutor) getDriver();
        js.executeScript("window.scrollBy(arguments[0], arguments[1]);", x, y);
    }

    private String baseUrl() {
        String url = TestFlyContext.getConfig().getExecution().getBaseUrl();
        if (url == null || url.isEmpty()) {
            throw new IllegalStateException("execution.baseUrl is not set in testfly.yml");
        }
        return url;
    }
}


