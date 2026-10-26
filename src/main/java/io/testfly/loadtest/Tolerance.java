package io.testfly.loadtest;

import io.testfly.api.TestFlyApi;

/**
 * How far a run may drift from a {@link LoadBaseline} before {@link LoadTestAssert#assertNoRegression} fails.
 *
 * <ul>
 *   <li>{@link #percent()}: the same relative tolerance for latency (p95, p99, mean may grow by it) and throughput
 *       (may shrink by it).</li>
 *   <li>{@link #errorRateDelta()}: an absolute tolerance for the error rate (0.0..1.0), default {@value #DEFAULT_ERROR_RATE_DELTA}
 *       (one percentage point). The percent does not apply to the error rate.</li>
 * </ul>
 *
 * <p>Immutable and thread-safe.
 */
@TestFlyApi(since = "1.1.0")
public final class Tolerance {

    /** Default absolute error-rate tolerance: one percentage point. */
    public static final double DEFAULT_ERROR_RATE_DELTA = 0.01;

    private final double percent;
    private final double errorRateDelta;

    private Tolerance(double percent, double errorRateDelta) {
        this.percent = percent;
        this.errorRateDelta = errorRateDelta;
    }

    /**
     * A tolerance of {@code pct} percent for latency and throughput, with the default error-rate delta.
     *
     * <p>A percent of 100 or more disables the throughput check (its limit {@code baseline × (1 − pct/100)} is 0 or
     * below), and no relative tolerance can absorb a latency increase over a 0 ms baseline.
     *
     * @throws IllegalArgumentException if {@code pct} is negative or not finite
     */
    @TestFlyApi(since = "1.1.0")
    public static Tolerance percent(double pct) {
        return new Tolerance(check("percent", pct), DEFAULT_ERROR_RATE_DELTA);
    }

    /**
     * A copy with an absolute error-rate tolerance (0.0..1.0 scale).
     *
     * @throws IllegalArgumentException if {@code absolute} is negative or not finite
     */
    @TestFlyApi(since = "1.1.0")
    public Tolerance errorRateDelta(double absolute) {
        return new Tolerance(percent, check("errorRateDelta", absolute));
    }

    /** The relative tolerance for latency and throughput, in percent. */
    @TestFlyApi(since = "1.1.0")
    public double percent() {
        return percent;
    }

    /** The absolute tolerance for the error rate. */
    @TestFlyApi(since = "1.1.0")
    public double errorRateDelta() {
        return errorRateDelta;
    }

    @Override
    public String toString() {
        return "Tolerance[percent=" + percent + ", errorRateDelta=" + errorRateDelta + "]";
    }

    private static double check(String what, double value) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException("[LoadTest] Tolerance " + what + " must be finite and >= 0, was "
                    + value);
        }
        return value;
    }
}
