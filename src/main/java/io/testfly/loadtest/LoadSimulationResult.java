package io.testfly.loadtest;

import io.testfly.api.TestFlyApi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Results of a {@link LoadSimulation} run: one {@link LoadTestMetrics} per scenario and one for all scenarios
 * together ({@code "<simulation> (global)"}).
 *
 * <p>Per-scenario step metrics are keyed by the step name; global step metrics by the request name
 * ({@code "<scenario> :: <step>"} when there is more than one scenario). A scenario's throughput is its
 * request total divided by the whole run's duration.
 *
 * <p>{@link LoadTestMetrics#partial()} is per scenario for requests without a recorded outcome, but a stop by
 * Gatling's {@code maxDuration} applies to the whole run: every scenario, and the global metrics, are then
 * flagged partial. Immutable.
 */
@TestFlyApi(since = "1.1.0")
public final class LoadSimulationResult {

    private final LoadTestMetrics global;
    private final Map<String, LoadTestMetrics> scenarios;

    LoadSimulationResult(LoadTestMetrics global, Map<String, LoadTestMetrics> scenarios) {
        this.global = global;
        this.scenarios = Collections.unmodifiableMap(new LinkedHashMap<>(scenarios));
    }

    /** Assertions on all scenarios together. */
    @TestFlyApi(since = "1.1.0")
    public LoadTestAssert global() {
        return new LoadTestAssert(global);
    }

    /**
     * Assertions on one scenario.
     *
     * @throws IllegalArgumentException if the simulation has no scenario with that name
     */
    @TestFlyApi(since = "1.1.0")
    public LoadTestAssert scenario(String name) {
        return new LoadTestAssert(scenarioMetrics(name));
    }

    /** Scenario names in insertion order. */
    @TestFlyApi(since = "1.1.0")
    public List<String> scenarioNames() {
        return List.copyOf(scenarios.keySet());
    }

    /** Metrics of all scenarios together. */
    @TestFlyApi(since = "1.1.0")
    public LoadTestMetrics globalMetrics() {
        return global;
    }

    /**
     * Metrics of one scenario.
     *
     * @throws IllegalArgumentException if the simulation has no scenario with that name
     */
    @TestFlyApi(since = "1.1.0")
    public LoadTestMetrics scenarioMetrics(String name) {
        LoadTestMetrics m = scenarios.get(name);
        if (m == null) {
            throw new IllegalArgumentException("[LoadTest] No scenario '" + name + "' in this simulation; scenarios: "
                    + scenarios.keySet());
        }
        return m;
    }
}
