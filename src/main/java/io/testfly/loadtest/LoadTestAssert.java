package io.testfly.loadtest;

import io.testfly.api.TestFlyApi;

/**
 * Fluent assertion API for load-test results.
 *
 * <p>
 * Returned by {@link LoadScenario#run()}. Every assertion method returns
 * {@code this} for chaining:
 *
 * <pre>
 * load("/api/health")
 *         .users(100)
 *         .run()
 *         .assertThroughputAbove(500)
 *         .assertP95Below(200)
 *         .assertErrorRateBelow(0.01)
 *         .assertNoStatus(500);
 * </pre>
 *
 * <p>
 * Access raw metrics via {@link #metrics()} for custom assertions.
 *
 * <p>
 * Latency, status-code and error assertions, and {@link #assertThroughputBelow(double)}, fail with
 * "metrics are partial" when {@link LoadTestMetrics#partial()} is {@code true}: some requests were started
 * but have no recorded outcome (or the run was stopped early), so the figures are incomplete. A metric that
 * the engine did not measure ({@link Double#NaN}) fails its assertion as "not measured".
 *
 * <p>
 * Latency assertions use the engine's latency population, which differs for requests that received no
 * HTTP response (connection refused, timeout, a request that could not be built): on Gatling, latency
 * percentiles, min, max and mean cover only requests that received a response, and no-response requests
 * count in the error rate and under status {@code -1}; the JDK engine also includes the elapsed time of
 * connection failures and timeouts in the latency figures. See {@link LoadTestMetrics}.
 */
@TestFlyApi(since = "1.1.0")
public final class LoadTestAssert {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(LoadTestAssert.class);

    private final LoadTestMetrics metrics;

    public LoadTestAssert(LoadTestMetrics metrics) {
        this.metrics = metrics;
    }

    // ── Throughput ───────────────────────────────────────────────────────

    /** Asserts throughput (requests/sec) is strictly above the given value. */
    public LoadTestAssert assertThroughputAbove(double rps) {
        requireThroughputMeasured();
        if (metrics.throughputRps() <= rps) {
            throw new AssertionError(String.format(
                    "[LoadTest] %s: throughput %.1f req/s is not above %.1f req/s",
                    metrics.scenarioName(), metrics.throughputRps(), rps));
        }
        return this;
    }

    /**
     * Asserts throughput (requests/sec) is strictly below the given value. Fails on partial metrics: an
     * upper bound checked against undercounted requests would pass wrongly.
     */
    public LoadTestAssert assertThroughputBelow(double rps) {
        requireComplete("throughput");
        requireThroughputMeasured();
        if (metrics.throughputRps() >= rps) {
            throw new AssertionError(String.format(
                    "[LoadTest] %s: throughput %.1f req/s is not below %.1f req/s",
                    metrics.scenarioName(), metrics.throughputRps(), rps));
        }
        return this;
    }

    // ── Latency percentiles ──────────────────────────────────────────────

    /** Asserts p50 latency is below the given threshold (ms). */
    public LoadTestAssert assertP50Below(double ms) {
        return assertPercentileBelow("p50", metrics.p50LatencyMs(), ms);
    }

    /**
     * Asserts p75 latency is below the given threshold (ms).
     *
     * @throws AssertionError if the threshold is not met or p75 was not measured by the engine
     */
    @TestFlyApi(since = "1.1.0")
    public LoadTestAssert assertP75Below(double ms) {
        return assertPercentileBelow("p75", metrics.p75LatencyMs(), ms);
    }

    /**
     * Asserts p90 latency is below the given threshold (ms). Fails with a "not measured" error
     * when the engine did not measure p90.
     */
    public LoadTestAssert assertP90Below(double ms) {
        return assertPercentileBelow("p90", metrics.p90LatencyMs(), ms);
    }

    /** Asserts p95 latency is below the given threshold (ms). */
    public LoadTestAssert assertP95Below(double ms) {
        return assertPercentileBelow("p95", metrics.p95LatencyMs(), ms);
    }

    /** Asserts p99 latency is below the given threshold (ms). */
    public LoadTestAssert assertP99Below(double ms) {
        return assertPercentileBelow("p99", metrics.p99LatencyMs(), ms);
    }

    /** Asserts mean latency is below the given threshold (ms). */
    public LoadTestAssert assertMeanLatencyBelow(double ms) {
        return assertPercentileBelow("mean", metrics.meanLatencyMs(), ms);
    }

