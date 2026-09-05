package io.testfly.assertion;

import io.testfly.api.TestFlyApi;
import io.testfly.driver.DriverManager;
import io.testfly.internal.TestFlyContext;
import io.testfly.steps.StepLogger;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * Fluent, auto-retrying assertion for browser page state (title, URL).
 *
 * <p>Every assertion polls via {@link WebDriverWait} until the condition is true
 * or the configured {@code timeouts.explicit} is exceeded — matching the style of
 * Playwright's {@code expect(page).toHaveTitle()}.
 *
 * <p>Obtain an instance via {@link SeleniumAssert#assertThat(WebDriver)} or
 * {@link SeleniumAssert#assertThatPage()}, or in test classes via
 * {@code assertThat(getDriver())} / {@code assertThatPage()}.
 *
 * <pre>
 * assertThat(getDriver()).hasTitle("Dashboard");
 * assertThat(getDriver()).urlContains("/inventory");
 * assertThatPage().titleContains("Sauce");
 * </pre>
 */
@TestFlyApi(since = "1.10.0")
public final class PageAssert {

    private final WebDriver driver;
    private Duration customTimeout;
    private String customMessage;
    private boolean soft;
    private final SoftAssertionCollector collector;

    PageAssert(WebDriver driver) {
        this(driver, false, null);
    }

    PageAssert(WebDriver driver, boolean soft) {
        this(driver, soft, null);
    }

    PageAssert(WebDriver driver, SoftAssertionCollector collector) {
        this(driver, true, collector);
    }

    PageAssert(WebDriver driver, boolean soft, SoftAssertionCollector collector) {
        this.driver    = driver;
        this.soft      = soft;
        this.collector = collector;
    }

    // ------------------------------------------------------------------
    // Modifiers & Configuration
    // ------------------------------------------------------------------

    /**
     * Overrides the wait timeout for this assertion.
     *
     * @param timeout custom duration to poll before failing
     * @return this assertion for chaining
     */
    public PageAssert within(Duration timeout) {
        this.customTimeout = timeout;
        return this;
    }

    /**
     * Overrides the wait timeout in seconds for this assertion.
     *
     * @param timeoutSeconds custom timeout in seconds
     * @return this assertion for chaining
     */
    public PageAssert within(int timeoutSeconds) {
        this.customTimeout = Duration.ofSeconds(timeoutSeconds);
        return this;
    }

    /**
     * Attaches a custom description or failure message to this assertion.
     *
     * @param message custom message displayed upon failure
     * @return this assertion for chaining
     */
    public PageAssert as(String message) {
        this.customMessage = message;
        return this;
    }

    /**
     * Alias for {@link #as(String)}.
     */
    public PageAssert describedAs(String message) {
        return as(message);
    }

    /**
     * Switches this assertion into soft mode — failures are collected in
     * {@link SoftAssertions} rather than throwing an immediate {@link AssertionError}.
     *
     * @return this assertion for chaining
     */
    public PageAssert softly() {
        this.soft = true;
        return this;
    }

    // ------------------------------------------------------------------
    // Title Assertions
    // ------------------------------------------------------------------

    /**
     * Asserts the page title equals {@code expectedTitle} exactly — retries until timeout.
     */
    public PageAssert hasTitle(String expectedTitle) {
        StepLogger.step("Assert page title is: " + expectedTitle);
        poll(ExpectedConditions.titleIs(expectedTitle),
                "Expected page title to be [" + expectedTitle + "]");
        return this;
    }

    /**
     * Asserts the page title contains {@code fragment} — retries until timeout.
     */
    public PageAssert titleContains(String fragment) {
        StepLogger.step("Assert page title contains: " + fragment);
        poll(ExpectedConditions.titleContains(fragment),
                "Expected page title to contain [" + fragment + "]");
        return this;
    }

    // ------------------------------------------------------------------
    // URL Assertions
    // ------------------------------------------------------------------

    /**
     * Asserts the page URL equals {@code expectedUrl} exactly — retries until timeout.
     */
    public PageAssert hasUrl(String expectedUrl) {
        StepLogger.step("Assert page URL is: " + expectedUrl);
        poll(ExpectedConditions.urlToBe(expectedUrl),
                "Expected page URL to be [" + expectedUrl + "]");
        return this;
    }

    /**
     * Asserts the page URL contains {@code fragment} — retries until timeout.
     */
    public PageAssert urlContains(String fragment) {
        StepLogger.step("Assert page URL contains: " + fragment);
        poll(ExpectedConditions.urlContains(fragment),
                "Expected page URL to contain [" + fragment + "]");
        return this;
    }

    /**
     * Asserts the page URL matches the given regex pattern — retries until timeout.
     */
    public PageAssert urlMatches(String regex) {
        StepLogger.step("Assert page URL matches: " + regex);
        poll(ExpectedConditions.urlMatches(regex),
                "Expected page URL to match regex [" + regex + "]");
        return this;
    }

    // ------------------------------------------------------------------
    // Internal poll
    // ------------------------------------------------------------------

    private <T> void poll(ExpectedCondition<T> condition, String failMessage) {
        Duration timeout = customTimeout != null
                ? customTimeout
                : (TestFlyContext.getConfig() != null && TestFlyContext.getConfig().getTimeouts() != null
                        ? Duration.ofSeconds(TestFlyContext.getConfig().getTimeouts().getExplicit())
                        : Duration.ofSeconds(10));
        WebDriver currentDriver = driver != null ? driver : DriverManager.getDriver();
        String fullMessage = customMessage != null && !customMessage.isBlank()
                ? "[" + customMessage + "] " + failMessage
                : failMessage;
        try {
            new WebDriverWait(currentDriver, timeout).until(condition);
        } catch (TimeoutException e) {
            String actualTitle = "";
            String actualUrl = "";
            try {
                actualTitle = currentDriver.getTitle();
                actualUrl = currentDriver.getCurrentUrl();
            } catch (Exception ignored) {
            }
            String timeoutStr = timeout.toMillis() >= 1000 && timeout.toMillis() % 1000 == 0
                    ? timeout.toSeconds() + "s"
                    : timeout.toMillis() + "ms";
            String err = fullMessage + " (actual title: [" + actualTitle + "], actual url: [" + actualUrl + "], timeout: " + timeoutStr + ")";
            if (soft) {
                SoftAssertionCollector target = collector != null ? collector : SoftAssertions.get();
                target.that(false, err);
            } else {
                throw new AssertionError(err, e);
            }
        }
    }
}
