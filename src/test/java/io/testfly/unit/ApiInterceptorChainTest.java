package io.testfly.unit;

import io.testfly.client.*;
import io.testfly.internal.api.ApiExecution;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.testng.Assert.*;

@Test(singleThreaded = true)
public class ApiInterceptorChainTest {
    private ApiRequest request() { return ApiRequest.builder().uri(URI.create("http://localhost/test")).build(); }
    private ApiResponse response(ApiRequest request, int status) {
        return ApiResponse.builder().request(request).status(status).build();
    }
    @AfterMethod public void cleanup() { ApiExecution.closeScope(); }

    @Test public void orderAndRepeatedProceed() {
        List<String> events = new ArrayList<>();
        AtomicInteger sends = new AtomicInteger();
        ApiInterceptor outer = chain -> {
            events.add("outer-in");
            ApiResponse result = chain.proceed(chain.request());
            events.add("outer-out"); return result;
        };
        ApiInterceptor refresh = chain -> {
            events.add("refresh");
            ApiResponse result = chain.proceed(chain.request());
            if (result.status() == 401) result = chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer new").build());
            return result;
        };
        ApiInterceptor downstream = chain -> {
            events.add("downstream");
            return chain.proceed(chain.request());
        };
        ApiResponse result = ApiExecution.invoke(List.of(outer, refresh, downstream), request(), r -> {
            events.add("transport");
            return response(r, sends.incrementAndGet() == 1 ? 401 : 200);
        }, ApiExecution.scope());
        assertEquals(result.status(), 200);
        assertEquals(result.request().header("authorization"), "Bearer new");
        assertEquals(events, List.of("outer-in", "refresh", "downstream", "transport", "downstream", "transport", "outer-out"));
    }

    @Test public void shortCircuitAndResponseRewrite() {
        ApiResponse result = ApiExecution.invoke(List.of(
                chain -> chain.proceed(chain.request()).newBuilder().header("X-Result", "changed").build(),
                chain -> ApiResponse.builder().request(chain.request()).status(200).body("{\"mock\":true}").build()),
                request(), r -> { fail("No transport for mock"); return null; }, ApiExecution.scope());
        result.assertStatus(200).assertJsonTrue("$.mock").assertHeader("x-result", "changed");
        assertTrue(result.isSynthetic());
    }

    @Test public void emptyChainUsesTransport() {
        assertEquals(ApiExecution.invoke(List.of(), request(), r -> response(r, 204), ApiExecution.scope()).status(), 204);
    }

    @Test public void expiredChainRejected() {
        AtomicReference<ApiInterceptor.Chain> saved = new AtomicReference<>();
        ApiExecution.invoke(List.of(chain -> { saved.set(chain); return response(chain.request(), 200); }),
                request(), r -> response(r, 200), ApiExecution.scope());
        expectThrows(IllegalStateException.class, () -> saved.get().proceed(request()));
        expectThrows(IllegalStateException.class, () -> saved.get().request());
    }

    @Test public void crossThreadProceedRejected() throws Exception {
        ApiExecution.invoke(List.of(chain -> {
            CompletableFuture<Throwable> result = new CompletableFuture<>();
            Thread worker = Thread.ofVirtual().start(() -> {
                try { chain.proceed(request()); result.complete(null); }
                catch (Throwable error) { result.complete(error); }
            });
            try { assertTrue(result.get(2, TimeUnit.SECONDS) instanceof IllegalStateException); worker.join(); }
            catch (Exception error) { throw new AssertionError(error); }
            return response(chain.request(), 200);
        }), request(), r -> response(r, 200), ApiExecution.scope());
    }

    @Test public void nullRequestAndResponseRejected() {
        expectThrows(NullPointerException.class, () -> ApiExecution.invoke(List.of(chain -> null), request(),
                r -> response(r, 200), ApiExecution.scope()));
        expectThrows(NullPointerException.class, () -> ApiExecution.invoke(List.of(chain -> chain.proceed(null)), request(),
                r -> response(r, 200), ApiExecution.scope()));
    }

    @Test public void requestDefensiveCopiesAndHeaderOperations() {
        byte[] original = "body".getBytes(StandardCharsets.UTF_8);
        ApiRequest r = request().newBuilder().header("X-Test", "one").addHeader("x-test", "two").body(original).build();
        original[0] = 'X'; r.body()[0] = 'Y';
        assertEquals(new String(r.body(), StandardCharsets.UTF_8), "body");
        assertEquals(r.headers().get("X-TEST"), List.of("one", "two"));
        expectThrows(UnsupportedOperationException.class, () -> r.headers().get("x-test").add("three"));
        expectThrows(UnsupportedOperationException.class, () -> r.headers().put("other", List.of("v")));
        ApiRequest replaced = r.newBuilder().header("x-TEST", "new").build();
        assertEquals(replaced.headers().get("X-Test"), List.of("new"));
        assertNull(replaced.newBuilder().removeHeader("X-TEST").build().header("x-test"));
        assertEquals(r.header("X-Test"), "one");
    }

    @Test public void invalidRequestAndResponseRejected() {
        expectThrows(IllegalArgumentException.class, () -> request().newBuilder().timeout(Duration.ZERO).build());
        expectThrows(IllegalArgumentException.class, () -> request().newBuilder().uri(URI.create("/relative")).build());
        expectThrows(IllegalArgumentException.class, () -> response(request(), 99));
        expectThrows(IllegalArgumentException.class, () -> response(request(), 600));
        expectThrows(NullPointerException.class, () -> ApiResponse.builder().status(200).build());
        expectThrows(IllegalArgumentException.class, () -> ApiResponse.builder().request(request()).status(200).durationMs(-1).build());
    }

    @Test public void copiedResponseRetainsDataAndDefaults() {
        ApiResponse initial = ApiResponse.builder().request(request()).status(200).header("X-Test", "one")
                .addHeader("x-test", "two").body("{\"value\":1}").durationMs(7).build();
        ApiResponse copy = initial.newBuilder().status(201).body("{\"value\":2}").build();
        assertEquals(copy.header("X-Test"), "one");
        assertEquals(copy.durationMs(), 7L);
        assertEquals(copy.json("$.value"), "2");
        assertEquals(initial.json("$.value"), "1");
        assertNull(copy.newBuilder().removeHeader("X-TEST").build().header("x-test"));
        assertEquals(response(request(), 204).body(), "");
        assertEquals(response(request(), 204).durationMs(), 0L);
    }
}
