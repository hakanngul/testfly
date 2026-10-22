package io.testfly.mobile.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.testfly.mobile.DeviceNotFoundException;
import io.testfly.mobile.MobileException;
import io.testfly.mobile.MobileRpcException;
import io.testfly.mobile.MobileServerUnavailableException;
import io.testfly.mobile.MobileTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JSON-RPC 2.0 over HTTP ({@code POST <serverUrl>/rpc}) for mobilecli (ADR 0002 D3, D6).
 *
 * <ul>
 *   <li>Every request is bounded by the timeout the caller passes.</li>
 *   <li>Read-only calls may be retried once after an I/O failure; state-changing calls never are.</li>
 *   <li>A connection the server closes without a JSON-RPC answer (mobilecli does this after 60 s on a
 *       slow device) becomes a {@link MobileRpcException}, not a hang or a parse error.</li>
 * </ul>
 */
public final class JsonRpcClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonRpcClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** mobilecli reports "device not found" as -32000; -32010 is the documented code. */
    static final int DEVICE_NOT_FOUND = -32010;
    static final int SERVER_ERROR = -32000;
    static final int TIMEOUT = -32050;

    private final URI endpoint;
    private final Map<String, String> headers;
    private final HttpClient http;
    private final AtomicLong ids = new AtomicLong();

    public JsonRpcClient(URI endpoint, Map<String, String> headers) {
        this.endpoint = endpoint;
        this.headers = headers == null ? Map.of() : Map.copyOf(headers);
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public URI endpoint() {
        return endpoint;
    }

    public static ObjectNode params() {
        return MAPPER.createObjectNode();
    }

    /**
     * Calls {@code method} and returns its {@code result} (never {@code null}; a missing result is
     * {@code NullNode}).
     *
     * @param readOnly whether the call may be retried once after an I/O failure
     */
    public JsonNode call(String method, ObjectNode params, Duration timeout, boolean readOnly) {
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            return attempt(method, params, timeout, readOnly);
        } catch (MobileRpcException e) {
            Duration left = Duration.ofNanos(deadline - System.nanoTime());
            if (!readOnly || e.getCode() != null || !(e.getCause() instanceof IOException) || left.toMillis() < 50) {
                throw e;
            }
            LOGGER.debug("Retrying read-only mobilecli call {} after I/O failure: {}", method, e.getMessage());
            return attempt(method, params, left, true);
        }
    }

    private JsonNode attempt(String method, ObjectNode params, Duration timeout, boolean readOnly) {
        long id = ids.incrementAndGet();
        ObjectNode body = MAPPER.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.put("id", id);
        body.put("method", method);
        if (params != null) {
            body.set("params", params);
        }
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout.isNegative() || timeout.isZero() ? Duration.ofMillis(1) : timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        headers.forEach(request::header);

        HttpResponse<String> response;
        try {
            response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (HttpConnectTimeoutException e) {
            // Slow to connect is a timeout; a refused connection (below) means no server.
            throw new MobileTimeoutException("Could not connect to mobilecli at " + endpoint + " within " + timeout.toMillis()
                    + " ms while calling " + method + ". Is the server overloaded or behind a slow tunnel?", e);
        } catch (HttpTimeoutException e) {
            throw new MobileTimeoutException("mobilecli call " + method + " did not answer within " + timeout.toMillis() + " ms."
                    + (readOnly ? "" : " The call may still complete on the device."), e);
        } catch (ConnectException | UnresolvedAddressException e) {
            throw unavailable(e);
        } catch (IOException e) {
            if (e.getCause() instanceof ConnectException || e.getCause() instanceof UnresolvedAddressException) {
                throw unavailable(e);
            }
            throw new MobileRpcException("mobilecli closed the connection without answering " + method + " ("
                    + e.getMessage() + "). mobilecli gives up after 60 s when the device is too slow to answer.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MobileRpcException("Interrupted while calling mobilecli " + method, e);
        }

        String text = response.body();
        if (text == null || text.isBlank()) {
            throw new MobileRpcException("mobilecli returned an empty response to " + method + " (HTTP " + response.statusCode() + ")",
                    null, null);
        }
        JsonNode reply;
        try {
            reply = MAPPER.readTree(text);
        } catch (IOException e) {
            throw new MobileRpcException("mobilecli returned invalid JSON for " + method + " (HTTP " + response.statusCode() + "): "
                    + abbreviate(text), e);
        }
        JsonNode error = reply.get("error");
        if (error != null && !error.isNull()) {
            throw toException(method, error);
        }
        JsonNode result = reply.get("result");
        if (result == null) {
            if (response.statusCode() >= 400) {
                throw new MobileRpcException("mobilecli answered " + method + " with HTTP " + response.statusCode() + ": "
                        + abbreviate(text), null, null);
            }
            return MAPPER.nullNode();
        }
        return result;
    }

    static MobileException toException(String method, JsonNode error) {
        int code = error.path("code").asInt(0);
        String message = error.path("message").asText("");
        JsonNode dataNode = error.get("data");
        String data = dataNode == null || dataNode.isNull() ? null : dataNode.isTextual() ? dataNode.asText() : dataNode.toString();
        String detail = data != null && !data.isBlank() ? data : message;
        if (code == DEVICE_NOT_FOUND || (code == SERVER_ERROR && detail.toLowerCase().contains("device not found"))) {
            return new DeviceNotFoundException(detail + ". Run 'mobilecli devices' to list the available devices.");
        }
        if (code == TIMEOUT) {
            return new MobileTimeoutException("mobilecli timed out in " + method + ": " + detail);
        }
        return new MobileRpcException("mobilecli " + method + " failed (" + code + " " + message + "): " + detail, code, data);
    }

    private MobileServerUnavailableException unavailable(Exception e) {
        return new MobileServerUnavailableException("Cannot reach the mobilecli server at " + endpoint
                + ". Start it with 'mobilecli server start' (or set mobile.serverUrl).", e);
    }

    private static String abbreviate(String s) {
        return s.length() > 300 ? s.substring(0, 297) + "..." : s;
    }
}
