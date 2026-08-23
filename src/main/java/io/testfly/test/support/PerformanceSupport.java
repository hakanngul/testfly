package io.testfly.test.support;

import io.testfly.api.TestFlyApi;
import io.testfly.performance.PerformanceAssert;
import io.testfly.performance.PerformanceCollector;
import io.testfly.performance.PerformanceMetrics;

/**
 * Provides Core Web Vitals helpers as interface default methods.
 *
 * @since 1.10.0
 */
@TestFlyApi(since = "1.10.0")
public interface PerformanceSupport extends StepSupport {

    /**
     * Collects Core Web Vitals and returns a fluent assertion builder.
     * Call after {@link NavigationSupport#open()} once the page has loaded.
     */
    default PerformanceAssert assertPerformance() {
        step("Assert performance (Core Web Vitals)");
        return PerformanceAssert.of(PerformanceCollector.collect());
    }

    /** Collects and returns raw Core Web Vitals for custom inspection. */
    default PerformanceMetrics collectPerformance() {
        step("Collect performance (Core Web Vitals)");
        return PerformanceCollector.collect();
    }
}
