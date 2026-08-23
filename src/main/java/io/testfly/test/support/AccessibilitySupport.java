package io.testfly.test.support;

import io.testfly.accessibility.AccessibilityAssert;
import io.testfly.api.TestFlyApi;
import io.testfly.steps.StepLogger;

/**
 * Shared accessibility helper — single source of truth for {@code accessibility()}.
 *
 * <p>Implemented by {@code BaseTest} and {@code BaseJUnit5Test} so the delegation
 * to {@link AccessibilityAssert} lives in one place. Unifies the previous inconsistency where
 * {@code BaseTest} logged via {@link StepLogger} and {@code BaseJUnit5Test} did not.
 */
@TestFlyApi(since = "1.10.0")
public interface AccessibilitySupport {

    /** Returns a fluent accessibility assertion builder backed by axe-core. */
    default AccessibilityAssert accessibility() {
        StepLogger.step("Run accessibility scan");
        return AccessibilityAssert.create();
    }
}
