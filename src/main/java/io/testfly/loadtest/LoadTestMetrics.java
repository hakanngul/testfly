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

    /** Returns an empty metrics snapshot (used when execution is skipped or fails early). */
    public static LoadTestMetrics empty(String scenarioName) {
        return new LoadTestMetrics(
                scenarioName, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                Collections.emptyMap(), 0, 0, "none", Collections.emptyMap());
    }
}
