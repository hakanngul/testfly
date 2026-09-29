package io.testfly.flakiness;

import io.testfly.api.TestFlyApi;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriverException;

import java.net.ConnectException;

/**
 * Analyses test failures dynamically at runtime to determine if they are likely
 * caused by an application bug or systemic infrastructure/flakiness issues.
 */
@TestFlyApi(since = "1.9.0")
public final class SmartTriageEngine {

    private SmartTriageEngine() {
    }

    public enum TriageResult {
        APPLICATION_BUG,
        SYSTEM_FLAKY,
        NEEDS_INVESTIGATION
    }

    /**
     * Triages a failure based on exception type and retry status.
     *
     * @param exception           the Throwable that caused the test to fail
     * @param wasRetriedAndPassed true if the test failed initially but passed on a subsequent retry
     * @return the classified TriageResult
     */
    public static TriageResult triageFailure(Throwable exception, boolean wasRetriedAndPassed) {
        if (exception == null) return TriageResult.NEEDS_INVESTIGATION;

        // Rule 1: Immediate indicator of flakiness
        if (wasRetriedAndPassed) {
            return TriageResult.SYSTEM_FLAKY;
        }

        int flakyScore = 0;
        Throwable rootCause = getRootCause(exception);

        // Rule 2: Exception heuristics
        if (rootCause instanceof TimeoutException || rootCause instanceof StaleElementReferenceException) {
            flakyScore += 80;
        } else if (rootCause instanceof ConnectException) {
            flakyScore += 90;
        } else if (rootCause instanceof AssertionError) {
            flakyScore -= 50; // Assertion errors are almost always application bugs
        } else if (rootCause instanceof WebDriverException) {
            flakyScore += 40; 
        }

        if (flakyScore >= 70) {
            return TriageResult.SYSTEM_FLAKY;
        } else if (flakyScore <= 0) {
            return TriageResult.APPLICATION_BUG;
        } else {
            return TriageResult.NEEDS_INVESTIGATION;
        }
    }

    /**
     * Extracts the root cause from a nested exception structure.
     */
    private static Throwable getRootCause(Throwable t) {
        Throwable cause = t.getCause();
        while (cause != null && cause != t) {
            t = cause;
            cause = t.getCause();
        }
        return t;
    }
}
