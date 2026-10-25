package io.testfly.inspector;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/**
 * Handles the webview's messages (the protocol of media/inspector.js, shared with the VS Code
 * extension) against mobilecli and posts the answers back. Runs off the UI thread; IDE services
 * (clipboard, status bar) come in through {@link Ide}.
 */
public final class InspectorController implements AutoCloseable {

    /** What the controller needs from the IDE. */
    public interface Ide {
        void copy(String text, String label);

        boolean autoRefreshAfterAction();
    }

    private final MobileCliClient cli;
    private final Consumer<JsonObject> post;
    private final Ide ide;
    private JsonObject device;
    private JsonObject screen;
    private boolean busy;
    private boolean warm; // mobilecli's agent is up for this device: tree and screenshot can run together
    private boolean serverStopped; // stopped from the panel: no auto-start until it is started from the panel again

    public InspectorController(MobileCliClient cli, Consumer<JsonObject> post, Ide ide) {
        this.cli = cli;
        this.post = post;
        this.ide = ide;
    }

    /** One message from the webview, as JSON text. */
    public void handle(String json) {
        JsonObject m = JsonParser.parseString(json).getAsJsonObject();
        try {
            switch (MobileCliClient.str(m, "type")) {
                case "ready", "devices" -> loadDevices();
                case "selectDevice" -> selectDevice(MobileCliClient.str(m, "id"), null);
                case "refresh" -> refresh(m.has("quiet") && m.get("quiet").getAsBoolean());
                case "server" -> {
                    if ("start".equals(MobileCliClient.str(m, "action"))) {
                        startServer();
                    } else {
                        stopServer();
                    }
                }
                case "snapshotText" -> post(msg("snapshotText").withText("text", cli.snapshotText(requireDevice())));
                case "tap" -> act(id -> cli.tap(id, num(m, "x"), num(m, "y")));
                case "longPress" -> act(id -> cli.longPress(id, num(m, "x"), num(m, "y")));
                case "text" -> act(id -> cli.typeText(id, MobileCliClient.str(m, "text")));
                case "tapType" -> act(id -> {
                    cli.tap(id, num(m, "x"), num(m, "y"));
                    sleep(300); // let the field take focus
                    cli.typeText(id, MobileCliClient.str(m, "text"));
                });
                case "button" -> act(id -> cli.pressButton(id, MobileCliClient.str(m, "button")));
                case "swipe" -> act(id -> {
                    if (m.has("x1") && m.has("y1") && m.has("x2") && m.has("y2")) {
                        cli.swipe(id, num(m, "x1"), num(m, "y1"), num(m, "x2"), num(m, "y2")); // dragged on the screenshot
                    } else {
                        swipe(id, MobileCliClient.str(m, "direction"));
                    }
                });
                case "copy" -> ide.copy(MobileCliClient.str(m, "text"), MobileCliClient.str(m, "label"));
                default -> {
                }
            }
        } catch (RuntimeException e) {
            boolean unreachable = e instanceof MobileCliClient.RpcException r && r.unreachable;
            if (unreachable) {
                post.accept(server(false, ""));
            }
            JsonObject error = msg("error").withText("message", e.getMessage()).json;
            error.addProperty("unreachable", unreachable);
            post.accept(error);
        }
    }

    private void loadDevices() {
        status("connecting", "Connecting to mobilecli…");
        String version = serverStopped ? cli.ensureServer(Duration.ofSeconds(20), false) : cli.ensureServer(Duration.ofSeconds(20));
        post.accept(server(true, version));
        JsonArray devices = cli.onlineDevices();
        JsonObject list = msg("devices").withText("version", version).json;
        list.add("devices", devices);
        post.accept(list);
        JsonObject keep = null;
        for (JsonElement d : devices) {
            if (device != null && MobileCliClient.str(d.getAsJsonObject(), "id").equals(MobileCliClient.str(device, "id"))) {
                keep = d.getAsJsonObject();
            }
        }
        if (keep == null && devices.size() == 1) {
            keep = devices.get(0).getAsJsonObject();
        }
        if (keep != null) {
            selectDevice(MobileCliClient.str(keep, "id"), devices);
        } else {
            status("idle", devices.isEmpty() ? "No online device. Boot a simulator or emulator, then press refresh." : "Choose a device.");
        }
    }

    private void startServer() {
        serverStopped = false;
        status("connecting", "Starting mobilecli server…");
        cli.startServer(Duration.ofSeconds(20));
        loadDevices();
    }

