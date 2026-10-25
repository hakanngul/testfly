package io.testfly.loadtest;

import io.testfly.api.TestFlyApi;

import java.time.Duration;

/**
 * One step of an open workload model ({@link Workload#open(OpenStep, OpenStep...)}): users arrive
 * independently of how many are already running, and each new user runs the scenario steps once.
 *
 * <p>Factories validate eagerly and throw {@link IllegalArgumentException}: user counts must be at least 1,
 * rates finite and {@code >= 0}, durations {@code > 0}.
 *
 * <p>{@code loadtest.maxUsers} caps the arrival rate and the user counts of {@code atOnce}/{@code ramp}/
 * {@code stressPeak}, not the resulting concurrency: on a slow endpoint, concurrent users can accumulate.
 *
 * <p>Evolution: new step types may be added in minor releases, so a {@code switch} over steps needs a
 * {@code default} branch. Existing record components are stable; new options arrive as new step types or
 * factory overloads, never as new components.
 */
@TestFlyApi(since = "1.1.0")
public sealed interface OpenStep {

    /** How long this step lasts; {@link Duration#ZERO} for {@link AtOnce}. */
    @TestFlyApi(since = "1.1.0")
    Duration duration();

    /** Starts {@code users} users at once. */
    @TestFlyApi(since = "1.1.0")
    static OpenStep atOnce(int users) {
        return new AtOnce(users);
    }

    /** Starts {@code users} users spread evenly over {@code over}. */
    @TestFlyApi(since = "1.1.0")
    static OpenStep ramp(int users, Duration over) {
        return new Ramp(users, over);
    }

    /** Starts {@code usersPerSec} new users per second during {@code during}. */
    @TestFlyApi(since = "1.1.0")
    static OpenStep constantRate(double usersPerSec, Duration during) {
        return new ConstantRate(usersPerSec, during);
    }

    /** Ramps the arrival rate linearly from {@code fromPerSec} to {@code toPerSec} users/s during {@code during}. */
    @TestFlyApi(since = "1.1.0")
    static OpenStep rampRate(double fromPerSec, double toPerSec, Duration during) {
        return new RampRate(fromPerSec, toPerSec, during);
    }

    /** Starts {@code users} users following a smooth (Heaviside) spike over {@code over}. */
    @TestFlyApi(since = "1.1.0")
    static OpenStep stressPeak(int users, Duration over) {
        return new StressPeak(users, over);
    }

    /** Starts no user for {@code duration}. */
    @TestFlyApi(since = "1.1.0")
    static OpenStep nothingFor(Duration duration) {
        return new NothingFor(duration);
    }

    /** {@link OpenStep#atOnce(int)}. */
    @TestFlyApi(since = "1.1.0")
    record AtOnce(int users) implements OpenStep {
        public AtOnce {
            WorkloadChecks.users("atOnce", users, 1);
        }

        @Override
        public Duration duration() {
            return Duration.ZERO;
        }
    }

    /** {@link OpenStep#ramp(int, Duration)}. */
    @TestFlyApi(since = "1.1.0")
    record Ramp(int users, Duration duration) implements OpenStep {
        public Ramp {
            WorkloadChecks.users("ramp", users, 1);
            WorkloadChecks.positive("ramp", duration);
        }
    }

    /** {@link OpenStep#constantRate(double, Duration)}. */
    @TestFlyApi(since = "1.1.0")
    record ConstantRate(double usersPerSec, Duration duration) implements OpenStep {
        public ConstantRate {
            WorkloadChecks.rate("constantRate", usersPerSec);
            WorkloadChecks.positive("constantRate", duration);
        }
    }

    /** {@link OpenStep#rampRate(double, double, Duration)}. */
    @TestFlyApi(since = "1.1.0")
    record RampRate(double fromPerSec, double toPerSec, Duration duration) implements OpenStep {
        public RampRate {
            WorkloadChecks.rate("rampRate", fromPerSec);
            WorkloadChecks.rate("rampRate", toPerSec);
            WorkloadChecks.positive("rampRate", duration);
        }
    }

    /** {@link OpenStep#stressPeak(int, Duration)}. */
    @TestFlyApi(since = "1.1.0")
    record StressPeak(int users, Duration duration) implements OpenStep {
        public StressPeak {
            WorkloadChecks.users("stressPeak", users, 1);
            WorkloadChecks.positive("stressPeak", duration);
        }
    }

    /** {@link OpenStep#nothingFor(Duration)}. */
    @TestFlyApi(since = "1.1.0")
    record NothingFor(Duration duration) implements OpenStep {
        public NothingFor {
            WorkloadChecks.positive("nothingFor", duration);
        }
    }
}
