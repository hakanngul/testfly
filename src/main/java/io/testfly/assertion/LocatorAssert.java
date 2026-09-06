package io.testfly.assertion;

import io.testfly.api.TestFlyApi;
import io.testfly.driver.DriverManager;
import io.testfly.internal.TestFlyContext;
import io.testfly.locator.Locator;
import io.testfly.steps.StepLogger;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.TimeoutException;

import java.time.Duration;
import java.util.List;
import io.testfly.ai.DomPruner;
import io.testfly.assertion.ai.AiAssertEngine;

/**
 * Fluent, auto-retrying assertion for a specific locator.
 *
 * <p>Every assertion polls via {@link WebDriverWait} until the condition is true
 * or the configured {@code timeouts.explicit} is exceeded — just like Playwright's
 * {@code expect(locator).toBeVisible()}.
 *
 * <p>Obtain an instance via {@link SeleniumAssert#assertThat(By)} or
 * {@link SeleniumAssert#assertThat(Locator)}.
 *
 * <pre>
 * assertThat(By.id("status")).hasText("Active");
 * assertThat(By.cssSelector(".error")).isVisible();
 * assertThat($("button").withText("Submit")).isEnabled();
 * </pre>
 */
@TestFlyApi(since = "1.4.0")
public final class LocatorAssert {

    private final By by;
    private final String description;
    private Duration customTimeout;
    private String customMessage;
    private boolean soft;
    private final SoftAssertionCollector collector;

    LocatorAssert(By by, String description) {
        this(by, description, false, null);
    }

    LocatorAssert(By by, String description, boolean soft) {
        this(by, description, soft, null);
    }

    LocatorAssert(By by, String description, SoftAssertionCollector collector) {
        this(by, description, true, collector);
    }

