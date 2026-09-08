package io.testfly.loadtest;

import io.testfly.api.TestFlyApi;
import io.testfly.loadtest.internal.GatlingBridge;
import io.testfly.loadtest.internal.GatlingEngine;
import io.testfly.loadtest.internal.JdkLoadEngine;
import io.testfly.loadtest.internal.LoadTestEngine;
import io.testfly.steps.StepLogger;

/**
 * Orchestrates load-test execution — selects the engine, runs the scenario,
 * and collects metrics.
 *
 * <p>
 * Analogous to {@link io.testfly.driver.DriverManager} for WebDriver lifecycle:
 * a static facade that hides engine selection and execution details from the
 * user.
 *
 * <p>
 * Engine selection ({@code loadtest.engine} config):
 * <ul>
 * <li>{@code auto} — Gatling if on classpath, else JDK fallback</li>
 * <li>{@code gatling} — require Gatling; throws if absent</li>
 * <li>{@code jdk} — always use JDK HttpClient + ExecutorService</li>
 * </ul>
 */
@TestFlyApi(since = "1.1.0")
public final class LoadTestRunner {

    private static final ThreadLocal<LoadTestMetrics> LAST_METRICS = new ThreadLocal<>();
    private static final JdkLoadEngine JDK_ENGINE = new JdkLoadEngine();

    private LoadTestRunner() {
    }

    /**
     * Executes the given load-test scenario and returns a fluent assertion handle.
     *
     * @param scenario the scenario to execute
     * @return assertions wrapping the collected metrics
     * @throws IllegalStateException if no engine is available
     */
    public static LoadTestAssert run(LoadScenario scenario) {
        Class<?> testClass = io.testfly.internal.TestFlyContext.getCurrentTestClass();
        java.lang.reflect.Method testMethod = io.testfly.internal.TestFlyContext.getCurrentTestMethod();
        LoadTestConfig config = LoadTestConfig.resolveFor(scenario, testClass, testMethod);

        StepLogger.step("Load Test: " + scenario.name()
                + " (" + config.getUsers() + " users, engine=" + config.getEngine() + ")");

        LoadTestEngine engine = selectEngine(config);
        LoadTestMetrics metrics = engine.execute(scenario, config);

        LAST_METRICS.set(metrics);

        return new LoadTestAssert(metrics);
    }

    /**
     * Returns the metrics from the most recent load-test execution on this thread.
     */
    public static LoadTestMetrics lastMetrics() {
        return LAST_METRICS.get();
    }

    /**
     * Clears the thread-local metrics (called by the framework after each test).
     */
    public static void clearLastMetrics() {
        LAST_METRICS.remove();
    }

    private static LoadTestEngine selectEngine(LoadTestConfig config) {
        String engine = config.getEngine();

        if ("jdk".equals(engine)) {
            return JDK_ENGINE;
        } else if ("gatling".equals(engine)) {
            if (!GatlingBridge.isAvailable()) {
                throw new IllegalStateException(GatlingBridge.missingDependencyMessage());
            }
            return new GatlingEngine();
        } else if ("auto".equals(engine)) {
            if (GatlingBridge.isAvailable()) {
                return new GatlingEngine();
            }
            return JDK_ENGINE;
        } else {
            throw new IllegalArgumentException(
                    "[LoadTest] Unknown engine: '" + engine + "'. Valid values: auto, gatling, jdk");
        }
    }
}
