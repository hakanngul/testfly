package io.testfly.unit.mobile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Base64;

import static io.testfly.unit.mobile.FakeMobilecliServer.MAPPER;

/**
 * A tiny Android app behind the fake server: a login screen (username, password, "Sign in" button
 * enabled once a username is typed) and a home screen with a greeting and a list. Taps resolve by
 * coordinates; {@code device.io.text}/{@code keys} edit the focused field, like a real device.
 */
final class FakeApp {

    static final String BUNDLE = "dev.testfly.demo";

    private final String deviceId;
    /** More online Android devices served by the same app (device pool tests). */
    private final java.util.List<String> extraIds;
    /** Platform the fake device reports ({@code android} or {@code ios}); the UI is the same. */
    String platform = "android";
    String screen = "login";
    String foreground = "com.android.launcher";
    String focused;
    String username = "";
    String password = "";
    String orientation = "portrait";
    /** "lat,lon" while the location is overridden, else null. */
    String location;
    /** The file a running screen recording writes to, else null. */
    String recordingTo;

    /** The home list was swiped up once, revealing its "Help" row (scrollIntoView tests). */
    boolean scrolled;

    /** Polls left before the Sign in button stops moving (stability check). */
    int buttonMoves;

    FakeApp(String deviceId, String... extraOnlineIds) {
        this.deviceId = deviceId;
        this.extraIds = java.util.List.of(extraOnlineIds);
    }

