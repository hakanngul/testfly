package io.testfly.test.support;

import io.testfly.api.TestFlyApi;
import io.testfly.locator.Locator;
import io.testfly.locator.Role;
import org.openqa.selenium.By;

/**
 * Shared locator factory — single source of truth for {@code find()} / {@code $()} / {@code getBy*()}.
 *
 * <p>Implemented by {@code BaseTest}, {@code BaseJUnit5Test}, {@code BasePage} and
 * {@code BaseCucumberSteps} so the delegation to {@link Locator} lives in one place.
 * Adding a new {@code getBy*} strategy only requires changing this interface.
 *
 * <p>All methods delegate to the static factories in {@link Locator}:
 * {@link Locator#ofCss(String)}, {@link Locator#of(By)}, {@link Locator#byRole(Role)}, etc.
 */
@TestFlyApi(since = "1.10.0")
public interface LocatorSupport {

    // ----------------------------------------------------------
    // Fluent Locator API  (find / $)
    // ----------------------------------------------------------

    /** Creates a chainable {@link Locator} from a CSS selector. */
    default Locator find(String css) {
        return Locator.ofCss(css);
    }

    /** Creates a chainable {@link Locator} from a Selenium {@link By} locator. */
    default Locator find(By by) {
        return Locator.of(by);
    }

    /**
     * Creates a chainable {@link Locator} from a CSS selector.
     *
     * @deprecated Use {@link #find(String)} instead. Scheduled for removal in 2.0.0.
     */
    @Deprecated(since = "1.1.0", forRemoval = true)
    default Locator $(String css) {
        return find(css);
    }

    /**
     * Creates a chainable {@link Locator} from a Selenium {@link By} locator.
     *
     * @deprecated Use {@link #find(By)} instead. Scheduled for removal in 2.0.0.
     */
    @Deprecated(since = "1.1.0", forRemoval = true)
    default Locator $(By by) {
        return find(by);
    }

    // ----------------------------------------------------------
    // Accessibility-first locators  (getBy*)
    // ----------------------------------------------------------

    /**
     * Locates elements by their ARIA role — the most resilient strategy, since it
     * targets the accessibility tree rather than DOM structure.
     *
     * <pre>
     * getByRole(Role.BUTTON).withName("Submit").click();
     * getByRole(Role.HEADING).withLevel(1).getText();
     * </pre>
     */
    default Locator getByRole(Role role) {
        return Locator.byRole(role);
    }

    /** Locates an element by its ARIA role and accessible name in one call. */
    default Locator getByRole(Role role, String name) {
        return Locator.byRole(role).withName(name);
    }

    /** Locates an element by visible text — case-insensitive substring by default. */
    default Locator getByText(String text) {
        return Locator.byText(text);
    }

    /** Locates a form control by its associated label text. */
    default Locator getByLabel(String label) {
        return Locator.byLabel(label);
    }

    /** Locates an element by its {@code placeholder} attribute. */
    default Locator getByPlaceholder(String placeholder) {
        return Locator.byPlaceholder(placeholder);
    }

    /** Locates an element by its test-id attribute (default {@code data-testid}). */
    default Locator getByTestId(String testId) {
        return Locator.byTestId(testId);
    }

    /** Locates an element (typically {@code <img>}) by its {@code alt} text. */
    default Locator getByAltText(String altText) {
        return Locator.byAltText(altText);
    }

    /** Locates an element by its {@code title} attribute. */
    default Locator getByTitle(String title) {
        return Locator.byTitle(title);
    }

    /**
     * Locates an element dynamically based on semantic natural language intent using AI.
     *
     * <pre>
     * byIntent("cart checkout button").click();
     * </pre>
     *
     * @param intent semantic description of the element
     * @return chainable {@link Locator}
     */
    default Locator byIntent(String intent) {
        By resolvedBy = io.testfly.agent.ActionCompiler.resolveIntent(io.testfly.driver.DriverManager.getDriver(), intent);
        return Locator.of(resolvedBy);
    }
}
