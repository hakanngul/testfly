package io.testfly.mobile.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.testfly.mobile.MobileBounds;
import io.testfly.mobile.MobileDeviceInfo;
import io.testfly.mobile.MobileNode;
import io.testfly.mobile.MobileOrientation;
import io.testfly.mobile.MobilePlatform;
import io.testfly.mobile.MobileRpcException;
import io.testfly.mobile.MobileScreenSize;
import io.testfly.mobile.spi.MobileDriver;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link MobileDriver} for an unmodified, user-installed mobilecli server (ADR 0002 D1, D3).
 * Method names and parameters follow mobilecli 1.0.20 as used by Mobilewright's mobilecli driver.
 * The selected {@code deviceId} is added to device-scoped calls ({@code device.*}) only, so
 * {@code server.info} and {@code devices.list} work before a device is chosen.
 */
public final class MobileCliDriver implements MobileDriver {

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    private final JsonRpcClient rpc;
    private MobileDeviceInfo device;

    public MobileCliDriver(JsonRpcClient rpc) {
        this.rpc = rpc;
    }

    // ── server and discovery ────────────────────────────────────────────

    @Override
    public String serverVersion(Duration timeout) {
        return rpc.call("server.info", null, timeout, true).path("version").asText("");
    }

    @Override
    public List<MobileDeviceInfo> devices(Duration timeout) {
        JsonNode result = rpc.call("devices.list", JsonRpcClient.params(), timeout, true);
        // 1.0.20 answers {devices: [...]}; the published spec shows a bare array.
        JsonNode list = result.isArray() ? result : result.path("devices");
        List<MobileDeviceInfo> out = new ArrayList<>();
        for (JsonNode d : list) {
            out.add(toDeviceInfo(d));
        }
        return out;
    }

    @Override
    public void useDevice(MobileDeviceInfo device) {
        this.device = device;
    }

    // ── device ──────────────────────────────────────────────────────────

    @Override
    public MobileScreenSize screenSize(Duration timeout) {
        JsonNode size = deviceCall("device.info", null, timeout, true).path("device").path("screenSize");
        return new MobileScreenSize(size.path("width").asInt(0), size.path("height").asInt(0), size.path("scale").asDouble(1));
    }

    @Override
    public List<MobileNode> viewTree(Duration timeout) {
        JsonNode result;
        try {
            result = deviceCall("device.dump.ui", null, timeout, true);
        } catch (MobileRpcException e) {
            // The UI dump needs mobilecli's on-device agent. TestFly may talk to a remote server, so it
            // cannot run 'mobilecli agent status' itself; it names the likely fix instead.
            throw new MobileRpcException(e.getMessage() + " If this persists, check that mobilecli's device agent is "
                    + "installed: mobilecli agent status --device " + requireDevice().id()
                    + " (install it with: mobilecli agent install --device " + requireDevice().id() + ").",
                    e.getCode(), e.getData());
        }
        JsonNode elements = result.isArray() ? result : result.path("elements");
        List<MobileNode> out = new ArrayList<>();
        for (JsonNode el : elements) {
            out.add(toNode(el));
        }
        return out;
    }

    @Override
    public byte[] screenshot(Duration timeout) {
        return screenshot(null, timeout);
    }

    @Override
    public byte[] screenshot(MobileBounds clip, Duration timeout) {
        ObjectNode p = JsonRpcClient.params();
        p.put("format", "png");
        if (clip != null) {
            ObjectNode c = p.putObject("clip");
            c.put("x", (int) Math.round(clip.x()));
            c.put("y", (int) Math.round(clip.y()));
            c.put("width", (int) Math.round(clip.width()));
            c.put("height", (int) Math.round(clip.height()));
        }
        String data = deviceCall("device.screenshot", p, timeout, true).path("data").asText("");
        int comma = data.indexOf(',');
        if (comma >= 0) {
            data = data.substring(comma + 1); // data:image/png;base64,...
        }
        if (data.isEmpty()) {
            throw new MobileRpcException("mobilecli device.screenshot returned no image data", null, null);
        }
        return Base64.getMimeDecoder().decode(data);
    }

    @Override
    public void tap(int x, int y, Duration timeout) {
        ObjectNode p = JsonRpcClient.params();
        p.put("x", x);
        p.put("y", y);
        deviceCall("device.io.tap", p, timeout, false);
    }

