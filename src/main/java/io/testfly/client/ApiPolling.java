package io.testfly.client;

import io.testfly.api.TestFlyApi;

import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable polling options for {@link ApiClient#pollUntil(java.util.function.Predicate, ApiPolling)}.
 *
 * <pre>
 * ApiResponse done = apiClient().get("/orders/{id}").pathParam("id", 42)
 *         .pollUntil(r -&gt; "DONE".equals(r.json("$.status")),
 *                 ApiPolling.within(Duration.ofSeconds(30))
 *                         .interval(Duration.ofMillis(500))
 *                         .failFastOn(404, 500));
 * </pre>
 */
@TestFlyApi(since = "1.1.0")
public final class ApiPolling {

    private final Duration timeout;
    private final Duration interval;
    private final Set<Integer> failFastStatuses;
    private final boolean rethrowPredicateErrors;
    private final int maxAttempts;

    private ApiPolling(Duration timeout, Duration interval, Set<Integer> failFastStatuses,
                       boolean rethrowPredicateErrors, int maxAttempts) {
        this.timeout = timeout;
        this.interval = interval;
        this.failFastStatuses = failFastStatuses;
        this.rethrowPredicateErrors = rethrowPredicateErrors;
        this.maxAttempts = maxAttempts;
    }

    /** Polls for at most {@code timeout}, every second by default. */
    public static ApiPolling within(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout must be positive");
        return new ApiPolling(timeout, Duration.ofSeconds(1), Set.of(), false, 0);
    }

    /** Delay between attempts (minimum 50 ms). Default: 1 second. */
    public ApiPolling interval(Duration value) {
        Objects.requireNonNull(value, "interval");
        if (value.isNegative()) throw new IllegalArgumentException("interval must be >= 0");
        return new ApiPolling(timeout, value, failFastStatuses, rethrowPredicateErrors, maxAttempts);
    }

    /** Stops polling immediately with an {@link ApiException} when one of these statuses is received. */
    public ApiPolling failFastOn(int... statuses) {
        Set<Integer> set = Arrays.stream(statuses).boxed().collect(Collectors.toUnmodifiableSet());
        return new ApiPolling(timeout, interval, set, rethrowPredicateErrors, maxAttempts);
    }

    /**
     * When {@code true}, an exception thrown by the condition (for example a JSON parse error)
     * stops polling. Default {@code false}: the error is logged and polling continues.
     */
    public ApiPolling rethrowPredicateErrors(boolean value) {
        return new ApiPolling(timeout, interval, failFastStatuses, value, maxAttempts);
    }

    /** Upper bound on attempts in addition to the timeout ({@code 0} = unlimited, the default). */
    public ApiPolling maxAttempts(int value) {
        if (value < 0) throw new IllegalArgumentException("maxAttempts must be >= 0");
        return new ApiPolling(timeout, interval, failFastStatuses, rethrowPredicateErrors, value);
    }

    public Duration timeout() { return timeout; }
    public Duration interval() { return interval; }
    public Set<Integer> failFastStatuses() { return failFastStatuses; }
    public boolean rethrowsPredicateErrors() { return rethrowPredicateErrors; }
    public int maxAttempts() { return maxAttempts; }
}
