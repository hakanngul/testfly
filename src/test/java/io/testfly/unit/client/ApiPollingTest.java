package io.testfly.unit.client;

import io.testfly.client.ApiClient;
import io.testfly.client.ApiException;
import io.testfly.client.ApiPolling;
import io.testfly.client.ApiResponse;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.*;

@Test(singleThreaded = true)
public class ApiPollingTest extends ApiClientTestBase {

    private final AtomicInteger calls = new AtomicInteger();

    @org.testng.annotations.BeforeMethod
    public void resetCalls() {
        calls.set(0);
    }

    private String statusSequence(int... statuses) throws Exception {
        return server(ex -> {
            int n = calls.getAndIncrement();
            int status = statuses[Math.min(n, statuses.length - 1)];
            reply(ex, status, "{\"n\":" + n + ",\"state\":\"" + (n >= 2 ? "DONE" : "RUNNING") + "\"}");
        });
    }

    private static ApiPolling fast(Duration timeout) {
        return ApiPolling.within(timeout).interval(Duration.ofMillis(50));
    }

    @Test
    public void conditionSatisfiedAfterSeveralAttempts() throws Exception {
        String base = statusSequence(200);

        ApiResponse done = ApiClient.get(base + "/job").pollUntil(r -> "DONE".equals(r.json("$.state")), fast(Duration.ofSeconds(5)));

        assertEquals(done.json("$.n"), "2");
        assertEquals(calls.get(), 3);
        assertTrue(steps().contains("Condition satisfied on attempt 3"), steps());
    }

    @Test
    public void legacyOverloadsStillWork() throws Exception {
        String base = statusSequence(200);
        ApiResponse done = ApiClient.get(base + "/job")
                .pollUntil(r -> "DONE".equals(r.json("$.state")), Duration.ofSeconds(5), Duration.ofMillis(50));
        assertEquals(done.json("$.state"), "DONE");
    }

    @Test
    public void failFastStatusStopsImmediately() throws Exception {
        String base = statusSequence(500);

        ApiException error = expectThrows(ApiException.class, () -> ApiClient.get(base + "/job")
                .pollUntil(r -> false, fast(Duration.ofSeconds(5)).failFastOn(500)));

        assertEquals(calls.get(), 1);
        assertEquals(error.getStatus(), 500);
        assertTrue(error.getMessage().contains("fail-fast status 500"), error.getMessage());
    }

    @Test
    public void predicateErrorsAreSwallowedByDefaultAndRethrownOnRequest() throws Exception {
        String base = statusSequence(200);

        ApiResponse done = ApiClient.get(base + "/job").pollUntil(r -> {
            if (calls.get() == 1) throw new IllegalStateException("not yet parseable");
            return "DONE".equals(r.json("$.state"));
        }, fast(Duration.ofSeconds(5)));
        assertEquals(done.json("$.state"), "DONE");

        calls.set(0);
        ApiException error = expectThrows(ApiException.class, () -> ApiClient.get(base + "/job").pollUntil(r -> {
            throw new IllegalStateException("broken predicate");
        }, fast(Duration.ofSeconds(5)).rethrowPredicateErrors(true)));
        assertEquals(calls.get(), 1);
        assertTrue(error.getCause() instanceof IllegalStateException);
    }

    @Test
    public void attemptLimitAndTimeoutCarryLastState() throws Exception {
        String base = statusSequence(202);

        ApiException limited = expectThrows(ApiException.class, () -> ApiClient.get(base + "/job")
                .pollUntil(r -> false, fast(Duration.ofSeconds(5)).maxAttempts(2)));
        assertEquals(calls.get(), 2);
        assertEquals(limited.getStatus(), 202);
        assertTrue(limited.getMessage().contains("attempt limit reached"), limited.getMessage());

        ApiException timeout = expectThrows(ApiException.class, () -> ApiClient.get(base + "/job")
                .pollUntil(r -> { throw new IllegalArgumentException("bad json"); }, fast(Duration.ofMillis(300))));
        assertTrue(timeout.getMessage().contains("Polling timeout exceeded"), timeout.getMessage());
        assertTrue(timeout.getCause() instanceof IllegalArgumentException, String.valueOf(timeout.getCause()));
    }

    @Test
    public void transportFailuresAreRetriedUntilTimeout() {
        ApiException error = expectThrows(ApiException.class, () -> ApiClient.get("http://127.0.0.1:1/down")
                .pollUntil(r -> true, fast(Duration.ofMillis(300))));
        assertEquals(error.getStatus(), 0);
        assertNotNull(error.getCause());
    }
}
