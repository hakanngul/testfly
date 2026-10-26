package io.testfly.unit.loadtest;

import io.testfly.loadtest.LoadScenario;
import io.testfly.loadtest.LoadSimulation;
import io.testfly.loadtest.LoadSimulationResult;
import io.testfly.loadtest.LoadSla;
import io.testfly.loadtest.LoadTestAssert;
import io.testfly.loadtest.LoadTestMetrics;
import io.testfly.loadtest.SlaResult;
import io.testfly.loadtest.SlaRule;
import io.testfly.loadtest.StepSla;
import io.testfly.loadtest.internal.SlaEvaluator;
import io.testfly.test.support.LoadTestSupport;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

/**
 * Test helper for the load-test SLA API of ADR 0004 decision 5 (Phase 3): {@link LoadSla}, {@link StepSla},
 * {@link SlaRule}, {@link SlaResult}, the {@code sla(..)} attachment points on {@link LoadScenario} and
 * {@link LoadSimulation}, {@link LoadTestSupport#sla()}, {@link LoadTestMetrics#slaResults()},
 * {@link LoadTestAssert#slaResults()}/{@link LoadTestAssert#assertSlaPassed()},
 * {@link LoadSimulationResult#slaResults()}, plus the internal entry points {@link SlaEvaluator#evaluate} and the
 * package-private {@code GatlingEngine.checkExitCode(int, String, File, boolean)}.
 *
 * <p>The red tests were written before that API existed and reached it through reflection. Every method is now a
 * direct call with the {@code Object}-typed signatures kept, so the tests stay unchanged; only
 * {@link #checkExitCode} still uses reflection, because that method is package-private.
 */
public final class SlaApi {

    public static final String LOAD_SLA = "io.testfly.loadtest.LoadSla";
    public static final String STEP_SLA = "io.testfly.loadtest.StepSla";
    public static final String SLA_RULE = "io.testfly.loadtest.SlaRule";
    public static final String SLA_RESULT = "io.testfly.loadtest.SlaResult";
    public static final String SLA_EVALUATOR = "io.testfly.loadtest.internal.SlaEvaluator";
    public static final String GATLING_ENGINE = "io.testfly.loadtest.internal.GatlingEngine";

    private SlaApi() {
    }

    // ── LoadSla / StepSla builders ───────────────────────────────────────

    /** {@code LoadSla.sla()}. */
    public static Object sla() {
        return LoadSla.sla();
    }

    /** {@code support.sla()} (default method of {@link LoadTestSupport}). */
    public static Object sla(LoadTestSupport support) {
        return support.sla();
    }