    @Override
    public void longPress(int x, int y, Duration pressDuration, Duration timeout) {
        ObjectNode p = JsonRpcClient.params();
        p.put("x", x);
        p.put("y", y);
        if (pressDuration != null) {
            p.put("duration", pressDuration.toMillis());
        }
        deviceCall("device.io.longpress", p, timeout, false);
    }

    @Override
    public void typeText(String text, Duration timeout) {
        ObjectNode p = JsonRpcClient.params();
        p.put("text", text);
        deviceCall("device.io.text", p, timeout, false);
    }

    @Override
    public void clearText(Duration timeout) {
        ObjectNode p = JsonRpcClient.params();
        // iOS: one HID "clear" event; Android: select all + delete.
        if (requireDevice().platform() != null && MobilePlatform.fromWire(requireDevice().platform()) == MobilePlatform.IOS) {
            p.putArray("keys").add("clear");
        } else {
            p.putArray("keys").add("ctrl+a").add("backspace");
        }
        deviceCall("device.io.keys", p, timeout, false);
    }

    @Override
    public void swipe(int x1, int y1, int x2, int y2, Duration timeout) {
        ObjectNode p = JsonRpcClient.params();
        p.put("x1", x1);
        p.put("y1", y1);
        p.put("x2", x2);
        p.put("y2", y2);
        deviceCall("device.io.swipe", p, timeout, false);
    }

    @Override
    public void pressButton(String button, Duration timeout) {
        ObjectNode p = JsonRpcClient.params();
        p.put("button", button);
        deviceCall("device.io.button", p, timeout, false);
    }

    @Override
    public void launchApp(String bundleId, Duration timeout) {
        launchApp(bundleId, null, timeout);
    }

    @Override
    public void launchApp(String bundleId, String activity, Duration timeout) {
        launchApp(bundleId, activity, null, timeout);
    }

    @Override
    public void launchApp(String bundleId, String activity, List<String> locales, Duration timeout) {
        if (MobilePlatform.fromWire(requireDevice().platform()) == MobilePlatform.IOS) {
            // iOS: start DeviceKit before the launch; its startup otherwise minimizes the new app (as Mobilewright does).
            try {
                deviceCall("device.apps.foreground", null, timeout, true);
            } catch (MobileRpcException e) {
                // No foreground app yet is fine; DeviceKit is up.
            }
        }
        ObjectNode p = bundle(bundleId);
        if (activity != null && !activity.isBlank()
                && MobilePlatform.fromWire(requireDevice().platform()) == MobilePlatform.ANDROID) {
            // Several LAUNCHER activities (e.g. LeakCanary in a debug build) make Android show a chooser instead.
            p.put("activity", activity);
        }
        if (locales != null && !locales.isEmpty()) {
            locales.forEach(p.putArray("locales")::add);
        }
        deviceCall("device.apps.launch", p, timeout, false);
    }

    @Override
    public void terminateApp(String bundleId, Duration timeout) {
        deviceCall("device.apps.terminate", bundle(bundleId), timeout, false);
    }

    @Override
    public void clearAppData(String bundleId, Duration timeout) {
        deviceCall("device.apps.clear", bundle(bundleId), timeout, false);
    }

    @Override
    public void installApp(String path, Duration timeout) {
        ObjectNode p = JsonRpcClient.params();
        p.put("path", path);
        deviceCall("device.apps.install", p, timeout, false);
    }

    @Override
    public void openUrl(String url, Duration timeout) {
        ObjectNode p = JsonRpcClient.params();
        p.put("url", url);
        deviceCall("device.url", p, timeout, false);
    }

    @Override
    public MobileOrientation orientation(Duration timeout) {
        String value = deviceCall("device.io.orientation.get", null, timeout, true).path("orientation").asText("");
        return "landscape".equalsIgnoreCase(value) ? MobileOrientation.LANDSCAPE : MobileOrientation.PORTRAIT;
    }

    @Override
    public void setOrientation(MobileOrientation orientation, Duration timeout) {
        ObjectNode p = JsonRpcClient.params();
        p.put("orientation", orientation.name().toLowerCase(java.util.Locale.ROOT));
        deviceCall("device.io.orientation.set", p, timeout, false);
    }

    @Override
    public void setLocation(double latitude, double longitude, Duration timeout) {
        ObjectNode p = JsonRpcClient.params();
        p.put("latitude", latitude);
        p.put("longitude", longitude);
        deviceCall("device.location.set", p, timeout, false);
    }

