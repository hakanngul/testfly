package io.testfly.unit.mobile;

import com.fasterxml.jackson.databind.JsonNode;
import io.testfly.exceptions.ConfigurationException;
import io.testfly.mobile.DeviceNotFoundException;
import io.testfly.mobile.MobileDeviceInfo;
import io.testfly.mobile.MobileNode;
import io.testfly.mobile.MobileRpcException;
import io.testfly.mobile.MobileServerUnavailableException;
import io.testfly.mobile.MobileTimeoutException;
import io.testfly.mobile.MobileVersionException;
import io.testfly.mobile.internal.JsonRpcClient;
import io.testfly.mobile.internal.MobileCliDriver;
import io.testfly.mobile.internal.MobileServerUrl;
import io.testfly.mobile.internal.ServerVersion;
import org.testng.annotations.Test;

import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static io.testfly.unit.mobile.FakeMobilecliServer.CLOSE;
import static io.testfly.unit.mobile.FakeMobilecliServer.MAPPER;
import static io.testfly.unit.mobile.FakeMobilecliServer.delay;
import static io.testfly.unit.mobile.FakeMobilecliServer.error;
import static io.testfly.unit.mobile.FakeMobilecliServer.fixture;
import static org.testng.Assert.*;

/** JSON-RPC transport, URL policy, version check and the mobilecli driver against recorded 1.0.20 responses. */
public class MobileTransportTest {

    private static final Duration T = Duration.ofSeconds(5);

    private static JsonRpcClient client(FakeMobilecliServer server) {
        return new JsonRpcClient(MobileServerUrl.rpcEndpoint(server.url(), false), Map.of("X-Trace", "abc"));
    }

    private static <E extends Throwable> E expectThrows(Class<E> type, Runnable body) {
        try {
            body.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
            throw new AssertionError("expected " + type.getSimpleName() + " but got " + t, t);
        }
        throw new AssertionError("expected " + type.getSimpleName());
    }

    // ── server URL policy ───────────────────────────────────────────────

    @Test
    public void loopbackUrlsAreAcceptedAndGetRpcPath() {
        assertEquals(MobileServerUrl.rpcEndpoint("http://localhost:12000", false), URI.create("http://localhost:12000/rpc"));
        assertEquals(MobileServerUrl.rpcEndpoint("http://127.0.0.1:12000/", false), URI.create("http://127.0.0.1:12000/rpc"));
        assertEquals(MobileServerUrl.rpcEndpoint("http://[::1]:12000", false), URI.create("http://[::1]:12000/rpc"));
        assertEquals(MobileServerUrl.rpcEndpoint("https://lab.example.com/mobile", true), URI.create("https://lab.example.com/mobile/rpc"));
    }

    @Test
    public void remoteUrlsNeedOptInAndHttps() {
        assertTrue(expectThrows(ConfigurationException.class, () -> MobileServerUrl.rpcEndpoint("http://10.0.0.5:12000", false))
                .getMessage().contains("allowRemote"));
        assertTrue(expectThrows(ConfigurationException.class, () -> MobileServerUrl.rpcEndpoint("http://lab.example.com", true))
                .getMessage().contains("https://"));
        // A DNS name is never trusted to be loopback.
        expectThrows(ConfigurationException.class, () -> MobileServerUrl.rpcEndpoint("http://localhost.evil.com", false));
        expectThrows(ConfigurationException.class, () -> MobileServerUrl.rpcEndpoint("ws://localhost:12000", false));
    }

    // ── JSON-RPC ────────────────────────────────────────────────────────

    @Test
    public void returnsResultAndSendsConfiguredHeaders() {
        try (FakeMobilecliServer server = new FakeMobilecliServer()) {
            JsonNode result = client(server).call("server.info", null, T, true);
            assertEquals(result.path("version").asText(), "1.0.20");
            assertEquals(server.lastHeaders().get("X-trace"), List.of("abc"));
        }
    }

