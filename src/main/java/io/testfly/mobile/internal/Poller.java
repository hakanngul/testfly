package io.testfly.mobile.internal;

import io.testfly.mobile.DeviceNotFoundException;
import io.testfly.mobile.MobileNode;
import io.testfly.mobile.MobileRpcException;
import io.testfly.mobile.MobileServerUnavailableException;
import io.testfly.mobile.MobileTimeoutException;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

/**
 * Polls the view tree until a check passes or the budget runs out (ADR 0002 D6). Each cycle fetches
 * the tree once; every request is bounded by the time left, so a stalled server cannot hold the
 * caller past its budget.
 */
public final class Poller {

    /** Below this much time left, another view-tree read is not started. */
    private static final long MIN_READ_NANOS = Duration.ofMillis(25).toNanos();

    private Poller() {
    }

    /** Outcome of one check against a tree snapshot. */
    public record Step<T>(boolean done, T value, String failure, List<MobileNode> evidence) {

        public static <T> Step<T> done(T value) {
            return new Step<>(true, value, null, List.of());
        }

        public static <T> Step<T> retry(String failure, List<MobileNode> evidence) {
            return new Step<>(false, null, failure, evidence == null ? List.of() : evidence);
        }
    }

    /** Result of a poll: the value, or the last failure and the nodes it saw. */
    public record Outcome<T>(boolean passed, T value, String lastFailure, List<MobileNode> lastEvidence, long elapsedMs) {
    }

    /**
     * Runs {@code check} against fresh trees until it reports done or {@code budget} elapses.
     * Exceptions thrown by {@code check} (strict-mode violations) end the poll immediately.
     */
    public static <T> Outcome<T> poll(MobileSession session, Duration budget, Function<List<MobileNode>, Step<T>> check) {
        long start = System.nanoTime();
        long deadline = start + budget.toNanos();
        String lastFailure = "the view tree was never read";
        List<MobileNode> evidence = List.of();
        boolean checked = false;
        while (true) {
            try {
                List<MobileNode> tree = session.driver().viewTree(session.requestTimeout(deadline));
                Step<T> step = check.apply(tree);
                if (step.done()) {
                    return new Outcome<>(true, step.value(), null, List.of(), elapsed(start));
                }
                lastFailure = step.failure();
                evidence = step.evidence();
                checked = true;
            } catch (MobileServerUnavailableException | DeviceNotFoundException e) {
                throw e;
            } catch (MobileTimeoutException e) {
                // A read cut off by the deadline says nothing new; keep the last real reason.
                if (!checked || System.nanoTime() < deadline) {
                    lastFailure = "reading the view tree timed out (" + e.getMessage() + ")";
                }
            } catch (MobileRpcException e) {
                // The device may be mid-transition; keep polling and report the last error.
                lastFailure = "reading the view tree failed: " + e.getMessage();
            }
            long left = deadline - System.nanoTime();
            // Too little time for another read to mean anything: report what was seen.
            if (left <= MIN_READ_NANOS) {
                return new Outcome<>(false, null, lastFailure, evidence, elapsed(start));
            }
            sleep(Math.min(session.pollInterval().toNanos(), left - MIN_READ_NANOS));
        }
    }

    private static long elapsed(long start) {
        return Duration.ofNanos(System.nanoTime() - start).toMillis();
    }

    private static void sleep(long nanos) {
        try {
            Thread.sleep(Duration.ofNanos(nanos));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MobileTimeoutException("Interrupted while waiting for the device");
        }
    }

    /** Lists up to 10 nodes for an error message. */
    public static String describe(List<MobileNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(nodes.size(), 10);
        for (int i = 0; i < limit; i++) {
            sb.append("\n  - ").append(nodes.get(i).describe());
        }
        if (nodes.size() > limit) {
            sb.append("\n  ... and ").append(nodes.size() - limit).append(" more");
        }
        return sb.toString();
    }
}
