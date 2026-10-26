package io.testfly.loadtest;

import io.testfly.api.TestFlyApi;

/**
 * Rules on one step of a {@link LoadSla}, created by {@link LoadSla#step(String)}. Every method adds a rule and
 * returns the parent {@link LoadSla}, so the chain continues there.
 */
@TestFlyApi(since = "1.1.0")
public final class StepSla {

    private final LoadSla parent;
    private final String stepName;

    StepSla(LoadSla parent, String stepName) {
        this.parent = parent;
        this.stepName = stepName;
    }

    /** The step's p95 latency strictly below {@code ms}. */
    @TestFlyApi(since = "1.1.0")
    public LoadSla p95Below(double ms) {
        return parent.add(stepName, SlaRule.Metric.P95, SlaRule.Comparison.BELOW, LoadSla.latency(ms));
    }

    /** The step's p99 latency strictly below {@code ms}. */
    @TestFlyApi(since = "1.1.0")
    public LoadSla p99Below(double ms) {
        return parent.add(stepName, SlaRule.Metric.P99, SlaRule.Comparison.BELOW, LoadSla.latency(ms));
    }

    /** The step's error rate (0.0..1.0) strictly below {@code rate}. */
    @TestFlyApi(since = "1.1.0")
    public LoadSla errorRateBelow(double rate) {
        return parent.add(stepName, SlaRule.Metric.ERROR_RATE, SlaRule.Comparison.BELOW, LoadSla.rate(rate));
    }
}
