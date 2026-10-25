package io.testfly.loadtest;

import io.testfly.api.TestFlyApi;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Workload model of a {@link LoadScenario} (ADR 0003 decision 4): an {@linkplain #open open} model in which
 * users arrive per the steps, or a {@linkplain #closed closed} model in which the number of concurrent users
 * follows the steps. Each virtual user runs the scenario steps once; there is no hold loop.
 *
 * <pre>
 * loadScenario("Checkout")
 *         .workload(Workload.closed(
 *                 ClosedStep.constantConcurrent(50, Duration.ofMinutes(2)),
 *                 ClosedStep.rampConcurrent(50, 0, Duration.ofSeconds(30))))
 *         .step("Cart").get("/cart").and()
 *         .run();
 * </pre>
 *
 * <p>Requires the Gatling engine, and cannot be combined with {@code users/rampUp/hold/cooldown}.
 * {@code loadtest.maxUsers} caps a closed workload's peak concurrency, and an open workload's arrival rate
 * and user-count steps, not its resulting concurrency (slow endpoints can accumulate concurrent users).
 * Immutable and thread-safe.
 */
@TestFlyApi(since = "1.1.0")
public final class Workload {

    /**
     * The workload models. More models may be added in minor releases, so a {@code switch} over them needs
     * a {@code default} branch.
     */
    @TestFlyApi(since = "1.1.0")
    public enum Model {
        /** Users arrive per the steps, independently of how many are running. */
        OPEN,
        /** The number of concurrent users follows the steps; a finished user is replaced. */
        CLOSED
    }

    private final Model model;
    private final List<OpenStep> openSteps;
    private final List<ClosedStep> closedSteps;

    private Workload(Model model, List<OpenStep> openSteps, List<ClosedStep> closedSteps) {
        this.model = model;
        this.openSteps = openSteps;
        this.closedSteps = closedSteps;
    }

    /** An open workload with the given steps, run in order. */
    @TestFlyApi(since = "1.1.0")
    public static Workload open(OpenStep first, OpenStep... more) {
        return new Workload(Model.OPEN, steps(first, more), List.of());
    }

    /** A closed workload with the given steps, run in order. */
    @TestFlyApi(since = "1.1.0")
    public static Workload closed(ClosedStep first, ClosedStep... more) {
        return new Workload(Model.CLOSED, List.of(), steps(first, more));
    }

    @SafeVarargs
    private static <T> List<T> steps(T first, T... more) {
        List<T> all = new ArrayList<>();
        all.add(first);
        if (more != null) {
            all.addAll(java.util.Arrays.asList(more));
        }
        for (T step : all) {
            if (step == null) {
                throw new IllegalArgumentException("[LoadTest] workload steps cannot be null");
            }
        }
        return List.copyOf(all);
    }

    /** {@link Model#OPEN} or {@link Model#CLOSED}. */
    @TestFlyApi(since = "1.1.0")
    public Model model() {
        return model;
    }

    /** The open steps in order; empty for a closed workload. */
    @TestFlyApi(since = "1.1.0")
    public List<OpenStep> openSteps() {
        return openSteps;
    }

    /** The closed steps in order; empty for an open workload. */
    @TestFlyApi(since = "1.1.0")
    public List<ClosedStep> closedSteps() {
        return closedSteps;
    }

    /** Sum of the step durations ({@code atOnce} lasts 0). */
    @TestFlyApi(since = "1.1.0")
    public Duration totalDuration() {
        Duration total = Duration.ZERO;
        for (OpenStep s : openSteps) {
            total = total.plus(s.duration());
        }
        for (ClosedStep s : closedSteps) {
            total = total.plus(s.duration());
        }
        return total;
    }

    /**
     * Open: the highest arrival rate (users/s) over the {@code constantRate} and {@code rampRate} steps, 0 if
     * there is none ({@code atOnce}, {@code ramp} and {@code stressPeak} are user counts, not rates). Closed: the
     * highest number of concurrent users, including ramp end points.
     */
    @TestFlyApi(since = "1.1.0")
    public double peak() {
        double peak = 0;
        for (OpenStep s : openSteps) {
            if (s instanceof OpenStep.ConstantRate r) {
                peak = Math.max(peak, r.usersPerSec());
            } else if (s instanceof OpenStep.RampRate r) {
                peak = Math.max(peak, Math.max(r.fromPerSec(), r.toPerSec()));
            }
        }
        for (ClosedStep s : closedSteps) {
            if (s instanceof ClosedStep.ConstantConcurrent c) {
                peak = Math.max(peak, c.users());
            } else if (s instanceof ClosedStep.RampConcurrent r) {
                peak = Math.max(peak, Math.max(r.fromUsers(), r.toUsers()));
            }
        }
        return peak;
    }

    @Override
    public String toString() {
        return model + " " + (model == Model.OPEN ? openSteps : closedSteps);
    }
}