    @Test
    public void mapsRecordedErrorResponses() {
        try (FakeMobilecliServer server = new FakeMobilecliServer()) {
            JsonNode unknownDevice = fixture("error.unknown-device").get("error");
            server.on("device.info", p -> error(unknownDevice.get("code").asInt(), unknownDevice.get("message").asText(),
                    unknownDevice.get("data").asText()));
            JsonRpcClient rpc = client(server);
            DeviceNotFoundException notFound = expectThrows(DeviceNotFoundException.class,
                    () -> rpc.call("device.info", JsonRpcClient.params().put("deviceId", "nope"), T, true));
            assertTrue(notFound.getMessage().contains("device not found: nope"), notFound.getMessage());

            MobileRpcException unknownMethod = expectThrows(MobileRpcException.class, () -> rpc.call("device.bogus", null, T, true));
            assertEquals(unknownMethod.getCode(), Integer.valueOf(-32601));

            JsonNode dump = fixture("device.dump.ui").get("error");
            server.on("device.dump.ui", p -> error(dump.get("code").asInt(), dump.get("message").asText(), dump.get("data").asText()));
            MobileRpcException dumpFailure = expectThrows(MobileRpcException.class, () -> rpc.call("device.dump.ui", null, T, true));
            assertEquals(dumpFailure.getCode(), Integer.valueOf(-32000));
            assertTrue(dumpFailure.getData().contains("no XML content"));

            server.on("device.slow", p -> error(-32050, "Timeout", "agent did not answer"));
            expectThrows(MobileTimeoutException.class, () -> rpc.call("device.slow", null, T, true));
        }
    }

    @Test
    public void closedConnectionWithoutAnswerIsATransportError() {
        try (FakeMobilecliServer server = new FakeMobilecliServer()) {
            server.on("device.screenshot", p -> CLOSE);
            MobileRpcException e = expectThrows(MobileRpcException.class,
                    () -> client(server).call("device.screenshot", null, T, false));
            assertNull(e.getCode());
            assertTrue(e.getMessage().contains("without answering"), e.getMessage());
            assertEquals(server.methods(), List.of("device.screenshot"), "state-changing/non-read-only calls are not retried");
        }
    }

    @Test
    public void readOnlyCallsAreRetriedOnceAfterIoFailure() {
        try (FakeMobilecliServer server = new FakeMobilecliServer()) {
            AtomicInteger attempts = new AtomicInteger();
            server.on("device.dump.ui", p -> attempts.incrementAndGet() == 1 ? CLOSE : MAPPER.createObjectNode().putArray("elements"));
            client(server).call("device.dump.ui", null, T, true);
            assertEquals(attempts.get(), 2);

            AtomicInteger taps = new AtomicInteger();
            server.on("device.io.tap", p -> {
                taps.incrementAndGet();
                return CLOSE;
            });
            expectThrows(MobileRpcException.class, () -> client(server).call("device.io.tap", null, T, false));
            assertEquals(taps.get(), 1, "a tap is never retried");
        }
    }

