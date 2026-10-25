package io.testfly.mobile;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects failed mobile assertions instead of stopping the test; create with
 * {@link MobileAssertions#softly()} and finish with {@link #assertAll()}.
 *
 * <pre>
 * MobileSoftAssertions soft = MobileAssertions.softly();
 * soft.expect(home.title).toHaveText("Welcome");
 * soft.expect(home.banner).toBeVisible();
 * soft.assertAll();
 * </pre>
 *
 * Not thread-safe: use one instance per test thread.
 */
public final class MobileSoftAssertions {

    private final List<MobileAssertionError> failures = new ArrayList<>();

    MobileSoftAssertions() {
    }

    public MobileLocatorAssert expect(MobileLocator locator) {
        return new MobileLocatorAssert(locator, null, failures::add);
    }

    /** Same, with {@code message} prefixed to a failure. */
    public MobileLocatorAssert expect(MobileLocator locator, String message) {
        return new MobileLocatorAssert(locator, message, failures::add);
    }

    /** The failures collected so far. */
    public List<MobileAssertionError> failures() {
        return List.copyOf(failures);
    }

    /** Throws one {@link MobileAssertionError} listing every collected failure; does nothing when all passed. */
    public void assertAll() {
        if (failures.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder(failures.size() + " mobile assertion(s) failed:");
        for (int i = 0; i < failures.size(); i++) {
            sb.append("\n\n").append(i + 1).append(") ").append(failures.get(i).getMessage());
        }
        MobileAssertionError all = new MobileAssertionError(sb.toString());
        failures.forEach(all::addSuppressed);
        failures.clear();
        throw all;
    }
}