    @Override
    public void clearLocation(Duration timeout) {
        deviceCall("device.location.clear", null, timeout, false);
    }

    @Override
    public void startRecording(Path output, Duration timeout) {
        ObjectNode p = JsonRpcClient.params();
        p.put("output", output.toAbsolutePath().toString());
        deviceCall("device.screenrecord", p, timeout, false);
    }

    @Override
    public void stopRecording(Duration timeout) {
        deviceCall("device.screenrecord.stop", null, timeout, false);
    }

    @Override
    public List<MobileNode> viewTree(boolean full, Duration timeout) {
        if (!full) {
            return viewTree(timeout);
        }
        ObjectNode p = JsonRpcClient.params();
        p.put("full", true);
        JsonNode result = deviceCall("device.dump.ui", p, timeout, true);
        List<MobileNode> out = new ArrayList<>();
        for (JsonNode el : result.isArray() ? result : result.path("elements")) {
            out.add(toNode(el));
        }
        return out;
    }

    @Override
    public void pinch(int x, int y, boolean out, int distance, Duration duration, Duration timeout) {
        ObjectNode p = JsonRpcClient.params();
        p.put("x", x);
        p.put("y", y);
        p.put("direction", out ? "out" : "in");
        p.put("distance", distance);
        p.put("duration", duration == null ? 0 : duration.toMillis());
        deviceCall("device.io.pinch", p, timeout, false);
    }

    @Override
    public void drag(int x1, int y1, int x2, int y2, Duration duration, Duration timeout) {
        ObjectNode p = JsonRpcClient.params();
        com.fasterxml.jackson.databind.node.ArrayNode actions = p.putArray("actions");
        actions.addObject().put("type", "pointerMove").put("x", x1).put("y", y1).put("button", 0);
        actions.addObject().put("type", "pointerDown").put("button", 0);
        actions.addObject().put("type", "pause").put("duration", 200).put("button", 0); // hold, so lists start a drag
        actions.addObject().put("type", "pointerMove").put("x", x2).put("y", y2)
                .put("duration", duration == null ? 500 : duration.toMillis()).put("button", 0);
        actions.addObject().put("type", "pointerUp").put("button", 0);
        deviceCall("device.io.gesture", p, timeout, false);
    }

    @Override
    public String clipboard(Duration timeout) {
        return deviceCall("device.clipboard.get", null, timeout, true).path("text").asText("");
    }

    @Override
    public void setClipboard(String text, Duration timeout) {
        ObjectNode p = JsonRpcClient.params();
        p.put("text", text);
        deviceCall("device.clipboard.set", p, timeout, false);
    }

    @Override
    public List<io.testfly.mobile.MobileCrashReport> crashReports(Duration timeout) {
        List<io.testfly.mobile.MobileCrashReport> out = new ArrayList<>();
        for (JsonNode c : deviceCall("device.crashes.list", null, timeout, true)) {
            out.add(new io.testfly.mobile.MobileCrashReport(text(c, "id"), text(c, "processName"), text(c, "timestamp")));
        }
        return out;
    }

    @Override
    public String crashReport(String id, Duration timeout) {
        ObjectNode p = JsonRpcClient.params();
        p.put("id", id);
        return deviceCall("device.crashes.get", p, timeout, true).path("content").asText("");
    }

    @Override
    public void pushFile(byte[] content, String remotePath, Duration timeout) {
        ObjectNode p = JsonRpcClient.params();
        p.put("remotePath", remotePath);
        p.put("content", Base64.getEncoder().encodeToString(content));
        deviceCall("device.fs.push", p, timeout, false);
    }

    @Override
    public byte[] pullFile(String remotePath, Duration timeout) {
        ObjectNode p = JsonRpcClient.params();
        p.put("remotePath", remotePath);
        return Base64.getDecoder().decode(deviceCall("device.fs.pull", p, timeout, true).path("content").asText(""));
    }

    @Override
    public Object webView(String operation, String id, Map<String, Object> params, Duration timeout) {
        ObjectNode p = JsonRpcClient.params();
        if (id != null) {
            p.put("id", id);
        }
        if (params != null) {
            p.setAll((ObjectNode) JSON.valueToTree(params));
        }
        boolean readOnly = !Set.of("goto", "reload", "goBack", "goForward", "evaluate").contains(operation);
        JsonNode result = deviceCall("device.webview." + operation, p, timeout, readOnly);
        return result == null || result.isNull() || result.isMissingNode() ? null : JSON.convertValue(result, Object.class);
    }

