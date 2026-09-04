package io.testfly.assertion;

import io.testfly.api.TestFlyApi;
import io.testfly.locator.Locator;
import org.openqa.selenium.By;

/**
 * Entry point for web-first assertions.
 *
 * <p>All assertions auto-retry until the condition is true or the configured
 * {@code timeouts.explicit} is exceeded — no manual waits required.
 *
 * <p>Available as a protected method in {@link io.testfly.test.BasePage}
 * and {@link io.testfly.test.BaseTest} via {@code assertThat(By)} /
 * {@code assertThat(Locator)}.
 *
 * <pre>
 * // In a test or page object:
 * assertThat(By.id("status")).isVisible();
 * assertThat(By.id("status")).hasText("Active");
 * assertThat(By.cssSelector(".error")).isHidden();
 * assertThat(By.id("submit")).isEnabled();
 * assertThat($(".items")).count(5);
 * </pre>
 */
@TestFlyApi(since = "1.4.0")
public final class SeleniumAssert {

    private SeleniumAssert() {
    }

    /**
     * Begin a fluent assertion chain for the given {@link By} locator.
     */
    public static LocatorAssert assertThat(By locator) {
        return new LocatorAssert(locator, locator.toString());
    }

    /**
     * Begin a fluent assertion chain for the given {@link Locator}.
     * <p>Extracts the underlying {@link By} from the locator for polling.
     */
    public static LocatorAssert assertThat(Locator locator) {
        // Resolve the By from the Locator's string representation
        // for the polling mechanism. Full chain resolution is handled
        // by LocatorAssert's poll methods via WebDriverWait.
        return new LocatorAssert(extractBy(locator), locator.toString());
    }

    /**
     * Begin a fluent soft assertion chain for the given {@link By} locator.
     * <p>Failures are collected in {@link SoftAssertions} and will not terminate test execution immediately.
     */
    public static LocatorAssert softAssert(By locator) {
        return new LocatorAssert(locator, locator.toString(), true);
    }

    /**
     * Begin a fluent soft assertion chain for the given {@link Locator}.
     * <p>Failures are collected in {@link SoftAssertions} and will not terminate test execution immediately.
     */
    public static LocatorAssert softAssert(Locator locator) {
        return new LocatorAssert(extractBy(locator), locator.toString(), true);
    }

    /**
     * Extracts the underlying {@link By} from a {@link Locator} instance
     * by parsing its toString. This is a lightweight bridge — for complex
     * chains (filter, nth, withText) use the Locator's own isVisible() / isHidden()
     * terminal methods before asserting on the result.
     */
    private static By extractBy(Locator locator) {
        // Locator.toString() = "Locator[css=.foo]" — parse out the By string.
        // The inner By is the root locator; chain filters are evaluated by Locator itself.
        // For simple cases this works perfectly; for chained cases callers should use
        // locator.element() and assert on the WebElement directly.
        String repr = locator.toString(); // "Locator[By.cssSelector: .foo]"
        // Strip wrapper prefix to get the By description for error messages only;
        // actual resolution goes through the Locator's own resolve path.
        // We re-expose the root as a CSS *if* it is a CSS selector, else pass through.
        // Safest: wrap Locator resolution as a custom By.
        return new LocatorBy(locator);
    }

    // ------------------------------------------------------------------
    // LocatorBy — bridges a Locator chain into a By for WebDriverWait
    // ------------------------------------------------------------------

    static final class LocatorBy extends By {
        private final Locator locator;

        LocatorBy(Locator locator) {
            this.locator = locator;
        }

        @Override
        public java.util.List<org.openqa.selenium.WebElement> findElements(
                org.openqa.selenium.SearchContext context) {
            return locator.elements();
        }

        @Override
        public String toString() {
            return locator.toString();
        }
    }
}
