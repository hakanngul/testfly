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

    /**
     * All SLA results of the run: the simulation-level SLA (on the global metrics) first, then each scenario's own
     * SLA in scenario order; empty when no SLA was declared.
     *
     * <p>The flattened list does not say which scenario a result belongs to (a scenario's rules carry only the step
     * name or {@code "global"}). For attributed results use {@code globalMetrics().slaResults()} for the
     * simulation-level SLA and {@code scenarioMetrics(name).slaResults()} for one scenario's SLA.
     */
    @TestFlyApi(since = "1.1.0")
    public List<SlaResult> slaResults() {
        List<SlaResult> all = new java.util.ArrayList<>(global.slaResults());
        scenarios.values().forEach(m -> all.addAll(m.slaResults()));
        return List.copyOf(all);
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
