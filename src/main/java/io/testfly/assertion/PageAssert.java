package io.testfly.assertion;

import io.testfly.ai.DomPruner;
import io.testfly.api.TestFlyApi;
import io.testfly.assertion.ai.AiAssertEngine;
import io.testfly.driver.DriverManager;
import io.testfly.internal.TestFlyContext;
import io.testfly.steps.StepLogger;
import io.testfly.wait.WaitEngine;

import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoAlertPresentException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * Fluent, auto-retrying assertion for browser page state (title, URL).
 *
 * <p>
 * Every assertion polls via {@link WebDriverWait} until the condition is true
 * or the configured {@code timeouts.explicit} is exceeded — matching the style
 * of
 * Playwright's {@code expect(page).toHaveTitle()}.
 *
 * <p>
 * Obtain an instance via {@link SeleniumAssert#assertThat(WebDriver)} or
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
    private boolean negated;
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
        this.driver = driver;
        this.soft = soft;
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
     * {@link SoftAssertions} rather than throwing an immediate
     * {@link AssertionError}.
     *
     * @return this assertion for chaining
     */
    public PageAssert softly() {
        this.soft = true;
        return this;
    }

    /**
     * Negates the <b>next</b> assertion in the chain, like Playwright's
     * {@code not()}.
     *
     * <pre>
     * assertThatPage().not().urlContains("/login");
     * assertThatPage().not().hasTitle("Error");
     * </pre>
     *
     * <p>
     * Only the assertion directly after {@code not()} is negated:
     * {@code assertThatPage().not().urlContains("/login").hasTitle("Home")} checks
     * "URL does not contain /login", then "title is Home". A negated assertion
     * passes as soon as the condition is not met, so after a navigation assert
     * the expected new state first when the old state may still be showing.
     *
     * @return this assertion for chaining
     * @since 1.1.0
     */
    public PageAssert not() {
        this.negated = true;
        return this;
    }

    // ------------------------------------------------------------------
    // Title Assertions
    // ------------------------------------------------------------------

    /**
     * Asserts the page title equals {@code expectedTitle} exactly — retries until
     * timeout.
     */
    public PageAssert hasTitle(String expectedTitle) {
        step("Assert page title is: " + expectedTitle);
        poll(ExpectedConditions.titleIs(expectedTitle),
                "Expected page title to be [" + expectedTitle + "]");
        return this;
    }

    /**
     * Asserts the page title contains {@code fragment} — retries until timeout.
     */
    public PageAssert titleContains(String fragment) {
        step("Assert page title contains: " + fragment);
        poll(ExpectedConditions.titleContains(fragment),
                "Expected page title to contain [" + fragment + "]");
        return this;
    }

    // ------------------------------------------------------------------
    // URL Assertions
    // ------------------------------------------------------------------

    /**
     * Asserts the page URL equals {@code expectedUrl} exactly — retries until
     * timeout.
     */
    public PageAssert hasUrl(String expectedUrl) {
        step("Assert page URL is: " + expectedUrl);
        poll(ExpectedConditions.urlToBe(expectedUrl),
                "Expected page URL to be [" + expectedUrl + "]");
        return this;
    }

    /**
     * Asserts the page URL contains {@code fragment} — retries until timeout.
     */
    public PageAssert urlContains(String fragment) {
        step("Assert page URL contains: " + fragment);
        poll(ExpectedConditions.urlContains(fragment),
                "Expected page URL to contain [" + fragment + "]");
        return this;
    }

    /**
     * Asserts the page URL matches the given regex pattern — retries until timeout.
     */
    public PageAssert urlMatches(String regex) {
        step("Assert page URL matches: " + regex);
        poll(ExpectedConditions.urlMatches(regex),
                "Expected page URL to match regex [" + regex + "]");
        return this;
    }

    // ------------------------------------------------------------------
    // Alerts
    // ------------------------------------------------------------------

    /**
     * Asserts a JavaScript alert/confirm/prompt is open — retries until timeout.
     *
     * @since 1.1.0
     */
    public PageAssert hasAlert() {
        step("Assert alert is present");
        poll(ExpectedConditions.alertIsPresent(), "Expected an alert to be present");
        return this;
    }

    /**
     * Asserts an open alert's text equals {@code expected} — retries until
     * timeout. The alert is left open.
     *
     * @since 1.1.0
     */
    public PageAssert hasAlertText(String expected) {
        step("Assert alert text: " + expected);
        poll(d -> {
            try {
                return expected.equals(d.switchTo().alert().getText()) ? true : null;
            } catch (NoAlertPresentException e) {
                return null;
            }
        }, "Expected alert text [" + expected + "]");
        return this;
    }

    /**
     * Asserts no alert is open — retries until timeout.
     *
     * @since 1.1.0
     */
    public PageAssert hasNoAlert() {
        step("Assert no alert is present");
        poll(d -> {
            try {
                d.switchTo().alert();
                return null;
            } catch (NoAlertPresentException e) {
                return true;
            }
        }, "Expected no alert to be present");
        return this;
    }

    // ------------------------------------------------------------------
    // Cookies, storage & windows
    // ------------------------------------------------------------------

    /**
     * Asserts a cookie with the given name exists — retries until timeout.
     *
     * @since 1.1.0
     */
    public PageAssert hasCookie(String name) {
        step("Assert cookie exists: " + name);
        poll(d -> d.manage().getCookieNamed(name) != null ? true : null,
                "Expected cookie [" + name + "] to exist");
        return this;
    }

    /**
     * Asserts a cookie with the given name has the given value — retries until
     * timeout.
     *
     * @since 1.1.0
     */
    public PageAssert hasCookie(String name, String expectedValue) {
        step("Assert cookie " + name + "=" + expectedValue);
        poll(d -> {
            Cookie cookie = d.manage().getCookieNamed(name);
            return cookie != null && expectedValue.equals(cookie.getValue()) ? true : null;
        }, "Expected cookie [" + name + "=" + expectedValue + "]");
        return this;
    }

    /**
     * Asserts no cookie with the given name exists — retries until timeout.
     *
     * @since 1.1.0
     */
    public PageAssert doesNotHaveCookie(String name) {
        step("Assert cookie absent: " + name);
        poll(d -> d.manage().getCookieNamed(name) == null ? true : null,
                "Expected cookie [" + name + "] not to exist");
        return this;
    }

    /**
     * Asserts {@code localStorage} contains the given key — retries until timeout.
     *
     * @since 1.1.0
     */
    public PageAssert hasLocalStorageItem(String key) {
        step("Assert localStorage item exists: " + key);
        poll(d -> localStorageItem(d, key) != null ? true : null,
                "Expected localStorage item [" + key + "] to exist");
        return this;
    }

    /**
     * Asserts the {@code localStorage} value for {@code key} equals
     * {@code expectedValue} — retries until timeout.
     *
     * @since 1.1.0
     */
    public PageAssert hasLocalStorageItem(String key, String expectedValue) {
        step("Assert localStorage " + key + "=" + expectedValue);
        poll(d -> expectedValue.equals(localStorageItem(d, key)) ? true : null,
                "Expected localStorage item [" + key + "=" + expectedValue + "]");
        return this;
    }

    /**
     * Asserts the browser has exactly {@code expected} open windows/tabs — retries
     * until timeout.
     *
     * @since 1.1.0
     */
    public PageAssert hasWindowCount(int expected) {
        step("Assert window count: " + expected);
        poll(ExpectedConditions.numberOfWindowsToBe(expected),
                "Expected " + expected + " open window(s)");
        return this;
    }

    private static Object localStorageItem(WebDriver d, String key) {
        return ((JavascriptExecutor) d).executeScript("return window.localStorage.getItem(arguments[0]);", key);
    }

    // ------------------------------------------------------------------
    // Semantic AI Assertions
    // ------------------------------------------------------------------

    /**
     * Asserts that the current page state semantically satisfies the given natural
     * language condition.
     *
     * <p>
     * Anti-throttle guarantee: does not poll every 500ms. Extracts pruned DOM and
     * performs a bounded
     * LLM reasoning evaluation.
     *
     * @param expectedCondition natural language expectation (e.g. "Order was placed
     *                          successfully")
     * @return this assertion for chaining
     */
    public PageAssert satisfiesAi(String expectedCondition) {
        step("Assert page satisfies condition (AI): " + expectedCondition);
        evaluateAi(expectedCondition, !consumeNegation());
        return this;
    }

    /**
     * Asserts that the current page state does NOT violate or contain the given
     * forbidden condition.
     *
     * @param forbiddenCondition natural language forbidden condition (e.g. "Error
     *                           500 or red alert banner")
     * @return this assertion for chaining
     */
    public PageAssert violatesAi(String forbiddenCondition) {
        step("Assert page does not violate condition (AI): " + forbiddenCondition);
        evaluateAi(forbiddenCondition, consumeNegation());
        return this;
    }

    private void evaluateAi(String condition, boolean expectSatisfaction) {
        WebDriver currentDriver = driver != null ? driver : DriverManager.getDriver();

        try {
            WaitEngine.waitForPageLoad();
        } catch (Exception ignored) {
        }

        String prunedDom = DomPruner.prune(currentDriver);
        AiAssertEngine.AiAssertionResult result = AiAssertEngine.verify(currentDriver, prunedDom, condition,
                expectSatisfaction);

        // If not passed initially, allow in-flight navigation/AJAX redirects to settle
        // and retry once
        if (!result.isPassed()) {
            WaitEngine.waitMillis(3000);
            prunedDom = DomPruner.prune(currentDriver);
            result = AiAssertEngine.verify(currentDriver, prunedDom, condition, expectSatisfaction);
        }

        if (!result.isPassed()) {
            String prefix = customMessage != null && !customMessage.isBlank() ? "[" + customMessage + "] " : "";
            String modeStr = expectSatisfaction ? "satisfy" : "not violate";
            String err = prefix + "Expected page to " + modeStr + " AI condition: \"" + condition + "\". Reason: "
                    + result.reason();

            if (soft) {
                SoftAssertionCollector target = collector != null ? collector : SoftAssertions.get();
                target.that(false, err);
            } else {
                throw new AssertionError(err);
            }
        }
    }

    // ------------------------------------------------------------------
    // Internal poll
    // ------------------------------------------------------------------

    private <T> void poll(ExpectedCondition<T> condition, String failMessage) {
        boolean negate = consumeNegation();
        Duration timeout = customTimeout != null
                ? customTimeout
                : (TestFlyContext.getConfig() != null && TestFlyContext.getConfig().getTimeouts() != null
                        ? Duration.ofSeconds(TestFlyContext.getConfig().getTimeouts().getExplicit())
                        : Duration.ofSeconds(10));
        WebDriver currentDriver = driver != null ? driver : DriverManager.getDriver();
        String message = negate ? negateMessage(failMessage) : failMessage;
        String fullMessage = customMessage != null && !customMessage.isBlank()
                ? "[" + customMessage + "] " + message
                : message;
        try {
            new WebDriverWait(currentDriver, timeout).until(negate ? negated(condition) : condition);
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
            String err = fullMessage + " (" + (negate ? "condition still met, " : "") + "actual title: ["
                    + actualTitle + "], actual url: [" + actualUrl + "], timeout: " + timeoutStr + ")";
            if (soft) {
                SoftAssertionCollector target = collector != null ? collector : SoftAssertions.get();
                target.that(false, err);
            } else {
                throw new AssertionError(err, e);
            }
        }
    }

    /**
     * Inverts a condition: met while {@code condition} yields {@code null} or
     * {@code false}, the values {@link WebDriverWait} treats as "not yet".
     */
    private static <T> ExpectedCondition<Boolean> negated(ExpectedCondition<T> condition) {
        return d -> {
            T result = condition.apply(d);
            return result == null || Boolean.FALSE.equals(result) ? Boolean.TRUE : null;
        };
    }

    /** Logs an assertion step, marking it when the assertion is negated. */
    private void step(String message) {
        StepLogger.step(negated ? "[not] " + message : message);
    }

    /** Returns whether the current assertion is negated and resets the flag. */
    private boolean consumeNegation() {
        boolean wasNegated = negated;
        negated = false;
        return wasNegated;
    }

    private static String negateMessage(String failMessage) {
        return failMessage.startsWith("Expected ")
                ? "Expected NOT " + failMessage.substring("Expected ".length())
                : "NOT: " + failMessage;
    }
}
