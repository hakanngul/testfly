package io.testfly.inspector;

import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.sun.net.httpserver.HttpServer;

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import java.awt.Component;
import java.awt.Container;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/** The tool window's device list against a fake mobilecli: groups, states, Boot and Shut down. */
public class DeviceListPanelTest extends BasePlatformTestCase {

    private HttpServer server;
    private final ConcurrentHashMap<String, String> state = new ConcurrentHashMap<>();
    private final List<String> calls = new ArrayList<>();
    private String savedUrl;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        state.put("Pixel_10", "online");
        state.put("Pixel_Tablet", "offline");
        state.put("SIM-17", "online");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/rpc", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            var msg = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
            String method = msg.get("method").getAsString();
            var params = msg.getAsJsonObject("params");
            synchronized (calls) {
                calls.add(method + " " + params);
            }
            String result = switch (method) {
                case "server.info" -> "{\"version\":\"fake\"}";
                case "devices.list" -> "{\"devices\":["
                        + dev("Pixel_10", "Pixel 10", "android", "emulator", "17") + ","
                        + dev("Pixel_Tablet", "Pixel Tablet", "android", "emulator", "16") + ","
                        + dev("SIM-17", "iPhone 17", "ios", "simulator", "26.5") + ","
                        + "{\"id\":\"00008150\",\"name\":\"Hakan iPhone\",\"platform\":\"ios\",\"type\":\"real\",\"state\":\"online\",\"version\":\"27.2\"}]}";
                case "device.boot" -> {
                    state.put(params.get("deviceId").getAsString(), "online");
                    yield "{}";
                }
                case "device.shutdown" -> {
                    state.put(params.get("deviceId").getAsString(), "offline");
                    yield "{}";
                }
                default -> "{}";
            };
            byte[] reply = ("{\"jsonrpc\":\"2.0\",\"id\":" + msg.get("id") + ",\"result\":" + result + "}").getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, reply.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(reply);
            }
        });
        server.start();
        savedUrl = InspectorSettings.get().getState().serverUrl;
        InspectorSettings.get().getState().serverUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private String dev(String id, String name, String platform, String type, String version) {
        return "{\"id\":\"" + id + "\",\"name\":\"" + name + "\",\"platform\":\"" + platform + "\",\"type\":\"" + type
                + "\",\"state\":\"" + state.get(id) + "\",\"version\":\"" + version + "\"}";
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            InspectorSettings.get().getState().serverUrl = savedUrl;
            server.stop(0);
        } finally {
            super.tearDown();
        }
    }

    public void testListsGroupsAndBootsAndShutsDown() throws Exception {
        DeviceListPanel panel = new DeviceListPanel(getProject(), null);
        try {
            waitFor(() -> texts(panel).contains("Hakan iPhone"));
            List<String> t = texts(panel);
            assertTrue(t.toString(), t.indexOf("Android") < t.indexOf("Pixel 10") && t.indexOf("iOS simulators") < t.indexOf("iPhone 17")
                    && t.indexOf("iOS devices") < t.indexOf("Hakan iPhone"));
            assertTrue(t.toString(), t.contains("READY  Android 17 · Emulator · Pixel_10"));
            assertTrue(t.toString(), t.contains("Android 16 · Emulator · Pixel_Tablet"));
            assertTrue("offline devices are listed: devices.list asks for them", calls.stream().anyMatch(c -> c.contains("\"includeOffline\":true")));

            click(panel, "Boot");
            waitFor(() -> texts(panel).contains("READY  Android 16 · Emulator · Pixel_Tablet"));
            assertTrue(calls.contains("device.boot {\"deviceId\":\"Pixel_Tablet\"}"));

            List<AbstractButton> stops = buttons(panel).stream().filter(b -> "Shut down".equals(b.getToolTipText())).toList();
            assertEquals("emulators and simulators can be shut down, real devices not", 3, stops.size());
            stops.get(0).doClick();
            waitFor(() -> calls.stream().anyMatch(c -> c.startsWith("device.shutdown")));
        } finally {
            panel.dispose();
        }
    }

    private static void waitFor(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean()) {
            assertTrue("timed out", System.currentTimeMillis() < deadline);
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(50);
        }
    }

    private static List<String> texts(Container c) {
        List<String> out = new ArrayList<>();
        for (Component child : c.getComponents()) {
            if (child instanceof JLabel l && l.getText() != null) {
                out.add(l.getText());
            }
            if (child instanceof Container k) {
                out.addAll(texts(k));
            }
        }
        return out;
    }

    private static List<AbstractButton> buttons(Container c) {
        List<AbstractButton> out = new ArrayList<>();
        for (Component child : c.getComponents()) {
            if (child instanceof AbstractButton b) {
                out.add(b);
            }
            if (child instanceof Container k) {
                out.addAll(buttons(k));
            }
        }
        return out;
    }

    private static void click(Container c, String text) {
        buttons(c).stream().filter(b -> text.equals(b.getText())).findFirst().orElseThrow().doClick();
    }
}
