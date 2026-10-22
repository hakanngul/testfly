package io.testfly.mobile;

import io.testfly.mobile.internal.MobileSessions;

/**
 * Native mobile helpers for tests (incubating, ADR 0002). The device is opened by the TestNG
 * listener, the JUnit 5 extension or the Cucumber hooks for tests marked {@link MobileTest}
 * (or {@code @mobile}), and is confined to the test's thread.
 */
public interface MobileSupport {

    /** The device of the current test. */
    default MobileDevice device() {
        return MobileSessions.current();
    }

    /** Locators of the current test's device screen. */
    default MobileScreen screen() {
        return device().screen();
    }

    /** {@code expect(screen().getByText("Welcome")).toBeVisible()} */
    default MobileLocatorAssert expect(MobileLocator locator) {
        return MobileAssertions.expect(locator);
    }
}
