package io.testfly.test.support;

import io.testfly.api.TestFlyApi;
import io.testfly.loadtest.CheckCondition;
import io.testfly.loadtest.LoadScenario;
import io.testfly.loadtest.LoadStep;
import io.testfly.loadtest.LoadTestMetrics;
import io.testfly.loadtest.LoadTestRunner;

/**
 * Shared load-testing helpers — single source of truth for {@code load()} and
 * {@code loadScenario()}.
 *
 * <p>
 * Implemented by {@code BaseLoadTest} so the delegation to
 * {@link LoadScenario} and {@link LoadTestRunner} lives in one place.
 * Follows the same mixin pattern as {@link ApiSupport}, {@link StepSupport},
 * etc.
 */
@TestFlyApi(since = "1.1.0")
public interface LoadTestSupport {

    /**
     * Creates a single-step load-test scenario targeting the given path (GET).
     *
     * <pre>
     * load("/api/health")
     *         .users(100)
     *         .run()
     *         .assertP95Below(200);
     * </pre>
     */
    default LoadScenario load(String path) {
        return LoadScenario.single(path);
    }

    /**
     * Creates a named, multi-step load-test scenario.
     *
     * <pre>
     * loadScenario("Checkout Flow")
     *     .users(500)
     *     .step("Login").post("/api/auth/login").body(...)
     *     .step("Add to Cart").post("/api/cart").body(...)
     *     .run()
     *     .assertP95Below(500);
     * </pre>
     */
    default LoadScenario loadScenario(String name) {
        return LoadScenario.named(name);
    }

    /**
     * Returns the metrics from the most recent load-test execution on this thread.
     * Useful for custom assertions or logging after {@code run()}.
     */
    default LoadTestMetrics lastLoadMetrics() {
        return LoadTestRunner.lastMetrics();
    }

    /**
     * Creates a status-code check builder for use with
     * {@link LoadStep#check(CheckCondition)}.
     *
     * <pre>
     * .check(status().is(200))
     * .check(status().in(200, 201))
     * </pre>
     */
    default CheckCondition.Builder status() {
        return LoadStep.status();
    }

    /**
     * Creates a JSONPath check builder for use with
     * {@link LoadStep#check(CheckCondition)}.
     *
     * <pre>
     * .check(jsonPath("$.id").exists())
     * </pre>
     */
    default CheckCondition.Builder jsonPath(String path) {
        return LoadStep.jsonPath(path);
    }
}
