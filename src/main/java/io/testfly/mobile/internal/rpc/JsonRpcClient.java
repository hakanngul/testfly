package io.testfly.mobile.internal.rpc;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static io.testfly.mobile.internal.rpc.JsonRpcException.Kind.*;

/**
 * Bounded JSON-RPC 2.0 over HTTP. Thread-safe; performs no automatic retries, including reads.
 * Cancellation bounds the client wait but cannot guarantee cancellation at the remote device.
 */
public final class JsonRpcClient implements AutoCloseable {
    private final JsonRpcOptions options;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final AtomicLong ids = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();

    public JsonRpcClient(JsonRpcOptions options) {
        this.options = Objects.requireNonNull(options, "options");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(options.rpcTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .proxy(new DirectProxySelector())
                .build();
    }

    public JsonNode call(String method, Map<String, ?> params, Deadline deadline) {
        Objects.requireNonNull(deadline, "deadline");
        Objects.requireNonNull(params, "params");
        if (method == null || method.length() > 256 || !method.matches("[A-Za-z_][A-Za-z0-9_.:-]*")) {
            throw new IllegalArgumentException("Invalid JSON-RPC method");
        }
        checkReady(deadline);
        long id = ids.incrementAndGet();
        if (id <= 0) {
            throw new JsonRpcException(PROTOCOL, "JSON-RPC request ID space exhausted");
        }
        byte[] body;
        try {
            ObjectNode request = mapper.createObjectNode();
            request.put("jsonrpc", "2.0");
            request.put("id", id);
            request.put("method", method);
            request.set("params", mapper.valueToTree(params));
            body = mapper.writeValueAsBytes(request);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new JsonRpcException(PROTOCOL, "Could not encode JSON-RPC request");
        }
        checkReady(deadline);
        Duration timeout = Duration.ofNanos(Math.min(options.rpcTimeout().toNanos(), deadline.remainingNanos()));
        if (timeout.isZero()) {
            throw timedOut();
        }
        Deadline requestDeadline = Deadline.after(timeout);
        HttpRequest request;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(options.rpcEndpoint())
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body));
            options.headers().forEach(builder::header);
            request = builder.build();
        } catch (IllegalArgumentException e) {
            throw new JsonRpcException(TRANSPORT, "Could not construct JSON-RPC HTTP request");
        }
        CompletableFuture<HttpResponse<byte[]>> pending;
        try {
            pending = httpClient.sendAsync(request, info -> new LimitedBodySubscriber(options.maxResponseBytes()));
        } catch (RuntimeException e) {
            throw new JsonRpcException(TRANSPORT, "Could not start JSON-RPC HTTP request");
        }
        HttpResponse<byte[]> response;
        try {
            long remaining = Math.min(deadline.remainingNanos(), requestDeadline.remainingNanos());
            if (remaining == 0) {
                pending.cancel(true);
                throw timedOut();
            }
            // HttpRequest.timeout alone does not reliably bound streaming response bodies.
            response = pending.get(remaining, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw interrupted();
        } catch (TimeoutException e) {
            pending.cancel(true);
            throw timedOut();
        } catch (ExecutionException e) {
            if (hasCause(e, HttpTimeoutException.class)) {
                throw timedOut();
            }
            if (hasCause(e, ResponseTooLargeException.class)) {
                throw new JsonRpcException(PROTOCOL, "JSON-RPC response exceeded the configured size limit");
            }
            // Do not retain raw HTTP/parser causes: their messages can contain credentials or bodies.
            throw new JsonRpcException(TRANSPORT, "JSON-RPC HTTP exchange failed");
        } catch (CancellationException e) {
            throw new JsonRpcException(TRANSPORT, "JSON-RPC HTTP exchange was cancelled");
        }
        checkReady(deadline);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new JsonRpcException(TRANSPORT, "JSON-RPC HTTP status " + response.statusCode());
        }
        JsonNode result = decode(response.body(), id);
        checkReady(deadline);
        return result;
    }

    /** Non-blocking shutdown; an in-flight mutation may still complete remotely. */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            httpClient.shutdownNow();
        }
    }

    private void checkReady(Deadline deadline) {
        if (Thread.currentThread().isInterrupted()) {
            throw interrupted();
        }
        if (deadline.expired()) {
            throw timedOut();
        }
        if (closed.get()) {
            throw new JsonRpcException(TRANSPORT, "JSON-RPC client is closed");
        }
    }

    private JsonNode decode(byte[] body, long id) {
        JsonNode envelope;
        try {
            envelope = mapper.readTree(body);
        } catch (IOException | RuntimeException e) {
            throw new JsonRpcException(PROTOCOL, "Malformed JSON-RPC response");
        }
        if (envelope == null || !envelope.isObject()
                || !envelope.path("jsonrpc").isTextual() || !envelope.path("jsonrpc").textValue().equals("2.0")
                || !envelope.path("id").isIntegralNumber() || !envelope.path("id").canConvertToLong()
                || envelope.path("id").longValue() != id
                || envelope.has("result") == envelope.has("error")) {
            throw new JsonRpcException(PROTOCOL, "Invalid or mismatched JSON-RPC response envelope");
        }
        if (envelope.has("error")) {
            JsonNode error = envelope.get("error");
            if (!error.isObject() || !error.path("code").isIntegralNumber()
                    || !error.path("code").canConvertToInt() || !error.path("message").isTextual()) {
                throw new JsonRpcException(PROTOCOL, "Invalid JSON-RPC error envelope");
            }
            int code = error.get("code").intValue();
            throw new JsonRpcException(RPC, "JSON-RPC server error (code " + code + ")", code);
        }
        return envelope.get("result");
    }

    private static JsonRpcException timedOut() {
        return new JsonRpcException(DEADLINE, "JSON-RPC time budget exhausted; a state-changing operation may still complete remotely");
    }

    private static JsonRpcException interrupted() {
        return new JsonRpcException(INTERRUPTED, "JSON-RPC request interrupted; a state-changing operation may still complete remotely");
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }

    private static final class DirectProxySelector extends ProxySelector {
        @Override
        public List<Proxy> select(URI uri) {
            return List.of(Proxy.NO_PROXY);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress address, IOException error) {
            // Explicit direct connections do not fall back to a process-wide proxy.
        }
    }

    private static final class ResponseTooLargeException extends IOException { }

    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;
        private final ByteArrayOutputStream bytes;
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private Flow.Subscription subscription;

        private LimitedBodySubscriber(int limit) {
            this.limit = limit;
            this.bytes = new ByteArrayOutputStream(Math.min(limit, 8192));
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription incoming) {
            if (subscription != null) {
                incoming.cancel();
                return;
            }
            subscription = incoming;
            subscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (body.isDone()) {
                return;
            }
            for (ByteBuffer buffer : buffers) {
                int length = buffer.remaining();
                if (length > limit - bytes.size()) {
                    body.completeExceptionally(new ResponseTooLargeException());
                    subscription.cancel();
                    return;
                }
                byte[] chunk = new byte[length];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable error) {
            body.completeExceptionally(error);
        }

        @Override
        public void onComplete() {
            body.complete(bytes.toByteArray());
        }
    }
}