    /** Asserts max latency is below the given threshold (ms). */
    public LoadTestAssert assertMaxLatencyBelow(double ms) {
        return assertPercentileBelow("max", metrics.maxLatencyMs(), ms);
    }

    /** Asserts min latency is below the given threshold (ms). */
    public LoadTestAssert assertMinLatencyBelow(double ms) {
        return assertPercentileBelow("min", metrics.minLatencyMs(), ms);
    }

    /** Asserts total request count is strictly above the given minimum. */
    public LoadTestAssert assertTotalRequestsAbove(int count) {
        if (metrics.totalRequests() <= count) {
            throw new AssertionError(String.format(
                    "[LoadTest] %s: total requests %d is not above %d",
                    metrics.scenarioName(), metrics.totalRequests(), count));
        }
        return this;
    }

    /** Asserts successful request count is strictly above the given minimum. */
    public LoadTestAssert assertSuccessfulRequestsAbove(int count) {
        if (metrics.successfulRequests() <= count) {
            throw new AssertionError(String.format(
                    "[LoadTest] %s: successful requests %d is not above %d",
                    metrics.scenarioName(), metrics.successfulRequests(), count));
        }
        return this;
    }

    /** Asserts failed request count is strictly below the given threshold. */
    public LoadTestAssert assertFailedRequestsBelow(int count) {
        requireComplete("failed requests");
        if (metrics.failedRequests() >= count) {
            throw new AssertionError(String.format(
                    "[LoadTest] %s: failed requests %d is not below %d",
                    metrics.scenarioName(), metrics.failedRequests(), count));
        }
        return this;
    }

    // ── Error rate ───────────────────────────────────────────────────────

    /** Asserts error rate (0.0–1.0) is below the given threshold. */
    public LoadTestAssert assertErrorRateBelow(double rate) {
        requireComplete("error rate");
        if (metrics.errorRate() >= rate) {
            throw new AssertionError(String.format(
                    "[LoadTest] %s: error rate %.4f is not below %.4f (%d/%d failed)",
                    metrics.scenarioName(), metrics.errorRate(), rate,
                    metrics.failedRequests(), metrics.totalRequests()));
        }
        return this;
    }

    /** Asserts success rate (0.0–1.0) is above the given threshold. */
    public LoadTestAssert assertSuccessRateAbove(double rate) {
        requireComplete("success rate");
        double successRate = 1.0 - metrics.errorRate();
        if (successRate <= rate) {
            throw new AssertionError(String.format(
                    "[LoadTest] %s: success rate %.4f is not above %.4f",
                    metrics.scenarioName(), successRate, rate));
        }
        return this;
    }

    // ── Status codes ─────────────────────────────────────────────────────

    // Status-code assertions fail with "HTTP status codes were not measured" when
    // LoadTestMetrics#statusCodesMeasured() is false, instead of passing or failing on fabricated data.

    /** Asserts that at least one response had the given status code. */
    public LoadTestAssert assertStatus(int status) {
        return assertStatusCodeCount(status, 1);
    }

    /**
     * Asserts that at least {@code minCount} responses had the given status code.
     */
    public LoadTestAssert assertStatusCodeCount(int status, long minCount) {
        requireStatusCodesMeasured();
        long actual = metrics.statusCodeCount(status);
        if (actual < minCount) {
            throw new AssertionError(String.format(
                    "[LoadTest] %s: status %d count %d is below minimum %d",
                    metrics.scenarioName(), status, actual, minCount));
        }
        return this;
    }

    /** Asserts that no response had the given status code. */
    public LoadTestAssert assertNoStatus(int status) {
        requireStatusCodesMeasured();
        long count = metrics.statusCodeCount(status);
        if (count > 0) {
            throw new AssertionError(String.format(
                    "[LoadTest] %s: expected no status %d but got %d occurrences",
                    metrics.scenarioName(), status, count));
        }
        return this;
    }

    // ── Per-step assertions ──────────────────────────────────────────────

    /**
     * Asserts p95 latency for a specific step is below the given threshold (ms).
     */
    public LoadTestAssert assertStepP95Below(String stepName, double ms) {
        requireComplete("step '" + stepName + "' p95");
        LoadTestMetrics.StepMetrics step = metrics.steps().get(stepName);
        if (step == null) {
            throw new AssertionError(String.format(
                    "[LoadTest] %s: step '%s' not found in metrics", metrics.scenarioName(), stepName));
        }
        if (!LoadTestMetrics.isMeasured(step.p95LatencyMs())) {
            throw notMeasured("step '" + stepName + "' p95");
        }
        if (step.p95LatencyMs() >= ms) {
            throw new AssertionError(String.format(
                    "[LoadTest] %s → step '%s': p95 %.0fms is not below %.0fms",
                    metrics.scenarioName(), stepName, step.p95LatencyMs(), ms));
        }
        return this;
    }

