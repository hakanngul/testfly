package io.testfly.flakiness;

import io.testfly.api.TestFlyApi;

/**
 * Flakiness risk score for a single test, computed across multiple historical
 * runs.
 *
 * @param failureRate 0–100
 */
@TestFlyApi(since = "0.7.0")
public record FlakinessScore(String testId, int runsAnalysed, int failCount, double failureRate, Risk risk) {

    public enum Risk {
        STABLE, WATCH, HIGH
    }

    public static Risk classify(double failureRate, double highThreshold) {
        if (failureRate >= highThreshold)
            return Risk.HIGH;
        if (failureRate >= 10.0)
            return Risk.WATCH;
        return Risk.STABLE;
    }
}
