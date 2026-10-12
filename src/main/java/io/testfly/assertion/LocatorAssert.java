package io.testfly.assertion;

import io.testfly.accessibility.AriaSnapshot;
import io.testfly.api.TestFlyApi;
import io.testfly.internal.A11yProbe;
import io.testfly.locator.Role;
import io.testfly.driver.DriverManager;
import io.testfly.config.TestFlyConfig;
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
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
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
    private boolean negated;
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

    /**
     * Negates the <b>next</b> assertion in the chain, like Playwright's
     * {@code not()}.
     *
     * <pre>
     * assertThat(status).not().hasText("Error");
     * assertThat(spinner).not().isVisible();
     * assertThat(errorBanner).not().isAttached();
     * </pre>
     *
     * <p>
     * Only the assertion directly after {@code not()} is negated:
     * {@code assertThat(x).not().isVisible().hasText("a")} checks
     * "not visible", then "has text a". As in Playwright, most negated assertions
     * also pass when no element matches. To require the element to exist, assert
     * that first: {@code assertThat(status).isVisible().not().hasText("Error")}.
     *
     * <p>
     * {@code not()} cannot be combined with the verified actions
     * {@link #click()}, {@link #fill(String)} and {@link #clear()}.
     *
     * @return this assertion for chaining
     * @since 1.1.0
     */
    public LocatorAssert not() {
        this.negated = true;
        return this;
    }

    // ------------------------------------------------------------------
    // Visibility
    // ------------------------------------------------------------------

    /** Asserts element is present and visible — retries until timeout. */
    public LocatorAssert isVisible() {
        step("Assert visible: " + description);
        poll(d -> d.findElement(by).isDisplayed() ? Check.pass() : Check.fail("not displayed"),
                "Expected element to be visible: " + description);
        return this;
    }

    /** Asserts element is absent or not visible — retries until timeout. */
    public LocatorAssert isHidden() {
        step("Assert hidden: " + description);
        poll(d -> {
            try {
                return d.findElement(by).isDisplayed() ? Check.fail("displayed") : Check.pass();
            } catch (NoSuchElementException e) {
                return Check.pass();
            }
        }, "Expected element to be hidden: " + description);
        return this;
    }

    // ------------------------------------------------------------------
    // Interactability
    // ------------------------------------------------------------------

    /** Asserts element is present, visible, and enabled — retries until timeout. */
    public LocatorAssert isEnabled() {
        step("Assert enabled: " + description);
        poll(d -> {
            WebElement el = target(d);
            if (el == null)
                return Check.fail("not found");
            if (!el.isDisplayed())
                return Check.fail("not displayed");
            return el.isEnabled() ? Check.pass() : Check.fail("disabled");
        }, "Expected element to be enabled: " + description);
        return this;
    }

    /** Asserts element is present but disabled — retries until timeout. */
    public LocatorAssert isDisabled() {
        step("Assert disabled: " + description);
        poll(d -> {
            WebElement el = target(d);
            if (el == null)
                return Check.fail("not found");
            if (!el.isDisplayed())
                return Check.fail("not displayed");
            return !el.isEnabled() ? Check.pass() : Check.fail("enabled");
        }, "Expected element to be disabled: " + description);
        return this;
    }

    /** Asserts a checkbox or radio button is checked — retries until timeout. */
    public LocatorAssert isChecked() {
        step("Assert checked: " + description);
        poll(d -> {
            WebElement el = target(d);
            if (el == null)
                return Check.fail("not found");
            return el.isSelected() ? Check.pass() : Check.fail("not selected");
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
        step("Assert text '" + expected + "' for: " + description);
        poll(d -> {
            String text = d.findElement(by).getText();
            String trimmed = text != null ? text.trim() : null;
            return trimmed != null && trimmed.equals(expected) ? Check.pass() : Check.fail(quote(text));
        }, "Expected text [" + expected + "] for: " + description);
        return this;
    }

    /**
     * Asserts the element's visible text contains {@code fragment} — retries until
     * timeout.
     */
    public LocatorAssert containsText(String fragment) {
        step("Assert contains text '" + fragment + "' for: " + description);
        poll(d -> {
            String text = d.findElement(by).getText();
            return text != null && fragment != null && text.contains(fragment) ? Check.pass()
                    : Check.fail(quote(text));
        }, "Expected text to contain [" + fragment + "] for: " + description);
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
        step("Assert value '" + expected + "' for: " + description);
        poll(d -> {
            String value = d.findElement(by).getAttribute("value");
            return value != null && value.equals(expected) ? Check.pass() : Check.fail(quote(value));
        }, "Expected value [" + expected + "] for: " + description);
        return this;
    }

    /**
     * Asserts the element has a specific attribute value — retries until timeout.
     */
    public LocatorAssert hasAttribute(String attribute, String expected) {
        step("Assert attribute " + attribute + "=" + expected + " for: " + description);
        poll(d -> {
            String value = d.findElement(by).getAttribute(attribute);
            if (value == null)
                return Check.fail("attribute '" + attribute + "' absent");
            return value.equals(expected) ? Check.pass() : Check.fail(attribute + "=" + quote(value));
        }, "Expected attribute [" + attribute + "=" + expected + "] for: " + description);
        return this;
    }

    /**
     * Asserts the element has the specified attribute present (regardless of its
     * value) — retries until timeout.
     */
    public LocatorAssert hasAttribute(String attribute) {
        step("Assert attribute '" + attribute + "' exists for: " + description);
        poll(d -> {
            WebElement el = target(d);
            if (el == null)
                return Check.fail("not found");
            return el.getAttribute(attribute) != null ? Check.pass()
                    : Check.fail("attribute '" + attribute + "' absent");
        }, "Expected attribute [" + attribute + "] to exist for: " + description);
        return this;
    }

    /**
     * Asserts the element has the specified CSS property value — retries until
     * timeout.
     */
    public LocatorAssert hasCssValue(String propertyName, String expectedValue) {
        step("Assert CSS " + propertyName + "='" + expectedValue + "' for: " + description);
        poll(d -> {
            WebElement el = target(d);
            if (el == null)
                return Check.fail("not found");
            String val = el.getCssValue(propertyName);
            return expectedValue != null && expectedValue.equals(val) ? Check.pass()
                    : Check.fail(propertyName + "=" + quote(val));
        }, "Expected CSS property [" + propertyName + "='" + expectedValue + "'] for: " + description);
        return this;
    }

    /**
     * Asserts the element is currently focused (the active element in the document)
     * — retries until timeout.
     */
    public LocatorAssert isFocused() {
        step("Assert focused: " + description);
        poll(d -> {
            WebElement el = target(d);
            if (el == null)
                return Check.fail("not found");
            WebDriver.TargetLocator target = d.switchTo();
            WebElement active = target != null ? target.activeElement() : null;
            return el.equals(active) ? Check.pass()
                    : Check.fail("focused element: " + describeElement(active));
        }, "Expected element to be focused: " + description);
        return this;
    }

    /** Asserts the element has the given CSS class — retries until timeout. */
    public LocatorAssert hasClass(String className) {
        step("Assert has class '" + className + "' for: " + description);
        poll(d -> {
            WebElement el = target(d);
            if (el == null)
                return Check.fail("not found");
            String classes = el.getAttribute("class");
            if (classes != null) {
                for (String cls : classes.split("\\s+")) {
                    if (cls.equals(className))
                        return Check.pass();
                }
            }
            return Check.fail("class=" + quote(classes));
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
        step("Assert count " + expected + " for: " + description);
        poll(d -> {
            int actual = d.findElements(by).size();
            return actual == expected ? Check.pass() : Check.fail(actual + " element(s)");
        }, "Expected " + expected + " element(s) for: " + description);
        return this;
    }

    /**
     * Asserts at least {@code min} elements match — retries until timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert countAtLeast(int min) {
        step("Assert count >= " + min + " for: " + description);
        poll(d -> {
            int actual = d.findElements(by).size();
            return actual >= min ? Check.pass() : Check.fail(actual + " element(s)");
        },
                "Expected at least " + min + " element(s) for: " + description);
        return this;
    }

    /**
     * Asserts at most {@code max} elements match — retries until timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert countAtMost(int max) {
        step("Assert count <= " + max + " for: " + description);
        poll(d -> {
            int actual = d.findElements(by).size();
            return actual <= max ? Check.pass() : Check.fail(actual + " element(s)");
        },
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
        step("Assert exists: " + description);
        poll(d -> d.findElements(by).isEmpty() ? Check.fail("not found") : Check.pass(),
                "Expected element to exist: " + description);
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
        rejectNegation("click()");
        step("Click (verified): " + description);
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
        rejectNegation("fill()");
        step("Fill (verified): " + description);
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
        rejectNegation("clear()");
        step("Clear (verified): " + description);
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
        step("Assert texts " + wanted + " for: " + description);
        poll(d -> {
            List<String> texts = textsOf(d.findElements(by));
            return wanted.equals(texts) ? Check.pass() : Check.fail(shorten(texts.toString()));
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
        step("Assert contains texts " + wanted + " for: " + description);
        poll(d -> {
            List<String> texts = textsOf(d.findElements(by));
            return texts.containsAll(wanted) ? Check.pass() : Check.fail(shorten(texts.toString()));
        }, "Expected texts to contain " + wanted + " for: " + description);
        return this;
    }

    // ------------------------------------------------------------------
    // Selection
    // ------------------------------------------------------------------

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
    // Playwright parity (names follow Playwright for Java's LocatorAssertions)
    // Pattern overloads use find(): the regex may match part of the value, as in
    // Playwright. Anchor it with ^...$ for a full match.
    // ------------------------------------------------------------------

    /**
     * Asserts at least one element matches, visible or not — retries until
     * timeout. Same as {@link #exists()}.
     *
     * @since 1.1.0
     */
    public LocatorAssert isAttached() {
        step("Assert attached: " + description);
        poll(d -> d.findElements(by).isEmpty() ? Check.fail("not found") : Check.pass(),
                "Expected element to be attached: " + description);
        return this;
    }

    /**
     * Asserts the element is empty — retries until timeout. For
     * {@code input}, {@code textarea} and {@code select} the value must be empty;
     * for any other element its text content (trimmed) must be empty.
     *
     * @since 1.1.0
     */
    public LocatorAssert isEmpty() {
        pollFirst("Assert empty: " + description, LocatorAssert::isEmptyElement,
                "Expected element to be empty: " + description);
        return this;
    }

    /**
     * Asserts a checkbox is in the indeterminate (mixed) state — retries until
     * timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert isIndeterminate() {
        pollFirst("Assert indeterminate: " + description,
                el -> "true".equals(el.getDomProperty("indeterminate")),
                "Expected checkbox to be indeterminate: " + description);
        return this;
    }

    /**
     * Asserts at least {@code ratio} (0..1) of the element's area is inside the
     * viewport; {@code 0} means any part — retries until timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert isInViewport(double ratio) {
        if (ratio < 0 || ratio > 1) {
            throw new IllegalArgumentException("ratio must be between 0 and 1: " + ratio);
        }
        pollFirst("Assert in viewport (ratio " + ratio + "): " + description,
                el -> {
                    Object visible = js(el, VIEWPORT_RATIO_JS);
                    if (!(visible instanceof Number n))
                        return false;
                    return ratio == 0 ? n.doubleValue() > 0 : n.doubleValue() >= ratio;
                },
                "Expected at least " + ratio + " of the element to be in the viewport: " + description);
        return this;
    }

    /**
     * Asserts the number of matching elements equals {@code expected} — retries
     * until timeout. Same as {@link #count(int)}.
     *
     * @since 1.1.0
     */
    public LocatorAssert hasCount(int expected) {
        return count(expected);
    }

    /**
     * Asserts the element's trimmed visible text matches {@code pattern} — retries
     * until timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert hasText(Pattern pattern) {
        pollFirst("Assert text matches /" + pattern + "/ for: " + description,
                el -> pattern.matcher(el.getText().trim()).find(),
                "Expected text to match [" + pattern + "] for: " + description);
        return this;
    }

    /**
     * Asserts the trimmed texts of all matching elements equal {@code expected},
     * in order — retries until timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert hasText(String[] expected) {
        return hasTexts(expected);
    }

    /**
     * Asserts the trimmed texts of all matching elements match {@code patterns},
     * in order and one pattern per element — retries until timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert hasText(Pattern[] patterns) {
        List<Pattern> wanted = Arrays.asList(patterns);
        step("Assert texts match " + wanted + " for: " + description);
        poll(d -> {
            List<String> texts = textsOf(d.findElements(by));
            return allMatch(texts, wanted) ? Check.pass() : Check.fail(shorten(texts.toString()));
        }, "Expected texts to match " + wanted + " for: " + description);
        return this;
    }

    /**
     * Asserts the element's visible text contains a match of {@code pattern} —
     * retries until timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert containsText(Pattern pattern) {
        return hasText(pattern);
    }

    /**
     * Asserts the matching elements contain the given texts in order: each
     * expected text is found in a later element than the previous one — retries
     * until timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert containsText(String[] expected) {
        List<String> wanted = Arrays.asList(expected);
        step("Assert texts contain " + wanted + " in order for: " + description);
        poll(d -> {
            List<String> texts = textsOf(d.findElements(by));
            return containsInOrder(texts, wanted) ? Check.pass() : Check.fail(shorten(texts.toString()));
        }, "Expected texts to contain " + wanted + " in order for: " + description);
        return this;
    }

    /**
     * Asserts the element's current value matches {@code pattern} — retries until
     * timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert hasValue(Pattern pattern) {
        pollFirst("Assert value matches /" + pattern + "/ for: " + description,
                el -> {
                    String value = valueOf(el);
                    return value != null && pattern.matcher(value).find();
                },
                "Expected value to match [" + pattern + "] for: " + description);
        return this;
    }

    /**
     * Asserts the selected option values of a {@code <select multiple>} equal
     * {@code expected}, in order — retries until timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert hasValues(String... expected) {
        List<String> wanted = Arrays.asList(expected);
        pollFirst("Assert selected values " + wanted + " for: " + description,
                el -> wanted.equals(selectedValues(el)),
                "Expected selected values " + wanted + " for: " + description);
        return this;
    }

    /**
     * Asserts the selected option values of a {@code <select multiple>} match
     * {@code patterns}, in order and one pattern per value — retries until
     * timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert hasValues(Pattern... patterns) {
        List<Pattern> wanted = Arrays.asList(patterns);
        pollFirst("Assert selected values match " + wanted + " for: " + description,
                el -> allMatch(selectedValues(el), wanted),
                "Expected selected values to match " + wanted + " for: " + description);
        return this;
    }

    /**
     * Asserts the given attribute's value matches {@code pattern} — retries until
     * timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert hasAttribute(String attribute, Pattern pattern) {
        pollFirst("Assert attribute " + attribute + " matches /" + pattern + "/ for: " + description,
                el -> {
                    String value = el.getDomAttribute(attribute);
                    return value != null && pattern.matcher(value).find();
                },
                "Expected attribute [" + attribute + "] to match [" + pattern + "] for: " + description);
        return this;
    }

    /**
     * Asserts the whole {@code class} attribute matches {@code pattern} — retries
     * until timeout. Note: {@link #hasClass(String)} checks a single class name,
     * like Playwright's {@code containsClass}.
     *
     * @since 1.1.0
     */
    public LocatorAssert hasClass(Pattern pattern) {
        pollFirst("Assert class matches /" + pattern + "/ for: " + description,
                el -> {
                    String classes = el.getDomAttribute("class");
                    return classes != null && pattern.matcher(classes).find();
                },
                "Expected class attribute to match [" + pattern + "] for: " + description);
        return this;
    }

    /**
     * Asserts the element has all the given space-separated class names, in any
     * order — retries until timeout.
     *
     * <pre>
     * assertThat(button).containsClass("btn primary");
     * </pre>
     *
     * @since 1.1.0
     */
    public LocatorAssert containsClass(String classNames) {
        pollFirst("Assert contains class '" + classNames + "' for: " + description,
                el -> hasAllClasses(el, classNames),
                "Expected element to contain class [" + classNames + "]: " + description);
        return this;
    }

    /**
     * Asserts the matching elements contain the given class names, one entry per
     * element, in order — retries until timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert containsClass(List<String> classNamesPerElement) {
        step("Assert elements contain classes " + classNamesPerElement + " for: " + description);
        poll(d -> {
            List<WebElement> els = d.findElements(by);
            List<String> classes = new ArrayList<>(els.size());
            boolean all = els.size() == classNamesPerElement.size();
            for (int i = 0; i < els.size(); i++) {
                classes.add(els.get(i).getDomAttribute("class"));
                if (all && !hasAllClasses(els.get(i), classNamesPerElement.get(i)))
                    all = false;
            }
            return all ? Check.pass()
                    : Check.fail(els.size() + " element(s), classes: " + shorten(classes.toString()));
        }, "Expected elements to contain classes " + classNamesPerElement + " for: " + description);
        return this;
    }

    /**
     * Asserts the element's computed CSS property equals {@code expected} —
     * retries until timeout. Same as {@link #hasCssValue(String, String)}.
     *
     * @since 1.1.0
     */
    public LocatorAssert hasCSS(String propertyName, String expected) {
        return hasCssValue(propertyName, expected);
    }

    /**
     * Asserts the element's computed CSS property matches {@code pattern} —
     * retries until timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert hasCSS(String propertyName, Pattern pattern) {
        pollFirst("Assert CSS " + propertyName + " matches /" + pattern + "/ for: " + description,
                el -> {
                    String value = el.getCssValue(propertyName);
                    return value != null && pattern.matcher(value).find();
                },
                "Expected CSS property [" + propertyName + "] to match [" + pattern + "] for: " + description);
        return this;
    }

    /**
     * Asserts the element's {@code id} matches {@code pattern} — retries until
     * timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert hasId(Pattern pattern) {
        pollFirst("Assert id matches /" + pattern + "/ for: " + description,
                el -> {
                    String id = el.getDomAttribute("id");
                    return id != null && pattern.matcher(id).find();
                },
                "Expected id to match [" + pattern + "] for: " + description);
        return this;
    }

    /**
     * Asserts the element's JavaScript property equals {@code expected} — retries
     * until timeout. Numbers compare by value ({@code 1}, {@code 1L} and
     * {@code 1.0} are equal).
     *
     * <pre>
     * assertThat(checkbox).hasJSProperty("checked", true);
     * assertThat(card).hasJSProperty("dataset", Map.of("id", "42"));
     * </pre>
     *
     * @since 1.1.0
     */
    public LocatorAssert hasJSProperty(String name, Object expected) {
        pollFirst("Assert JS property " + name + "=" + expected + " for: " + description,
                el -> sameJsValue(js(el, "return arguments[0][arguments[1]];", name), expected),
                "Expected JS property [" + name + "=" + expected + "] for: " + description);
        return this;
    }

    /**
     * Asserts the element's accessible name matches {@code pattern} — retries until
     * timeout. Use {@code Pattern.compile("(?i)submit")} for a case-insensitive
     * "contains" check.
     *
     * @since 1.1.0
     */
    public LocatorAssert hasAccessibleName(Pattern pattern) {
        Objects.requireNonNull(pattern, "pattern");
        step("Assert accessible name matches /" + pattern + "/ for: " + description);
        poll(d -> {
            A11yProbe.NodeInfo info = probeFirst(d);
            if (info == null)
                return Check.fail("not found");
            return info.name() != null && pattern.matcher(info.name()).find() ? Check.pass()
                    : Check.fail(quote(info.name()));
        }, "Expected accessible name to match [" + pattern + "] for: " + description);
        return this;
    }

    /**
     * Asserts the element's accessible description matches {@code pattern} —
     * retries until timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert hasAccessibleDescription(Pattern pattern) {
        Objects.requireNonNull(pattern, "pattern");
        step("Assert accessible description matches /" + pattern + "/ for: " + description);
        poll(d -> {
            A11yProbe.NodeInfo info = probeFirst(d);
            if (info == null)
                return Check.fail("not found");
            return info.description() != null && pattern.matcher(info.description()).find() ? Check.pass()
                    : Check.fail(quote(info.description()));
        }, "Expected accessible description to match [" + pattern + "] for: " + description);
        return this;
    }

    /**
     * Asserts the element's accessible error message equals {@code expected}
     * (whitespace-normalized) — retries until timeout. The message is the text of
     * the elements referenced by {@code aria-errormessage}, and only counts while
     * {@code aria-invalid} is set and not {@code "false"}.
     *
     * @since 1.1.0
     */
    public LocatorAssert hasAccessibleErrorMessage(String expected) {
        pollFirst("Assert accessible error message '" + expected + "' for: " + description,
                el -> normalizeSpace(expected).equals(jsText(el, ACCESSIBLE_ERROR_MESSAGE_JS)),
                "Expected accessible error message [" + expected + "] for: " + description);
        return this;
    }

    /**
     * Asserts the element's accessible error message matches {@code pattern} —
     * retries until timeout.
     *
     * @since 1.1.0
     */
    public LocatorAssert hasAccessibleErrorMessage(Pattern pattern) {
        pollFirst("Assert accessible error message matches /" + pattern + "/ for: " + description,
                el -> pattern.matcher(jsText(el, ACCESSIBLE_ERROR_MESSAGE_JS)).find(),
                "Expected accessible error message to match [" + pattern + "] for: " + description);
        return this;
    }

    // ------------------------------------------------------------------
    // Accessibility (deterministic)
    // ------------------------------------------------------------------

    /**
     * Asserts the element's role — the explicit {@code role} attribute, or the implicit role of
     * its HTML tag — equals {@code role}. Retries until timeout.
     */
    @TestFlyApi(since = "1.1.0")
    public LocatorAssert hasRole(Role role) {
        Objects.requireNonNull(role, "role");
        return hasRole(role.ariaName());
    }

    /**
     * Asserts the element's role — the explicit {@code role} attribute, or the implicit role of
     * its HTML tag — equals {@code expected}, ignoring case. Retries until timeout.
     */
    @TestFlyApi(since = "1.1.0")
    public LocatorAssert hasRole(String expected) {
        Objects.requireNonNull(expected, "expected");
        step("Assert role '" + expected + "' for: " + description);
        poll(d -> {
            A11yProbe.NodeInfo info = probeFirst(d);
            if (info == null)
                return Check.fail("not found");
            return expected.equalsIgnoreCase(info.role()) ? Check.pass()
                    : Check.fail(info.role() == null ? "generic (no role)" : info.role());
        }, "Expected element to have role [" + expected + "]: " + description);
        return this;
    }

    /**
     * Asserts the element's accessible name equals {@code expected}, with whitespace collapsed —
     * the same name {@code Locator.withName(...)} matches on. Retries until timeout. For a partial
     * or case-insensitive match use {@link #hasAccessibleName(Pattern)}.
     */
    @TestFlyApi(since = "1.1.0")
    public LocatorAssert hasAccessibleName(String expected) {
        Objects.requireNonNull(expected, "expected");
        step("Assert accessible name '" + expected + "' for: " + description);
        poll(d -> {
            A11yProbe.NodeInfo info = probeFirst(d);
            if (info == null)
                return Check.fail("not found");
            return collapse(expected).equals(info.name()) ? Check.pass() : Check.fail(quote(info.name()));
        }, "Expected accessible name [" + expected + "] for: " + description);
        return this;
    }

    /**
     * Asserts the element's accessible description (aria-describedby, aria-description or title)
     * equals {@code expected}, with whitespace collapsed. Retries until timeout. For a partial or
     * case-insensitive match use {@link #hasAccessibleDescription(Pattern)}.
     */
    @TestFlyApi(since = "1.1.0")
    public LocatorAssert hasAccessibleDescription(String expected) {
        Objects.requireNonNull(expected, "expected");
        step("Assert accessible description '" + expected + "' for: " + description);
        poll(d -> {
            A11yProbe.NodeInfo info = probeFirst(d);
            if (info == null)
                return Check.fail("not found");
            return collapse(expected).equals(info.description()) ? Check.pass()
                    : Check.fail(quote(info.description()));
        }, "Expected accessible description [" + expected + "] for: " + description);
        return this;
    }

    /**
     * Asserts the accessibility tree under the element matches a YAML template. See
     * {@link AriaSnapshot} for the template syntax and matching rules. A malformed template fails
     * immediately with {@link IllegalArgumentException}, without waiting for the timeout. On
     * failure the message includes the template and the captured snapshot.
     *
     * <pre>
     * assertThat(By.tagName("nav")).matchesAriaSnapshot("""
     *     - link "Home"
     *     - link "Docs"
     *     """);
     * </pre>
     */
    @TestFlyApi(since = "1.1.0")
    public LocatorAssert matchesAriaSnapshot(String template) {
        Objects.requireNonNull(template, "template");
        List<AriaSnapshot.AriaNode> expected = AriaSnapshot.parseTemplate(template);
        step("Assert ARIA snapshot for: " + description);
        AtomicReference<String> detail = new AtomicReference<>("");
        poll(d -> {
            List<WebElement> els = d.findElements(by);
            if (els.isEmpty())
                return Check.fail("not found");
            List<AriaSnapshot.AriaNode> actual = AriaSnapshot.capture(d, els.getFirst());
            AriaSnapshot.MatchResult result = AriaSnapshot.match(actual, expected);
            if (result.matches())
                return Check.pass();
            detail.set(result.diff());
            return Check.fail(shorten(result.diff().lines().skip(1).findFirst().orElse("mismatch")));
        }, "Expected element to match ARIA snapshot: " + description, detail::get);
        return this;
    }

    private A11yProbe.NodeInfo probeFirst(WebDriver d) {
        WebElement el = target(d);
        return el == null ? null : A11yProbe.probe(d, el);
    }

    private static String collapse(String s) {
        return s.replaceAll("\\s+", " ").trim();
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
        step("Assert element satisfies condition (AI): \"" + expectedCondition + "\" for: " + description);
        evaluateAi(expectedCondition, !consumeNegation());
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
        step(
                "Assert element does not violate condition (AI): \"" + forbiddenCondition + "\" for: " + description);
        evaluateAi(forbiddenCondition, consumeNegation());
        return this;
    }

    private void evaluateAi(String condition, boolean expectSatisfaction) {
        WebDriver driver = DriverManager.getDriver();
        WebElement el;
        try {
            el = target(driver);
        } catch (NoSuchElementException | LocatorException e) {
            el = null;
        }
        if (el == null) {
            report("Cannot evaluate AI condition: element not found for " + description, null);
            return;
        }

        String rawHtml;
        try {
            rawHtml = el.getAttribute("outerHTML");
        } catch (Exception e) {
            rawHtml = el.getText();
        }

        String pruned = DomPruner.prune(rawHtml);
        AiAssertEngine.AiAssertionResult result = AiAssertEngine.verify(driver, pruned, condition, expectSatisfaction);

        if (!result.isPassed()) {
            String modeStr = expectSatisfaction ? "satisfy" : "not violate";
            report("Expected element [" + description + "] to " + modeStr + " AI condition: \""
                    + condition + "\". Reason: " + result.reason(), null);
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

    /** Fraction (0..1) of the element's area that lies inside the viewport. */
    private static final String VIEWPORT_RATIO_JS = "const r = arguments[0].getBoundingClientRect();"
            + "const vw = window.innerWidth || document.documentElement.clientWidth;"
            + "const vh = window.innerHeight || document.documentElement.clientHeight;"
            + "const area = r.width * r.height;"
            + "if (area <= 0) return 0;"
            + "const w = Math.max(0, Math.min(r.right, vw) - Math.max(r.left, 0));"
            + "const h = Math.max(0, Math.min(r.bottom, vh) - Math.max(r.top, 0));"
            + "return (w * h) / area;";

    private static final String TEXT_OF_IDS_JS = "function textOfIds(ids) {"
            + "  return (ids || '').split(/\\s+/).filter(Boolean)"
            + "    .map(function (id) { const r = document.getElementById(id); return r ? r.textContent : ''; })"
            + "    .join(' ').replace(/\\s+/g, ' ').trim();"
            + "}";

    /** aria-describedby text, then aria-description, then title. */
    private static final String ACCESSIBLE_DESCRIPTION_JS = TEXT_OF_IDS_JS
            + "const e = arguments[0];"
            + "const byIds = textOfIds(e.getAttribute('aria-describedby'));"
            + "if (byIds) return byIds;"
            + "const d = e.getAttribute('aria-description');"
            + "if (d && d.trim()) return d;"
            + "return e.getAttribute('title') || '';";

    /** aria-errormessage text, only while aria-invalid is set and not "false". */
    private static final String ACCESSIBLE_ERROR_MESSAGE_JS = TEXT_OF_IDS_JS
            + "const e = arguments[0];"
            + "const invalid = e.getAttribute('aria-invalid');"
            + "if (!invalid || invalid === 'false') return '';"
            + "return textOfIds(e.getAttribute('aria-errormessage'));";

    private static final String IN_VIEWPORT_JS ="const r = arguments[0].getBoundingClientRect();"
            + "const h = window.innerHeight || document.documentElement.clientHeight;"
            + "const w = window.innerWidth || document.documentElement.clientWidth;"
            + "return r.width > 0 && r.height > 0 && r.bottom > 0 && r.right > 0 && r.top < h && r.left < w;";

    /**
     * Polls until the first matching element satisfies {@code check}. A missing or
     * stale element counts as "not yet".
     */
    private void pollFirst(String stepMessage, Predicate<WebElement> check, String failMessage) {
        step(stepMessage);
        poll(d -> {
            WebElement el = target(d);
            if (el == null)
                return Check.fail("not found");
            return check.test(el) ? Check.pass() : Check.fail("condition not met on " + describeElement(el));
        }, failMessage);
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

    private void rejectNegation(String action) {
        if (consumeNegation()) {
            throw new IllegalStateException("not() cannot be combined with " + action);
        }
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
    private static Object js(WebElement el, String script, Object... extraArgs) {
        Object[] args = new Object[extraArgs.length + 1];
        args[0] = el;
        System.arraycopy(extraArgs, 0, args, 1, extraArgs.length);
        try {
            return ((JavascriptExecutor) DriverManager.getDriver()).executeScript(script, args);
        } catch (JavascriptException e) {
            return null;
        }
    }

    /** Whitespace-normalized string result of a script. */
    private static String jsText(WebElement el, String script) {
        Object result = js(el, script);
        return normalizeSpace(result == null ? null : result.toString());
    }

    private static String normalizeSpace(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }

    private static boolean isEmptyElement(WebElement el) {
        String tag = el.getTagName();
        if (tag != null && List.of("input", "textarea", "select").contains(tag.toLowerCase(java.util.Locale.ROOT))) {
            return isNullOrEmpty(el.getDomProperty("value"));
        }
        String text = el.getDomProperty("textContent");
        return text == null || text.trim().isEmpty();
    }

    /** True when there is one pattern per value and each pattern finds a match. */
    private static boolean allMatch(List<String> values, List<Pattern> patterns) {
        if (values.size() != patterns.size())
            return false;
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) == null || !patterns.get(i).matcher(values.get(i)).find())
                return false;
        }
        return true;
    }

    /** True when each expected fragment is contained in a later text than the previous one. */
    private static boolean containsInOrder(List<String> texts, List<String> expected) {
        int next = 0;
        for (String text : texts) {
            if (next < expected.size() && text.contains(expected.get(next)))
                next++;
        }
        return next == expected.size();
    }

    private static List<String> selectedValues(WebElement select) {
        List<String> values = new ArrayList<>();
        for (WebElement option : new Select(select).getAllSelectedOptions()) {
            values.add(option.getDomProperty("value"));
        }
        return values;
    }

    private static boolean hasAllClasses(WebElement el, String classNames) {
        String classes = el.getDomAttribute("class");
        if (classes == null)
            return classNames.isBlank();
        List<String> present = Arrays.asList(classes.trim().split("\\s+"));
        for (String wanted : classNames.trim().split("\\s+")) {
            if (!wanted.isEmpty() && !present.contains(wanted))
                return false;
        }
        return true;
    }

    /** Equality for values returned by JavaScript; numbers compare by value. */
    private static boolean sameJsValue(Object actual, Object expected) {
        if (actual instanceof Number a && expected instanceof Number b)
            return Double.compare(a.doubleValue(), b.doubleValue()) == 0;
        return Objects.equals(actual, expected);
    }

    /** Outcome of one probe: whether it passed and what was observed. */
    private record Check(boolean passed, String actual) {
        static Check pass() {
            return new Check(true, "ok");
        }

        static Check fail(String actual) {
            return new Check(false, actual);
        }
    }

    /**
     * The element single-element matchers inspect, or {@code null} when nothing matches. A
     * {@code Locator} picks its first visible match (see {@code SeleniumAssert.LocatorBy}); a plain
     * {@code By} keeps its first match.
     */
    private WebElement target(WebDriver d) {
        if (by instanceof SeleniumAssert.LocatorBy) {
            return d.findElement(by);
        }
        List<WebElement> els = d.findElements(by);
        return els.isEmpty() ? null : els.getFirst();
    }

    private static Check safeProbe(Function<WebDriver, Check> probe, WebDriver driver) {
        try {
            return probe.apply(driver);
        } catch (NoSuchElementException e) {
            return Check.fail("not found");
        } catch (StaleElementReferenceException e) {
            return Check.fail("element went stale");
        } catch (LocatorException e) {
            return Check.fail("locator failed: " + shorten(e.getMessage()));
        }
    }

    private void poll(Function<WebDriver, Check> probe, String failMessage) {
        poll(probe, failMessage, null);
    }

    /** {@code detail}, when not null, is appended on new lines after the one-line failure message. */
    private void poll(Function<WebDriver, Check> probe, String failMessage, Supplier<String> detail) {
        boolean negate = consumeNegation();
        Duration timeout = timeout();
        WebDriver driver = DriverManager.getDriver();
        AtomicReference<String> lastActual = new AtomicReference<>("not evaluated");
        try {
            new WebDriverWait(driver, timeout).until(d -> {
                Check c = safeProbe(probe, d);
                lastActual.set(c.actual());
                return c.passed() != negate ? Boolean.TRUE : null;
            });
        } catch (TimeoutException e) {
            String timeoutStr = timeout.toMillis() >= 1000 && timeout.toMillis() % 1000 == 0
                    ? timeout.toSeconds() + "s"
                    : timeout.toMillis() + "ms";
            String message = negate ? negateMessage(failMessage) : failMessage;
            String actual = negate ? "condition still met" : "actual: [" + lastActual.get() + "]";
            String extra = detail == null || detail.get().isEmpty() ? "" : "\n" + detail.get();
            report(message + " (" + actual + ", timeout: " + timeoutStr + ")" + extra, e);
        }
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
        if (customTimeout != null)
            return customTimeout;
        TestFlyConfig config = TestFlyContext.getConfig();
        return config != null && config.getTimeouts() != null
                ? Duration.ofSeconds(config.getTimeouts().getExplicit())
                : Duration.ofSeconds(10);
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

    // ------------------------------------------------------------------
    // Formatting helpers — keep every "actual" on a single line so soft
    // failures joined with '\n' stay readable.
    // ------------------------------------------------------------------

    private static String quote(String s) {
        return s == null ? "null" : "\"" + shorten(s) + "\"";
    }

    private static String shorten(String s) {
        if (s == null)
            return "null";
        String oneLine = s.replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n");
        return oneLine.length() > 200 ? oneLine.substring(0, 200) + "…" : oneLine;
    }

    private static String describeElement(WebElement el) {
        if (el == null)
            return "unknown";
        try {
            StringBuilder sb = new StringBuilder("<");
            String tag = el.getTagName();
            sb.append(tag != null ? tag : "element");
            String id = el.getAttribute("id");
            if (id != null && !id.isBlank())
                sb.append('#').append(id);
            return sb.append('>').toString();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
