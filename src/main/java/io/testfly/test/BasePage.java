package io.testfly.test;

import io.testfly.api.TestFlyApi;
import io.testfly.internal.TestFlyContext;
import io.testfly.shadow.ShadowDom;
import io.testfly.steps.StepLogger;
import io.testfly.test.support.AssertionSupport;
import io.testfly.test.support.BrowserSupport;
import io.testfly.test.support.LocatorSupport;
import io.testfly.test.support.SoftAssertSupport;
import io.testfly.test.support.StepSupport;
import io.testfly.test.support.VisualSupport;
import io.testfly.wait.WaitEngine;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * Base class for all page objects.
 *
 * <p>Provides safe, wait-backed helpers so page objects never call raw
 * Selenium APIs directly. Extend this class instead of writing boilerplate
 * in every page object.
 *
 * <pre>
 * public class LoginPage extends BasePage {
 *     private static final By USERNAME = By.id("username");
 *     private static final By PASSWORD = By.id("password");
 *     private static final By SUBMIT   = By.id("submit");
 *
 *     public LoginPage(WebDriver driver) { super(driver); }
 *
 *     public void login(String user, String pass) {
 *         type(USERNAME, user);
 *         type(PASSWORD, pass);
 *         click(SUBMIT);
 *     }
 * }
 * </pre>
 */
