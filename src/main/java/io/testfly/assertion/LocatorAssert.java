package io.testfly.assertion;

import io.testfly.api.TestFlyApi;
import io.testfly.driver.DriverManager;
import io.testfly.internal.TestFlyContext;
import io.testfly.locator.Locator;
import io.testfly.steps.StepLogger;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import io.testfly.ai.DomPruner;
import io.testfly.assertion.ai.AiAssertEngine;

/**
 * Fluent, auto-retrying assertion for a specific locator.
 *
 * <p>
 * Every assertion polls via {@link WebDriverWait} until the condition is true
 * or the configured {@code timeouts.explicit} is exceeded — just like
 * Playwright's
 * {@code expect(locator).toBeVisible()}.
 *
 * <p>
 * Obtain an instance via {@link SeleniumAssert#assertThat(By)} or
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
        this.by = by;
        this.description = description;
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
     * {@link SoftAssertions} rather than throwing an immediate
     * {@link AssertionError}.
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
            try {
                List<WebElement> els = driver.findElements(by);
                if (els.isEmpty())
                    return null;
                WebElement el = els.getFirst();
                return el.isDisplayed() && el.isEnabled() ? true : null;
            } catch (StaleElementReferenceException e) {
                return null;
            }
        }, "Expected element to be enabled: " + description);
        return this;
    }

    /** Asserts element is present but disabled — retries until timeout. */
    public LocatorAssert isDisabled() {
        StepLogger.step("Assert disabled: " + description);
        poll(driver -> {
            try {
                List<WebElement> els = driver.findElements(by);
                if (els.isEmpty())
                    return null;
                WebElement el = els.getFirst();
                return el.isDisplayed() && !el.isEnabled() ? true : null;
            } catch (StaleElementReferenceException e) {
                return null;
            }
        }, "Expected element to be disabled: " + description);
        return this;
    }

    /** Asserts a checkbox or radio button is checked — retries until timeout. */
    public LocatorAssert isChecked() {
        StepLogger.step("Assert checked: " + description);
        poll(driver -> {
            try {
                List<WebElement> els = driver.findElements(by);
                if (els.isEmpty())
                    return null;
                return els.getFirst().isSelected() ? true : null;
            } catch (StaleElementReferenceException e) {
                return null;
            }
        }, "Expected element to be checked: " + description);
        return this;
    }

    // ------------------------------------------------------------------
    // Text
    // ------------------------------------------------------------------

    /**
     * Asserts the element's visible text equals {@code expected} (trimmed) —
     * retries until timeout.
     */
    public LocatorAssert hasText(String expected) {
        StepLogger.step("Assert text '" + expected + "' for: " + description);
        poll(ExpectedConditions.textToBe(by, expected),
                "Expected text [" + expected + "] for: " + description);
        return this;
    }

    /**
     * Asserts the element's visible text contains {@code fragment} — retries until
     * timeout.
     */
    public LocatorAssert containsText(String fragment) {
        StepLogger.step("Assert contains text '" + fragment + "' for: " + description);
        poll(ExpectedConditions.textToBePresentInElementLocated(by, fragment),
                "Expected text to contain [" + fragment + "] for: " + description);
        return this;
    }

    // ------------------------------------------------------------------
    // Attributes & CSS
    // ------------------------------------------------------------------

    /**
     * Asserts the element's {@code value} attribute equals {@code expected} —
     * retries until timeout.
     */
    public LocatorAssert hasValue(String expected) {
        StepLogger.step("Assert value '" + expected + "' for: " + description);
        poll(ExpectedConditions.attributeToBe(by, "value", expected),
                "Expected value [" + expected + "] for: " + description);
        return this;
    }

    /**
     * Asserts the element has a specific attribute value — retries until timeout.
     */
    public LocatorAssert hasAttribute(String attribute, String expected) {
        StepLogger.step("Assert attribute " + attribute + "=" + expected + " for: " + description);
        poll(ExpectedConditions.attributeToBe(by, attribute, expected),
                "Expected attribute [" + attribute + "=" + expected + "] for: " + description);
        return this;
    }

    /**
     * Asserts the element has the specified attribute present (regardless of its
     * value) — retries until timeout.
     */
    public LocatorAssert hasAttribute(String attribute) {
        StepLogger.step("Assert attribute '" + attribute + "' exists for: " + description);
        poll(driver -> {
            try {
                List<WebElement> els = driver.findElements(by);
                if (els.isEmpty())
                    return null;
                return els.getFirst().getAttribute(attribute) != null ? true : null;
            } catch (StaleElementReferenceException e) {
                return null;
            }
        }, "Expected attribute [" + attribute + "] to exist for: " + description);
        return this;
    }

    /**
     * Asserts the element has the specified CSS property value — retries until
     * timeout.
     */
    public LocatorAssert hasCssValue(String propertyName, String expectedValue) {
        StepLogger.step("Assert CSS " + propertyName + "='" + expectedValue + "' for: " + description);
        poll(driver -> {
            try {
                List<WebElement> els = driver.findElements(by);
                if (els.isEmpty())
                    return null;
                String val = els.getFirst().getCssValue(propertyName);
                return expectedValue != null && expectedValue.equals(val) ? true : null;
            } catch (StaleElementReferenceException e) {
                return null;
            }
        }, "Expected CSS property [" + propertyName + "='" + expectedValue + "'] for: " + description);
        return this;
    }

    /**
     * Asserts the element is currently focused (the active element in the document)
     * — retries until timeout.
     */
    public LocatorAssert isFocused() {
        StepLogger.step("Assert focused: " + description);
        poll(driver -> {
            try {
                List<WebElement> els = driver.findElements(by);
                if (els.isEmpty())
                    return null;
                WebElement active = driver.switchTo().activeElement();
                return els.getFirst().equals(active) ? true : null;
            } catch (StaleElementReferenceException e) {
                return null;
            }
        }, "Expected element to be focused: " + description);
        return this;
    }

    /** Asserts the element has the given CSS class — retries until timeout. */
    public LocatorAssert hasClass(String className) {
        StepLogger.step("Assert has class '" + className + "' for: " + description);
        poll(driver -> {
            try {
                List<WebElement> els = driver.findElements(by);
                if (els.isEmpty())
                    return null;
                String classes = els.getFirst().getAttribute("class");
                if (classes == null)
                    return null;
                for (String cls : classes.split("\\s+")) {
                    if (cls.equals(className))
                        return true;
                }
                return null;
            } catch (StaleElementReferenceException e) {
                return null;
            }
        }, "Expected element to have class [" + className + "]: " + description);
        return this;
    }

    // ------------------------------------------------------------------
    // Count
    // ------------------------------------------------------------------

    /**
     * Asserts the number of matching elements equals {@code expected} — retries
     * until timeout.
     */
    public LocatorAssert count(int expected) {
        StepLogger.step("Assert count " + expected + " for: " + description);
        poll(ExpectedConditions.numberOfElementsToBe(by, expected),
                "Expected " + expected + " element(s) for: " + description);
        return this;
    }

    /**
     * Asserts at least {@code min} elements match — retries until timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert countAtLeast(int min) {
        StepLogger.step("Assert count >= " + min + " for: " + description);
        poll(driver -> driver.findElements(by).size() >= min ? true : null,
                "Expected at least " + min + " element(s) for: " + description);
        return this;
    }

    /**
     * Asserts at most {@code max} elements match — retries until timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert countAtMost(int max) {
        StepLogger.step("Assert count <= " + max + " for: " + description);
        poll(driver -> driver.findElements(by).size() <= max ? true : null,
                "Expected at most " + max + " element(s) for: " + description);
        return this;
    }

    /**
     * Asserts at least one element matches, whether or not it is visible — retries
     * until timeout.
     *
     * <p>
     * Note: when asserting on a {@link Locator}, only visible elements are matched
     * (see {@link SeleniumAssert#assertThat(Locator)}); use a {@link By} to include
     * hidden elements.
     *
     * @since 1.1.0
     */
    public LocatorAssert exists() {
        StepLogger.step("Assert exists: " + description);
        poll(driver -> driver.findElements(by).isEmpty() ? null : true,
                "Expected element to exist: " + description);
        return this;
    }

    /**
     * Asserts no element matches — retries until timeout. Unlike
     * {@link #isHidden()}, an element that is present but invisible fails this
     * assertion (when asserting on a {@link By}).
     *
     * @since 1.1.0
     */
    public LocatorAssert doesNotExist() {
        StepLogger.step("Assert does not exist: " + description);
        poll(driver -> driver.findElements(by).isEmpty() ? true : null,
                "Expected element not to exist: " + description);
        return this;
    }

    // ------------------------------------------------------------------
    // Value & form input
    // ------------------------------------------------------------------

    /**
     * Asserts the element's current value is not empty (any value) — retries until
     * timeout.
     *
     * <pre>
     * assertThat(passwordField).hasValue();
     * </pre>
     *
     * @since 1.1.0
     */
    public LocatorAssert hasValue() {
        pollFirst("Assert has a value: " + description,
                el -> !isNullOrEmpty(valueOf(el)),
                "Expected a non-empty value for: " + description);
        return this;
    }

    /**
     * Asserts the element's current value is empty — retries until timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert hasEmptyValue() {
        pollFirst("Assert empty value: " + description,
                el -> isNullOrEmpty(valueOf(el)),
                "Expected an empty value for: " + description);
        return this;
    }

    /**
     * Asserts the element's current value contains {@code fragment} — retries until
     * timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert containsValue(String fragment) {
        pollFirst("Assert value contains '" + fragment + "' for: " + description,
                el -> {
                    String value = valueOf(el);
                    return value != null && value.contains(fragment);
                },
                "Expected value to contain [" + fragment + "] for: " + description);
        return this;
    }

    /**
     * Asserts the element's current value fully matches the given regular
     * expression — retries until timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert valueMatches(String regex) {
        Pattern pattern = Pattern.compile(regex);
        pollFirst("Assert value matches /" + regex + "/ for: " + description,
                el -> {
                    String value = valueOf(el);
                    return value != null && pattern.matcher(value).matches();
                },
                "Expected value to match [" + regex + "] for: " + description);
        return this;
    }

    /**
     * Asserts the input masks its content ({@code type="password"}) — retries until
     * timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert isPasswordHidden() {
        pollFirst("Assert password hidden: " + description,
                el -> "password".equalsIgnoreCase(el.getDomProperty("type")),
                "Expected password to be hidden (type=password) for: " + description);
        return this;
    }

    /**
     * Asserts the input shows its content in plain text ({@code type} is not
     * {@code password}), e.g. after a "show password" toggle — retries until
     * timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert isPasswordVisible() {
        pollFirst("Assert password visible: " + description,
                el -> {
                    String type = el.getDomProperty("type");
                    return type != null && !"password".equalsIgnoreCase(type);
                },
                "Expected password to be visible (type is not password) for: " + description);
        return this;
    }

    /**
     * Asserts the element's {@code type} equals {@code expected} (case-insensitive)
     * — retries until timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert hasType(String expected) {
        pollFirst("Assert type '" + expected + "' for: " + description,
                el -> expected.equalsIgnoreCase(el.getDomProperty("type")),
                "Expected type [" + expected + "] for: " + description);
        return this;
    }

    /**
     * Asserts the element's {@code placeholder} equals {@code expected} — retries
     * until timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert hasPlaceholder(String expected) {
        pollFirst("Assert placeholder '" + expected + "' for: " + description,
                el -> expected.equals(el.getDomAttribute("placeholder")),
                "Expected placeholder [" + expected + "] for: " + description);
        return this;
    }

    /**
     * Asserts the element's {@code maxlength} equals {@code expected} — retries
     * until timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert hasMaxLength(int expected) {
        pollFirst("Assert maxlength " + expected + " for: " + description,
                el -> String.valueOf(expected).equals(el.getDomAttribute("maxlength")),
                "Expected maxlength [" + expected + "] for: " + description);
        return this;
    }

    /**
     * Asserts the element is visible, enabled and not read-only — retries until
     * timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert isEditable() {
        pollFirst("Assert editable: " + description,
                el -> el.isDisplayed() && el.isEnabled() && el.getDomAttribute("readonly") == null,
                "Expected element to be editable: " + description);
        return this;
    }

    /**
     * Asserts the element carries the {@code readonly} attribute — retries until
     * timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert isReadOnly() {
        pollFirst("Assert read-only: " + description,
                el -> el.getDomAttribute("readonly") != null,
                "Expected element to be read-only: " + description);
        return this;
    }

    /**
     * Asserts the element is required ({@code required} attribute or
     * {@code aria-required="true"}) — retries until timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert isRequired() {
        pollFirst("Assert required: " + description,
                el -> el.getDomAttribute("required") != null
                        || "true".equalsIgnoreCase(el.getDomAttribute("aria-required")),
                "Expected element to be required: " + description);
        return this;
    }

    /**
     * Asserts the form control passes HTML5 constraint validation
     * ({@code checkValidity()}) — retries until timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert isValid() {
        pollFirst("Assert valid: " + description,
                el -> Boolean.TRUE.equals(js(el, "return arguments[0].checkValidity();")),
                "Expected element to be valid: " + description);
        return this;
    }

    /**
     * Asserts the form control fails HTML5 constraint validation
     * ({@code checkValidity()}) — retries until timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert isInvalid() {
        pollFirst("Assert invalid: " + description,
                el -> Boolean.FALSE.equals(js(el, "return arguments[0].checkValidity();")),
                "Expected element to be invalid: " + description);
        return this;
    }

    /**
     * Asserts the browser's HTML5 {@code validationMessage} contains
     * {@code fragment} — retries until timeout. The message text is
     * browser/locale-specific.
     *
     * @since 1.1.0
     */
    public LocatorAssert hasValidationMessage(String fragment) {
        pollFirst("Assert validation message contains '" + fragment + "' for: " + description,
                el -> {
                    Object msg = js(el, "return arguments[0].validationMessage;");
                    return msg != null && msg.toString().contains(fragment);
                },
                "Expected validation message to contain [" + fragment + "] for: " + description);
        return this;
    }

    // ------------------------------------------------------------------
    // Text (extended)
    // ------------------------------------------------------------------

    /**
     * Asserts the element's trimmed visible text fully matches the given regular
     * expression — retries until timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert hasTextMatching(String regex) {
        Pattern pattern = Pattern.compile(regex);
        pollFirst("Assert text matches /" + regex + "/ for: " + description,
                el -> pattern.matcher(el.getText().trim()).matches(),
                "Expected text to match [" + regex + "] for: " + description);
        return this;
    }

    /**
     * Asserts the element's trimmed visible text equals {@code expected}, ignoring
     * case — retries until timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert hasTextIgnoringCase(String expected) {
        pollFirst("Assert text '" + expected + "' (ignoring case) for: " + description,
                el -> el.getText().trim().equalsIgnoreCase(expected),
                "Expected text [" + expected + "] (ignoring case) for: " + description);
        return this;
    }

    /**
     * Asserts the trimmed texts of all matching elements equal {@code expected}, in
     * order — retries until timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert hasTexts(String... expected) {
        List<String> wanted = Arrays.asList(expected);
        StepLogger.step("Assert texts " + wanted + " for: " + description);
        poll(driver -> {
            try {
                return wanted.equals(textsOf(driver.findElements(by))) ? true : null;
            } catch (StaleElementReferenceException e) {
                return null;
            }
        }, "Expected texts " + wanted + " for: " + description);
        return this;
    }

    /**
     * Asserts every given text appears among the trimmed texts of the matching
     * elements, in any order — retries until timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert containsTexts(String... expected) {
        List<String> wanted = Arrays.asList(expected);
        StepLogger.step("Assert contains texts " + wanted + " for: " + description);
        poll(driver -> {
            try {
                return textsOf(driver.findElements(by)).containsAll(wanted) ? true : null;
            } catch (StaleElementReferenceException e) {
                return null;
            }
        }, "Expected texts to contain " + wanted + " for: " + description);
        return this;
    }

    // ------------------------------------------------------------------
    // Selection
    // ------------------------------------------------------------------

    /**
     * Asserts a checkbox or radio button is not checked — retries until timeout.
     * The element must exist.
     *
     * @since 1.1.0
     */
    public LocatorAssert isNotChecked() {
        pollFirst("Assert not checked: " + description,
                el -> !el.isSelected(),
                "Expected element not to be checked: " + description);
        return this;
    }

    /**
     * Asserts the element (option, checkbox, radio) is selected — retries until
     * timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert isSelected() {
        pollFirst("Assert selected: " + description,
                WebElement::isSelected,
                "Expected element to be selected: " + description);
        return this;
    }

    /**
     * Asserts the first selected option of a {@code <select>} has the given visible
     * text — retries until timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert hasSelectedOption(String visibleText) {
        pollFirst("Assert selected option '" + visibleText + "' for: " + description,
                el -> {
                    List<WebElement> selected = new Select(el).getAllSelectedOptions();
                    return !selected.isEmpty() && visibleText.equals(selected.getFirst().getText().trim());
                },
                "Expected selected option [" + visibleText + "] for: " + description);
        return this;
    }

    /**
     * Asserts the option texts of a {@code <select>} equal {@code expected}, in
     * order — retries until timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert hasOptions(String... expected) {
        List<String> wanted = Arrays.asList(expected);
        pollFirst("Assert options " + wanted + " for: " + description,
                el -> wanted.equals(textsOf(new Select(el).getOptions())),
                "Expected options " + wanted + " for: " + description);
        return this;
    }

    /**
     * Asserts the element is at least partly inside the viewport — retries until
     * timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert isInViewport() {
        pollFirst("Assert in viewport: " + description,
                el -> Boolean.TRUE.equals(js(el, IN_VIEWPORT_JS)),
                "Expected element to be in the viewport: " + description);
        return this;
    }

    // ------------------------------------------------------------------
    // Accessibility & identity
    // ------------------------------------------------------------------

    /**
     * Asserts the element's computed ARIA role equals {@code expected} — retries
     * until timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert hasRole(String expected) {
        pollFirst("Assert role '" + expected + "' for: " + description,
                el -> expected.equalsIgnoreCase(el.getAriaRole()),
                "Expected role [" + expected + "] for: " + description);
        return this;
    }

    /**
     * Asserts the element's computed accessible name equals {@code expected}
     * (trimmed) — retries until timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert hasAccessibleName(String expected) {
        pollFirst("Assert accessible name '" + expected + "' for: " + description,
                el -> {
                    String name = el.getAccessibleName();
                    return name != null && expected.equals(name.trim());
                },
                "Expected accessible name [" + expected + "] for: " + description);
        return this;
    }

    /**
     * Asserts the element's {@code id} equals {@code expected} — retries until
     * timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert hasId(String expected) {
        pollFirst("Assert id '" + expected + "' for: " + description,
                el -> expected.equals(el.getDomAttribute("id")),
                "Expected id [" + expected + "] for: " + description);
        return this;
    }

    // ------------------------------------------------------------------
    // Negations — the element must exist; only its property must not match.
    // Use doesNotExist() / isHidden() to assert absence.
    // ------------------------------------------------------------------

    /**
     * Asserts the element's trimmed visible text does not equal {@code unexpected}
     * — retries until timeout. The element must exist.
     *
     * @since 1.1.0
     */
    public LocatorAssert doesNotHaveText(String unexpected) {
        pollFirst("Assert text is not '" + unexpected + "' for: " + description,
                el -> !el.getText().trim().equals(unexpected),
                "Expected text not to be [" + unexpected + "] for: " + description);
        return this;
    }

    /**
     * Asserts the element's visible text does not contain {@code fragment} —
     * retries until timeout. The element must exist.
     *
     * @since 1.1.0
     */
    public LocatorAssert doesNotContainText(String fragment) {
        pollFirst("Assert text does not contain '" + fragment + "' for: " + description,
                el -> !el.getText().contains(fragment),
                "Expected text not to contain [" + fragment + "] for: " + description);
        return this;
    }

    /**
     * Asserts the element's current value does not equal {@code unexpected} —
     * retries until timeout. The element must exist.
     *
     * @since 1.1.0
     */
    public LocatorAssert doesNotHaveValue(String unexpected) {
        pollFirst("Assert value is not '" + unexpected + "' for: " + description,
                el -> !Objects.equals(valueOf(el), unexpected),
                "Expected value not to be [" + unexpected + "] for: " + description);
        return this;
    }

    /**
     * Asserts the element does not have the given CSS class — retries until
     * timeout. The element must exist.
     *
     * @since 1.1.0
     */
    public LocatorAssert doesNotHaveClass(String className) {
        pollFirst("Assert does not have class '" + className + "' for: " + description,
                el -> {
                    String classes = el.getDomAttribute("class");
                    return classes == null || !Arrays.asList(classes.trim().split("\\s+")).contains(className);
                },
                "Expected element not to have class [" + className + "]: " + description);
        return this;
    }

    /**
     * Asserts the element does not carry the given attribute — retries until
     * timeout. The element must exist.
     *
     * @since 1.1.0
     */
    public LocatorAssert doesNotHaveAttribute(String attribute) {
        pollFirst("Assert attribute '" + attribute + "' absent for: " + description,
                el -> el.getDomAttribute(attribute) == null,
                "Expected attribute [" + attribute + "] to be absent for: " + description);
        return this;
    }

    /**
     * Asserts the given attribute does not have the value {@code unexpected} (an
     * absent attribute passes) — retries until timeout. The element must exist.
     *
     * @since 1.1.0
     */
    public LocatorAssert doesNotHaveAttribute(String attribute, String unexpected) {
        pollFirst("Assert attribute " + attribute + "!=" + unexpected + " for: " + description,
                el -> !Objects.equals(el.getDomAttribute(attribute), unexpected),
                "Expected attribute [" + attribute + "] not to be [" + unexpected + "] for: " + description);
        return this;
    }

    /**
     * Asserts the element is not the document's active element — retries until
     * timeout. The element must exist.
     *
     * @since 1.1.0
     */
    public LocatorAssert isNotFocused() {
        pollFirst("Assert not focused: " + description,
                el -> !el.equals(DriverManager.getDriver().switchTo().activeElement()),
                "Expected element not to be focused: " + description);
        return this;
    }

    // ------------------------------------------------------------------
    // Semantic AI Assertions
    // ------------------------------------------------------------------

    /**
     * Asserts that the element semantically satisfies the given natural language
     * condition.
     *
     * <p>
     * Anti-throttle guarantee: does not poll repeatedly. Extracts element HTML and
     * performs a bounded
     * LLM reasoning evaluation.
     *
     * @param expectedCondition natural language expectation (e.g. "Displays active
     *                          subscription status")
     * @return this assertion for chaining
     */
    public LocatorAssert satisfiesAi(String expectedCondition) {
        StepLogger.step("Assert element satisfies condition (AI): \"" + expectedCondition + "\" for: " + description);
        evaluateAi(expectedCondition, true);
        return this;
    }

    /**
     * Asserts that the element does NOT violate or contain the given forbidden
     * condition.
     *
     * @param forbiddenCondition natural language forbidden condition (e.g.
     *                           "Contains error banner or expired tag")
     * @return this assertion for chaining
     */
    public LocatorAssert violatesAi(String forbiddenCondition) {
        StepLogger.step(
                "Assert element does not violate condition (AI): \"" + forbiddenCondition + "\" for: " + description);
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
            rawHtml = els.getFirst().getAttribute("outerHTML");
        } catch (Exception e) {
            rawHtml = els.getFirst().getText();
        }

        String pruned = DomPruner.prune(rawHtml);
        AiAssertEngine.AiAssertionResult result = AiAssertEngine.verify(driver, pruned, condition, expectSatisfaction);

        if (!result.isPassed()) {
            String prefix = customMessage != null && !customMessage.isBlank() ? "[" + customMessage + "] " : "";
            String modeStr = expectSatisfaction ? "satisfy" : "not violate";
            String err = prefix + "Expected element [" + description + "] to " + modeStr + " AI condition: \""
                    + condition + "\". Reason: " + result.reason();

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

    private static final String IN_VIEWPORT_JS = "const r = arguments[0].getBoundingClientRect();"
            + "const h = window.innerHeight || document.documentElement.clientHeight;"
            + "const w = window.innerWidth || document.documentElement.clientWidth;"
            + "return r.width > 0 && r.height > 0 && r.bottom > 0 && r.right > 0 && r.top < h && r.left < w;";

    /**
     * Polls until the first matching element satisfies {@code check}. A missing or
     * stale element counts as "not yet".
     */
    private void pollFirst(String stepMessage, Predicate<WebElement> check, String failMessage) {
        StepLogger.step(stepMessage);
        poll(driver -> {
            try {
                List<WebElement> els = driver.findElements(by);
                if (els.isEmpty())
                    return null;
                return check.test(els.getFirst()) ? true : null;
            } catch (StaleElementReferenceException e) {
                return null;
            }
        }, failMessage);
    }

    private static String valueOf(WebElement el) {
        return el.getDomProperty("value");
    }

    private static boolean isNullOrEmpty(String s) {
        return s == null || s.isEmpty();
    }

    private static List<String> textsOf(List<WebElement> els) {
        List<String> texts = new ArrayList<>(els.size());
        for (WebElement el : els) {
            texts.add(el.getText().trim());
        }
        return texts;
    }

    /** Runs a script against the element; a script error counts as "not yet". */
    private static Object js(WebElement el, String script) {
        try {
            return ((JavascriptExecutor) DriverManager.getDriver()).executeScript(script, el);
        } catch (JavascriptException e) {
            return null;
        }
    }

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
