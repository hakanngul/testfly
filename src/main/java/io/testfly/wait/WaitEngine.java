package io.testfly.wait;

import io.testfly.api.TestFlyApi;
import io.testfly.driver.DriverManager;
import io.testfly.healing.SelfHealingLocator;
import io.testfly.internal.TestFlyContext;
import io.testfly.metrics.ExecutionMetrics;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Centralized explicit wait handler.
 *
 * <p>Rules:
 * <li>Explicit waits only — never set implicitlyWait on the driver</li>
 * <li>Timeout always comes from configuration (testfly.yml)</li>
 * <li>Use {@link #wait(ExpectedCondition)} for conditions not covered here</li>
 */
@TestFlyApi(since = "0.4.0")
public final class WaitEngine {

    private WaitEngine() {
    }

    private static WebDriverWait createWait() {
        WebDriver driver = DriverManager.getDriver();
        int timeoutSeconds = TestFlyContext.getConfig().getTimeouts().getExplicit();
        return new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
    }

    // ----------------------------------------------------------
    // Basic waits
    // ----------------------------------------------------------

    /**
     * Pauses the current thread for the given number of milliseconds.
     *
     * <p>Use sparingly — prefer {@link #waitForVisible(By)} and other explicit waits.
     */
    public static void waitMillis(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("[WaitEngine] Sleep interrupted", e);
        }
    }

    /**
     * Pauses the current thread for the given number of seconds.
     *
     * <p>Use sparingly — prefer {@link #waitForVisible(By)} and other explicit waits.
     */
    public static void waitSeconds(int seconds) {
        waitMillis(seconds * 1000L);
    }

    // ----------------------------------------------------------
    // Visibility
    // ----------------------------------------------------------

    public static WebElement waitForVisible(By locator) {
        try {
            return createWait().until(ExpectedConditions.visibilityOfElementLocated(locator));
        } catch (TimeoutException | NoSuchElementException e) {
            WebElement healed = tryHeal(locator);
            if (healed != null) return healed;
            throw e;
        }
    }

    /**
     * Waits until the given already-resolved element is visible.
     *
     * <p>Element-based variant used by {@link io.testfly.locator.Locator} after its
     * candidate set has been resolved. Self-healing is applied earlier, at resolution
     * time, so this overload only waits.
     *
     * @param element the already-resolved element to wait on
     * @return the element once it is visible
     */
    public static WebElement waitForVisible(WebElement element) {
        return createWait().until(ExpectedConditions.visibilityOf(element));
    }

    public static boolean waitForInvisible(By locator) {
        return createWait()
                .until(ExpectedConditions.invisibilityOfElementLocated(locator));
    }

    // ----------------------------------------------------------
    // Interactability
    // ----------------------------------------------------------

    public static WebElement waitForClickable(By locator) {
        try {
            return createWait().until(ExpectedConditions.elementToBeClickable(locator));
        } catch (TimeoutException | NoSuchElementException e) {
            WebElement healed = tryHeal(locator);
            if (healed != null) return healed;
            throw e;
        }
    }

    /**
     * Waits until the given already-resolved element is clickable (visible and enabled).
     *
     * <p>Element-based variant used by {@link io.testfly.locator.Locator} after its
     * candidate set has been resolved. Self-healing is applied earlier, at resolution
     * time, so this overload only waits.
     *
     * @param element the already-resolved element to wait on
     * @return the element once it is clickable
     */
    public static WebElement waitForClickable(WebElement element) {
        return createWait().until(ExpectedConditions.elementToBeClickable(element));
    }

    public static boolean waitForStaleness(WebElement element) {
        return createWait()
                .until(ExpectedConditions.stalenessOf(element));
    }

    /**
     * Waits until the element is enabled (not disabled).
     *
     * <pre>
     * WaitEngine.waitForEnabled(By.id("submit"));
     * </pre>
     */
    public static WebElement waitForEnabled(By locator) {
        try {
            return createWait().until(ExpectedConditions.elementToBeClickable(locator));
        } catch (TimeoutException | NoSuchElementException e) {
            WebElement healed = tryHeal(locator);
            if (healed != null) return healed;
            throw e;
        }
    }

    /**
     * Waits until the element is disabled.
     *
     * <pre>
     * WaitEngine.waitForDisabled(By.id("submit"));
     * </pre>
     */
    public static boolean waitForDisabled(By locator) {
        return createWait().until(ExpectedConditions.attributeToBe(locator, "disabled", "true"));
    }

    /**
     * Waits until a form control is selected (checkbox, radio button, or option).
     *
     * <pre>
     * WaitEngine.waitForSelected(By.id("terms"));
     * </pre>
     */
    public static boolean waitForSelected(By locator) {
        return createWait().until(ExpectedConditions.elementToBeSelected(locator));
    }

    // ----------------------------------------------------------
    // Content
    // ----------------------------------------------------------

    public static WebElement waitForText(By locator, String text) {
        createWait().until(ExpectedConditions.textToBe(locator, text));
        return DriverManager.getDriver().findElement(locator);
    }

    public static WebElement waitForAttributeContains(By locator, String attribute, String value) {
        createWait().until(ExpectedConditions.attributeContains(locator, attribute, value));
        return DriverManager.getDriver().findElement(locator);
    }

    /**
     * Waits until the element's attribute equals {@code value} exactly.
     * Use {@link #waitForAttributeContains(By, String, String)} for a substring match.
     *
     * <pre>
     * WaitEngine.waitForAttribute(By.id("status"), "aria-expanded", "true");
     * </pre>
     */
    public static WebElement waitForAttribute(By locator, String attribute, String value) {
        createWait().until(ExpectedConditions.attributeToBe(locator, attribute, value));
        return DriverManager.getDriver().findElement(locator);
    }

    /**
     * Waits until the element's visible text matches the given regular expression.
     *
     * <pre>
     * WaitEngine.waitForTextMatches(By.cssSelector(".total"), "\\$\\d+\\.\\d{2}");
     * </pre>
     */
    public static WebElement waitForTextMatches(By locator, String textRegex) {
        createWait().until(ExpectedConditions.textMatches(locator, Pattern.compile(textRegex)));
        return DriverManager.getDriver().findElement(locator);
    }

    // ----------------------------------------------------------
    // Navigation
    // ----------------------------------------------------------

    public static boolean waitForTitle(String title) {
        return createWait()
                .until(ExpectedConditions.titleIs(title));
    }

    public static boolean waitForUrlContains(String partialUrl) {
        return createWait()
                .until(ExpectedConditions.urlContains(partialUrl));
    }

    /**
     * Waits until the current URL matches the given regular expression.
     * Use {@link #waitForUrlContains(String)} for a simple substring match.
     *
     * <pre>
     * WaitEngine.waitForUrlMatches(".*&#47;orders&#47;\\d+");
     * </pre>
     */
    public static boolean waitForUrlMatches(String urlRegex) {
        return createWait()
                .until(ExpectedConditions.urlMatches(urlRegex));
    }

    public static void waitForPageLoad() {
        createWait().until(driver ->
                "complete".equals(((JavascriptExecutor) driver)
                        .executeScript("return document.readyState")));
    }

    // ----------------------------------------------------------
    // Windows & frames
    // ----------------------------------------------------------

    /**
     * Waits until the browser has the expected number of open windows/tabs.
     *
     * <pre>
     * WaitEngine.waitForNumberOfWindowsToBe(2);
     * </pre>
     */
    public static boolean waitForNumberOfWindowsToBe(int expectedNumberOfWindows) {
        return createWait().until(ExpectedConditions.numberOfWindowsToBe(expectedNumberOfWindows));
    }

    /**
     * Waits until a frame is available and switches the driver context to it.
     * Remember to call {@code driver.switchTo().defaultContent()} when done.
     *
     * <pre>
     * WaitEngine.waitForFrameAvailableAndSwitchToIt(By.id("payment-iframe"));
     * </pre>
     */
    public static WebDriver waitForFrameAvailableAndSwitchToIt(By frameLocator) {
        return createWait().until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(frameLocator));
    }

    /**
     * Waits until at least {@code minimumCount} elements matching the locator are present.
     * Useful for infinite-scroll feeds or lists that grow asynchronously.
     *
     * <pre>
     * WaitEngine.waitForMinimumElementCount(By.cssSelector(".product-card"), 10);
     * </pre>
     */
    public static List<WebElement> waitForMinimumElementCount(By locator, int minimumCount) {
        return createWait().until(ExpectedConditions.numberOfElementsToBeMoreThan(locator, minimumCount - 1));
    }

    // ----------------------------------------------------------
    // Component framework waits
    // ----------------------------------------------------------

    /**
     * Waits until the Angular application has no pending HTTP requests, timers,
     * or micro-tasks — i.e., the zone is stable.
     *
     * <p>Supports both Angular 2+ ({@code window.getAllAngularTestabilities()})
     * and AngularJS 1.x ({@code $http.pendingRequests}).
     * Returns immediately if neither API is detected on the page.
     *
     * <pre>
     * WaitEngine.waitForAngular();
     * page.clickSubmit();
     * </pre>
     */
    public static void waitForAngular() {
        createWait().until(driver -> {
            try {
                Object result = ((JavascriptExecutor) driver).executeScript(
                    // Angular 2+ — testability API
                    "if (window.getAllAngularTestabilities) {" +
                    "  var t = window.getAllAngularTestabilities();" +
                    "  if (!t || t.length === 0) return true;" +
                    "  return t.every(function(tb) { return tb.isStable(); });" +
                    "}" +
                    // AngularJS 1.x — $http pending requests
                    "if (window.angular) {" +
                    "  try {" +
                    "    var inj = window.angular.element(document.body).injector();" +
                    "    if (!inj) return true;" +
                    "    return inj.get('$http').pendingRequests.length === 0;" +
                    "  } catch(e) { return true; }" +
                    "}" +
                    // Not an Angular page — nothing to wait for
                    "return true;"
                );
                return Boolean.TRUE.equals(result);
            } catch (Exception e) {
                return true;
            }
        });
    }

    /**
     * Waits until React has finished hydrating the server-side-rendered HTML.
     *
     * <p>Detection strategy (in order):
     * <ol>
     *   <li>React 18 — looks for {@code __reactFiber$} or {@code __reactContainer$}
     *       keys on the root element.</li>
     *   <li>React 16/17 — looks for {@code _reactRootContainer} on the root element.</li>
     *   <li>Falls back to looking for any element with a {@code __reactFiber$} key
     *       (catches apps that mount to a non-standard root id).</li>
     * </ol>
     *
     * <p>Common root ids checked: {@code #root}, {@code #__next} (Next.js), {@code #app}.
     * Returns immediately if none are found or if React is not present on the page.
     *
     * <pre>
     * WaitEngine.waitForReactHydration();
     * page.clickButton();
     * </pre>
     */
    public static void waitForReactHydration() {
        waitForPageLoad();
        createWait().until(driver -> {
            try {
                Object result = ((JavascriptExecutor) driver).executeScript(
                    // Locate the React root element (standard root ids)
                    "var root = document.getElementById('root') ||" +
                    "           document.getElementById('__next') ||" +
                    "           document.getElementById('app');" +
                    "if (root) {" +
                    "  var keys = Object.keys(root);" +
                    // React 18: __reactFiber$xxx or __reactContainer$xxx
                    "  if (keys.some(function(k) {" +
                    "    return k.startsWith('__reactFiber') || k.startsWith('__reactContainer');" +
                    "  })) return true;" +
                    // React 16/17: _reactRootContainer
                    "  if (root._reactRootContainer) return true;" +
                    "}" +
                    // Fallback: scan body children for any React fiber key
                    "var children = document.body ? document.body.children : [];" +
                    "for (var i = 0; i < children.length; i++) {" +
                    "  var k = Object.keys(children[i]);" +
                    "  if (k.some(function(key) { return key.startsWith('__reactFiber'); })) return true;" +
                    "}" +
                    // React not detected — nothing to wait for
                    "return true;"
                );
                return Boolean.TRUE.equals(result);
            } catch (Exception e) {
                return true;
            }
        });
    }

    // ----------------------------------------------------------
    // Alert
    // ----------------------------------------------------------

    public static Alert waitForAlert() {
        return createWait()
                .until(ExpectedConditions.alertIsPresent());
    }

    // ----------------------------------------------------------
    // Escape hatch for custom conditions
    // ----------------------------------------------------------

    public static <T> T wait(ExpectedCondition<T> condition) {
        return createWait().until(condition);
    }

    // ----------------------------------------------------------
    // Self-healing fallback
    // ----------------------------------------------------------

    /**
     * Attempts to find an element using self-healing fallback strategies when the
     * primary locator fails. Returns {@code null} if healing is disabled or no
     * fallback strategy succeeds.
     *
     * <p>Enabled via {@code locators.selfHealing: true} in {@code testfly.yml}.
     * Each successful heal is recorded in {@link ExecutionMetrics} and surfaced in
     * the HTML report.
     *
     * @param locator the failing {@link By} locator
     * @return a visible {@link WebElement} found by a fallback strategy, or {@code null}
     */
    public static WebElement tryHeal(By locator) {
        if (!SelfHealingLocator.isEnabled()) return null;
        String testId = TestFlyContext.getCurrentTestId();
        WebElement healed = SelfHealingLocator.tryHeal(DriverManager.getDriver(), locator, testId);
        if (healed != null) {
            ExecutionMetrics.recordHeal(testId);
        }
        return healed;
    }
}
