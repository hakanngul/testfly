package io.testfly.mobile.internal;

import io.testfly.config.TestFlyConfig;
import io.testfly.mobile.MobileServerUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Starts {@code mobilecli server start} when a loopback mobilecli server does not answer and
 * {@code mobile.autoStartServer} is on. The binary is the one the user installed
 * ({@code mobile.mobilecliPath}); TestFly never bundles or downloads it (ADR 0002).
 *
 * <p>One start per endpoint and JVM: parallel sessions wait for the same server, and a failed start
 * fails the later sessions fast with the same error. A server TestFly started is stopped when the JVM
 * exits; a server the user started is never touched.
 */
public final class MobileServerLauncher {

    private static final Logger LOGGER = LoggerFactory.getLogger(MobileServerLauncher.class);
    private static final Duration RETRY_PAUSE = Duration.ofMillis(200);
    private static final Map<URI, Process> STARTED = new ConcurrentHashMap<>();
    private static final Map<URI, MobileServerUnavailableException> FAILED = new ConcurrentHashMap<>();

    private MobileServerLauncher() {
    }

    /** {@code server.info}'s version, starting the server first when it is down and may be started. */
    public static String serverVersion(TestFlyConfig.Mobile mobile, URI endpoint, MobileCliDriver driver, Duration rpcTimeout) {
        try {
            return driver.serverVersion(rpcTimeout);
        } catch (MobileServerUnavailableException down) {
            if (!mobile.isAutoStartServer() || !MobileServerUrl.isLoopback(endpoint.getHost())) {
                throw down;
            }
            Path log = Path.of("target", "mobilecli-autostart.log").toAbsolutePath();
            Process process;
            synchronized (STARTED) {
                MobileServerUnavailableException failed = FAILED.get(endpoint);
                if (failed != null) {
                    throw failed;
                }
                process = STARTED.get(endpoint);
                if (process == null) {
                    process = start(mobile.getMobilecliPath(), endpoint, log, down);
                    STARTED.put(endpoint, process);
                }
            }
            return await(endpoint, process, driver, rpcTimeout, Duration.ofMillis(mobile.getTimeouts().getServerStartMs()), log);
        }
    }

    private static Process start(String mobilecli, URI endpoint, Path log, MobileServerUnavailableException down) {
        int port = endpoint.getPort() != -1 ? endpoint.getPort() : "https".equalsIgnoreCase(endpoint.getScheme()) ? 443 : 80;
        String listen = endpoint.getHost() + ":" + port;
        try {
            Files.createDirectories(log.getParent());
            Process process = new ProcessBuilder(mobilecli, "server", "start", "--listen", listen)
                    .redirectErrorStream(true)
                    .redirectOutput(log.toFile())
                    .start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                process.descendants().forEach(ProcessHandle::destroy);
                process.destroy();
            }, "testfly-mobilecli-stop"));
            LOGGER.info("[TestFly] mobilecli server was not running; started '{} server start --listen {}' (log: {})",
                    mobilecli, listen, log);
            return process;
        } catch (IOException e) {
            MobileServerUnavailableException failed = new MobileServerUnavailableException(
                    "mobilecli not found at '" + mobilecli + "', so the server at " + endpoint + " could not be started. "
                            + "Install it (npm i -g mobilecli), set mobile.mobilecliPath, or start the server yourself.", e);
            failed.addSuppressed(down);
            FAILED.put(endpoint, failed);
            throw failed;
        }
    }

    private static String await(URI endpoint, Process process, MobileCliDriver driver, Duration rpcTimeout,
            Duration startTimeout, Path log) {
        long deadline = System.nanoTime() + startTimeout.toNanos();
        MobileServerUnavailableException last = null;
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) {
                return fail(endpoint, process, "mobilecli server exited with code " + process.exitValue() + " before it answered; see " + log, last);
            }
            try {
                long left = Math.max(1, (deadline - System.nanoTime()) / 1_000_000);
                return driver.serverVersion(Duration.ofMillis(Math.min(rpcTimeout.toMillis(), left)));
            } catch (MobileServerUnavailableException e) {
                last = e;
            }
            try {
                Thread.sleep(RETRY_PAUSE.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new MobileServerUnavailableException("Interrupted while waiting for the mobilecli server at " + endpoint, e);
            }
        }
        return fail(endpoint, process, "mobilecli server did not answer within " + startTimeout.toMillis()
                + " ms (mobile.timeouts.serverStartMs); see " + log, last);
    }

    private static String fail(URI endpoint, Process process, String message, MobileServerUnavailableException cause) {
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroy();
        MobileServerUnavailableException failed = new MobileServerUnavailableException(message, cause);
        FAILED.putIfAbsent(endpoint, failed);
        throw failed;
    }
}
