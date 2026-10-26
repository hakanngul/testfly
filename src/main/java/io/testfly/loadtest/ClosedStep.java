package io.testfly.loadtest;

import io.testfly.api.TestFlyApi;

import java.time.Duration;

/**
 * One step of a closed workload model ({@link Workload#closed(ClosedStep, ClosedStep...)}): the number of
 * concurrent users follows the steps, and a user that finishes the scenario steps is replaced by a new one.
 *
 * <p>Factories validate eagerly and throw {@link IllegalArgumentException}: user counts must be at least 1
 * ({@link #rampConcurrent(int, int, Duration)} allows 0), durations {@code > 0}. A ramp down to 0 is the
 * explicit cooldown.
 *
 * <p>Evolution: new step types may be added in minor releases, so a {@code switch} over steps needs a
 * {@code default} branch. Existing record components are stable; new options arrive as new step types or
 * factory overloads, never as new components.
 */
@TestFlyApi(since = "1.1.0")
public sealed interface ClosedStep {

    /** How long this step lasts. */
    @TestFlyApi(since = "1.1.0")
    Duration duration();

    /** Keeps {@code users} users running concurrently during {@code during}. */
    @TestFlyApi(since = "1.1.0")
    static ClosedStep constantConcurrent(int users, Duration during) {
        return new ConstantConcurrent(users, during);
    }

    /** Changes the concurrent users linearly from {@code fromUsers} to {@code toUsers} during {@code during}. */
    @TestFlyApi(since = "1.1.0")
    static ClosedStep rampConcurrent(int fromUsers, int toUsers, Duration during) {
        return new RampConcurrent(fromUsers, toUsers, during);
    }

    /** {@link ClosedStep#constantConcurrent(int, Duration)}. */
    @TestFlyApi(since = "1.1.0")
    record ConstantConcurrent(int users, Duration duration) implements ClosedStep {
        public ConstantConcurrent {
            WorkloadChecks.users("constantConcurrent", users, 1);
            WorkloadChecks.positive("constantConcurrent", duration);
        }
    }

    /** {@link ClosedStep#rampConcurrent(int, int, Duration)}. */
    @TestFlyApi(since = "1.1.0")
    record RampConcurrent(int fromUsers, int toUsers, Duration duration) implements ClosedStep {
        public RampConcurrent {
            WorkloadChecks.users("rampConcurrent", fromUsers, 0);
            WorkloadChecks.users("rampConcurrent", toUsers, 0);
            WorkloadChecks.positive("rampConcurrent", duration);
        }
    }
}