@TestFlyApi(since = "0.8.0")
public abstract class BasePage implements LocatorSupport, AssertionSupport, StepSupport,
        SoftAssertSupport, BrowserSupport, VisualSupport {

    protected final WebDriver driver;

    /** Tracks how many frames deep we are on this thread — used to decide parentFrame vs defaultContent. */
    private static final ThreadLocal<Integer> FRAME_DEPTH = ThreadLocal.withInitial(() -> 0);

    protected BasePage(WebDriver driver) {
        this.driver = driver;
    }

    // ----------------------------------------------------------
    // Basic waits
    // ----------------------------------------------------------

    /**
     * Pauses execution for the given number of milliseconds.
     *
     * <p>Use sparingly — prefer explicit waits such as {@link WaitEngine#waitForVisible(By)}.
     */
    protected void waitMillis(long milliseconds) {
        WaitEngine.waitMillis(milliseconds);
    }

    /**
     * Pauses execution for the given number of seconds.
     *
     * <p>Use sparingly — prefer explicit waits such as {@link WaitEngine#waitForVisible(By)}.
     */
    protected void waitSeconds(int seconds) {
        WaitEngine.waitSeconds(seconds);
    }

    // ----------------------------------------------------------
    // Core interaction helpers
    // ----------------------------------------------------------

    /**
     * Waits for the element to be clickable, then clicks it.
     */
    protected void click(By locator) {
        find(locator).click();
    }

    /**
     * Waits for the element to be visible, clears it, then types the given text.
     */
    protected void type(By locator, String text) {
        find(locator).type(text);
    }

    /**
     * Waits for the element to be visible and returns its visible text.
     */
    protected String getText(By locator) {
        return find(locator).getText();
    }

    /**
     * Waits for the element to be visible and returns the value of the given attribute.
     */
    protected String getAttribute(By locator, String attribute) {
        return find(locator).getAttribute(attribute);
    }

    /**
     * Returns {@code true} if the element is present in the DOM and visible.
     * Does not throw — returns {@code false} for missing or hidden elements.
     */
    protected boolean isDisplayed(By locator) {
        return find(locator).isVisible();
    }

    // ----------------------------------------------------------
    // Dropdown helpers (HTML <select>)
    // ----------------------------------------------------------

    /**
     * Selects an option from a {@code <select>} element by its visible text.
     *
     * <pre>selectByText(By.id("country"), "United Kingdom");</pre>
     */
    protected void selectByText(By locator, String text) {
        step("Select by text in " + locator);
        new Select(WaitEngine.waitForVisible(locator)).selectByVisibleText(text);
    }

    /**
     * Selects an option from a {@code <select>} element by its {@code value} attribute.
     *
     * <pre>selectByValue(By.id("status"), "active");</pre>
     */
    protected void selectByValue(By locator, String value) {
        step("Select by value in " + locator);
        new Select(WaitEngine.waitForVisible(locator)).selectByValue(value);
    }

    /**
     * Selects an option from a {@code <select>} element by its zero-based index.
     *
     * <pre>selectByIndex(By.id("month"), 2);</pre>
     */
    protected void selectByIndex(By locator, int index) {
        step("Select by index in " + locator);
        new Select(WaitEngine.waitForVisible(locator)).selectByIndex(index);
    }

    /**
     * Returns the visible text of the currently selected option in a {@code <select>} element.
     */
    protected String getSelectedOption(By locator) {
        step("Get selected option from " + locator);
        return new Select(WaitEngine.waitForVisible(locator)).getFirstSelectedOption().getText();
    }

    // ----------------------------------------------------------
    // Alert helpers
    // ----------------------------------------------------------

    /**
     * Waits for a browser alert to be present, then accepts it (clicks OK).
     */
    protected void acceptAlert() {
        step("Accept alert");
        waitForAlert().accept();
    }

    /**
     * Waits for a browser alert to be present, then dismisses it (clicks Cancel).
     */
    protected void dismissAlert() {
        step("Dismiss alert");
        waitForAlert().dismiss();
    }

    /**
     * Waits for a browser alert to be present and returns its text.
     */
    protected String getAlertText() {
        step("Get alert text");
        return waitForAlert().getText();
    }

    /**
     * Waits for a browser alert to be present, captures its text, accepts it,
     * and returns the text in one step.
     *
     * <pre>String msg = getAndAcceptAlert();</pre>
     */
    protected String getAndAcceptAlert() {
        step("Get and accept alert");
        Alert alert = waitForAlert();
        String text = alert.getText();
        alert.accept();
        return text;
    }

    /**
     * Waits for a prompt alert, types the given text into it, then accepts it.
     *
     * <pre>typeInAlert("my input");</pre>
     */
    protected void typeInAlert(String text) {
        step("Type in alert");
        Alert alert = waitForAlert();
        alert.sendKeys(text);
        alert.accept();
    }

    private Alert waitForAlert() {
        int timeout = TestFlyContext.getConfig().getTimeouts().getExplicit();
        return new WebDriverWait(driver, Duration.ofSeconds(timeout))
                .until(ExpectedConditions.alertIsPresent());
    }

    // ----------------------------------------------------------
    // Mouse action helpers
    // ----------------------------------------------------------

    /**
     * Moves the mouse over the element (hover / mouse-over).
     *
     * <pre>hover(By.id("menu-item"));</pre>
     */
    protected void hover(By locator) {
        find(locator).hover();
    }

    /**
     * Double-clicks the element.
     */
    protected void doubleClick(By locator) {
        step("Double-click " + locator);
        WebElement el = WaitEngine.waitForClickable(locator);
        new Actions(driver).doubleClick(el).perform();
    }

    /**
     * Right-clicks (context menu) the element.
     */
    protected void rightClick(By locator) {
        step("Right-click " + locator);
        WebElement el = WaitEngine.waitForVisible(locator);
        new Actions(driver).contextClick(el).perform();
    }

    // ----------------------------------------------------------
    // Scroll helpers
    // ----------------------------------------------------------

    /**
     * Scrolls the element into the visible viewport.
     *
     * <pre>scrollTo(By.id("footer"));</pre>
     */
    protected void scrollTo(By locator) {
        find(locator).scrollIntoView();
    }

    /**
     * Scrolls the page to the very top.
     */
    protected void scrollToTop() {
        step("Scroll to top");
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");
    }

    /**
     * Scrolls the page to the very bottom.
     */
    protected void scrollToBottom() {
        step("Scroll to bottom");
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, document.body.scrollHeight);");
    }

    // ----------------------------------------------------------
    // JavaScript fallback helpers
    // ----------------------------------------------------------

    /**
     * Clicks the element via JavaScript — useful when a native click is blocked by an overlay.
     *
     * <pre>jsClick(By.id("hidden-trigger"));</pre>
     */
    protected void jsClick(By locator) {
        find(locator).jsClick();
    }

    /**
     * Sets the element's {@code value} property via JavaScript — useful for read-only inputs
     * or custom components that block native {@code sendKeys}.
     *
     * <pre>jsType(By.id("date-picker"), "2025-01-01");</pre>
     */
    protected void jsType(By locator, String text) {
        step("JS type into " + locator);
        WebElement el = driver.findElement(locator);
        ((JavascriptExecutor) driver).executeScript("arguments[0].value = arguments[1];", el, text);
    }

    // ----------------------------------------------------------
    // Soft assertions
    // ----------------------------------------------------------



    // ----------------------------------------------------------
    // SmartLocator helper
    // ----------------------------------------------------------

    /**
     * Tries each locator in order and returns the first element that is found and displayed.
     *
     * <p>Delegates to {@link SmartLocator#find(WebDriver, By...)} — no need to pass the driver manually.
     *
     * <pre>
     * WebElement btn = smartFind(
     *     By.cssSelector(".submit-btn"),
     *     By.xpath("//button[@type='submit']")
     * );
     * </pre>
     *
     * @param primary   the preferred locator strategy
     * @param fallbacks additional strategies tried in order if the primary fails
     * @return the first matching visible element
     */
    protected WebElement smartFind(By primary, By... fallbacks) {
        step("Smart find " + primary);
        By[] all = new By[1 + fallbacks.length];
        all[0] = primary;
        System.arraycopy(fallbacks, 0, all, 1, fallbacks.length);
        return SmartLocator.find(driver, all);
    }

    // ----------------------------------------------------------
    // iFrame helpers
    // ----------------------------------------------------------

    /**
     * Switches into the given frame, runs the action, then restores the previous context.
     * Safe to nest — inner frames restore to their parent frame, not default content.
     *
     * <pre>
     * withinFrame(By.id("outer-iframe"), () -> {
     *     withinFrame(By.id("inner-iframe"), () -> {
     *         type(By.id("card-number"), "4111111111111111");
     *     });
     *     click(By.id("pay")); // still inside outer-iframe
     * });
     * </pre>
     */
    protected void withinFrame(By frameLocator, Runnable action) {
        step("Switch to frame " + frameLocator);
        WebElement frame = WaitEngine.waitForVisible(frameLocator);
        driver.switchTo().frame(frame);
        FRAME_DEPTH.set(FRAME_DEPTH.get() + 1);
        try {
            action.run();
        } finally {
            exitFrame();
        }
    }

    /**
     * Switches into the frame at the given zero-based index, runs the action,
     * then restores the previous context. Safe to nest.
     */
    protected void withinFrameIndex(int index, Runnable action) {
        step("Switch to frame index " + index);
        driver.switchTo().frame(index);
        FRAME_DEPTH.set(FRAME_DEPTH.get() + 1);
        try {
            action.run();
        } finally {
            exitFrame();
        }
    }

    /**
     * Switches into the frame identified by name or id attribute, runs the action,
     * then restores the previous context. Safe to nest.
     */
    protected void withinFrameName(String nameOrId, Runnable action) {
        step("Switch to frame \"" + nameOrId + "\"");
        driver.switchTo().frame(nameOrId);
        FRAME_DEPTH.set(FRAME_DEPTH.get() + 1);
        try {
            action.run();
        } finally {
            exitFrame();
        }
    }

    private void exitFrame() {
        int depth = FRAME_DEPTH.get() - 1;
        FRAME_DEPTH.set(depth);
        if (depth == 0) {
            driver.switchTo().defaultContent();
        } else {
            driver.switchTo().parentFrame();
        }
    }

    // ----------------------------------------------------------
    // Shadow DOM helpers
    // ----------------------------------------------------------

    /**
     * Finds a single element inside the shadow root of the element at {@code hostLocator}.
     *
     * <pre>
     * WebElement input = shadowFind(By.cssSelector("my-form"), "#email");
     * </pre>
     *
     * @param hostLocator locator for the shadow host element
     * @param innerCss    CSS selector scoped to the shadow root (XPath not supported)
     */
    protected WebElement shadowFind(By hostLocator, String innerCss) {
        step("Shadow find " + hostLocator + " / " + innerCss);
        return ShadowDom.find(hostLocator, innerCss);
    }

    /**
     * Finds all elements matching {@code innerCss} inside the shadow root of {@code hostLocator}.
     *
     * @return unmodifiable list; empty if nothing matches
     */
    protected java.util.List<WebElement> shadowFindAll(By hostLocator, String innerCss) {
        step("Shadow find all " + hostLocator + " / " + innerCss);
        return ShadowDom.findAll(hostLocator, innerCss);
    }

    /**
     * Clicks an element inside a shadow root.
     *
     * <pre>shadowClick(By.cssSelector("my-form"), "#submit-btn");</pre>
     */
    protected void shadowClick(By hostLocator, String innerCss) {
        step("Shadow click " + hostLocator + " / " + innerCss);
        ShadowDom.find(hostLocator, innerCss).click();
    }

    /**
     * Clears and types text into an input inside a shadow root.
     *
     * <pre>shadowType(By.cssSelector("my-form"), "#email", "user@example.com");</pre>
     */
    protected void shadowType(By hostLocator, String innerCss, String text) {
        step("Shadow type into " + hostLocator + " / " + innerCss);
        WebElement el = ShadowDom.find(hostLocator, innerCss);
        el.clear();
        el.sendKeys(text);
    }

    /**
     * Returns the visible text of an element inside a shadow root.
     */
    protected String shadowGetText(By hostLocator, String innerCss) {
        step("Shadow get text " + hostLocator + " / " + innerCss);
        return ShadowDom.find(hostLocator, innerCss).getText();
    }

    /**
     * Traverses nested shadow roots and returns the target element.
     * Pass CSS selectors from outermost host down to the target element.
     *
     * <pre>
     * // <checkout-flow> → shadow → <payment-widget> → shadow → #pay-btn
     * WebElement btn = shadowPierce("checkout-flow", "payment-widget", "#pay-btn");
     * </pre>
     */
    protected WebElement shadowPierce(String... cssSelectors) {
        step("Shadow pierce " + String.join(" -> ", cssSelectors));
        return ShadowDom.pierce(cssSelectors);
    }

    /**
     * Returns {@code true} if at least one element matching {@code innerCss}
     * exists inside the host's shadow root. Never throws.
     */
    protected boolean shadowExists(By hostLocator, String innerCss) {
        return ShadowDom.exists(hostLocator, innerCss);
    }

    // ----------------------------------------------------------
    // File upload
    // ----------------------------------------------------------

    /**
     * Sends the given file path to a file input element.
     *
     * <p>The {@code filePath} is resolved in this order:
     * <ol>
     *   <li>Absolute path — used as-is if the file exists.</li>
     *   <li>Classpath resource — resolved relative to {@code src/test/resources/}.</li>
     *   <li>Project-root relative path — resolved from the current working directory.</li>
     * </ol>
     *
     * <pre>
     * upload(By.id("file-input"), "testfiles/sample.pdf");
     * upload(By.id("avatar"),     "/absolute/path/to/image.png");
     * </pre>
     */
    protected void upload(By inputLocator, String filePath) {
        step("Upload file to " + inputLocator);
        String absolutePath = resolveFilePath(filePath);
        WebElement input = WaitEngine.waitForVisible(inputLocator);
        input.sendKeys(absolutePath);
    }

    // ----------------------------------------------------------
    // Phase 14 — Network, Storage, GeoLocation, Clipboard
    // ----------------------------------------------------------

    // networkMock(), localStorage(), sessionStorage(), cookies(), mockLocation(), clipboard() — via BrowserSupport

    // ----------------------------------------------------------
    // Fluent Locator API (find / $), Accessibility locators (getBy*),
    // Web-First Assertions (assertThat) — via support interfaces
    // ----------------------------------------------------------
    // find(String/By), $(String/By), getByRole/Text/Label/Placeholder/TestId/AltText/Title
    // and assertThat(By/Locator) are provided as default methods in
    // io.testfly.test.support.LocatorSupport and AssertionSupport.

    // ----------------------------------------------------------
    // Phase 15 — Visual Regression + Device Emulation
    // ----------------------------------------------------------

    // assertScreenshot() ×4, emulateDevice(), resetDevice() — via VisualSupport

    private String resolveFilePath(String filePath) {
        // 1. Absolute path
        File absolute = new File(filePath);
        if (absolute.isAbsolute() && absolute.exists()) {
            return absolute.getAbsolutePath();
        }

        // 2. Classpath resource (src/test/resources)
        java.net.URL resource = getClass().getClassLoader().getResource(filePath);
        if (resource != null) {
            try {
                return Paths.get(resource.toURI()).toAbsolutePath().toString();
            } catch (Exception ignored) {}
        }

        // 3. Project-root relative
        File relative = Paths.get(System.getProperty("user.dir"), filePath).toFile();
        if (relative.exists()) {
            return relative.getAbsolutePath();
        }

        throw new IllegalArgumentException(
            "File not found for upload: '" + filePath + "'. " +
            "Checked: absolute path, classpath resources, and project-root relative path."
        );
    }

    
}
