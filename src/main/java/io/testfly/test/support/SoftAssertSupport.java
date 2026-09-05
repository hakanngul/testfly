package io.testfly.test.support;

import io.testfly.api.TestFlyApi;
import io.testfly.assertion.LocatorAssert;
import io.testfly.assertion.PageAssert;
import io.testfly.assertion.SeleniumAssert;
import io.testfly.assertion.SoftAssertionCollector;
import io.testfly.assertion.SoftAssertions;
import io.testfly.locator.Locator;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

/**
 * Shared soft-assertion helper — single source of truth for {@code softAssert()}.
 *
 * <p>Implemented by {@code BaseTest}, {@code BaseApiTest} and {@code BasePage}
 * so the delegation to {@link SoftAssertions} lives in one place.
 */
@TestFlyApi(since = "1.10.0")
public interface SoftAssertSupport {

    /** Returns the soft assertion collector for this test. */
    default SoftAssertionCollector softAssert() {
        return SoftAssertions.get();
    }

    /** Begins a fluent, auto-retrying soft assertion on the given locator. */
    @TestFlyApi(since = "1.0.0")
    default LocatorAssert softAssert(By locator) {
        return SeleniumAssert.softAssert(locator);
    }

    /** Begins a fluent, auto-retrying soft assertion on the given {@link Locator} chain. */
    @TestFlyApi(since = "1.0.0")
    default LocatorAssert softAssert(Locator locator) {
        return SeleniumAssert.softAssert(locator);
    }

    /** Begins a fluent, auto-retrying soft assertion on the given {@link WebDriver} page state. */
    @TestFlyApi(since = "1.10.0")
    default PageAssert softAssert(WebDriver driver) {
        return SeleniumAssert.softAssert(driver);
    }

    /** Begins a fluent, auto-retrying soft assertion on the current thread's {@link WebDriver} page state. */
    @TestFlyApi(since = "1.10.0")
    default PageAssert softAssertPage() {
        return SeleniumAssert.softAssertPage();
    }
}
