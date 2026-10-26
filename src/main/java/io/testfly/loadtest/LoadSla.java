package io.testfly.loadtest;

import io.testfly.api.TestFlyApi;

import java.util.ArrayList;
import java.util.List;

/**
 * Service-level agreement of a load test: rules on the global result or on single steps, evaluated by TestFly from
 * {@link LoadTestMetrics} after the run (both engines). Attach it with {@link LoadScenario#sla(LoadSla)} or
 * {@link LoadSimulation#sla(LoadSla)}; {@code run()} then records the metrics and throws {@link AssertionError} if a
 * rule fails ({@link LoadTestAssert#assertSlaPassed()}).
 *
 * <pre>
 * loadScenario("Checkout")
 *         .sla(sla().p95Below(300).errorRateBelow(0.01).step("pay").p99Below(800))
 *         ...
 *         .run();
 * </pre>
 *
 * <p>Comparisons are strict: {@code xBelow(t)} passes iff {@code actual < t}, {@code throughputAbove(t)} iff
 * {@code actual > t}. An unmeasured value, a missing step or partial metrics fail the rule. On Gatling the rules are
 * also registered as native assertions, for Gatling's own report only; TestFly's verdict is authoritative.
 *
 * <p>A mutable builder (like {@link LoadScenario}): declare the rules on one thread, then run.
 */
@TestFlyApi(since = "1.1.0")
public final class LoadSla {

    private final List<SlaRule> rules = new ArrayList<>();

    private LoadSla() {
    }

    /** A new SLA without rules. */
    @TestFlyApi(since = "1.1.0")
    public static LoadSla sla() {
        return new LoadSla();
    }

    /** Global p50 latency strictly below {@code ms}. */
    @TestFlyApi(since = "1.1.0")
    public LoadSla p50Below(double ms) {
        return add(SlaRule.GLOBAL, SlaRule.Metric.P50, SlaRule.Comparison.BELOW, latency(ms));
    }

    /** Global p75 latency strictly below {@code ms}. */
    @TestFlyApi(since = "1.1.0")
    public LoadSla p75Below(double ms) {
        return add(SlaRule.GLOBAL, SlaRule.Metric.P75, SlaRule.Comparison.BELOW, latency(ms));
    }

    /** Global p90 latency strictly below {@code ms}. */
    @TestFlyApi(since = "1.1.0")
    public LoadSla p90Below(double ms) {
        return add(SlaRule.GLOBAL, SlaRule.Metric.P90, SlaRule.Comparison.BELOW, latency(ms));
    }

    /** Global p95 latency strictly below {@code ms}. */
    @TestFlyApi(since = "1.1.0")
    public LoadSla p95Below(double ms) {
        return add(SlaRule.GLOBAL, SlaRule.Metric.P95, SlaRule.Comparison.BELOW, latency(ms));
    }

    /** Global p99 latency strictly below {@code ms}. */
    @TestFlyApi(since = "1.1.0")
    public LoadSla p99Below(double ms) {
        return add(SlaRule.GLOBAL, SlaRule.Metric.P99, SlaRule.Comparison.BELOW, latency(ms));
    }

    /** Global mean latency strictly below {@code ms}. */
    @TestFlyApi(since = "1.1.0")
    public LoadSla meanBelow(double ms) {
        return add(SlaRule.GLOBAL, SlaRule.Metric.MEAN, SlaRule.Comparison.BELOW, latency(ms));
    }

    /** Global max latency strictly below {@code ms}. */
    @TestFlyApi(since = "1.1.0")
    public LoadSla maxBelow(double ms) {
        return add(SlaRule.GLOBAL, SlaRule.Metric.MAX, SlaRule.Comparison.BELOW, latency(ms));
    }

    /** Global error rate (0.0..1.0) strictly below {@code rate}. */
    @TestFlyApi(since = "1.1.0")
    public LoadSla errorRateBelow(double rate) {
        return add(SlaRule.GLOBAL, SlaRule.Metric.ERROR_RATE, SlaRule.Comparison.BELOW, rate(rate));
    }

    /** Global throughput (requests/s) strictly above {@code rps}. */
    @TestFlyApi(since = "1.1.0")
    public LoadSla throughputAbove(double rps) {
        return add(SlaRule.GLOBAL, SlaRule.Metric.THROUGHPUT, SlaRule.Comparison.ABOVE, finite("throughput", rps));
    }

    /**
     * Rules on one step. In a {@link LoadSimulation} use {@code "<scenario> :: <step>"}, or a bare step name that is
     * unique across the scenarios.
     *
     * @throws IllegalArgumentException if the name is null, blank or {@value SlaRule#GLOBAL}
     */
    @TestFlyApi(since = "1.1.0")
    public StepSla step(String stepName) {
        if (stepName == null || stepName.isBlank()) {
            throw new IllegalArgumentException("[LoadTest] sla().step(..) needs a non-blank step name");
        }
        if (SlaRule.GLOBAL.equals(stepName)) {
            throw new IllegalArgumentException("[LoadTest] sla().step(\"global\"): 'global' is the scope of the "
                    + "global rules; use the global methods of LoadSla");
        }
        return new StepSla(this, stepName);
    }

    /**
     * The declared rules in declaration order: an immutable snapshot, so a run evaluates exactly the rules it saw
     * when it started even if the builder is changed later.
     */
    @TestFlyApi(since = "1.1.0")
    public List<SlaRule> rules() {
        return List.copyOf(rules);
    }

    @Override
    public String toString() {
        return "LoadSla" + rules;
    }

    LoadSla add(String scope, SlaRule.Metric metric, SlaRule.Comparison comparison, double threshold) {
        rules.add(new SlaRule(scope, metric, comparison, threshold));
        return this;
    }

    static double latency(double ms) {
        return finite("latency", ms);
    }

    static double rate(double rate) {
        if (!(rate >= 0 && rate <= 1)) {
            throw new IllegalArgumentException("[LoadTest] SLA error rate must be within 0.0..1.0, was " + rate);
        }
        return rate;
    }

    private static double finite(String what, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("[LoadTest] SLA " + what + " threshold must be finite, was " + value);
        }
        return value;
    }
}
