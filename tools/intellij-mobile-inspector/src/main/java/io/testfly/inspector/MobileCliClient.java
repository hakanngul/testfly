package io.testfly.inspector;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/** JSON-RPC client for a mobilecli server ({@code POST <serverUrl>/rpc}); the Java twin of the VS Code extension's mobilecli.ts. */
public final class MobileCliClient implements AutoCloseable {

    /** A JSON-RPC or transport failure; {@code unreachable} when no server answered. */
    public static final class RpcException extends RuntimeException {
        final boolean unreachable;

        RpcException(String message, boolean unreachable) {
            super(message);
            this.unreachable = unreachable;
        }
    }

    private final String serverUrl;
    private final String mobilecliPath;
    private final boolean autoStart;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final AtomicInteger nextId = new AtomicInteger(1);
    private Process server;

    public MobileCliClient(String serverUrl, String mobilecliPath, boolean autoStart) {
        this.serverUrl = serverUrl.replaceAll("/+$", "");
        this.mobilecliPath = mobilecliPath;
        this.autoStart = autoStart;
    }

    public JsonElement call(String method, JsonObject params, Duration timeout) {
        JsonObject body = new JsonObject();
        body.addProperty("jsonrpc", "2.0");
        body.addProperty("id", nextId.getAndIncrement());
        body.addProperty("method", method);
        body.add("params", params != null ? params : new JsonObject());
        HttpRequest request = HttpRequest.newBuilder(URI.create(serverUrl + "/rpc"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new RpcException("mobilecli server at " + serverUrl + " is not reachable (" + e.getMessage() + ")", true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RpcException("Interrupted while calling mobilecli " + method, false);
        }
        JsonObject reply;
        try {
            reply = JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new RpcException("mobilecli " + method + ": HTTP " + response.statusCode() + " without a JSON-RPC body", false);
        }
        if (reply.has("error") && !reply.get("error").isJsonNull()) {
            JsonObject error = reply.getAsJsonObject("error");
            String data = error.has("data") && error.get("data").isJsonPrimitive() ? ": " + error.get("data").getAsString() : "";
            throw new RpcException("mobilecli " + method + " failed (" + error.get("code") + " " + str(error, "message") + ")" + data, false);
        }
        return reply.has("result") ? reply.get("result") : JsonNull.INSTANCE;
    }

    public JsonElement call(String method, JsonObject params) {
        return call(method, params, Duration.ofSeconds(60));
    }

    /** Checks the server; runs {@code mobilecli server start} once when a local server does not answer. */
    public String ensureServer(Duration startTimeout) {
        try {
            return version();
        } catch (RpcException e) {
            if (!e.unreachable || !autoStart || !isLoopback(serverUrl) || server != null) {
                throw e;
            }
        }
        URI uri = URI.create(serverUrl);
        int port = uri.getPort() > 0 ? uri.getPort() : 12000;
        try {
            server = new ProcessBuilder(mobilecliPath, "server", "start", "--listen", "localhost:" + port)
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException e) {
            throw new RpcException("Could not run '" + mobilecliPath + " server start': " + e.getMessage()
                    + ". Install mobilecli or set its path in Settings > Tools > TestFly Mobile Inspector.", true);
        }
        long deadline = System.nanoTime() + startTimeout.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                return version();
            } catch (RpcException e) {
                if (!e.unreachable) {
                    throw e;
                }
                if (!server.isAlive()) {
                    throw new RpcException("'" + mobilecliPath + " server start' exited with code " + server.exitValue(), true);
                }
                sleep(300);
            }
        }
        throw new RpcException("mobilecli server did not answer at " + serverUrl + " within " + startTimeout.toMillis() + " ms", true);
    }

    public String version() {
        JsonElement info = call("server.info", new JsonObject(), Duration.ofSeconds(3));
        return info.isJsonObject() ? str(info.getAsJsonObject(), "version") : "";
    }

