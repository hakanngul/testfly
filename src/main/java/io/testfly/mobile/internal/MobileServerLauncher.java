package io.testfly.mobile.internal;

import io.testfly.config.TestFlyConfig;
import io.testfly.mobile.MobileException;
import io.testfly.mobile.MobileServerUnavailableException;
import io.testfly.mobile.MobileTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Starts {@code mobilecli server start} when a loopback mobilecli server does not answer and
 * {@code mobile.autoStartServer} is on. The binary is the one the user installed
 * ({@code mobile.mobilecliPath}); TestFly never bundles or downloads it (ADR 0002).
 *
 * <p>One start per endpoint and JVM: parallel sessions wait for the same server, and a failed start
 * fails the later sessions fast with the same error. A server TestFly started is stopped when the JVM
 * exits; a server the user started is never touched.
 *
 * <p>While waiting for a started server, a probe that times out counts as "not answering yet", like a
 * refused one; when {@code mobile.timeouts.serverStartMs} runs out the wait fails with a
 * {@link MobileServerUnavailableException} whose cause is the last probe failure.
 */
public final class MobileServerLauncher {

    private static final Logger LOGGER = LoggerFactory.getLogger(MobileServerLauncher.class);
    private static final Duration RETRY_PAUSE = Duration.ofMillis(200);
    /** Below this much time left in the start-up wait, no further {@code server.info} probe is started. */
    public static final Duration MIN_PROBE_BUDGET = Duration.ofMillis(50);
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
            return await(endpoint, process, driver::serverVersion, rpcTimeout, Duration.ofMillis(mobile.getTimeouts().getServerStartMs()), log);
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

    /**
     * Probes {@code endpoint} until it answers, {@code process} exits or {@code startTimeout} runs out.
     *
     * <p>While the server starts, a probe that times out means the same as a refused one: not answering
     * yet. Either is kept as the cause and the wait goes on. No probe starts with less than
     * {@link #MIN_PROBE_BUDGET} left, so a probe cannot fail on its own tiny budget instead of the wait
     * reporting that the server did not answer. Outside start-up the usual rule holds: a slow connect is
     * a {@link MobileTimeoutException}, a refused one a {@link MobileServerUnavailableException}.
     *
     * <p>Public only as a test seam; not part of the API.
     *
     * @param probe calls {@code server.info} within the given budget and returns its version
     * @throws MobileServerUnavailableException when the process exits or the server does not answer in time
     */
    public static String await(URI endpoint, Process process, Function<Duration, String> probe, Duration rpcTimeout,
            Duration startTimeout, Path log) {
        long deadline = System.nanoTime() + startTimeout.toNanos();
        long minProbe = MIN_PROBE_BUDGET.toNanos();
        MobileException last = null;
        while (true) {
            if (!process.isAlive()) {
                return fail(endpoint, process, "mobilecli server exited with code " + process.exitValue() + " before it answered; see " + log, last);
            }
            long left = deadline - System.nanoTime();
            if (left < minProbe) {
                break;
            }
            try {
                return probe.apply(Duration.ofNanos(Math.min(rpcTimeout.toNanos(), left)));
            } catch (MobileServerUnavailableException | MobileTimeoutException e) {
                last = e;
            }
            left = deadline - System.nanoTime();
            if (left < minProbe) {
                break;
            }
            try {
                Thread.sleep(Math.min(RETRY_PAUSE.toMillis(), (left - minProbe) / 1_000_000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new MobileServerUnavailableException("Interrupted while waiting for the mobilecli server at " + endpoint, e);
            }
        }
        return fail(endpoint, process, "mobilecli server did not answer within " + startTimeout.toMillis()
                + " ms (mobile.timeouts.serverStartMs); see " + log, last);
    }

    private static String fail(URI endpoint, Process process, String message, MobileException cause) {
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroy();
        MobileServerUnavailableException failed = new MobileServerUnavailableException(message, cause);
        FAILED.putIfAbsent(endpoint, failed);
        throw failed;
    }
}
