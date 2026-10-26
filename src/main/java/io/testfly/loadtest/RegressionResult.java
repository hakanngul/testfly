package io.testfly.loadtest;

import io.testfly.api.TestFlyApi;

/**
 * The comparison of one metric of a run with a {@link LoadBaseline}
 * ({@link LoadTestAssert#regressionResults(LoadBaseline, Tolerance)}).
 *
 * @param metric    {@code "p95"}, {@code "p99"}, {@code "mean"}, {@code "errorRate"} or {@code "throughput"}
 * @param baseline  the baseline value; {@link Double#NaN} if the baseline did not measure it
 * @param actual    the run's value; {@link Double#NaN} if the run did not measure it
 * @param regressed {@code true} if the run is worse than the baseline beyond the {@link Tolerance}
 * @param reason    {@value LoadBaseline#NOT_COMPARED} when either side is unmeasured (then {@code regressed} is
 *                  {@code false}), why it regressed when {@code regressed}, else {@code null}
 */
@TestFlyApi(since = "1.1.0")
public record RegressionResult(String metric, double baseline, double actual, boolean regressed, String reason) {
}
