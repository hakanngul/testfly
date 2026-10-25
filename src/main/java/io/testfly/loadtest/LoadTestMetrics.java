package io.testfly.loadtest;

import io.testfly.api.TestFlyApi;

import java.util.Collections;
import java.util.Map;

/**
 * Immutable snapshot of load-test execution results.
 *
 * <p>Produced by {@link LoadTestRunner#run(LoadScenario)} and consumed by
 * {@link LoadTestAssert} for fluent assertions and by the reporting layer
 * for HTML/JSON output.
 *
 * <p>Latency values are in milliseconds. A latency of {@link Double#NaN} means the engine did
 * not measure it (see {@link #isMeasured(double)}); {@code statusCodes} may contain
 * {@code -1} for requests that received no HTTP response.
 *
 * <p>Latency population per engine: on Gatling, latency percentiles, min, max and mean (overall and per
 * step) cover only requests that received an HTTP response; requests without a response (connection
 * refused, timeout, a request that could not be built) are counted in {@code failedRequests}, the error
 * rate and under status {@code -1}, but contribute no latency sample. The JDK engine also includes the
 * elapsed time of connection failures and timeouts in its latency figures. {@code throughputRps} is {@link Double#NaN} when
 * the run duration could not be measured.
 *
 * <p>{@code partial} is {@code true} when the engine recorded requests that were started but have no
 * outcome, or the run was stopped by Gatling's {@code maxDuration}; latency, status, error and
 * upper-bound throughput assertions on such a result fail instead of passing on incomplete data.
 */
@TestFlyApi(since = "1.1.0")
public record LoadTestMetrics(
        String scenarioName,
        int totalRequests,
        int successfulRequests,
        int failedRequests,
        double throughputRps,
        double meanLatencyMs,
        double p50LatencyMs,
        double p90LatencyMs,
        double p95LatencyMs,
        double p99LatencyMs,
        double minLatencyMs,
        double maxLatencyMs,
        double errorRate,
        Map<Integer, Long> statusCodes,
        long durationMs,
        int users,
        String engine,
        Map<String, StepMetrics> steps,
        double p75LatencyMs,
        @TestFlyApi(since = "1.1.0") boolean partial
) {

    /**
     * Constructor without {@code partial} (kept for binary compatibility); the result is complete
     * ({@code partial = false}).
     */
    @TestFlyApi(since = "1.1.0")
    public LoadTestMetrics(
            String scenarioName,
            int totalRequests,
            int successfulRequests,
            int failedRequests,
            double throughputRps,
            double meanLatencyMs,
            double p50LatencyMs,
            double p90LatencyMs,
            double p95LatencyMs,
            double p99LatencyMs,
            double minLatencyMs,
            double maxLatencyMs,
            double errorRate,
            Map<Integer, Long> statusCodes,
            long durationMs,
            int users,
            String engine,
            Map<String, StepMetrics> steps,
            double p75LatencyMs) {
        this(scenarioName, totalRequests, successfulRequests, failedRequests, throughputRps, meanLatencyMs,
                p50LatencyMs, p90LatencyMs, p95LatencyMs, p99LatencyMs, minLatencyMs, maxLatencyMs, errorRate,
                statusCodes, durationMs, users, engine, steps, p75LatencyMs, false);
    }

    /**
     * Pre-1.1.0 constructor without {@code p75LatencyMs} (kept for binary compatibility);
     * p75 is reported as not measured ({@link Double#NaN}).
     */
    @TestFlyApi(since = "1.1.0")
    public LoadTestMetrics(
            String scenarioName,
            int totalRequests,
            int successfulRequests,
            int failedRequests,
            double throughputRps,
            double meanLatencyMs,
            double p50LatencyMs,
            double p90LatencyMs,
            double p95LatencyMs,
            double p99LatencyMs,
            double minLatencyMs,
            double maxLatencyMs,
            double errorRate,
            Map<Integer, Long> statusCodes,
            long durationMs,
            int users,
            String engine,
            Map<String, StepMetrics> steps) {
        this(scenarioName, totalRequests, successfulRequests, failedRequests, throughputRps, meanLatencyMs,
                p50LatencyMs, p90LatencyMs, p95LatencyMs, p99LatencyMs, minLatencyMs, maxLatencyMs, errorRate,
                statusCodes, durationMs, users, engine, steps, Double.NaN, false);
    }

    /**
     * Returns {@code true} if the engine measured the value; {@link Double#NaN} means
     * "not measured" (e.g. a latency of a run in which no request received a response).
     */
    @TestFlyApi(since = "1.1.0")
    public static boolean isMeasured(double value) {
        return !Double.isNaN(value);
    }

    /**
     * Returns {@code true} if real HTTP status codes were recorded for this run
     * (or no request was sent at all). {@code false} means status-code assertions are
     * meaningless because the engine could not measure them.
     */
    @TestFlyApi(since = "1.1.0")
    public boolean statusCodesMeasured() {
        return totalRequests == 0 || (statusCodes != null && !statusCodes.isEmpty());
    }

    /**
     * Per-step breakdown within a multi-step scenario. {@code p95LatencyMs} is
     * {@link Double#NaN} when the engine did not measure it.
     */
    @TestFlyApi(since = "1.1.0")
    public record StepMetrics(
            String name,
            int totalRequests,
            int successfulRequests,
            int failedRequests,
            double p95LatencyMs,
            double errorRate
    ) {}

    /** Returns the success rate (0.0–1.0), or 0.0 if no requests were recorded. */
    @TestFlyApi(since = "1.1.0")
    public double successRate() {
        return totalRequests > 0 ? (double) successfulRequests / totalRequests : 0.0;
    }

    /** Returns the count of responses with the given HTTP status code. */
    @TestFlyApi(since = "1.1.0")
    public long statusCodeCount(int status) {
        return statusCodes != null ? statusCodes.getOrDefault(status, 0L) : 0L;
    }

    /** Returns {@code true} if at least one response had the given HTTP status code. */
    @TestFlyApi(since = "1.1.0")
    public boolean hasStatusCode(int status) {
        return statusCodes != null && statusCodes.containsKey(status) && statusCodes.get(status) > 0;
    }

    /** Returns the metrics for a specific step, or {@code null} if not found. */
    @TestFlyApi(since = "1.1.0")
    public StepMetrics step(String name) {
        return steps != null ? steps.get(name) : null;
    }

    /** Returns {@code true} if the given step is present in the results. */
    @TestFlyApi(since = "1.1.0")
    public boolean hasStep(String name) {
        return steps != null && steps.containsKey(name);
    }

    /** Returns an empty metrics snapshot (used when execution is skipped or fails early). */
    public static LoadTestMetrics empty(String scenarioName) {
        return new LoadTestMetrics(
                scenarioName, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                Collections.emptyMap(), 0, 0, "none", Collections.emptyMap(), 0, false);
    }
}
