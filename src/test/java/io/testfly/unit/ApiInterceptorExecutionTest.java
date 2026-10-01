package io.testfly.unit;

import com.sun.net.httpserver.HttpServer;
import io.testfly.client.*;
import io.testfly.config.TestFlyConfig;
import io.testfly.internal.TestFlyContext;
import io.testfly.internal.api.ApiExecution;
import io.testfly.metrics.ExecutionMetrics;
import io.testfly.steps.StepLogger;
import io.testfly.steps.StepStatus;
import org.mockito.MockedStatic;
import org.testng.annotations.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

@Test(singleThreaded = true)
public class ApiInterceptorExecutionTest {
    private MockedStatic<TestFlyContext> context;
    private TestFlyConfig config;
    private HttpServer server;
    private ExecutorService serverExecutor;
    private String testId;

    @BeforeMethod public void setup(java.lang.reflect.Method method) {
        io.testfly.internal.api.ApiExecution.cleanupTestContext();
        config = new TestFlyConfig();
        config.setApi(new TestFlyConfig.Api());
        context = mockStatic(TestFlyContext.class, CALLS_REAL_METHODS);
        context.when(TestFlyContext::isInitialized).thenReturn(true);
        context.when(TestFlyContext::getConfig).thenReturn(config);
        testId = "chain-unit-" + method.getName();
        TestFlyContext.setCurrentTestId(testId);
        TestFlyContext.setCurrentTest(getClass(), method);
        ExecutionMetrics.markStart(testId);
        ExecutionMetrics.clearSteps(testId);
    }
    @AfterMethod public void cleanup() {
        io.testfly.internal.api.ApiExecution.cleanupTestContext();
        ApiClient.clearInterceptors();
        ApiClient.clearGlobalSpec();
        TestFlyContext.clearCurrentTest();
        context.close();
        if (server != null) { server.stop(0); server = null; }
        if (serverExecutor != null) { serverExecutor.shutdownNow(); serverExecutor = null; }
    }
    private ApiResponse mockResponse(ApiRequest request, int status) {
        return ApiResponse.builder().request(request).status(status).body("{}").build();
    }
    private io.testfly.metrics.TestTiming timing() {
        return ExecutionMetrics.getTimings().stream().filter(t -> t.getTestId().equals(testId)).findFirst().orElse(null);
    }
    private ApiClient client() { return ApiClient.get("http://localhost:1/test"); }
    private void retry(int attempts, boolean exceptions) {
        var retry = config.getApi().getRetry();
        retry.setEnabled(true); retry.setMaxAttempts(attempts); retry.setBackoffMs(0); retry.setRetryOnException(exceptions);
    }
    private String startServer(com.sun.net.httpserver.HttpHandler handler) throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        serverExecutor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(serverExecutor); server.createContext("/", handler); server.start();
        return "http://" + (server.getAddress().getAddress() instanceof Inet6Address ? "[::1]" : "127.0.0.1") + ":" + server.getAddress().getPort();
    }
    private void reply(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes); exchange.close();
    }

    @Test public void registrationOrderAndLegacyResponseForMock() {
        List<String> order = new ArrayList<>(); AtomicInteger requestHooks = new AtomicInteger();
        ApiClient.addInterceptor(chain -> { order.add("test"); return chain.proceed(chain.request()); });
        ApiClient.addRequestInterceptor(builder -> requestHooks.incrementAndGet());
        ApiClient.addResponseInterceptor(response -> order.add("legacy-response"));
        client().interceptor(chain -> { order.add("request"); return mockResponse(chain.request(), 200); }).send();
        assertEquals(order, List.of("test", "request", "legacy-response"));
        assertEquals(requestHooks.get(), 0);
        ApiClient.clearChainInterceptors();
        order.clear(); client().interceptor(chain -> { order.add("request"); return mockResponse(chain.request(), 200); }).send();
        assertEquals(order, List.of("request", "legacy-response"));
    }

    @Test public void retryRestartsChainAndAuth() {
        retry(3, true); AtomicInteger calls = new AtomicInteger(); AtomicInteger authCalls = new AtomicInteger();
        ApiClient.setGlobalAuth(builder -> builder.setHeader("Authorization", "Bearer " + authCalls.incrementAndGet()));
        List<String> tokens = new ArrayList<>();
        ApiResponse response = client().interceptor(chain -> {
            tokens.add(chain.request().header("Authorization"));
            return mockResponse(chain.request(), calls.incrementAndGet() < 3 ? 503 : 200);
        }).send();
        assertEquals(response.status(), 200); assertEquals(tokens, List.of("Bearer 1", "Bearer 2", "Bearer 3"));
    }

    @Test public void retryDisabledAndExhaustionReturnStatus() {
        AtomicInteger calls = new AtomicInteger();
        ApiClient client = client().interceptor(chain -> { calls.incrementAndGet(); return mockResponse(chain.request(), 503); });
        assertEquals(client.send().status(), 503); assertEquals(calls.get(), 1);
        retry(2, false); calls.set(0);
        assertEquals(client.send().status(), 503); assertEquals(calls.get(), 2);
    }

    @Test public void interceptorAndAssertionErrorsDoNotRetry() {
        retry(3, true); AtomicInteger calls = new AtomicInteger();
        expectThrows(IllegalStateException.class, () -> client().interceptor(chain -> {
            calls.incrementAndGet(); throw new IllegalStateException("broken middleware");
        }).send());
        assertEquals(calls.get(), 1); calls.set(0);
        expectThrows(ApiException.class, () -> client().interceptor(chain -> {
            calls.incrementAndGet(); return mockResponse(chain.request(), 401).assertStatus(200);
        }).send());
        assertEquals(calls.get(), 1);
    }

    @Test public void responseHookErrorsDoNotRetry() {
        retry(3, true); AtomicInteger calls = new AtomicInteger();
        ApiClient.addResponseInterceptor(response -> { throw new IllegalArgumentException("hook failed"); });
        expectThrows(IllegalArgumentException.class, () -> client().interceptor(chain -> {
            calls.incrementAndGet(); return mockResponse(chain.request(), 200);
        }).send());
        assertEquals(calls.get(), 1);
    }

    @Test public void syncAsyncContextAndSnapshotParity() throws Exception {
        ApiClient.setGlobalAuth(ApiAuth.bearerToken("caller"));
        ApiClient.addInterceptor(chain -> {
            assertEquals(TestFlyContext.getCurrentTestId(), testId);
            assertEquals(TestFlyContext.getCurrentTestClass(), getClass());
            StepLogger.step("inside middleware");
            return chain.proceed(chain.request());
        });
        ApiClient request = client().body(Map.of("value", 1)).interceptor(chain -> mockResponse(chain.request(), 200));
        ApiResponse sync = request.send();
        CompletableFuture<ApiResponse> future = request.sendAsync();
        request.body(Map.of("value", 2)); ApiClient.clearChainInterceptors(); ApiClient.clearGlobalAuth();
        ApiResponse async = future.get(2, TimeUnit.SECONDS);
        assertEquals(async.request().header("authorization"), sync.request().header("authorization"));
        assertEquals(async.request().body(), sync.request().body());
        assertEquals(timing().getSteps().stream()
                .filter(step -> step.getName().equals("inside middleware")).count(), 2L);
    }

    @Test public void asyncCapturesRetryAndLoggingSettings() throws Exception {
        retry(2, false);
        config.getApi().setLogBody(true);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        CompletableFuture<ApiResponse> future = client().interceptor(chain -> {
            if (calls.incrementAndGet() == 1) {
                entered.countDown();
                try { release.await(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new CancellationException(); }
                return mockResponse(chain.request(), 503);
            }
            return mockResponse(chain.request(), 200).newBuilder().body("snapshot-body").build();
        }).sendAsync();
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            config.getApi().getRetry().setEnabled(false);
            config.getApi().getRetry().setMaxAttempts(99);
            config.getApi().setLogBody(false);
        } finally { release.countDown(); }
        assertEquals(future.get(2, TimeUnit.SECONDS).status(), 200);
        assertEquals(calls.get(), 2);
        assertTrue(timing().getSteps().stream().anyMatch(step -> step.getName().contains("Body: snapshot-body")));
    }

    @Test public void requestHookErrorsDoNotRetry() {
        retry(3, true);
        AtomicInteger calls = new AtomicInteger();
        ApiClient.addRequestInterceptor(builder -> { calls.incrementAndGet(); throw new IllegalStateException("hook bug"); });
        expectThrows(IllegalStateException.class, client()::send);
        assertEquals(calls.get(), 1);
    }

    @Test public void compiledExamplesProduceEquivalentMockResponses() {
        var sync = io.testfly.examples.api.ApiInterceptorExamples.syncPayment("http://localhost:1");
        var async = io.testfly.examples.api.ApiInterceptorExamples.asyncPayment("http://localhost:1").join();
        sync.assertStatus(200).assertJson("$.status", "mocked");
        assertEquals(sync.body(), async.body());
    }

    @Test public void asyncPreparationErrorReturnsFailedFuture() {
        CompletableFuture<ApiResponse> future = ApiClient.get("http://localhost/{id}").sendAsync();
        assertTrue(future.isCompletedExceptionally());
        assertTrue(expectThrows(CompletionException.class, future::join).getCause() instanceof IllegalStateException);
    }

    @Test public void cancelInterruptsWorkerAndCleanupSuppressesLateLogs() throws Exception {
        CountDownLatch started = new CountDownLatch(1), interrupted = new CountDownLatch(1), exited = new CountDownLatch(1);
        CompletableFuture<ApiResponse> future = client().interceptor(chain -> {
            started.countDown();
            try { new CountDownLatch(1).await(); }
            catch (InterruptedException e) { interrupted.countDown(); Thread.currentThread().interrupt(); }
            StepLogger.step("late step", StepStatus.FAIL);
            exited.countDown();
            return mockResponse(chain.request(), 200);
        }).sendAsync();
        assertTrue(started.await(2, TimeUnit.SECONDS));
        io.testfly.internal.api.ApiExecution.cleanupTestContext();
        assertTrue(future.isCancelled()); assertTrue(interrupted.await(2, TimeUnit.SECONDS));
        assertTrue(exited.await(2, TimeUnit.SECONDS));
        var timing = timing();
        assertTrue(timing == null || timing.getSteps().stream().noneMatch(step -> step.getName().equals("late step")));
        assertEquals(client().interceptor(chain -> mockResponse(chain.request(), 200)).send().status(), 200);
    }

    @Test public void explicitCancellationPreventsNextProceed() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), stopped = new CountDownLatch(1);
        AtomicBoolean proceeded = new AtomicBoolean();
        CompletableFuture<ApiResponse> future = client().interceptor(chain -> {
            entered.countDown();
            try { new CountDownLatch(1).await(); }
            catch (InterruptedException ignored) { /* Middleware deliberately swallows interrupt. */ }
            try { return chain.proceed(chain.request()); }
            finally { stopped.countDown(); }
        }).interceptor(chain -> { proceeded.set(true); return mockResponse(chain.request(), 200); }).sendAsync();
        assertTrue(entered.await(2, TimeUnit.SECONDS)); assertTrue(future.cancel(true));
        assertTrue(stopped.await(2, TimeUnit.SECONDS)); assertFalse(proceeded.get());
    }

    @Test public void interruptedBackoffStopsRetry() throws Exception {
        retry(3, true); config.getApi().getRetry().setBackoffMs(60_000);
        CountDownLatch first = new CountDownLatch(1); AtomicInteger calls = new AtomicInteger();
        CompletableFuture<ApiResponse> future = client().interceptor(chain -> {
            calls.incrementAndGet(); first.countDown(); return mockResponse(chain.request(), 503);
        }).sendAsync();
        assertTrue(first.await(2, TimeUnit.SECONDS)); assertTrue(future.cancel(true));
        assertEquals(calls.get(), 1);
    }

    @Test public void batchLimitOrderAndContext() {
        AtomicInteger created = new AtomicInteger(), active = new AtomicInteger(), maximum = new AtomicInteger();
        CountDownLatch simultaneous = new CountDownLatch(2);
        ApiClient.addInterceptor(chain -> {
            assertEquals(TestFlyContext.getCurrentTestId(), testId);
            int current = active.incrementAndGet(); maximum.accumulateAndGet(current, Math::max);
            try {
                simultaneous.countDown();
                if (!simultaneous.await(2, TimeUnit.SECONDS)) throw new AssertionError("Expected two concurrent calls");
                return chain.proceed(chain.request());
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt(); throw new CancellationException();
            } finally { active.decrementAndGet(); }
        });
        List<ApiResponse> responses = ApiBatchRunner.fire(12).concurrently(2).request(() -> {
            int id = created.incrementAndGet();
            return client().interceptor(chain -> mockResponse(chain.request(), 200).newBuilder().body(Integer.toString(id)).build());
        }).execute();
        assertEquals(maximum.get(), 2);
        for (int i = 0; i < responses.size(); i++) assertEquals(responses.get(i).body(), Integer.toString(i + 1));
        assertEquals(ApiBatchRunner.fire(3).request(() -> client().interceptor(chain -> mockResponse(chain.request(), 200))).execute().size(), 3);
    }

    @Test public void shutdownAndRestartExecutor() {
        assertEquals(client().interceptor(chain -> mockResponse(chain.request(), 200)).sendAsync().join().status(), 200);
        io.testfly.internal.api.ApiExecution.shutdown();
        assertEquals(client().interceptor(chain -> mockResponse(chain.request(), 201)).sendAsync().join().status(), 201);
    }

    @Test public void localhostSmokeAuthRefreshLegacyHooksAndReport() throws Exception {
        config.getApi().setLogCurl(true);
        AtomicInteger sends = new AtomicInteger(), hooks = new AtomicInteger();
        String base = startServer(exchange -> {
            sends.incrementAndGet();
            assertEquals(exchange.getRequestHeaders().getFirst("X-Legacy"), "yes");
            assertEquals(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), "{\"value\":1}");
            reply(exchange, "Bearer fresh".equals(exchange.getRequestHeaders().getFirst("Authorization")) ? 200 : 401, "{}");
        });
        ApiClient.addRequestInterceptor(builder -> builder.setHeader("X-Legacy", "yes"));
        ApiClient.addResponseInterceptor(response -> hooks.incrementAndGet());
        ApiInterceptor refresh = chain -> {
            ApiResponse result = chain.proceed(chain.request());
            return result.status() == 401 ? chain.proceed(chain.request().newBuilder().header("authorization", "Bearer fresh").build()) : result;
        };
        ApiClient request = ApiClient.post(base + "/before").body(Map.of("value", 1)).auth(ApiAuth.bearerToken("old"))
                .interceptor(chain -> chain.proceed(chain.request().newBuilder().uri(URI.create(base + "/actual")).build()))
                .interceptor(refresh);
        ApiResponse sync = request.send(); ApiResponse async = request.sendAsync().get(3, TimeUnit.SECONDS);
        assertFalse(sync.isSynthetic()); assertEquals(sync.status(), 200); assertEquals(async.status(), 200);
        assertEquals(sync.requestUrl(), base + "/actual"); assertEquals(sends.get(), 4); assertEquals(hooks.get(), 2);
        String logs = timing().getSteps().stream().map(step -> step.getName()).collect(java.util.stream.Collectors.joining("\n"));
        assertTrue(logs.contains("/actual")); assertTrue(logs.toLowerCase(Locale.ROOT).contains("authorization: ***"));
        assertFalse(logs.contains("Bearer fresh")); assertFalse(logs.contains("Bearer old"));
        assertTrue(timing().getSteps().stream().noneMatch(step -> step.getStatus().equals("FAIL")));
    }

    @Test public void transportFailureHonorsRetryOnException() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        String base = startServer(exchange -> { attempts.incrementAndGet(); reply(exchange, 200, "{}"); });
        // Invalid endpoint gives a deterministic connect failure, tracked by outer middleware.
        retry(3, false); AtomicInteger chains = new AtomicInteger();
        ApiClient broken = ApiClient.get("http://127.0.0.1:0/test").interceptor(chain -> {
            chains.incrementAndGet(); return chain.proceed(chain.request());
        });
        expectThrows(ApiException.class, broken::send); assertEquals(chains.get(), 1);
        retry(3, true); chains.set(0);
        expectThrows(ApiException.class, broken::send); assertEquals(chains.get(), 3);
        assertEquals(ApiClient.get(base).send().status(), 200);
    }

    @Test public void jsonFormAndMultipartArePreparedOnce() throws Exception {
        retry(2, false);
        Path file = Files.createTempFile("testfly-body", ".txt");
        try {
            Files.writeString(file, "original");
            List<byte[]> bodies = new ArrayList<>();
            AtomicInteger count = new AtomicInteger();
            ApiClient multipart = ApiClient.post("http://localhost:1/upload").multipart("file", file).interceptor(chain -> {
                bodies.add(chain.request().body());
                try { Files.writeString(file, "changed"); } catch (Exception e) { throw new AssertionError(e); }
                return mockResponse(chain.request(), count.incrementAndGet() == 1 ? 503 : 200);
            });
            multipart.send(); assertEquals(bodies.get(0), bodies.get(1));
            assertTrue(new String(bodies.get(1), StandardCharsets.UTF_8).contains("original"));
            bodies.clear(); count.set(0);
            Map<String, Object> body = new HashMap<>(); body.put("value", 1);
            ApiClient.post("http://localhost:1/json").body(body).interceptor(chain -> {
                bodies.add(chain.request().body()); body.put("value", 2);
                return mockResponse(chain.request(), count.incrementAndGet() == 1 ? 503 : 200);
            }).send(); assertEquals(bodies.get(0), bodies.get(1));
            ApiResponse form = ApiClient.post("http://localhost:1/form").formParam("a", "a b")
                    .interceptor(chain -> mockResponse(chain.request(), 200)).send();
            assertEquals(new String(form.request().body(), StandardCharsets.UTF_8), "a=a+b");
            assertEquals(form.request().header("Content-Type"), "application/x-www-form-urlencoded");
        } finally { Files.deleteIfExists(file); }
    }

    @Test public void cookiesPersistAcrossAsyncAndRemainTestScoped() throws Exception {
        String base = startServer(exchange -> {
            if (exchange.getRequestURI().getPath().equals("/set")) exchange.getResponseHeaders().add("Set-Cookie", "session=caller; Path=/");
            reply(exchange, 200, Objects.toString(exchange.getRequestHeaders().getFirst("Cookie"), "none"));
        });
        ApiClient.get(base + "/set").withCookies().sendAsync().get(3, TimeUnit.SECONDS);
        assertEquals(ApiClient.get(base + "/read").withCookies().sendAsync().get(3, TimeUnit.SECONDS).body(), "session=caller");
        assertEquals(ApiClient.get(base + "/read").withCookies().send().body(), "session=caller");
        io.testfly.internal.api.ApiExecution.cleanupTestContext();
        assertEquals(ApiClient.get(base + "/read").withCookies().send().body(), "none");
    }

    @Test public void parallelTestsKeepCookiesAuthAndStepsSeparate() throws Exception {
        String base = startServer(exchange -> {
            if (exchange.getRequestURI().getPath().equals("/set")) {
                exchange.getResponseHeaders().add("Set-Cookie", "session=" + exchange.getRequestURI().getRawQuery());
            }
            reply(exchange, 200, Objects.toString(exchange.getRequestHeaders().getFirst("Cookie"), "none")
                    + "|" + exchange.getRequestHeaders().getFirst("Authorization"));
        });
        CountDownLatch ready = new CountDownLatch(2);
        List<CompletableFuture<String>> results = new ArrayList<>();
        for (String name : List.of("left", "right")) {
            CompletableFuture<String> result = new CompletableFuture<>(); results.add(result);
            Thread.ofVirtual().start(() -> {
                try {
                    TestFlyContext.setCurrentTestId(testId + "-" + name);
                    ApiClient.setGlobalAuth(ApiAuth.bearerToken(name));
                    ApiClient.addInterceptor(chain -> {
                        StepLogger.step("marker-" + name);
                        return chain.proceed(chain.request());
                    });
                    ApiClient.get(base + "/set?" + name).withCookies().sendAsync().join();
                    ready.countDown();
                    if (!ready.await(3, TimeUnit.SECONDS)) throw new AssertionError("Parallel setup timed out");
                    result.complete(ApiClient.get(base + "/read").withCookies().sendAsync().join().body());
                } catch (Throwable error) { result.completeExceptionally(error); }
                finally { ApiExecution.cleanupTestContext(); TestFlyContext.clearCurrentTest(); }
            });
        }
        assertEquals(results.get(0).get(5, TimeUnit.SECONDS), "session=left|Bearer left");
        assertEquals(results.get(1).get(5, TimeUnit.SECONDS), "session=right|Bearer right");
        for (String name : List.of("left", "right")) {
            var steps = ExecutionMetrics.getTimings().stream().filter(t -> t.getTestId().equals(testId + "-" + name))
                    .findFirst().orElseThrow().getSteps();
            assertEquals(steps.stream().filter(step -> step.getName().equals("marker-" + name)).count(), 2L);
            assertTrue(steps.stream().noneMatch(step -> step.getName().equals("marker-" + (name.equals("left") ? "right" : "left"))));
        }
    }

    @Test public void separateThreadsHaveSeparateAuthAndInterceptors() throws Exception {
        CompletableFuture<String> other = new CompletableFuture<>();
        ApiClient.setGlobalAuth(ApiAuth.bearerToken("main"));
        ApiClient.addInterceptor(chain -> mockResponse(chain.request(), 201));
        Thread worker = Thread.ofVirtual().start(() -> {
            try {
                ApiClient.setGlobalAuth(ApiAuth.bearerToken("other"));
                ApiClient.addInterceptor(chain -> mockResponse(chain.request(), 202));
                ApiResponse result = client().sendAsync().join();
                other.complete(result.status() + ":" + result.request().header("Authorization"));
            } catch (Throwable error) { other.completeExceptionally(error); }
            finally { io.testfly.internal.api.ApiExecution.cleanupTestContext(); }
        });
        ApiResponse main = client().sendAsync().get(2, TimeUnit.SECONDS);
        assertEquals(main.status(), 201); assertEquals(main.request().header("Authorization"), "Bearer main");
        assertEquals(other.get(2, TimeUnit.SECONDS), "202:Bearer other"); worker.join();
    }
}