    private void stopServer() {
        serverStopped = true;
        status("loading", "Stopping mobilecli server…");
        cli.stopServer(Duration.ofSeconds(5));
        device = null;
        screen = null;
        warm = false;
        post.accept(server(false, ""));
        JsonObject list = msg("devices").json;
        list.add("devices", new JsonArray());
        post.accept(list);
        status("idle", "mobilecli server stopped. Start it from the Server button.");
    }

    private JsonObject server(boolean running, String version) {
        JsonObject m = msg("server").withText("version", version).json;
        m.addProperty("running", running);
        return m;
    }

    private void selectDevice(String id, JsonArray known) {
        JsonArray devices = known != null ? known : cli.onlineDevices();
        device = null;
        screen = null;
        warm = false;
        for (JsonElement d : devices) {
            if (MobileCliClient.str(d.getAsJsonObject(), "id").equals(id)) {
                device = d.getAsJsonObject();
            }
        }
        if (device == null) {
            throw new IllegalStateException("Device " + id + " is not online any more.");
        }
        JsonObject m = msg("device").json;
        m.add("device", device);
        post.accept(m);
        refresh(false);
    }

    /** quiet: a Live refresh, which neither dims the screen nor reports progress. */
    private void refresh(boolean quiet) {
        String id = requireDevice();
        synchronized (this) {
            if (busy) {
                return;
            }
            busy = true;
        }
        if (!quiet) {
            status("loading", "Reading " + MobileCliClient.str(device, "name") + "…");
        }
        try {
            long started = System.nanoTime();
            if (screen == null) {
                screen = cli.screenSize(id);
            }
            JsonArray elements;
            String screenshot;
            if (warm) {
                // Both at once; the picture is shown as soon as it arrives, the tree follows.
                CompletableFuture<String> shot = CompletableFuture.supplyAsync(() -> {
                    String s = cli.screenshot(id);
                    if (!quiet) {
                        post(msg("shot").withText("screenshot", s));
                    }
                    return s;
                });
                elements = cli.dump(id);
                try {
                    screenshot = shot.join();
                } catch (CompletionException e) {
                    throw e.getCause() instanceof RuntimeException r ? r : e;
                }
            } else {
                // The tree first: on iOS it also starts mobilecli's agent, which the screenshot then reuses.
                elements = cli.dump(id);
                screenshot = cli.screenshot(id);
                warm = true;
            }
            JsonObject m = msg("screen").withText("screenshot", screenshot).json;
            m.add("device", device);
            m.add("screen", screen);
            m.add("elements", elements);
            m.addProperty("ms", (System.nanoTime() - started) / 1_000_000);
            m.addProperty("quiet", quiet);
            post.accept(m);
        } finally {
            synchronized (this) {
                busy = false;
            }
        }
    }

    private interface Action {
        void run(String deviceId);
    }

    private void act(Action action) {
        String id = requireDevice();
        action.run(id);
        if (ide.autoRefreshAfterAction()) {
            sleep(300); // let the UI settle (animations, navigation)
            refresh(false);
        } else {
            status("ok", MobileCliClient.str(device, "name") + " · done; press Refresh to read the screen");
        }
    }

    private void swipe(String id, String direction) {
        JsonObject s = screen != null ? screen : cli.screenSize(id);
        double w = s.get("width").getAsDouble(), h = s.get("height").getAsDouble();
        double cx = w / 2, cy = h / 2, dx = w / 4, dy = h / 4;
        switch (direction) {
            case "up" -> cli.swipe(id, cx, cy + dy, cx, cy - dy);
            case "down" -> cli.swipe(id, cx, cy - dy, cx, cy + dy);
            case "left" -> cli.swipe(id, cx + dx, cy, cx - dx, cy);
            default -> cli.swipe(id, cx - dx, cy, cx + dx, cy);
        }
    }

    private String requireDevice() {
        if (device == null) {
            throw new IllegalStateException("Choose a device first.");
        }
        return MobileCliClient.str(device, "id");
    }

    private void status(String state, String message) {
        post(msg("status").withText("state", state).withText("message", message));
    }

    private void post(Msg m) {
        post.accept(m.json);
    }

    @Override
    public void close() {
        cli.close();
    }

    private static double num(JsonObject m, String key) {
        return m.has(key) ? m.get(key).getAsDouble() : 0;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Msg msg(String type) {
        return new Msg(type);
    }

    private static final class Msg {
        final JsonObject json = new JsonObject();

        Msg(String type) {
            json.addProperty("type", type);
        }

        Msg withText(String key, String value) {
            json.addProperty(key, value);
            return this;
        }
    }
}
