package io.testfly.loadtest;

import io.testfly.api.TestFlyApi;
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
 *
 * <p>
 * Full engine implementations arrive in Sprint 2 (JDK) and Sprint 3 (Gatling).
 * This Sprint 1 skeleton validates the wiring and config resolution.
 */
@TestFlyApi(since = "1.1.0")
public final class LoadTestRunner {

    private static final ThreadLocal<LoadTestMetrics> LAST_METRICS = new ThreadLocal<>();

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
        LoadTestConfig config = LoadTestConfig.resolveFor(scenario, null, null);

        StepLogger.step("Load Test: " + scenario.name()
                + " (" + config.getUsers() + " users, engine=" + config.getEngine() + ")");

        LoadTestMetrics metrics = execute(scenario, config);

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

    private static LoadTestMetrics execute(LoadScenario scenario, LoadTestConfig config) {
        String engine = config.getEngine();

        // Sprint 1: no engine implemented yet — clear message per engine choice
        // Sprint 2: JdkLoadEngine
        // Sprint 3: GatlingEngine + auto-selection
        if ("gatling".equals(engine)) {
            throw new IllegalStateException(
                    "[LoadTest] Gatling engine not yet implemented (Sprint 3). " +
                            "Use engine: jdk or engine: auto for now.");
        } else if ("jdk".equals(engine)) {
            throw new IllegalStateException(
                    "[LoadTest] JDK engine not yet implemented (Sprint 2). " +
                            "Coming in the next sprint.");
        } else if ("auto".equals(engine)) {
            throw new IllegalStateException(
                    "[LoadTest] No load-test engine available yet. " +
                            "JDK engine arrives in Sprint 2, Gatling engine in Sprint 3. " +
                            "Current scenario: '" + scenario.name() + "' with " +
                            config.getUsers() + " users.");
        } else {
            throw new IllegalArgumentException(
                    "[LoadTest] Unknown engine: '" + engine + "'. Valid values: auto, gatling, jdk");
        }
    }
}