    @Test
    public void requestsAreBoundedByTheirTimeout() {
        try (FakeMobilecliServer server = new FakeMobilecliServer()) {
            server.on("device.io.tap", p -> delay(3000, null));
            long start = System.nanoTime();
            MobileTimeoutException e = expectThrows(MobileTimeoutException.class,
                    () -> client(server).call("device.io.tap", null, Duration.ofMillis(300), false));
            assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 2500);
            assertTrue(e.getMessage().contains("may still complete"), e.getMessage());

            server.on("device.dump.ui", p -> delay(3000, null));
            MobileTimeoutException read = expectThrows(MobileTimeoutException.class,
                    () -> client(server).call("device.dump.ui", null, Duration.ofMillis(300), true));
            assertFalse(read.getMessage().contains("may still complete"), "only state-changing calls carry the warning");
        }
    }

    @Test
    public void unreachableServerExplainsHowToStartIt() throws Exception {
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        JsonRpcClient rpc = new JsonRpcClient(URI.create("http://127.0.0.1:" + port + "/rpc"), Map.of());
        MobileServerUnavailableException e = expectThrows(MobileServerUnavailableException.class,
                () -> rpc.call("server.info", null, T, true));
        assertTrue(e.getMessage().contains("mobilecli server start"), e.getMessage());
    }

    // ── version ─────────────────────────────────────────────────────────

    @Test
    public void minimumVersionIsEnforced() {
        ServerVersion.require("1.0.20");
        ServerVersion.require("v1.1.0");
        ServerVersion.require("2.0.0-beta.1");
        ServerVersion.require("dev"); // unrecognised: warn, do not fail
        assertTrue(expectThrows(MobileVersionException.class, () -> ServerVersion.require("1.0.19")).getMessage().contains("1.0.20"));
    }

    // ── driver ──────────────────────────────────────────────────────────

    @Test
    public void driverParsesRecordedDiscoveryResponses() {
        try (FakeMobilecliServer server = new FakeMobilecliServer()) {
            server.on("device.info", p -> fixture("device.info").get("result"));
            MobileCliDriver driver = new MobileCliDriver(client(server));
            assertEquals(driver.serverVersion(T), "1.0.20");
            List<MobileDeviceInfo> devices = driver.devices(T);
            assertEquals(devices, List.of(new MobileDeviceInfo("tf34", "tf34", "android", "emulator", "online", "14", "sdk_gphone64_x86_64")));
            driver.useDevice(devices.get(0));
            assertEquals(driver.screenSize(T).width(), 1080);
            assertEquals(driver.screenSize(T).height(), 2400);
            // Discovery calls carry no deviceId; device calls do.
            assertFalse(server.calls().get(1).params().has("deviceId"));
            assertEquals(server.calls().get(2).params().path("deviceId").asText(), "tf34");
        }
    }

    @Test
    public void driverMapsElementsAndPlatformSpecificClear() {
        try (FakeMobilecliServer server = new FakeMobilecliServer()) {
            server.on("device.dump.ui", p -> {
                var r = MAPPER.createObjectNode();
                var el = r.putArray("elements").addObject();
                el.put("type", "XCUIElementTypeButton").put("label", "Save").put("text", "").put("enabled", "false")
                        .putObject("rect").put("x", 10).put("y", 20).put("width", 100).put("height", 40);
                el.putArray("children").addObject().put("type", "StaticText").put("text", "Save");
                return r;
            });
            server.on("device.io.keys", p -> null);
            server.on("device.screenshot", p -> MAPPER.createObjectNode().put("data", "data:image/png;base64,iVBORw=="));
            MobileCliDriver driver = new MobileCliDriver(client(server));
            driver.useDevice(new MobileDeviceInfo("sim-1", "iPhone", "ios", "simulator", "online", "18", null));

            MobileNode button = driver.viewTree(T).get(0);
            assertEquals(button.type(), "XCUIElementTypeButton");
            assertNull(button.text(), "empty strings are treated as absent");
            assertFalse(button.enabled(), "string booleans are understood");
            assertTrue(button.visible(), "visibility falls back to non-empty bounds");
            assertEquals(button.bounds().centerX(), 60.0);
            assertEquals(button.children().get(0).text(), "Save");

            driver.clearText(T);
            assertEquals(server.calls().get(1).params().get("keys").toString(), "[\"clear\"]");
            driver.useDevice(new MobileDeviceInfo("emu-1", "Pixel", "android", "emulator", "online", "14", null));
            driver.clearText(T);
            assertEquals(server.calls().get(2).params().get("keys").toString(), "[\"ctrl+a\",\"backspace\"]");

            assertEquals(driver.screenshot(T), new byte[] {(byte) 0x89, 'P', 'N', 'G'});
        }
    }
}
