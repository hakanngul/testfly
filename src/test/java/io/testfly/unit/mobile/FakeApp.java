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

    /** An iOS system alert covering the app (tracking prompt) while true; the tapped button lands in {@code alertAnswer}. */
    boolean alert;
    String alertAnswer;

    /** Android's runtime permission dialog covers the app while true; the tapped button lands in {@code permissionAnswer}. */
    boolean permissionDialog;
    String permissionAnswer;
    /** The on-screen keyboard is shown (only in a full dump); BACK or its Done key closes it. */
    boolean keyboard;
    String clipboard = "";
    String lastPinch;
    JsonNode lastGesture;
    String settings = "";
    String uninstalled;
    final java.util.Map<String, byte[]> files = new java.util.HashMap<>();
    final java.util.List<String> crashes = new java.util.ArrayList<>();
    String webViewUrl = "https://demo.testfly.dev/terms";
    String webViewValue = "";
    /** Arguments of every MobileWebViewLocator script call. */
    final java.util.List<String> webViewCalls = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Shows a "Remember me" checkbox under the login button (check/uncheck tests). */
    boolean showRemember;
    boolean rememberMe;
    String fold;
    JsonNode lastScreenshot;
    JsonNode lastLaunch;

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
            if ("text".equals(p.path("format").asText())) {
                return MAPPER.createObjectNode().put("text", "@e1 [FrameLayout]\n  @e2 [TextView] \"" + (screen.equals("login") ? "Welcome back" : "Hello") + "\"");
            }
            return tree(p.path("full").asBoolean(false));
        });
        server.on("device.io.button", p -> {
            if (p.path("button").asText().equals("BACK")) keyboard = false;
            if (p.path("button").asText().equals("HOME")) foreground = "com.android.launcher";
            return null;
        });
        server.on("device.io.pinch", p -> {
            lastPinch = p.path("direction").asText() + "@" + p.path("x").asInt() + "," + p.path("y").asInt();
            return null;
        });
        server.on("device.io.gesture", p -> {
            lastGesture = p.path("actions");
            return null;
        });
        server.on("device.clipboard.get", p -> MAPPER.createObjectNode().put("text", clipboard));
        server.on("device.clipboard.set", p -> {
            clipboard = p.path("text").asText();
            return null;
        });
        server.on("device.crashes.list", p -> {
            ArrayNode list = MAPPER.createArrayNode();
            for (String id : crashes) {
                list.addObject().put("id", id).put("processName", "Demo").put("timestamp", "2026-10-24-120000");
            }
            return list;
        });
        server.on("device.crashes.get", p -> MAPPER.createObjectNode().put("id", p.path("id").asText())
                .put("content", "Exception Type: EXC_CRASH (" + p.path("id").asText() + ")"));
        server.on("device.fs.push", p -> {
            files.put(p.path("remotePath").asText(), Base64.getDecoder().decode(p.path("content").asText()));
            return null;
        });
        server.on("device.fs.pull", p -> {
            byte[] content = files.get(p.path("remotePath").asText());
            if (content == null) return FakeMobilecliServer.error(-32000, "Server error", "no such file: " + p.path("remotePath").asText());
            return MAPPER.createObjectNode().put("content", Base64.getEncoder().encodeToString(content)).put("size", content.length);
        });
        server.on("device.settings.apply", p -> {
            settings = p.path("animations").asText("-") + "/" + p.path("appearance").asText("-");
            return MAPPER.createObjectNode().put("status", "ok");
        });
        server.on("device.apps.list", p -> {
            ArrayNode list = MAPPER.createArrayNode();
            list.addObject().put("packageName", BUNDLE).put("appName", "Demo");
            return list;
        });
        server.on("device.apps.foreground", p -> MAPPER.createObjectNode().put("packageName", foreground));
        server.on("device.apps.uninstall", p -> {
            uninstalled = p.path("bundleId").asText();
            return null;
        });
        server.on("device.webview.list", p -> {
            ArrayNode list = MAPPER.createArrayNode();
            list.addObject().put("id", "wv-1").put("url", webViewUrl).put("title", "Terms").put("isVisible", true);
            list.addObject().put("id", "wv-2").put("url", "about:blank").put("title", "").put("isVisible", false);
            return list;
        });
        server.on("device.webview.url", p -> MAPPER.getNodeFactory().textNode(webViewUrl));
        server.on("device.webview.title", p -> MAPPER.getNodeFactory().textNode("Terms"));
        server.on("device.webview.content", p -> MAPPER.getNodeFactory().textNode("<html><h1>Terms</h1></html>"));
        server.on("device.webview.goto", p -> {
            webViewUrl = p.path("url").asText();
            return MAPPER.createObjectNode().put("status", "ok");
        });
        server.on("device.webview.evaluate", p -> {
            JsonNode args = p.path("args");
            if (!args.isArray() || args.isEmpty()) return MAPPER.getNodeFactory().numberNode(p.path("expression").asText().length());
            // MobileWebViewLocator's script: [css, name, op, arg]; one visible element unless the css says "missing".
            webViewCalls.add(args.toString());
            boolean found = !args.get(0).asText().contains("missing");
            return switch (args.get(2).asText()) {
                case "count" -> MAPPER.getNodeFactory().numberNode(found ? 1 : 0);
                case "state" -> MAPPER.getNodeFactory().textNode(found ? "visible" : "matches:0");
                case "fill" -> { webViewValue = args.get(3).asText(); yield MAPPER.getNodeFactory().booleanNode(true); }
                case "value" -> MAPPER.getNodeFactory().textNode(webViewValue);
                case "text" -> MAPPER.getNodeFactory().textNode("Terms");
                case "attr" -> MAPPER.getNodeFactory().textNode("#" + args.get(3).asText());
                default -> MAPPER.getNodeFactory().booleanNode(true);
            };
        });
        server.on("device.webview.query", p -> {
            ArrayNode list = MAPPER.createArrayNode();
            list.addObject().put("tag", "h1").put("text", "Terms");
            return list;
        });
        server.on("device.webview.waitForLoadState", p -> MAPPER.createObjectNode().put("status", "ok"));
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
        server.on("device.fold.set", p -> {
            fold = p.path("state").asText();
            return null;
        });
        server.on("device.apps.launch", p -> {
            lastLaunch = p;
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
            lastScreenshot = p;
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

    private JsonNode tree(boolean full) {
        ObjectNode r = MAPPER.createObjectNode();
        ArrayNode elements = r.putArray("elements");
        if (!BUNDLE.equals(foreground)) {
            return r;
        }
        if (permissionDialog) {
            ArrayNode dialog = elements.addObject().put("type", "android.widget.LinearLayout").putArray("children");
            ObjectNode allow = dialog.addObject().put("type", "android.widget.Button").put("text", "While using the app")
                    .put("identifier", "com.android.permissioncontroller:id/permission_allow_foreground_only_button");
            rect(allow, 100, 1200, 880, 100);
            ObjectNode deny = dialog.addObject().put("type", "android.widget.Button").put("text", "Don\u2019t allow")
                    .put("identifier", "com.android.permissioncontroller:id/permission_deny_button");
            rect(deny, 100, 1350, 880, 100);
            return r;
        }
        if (full && keyboard) {
            ObjectNode kb = elements.addObject();
            if (platform.equals("ios")) {
                kb.put("type", "Keyboard");
                ObjectNode done = kb.putArray("children").addObject().put("type", "Button").put("label", "Done");
                rect(done, 900, 2300, 160, 80);
            } else {
                kb.put("type", "android.widget.FrameLayout").put("identifier", "com.google.android.inputmethod.latin:id/keyboard_holder");
            }
            rect(kb, 0, 1800, 1080, 600);
        }
        if (alert) {
            // Like mobilecli on iOS: only the alert is in the tree, the app beneath is not.
            ObjectNode a = elements.addObject();
            a.put("type", "Alert").put("label", "Allow \u201cDemo\u201d to track your activity?");
            rect(a, 100, 900, 880, 600);
            ArrayNode buttons = a.putArray("children");
            ObjectNode deny = buttons.addObject().put("type", "Button").put("label", "Ask App Not to Track");
            rect(deny, 120, 1200, 840, 100);
            ObjectNode allow = buttons.addObject().put("type", "Button").put("label", "Allow");
            rect(allow, 120, 1350, 840, 100);
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
            if (showRemember) {
                el(children, "android.widget.CheckBox", 4).put("text", "Remember me").put("checked", rememberMe);
            }
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
        if (keyboard && platform.equals("ios") && y >= 2300 && x >= 900) {
            keyboard = false;
            return;
        }
        if (permissionDialog) {
            if (y >= 1200 && y <= 1300) permissionAnswer = "allow";
            if (y >= 1350 && y <= 1450) permissionAnswer = "deny";
            permissionDialog = permissionAnswer == null;
            return;
        }
        if (alert) {
            if (y >= 1200 && y <= 1300) alertAnswer = "deny";
            if (y >= 1350 && y <= 1450) alertAnswer = "allow";
            alert = alertAnswer == null;
            return;
        }
        if (screen.equals("login")) {
            if (inside(y, 1)) focused = "username";
            else if (inside(y, 2)) focused = "password";
            else if (inside(y, 3) && !username.isEmpty()) {
                screen = "home";
                focused = null;
            } else if (showRemember && inside(y, 4)) rememberMe = !rememberMe;
            else focused = null;
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