    @Override
    public void applySettings(String animations, String appearance, Duration timeout) {
        ObjectNode p = JsonRpcClient.params();
        if (animations != null) {
            p.put("animations", animations);
        }
        if (appearance != null) {
            p.put("appearance", appearance);
        }
        deviceCall("device.settings.apply", p, timeout, false);
    }

    @Override
    public List<String> installedApps(Duration timeout) {
        JsonNode result = deviceCall("device.apps.list", null, timeout, true);
        List<String> out = new ArrayList<>();
        for (JsonNode app : result.isArray() ? result : result.path("apps")) {
            String id = text(app, "packageName");
            out.add(id != null ? id : text(app, "bundleId"));
        }
        return out;
    }

    @Override
    public String foregroundApp(Duration timeout) {
        JsonNode app = deviceCall("device.apps.foreground", null, timeout, true);
        String id = text(app, "packageName");
        return id != null ? id : text(app, "bundleId");
    }

    @Override
    public void setFoldState(String state, Duration timeout) {
        ObjectNode p = JsonRpcClient.params();
        p.put("state", state);
        deviceCall("device.fold.set", p, timeout, false);
    }

    @Override
    public void uninstallApp(String bundleId, Duration timeout) {
        deviceCall("device.apps.uninstall", bundle(bundleId), timeout, false);
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private JsonNode deviceCall(String method, ObjectNode params, Duration timeout, boolean readOnly) {
        ObjectNode p = params != null ? params : JsonRpcClient.params();
        p.put("deviceId", requireDevice().id());
        return rpc.call(method, p, timeout, readOnly);
    }

    private MobileDeviceInfo requireDevice() {
        if (device == null) {
            throw new IllegalStateException("No device selected: call useDevice(...) first");
        }
        return device;
    }

    private static ObjectNode bundle(String bundleId) {
        ObjectNode p = JsonRpcClient.params();
        p.put("bundleId", bundleId);
        return p;
    }

    static MobileDeviceInfo toDeviceInfo(JsonNode d) {
        return new MobileDeviceInfo(text(d, "id"), text(d, "name"), text(d, "platform"), text(d, "type"), text(d, "state"),
                text(d, "version"), text(d, "model"));
    }

    static MobileNode toNode(JsonNode el) {
        JsonNode rect = el.path("rect");
        MobileBounds bounds = rect.isObject()
                ? new MobileBounds(rect.path("x").asDouble(0), rect.path("y").asDouble(0),
                        rect.path("width").asDouble(0), rect.path("height").asDouble(0))
                : MobileBounds.EMPTY;
        List<MobileNode> children = new ArrayList<>();
        for (JsonNode child : el.path("children")) {
            children.add(toNode(child));
        }
        Map<String, Object> raw = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = el.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> f = fields.next();
            if (!f.getKey().equals("children") && f.getValue().isValueNode() && !f.getValue().isNull()) {
                raw.put(f.getKey(), f.getValue().isTextual() ? f.getValue().asText()
                        : f.getValue().isBoolean() ? f.getValue().asBoolean() : f.getValue().numberValue());
            }
        }
        return new MobileNode(
                text(el, "type"),
                text(el, "text"),
                text(el, "label"),
                text(el, "name"),
                text(el, "value"),
                text(el, "identifier"),
                text(el, "placeholder"),
                bounds,
                bool(el, "visible", !bounds.isEmpty()),
                bool(el, "enabled", true),
                bool(el, "selected", false),
                bool(el, "checked", false),
                bool(el, "focused", false),
                children,
                Collections.unmodifiableMap(raw));
    }

    /** Text value, with empty strings treated as absent (as Mobilewright does). */
    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || v.isContainerNode()) {
            return null;
        }
        String s = v.asText();
        return s.isEmpty() ? null : s;
    }

    /** Booleans may arrive as JSON booleans or as {@code "true"}/{@code "false"} strings. */
    private static boolean bool(JsonNode node, String field, boolean fallback) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return fallback;
        }
        if (v.isBoolean()) {
            return v.asBoolean();
        }
        if (v.isTextual()) {
            String s = v.asText().trim();
            if (s.equalsIgnoreCase("true")) return true;
            if (s.equalsIgnoreCase("false")) return false;
        }
        return fallback;
    }
}
