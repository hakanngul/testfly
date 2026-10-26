package io.testfly.unit.loadtest;

import io.testfly.loadtest.LoadBaseline;
import io.testfly.loadtest.LoadTestAssert;
import io.testfly.loadtest.LoadTestMetrics;
import io.testfly.loadtest.RegressionResult;
import io.testfly.loadtest.Tolerance;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Test helper for the load-test baseline API of ADR 0004 decision 6 (Phase 4): {@link LoadBaseline},
 * {@link Tolerance}, {@link RegressionResult} and the {@link LoadTestAssert} additions
 * {@link LoadTestAssert#assertNoRegression(LoadBaseline, Tolerance)} and
 * {@link LoadTestAssert#regressionResults(LoadBaseline, Tolerance)}.
 *
 * <p>The red tests were written before that API existed and reached it through reflection. Every method is now a
 * direct call; the {@code Object}-typed signatures are kept so the tests stay unchanged. Exceptions thrown by the API
 * (for example {@link IllegalArgumentException} or the {@link AssertionError} of {@code assertNoRegression}) pass
 * through unchanged.
 */
public final class BaselineApi {

    public static final String LOAD_BASELINE = "io.testfly.loadtest.LoadBaseline";
    public static final String TOLERANCE = "io.testfly.loadtest.Tolerance";
    public static final String REGRESSION_RESULT = "io.testfly.loadtest.RegressionResult";

    /** The per-run artefact written into each run directory. */
    public static final String RESULT_FILE = LoadBaseline.RESULT_FILE;

    /** The reason of a {@code RegressionResult} whose metric is unmeasured on either side. */
    public static final String NOT_COMPARED = LoadBaseline.NOT_COMPARED;

    private BaselineApi() {
    }

    // ── LoadBaseline ─────────────────────────────────────────────────────

    /** {@code LoadBaseline.of(metrics)}. */
    public static Object of(LoadTestMetrics metrics) {
        return LoadBaseline.of(metrics);
    }

    /** {@code LoadBaseline.load(file)}. */
    public static Object load(Path file) {
        return LoadBaseline.load(file);
    }

    /** {@code baseline.save(file)}. */
    public static void save(Object baseline, Path file) {
        b(baseline).save(file);
    }

    public static String scenarioName(Object baseline) {
        return b(baseline).scenarioName();
    }

    public static String engine(Object baseline) {
        return b(baseline).engine();
    }

    public static Instant createdAt(Object baseline) {
        return b(baseline).createdAt();
    }

    public static OptionalDouble p95(Object baseline) {
        return b(baseline).p95();
    }

    public static OptionalDouble p99(Object baseline) {
        return b(baseline).p99();
    }

    public static OptionalDouble mean(Object baseline) {
        return b(baseline).mean();
    }

    public static OptionalDouble errorRate(Object baseline) {
        return b(baseline).errorRate();
    }

    public static OptionalDouble throughput(Object baseline) {
        return b(baseline).throughput();
    }

    // ── Tolerance ────────────────────────────────────────────────────────

    /** {@code Tolerance.percent(pct)}. */
    public static Object percent(double pct) {
        return Tolerance.percent(pct);
    }

    /** {@code tolerance.errorRateDelta(absolute)}; returns the resulting {@code Tolerance}. */
    public static Object errorRateDelta(Object tolerance, double absolute) {
        return t(tolerance).errorRateDelta(absolute);
    }

    /** {@code tolerance.percent()}. */
    public static double percentOf(Object tolerance) {
        return t(tolerance).percent();
    }

    /** {@code tolerance.errorRateDelta()}. */
    public static double errorRateDeltaOf(Object tolerance) {
        return t(tolerance).errorRateDelta();
    }

    // ── LoadTestAssert additions ─────────────────────────────────────────

    /** {@code loadTestAssert.assertNoRegression(baseline, tolerance)}; returns what the method returns. */
    public static Object assertNoRegression(LoadTestAssert loadTestAssert, Object baseline, Object tolerance) {
        return loadTestAssert.assertNoRegression(b(baseline), t(tolerance));
    }

    /** {@code loadTestAssert.regressionResults(baseline, tolerance)}: one {@code RegressionResult} per metric. */
    public static List<?> regressionResults(LoadTestAssert loadTestAssert, Object baseline, Object tolerance) {
        return loadTestAssert.regressionResults(b(baseline), t(tolerance));
    }

    // ── RegressionResult ─────────────────────────────────────────────────

    /** {@code new RegressionResult(metric, baseline, actual, regressed, reason)}. */
    public static Object regressionResult(String metric, double baseline, double actual, boolean regressed,
            String reason) {
        return new RegressionResult(metric, baseline, actual, regressed, reason);
    }

    public static String metric(Object result) {
        return r(result).metric();
    }

    public static double baseline(Object result) {
        return r(result).baseline();
    }

    public static double actual(Object result) {
        return r(result).actual();
    }

    public static boolean regressed(Object result) {
        return r(result).regressed();
    }

    public static String reason(Object result) {
        return r(result).reason();
    }

    /** The result for {@code metric} in {@code results}; {@link AssertionError} when absent. */
    public static Object resultFor(List<?> results, String metric) {
        for (Object r : results) {
            if (metric.equals(metric(r))) {
                return r;
            }
        }
        throw new AssertionError("no RegressionResult for '" + metric + "' in " + results);
    }

    /** The class of a baseline API type (kept for the tests' {@code requireBaselineApi()} guard). */
    public static Class<?> type(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new UnsupportedOperationException("Baseline API missing: " + name, e);
        }
    }

    private static LoadBaseline b(Object baseline) {
        return (LoadBaseline) baseline;
    }

    private static Tolerance t(Object tolerance) {
        return (Tolerance) tolerance;
    }

    private static RegressionResult r(Object result) {
        return (RegressionResult) result;
    }
}
