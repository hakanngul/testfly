package io.testfly.loadtest.internal;

import io.testfly.loadtest.LoadScenario;
import io.testfly.loadtest.LoadTestConfig;
import io.testfly.loadtest.LoadTestMetrics;

/**
 * Strategy interface for load-test execution engines.
 *
 * <p>Two implementations:
 * <ul>
 *   <li>{@link JdkLoadEngine} — JDK HttpClient + ExecutorService (zero dependencies)</li>
 *   <li>{@code GatlingEngine} — Gatling async engine (Sprint 3, optional dependency)</li>
 * </ul>
 *
 * <p>Selected by {@link io.testfly.loadtest.LoadTestRunner} based on
 * {@code loadtest.engine} config ({@code auto}, {@code gatling}, {@code jdk}).
 */
public interface LoadTestEngine {

    /** Engine identifier: {@code "jdk"} or {@code "gatling"}. */
    String name();

    /** Returns {@code true} if this engine's dependencies are on the classpath. */
    boolean isAvailable();

    /**
     * Executes the load-test scenario and returns collected metrics.
     *
     * @param scenario the scenario definition (steps, feeders, think time)
     * @param config   resolved configuration (users, rampUp, hold, baseUrl, timeout)
     * @return immutable metrics snapshot
     */
    LoadTestMetrics execute(LoadScenario scenario, LoadTestConfig config);
}
