package io.testfly.inspector;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static org.junit.Assert.*;

/** The controller and the RPC client against a fake mobilecli JSON-RPC server. */
public class InspectorControllerTest {

    private HttpServer server;
    private final Map<String, Function<JsonObject, String>> handlers = new ConcurrentHashMap<>();
    private final List<String> calls = new ArrayList<>();
    private final List<JsonObject> posted = new ArrayList<>();
    private final List<String> copied = new ArrayList<>();

    @Before
    public void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/rpc", ex -> {
            JsonObject msg = JsonParser.parseString(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            String method = msg.get("method").getAsString();
            synchronized (calls) {
                calls.add(method + " " + msg.get("params"));
            }
            Function<JsonObject, String> h = handlers.get(method);
            String reply = h == null
                    ? "{\"jsonrpc\":\"2.0\",\"id\":" + msg.get("id") + ",\"error\":{\"code\":-32601,\"message\":\"Method not found\"}}"
                    : "{\"jsonrpc\":\"2.0\",\"id\":" + msg.get("id") + "," + h.apply(msg.getAsJsonObject("params")) + "}";
            byte[] body = reply.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        handlers.put("server.info", p -> "\"result\":{\"version\":\"1.0.20\"}");
        handlers.put("devices.list", p -> "\"result\":{\"devices\":[{\"id\":\"sim-1\",\"name\":\"iPhone 17\",\"platform\":\"ios\",\"type\":\"simulator\",\"state\":\"online\"},"
                + "{\"id\":\"old\",\"name\":\"Pixel 6\",\"platform\":\"android\",\"type\":\"emulator\",\"state\":\"offline\"}]}");
        handlers.put("device.info", p -> "\"result\":{\"device\":{\"screenSize\":{\"width\":390,\"height\":844,\"scale\":3}}}");
        handlers.put("device.dump.ui", p -> p.has("format")
                ? "\"result\":{\"text\":\"@e1 [Window]\\n  @e2 [Button] \\\"Go\\\"\"}"
                : "\"result\":{\"elements\":[{\"ref\":\"@e1\",\"type\":\"Window\",\"children\":[{\"ref\":\"@e2\",\"type\":\"Button\",\"label\":\"Go\"}]}]}");
        handlers.put("device.screenshot", p -> "\"result\":{\"format\":\"png\",\"data\":\"data:image/png;base64,AAAA\"}");
        for (String m : new String[] {"device.io.tap", "device.io.text", "device.io.button", "device.io.swipe", "device.io.longpress"}) {
            handlers.put(m, p -> "\"result\":null");
        }
    }

    @After
    public void stop() {
        server.stop(0);
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private InspectorController controller(boolean autoRefresh) {
        return new InspectorController(new MobileCliClient(url() + "/", "mobilecli", false), posted::add, new InspectorController.Ide() {
            @Override
            public void copy(String text, String label) {
                copied.add(label + ":" + text);
            }

            @Override
            public boolean autoRefreshAfterAction() {
                return autoRefresh;
            }
        });
    }

    private List<String> types() {
        return posted.stream().map(m -> m.get("type").getAsString()).toList();
    }

    @Test
    public void readyListsOnlineDevicesAndReadsTheOnlyOne() {
        controller(false).handle("{\"type\":\"ready\"}");
        assertEquals(List.of("status", "devices", "device", "status", "screen"), types());
        assertEquals(1, posted.get(1).getAsJsonArray("devices").size());
        JsonObject screen = posted.get(4);
        assertEquals("@e2", screen.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonArray("children").get(0).getAsJsonObject().get("ref").getAsString());
        assertEquals(390, screen.getAsJsonObject("screen").get("width").getAsInt());
        assertEquals("data:image/png;base64,AAAA", screen.get("screenshot").getAsString());
    }

    @Test
    public void actionsReachTheDeviceAndRefresh() {
        InspectorController c = controller(true);
        c.handle("{\"type\":\"ready\"}");
        posted.clear();
        c.handle("{\"type\":\"tapType\",\"x\":10.4,\"y\":20.6,\"text\":\"ada\"}");
        c.handle("{\"type\":\"swipe\",\"direction\":\"up\"}");
        c.handle("{\"type\":\"snapshotText\"}");
        c.handle("{\"type\":\"copy\",\"text\":\"btnLogin\",\"label\":\"selector\"}");
        assertTrue(calls.contains("device.io.tap {\"deviceId\":\"sim-1\",\"x\":10,\"y\":21}"));
        assertTrue(calls.contains("device.io.text {\"deviceId\":\"sim-1\",\"text\":\"ada\"}"));
        assertTrue(calls.contains("device.io.swipe {\"deviceId\":\"sim-1\",\"x1\":195,\"y1\":633,\"x2\":195,\"y2\":211}"));
        assertTrue(types().contains("screen"));
        assertTrue("warm refresh posts the screenshot early", types().contains("shot"));
        assertTrue(posted.get(posted.size() - 1).get("text").getAsString().contains("@e2 [Button]"));
        assertEquals(List.of("selector:btnLogin"), copied);
    }

    @Test
    public void errorsAreShownInTheWebview() {
        handlers.put("device.dump.ui", p -> "\"error\":{\"code\":-32000,\"message\":\"Server error\",\"data\":\"agent not installed\"}");
        controller(false).handle("{\"type\":\"ready\"}");
        JsonObject error = posted.get(posted.size() - 1);
        assertEquals("error", error.get("type").getAsString());
        assertTrue(error.get("message").getAsString(), error.get("message").getAsString().contains("agent not installed"));
        assertFalse(error.get("unreachable").getAsBoolean());

        posted.clear();
        new InspectorController(new MobileCliClient("http://127.0.0.1:1", "mobilecli", false), posted::add, null).handle("{\"type\":\"devices\"}");
        assertTrue(posted.get(posted.size() - 1).get("unreachable").getAsBoolean());
    }

    @Test
    public void autoStartOnlyForLocalServers() {
        assertTrue(MobileCliClient.isLoopback("http://localhost:12000"));
        assertTrue(MobileCliClient.isLoopback("http://127.0.0.1:12000"));
        assertFalse(MobileCliClient.isLoopback("https://lab.example.com"));
        MobileCliClient missing = new MobileCliClient("http://127.0.0.1:1", "/nonexistent/mobilecli", true);
        try {
            missing.ensureServer(Duration.ofSeconds(2));
            fail("expected an error");
        } catch (MobileCliClient.RpcException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Could not run"));
        }
    }
}
