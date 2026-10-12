package io.testfly.unit.client;

import io.testfly.client.ApiBatchResult;
import io.testfly.client.ApiBatchRunner;
import io.testfly.client.ApiClient;
import io.testfly.client.ApiException;
import org.testng.annotations.Test;

import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.*;

@Test(singleThreaded = true)
public class ApiBatchRunnerTest extends ApiClientTestBase {

    @Test
    public void executeAllKeepsPartialResultsInOrder() throws Exception {
        String base = server(ex -> {
            int id = Integer.parseInt(ex.getRequestURI().getQuery().substring(3));
            reply(ex, id % 2 == 0 ? 200 : 500, "{\"id\":" + id + "}");
        });
        AtomicInteger counter = new AtomicInteger();

        ApiBatchResult result = ApiBatchRunner.fire(7).concurrently(3).request(() -> {
            int i = counter.getAndIncrement();
            // index 3 targets a closed port: transport failure
            return i == 3 ? ApiClient.get("http://127.0.0.1:1/down") : ApiClient.get(base + "/item").queryParam("id", i);
        }).executeAll();

        assertEquals(result.size(), 7);
        assertEquals(result.failureCount(), 1);
        assertEquals(result.failures().get(0).index(), 3);
        assertTrue(result.failures().get(0).error() instanceof ApiException, String.valueOf(result.failures().get(0).error()));
        assertEquals(result.successCount(), 6);
        assertEquals(result.countStatus(500), 2, "odd indexes 1 and 5; index 3 is the transport failure");
        for (ApiBatchResult.Outcome o : result.outcomes()) {
            if (o.isSuccess()) assertEquals(o.response().json("$.id"), String.valueOf(o.index()));
        }
        assertFalse(result.allSucceeded());
        ApiException summary = expectThrows(ApiException.class, result::assertAllSucceeded);
        assertTrue(summary.getMessage().contains("1 of 7 requests failed; first at index 3"), summary.getMessage());
    }

    @Test
    public void executeStillFailsFastAndSupplierErrorsAreOutcomes() throws Exception {
        expectThrows(CompletionException.class, () -> ApiBatchRunner.fire(2)
                .request(() -> ApiClient.get("http://127.0.0.1:1/down")).execute());

        AtomicInteger counter = new AtomicInteger();
        String base = server(ex -> reply(ex, 200, "ok"));
        ApiBatchResult result = ApiBatchRunner.fire(3).request(() -> {
            if (counter.getAndIncrement() == 1) throw new IllegalStateException("bad supplier");
            return ApiClient.get(base + "/ok");
        }).executeAll();
        assertEquals(result.failureCount(), 1);
        assertTrue(result.failures().get(0).error() instanceof IllegalStateException);
        assertEquals(result.responses().size(), 2);
        assertTrue(ApiBatchRunner.fire(1).request(() -> ApiClient.get(base + "/ok")).executeAll().assertAllSucceeded().allSucceeded());
    }

    @Test
    public void outcomeRequiresExactlyOneValue() {
        expectThrows(IllegalArgumentException.class, () -> new ApiBatchResult.Outcome(0, null, null));
    }
}
