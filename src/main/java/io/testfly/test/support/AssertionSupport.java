package io.testfly.test.support;

import io.testfly.api.TestFlyApi;
import io.testfly.assertion.LocatorAssert;
import io.testfly.assertion.PageAssert;
import io.testfly.assertion.SeleniumAssert;
import io.testfly.locator.Locator;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

/**
 * Shared assertion factory — single source of truth for {@code assertThat()}.
 *
 * <p>Implemented by {@code BaseTest}, {@code BaseJUnit5Test}, {@code BasePage} and
 * {@code BaseCucumberSteps} so the delegation to {@link SeleniumAssert} lives in one place.
 */
@TestFlyApi(since = "1.10.0")
public interface AssertionSupport {

    /** Begins a fluent, auto-retrying assertion on the given locator. */
    default LocatorAssert assertThat(By locator) {
        return SeleniumAssert.assertThat(locator);
    }

    /** Begins a fluent, auto-retrying assertion on the given {@link Locator} chain. */
    default LocatorAssert assertThat(Locator locator) {
        return SeleniumAssert.assertThat(locator);
    }

    /** Begins a fluent, auto-retrying assertion on the given {@link WebDriver} page state. */
    default PageAssert assertThat(WebDriver driver) {
        return SeleniumAssert.assertThat(driver);
    }

    /** Begins a fluent, auto-retrying assertion on the current thread's {@link WebDriver} page state. */
    default PageAssert assertThatPage() {
        return SeleniumAssert.assertThatPage();
    }
}
