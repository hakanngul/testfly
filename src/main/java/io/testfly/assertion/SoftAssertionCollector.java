package io.testfly.assertion;

import io.testfly.api.TestFlyApi;
import io.testfly.locator.Locator;
import org.openqa.selenium.By;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collects soft assertion failures without throwing immediately.
 * Obtain via {@code softAssert()} in {@link io.testfly.test.BaseTest} or
 * {@link io.testfly.test.BasePage}. The framework flushes all failures at
 * test end — if any exist the test is marked as FAILED with all messages combined.
 *
 * <pre>
 * softAssert().that(title.equals("Dashboard"), "Title should be Dashboard, was: " + title);
 * softAssert().that(menuVisible, "Navigation menu should be visible");
 * // test continues even if assertions fail — framework reports all at the end
 * </pre>
 */
@TestFlyApi(since = "1.0.0")
public final class SoftAssertionCollector {

    private final List<String> failures = new ArrayList<>();

    SoftAssertionCollector() {}  // package-private — obtained via SoftAssertions.get()

    /**
     * Checks {@code condition}. If false, records {@code message} as a failure.
     * Does NOT throw — test execution continues.
     *
     * @param condition the assertion to check
     * @param message   failure description shown in the report when condition is false
     * @return this collector (for chaining)
     */
    public SoftAssertionCollector that(boolean condition, String message) {
        if (!condition) {
            failures.add(message);
        }
        return this;
    }

    /**
     * Begins a fluent, auto-retrying soft assertion for the given {@link By} locator.
     * Failures are recorded into this collector without throwing immediately.
     */
    public LocatorAssert assertThat(By locator) {
        return new LocatorAssert(locator, locator.toString(), this);
    }

    /**
     * Begins a fluent, auto-retrying soft assertion for the given {@link Locator} chain.
     * Failures are recorded into this collector without throwing immediately.
     */
    public LocatorAssert assertThat(Locator locator) {
        return new LocatorAssert(SeleniumAssert.extractBy(locator), locator.toString(), this);
    }

    /** Returns {@code true} if at least one {@code that()} call evaluated to {@code false}. */
    public boolean hasFailed() {
        return !failures.isEmpty();
    }

    /** Returns an unmodifiable snapshot of all collected failure messages. */
    public List<String> getFailures() {
        return Collections.unmodifiableList(failures);
    }

    /** Clears all collected failures. Called by the framework after flushing. */
    public void clear() {
        failures.clear();
    }
}
