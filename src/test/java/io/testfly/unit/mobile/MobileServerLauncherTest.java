package io.testfly.unit.mobile;

import io.testfly.config.TestFlyConfig;
import io.testfly.mobile.Mobile;
import io.testfly.mobile.MobileDevice;
import io.testfly.mobile.MobileServerUnavailableException;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.*;

/** {@code mobile.autoStartServer}: a fake {@code mobilecli} script stands in for the binary. */
public class MobileServerLauncherTest {

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    /** A fake mobilecli that records its arguments in {@code marker}, then runs {@code then}. */
    private static Path script(Path marker, String then) throws IOException {
        Path dir = Files.createTempDirectory("tf-mobilecli-");
        Path script = dir.resolve("mobilecli");
        Files.writeString(script, "#!/bin/sh\necho \"$@\" > '" + marker + "'\n" + then + "\n");
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
        return script;
    }

    private static TestFlyConfig.Mobile config(int port, String mobilecli, String deviceId) {
        TestFlyConfig.Mobile m = new TestFlyConfig.Mobile();
        m.setServerUrl("http://127.0.0.1:" + port);
        m.setMobilecliPath(mobilecli);
        m.setPlatform("android");
        m.setDeviceId(deviceId);
        m.getApp().setBundleId(FakeApp.BUNDLE);
        m.getTimeouts().setActionMs(1500);
        m.getTimeouts().setPollMs(20);
        m.getTimeouts().setRpcMs(1000);
        m.getTimeouts().setServerStartMs(1500);
        return m;
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

    @Test
    public void startsTheServerWhenItIsDownAndConnects() throws Exception {
        int port = freePort();
        Path marker = Files.createTempFile("tf-mobilecli-", ".args");
        Files.delete(marker);
        Path mobilecli = script(marker, "sleep 20");
        String id = "auto-" + Long.toHexString(System.nanoTime());

        // Plays mobilecli: once the script ran, serve the fake app on the port it was told to listen on.
        CompletableFuture<FakeMobilecliServer> served = CompletableFuture.supplyAsync(() -> {
            while (!Files.exists(marker)) {
                Thread.onSpinWait();
            }
            FakeMobilecliServer server = new FakeMobilecliServer(port);
            new FakeApp(id).install(server);
            return server;
        });
        try (MobileDevice device = Mobile.connect(config(port, mobilecli.toString(), id))) {
            assertEquals(device.id(), id);
            assertEquals(Files.readString(marker).trim(), "server start --listen 127.0.0.1:" + port);
        } finally {
            served.get(5, TimeUnit.SECONDS).close();
        }
    }

    @Test
    public void doesNotStartAServerThatAlreadyAnswers() throws Exception {
        Path marker = Files.createTempFile("tf-mobilecli-", ".args");
        Files.delete(marker);
        String id = "up-" + Long.toHexString(System.nanoTime());
        try (FakeMobilecliServer server = new FakeMobilecliServer()) {
            new FakeApp(id).install(server);
            int port = Integer.parseInt(server.url().replaceAll(".*:(\\d+).*", "$1"));
            try (MobileDevice device = Mobile.connect(config(port, script(marker, "exit 0").toString(), id))) {
                assertEquals(device.id(), id);
            }
        }
        assertFalse(Files.exists(marker), "mobilecli must not run when the server answers");
    }

    @Test
    public void keepsTheOldErrorWhenAutoStartIsOff() throws Exception {
        Path marker = Files.createTempFile("tf-mobilecli-", ".args");
        Files.delete(marker);
        TestFlyConfig.Mobile m = config(freePort(), script(marker, "exit 0").toString(), "x");
        m.setAutoStartServer(false);
        MobileServerUnavailableException e = expectThrows(MobileServerUnavailableException.class, () -> Mobile.connect(m));
        assertTrue(e.getMessage().contains("mobilecli server start"), e.getMessage());
        assertFalse(Files.exists(marker));
    }

    @Test
    public void explainsAMissingBinary() throws Exception {
        TestFlyConfig.Mobile m = config(freePort(), "/nonexistent/tf/mobilecli", "x");
        MobileServerUnavailableException e = expectThrows(MobileServerUnavailableException.class, () -> Mobile.connect(m));
        assertTrue(e.getMessage().contains("mobilecli not found at '/nonexistent/tf/mobilecli'"), e.getMessage());
        // A second session on the same endpoint fails fast with the same error.
        assertSame(expectThrows(MobileServerUnavailableException.class, () -> Mobile.connect(m)), e);
    }

    @Test
    public void reportsAServerThatExitsOrNeverAnswers() throws Exception {
        Path marker = Files.createTempFile("tf-mobilecli-", ".args");
        TestFlyConfig.Mobile exits = config(freePort(), script(marker, "exit 3").toString(), "x");
        MobileServerUnavailableException e = expectThrows(MobileServerUnavailableException.class, () -> Mobile.connect(exits));
        assertTrue(e.getMessage().contains("exited with code 3"), e.getMessage());
        assertTrue(e.getMessage().contains("mobilecli-autostart.log"), e.getMessage());

        TestFlyConfig.Mobile silent = config(freePort(), script(marker, "sleep 20").toString(), "x");
        e = expectThrows(MobileServerUnavailableException.class, () -> Mobile.connect(silent));
        assertTrue(e.getMessage().contains("did not answer within 1500 ms"), e.getMessage());
    }
}