    /** Asserts error rate for a specific step is below the given threshold. */
    public LoadTestAssert assertStepErrorRateBelow(String stepName, double rate) {
        requireComplete("step '" + stepName + "' error rate");
        LoadTestMetrics.StepMetrics step = metrics.steps().get(stepName);
        if (step == null) {
            throw new AssertionError(String.format(
                    "[LoadTest] %s: step '%s' not found in metrics", metrics.scenarioName(), stepName));
        }
        if (step.errorRate() >= rate) {
            throw new AssertionError(String.format(
                    "[LoadTest] %s → step '%s': error rate %.4f is not below %.4f",
                    metrics.scenarioName(), stepName, step.errorRate(), rate));
        }
        return this;
    }

    // ── Baseline regression ──────────────────────────────────────────────

    /**
     * Compares this run with a baseline without asserting: one {@link RegressionResult} per metric, in the order
     * p95, p99, mean, errorRate, throughput. A metric unmeasured on either side is not compared
     * ({@value LoadBaseline#NOT_COMPARED}). Logs a WARN when the baseline came from another engine or another
     * scenario; the comparison continues.
     */
    @TestFlyApi(since = "1.1.0")
    public java.util.List<RegressionResult> regressionResults(LoadBaseline baseline, Tolerance tolerance) {
        if (baseline == null || tolerance == null) {
            throw new IllegalArgumentException("[LoadTest] regressionResults needs a baseline and a tolerance");
        }
        if (baseline.engine() != null && !baseline.engine().equals(metrics.engine())) {
            LOGGER.warn("[LoadTest] {}: the baseline was recorded with engine '{}', this run used '{}'; the engines "
                    + "measure latency differently, so the comparison may drift", metrics.scenarioName(),
                    baseline.engine(), metrics.engine());
        }
        if (baseline.scenarioName() != null && !baseline.scenarioName().equals(metrics.scenarioName())) {
            LOGGER.warn("[LoadTest] {}: the baseline was recorded for scenario '{}'; comparing anyway",
                    metrics.scenarioName(), baseline.scenarioName());
        }
        return baseline.compare(metrics, tolerance);
    }

    /**
     * Asserts that no metric regressed against the baseline beyond the tolerance: latency (p95, p99, mean) may not
     * exceed {@code baseline × (1 + percent/100)}, throughput may not fall below {@code baseline × (1 − percent/100)},
     * and the error rate may not exceed {@code baseline + errorRateDelta} (all strict, limits computed exactly in
     * decimal). A metric unmeasured on either side is not compared and never counts as a pass: if nothing could be
     * compared the assertion fails, and when it passes with some metrics not compared a WARN lists them. An engine or
     * scenario mismatch only logs a WARN.
     *
     * @throws AssertionError if the metrics are partial, if no metric could be compared, or listing every regressed
     *                        metric with its baseline, actual value and limit, plus the metrics that were not compared
     */
    @TestFlyApi(since = "1.1.0")
    public LoadTestAssert assertNoRegression(LoadBaseline baseline, Tolerance tolerance) {
        requireComplete("no regression");
        java.util.List<RegressionResult> results = regressionResults(baseline, tolerance);
        StringBuilder regressed = new StringBuilder();
        java.util.List<String> notCompared = new java.util.ArrayList<>();
        for (RegressionResult r : results) {
            if (r.regressed()) {
                regressed.append(regressed.isEmpty() ? "" : "; ").append(r.metric())
                        .append(" baseline ").append(LoadBaseline.plain(r.baseline()))
                        .append(" actual ").append(LoadBaseline.plain(r.actual()))
                        .append(" limit ").append(LoadBaseline.plain(
                                LoadBaseline.limit(r.metric(), r.baseline(), tolerance)));
                if (r.reason() != null && r.reason().endsWith(LoadBaseline.ZERO_BASELINE)) {
                    regressed.append(' ').append(LoadBaseline.ZERO_BASELINE);
                }
            } else if (LoadBaseline.NOT_COMPARED.equals(r.reason())) {
                notCompared.add(r.metric());
            }
        }
        if (notCompared.size() == results.size()) {
            throw new AssertionError(String.format("[LoadTest] %s: nothing compared: %s are not measured in the "
                    + "baseline of '%s' or in this run", metrics.scenarioName(), notCompared, baseline.scenarioName()));
        }
        if (!regressed.isEmpty()) {
            throw new AssertionError(String.format("[LoadTest] %s: regression against the baseline of '%s' (%s, "
                            + "%s): %s%s", metrics.scenarioName(), baseline.scenarioName(), baseline.engine(),
                    tolerance, regressed,
                    notCompared.isEmpty() ? "" : "; not compared (not measured): " + String.join(", ", notCompared)));
        }
        if (!notCompared.isEmpty()) {
            LOGGER.warn("[LoadTest] {}: no regression, but {} not compared (not measured in the baseline or in this "
                    + "run)", metrics.scenarioName(), notCompared);
        }
        return this;
    }

