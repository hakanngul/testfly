package io.testfly.client;

import io.testfly.api.TestFlyApi;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Per-request outcome of {@link ApiBatchRunner#executeAll()}: every request has either a
 * response (any HTTP status) or an error (transport failure, cancellation, interceptor error).
 *
 * <pre>
 * ApiBatchResult result = ApiBatchRunner.fire(50).concurrently(10)
 *         .request(() -&gt; apiPost("/orders").body(order)).executeAll();
 * assertTrue(result.responses().stream().allMatch(r -&gt; r.status() == 201 || r.status() == 429));
 * </pre>
 */
@TestFlyApi(since = "1.1.0")
public final class ApiBatchResult {

    /** Outcome of the request at {@code index} (submission order). Exactly one of response/error is set. */
    @TestFlyApi(since = "1.1.0")
    public record Outcome(int index, ApiResponse response, Throwable error) {
        public Outcome {
            if ((response == null) == (error == null))
                throw new IllegalArgumentException("exactly one of response or error must be set");
        }

        /** True when a response was received, whatever its HTTP status. */
        public boolean isSuccess() {
            return response != null;
        }
    }

    private final List<Outcome> outcomes;

    ApiBatchResult(List<Outcome> outcomes) {
        this.outcomes = List.copyOf(Objects.requireNonNull(outcomes));
    }

    /** All outcomes in submission order. */
    public List<Outcome> outcomes() {
        return outcomes;
    }

    /** Responses of the requests that completed, in submission order. */
    public List<ApiResponse> responses() {
        return outcomes.stream().filter(Outcome::isSuccess).map(Outcome::response).collect(Collectors.toUnmodifiableList());
    }

    /** Outcomes that ended with an error. */
    public List<Outcome> failures() {
        return outcomes.stream().filter(o -> !o.isSuccess()).collect(Collectors.toUnmodifiableList());
    }

    public int size() {
        return outcomes.size();
    }

    public int successCount() {
        return (int) outcomes.stream().filter(Outcome::isSuccess).count();
    }

    public int failureCount() {
        return size() - successCount();
    }

    /** True when every request received a response. */
    public boolean allSucceeded() {
        return failureCount() == 0;
    }

    /** Number of responses with the given HTTP status. */
    public long countStatus(int status) {
        return responses().stream().filter(r -> r.status() == status).count();
    }

    /** Throws an {@link ApiException} naming the first failure when any request failed. */
    public ApiBatchResult assertAllSucceeded() {
        List<Outcome> failed = failures();
        if (!failed.isEmpty()) {
            Outcome first = failed.get(0);
            Throwable error = first.error();
            String method = error instanceof ApiFailure f ? f.getMethod() : null;
            String url = error instanceof ApiFailure f ? f.getUrl() : null;
            throw new ApiException(method, url, -1, null, "[ApiBatchRunner] " + failed.size() + " of " + size()
                    + " requests failed; first at index " + first.index() + ": " + error, error);
        }
        return this;
    }
}
