package io.testfly.mobile.internal.rpc;

import java.time.Duration;
import java.util.Objects;

/** Monotonic, immutable budget shared by all steps of an internal operation. */
public final class Deadline {
    private final long startedNanos;
    private final long budgetNanos;

    private Deadline(long budgetNanos) {
        this.startedNanos = System.nanoTime();
        this.budgetNanos = budgetNanos;
    }

    public static Deadline after(Duration budget) {
        Objects.requireNonNull(budget, "budget");
        long nanos;
        try {
            nanos = budget.toNanos();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Deadline budget is too large");
        }
        if (nanos <= 0) {
            throw new IllegalArgumentException("Deadline budget must be positive");
        }
        return new Deadline(nanos);
    }

    public long remainingNanos() {
        return Math.max(0, budgetNanos - (System.nanoTime() - startedNanos));
    }

    public Duration remaining() {
        return Duration.ofNanos(remainingNanos());
    }

    public boolean expired() {
        return remainingNanos() == 0;
    }
}
