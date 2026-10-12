package io.testfly.client;

import io.testfly.api.TestFlyApi;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Executes a batch of API requests asynchronously, with an optional concurrency limit.
 * Useful for rate limiting or concurrency (fuzzing) tests.
 */
@TestFlyApi(since = "1.2.0")
public class ApiBatchRunner {

    private final int requestCount;
    private int concurrencyLimit = -1;
    private Supplier<ApiClient> requestSupplier;

    private ApiBatchRunner(int requestCount) {
        if (requestCount < 1) throw new IllegalArgumentException("count must be positive");
        this.requestCount = requestCount;
    }

    /** Starts configuring a batch runner for the specified number of requests. */
    public static ApiBatchRunner fire(int count) {
        return new ApiBatchRunner(count);
    }

    /** Sets the maximum number of concurrent requests (thread pool size). */
    public ApiBatchRunner concurrently(int threads) {
        if (threads < 1) throw new IllegalArgumentException("concurrency must be positive");
        this.concurrencyLimit = threads;
        return this;
    }

    /** Sets the supplier that generates the ApiClient for each request. */
    public ApiBatchRunner request(Supplier<ApiClient> requestSupplier) {
        this.requestSupplier = requestSupplier;
        return this;
    }

    /**
     * Executes the batch and returns a list of responses (maintaining order).
     * The first failed request fails the whole batch; use {@link #executeAll()} to keep partial results.
     */
    public List<ApiResponse> execute() {
        if (requestSupplier == null) {
            throw new IllegalStateException("requestSupplier must be configured via .request()");
        }

        java.util.concurrent.Semaphore limit = concurrencyLimit > 0
                ? new java.util.concurrent.Semaphore(concurrencyLimit) : null;
        List<CompletableFuture<ApiResponse>> futures = new ArrayList<>(requestCount);
        try {
            for (int i = 0; i < requestCount; i++) {
                ApiClient client = java.util.Objects.requireNonNull(requestSupplier.get(), "requestSupplier result");
                futures.add(client.sendAsync(limit));
            }
            return futures.stream().map(CompletableFuture::join).collect(Collectors.toList());
        } finally {
            futures.forEach(future -> { if (!future.isDone()) future.cancel(true); });
        }
    }

    /**
     * Executes the batch and waits for every request, returning each response or error in
     * submission order. A failing request does not cancel the others.
     */
    @TestFlyApi(since = "1.1.0")
    public ApiBatchResult executeAll() {
        if (requestSupplier == null) {
            throw new IllegalStateException("requestSupplier must be configured via .request()");
        }
        java.util.concurrent.Semaphore limit = concurrencyLimit > 0
                ? new java.util.concurrent.Semaphore(concurrencyLimit) : null;
        List<CompletableFuture<ApiResponse>> futures = new ArrayList<>(requestCount);
        try {
            for (int i = 0; i < requestCount; i++) {
                ApiClient client;
                try {
                    client = java.util.Objects.requireNonNull(requestSupplier.get(), "requestSupplier result");
                } catch (RuntimeException error) {
                    futures.add(CompletableFuture.failedFuture(error));
                    continue;
                }
                futures.add(client.sendAsync(limit));
            }
            List<ApiBatchResult.Outcome> outcomes = new ArrayList<>(requestCount);
            for (int i = 0; i < futures.size(); i++) {
                try {
                    outcomes.add(new ApiBatchResult.Outcome(i, futures.get(i).join(), null));
                } catch (java.util.concurrent.CompletionException error) {
                    outcomes.add(new ApiBatchResult.Outcome(i, null, error.getCause() != null ? error.getCause() : error));
                } catch (java.util.concurrent.CancellationException error) {
                    outcomes.add(new ApiBatchResult.Outcome(i, null, error));
                }
            }
            return new ApiBatchResult(outcomes);
        } finally {
            futures.forEach(future -> { if (!future.isDone()) future.cancel(true); });
        }
    }
}
