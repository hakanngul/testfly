package io.testfly.assertion;

import io.testfly.api.TestFlyApi;
import io.testfly.driver.DriverManager;
import io.testfly.internal.TestFlyContext;
import io.testfly.locator.Locator;
import io.testfly.locator.LocatorException;
import io.testfly.steps.StepLogger;
import io.testfly.wait.WaitEngine;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoAlertPresentException;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.UnhandledAlertException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import io.testfly.steps.StepStatus;
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
    // Verified click
    // ------------------------------------------------------------------

    /**
     * Clicks the element and asserts that the click really reached it.
     *
     * <ol>
     * <li>Waits until the element is visible and enabled.</li>
     * <li>Scrolls it to the centre of the viewport and moves the mouse onto
     * it.</li>
     * <li>Waits until no other element (cookie banner, modal, sticky header…)
     * covers its centre.</li>
     * <li>Clicks it and verifies that the {@code click} event was dispatched to
     * the element or one of its descendants.</li>
     * </ol>
     *
     * <p>
     * On success a {@code PASS} step "Clicked: …" is logged. On failure an
     * {@link AssertionError} explains which step failed (in soft mode the failure
     * is recorded and the test continues). If the click navigates away or opens
     * an alert before the event can be read back, the click counts as delivered.
     *
     * <pre>
     * assertThat(loginButton).click();
     * </pre>
     *
     * @return this assertion for chaining
     * @since 1.1.0
     */
    public LocatorAssert click() {
        StepLogger.step("Click (verified): " + description);
        WebDriver driver = DriverManager.getDriver();

        // 1. Visible and enabled (resolved like Locator.click(), incl. self-healing)
        WebElement el = resolveForAction("Click");
        if (el == null)
            return this;

        // 2. Scroll to centre and hover, like a real user
        try {
            ((JavascriptExecutor) driver).executeScript(SCROLL_TO_CENTER_JS, el);
            new Actions(driver).moveToElement(el).perform();
        } catch (WebDriverException e) {
            report("Click not performed: could not move to element " + description + ": " + firstLine(e), e);
            return this;
        }

        // 3. Nothing covers it
        String[] coveredBy = new String[1];
        Boolean uncovered = await(d -> {
            try {
                Object hit = ((JavascriptExecutor) d).executeScript(COVERING_ELEMENT_JS, el);
                coveredBy[0] = hit == null ? null : hit.toString();
                return hit == null ? true : null;
            } catch (StaleElementReferenceException e) {
                coveredBy[0] = "(element became stale)";
                return null;
            }
        }, () -> "Click not performed: " + description + " is covered by " + coveredBy[0]);
        if (uncovered == null)
            return this;

        // 4. Click with a one-shot listener installed
        try {
            ((JavascriptExecutor) driver).executeScript(INSTALL_CLICK_PROBE_JS, el);
            el.click();
        } catch (WebDriverException e) {
            report("Click not performed on " + description + ": " + firstLine(e), e);
            return this;
        }

        // 5. The click event reached the element
        boolean reached;
        if (alertOpen(driver)) {
            reached = true; // running a script now would dismiss the alert the click opened
        } else {
            try {
                reached = Boolean.TRUE.equals(((JavascriptExecutor) driver).executeScript(READ_CLICK_PROBE_JS, el));
            } catch (StaleElementReferenceException | NoSuchElementException | UnhandledAlertException e) {
                reached = true; // the click navigated away, re-rendered, or opened an alert
            }
        }
        if (!reached) {
            report("Click did not reach " + description + " (no click event was received)", null);
            return this;
        }

        StepLogger.step("Clicked: " + description, StepStatus.PASS);
        return this;
    }

    /**
     * Clears the field, types {@code text}, and asserts that the field's value
     * then equals {@code text} exactly.
     *
     * <ol>
     * <li>Resolves the element like {@link Locator#fill(String)} (waiting and
     * self-healing) and waits until it is visible and enabled.</li>
     * <li>Waits until it is not {@code readonly}, then scrolls it to the centre of
     * the viewport.</li>
     * <li>Clears it and types the text, exactly as {@link Locator#type(String)}
     * does.</li>
     * <li>Waits until the value equals {@code text}. This catches values that
     * {@code maxlength}, input masks, or framework state (React, Vue…) changed or
     * reset after typing.</li>
     * </ol>
     *
     * <p>
     * On success a {@code PASS} step "Filled: …" is logged. For
     * {@code type="password"} fields the value is never written to logs or
     * failure messages; only its length is. Fields that reformat their input
     * (e.g. phone masks) fail this assertion; use {@link Locator#fill(String)}
     * followed by {@link #hasValue(String)} with the formatted value instead.
     *
     * <pre>
     * assertThat(userNameField).fill("admin");
     * assertThat(passwordField).fill(password);
     * </pre>
     *
     * @param text the text to type; {@code null} or empty clears the field
     * @return this assertion for chaining
     * @since 1.1.0
     */
    public LocatorAssert fill(String text) {
        StepLogger.step("Fill (verified): " + description);
        return fillAndVerify(text == null ? "" : text, "Fill", "Filled");
    }

    /**
     * Clears the field and asserts that its value is then empty. Same checks as
     * {@link #fill(String)} with an empty text.
     *
     * <pre>
     * assertThat(searchField).clear();
     * </pre>
     *
     * @return this assertion for chaining
     * @since 1.1.0
     */
    public LocatorAssert clear() {
        StepLogger.step("Clear (verified): " + description);
        return fillAndVerify("", "Clear", "Cleared");
    }

    private LocatorAssert fillAndVerify(String text, String action, String pastTense) {
        WebDriver driver = DriverManager.getDriver();

        // 1. Visible and enabled
        WebElement el = resolveForAction(action);
        if (el == null)
            return this;
        boolean secret = isPasswordField(el);

        // 2. Not read-only, then into view
        Boolean writable = await(d -> {
            try {
                return el.getDomAttribute("readonly") == null ? true : null;
            } catch (StaleElementReferenceException e) {
                return null;
            }
        }, () -> action + " not performed: element is read-only: " + description);
        if (writable == null)
            return this;

        // 3. Clear and type, like Locator.type()
        try {
            ((JavascriptExecutor) driver).executeScript(SCROLL_TO_CENTER_JS, el);
            Locator.robustClear(el);
            if (!text.isEmpty()) {
                el.sendKeys(text);
            }
        } catch (WebDriverException e) {
            report(action + " not performed on " + description + ": " + firstLine(e), e);
            return this;
        }

        // 4. The value stuck (re-reads the field if it was re-rendered)
        String[] actual = new String[1];
        Boolean stuck = await(d -> {
            actual[0] = currentValue(d, el);
            if (!text.equals(actual[0]))
                return null;
            // Re-read after a short pause so a value the app resets asynchronously is caught.
            WaitEngine.waitMillis(VALUE_SETTLE_MILLIS);
            actual[0] = currentValue(d, el);
            return text.equals(actual[0]) ? true : null;
        }, () -> action + " did not stick for " + description + ": "
                + describeMismatch(text, actual[0], secret));
        if (stuck == null)
            return this;

        StepLogger.step(pastTense + ": " + description + describeFilled(text, secret), StepStatus.PASS);
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

    /** How long a filled value must stay unchanged to count as stuck. */
    private static final long VALUE_SETTLE_MILLIS = 150;

    private static final String SCROLL_TO_CENTER_JS ="arguments[0].scrollIntoView({block: 'center', inline: 'center'});";

    /**
     * Returns null when the element (or a descendant) is topmost at its centre,
     * otherwise a short description of the element covering it.
     */
    private static final String COVERING_ELEMENT_JS = "const e = arguments[0];"
            + "const r = e.getBoundingClientRect();"
            + "const x = r.left + r.width / 2, y = r.top + r.height / 2;"
            + "const hit = document.elementFromPoint(x, y);"
            + "if (!hit) return 'nothing (centre is outside the viewport)';"
            + "if (hit === e || e.contains(hit)) return null;"
            + "let d = '<' + hit.tagName.toLowerCase();"
            + "if (hit.id) d += \" id='\" + hit.id + \"'\";"
            + "if (typeof hit.className === 'string' && hit.className.trim()) d += \" class='\" + hit.className.trim() + \"'\";"
            + "return d + '>';";

    private static final String INSTALL_CLICK_PROBE_JS = "const e = arguments[0];"
            + "e.__testflyClicked = false;"
            + "e.addEventListener('click', function probe() {"
            + "  e.__testflyClicked = true; e.removeEventListener('click', probe, true);"
            + "}, true);";

    private static final String READ_CLICK_PROBE_JS = "return arguments[0].__testflyClicked === true;";

    /** The {@link Locator} behind this assertion, wrapping a plain {@link By} if needed. */
    private Locator toLocator() {
        if (by instanceof Locator locator)
            return locator;
        if (by instanceof SeleniumAssert.LocatorBy locatorBy)
            return locatorBy.locator();
        return Locator.of(by);
    }

    /**
     * Resolves the element the same way {@link Locator}'s actions do (waiting up
     * to {@code timeouts.explicit}, with self-healing) and waits until it is
     * visible and enabled. Reports the failure and returns {@code null} if not.
     */
    private WebElement resolveForAction(String action) {
        try {
            return WaitEngine.waitForClickable(toLocator().element());
        } catch (LocatorException | WebDriverException e) {
            report(action + " not performed: element not visible and enabled: " + description
                    + " (" + firstLine(e) + ")", e);
            return null;
        }
    }

    private static boolean isPasswordField(WebElement el) {
        try {
            return "password".equalsIgnoreCase(el.getDomProperty("type"));
        } catch (WebDriverException e) {
            return false;
        }
    }

    /** Current value of the field, re-finding it if it was re-rendered. */
    private String currentValue(WebDriver driver, WebElement el) {
        try {
            return valueOrText(el);
        } catch (StaleElementReferenceException e) {
            List<WebElement> els = driver.findElements(by);
            return els.isEmpty() ? null : valueOrText(els.getFirst());
        }
    }

    /** {@code value} for form controls, visible text for e.g. contenteditable elements. */
    private static String valueOrText(WebElement el) {
        String value = el.getDomProperty("value");
        return value != null ? value : el.getText();
    }

    private static String describeMismatch(String expected, String actual, boolean secret) {
        if (secret) {
            return "expected " + expected.length() + " characters but got "
                    + (actual == null ? "none" : actual.length() + "") + " (password value hidden)";
        }
        return "expected value [" + expected + "] but was [" + actual + "]";
    }

    private static String describeFilled(String text, boolean secret) {
        if (text.isEmpty())
            return "";
        return secret ? " (" + text.length() + " characters)" : " with [" + text + "]";
    }

    /** Checks for an open alert without dismissing it. */
    private static boolean alertOpen(WebDriver driver) {
        try {
            WebDriver.TargetLocator target = driver.switchTo();
            return target != null && target.alert() != null;
        } catch (NoAlertPresentException e) {
            return false;
        }
    }

    private static String firstLine(Throwable e) {
        String msg = e.getMessage();
        if (msg == null)
            return e.getClass().getSimpleName();
        int nl = msg.indexOf('\n');
        return nl >= 0 ? msg.substring(0, nl) : msg;
    }

    private static final String IN_VIEWPORT_JS ="const r = arguments[0].getBoundingClientRect();"
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
        await(condition, () -> failMessage);
    }

    /**
     * Waits for {@code condition} and returns its value. On timeout the failure is
     * reported (thrown, or collected in soft mode) and {@code null} is returned.
     */
    private <T> T await(ExpectedCondition<T> condition, java.util.function.Supplier<String> failMessage) {
        Duration timeout = timeout();
        WebDriver driver = DriverManager.getDriver();
        try {
            return new WebDriverWait(driver, timeout).until(condition);
        } catch (TimeoutException e) {
            String timeoutStr = timeout.toMillis() >= 1000 && timeout.toMillis() % 1000 == 0
                    ? timeout.toSeconds() + "s"
                    : timeout.toMillis() + "ms";
            report(failMessage.get() + " (timeout: " + timeoutStr + ")", e);
            return null;
        }
    }

    private Duration timeout() {
        return customTimeout != null
                ? customTimeout
                : Duration.ofSeconds(TestFlyContext.getConfig().getTimeouts().getExplicit());
    }

    /** Throws an {@link AssertionError}, or records the failure in soft mode. */
    private void report(String failMessage, Throwable cause) {
        String err = customMessage != null && !customMessage.isBlank()
                ? "[" + customMessage + "] " + failMessage
                : failMessage;
        if (soft) {
            SoftAssertionCollector target = collector != null ? collector : SoftAssertions.get();
            target.that(false, err);
        } else {
            throw cause != null ? new AssertionError(err, cause) : new AssertionError(err);
        }
    }
}
