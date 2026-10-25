package io.testfly.mobile;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Retrying assertion on any value; create with {@link MobileAssertions#expectPoll(Supplier)}.
 * Exceptions thrown by the supplier count as a failed attempt until the timeout runs out.
 */
public final class MobilePollAssert<T> {

    private static final Duration INTERVAL = Duration.ofMillis(200);

    private final Supplier<T> supplier;
    private final Duration timeout;

    MobilePollAssert(Supplier<T> supplier, Duration timeout) {
        this.supplier = Objects.requireNonNull(supplier, "supplier");
        this.timeout = timeout;
    }

    public MobilePollAssert<T> withTimeout(Duration timeout) {
        return new MobilePollAssert<>(supplier, Objects.requireNonNull(timeout, "timeout"));
    }

    /** The value equals {@code expected}. */
    public void toBe(T expected) {
        matches(v -> Objects.equals(v, expected), "to be " + expected);
    }

    /** The value passes {@code condition}; {@code description} names it in the failure. */
    public void matches(Predicate<? super T> condition, String description) {
        long deadline = System.nanoTime() + timeout.toNanos();
        Object last;
        while (true) {
            try {
                T value = supplier.get();
                if (condition.test(value)) {
                    return;
                }
                last = value;
            } catch (RuntimeException e) {
                last = e;
            }
            if (System.nanoTime() >= deadline) {
                throw new MobileAssertionError("expectPoll(...) " + description + " failed after " + timeout.toMillis()
                        + " ms; last " + (last instanceof RuntimeException e ? "error: " + e.getMessage() : "value: " + last));
            }
            try {
                Thread.sleep(INTERVAL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new MobileAssertionError("expectPoll(...) was interrupted");
            }
        }
    }
}
