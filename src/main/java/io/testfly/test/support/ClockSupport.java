package io.testfly.test.support;

import io.testfly.api.TestFlyApi;
import io.testfly.clock.TestClock;

/**
 * Provides browser clock mocking as an interface default method.
 *
 * @since 1.10.0
 */
@TestFlyApi(since = "1.10.0")
public interface ClockSupport extends StepSupport {

    /**
     * Returns a {@link TestClock} that controls the browser's perception of time.
     * Call after {@link NavigationSupport#open()} so the page is loaded before injecting the mock.
     */
    default TestClock clock() {
        step("Create test clock");
        return TestClock.create();
    }
}
