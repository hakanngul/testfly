package io.testfly.loadtest;

import java.time.Duration;

/** Eager argument checks shared by the {@link OpenStep} and {@link ClosedStep} factories. */
final class WorkloadChecks {

    private WorkloadChecks() {
    }

    static void users(String step, int users, int min) {
        if (users < min) {
            throw new IllegalArgumentException("[LoadTest] " + step + ": user count must be >= " + min
                    + ", was " + users);
        }
    }

    static void rate(String step, double usersPerSec) {
        if (!Double.isFinite(usersPerSec) || usersPerSec < 0) {
            throw new IllegalArgumentException("[LoadTest] " + step + ": rate must be finite and >= 0 users/s, was "
                    + usersPerSec);
        }
    }

    static void positive(String step, Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("[LoadTest] " + step + ": duration must be > 0, was " + duration);
        }
    }
}