    // ── SLA ──────────────────────────────────────────────────────────────

    /** The SLA results of the run (one per rule, in declaration order); empty when no SLA was declared. */
    @TestFlyApi(since = "1.1.0")
    public java.util.List<SlaResult> slaResults() {
        return metrics.slaResults();
    }

    /**
     * Asserts that every SLA rule passed; passes when no SLA was declared.
     *
     * @throws AssertionError listing every failed rule with its scope, metric, threshold, actual value and reason
     */
    @TestFlyApi(since = "1.1.0")
    public LoadTestAssert assertSlaPassed() {
        String failures = slaFailures(metrics);
        if (failures != null) {
            throw new AssertionError("[LoadTest] " + metrics.scenarioName() + ": SLA failed: " + failures);
        }
        return this;
    }

    /** The failed rules of {@code m} as one line, or {@code null} if none failed. */
    static String slaFailures(LoadTestMetrics m) {
        StringBuilder sb = new StringBuilder();
        for (SlaResult r : m.slaResults()) {
            if (r.passed()) {
                continue;
            }
            SlaRule rule = r.rule();
            if (!sb.isEmpty()) {
                sb.append("; ");
            }
            sb.append(rule.scope()).append(' ').append(rule.metric()).append(' ').append(rule.comparison())
                    .append(' ').append(rule.threshold()).append(" (actual ").append(r.actual()).append("): ")
                    .append(r.reason());
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    // ── Raw access ───────────────────────────────────────────────────────

    /** Returns the underlying metrics for custom assertions or logging. */
    public LoadTestMetrics metrics() {
        return metrics;
    }

    // ── Internal ─────────────────────────────────────────────────────────

    private LoadTestAssert assertPercentileBelow(String label, double actual, double threshold) {
        requireComplete(label + " latency");
        if (!LoadTestMetrics.isMeasured(actual)) {
            throw notMeasured(label);
        }
        if (actual >= threshold) {
            throw new AssertionError(String.format(
                    "[LoadTest] %s: %s latency %.0fms is not below %.0fms",
                    metrics.scenarioName(), label, actual, threshold));
        }
        return this;
    }

    /**
     * Throws if the metrics are partial: requests were started without a recorded outcome, so
     * latency, status and error figures are incomplete.
     */
    private void requireComplete(String what) {
        if (metrics.partial()) {
            throw new AssertionError(String.format(
                    "[LoadTest] %s: cannot assert %s, metrics are partial (some requests have no recorded "
                            + "outcome; engine '%s')",
                    metrics.scenarioName(), what, metrics.engine()));
        }
    }

    private void requireThroughputMeasured() {
        if (!LoadTestMetrics.isMeasured(metrics.throughputRps())) {
            throw new AssertionError(String.format(
                    "[LoadTest] %s: throughput was not measured by engine '%s' (run duration unknown)",
                    metrics.scenarioName(), metrics.engine()));
        }
    }

    private AssertionError notMeasured(String label) {
        return new AssertionError(String.format(
                "[LoadTest] %s: %s latency was not measured by engine '%s'",
                metrics.scenarioName(), label, metrics.engine()));
    }

    /**
     * Throws if the engine did not record real HTTP status codes for this run.
     */
    private void requireStatusCodesMeasured() {
        requireComplete("status codes");
        if (!metrics.statusCodesMeasured()) {
            throw new AssertionError(String.format(
                    "[LoadTest] %s: HTTP status codes were not measured by engine '%s'",
                    metrics.scenarioName(), metrics.engine()));
        }
    }
}
