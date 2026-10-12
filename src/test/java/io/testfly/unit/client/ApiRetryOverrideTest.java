package io.testfly.unit.client;

import io.testfly.client.ApiClient;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;

@Test(singleThreaded = true)
public class ApiRetryOverrideTest extends ApiClientTestBase {

    private final AtomicInteger calls = new AtomicInteger();

    @org.testng.annotations.BeforeMethod
    public void resetCalls() {
        calls.set(0);
    }

    @Test
    public void perRequestRetryWorksWhenConfigDisabled() throws Exception {
        String counted = server(ex -> { calls.incrementAndGet(); reply(ex, 503, "x"); });

        assertEquals(ApiClient.get(counted + "/a").retry(3, Duration.ZERO).send().status(), 503);
        assertEquals(calls.get(), 3);
    }

    @Test
    public void retryOnReplacesConfiguredStatuses() throws Exception {
        String base = server(ex -> { calls.incrementAndGet(); reply(ex, 429, "slow down"); });

        ApiClient.get(base + "/a").retry(2, Duration.ZERO).send();
        assertEquals(calls.get(), 1, "429 is not in the default retry statuses");

        calls.set(0);
        ApiClient.get(base + "/a").retry(4, Duration.ZERO).retryOn(429).send();
        assertEquals(calls.get(), 4);
    }

    @Test
    public void noRetryOverridesEnabledConfig() throws Exception {
        var retry = config.getApi().getRetry();
        retry.setEnabled(true);
        retry.setMaxAttempts(3);
        retry.setBackoffMs(0);
        String base = server(ex -> { calls.incrementAndGet(); reply(ex, 502, "x"); });

        ApiClient.get(base + "/a").send();
        assertEquals(calls.get(), 3);

        calls.set(0);
        ApiClient.get(base + "/a").noRetry().send();
        assertEquals(calls.get(), 1);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void invalidAttemptsRejected() {
        ApiClient.get("http://localhost:1/a").retry(0);
    }
}
