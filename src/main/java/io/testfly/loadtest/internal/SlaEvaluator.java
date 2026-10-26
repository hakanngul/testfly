package io.testfly.loadtest.internal;

import io.testfly.loadtest.LoadSla;
import io.testfly.loadtest.LoadTestMetrics;
import io.testfly.loadtest.SlaResult;
import io.testfly.loadtest.SlaRule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Evaluates SLA rules against {@link LoadTestMetrics}: TestFly's verdict, the same on every engine (ADR 0004
 * decision 5).
 *
 * <p>Per rule, in this order: partial metrics fail with {@value #PARTIAL}; a step absent from the metrics fails with
 * {@value #STEP_NOT_FOUND}; a {@link Double#NaN} value fails with {@value #NOT_MEASURED}; otherwise {@code BELOW}
 * passes iff {@code actual < threshold} and {@code ABOVE} iff {@code actual > threshold}.
 *
 * <p>Step scopes: a scenario's own metrics (and {@link #evaluate(LoadSla, LoadTestMetrics)}) match the step name
 * exactly. A simulation's global metrics resolve the scope with {@link #simulationStepKey}.
 */
public final class SlaEvaluator {

    static final String PARTIAL = "metrics are partial";
    static final String STEP_NOT_FOUND = "step not found";
    static final String NOT_MEASURED = "not measured";

    private static final String SEP = LoadScenarioValidator.REQUEST_NAME_SEPARATOR;

    private SlaEvaluator() {
    }

    /** One {@link SlaResult} per rule of {@code sla}, in declaration order; step scopes match exactly. */
    public static List<SlaResult> evaluate(LoadSla sla, LoadTestMetrics metrics) {
        return evaluate(sla == null ? List.of() : sla.rules(), metrics);
    }

    /** One {@link SlaResult} per rule, in order; step scopes match the step keys of {@code metrics} exactly. */
    public static List<SlaResult> evaluate(List<SlaRule> rules, LoadTestMetrics metrics) {
        List<SlaResult> results = new ArrayList<>();
        for (SlaRule rule : rules) {
            String key = rule.isGlobal() ? null : (steps(metrics).containsKey(rule.scope()) ? rule.scope() : null);
            results.add(evaluate(rule, metrics, key));
        }
        return results;
    }

    /**
     * One {@link SlaResult} per simulation-level rule against the simulation's global metrics, resolving each step
     * scope with {@link #simulationStepKey} over the given scenario names.
     */
    public static List<SlaResult> evaluateSimulation(List<SlaRule> rules, LoadTestMetrics global,
            List<String> scenarioNames) {
        List<SlaResult> results = new ArrayList<>();
        for (SlaRule rule : rules) {
            String key = rule.isGlobal() ? null
                    : simulationStepKey(steps(global).keySet(), rule.scope(), scenarioNames);
            results.add(evaluate(rule, global, key));
        }
        return results;
    }

    /**
     * Resolves a simulation SLA step scope to a key of {@code keys} (the run's step request names), or {@code null}.
     * <ul>
     *   <li>One scenario (keys unprefixed): a leading {@code "<scenario> :: "} is stripped, then the scope must equal
     *       a key exactly.</li>
     *   <li>Several scenarios (keys {@code "<scenario> :: <step>"}): an exact key wins; otherwise the scope is a bare
     *       step name and must equal the part after the FIRST {@code " :: "} of exactly one key (scenario names
     *       cannot contain {@code " :: "}, step names can).</li>
     * </ul>
     */
    public static String simulationStepKey(Collection<String> keys, String scope, List<String> scenarioNames) {
        if (scenarioNames.size() == 1) {
            String prefix = scenarioNames.get(0) + SEP;
            String step = scope.startsWith(prefix) ? scope.substring(prefix.length()) : scope;
            return keys.contains(step) ? step : null;
        }
        if (keys.contains(scope)) {
            return scope;
        }
        List<String> candidates = bareCandidates(keys, scope);
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    /** The prefixed keys whose step part (after the first {@code " :: "}) equals {@code step}. */
    public static List<String> bareCandidates(Collection<String> keys, String step) {
        List<String> out = new ArrayList<>();
        for (String key : keys) {
            int i = key.indexOf(SEP);
            if (i >= 0 && key.substring(i + SEP.length()).equals(step)) {
                out.add(key);
            }
        }
        return out;
    }

    private static Map<String, LoadTestMetrics.StepMetrics> steps(LoadTestMetrics m) {
        return m.steps() != null ? m.steps() : Map.of();
    }

    /** Evaluates one rule; {@code stepKey} is the resolved step key ({@code null}: global, or not found). */
    private static SlaResult evaluate(SlaRule rule, LoadTestMetrics m, String stepKey) {
        LoadTestMetrics.StepMetrics step = stepKey == null ? null : steps(m).get(stepKey);
        boolean stepMissing = !rule.isGlobal() && step == null;
        double actual = stepMissing ? Double.NaN : value(rule, m, step);
        if (m.partial()) {
            return new SlaResult(rule, actual, false, PARTIAL);
        }
        if (stepMissing) {
            return new SlaResult(rule, Double.NaN, false, STEP_NOT_FOUND);
        }
        if (Double.isNaN(actual)) {
            return new SlaResult(rule, Double.NaN, false, NOT_MEASURED);
        }
        boolean below = rule.comparison() == SlaRule.Comparison.BELOW;
        boolean passed = below ? actual < rule.threshold() : actual > rule.threshold();
        return new SlaResult(rule, actual, passed, passed ? null
                : actual + " is not " + (below ? "below " : "above ") + rule.threshold());
    }

    private static double value(SlaRule rule, LoadTestMetrics m, LoadTestMetrics.StepMetrics step) {
        if (step != null) {
            return switch (rule.metric()) {
                case P95 -> step.p95LatencyMs();
                case P99 -> step.p99LatencyMs();
                case ERROR_RATE -> step.errorRate();
                default -> Double.NaN; // per-step values not kept: not measured
            };
        }
        return switch (rule.metric()) {
            case P50 -> m.p50LatencyMs();
            case P75 -> m.p75LatencyMs();
            case P90 -> m.p90LatencyMs();
            case P95 -> m.p95LatencyMs();
            case P99 -> m.p99LatencyMs();
            case MEAN -> m.meanLatencyMs();
            case MAX -> m.maxLatencyMs();
            case ERROR_RATE -> m.errorRate();
            case THROUGHPUT -> m.throughputRps();
        };
    }
}