    LocatorAssert(By by, String description, boolean soft, SoftAssertionCollector collector) {
        this.by          = by;
        this.description = description;
        this.soft        = soft;
        this.collector   = collector;
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
    public LocatorAssert within(Duration timeout) {
        this.customTimeout = timeout;
        return this;
    }

    /**
     * Overrides the wait timeout in seconds for this assertion.
     *
     * @param timeoutSeconds custom timeout in seconds
     * @return this assertion for chaining
     */
    public LocatorAssert within(int timeoutSeconds) {
        this.customTimeout = Duration.ofSeconds(timeoutSeconds);
        return this;
    }

    /**
     * Attaches a custom description or failure message to this assertion.
     *
     * @param message custom message displayed upon failure
     * @return this assertion for chaining
     */
    public LocatorAssert as(String message) {
        this.customMessage = message;
        return this;
    }

    /**
     * Alias for {@link #as(String)}.
     */
    public LocatorAssert describedAs(String message) {
        return as(message);
    }

    /**
     * Switches this assertion into soft mode — failures are collected in
     * {@link SoftAssertions} rather than throwing an immediate {@link AssertionError}.
     *
     * @return this assertion for chaining
     */
    public LocatorAssert softly() {
        this.soft = true;
        return this;
    }

    // ------------------------------------------------------------------
    // Visibility
    // ------------------------------------------------------------------

    /** Asserts element is present and visible — retries until timeout. */
    public LocatorAssert isVisible() {
        StepLogger.step("Assert visible: " + description);
        poll(ExpectedConditions.visibilityOfElementLocated(by),
                "Expected element to be visible: " + description);
        return this;
    }

    /** Asserts element is absent or not visible — retries until timeout. */
    public LocatorAssert isHidden() {
        StepLogger.step("Assert hidden: " + description);
        poll(ExpectedConditions.invisibilityOfElementLocated(by),
                "Expected element to be hidden: " + description);
        return this;
    }

    // ------------------------------------------------------------------
    // Interactability
    // ------------------------------------------------------------------

    /** Asserts element is present, visible, and enabled — retries until timeout. */
    public LocatorAssert isEnabled() {
        StepLogger.step("Assert enabled: " + description);
        poll(driver -> {
            List<WebElement> els = driver.findElements(by);
            if (els.isEmpty()) return null;
            WebElement el = els.get(0);
            return el.isDisplayed() && el.isEnabled() ? true : null;
        }, "Expected element to be enabled: " + description);
        return this;
    }

    /** Asserts element is present but disabled — retries until timeout. */
    public LocatorAssert isDisabled() {
        StepLogger.step("Assert disabled: " + description);
        poll(driver -> {
            List<WebElement> els = driver.findElements(by);
            if (els.isEmpty()) return null;
            WebElement el = els.get(0);
            return el.isDisplayed() && !el.isEnabled() ? true : null;
        }, "Expected element to be disabled: " + description);
        return this;
    }

    /** Asserts a checkbox or radio button is checked — retries until timeout. */
    public LocatorAssert isChecked() {
        StepLogger.step("Assert checked: " + description);
        poll(driver -> {
            List<WebElement> els = driver.findElements(by);
            if (els.isEmpty()) return null;
            return els.get(0).isSelected() ? true : null;
        }, "Expected element to be checked: " + description);
        return this;
    }

    // ------------------------------------------------------------------
    // Text
    // ------------------------------------------------------------------

    /** Asserts the element's visible text equals {@code expected} (trimmed) — retries until timeout. */
    public LocatorAssert hasText(String expected) {
        StepLogger.step("Assert text '" + expected + "' for: " + description);
        poll(ExpectedConditions.textToBe(by, expected),
                "Expected text [" + expected + "] for: " + description);
        return this;
    }

    /** Asserts the element's visible text contains {@code fragment} — retries until timeout. */
    public LocatorAssert containsText(String fragment) {
        StepLogger.step("Assert contains text '" + fragment + "' for: " + description);
        poll(ExpectedConditions.textToBePresentInElementLocated(by, fragment),
                "Expected text to contain [" + fragment + "] for: " + description);
        return this;
    }

    // ------------------------------------------------------------------
    // Attributes & CSS
    // ------------------------------------------------------------------

    /** Asserts the element's {@code value} attribute equals {@code expected} — retries until timeout. */
    public LocatorAssert hasValue(String expected) {
        StepLogger.step("Assert value '" + expected + "' for: " + description);
        poll(ExpectedConditions.attributeToBe(by, "value", expected),
                "Expected value [" + expected + "] for: " + description);
        return this;
    }

    /** Asserts the element has a specific attribute value — retries until timeout. */
    public LocatorAssert hasAttribute(String attribute, String expected) {
        StepLogger.step("Assert attribute " + attribute + "=" + expected + " for: " + description);
        poll(ExpectedConditions.attributeToBe(by, attribute, expected),
                "Expected attribute [" + attribute + "=" + expected + "] for: " + description);
        return this;
    }

    /** Asserts the element has the specified attribute present (regardless of its value) — retries until timeout. */
    public LocatorAssert hasAttribute(String attribute) {
        StepLogger.step("Assert attribute '" + attribute + "' exists for: " + description);
        poll(driver -> {
            List<WebElement> els = driver.findElements(by);
            if (els.isEmpty()) return null;
            return els.get(0).getAttribute(attribute) != null ? true : null;
        }, "Expected attribute [" + attribute + "] to exist for: " + description);
        return this;
    }

    /** Asserts the element has the specified CSS property value — retries until timeout. */
    public LocatorAssert hasCssValue(String propertyName, String expectedValue) {
        StepLogger.step("Assert CSS " + propertyName + "='" + expectedValue + "' for: " + description);
        poll(driver -> {
            List<WebElement> els = driver.findElements(by);
            if (els.isEmpty()) return null;
            String val = els.get(0).getCssValue(propertyName);
            return expectedValue != null && expectedValue.equals(val) ? true : null;
        }, "Expected CSS property [" + propertyName + "='" + expectedValue + "'] for: " + description);
        return this;
    }

    /** Asserts the element is currently focused (the active element in the document) — retries until timeout. */
    public LocatorAssert isFocused() {
        StepLogger.step("Assert focused: " + description);
        poll(driver -> {
            List<WebElement> els = driver.findElements(by);
            if (els.isEmpty()) return null;
            WebElement active = driver.switchTo().activeElement();
            return els.get(0).equals(active) ? true : null;
        }, "Expected element to be focused: " + description);
        return this;
    }

    /** Asserts the element has the given CSS class — retries until timeout. */
    public LocatorAssert hasClass(String className) {
        StepLogger.step("Assert has class '" + className + "' for: " + description);
        poll(driver -> {
            List<WebElement> els = driver.findElements(by);
            if (els.isEmpty()) return null;
            String classes = els.get(0).getAttribute("class");
            if (classes == null) return null;
            for (String cls : classes.split("\\s+")) {
                if (cls.equals(className)) return true;
            }
            return null;
        }, "Expected element to have class [" + className + "]: " + description);
        return this;
    }

    // ------------------------------------------------------------------
    // Count
    // ------------------------------------------------------------------

    /** Asserts the number of matching elements equals {@code expected} — retries until timeout. */
    public LocatorAssert count(int expected) {
        StepLogger.step("Assert count " + expected + " for: " + description);
        poll(ExpectedConditions.numberOfElementsToBe(by, expected),
                "Expected " + expected + " element(s) for: " + description);
        return this;
    }

    // ------------------------------------------------------------------
    // Semantic AI Assertions
    // ------------------------------------------------------------------

    /**
     * Asserts that the element semantically satisfies the given natural language condition.
     *
     * <p>Anti-throttle guarantee: does not poll repeatedly. Extracts element HTML and performs a bounded
     * LLM reasoning evaluation.
     *
     * @param expectedCondition natural language expectation (e.g. "Displays active subscription status")
     * @return this assertion for chaining
     */
    public LocatorAssert satisfiesAi(String expectedCondition) {
        StepLogger.step("Assert element satisfies condition (AI): \"" + expectedCondition + "\" for: " + description);
        evaluateAi(expectedCondition, true);
        return this;
    }

    /**
     * Asserts that the element does NOT violate or contain the given forbidden condition.
     *
     * @param forbiddenCondition natural language forbidden condition (e.g. "Contains error banner or expired tag")
     * @return this assertion for chaining
     */
    public LocatorAssert violatesAi(String forbiddenCondition) {
        StepLogger.step("Assert element does not violate condition (AI): \"" + forbiddenCondition + "\" for: " + description);
        evaluateAi(forbiddenCondition, false);
        return this;
    }

    private void evaluateAi(String condition, boolean expectSatisfaction) {
        WebDriver driver = DriverManager.getDriver();
        List<WebElement> els = driver.findElements(by);
        if (els.isEmpty()) {
            String prefix = customMessage != null && !customMessage.isBlank() ? "[" + customMessage + "] " : "";
            String err = prefix + "Cannot evaluate AI condition: element not found for " + description;
            if (soft) {
                SoftAssertionCollector target = collector != null ? collector : SoftAssertions.get();
                target.that(false, err);
            } else {
                throw new AssertionError(err);
            }
            return;
        }

        String rawHtml;
        try {
            rawHtml = els.get(0).getAttribute("outerHTML");
        } catch (Exception e) {
            rawHtml = els.get(0).getText();
        }

        String pruned = DomPruner.prune(rawHtml);
        AiAssertEngine.AiAssertionResult result =
                AiAssertEngine.verify(driver, pruned, condition, expectSatisfaction);

        if (!result.isPassed()) {
            String prefix = customMessage != null && !customMessage.isBlank() ? "[" + customMessage + "] " : "";
            String modeStr = expectSatisfaction ? "satisfy" : "not violate";
            String err = prefix + "Expected element [" + description + "] to " + modeStr + " AI condition: \"" + condition + "\". Reason: " + result.reason();

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
        Duration timeout = customTimeout != null
                ? customTimeout
                : Duration.ofSeconds(TestFlyContext.getConfig().getTimeouts().getExplicit());
        WebDriver driver = DriverManager.getDriver();
        String fullMessage = customMessage != null && !customMessage.isBlank()
                ? "[" + customMessage + "] " + failMessage
                : failMessage;
        try {
            new WebDriverWait(driver, timeout).until(condition);
        } catch (TimeoutException e) {
            String timeoutStr = timeout.toMillis() >= 1000 && timeout.toMillis() % 1000 == 0
                    ? timeout.toSeconds() + "s"
                    : timeout.toMillis() + "ms";
            String err = fullMessage + " (timeout: " + timeoutStr + ")";
            if (soft) {
                SoftAssertionCollector target = collector != null ? collector : SoftAssertions.get();
                target.that(false, err);
            } else {
                throw new AssertionError(err, e);
            }
        }
    }
}