    /** Online devices as the webview expects them: {id, name, platform, type, state, version}. */
    public JsonArray onlineDevices() {
        JsonElement result = call("devices.list", new JsonObject());
        JsonArray list = result.isJsonArray() ? result.getAsJsonArray()
                : result.isJsonObject() && result.getAsJsonObject().has("devices") ? result.getAsJsonObject().getAsJsonArray("devices") : new JsonArray();
        JsonArray online = new JsonArray();
        for (JsonElement d : list) {
            if ("online".equals(str(d.getAsJsonObject(), "state"))) {
                online.add(d);
            }
        }
        return online;
    }

    public JsonObject screenSize(String deviceId) {
        JsonElement info = call("device.info", device(deviceId));
        JsonObject size = info.isJsonObject() && info.getAsJsonObject().has("device")
                && info.getAsJsonObject().getAsJsonObject("device").has("screenSize")
                ? info.getAsJsonObject().getAsJsonObject("device").getAsJsonObject("screenSize") : new JsonObject();
        JsonObject out = new JsonObject();
        out.addProperty("width", size.has("width") ? size.get("width").getAsInt() : 0);
        out.addProperty("height", size.has("height") ? size.get("height").getAsInt() : 0);
        out.addProperty("scale", size.has("scale") ? size.get("scale").getAsDouble() : 1);
        return out;
    }

    public JsonArray dump(String deviceId) {
        JsonElement result = call("device.dump.ui", device(deviceId));
        if (result.isJsonArray()) {
            return result.getAsJsonArray();
        }
        return result.isJsonObject() && result.getAsJsonObject().has("elements") ? result.getAsJsonObject().getAsJsonArray("elements") : new JsonArray();
    }

    /** The tree as mobilecli's text snapshot ({@code mobilecli snapshot}). */
    public String snapshotText(String deviceId) {
        JsonObject p = device(deviceId);
        p.addProperty("format", "text");
        JsonElement result = call("device.dump.ui", p);
        return result.isJsonObject() ? str(result.getAsJsonObject(), "text") : "";
    }

    /** The screenshot as a data URL. */
    public String screenshot(String deviceId) {
        JsonObject p = device(deviceId);
        p.addProperty("format", "png");
        JsonElement result = call("device.screenshot", p);
        return result.isJsonObject() ? str(result.getAsJsonObject(), "data") : "";
    }

    public void tap(String deviceId, double x, double y) {
        call("device.io.tap", point(deviceId, x, y));
    }

    public void longPress(String deviceId, double x, double y) {
        call("device.io.longpress", point(deviceId, x, y));
    }

    public void typeText(String deviceId, String text) {
        JsonObject p = device(deviceId);
        p.addProperty("text", text);
        call("device.io.text", p);
    }

    public void swipe(String deviceId, double x1, double y1, double x2, double y2) {
        JsonObject p = device(deviceId);
        p.addProperty("x1", Math.round(x1));
        p.addProperty("y1", Math.round(y1));
        p.addProperty("x2", Math.round(x2));
        p.addProperty("y2", Math.round(y2));
        call("device.io.swipe", p);
    }

    public void pressButton(String deviceId, String button) {
        JsonObject p = device(deviceId);
        p.addProperty("button", button);
        call("device.io.button", p);
    }

    /** Stops a server this client started; a server that was already running is left alone. */
    @Override
    public void close() {
        if (server != null) {
            server.destroy();
            server = null;
        }
    }

    static boolean isLoopback(String url) {
        try {
            String host = URI.create(url).getHost();
            return host != null && (host.equals("localhost") || host.equals("::1") || host.equals("[::1]") || host.startsWith("127."));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static JsonObject device(String deviceId) {
        JsonObject p = new JsonObject();
        p.addProperty("deviceId", deviceId);
        return p;
    }

    private static JsonObject point(String deviceId, double x, double y) {
        JsonObject p = device(deviceId);
        p.addProperty("x", Math.round(x));
        p.addProperty("y", Math.round(y));
        return p;
    }

    static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
