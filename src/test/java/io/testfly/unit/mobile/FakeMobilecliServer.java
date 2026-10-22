package io.testfly.unit.mobile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * In-process stand-in for {@code mobilecli server start}: JSON-RPC 2.0 on {@code POST /rpc}.
 * Handlers return a {@code result}, or one of {@link #error}, {@link #CLOSE} (close without answering,
 * as mobilecli does after its 60 s daemon timeout) or {@link #delay}.
 */
final class FakeMobilecliServer implements AutoCloseable {

    static final ObjectMapper MAPPER = new ObjectMapper();
    static final Object CLOSE = new Object();

    record Error(int code, String message, String data) {
    }

    record Delay(long millis, Object then) {
    }

    record Call(String method, JsonNode params) {
    }

    private final HttpServer server;
    private final Map<String, Function<JsonNode, Object>> handlers = new ConcurrentHashMap<>();
    private final List<Call> calls = new ArrayList<>();
    private final List<Map<String, List<String>>> requestHeaders = new ArrayList<>();

    FakeMobilecliServer() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/rpc", this::handle);
        server.start();
        // Recorded from mobilecli 1.0.20 (src/test/resources/mobile/mobilecli-1.0.20).
        on("server.info", p -> fixture("server.info").get("result"));
        on("devices.list", p -> fixture("devices.list").get("result"));
    }

    static Error error(int code, String message, String data) {
        return new Error(code, message, data);
    }

    static Delay delay(long millis, Object then) {
        return new Delay(millis, then);
    }

    static JsonNode fixture(String name) {
        String path = "mobile/mobilecli-1.0.20/" + name + ".json";
        try (InputStream in = FakeMobilecliServer.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing fixture " + path);
            }
            return MAPPER.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    FakeMobilecliServer on(String method, Function<JsonNode, Object> handler) {
        handlers.put(method, handler);
        return this;
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    synchronized List<Call> calls() {
        return List.copyOf(calls);
    }

    synchronized List<String> methods() {
        return calls.stream().map(Call::method).toList();
    }

    synchronized Map<String, List<String>> lastHeaders() {
        return requestHeaders.isEmpty() ? Map.of() : requestHeaders.get(requestHeaders.size() - 1);
    }

    private void handle(HttpExchange exchange) throws IOException {
        JsonNode request;
        try (InputStream in = exchange.getRequestBody()) {
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            try {
                request = MAPPER.readTree(body);
            } catch (IOException e) {
                reply(exchange, errorBody(null, -32700, "Parse error", "expecting jsonrpc payload"));
                return;
            }
        }
        String method = request.path("method").asText();
        JsonNode params = request.path("params");
        JsonNode id = request.get("id");
        synchronized (this) {
            calls.add(new Call(method, params));
            requestHeaders.add(Map.copyOf(exchange.getRequestHeaders()));
        }
        Function<JsonNode, Object> handler = handlers.get(method);
        Object out = handler == null ? error(-32601, "Method not found", "Method '" + method + "' not found") : handler.apply(params);
        while (out instanceof Delay d) {
            try {
                Thread.sleep(d.millis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            out = d.then();
        }
        if (out == CLOSE) {
            exchange.close();
            return;
        }
        if (out instanceof Error e) {
            reply(exchange, errorBody(id, e.code(), e.message(), e.data()));
            return;
        }
        ObjectNode body = MAPPER.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.set("result", out == null ? MAPPER.nullNode() : MAPPER.valueToTree(out));
        body.set("id", id);
        reply(exchange, body);
    }

    private static ObjectNode errorBody(JsonNode id, int code, String message, String data) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("jsonrpc", "2.0");
        ObjectNode error = body.putObject("error");
        error.put("code", code);
        error.put("message", message);
        if (data != null) {
            error.put("data", data);
        }
        body.set("id", id);
        return body;
    }

    private static void reply(HttpExchange exchange, JsonNode body) throws IOException {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
