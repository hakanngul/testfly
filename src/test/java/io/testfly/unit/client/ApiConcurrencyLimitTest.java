package io.testfly.unit.client;

import io.testfly.client.ApiBatchRunner;
import io.testfly.client.ApiClient;
import io.testfly.client.ApiMockRule;
import io.testfly.client.ApiResponse;
import org.testng.annotations.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.*;

@Test(singleThreaded = true)
public class ApiConcurrencyLimitTest extends ApiClientTestBase {

    @Test
    public void maxConcurrentRequestsCapsInFlightTransportCalls() throws Exception {
        config.getApi().setMaxConcurrentRequests(2);
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxSeen = new AtomicInteger();
        String base = server(ex -> {
            int now = inFlight.incrementAndGet();
            maxSeen.accumulateAndGet(now, Math::max);
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                inFlight.decrementAndGet();
            }
            reply(ex, 200, "ok");
        });

        var responses = ApiBatchRunner.fire(6).request(() -> ApiClient.get(base + "/slow")).execute();

        assertEquals(responses.size(), 6);
        assertEquals(maxSeen.get(), 2);
    }

    @Test
    public void mockedResponsesDoNotConsumePermits() throws Exception {
        config.getApi().setMaxConcurrentRequests(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(1);
        String base = server(ex -> {
            entered.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            reply(ex, 200, "real");
        });

        CompletableFuture<ApiResponse> blocking = ApiClient.get(base + "/hold").sendAsync();
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        ApiResponse mocked = ApiClient.get(base + "/mocked")
                .mockRule(ApiMockRule.builder().match(r -> r.uri().getPath().equals("/mocked"))
                        .respond(r -> ApiResponse.builder().request(r).status(204).build()).build())
                .sendAsync().get(2, TimeUnit.SECONDS);

        assertEquals(mocked.status(), 204);
        release.countDown();
        assertEquals(blocking.get(5, TimeUnit.SECONDS).body(), "real");
    }
}
