package io.testfly.client;

import io.testfly.api.TestFlyApi;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
        this.requestCount = requestCount;
    }

    /** Starts configuring a batch runner for the specified number of requests. */
    public static ApiBatchRunner fire(int count) {
        return new ApiBatchRunner(count);
    }

    /** Sets the maximum number of concurrent requests (thread pool size). */
    public ApiBatchRunner concurrently(int threads) {
        this.concurrencyLimit = threads;
        return this;
    }

    /** Sets the supplier that generates the ApiClient for each request. */
    public ApiBatchRunner request(Supplier<ApiClient> requestSupplier) {
        this.requestSupplier = requestSupplier;
        return this;
    }

    /** Executes the batch and returns a list of responses (maintaining order). */
    public List<ApiResponse> execute() {
        if (requestSupplier == null) {
            throw new IllegalStateException("requestSupplier must be configured via .request()");
        }

        ExecutorService executor = null;
        try {
            if (concurrencyLimit > 0) {
                executor = Executors.newFixedThreadPool(concurrencyLimit);
            }

            List<CompletableFuture<ApiResponse>> futures = new ArrayList<>(requestCount);

            for (int i = 0; i < requestCount; i++) {
                ApiClient client = requestSupplier.get();
                if (executor != null) {
                    futures.add(CompletableFuture.supplyAsync(client::send, executor));
                } else {
                    futures.add(client.sendAsync());
                }
            }

            return futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());

        } finally {
            if (executor != null) {
                executor.shutdown();
            }
        }
    }
}
