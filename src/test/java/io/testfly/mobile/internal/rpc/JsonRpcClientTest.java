package io.testfly.mobile.internal.rpc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.testfly.mobile.internal.rpc.JsonRpcException.Kind.*;
import static org.testng.Assert.*;

public class JsonRpcClientTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    public void sendsEnvelopeAndHeadersToProxyBasePath() throws Exception {
        AtomicReference<JsonNode> requestSeen = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> verb = new AtomicReference<>();
        try (LocalServer server = new LocalServer(exchange -> {
            JsonNode request = readRequest(exchange);
            requestSeen.set(request);
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            path.set(exchange.getRequestURI().getPath());
            verb.set(exchange.getRequestMethod());
            send(exchange, success(request, JSON.createObjectNode().put("online", true)));
        }); JsonRpcClient client = new JsonRpcClient(new JsonRpcOptions(server.uri("/lab/mobile/"),
                false, Map.of("Authorization", "Bearer request-secret"), Duration.ofSeconds(5), 1024))) {
            JsonNode result = client.call("server.info", Map.of("example", "value"), budget());
            assertTrue(result.get("online").booleanValue());
            assertEquals(path.get(), "/lab/mobile/rpc");
            assertEquals(verb.get(), "POST");
            assertEquals(authorization.get(), "Bearer request-secret");
            assertEquals(requestSeen.get().get("jsonrpc").textValue(), "2.0");
            assertEquals(requestSeen.get().get("method").textValue(), "server.info");
            assertEquals(requestSeen.get().get("params").get("example").textValue(), "value");
        }
    }

    @Test
    public void acceptsExplicitNullResult() throws Exception {
        try (LocalServer server = new LocalServer(exchange -> send(exchange,
                success(readRequest(exchange), JSON.nullNode())));
             JsonRpcClient client = new JsonRpcClient(JsonRpcOptions.defaults(server.uri("")))) {
            assertTrue(client.call("example.read", Map.of(), budget()).isNull());
        }
    }

    @Test
    public void rpcErrorsKeepCodeButDoNotExposeServerMessageDataOrInput() throws Exception {
        try (LocalServer server = new LocalServer(exchange -> {
            JsonNode request = readRequest(exchange);
            send(exchange, "{\"jsonrpc\":\"2.0\",\"id\":" + request.get("id")
                    + ",\"error\":{\"code\":-32000,\"message\":\"server-secret\","
                    + "\"data\":{\"password\":\"input-secret\"}}}");
        }); JsonRpcClient client = new JsonRpcClient(JsonRpcOptions.defaults(server.uri("")))) {
            JsonRpcException error = expectThrows(JsonRpcException.class,
                    () -> client.call("example.mutate", Map.of("password", "input-secret"), budget()));
            assertEquals(error.kind(), RPC);
            assertEquals(error.rpcCode().orElseThrow(), -32000);
            assertSanitized(error, "server-secret", "input-secret");
            assertEquals(server.requests.get(), 1);
        }
    }

    @DataProvider
    public Object[][] invalidEnvelopes() {
        return new Object[][] {
                {"{\"jsonrpc\":\"1.0\",\"id\":1,\"result\":true}"},
                {"{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":true}"},
                {"{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":true}"},
                {"{\"jsonrpc\":\"2.0\",\"id\":null,\"result\":true}"},
                {"{\"jsonrpc\":\"2.0\",\"result\":true}"},
                {"{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":true,\"error\":null}"},
                {"{\"jsonrpc\":\"2.0\",\"id\":1}"},
                {"{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":\"-1\",\"message\":\"secret\"}}"},
                {"{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":null}"},
                {"{\"jsonrpc\":\"2.0\",\"id\":1,\"id\":1,\"result\":true}"},
                {"{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":true} {}"},
                {"[]"}, {"not-json-secret"}, {""}
        };
    }

    @Test(dataProvider = "invalidEnvelopes")
    public void rejectsMalformedOrMismatchedResponses(String body) throws Exception {
        try (LocalServer server = new LocalServer(exchange -> {
            readRequest(exchange);
            send(exchange, body);
        }); JsonRpcClient client = new JsonRpcClient(JsonRpcOptions.defaults(server.uri("")))) {
            JsonRpcException error = expectThrows(JsonRpcException.class,
                    () -> client.call("example.read", Map.of(), budget()));
            assertEquals(error.kind(), PROTOCOL);
            assertSanitized(error, "secret");
        }
    }

    @DataProvider
    public Object[][] framing() {
        return new Object[][] { {false}, {true} };
    }

    @Test(dataProvider = "framing")
    public void boundsResponseSizeWithFixedLengthOrChunkedBodies(boolean chunked) throws Exception {
        try (LocalServer server = new LocalServer(exchange -> {
            readRequest(exchange);
            byte[] body = ("oversize-secret" + "x".repeat(4096)).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, chunked ? 0 : body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        }); JsonRpcClient client = new JsonRpcClient(new JsonRpcOptions(server.uri(""), false,
                Map.of(), Duration.ofSeconds(5), 128))) {
            JsonRpcException error = expectThrows(JsonRpcException.class,
                    () -> client.call("example.read", Map.of(), budget()));
            assertEquals(error.kind(), PROTOCOL);
            assertSanitized(error, "oversize-secret");
        }
    }

    @Test
    public void neverFollowsRedirectsOrRetriesFailedMutations() throws Exception {
        AtomicInteger destinationCalls = new AtomicInteger();
        try (LocalServer destination = new LocalServer(exchange -> {
            destinationCalls.incrementAndGet();
            send(exchange, "{}");
        }); LocalServer redirect = new LocalServer(exchange -> {
            readRequest(exchange);
            exchange.getResponseHeaders().set("Location", destination.uri("/secret-target").toString());
            exchange.sendResponseHeaders(307, -1);
            exchange.close();
        }); JsonRpcClient client = new JsonRpcClient(JsonRpcOptions.defaults(redirect.uri("")))) {
            JsonRpcException error = expectThrows(JsonRpcException.class,
                    () -> client.call("example.mutate", Map.of(), budget()));
            assertEquals(error.kind(), TRANSPORT);
            assertEquals(redirect.requests.get(), 1);
            assertEquals(destinationCalls.get(), 0);
            assertSanitized(error, "secret-target");
        }
        try (LocalServer server = new LocalServer(exchange -> {
            readRequest(exchange);
            byte[] body = "upstream-secret".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        }); JsonRpcClient client = new JsonRpcClient(JsonRpcOptions.defaults(server.uri("")))) {
            JsonRpcException error = expectThrows(JsonRpcException.class,
                    () -> client.call("example.mutate", Map.of(), budget()));
            assertEquals(error.kind(), TRANSPORT);
            assertEquals(server.requests.get(), 1);
            assertSanitized(error, "upstream-secret");
        }
    }

    @Test(dataProvider = "framing")
    public void boundsStreamingBodyByActionAndRpcBudget(boolean rpcBudgetIsShorter) throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch bodyStarted = new CountDownLatch(1);
        try (LocalServer server = new LocalServer(exchange -> {
            JsonNode request = readRequest(exchange);
            if (request.get("method").textValue().equals("warm")) {
                send(exchange, success(request, JSON.nullNode()));
                return;
            }
            exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) {
                output.write('{');
                output.flush();
                bodyStarted.countDown();
                await(release);
            }
        }); JsonRpcClient client = new JsonRpcClient(new JsonRpcOptions(server.uri(""), false, Map.of(),
                rpcBudgetIsShorter ? Duration.ofMillis(500) : Duration.ofSeconds(5), 1024))) {
            client.call("warm", Map.of(), budget());
            long started = System.nanoTime();
            JsonRpcException error = expectThrows(JsonRpcException.class,
                    () -> client.call("example.mutate", Map.of(), Deadline.after(
                            rpcBudgetIsShorter ? Duration.ofSeconds(5) : Duration.ofMillis(500))));
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            assertEquals(error.kind(), DEADLINE);
            assertTrue(bodyStarted.await(1, TimeUnit.SECONDS), "Must exercise a stalled streaming body");
            assertTrue(elapsedMs < 2500, "Body wait must respect the shorter budget: " + elapsedMs);
            assertEquals(server.requests.get(), 2, "The stalled mutation must not be retried");
        } finally {
            release.countDown();
        }
    }

    @Test
    public void interruptionCancelsWaitAndPreservesThreadFlag() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        try (LocalServer server = new LocalServer(exchange -> {
            readRequest(exchange);
            entered.countDown();
            await(release);
            exchange.close();
        }); JsonRpcClient client = new JsonRpcClient(JsonRpcOptions.defaults(server.uri("")))) {
            Thread caller = Thread.ofVirtual().start(() -> {
                try {
                    client.call("example.mutate", Map.of(), Deadline.after(Duration.ofSeconds(10)));
                } catch (Throwable error) {
                    failure.set(error);
                    interrupted.set(Thread.currentThread().isInterrupted());
                }
            });
            try {
                assertTrue(entered.await(3, TimeUnit.SECONDS));
                caller.interrupt();
                caller.join(2000);
                assertFalse(caller.isAlive(), "Interrupt must release the caller promptly");
                assertTrue(failure.get() instanceof JsonRpcException);
                assertEquals(((JsonRpcException) failure.get()).kind(), INTERRUPTED);
                assertTrue(interrupted.get());
                assertEquals(server.requests.get(), 1);
            } finally {
                caller.interrupt();
                release.countDown();
                caller.join(2000);
            }
        } finally {
            release.countDown();
        }
    }

    @Test
    public void expiredAndInterruptedCallsSendNothingAndCloseIsIdempotent() throws Exception {
        try (LocalServer server = new LocalServer(exchange -> send(exchange, "{}"));
             JsonRpcClient client = new JsonRpcClient(JsonRpcOptions.defaults(server.uri("")))) {
            Deadline expired = Deadline.after(Duration.ofNanos(1));
            while (!expired.expired()) {
                Thread.onSpinWait();
            }
            assertEquals(expectThrows(JsonRpcException.class,
                    () -> client.call("example.mutate", Map.of(), expired)).kind(), DEADLINE);
            Thread.currentThread().interrupt();
            try {
                assertEquals(expectThrows(JsonRpcException.class,
                        () -> client.call("example.mutate", Map.of(), budget())).kind(), INTERRUPTED);
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
            client.close();
            client.close();
            assertEquals(expectThrows(JsonRpcException.class,
                    () -> client.call("example.mutate", Map.of(), budget())).kind(), TRANSPORT);
            assertEquals(server.requests.get(), 0);
        }
    }

    @Test
    public void concurrentCallsUseDistinctMatchingIds() throws Exception {
        List<Long> ids = Collections.synchronizedList(new ArrayList<>());
        try (LocalServer server = new LocalServer(exchange -> {
            JsonNode request = readRequest(exchange);
            ids.add(request.get("id").longValue());
            send(exchange, success(request, request.get("params").get("value")));
        }); JsonRpcClient client = new JsonRpcClient(JsonRpcOptions.defaults(server.uri("")));
             ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = new ArrayList<java.util.concurrent.Future<JsonNode>>();
            for (int i = 0; i < 16; i++) {
                int value = i;
                tasks.add(workers.submit(() -> client.call("example.read", Map.of("value", value), budget())));
            }
            for (int i = 0; i < tasks.size(); i++) {
                assertEquals(tasks.get(i).get(5, TimeUnit.SECONDS).intValue(), i);
            }
            assertEquals(new HashSet<>(ids).size(), 16);
        }
    }

    private static Deadline budget() {
        return Deadline.after(Duration.ofSeconds(5));
    }

    private static JsonNode readRequest(HttpExchange exchange) throws IOException {
        try (var input = exchange.getRequestBody()) {
            return JSON.readTree(input);
        }
    }

    private static String success(JsonNode request, JsonNode result) throws IOException {
        ObjectNode response = JSON.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", request.get("id"));
        response.set("result", result);
        return JSON.writeValueAsString(response);
    }

    private static void send(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length == 0 ? -1 : bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static void await(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IOException("Test server latch timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Test server interrupted");
        }
    }

    private static void assertSanitized(Throwable error, String... secrets) {
        StringWriter output = new StringWriter();
        error.printStackTrace(new PrintWriter(output));
        for (String secret : secrets) {
            assertFalse(output.toString().contains(secret), "Exception must not contain secrets");
        }
    }

    private static final class LocalServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        private final AtomicInteger requests = new AtomicInteger();

        private LocalServer(HttpHandler handler) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                requests.incrementAndGet();
                handler.handle(exchange);
            });
            server.setExecutor(executor);
            server.start();
        }

        private URI uri(String path) {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
