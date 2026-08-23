package io.testfly.test.support;

import io.testfly.api.TestFlyApi;
import io.testfly.assertion.SoftAssertionCollector;
import io.testfly.assertion.SoftAssertions;

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
}
