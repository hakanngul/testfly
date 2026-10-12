package io.testfly.unit.client;

import io.testfly.client.ApiAuth;
import io.testfly.client.ApiClient;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;

@Test(singleThreaded = true)
public class ApiAuthBasicTest extends ApiClientTestBase {

    @Test
    public void basicAuthEncodesCredentialsAsUtf8() throws Exception {
        AtomicReference<String> header = new AtomicReference<>();
        String base = server(ex -> { header.set(ex.getRequestHeaders().getFirst("Authorization")); reply(ex, 200, "ok"); });

        ApiClient.get(base + "/me").auth(ApiAuth.basicAuth("ada", "şifre")).send().assertStatus(200);

        // base64(UTF-8 "ada:şifre"), independent of -Dfile.encoding
        assertEquals(header.get(), "Basic YWRhOsWfaWZyZQ==");
    }

    @Test
    public void bearerAndApiKeyHeaders() throws Exception {
        AtomicReference<String> auth = new AtomicReference<>();
        AtomicReference<String> key = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        String base = server(ex -> {
            auth.set(ex.getRequestHeaders().getFirst("Authorization"));
            key.set(ex.getRequestHeaders().getFirst("X-Custom-Key"));
            query.set(ex.getRequestURI().getQuery());
            reply(ex, 200, "ok");
        });

        ApiClient.get(base + "/a").auth(ApiAuth.bearerToken("t0k")).send();
        assertEquals(auth.get(), "Bearer t0k");
        ApiClient.get(base + "/b").auth(ApiAuth.apiKey("X-Custom-Key", "k1")).send();
        assertEquals(key.get(), "k1");
        ApiClient.get(base + "/c").auth(ApiAuth.apiKeyQuery("api_key", "q1")).send();
        assertEquals(query.get(), "api_key=q1");
    }

    @Test
    public void interceptingLambdaFromTheGuideRetriesOnce() throws Exception {
        java.util.concurrent.atomic.AtomicInteger hits = new java.util.concurrent.atomic.AtomicInteger();
        String base = server(ex -> {
            hits.incrementAndGet();
            boolean ok = "Bearer fresh".equals(ex.getRequestHeaders().getFirst("Authorization"));
            reply(ex, ok ? 200 : 401, ok ? "ok" : "expired");
        });
        ApiAuth retryOn401 = (ApiAuth.Intercepting) chain -> {
            io.testfly.client.ApiResponse first = chain.proceed(chain.request());
            if (first.status() != 401) return first;
            return chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer fresh").build());
        };

        ApiClient.get(base + "/me").auth(retryOn401).send().assertStatus(200);
        assertEquals(hits.get(), 2);
    }
}
