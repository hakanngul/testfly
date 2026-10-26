package io.testfly.loadtest;

import io.testfly.api.TestFlyApi;

/**
 * The outcome of one {@link SlaRule}, evaluated by TestFly from {@link LoadTestMetrics} the same way on every engine.
 *
 * <p>A rule fails with reason {@code "metrics are partial"} when the metrics are partial, {@code "step not found"}
 * when its step is not in the metrics, {@code "not measured"} when the value is {@link Double#NaN}, and otherwise
 * with a reason such as {@code "250.0 is not below 200.0"}.
 *
 * @param rule   the evaluated rule
 * @param actual the measured value; {@link Double#NaN} when the step is missing or the value was not measured
 * @param passed {@code true} if the rule holds
 * @param reason why the rule failed; {@code null} when it passed
 */
@TestFlyApi(since = "1.1.0")
public record SlaResult(SlaRule rule, double actual, boolean passed, String reason) {
}
