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
import org.testng.Assert;

import java.time.Duration;
import java.util.List;

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

    LocatorAssert(By by, String description) {
        this.by          = by;
        this.description = description;
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
    // Internal poll
    // ------------------------------------------------------------------

    private <T> void poll(ExpectedCondition<T> condition, String failMessage) {
        int timeout = TestFlyContext.getConfig().getTimeouts().getExplicit();
        WebDriver driver = DriverManager.getDriver();
        try {
            new WebDriverWait(driver, Duration.ofSeconds(timeout)).until(condition);
        } catch (Exception e) {
            Assert.fail(failMessage + " (timeout: " + timeout + "s)");
        }
    }
}
