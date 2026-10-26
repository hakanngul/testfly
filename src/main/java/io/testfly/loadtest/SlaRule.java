package io.testfly.loadtest;

import io.testfly.api.TestFlyApi;

/**
 * One service-level rule of a {@link LoadSla}: a metric of a scope compared with a threshold.
 *
 * <p>{@code scope} is {@value #GLOBAL} for the whole result, or a step name. In a {@link LoadSimulation} a step
 * scope is {@code "<scenario> :: <step>"}, or a bare step name that is unique across the scenarios.
 *
 * <p>Evolution: new metrics (and comparisons) may be added in minor releases, so a {@code switch} over
 * {@link Metric} or {@link Comparison} needs a {@code default} branch. The record components are stable.
 *
 * @param scope      {@value #GLOBAL} or a step name
 * @param metric     the measured value
 * @param comparison {@link Comparison#BELOW}: passes iff {@code actual < threshold}; {@link Comparison#ABOVE}:
 *                   passes iff {@code actual > threshold}
 * @param threshold  milliseconds for latencies, 0.0..1.0 for the error rate, requests/s for the throughput
 */
@TestFlyApi(since = "1.1.0")
public record SlaRule(String scope, Metric metric, Comparison comparison, double threshold) {

    /** Scope of a rule on the whole result rather than one step. */
    public static final String GLOBAL = "global";

    /** The measured value of a rule. More may be added in minor releases. */
    @TestFlyApi(since = "1.1.0")
    public enum Metric {
        P50, P75, P90, P95, P99, MEAN, MAX, ERROR_RATE, THROUGHPUT
    }

    /** How the actual value is compared with the threshold (both strict). More may be added in minor releases. */
    @TestFlyApi(since = "1.1.0")
    public enum Comparison {
        BELOW, ABOVE
    }

    /** {@code true} if the rule is on the whole result rather than one step. */
    @TestFlyApi(since = "1.1.0")
    public boolean isGlobal() {
        return GLOBAL.equals(scope);
    }
}
