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
 */
@TestFlyApi(since = "1.1.0")
public final class LoadTestAssert {

    private final LoadTestMetrics metrics;

    public LoadTestAssert(LoadTestMetrics metrics) {
        this.metrics = metrics;
    }

    // ── Throughput ───────────────────────────────────────────────────────

    /** Asserts throughput (requests/sec) is strictly above the given value. */
    public LoadTestAssert assertThroughputAbove(double rps) {
        if (metrics.throughputRps() <= rps) {
            throw new AssertionError(String.format(
                    "[LoadTest] %s: throughput %.1f req/s is not above %.1f req/s",
                    metrics.scenarioName(), metrics.throughputRps(), rps));
        }
        return this;
    }

    /** Asserts throughput (requests/sec) is strictly below the given value. */
    public LoadTestAssert assertThroughputBelow(double rps) {
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

    /** Asserts p90 latency is below the given threshold (ms). */
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

    // ── Error rate ───────────────────────────────────────────────────────

    /** Asserts error rate (0.0–1.0) is below the given threshold. */
    public LoadTestAssert assertErrorRateBelow(double rate) {
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
        double successRate = 1.0 - metrics.errorRate();
        if (successRate <= rate) {
            throw new AssertionError(String.format(
                    "[LoadTest] %s: success rate %.4f is not above %.4f",
                    metrics.scenarioName(), successRate, rate));
        }
        return this;
    }

    // ── Status codes ─────────────────────────────────────────────────────

    /**
     * Asserts that at least {@code minCount} responses had the given status code.
     */
    public LoadTestAssert assertStatusCodeCount(int status, long minCount) {
        long actual = metrics.statusCodes().getOrDefault(status, 0L);
        if (actual < minCount) {
            throw new AssertionError(String.format(
                    "[LoadTest] %s: status %d count %d is below minimum %d",
                    metrics.scenarioName(), status, actual, minCount));
        }
        return this;
    }

    /** Asserts that no response had the given status code. */
    public LoadTestAssert assertNoStatus(int status) {
        long count = metrics.statusCodes().getOrDefault(status, 0L);
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
        LoadTestMetrics.StepMetrics step = metrics.steps().get(stepName);
        if (step == null) {
            throw new AssertionError(String.format(
                    "[LoadTest] %s: step '%s' not found in metrics", metrics.scenarioName(), stepName));
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

    // ── Raw access ───────────────────────────────────────────────────────

    /** Returns the underlying metrics for custom assertions or logging. */
    public LoadTestMetrics metrics() {
        return metrics;
    }

    // ── Internal ─────────────────────────────────────────────────────────

    private LoadTestAssert assertPercentileBelow(String label, double actual, double threshold) {
        if (actual >= threshold) {
            throw new AssertionError(String.format(
                    "[LoadTest] %s: %s latency %.0fms is not below %.0fms",
                    metrics.scenarioName(), label, actual, threshold));
        }
        return this;
    }
}
