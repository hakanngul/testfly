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
        Map<String, StepMetrics> steps
) {

    /**
     * Per-step breakdown within a multi-step scenario.
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
                Collections.emptyMap(), 0, 0, "none", Collections.emptyMap());
    }
}