    /** Registers the app's handlers on {@code server}, with a device list holding {@code deviceId}. */
    FakeApp install(FakeMobilecliServer server) {
        server.on("devices.list", p -> devices());
        server.on("device.info", p -> {
            requireDevice(p);
            ObjectNode r = MAPPER.createObjectNode();
            ObjectNode d = r.putObject("device");
            d.put("id", deviceId).put("name", deviceId).put("platform", platform).put("type", "emulator").put("state", "online");
            d.putObject("screenSize").put("width", 1080).put("height", 2400).put("scale", 1);
            return r;
        });
        server.on("device.dump.ui", p -> {
            requireDevice(p);
            return tree();
        });
        server.on("device.io.tap", p -> {
            tap(p.path("x").asInt(), p.path("y").asInt());
            return null;
        });
        server.on("device.io.swipe", p -> {
            if (screen.equals("home") && p.path("y1").asInt() > p.path("y2").asInt()) scrolled = true;
            return null;
        });
        server.on("device.io.text", p -> {
            if ("username".equals(focused)) username += p.path("text").asText();
            if ("password".equals(focused)) password += p.path("text").asText();
            return null;
        });
        server.on("device.io.keys", p -> {
            if ("username".equals(focused)) username = "";
            if ("password".equals(focused)) password = "";
            return null;
        });
        server.on("device.apps.terminate", p -> {
            if (p.path("bundleId").asText().equals(foreground)) foreground = "com.android.launcher";
            return null;
        });
        server.on("device.apps.launch", p -> {
            foreground = p.path("bundleId").asText();
            screen = "login";
            return null;
        });
        server.on("device.io.orientation.get", p -> MAPPER.createObjectNode().put("orientation", orientation));
        server.on("device.io.orientation.set", p -> {
            orientation = p.path("orientation").asText();
            return null;
        });
        server.on("device.location.set", p -> {
            location = p.path("latitude").asDouble() + "," + p.path("longitude").asDouble();
            return null;
        });
        server.on("device.location.clear", p -> {
            location = null;
            return null;
        });
        server.on("device.screenrecord", p -> {
            recordingTo = p.path("output").asText();
            return null;
        });
        server.on("device.screenrecord.stop", p -> {
            // Like mobilecli: the MP4 is written when the recording stops.
            try {
                java.nio.file.Files.write(java.nio.file.Path.of(recordingTo), new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p'});
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            recordingTo = null;
            return MAPPER.createObjectNode().put("status", "ok");
        });
        server.on("device.screenshot", p -> {
            ObjectNode r = MAPPER.createObjectNode();
            r.put("data", "data:image/png;base64," + Base64.getEncoder().encodeToString(new byte[] {(byte) 0x89, 'P', 'N', 'G'}));
            return r;
        });
        return this;
    }

    private JsonNode devices() {
        ObjectNode r = MAPPER.createObjectNode();
        ArrayNode list = r.putArray("devices");
        list.addObject().put("id", deviceId).put("name", "Pixel 8 (" + deviceId + ")").put("platform", platform)
                .put("type", "emulator").put("version", "14").put("state", "online");
        for (String extra : extraIds) {
            list.addObject().put("id", extra).put("name", "Pixel 8 (" + extra + ")").put("platform", "android")
                    .put("type", "emulator").put("version", "14").put("state", "online");
        }
        list.addObject().put("id", "offline-1").put("name", "Pixel 6").put("platform", "android")
                .put("type", "emulator").put("state", "offline");
        list.addObject().put("id", "iphone-1").put("name", "iPhone 16").put("platform", "ios")
                .put("type", "simulator").put("state", "online");
        return r;
    }

    private void requireDevice(JsonNode p) {
        String id = p.path("deviceId").asText();
        if (!deviceId.equals(id) && !extraIds.contains(id)) {
            throw new IllegalStateException("call for unknown device " + p.path("deviceId"));
        }
    }

    private JsonNode tree() {
        ObjectNode r = MAPPER.createObjectNode();
        ArrayNode elements = r.putArray("elements");
        if (!BUNDLE.equals(foreground)) {
            return r;
        }
        ObjectNode root = elements.addObject();
        root.put("type", "android.widget.FrameLayout");
        rect(root, 0, 0, 1080, 2400);
        ArrayNode children = root.putArray("children");
        if (screen.equals("login")) {
            el(children, "android.widget.TextView", 0).put("text", "Welcome back");
            ObjectNode user = el(children, "android.widget.EditText", 1);
            user.put("identifier", BUNDLE + ":id/username").put("placeholder", "Username").put("text", username)
                    .put("focused", "username".equals(focused) ? "true" : "false");
            ObjectNode pass = el(children, "android.widget.EditText", 2);
            pass.put("identifier", BUNDLE + ":id/password").put("label", "Password").put("text", password);
            int shift = buttonMoves > 0 ? buttonMoves-- * 7 : 0;
            ObjectNode button = el(children, "android.widget.Button", 3);
            button.put("text", "Sign in").put("identifier", BUNDLE + ":id/sign_in").put("enabled", !username.isEmpty());
            rect(button, 40, 100 + 3 * 160 + shift, 1000, 120);
        } else {
            el(children, "android.widget.TextView", 0).put("text", "Hello, " + username).put("identifier", BUNDLE + ":id/greeting");
            ObjectNode list = el(children, "androidx.recyclerview.widget.RecyclerView", 1);
            rect(list, 40, 260, 1000, 600);
            // Flat list items: inside the list's bounds but not its children (bounds fallback).
            ObjectNode orders = el(children, "android.widget.LinearLayout", 2);
            orders.put("text", "Orders");
            rect(orders, 40, 280, 1000, 120);
            ObjectNode settings = el(children, "android.widget.LinearLayout", 3);
            settings.put("text", "Settings");
            rect(settings, 40, 420, 1000, 120);
            if (scrolled) {
                ObjectNode help = el(children, "android.widget.LinearLayout", 3);
                help.put("text", "Help").put("identifier", BUNDLE + ":id/help");
                rect(help, 40, 560, 1000, 120);
            }
            ObjectNode signOut = el(children, "android.widget.Button", 4);
            signOut.put("text", "Sign out");
            rect(signOut, 40, 1000, 1000, 120);
        }
        return r;
    }

    private static ObjectNode el(ArrayNode parent, String type, int row) {
        ObjectNode e = parent.addObject();
        e.put("type", type);
        rect(e, 40, 100 + row * 160, 1000, 120);
        return e;
    }

    private static void rect(ObjectNode e, int x, int y, int w, int h) {
        e.putObject("rect").put("x", x).put("y", y).put("width", w).put("height", h);
    }

    private void tap(int x, int y) {
        if (screen.equals("login")) {
            if (inside(y, 1)) focused = "username";
            else if (inside(y, 2)) focused = "password";
            else if (inside(y, 3) && !username.isEmpty()) {
                screen = "home";
                focused = null;
            } else focused = null;
        } else if (y >= 1000 && y <= 1120) {
            screen = "login";
            username = "";
            password = "";
        }
    }

    private static boolean inside(int y, int row) {
        int top = 100 + row * 160;
        return y >= top && y <= top + 120;
    }
}