    /** {@code true} if {@code LoadTestSupport.sla()} exists and is a default method. */
    public static boolean slaIsDefaultMethodOnSupport() {
        try {
            return LoadTestSupport.class.getMethod("sla").isDefault();
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    public static Object p50Below(Object sla, double ms) {
        return sla(sla).p50Below(ms);
    }

    public static Object p75Below(Object sla, double ms) {
        return sla(sla).p75Below(ms);
    }

    public static Object p90Below(Object sla, double ms) {
        return sla(sla).p90Below(ms);
    }

    public static Object p95Below(Object sla, double ms) {
        return sla(sla).p95Below(ms);
    }

    public static Object p99Below(Object sla, double ms) {
        return sla(sla).p99Below(ms);
    }

    public static Object meanBelow(Object sla, double ms) {
        return sla(sla).meanBelow(ms);
    }

    public static Object maxBelow(Object sla, double ms) {
        return sla(sla).maxBelow(ms);
    }

    public static Object errorRateBelow(Object sla, double rate) {
        return sla(sla).errorRateBelow(rate);
    }

    public static Object throughputAbove(Object sla, double rps) {
        return sla(sla).throughputAbove(rps);
    }

    /** {@code sla.step(name)}; returns the {@code StepSla}. */
    public static Object step(Object sla, String stepName) {
        return sla(sla).step(stepName);
    }

    /** {@code stepSla.p95Below(ms)}; returns the parent {@code LoadSla}. */
    public static Object stepP95Below(Object stepSla, double ms) {
        return ((StepSla) stepSla).p95Below(ms);
    }

    /** {@code stepSla.p99Below(ms)}; returns the parent {@code LoadSla}. */
    public static Object stepP99Below(Object stepSla, double ms) {
        return ((StepSla) stepSla).p99Below(ms);
    }

    /** {@code stepSla.errorRateBelow(rate)}; returns the parent {@code LoadSla}. */
    public static Object stepErrorRateBelow(Object stepSla, double rate) {
        return ((StepSla) stepSla).errorRateBelow(rate);
    }

    /** {@code sla.rules()}: the declared {@code SlaRule}s. */
    public static List<?> rules(Object sla) {
        return sla(sla).rules();
    }

    // ── SlaRule / SlaResult ──────────────────────────────────────────────

    /** {@code new SlaRule(scope, Metric.valueOf(metric), Comparison.valueOf(comparison), threshold)}. */
    public static Object rule(String scope, String metric, String comparison, double threshold) {
        return new SlaRule(scope, SlaRule.Metric.valueOf(metric), SlaRule.Comparison.valueOf(comparison), threshold);
    }

    public static String scope(Object rule) {
        return ((SlaRule) rule).scope();
    }

    /** {@code rule.metric().name()}. */
    public static String metric(Object rule) {
        return ((SlaRule) rule).metric().name();
    }

    /** {@code rule.comparison().name()}. */
    public static String comparison(Object rule) {
        return ((SlaRule) rule).comparison().name();
    }

    public static double threshold(Object rule) {
        return ((SlaRule) rule).threshold();
    }

    /** Names of {@code SlaRule.Metric} in declaration order. */
    public static List<String> metricNames() {
        return Arrays.stream(SlaRule.Metric.values()).map(Enum::name).toList();
    }

    /** Names of {@code SlaRule.Comparison} in declaration order. */
    public static List<String> comparisonNames() {
        return Arrays.stream(SlaRule.Comparison.values()).map(Enum::name).toList();
    }

    /** {@code new SlaResult(rule, actual, passed, reason)}. */
    public static Object result(Object rule, double actual, boolean passed, String reason) {
        return new SlaResult((SlaRule) rule, actual, passed, reason);
    }

    public static Object resultRule(Object result) {
        return ((SlaResult) result).rule();
    }

    public static double actual(Object result) {
        return ((SlaResult) result).actual();
    }

    public static boolean passed(Object result) {
        return ((SlaResult) result).passed();
    }

    public static String reason(Object result) {
        return ((SlaResult) result).reason();
    }

    // ── attachment points ────────────────────────────────────────────────

    /** {@code scenario.sla(sla)}; returns the scenario. */
    public static LoadScenario scenarioSla(LoadScenario scenario, Object sla) {
        return scenario.sla(sla(sla));
    }

    /** {@code scenario.sla()}: the attached {@code LoadSla} or {@code null}. */
    public static Object scenarioSlaOf(LoadScenario scenario) {
        return scenario.sla();
    }

    /** {@code simulation.sla(sla)}; returns the simulation. */
    public static Object simulationSla(Object simulation, Object sla) {
        return ((LoadSimulation) simulation).sla(sla(sla));
    }

    // ── results ──────────────────────────────────────────────────────────

    /**
     * {@code holder.slaResults()} on a {@code LoadTestMetrics}, a {@code LoadTestAssert} or a
     * {@code LoadSimulationResult}.
     */
    public static List<?> slaResults(Object holder) {
        if (holder instanceof LoadTestMetrics m) {
            return m.slaResults();
        }
        if (holder instanceof LoadTestAssert a) {
            return a.slaResults();
        }
        if (holder instanceof LoadSimulationResult r) {
            return r.slaResults();
        }
        throw new IllegalArgumentException("no slaResults() on " + holder);
    }

    /** {@code loadTestAssert.assertSlaPassed()}; returns the assert. */
    public static Object assertSlaPassed(Object loadTestAssert) {
        return ((LoadTestAssert) loadTestAssert).assertSlaPassed();
    }

    /**
     * Copy of {@code m} through the canonical {@code LoadTestMetrics} constructor (the 20 earlier components plus
     * the trailing {@code List<SlaResult> slaResults}).
     */
    @SuppressWarnings("unchecked")
    public static LoadTestMetrics withSlaResults(LoadTestMetrics m, List<?> slaResults) {
        return new LoadTestMetrics(m.scenarioName(), m.totalRequests(), m.successfulRequests(), m.failedRequests(),
                m.throughputRps(), m.meanLatencyMs(), m.p50LatencyMs(), m.p90LatencyMs(), m.p95LatencyMs(),
                m.p99LatencyMs(), m.minLatencyMs(), m.maxLatencyMs(), m.errorRate(), m.statusCodes(),
                m.durationMs(), m.users(), m.engine(), m.steps(), m.p75LatencyMs(), m.partial(),
                (List<SlaResult>) slaResults);
    }

    // ── internal entry points ────────────────────────────────────────────

    /** {@code SlaEvaluator.evaluate(sla, metrics)}: one {@code SlaResult} per rule, in declaration order. */
    public static List<?> evaluate(Object sla, LoadTestMetrics metrics) {
        return SlaEvaluator.evaluate(sla(sla), metrics);
    }

    /**
     * {@link #checkExitCode(int, String, File, boolean)} for a run with native assertions registered: {@code false}
     * for 0, {@code true} for 2 (Gatling assertions failed, not a crash), {@link IllegalStateException} otherwise.
     */
    public static boolean checkExitCode(int exitCode, String runDescription, File logFile) {
        return checkExitCode(exitCode, runDescription, logFile, true);
    }

    /**
     * {@code GatlingEngine.checkExitCode(exitCode, runDescription, logFile, nativeAssertionsRegistered)}
     * (package-private static). Without native assertions, exit code 2 is a crash like any other non-zero code.
     */
    public static boolean checkExitCode(int exitCode, String runDescription, File logFile,
            boolean nativeAssertionsRegistered) {
        Method m;
        try {
            m = type(GATLING_ENGINE).getDeclaredMethod("checkExitCode", int.class, String.class, File.class,
                    boolean.class);
        } catch (NoSuchMethodException e) {
            throw missing("static boolean GatlingEngine.checkExitCode(int, String, File, boolean)", e);
        }
        if (!Modifier.isStatic(m.getModifiers()) || m.getReturnType() != boolean.class) {
            throw missing("GatlingEngine.checkExitCode must be static and return boolean, was " + m, null);
        }
        m.setAccessible(true);
        try {
            return (Boolean) m.invoke(null, exitCode, runDescription, logFile, nativeAssertionsRegistered);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause instanceof Error err) {
                throw err;
            }
            throw new IllegalStateException(cause);
        } catch (IllegalAccessException e) {
            throw missing("GatlingEngine.checkExitCode not accessible", e);
        }
    }

    public static Class<?> type(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw missing(name, e);
        }
    }

    private static LoadSla sla(Object sla) {
        return (LoadSla) sla;
    }

    private static UnsupportedOperationException missing(String what, Throwable cause) {
        UnsupportedOperationException e = new UnsupportedOperationException("SLA API missing: " + what);
        if (cause != null) {
            e.initCause(cause);
        }
        return e;
    }
}
