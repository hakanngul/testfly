package io.testfly.integration.loadtest;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.testfly.config.TestFlyConfig;
import io.testfly.internal.TestFlyContext;
import io.testfly.loadtest.BaseLoadTest;
import io.testfly.loadtest.LoadScenario;
import io.testfly.loadtest.LoadTestAssert;
import io.testfly.loadtest.LoadTestConfig;
import io.testfly.loadtest.LoadTestMetrics;
import io.testfly.loadtest.Workload;
import io.testfly.loadtest.internal.GatlingBridge;
import io.testfly.unit.loadtest.BaselineApi;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static io.testfly.unit.loadtest.WorkloadApi.atOnce;
import static io.testfly.unit.loadtest.WorkloadApi.open;
import static org.testng.Assert.*;

/**
 * Load-test baselines and regression comparison (ADR 0004 decision 6, Phase 4) against real runs on a local
 * {@link HttpServer} bound to 127.0.0.1. Every endpoint has its own request counter and its own server-side
 * latency, which a test can switch between runs.
 *
 * <p>Written before the API existed (red first); every baseline call goes through the {@link BaselineApi} helper.
 *
 * <p>Run directories: the class points {@code loadtest.resultsDir} at {@value #RESULTS_DIR} (unique to this
 * class) for its lifetime and restores the previous value afterwards, so the per-run
 * {@code testfly-loadtest-result.json} files are found deterministically under
 * {@code <resultsDir>/<scenario-slug>-<millis>/}, with UUID-unique scenario names. No test here reads
 * {@code testfly-metrics.json} or the HTML report.
 *
 * <p>No class-level {@code @Test}: on a {@link BaseLoadTest} subclass it would turn the inherited public mixin
 * methods into tests. Methods are parallel-safe: each uses its own endpoint and scenario name. The Gatling test
 * skips without Gatling.
 */
public class LoadBaselineIntegrationTest extends BaseLoadTest {

    private static final String RESULTS_DIR = "target/loadtest-baseline-it";
    private static final long FAST_MS = 5;
    private static final long SLOW_MS = 200;

    private HttpServer server;
    private ExecutorService serverExecutor;
    private String baseUrl;
    private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> latencies = new ConcurrentHashMap<>();

    private TestFlyConfig.LoadTest previousLoadTest;
    private String previousResultsDir;
    private boolean configPatched;

    /**
     * Points {@code loadtest.resultsDir} at {@value #RESULTS_DIR} by patching the shared {@link TestFlyConfig}. This is
     * safe only because no other test class runs in this JVM at the same time: surefire runs every class in its own
     * fork ({@code reuseForks=false}), and failsafe runs the integration classes one at a time.
     */
    @BeforeClass
    public void isolateResultsDirAndStartServer() throws IOException {
        deleteRecursive(Path.of(RESULTS_DIR));
        TestFlyConfig config = TestFlyContext.getConfig();
        previousLoadTest = config.getLoadTest();
        if (previousLoadTest == null) {
            TestFlyConfig.LoadTest lt = new TestFlyConfig.LoadTest();
            lt.setResultsDir(RESULTS_DIR);
            config.setLoadTest(lt);
        } else {
            previousResultsDir = previousLoadTest.getResultsDir();
            previousLoadTest.setResultsDir(RESULTS_DIR);
        }
        configPatched = true;

        serverExecutor = Executors.newCachedThreadPool();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(serverExecutor);
        endpoint("/bl-fast", FAST_MS);
        endpoint("/bl-slow", FAST_MS);
        endpoint("/bl-artefact", FAST_MS);
        endpoint("/bl-gatling", 0);
        endpoint("/bl-warmup", FAST_MS);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        // A cold first JDK run is visibly slower (p95 ~100 ms vs ~55 ms warm on a loopback round trip of ~50 ms);
        // warming up keeps the baselines below from being inflated by JIT and connection setup.
        jdkScenario(unique("BaselineWarmup"), "/bl-warmup").run();
    }

    @AfterClass(alwaysRun = true)
    public void stopServerAndRestoreConfig() {
        if (server != null) {
            server.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
        if (configPatched) {
            TestFlyConfig config = TestFlyContext.getConfig();
            if (previousLoadTest == null) {
                config.setLoadTest(null);
            } else {
                previousLoadTest.setResultsDir(previousResultsDir);
            }
        }
    }

    // ── JDK: an unchanged system does not regress ────────────────────────

    @Test
    public void jdk_sameEndpointTwice_noRegression_withPercent500() throws IOException {
        requireBaselineApi();
        String name = unique("BaselineFast");
        Path baselineFile = Path.of(RESULTS_DIR, "baselines", name + ".json");

        LoadTestAssert first = jdkScenario(name, "/bl-fast").run();
        int afterFirst = hits.get("/bl-fast").get();
        assertTrue(afterFirst > 0, "the first run must reach the server");
        BaselineApi.save(BaselineApi.of(first.metrics()), baselineFile);
        assertTrue(Files.isRegularFile(baselineFile), "the baseline was saved to " + baselineFile);

        LoadTestAssert second = jdkScenario(name, "/bl-fast").run();
        assertTrue(hits.get("/bl-fast").get() > afterFirst, "the second run must reach the server");

        // this test is about latency drift: the error rate gets a delta that cannot fail on a loopback hiccup
        Object tolerance = BaselineApi.errorRateDelta(BaselineApi.percent(500), 1);
        Object returned = BaselineApi.assertNoRegression(second, BaselineApi.load(baselineFile), tolerance);

        assertSame(returned, second, "no regression: assertNoRegression returns this");
        for (Object r : BaselineApi.regressionResults(second, BaselineApi.load(baselineFile), tolerance)) {
            assertFalse(BaselineApi.regressed(r), "first " + first.metrics() + ", second " + second.metrics()
                    + ": " + r);
        }
    }

    // ── JDK: a slower endpoint regresses p95 ─────────────────────────────

    @Test
    public void jdk_slowSecondRun_failsWithPercent50_namingP95() throws IOException {
        requireBaselineApi();
        String name = unique("BaselineSlow");
        Path baselineFile = Path.of(RESULTS_DIR, "baselines", name + ".json");

        LoadTestAssert fast = jdkScenario(name, "/bl-slow").run();
        BaselineApi.save(BaselineApi.of(fast.metrics()), baselineFile);
        assertTrue(fast.metrics().p95LatencyMs() * 1.5 < SLOW_MS,
                "precondition: the fast run's p95 x 1.5 (the percent(50) limit) is below the slow endpoint's "
                        + SLOW_MS + " ms, otherwise the slow run could not exceed it: " + fast.metrics());

        latencies.get("/bl-slow").set(SLOW_MS);
        LoadTestAssert slow = jdkScenario(name, "/bl-slow").run();
        assertTrue(slow.metrics().p95LatencyMs() >= SLOW_MS,
                "precondition: the server-side switch slowed the second run: " + slow.metrics());
        Object baseline = BaselineApi.load(baselineFile);

        AssertionError e = expectThrown(AssertionError.class,
                () -> BaselineApi.assertNoRegression(slow, baseline, BaselineApi.percent(50)));

        assertTrue(String.valueOf(e.getMessage()).contains("p95"), "the failure names p95: " + e.getMessage());
        Object p95 = BaselineApi.resultFor(
                BaselineApi.regressionResults(slow, baseline, BaselineApi.percent(50)), "p95");
        assertTrue(BaselineApi.regressed(p95), p95.toString());
        assertEquals(BaselineApi.baseline(p95), fast.metrics().p95LatencyMs(), 0.0, "baseline p95 from the file");
        assertEquals(BaselineApi.actual(p95), slow.metrics().p95LatencyMs(), 0.0, "actual p95 of the slow run");
    }

    // ── JDK: per-run artefact ────────────────────────────────────────────

    @Test
    public void jdk_eachRun_writesResultFile_intoItsOwnRunDir_andItLoads() throws IOException {
        requireBaselineApi();
        String name = unique("BaselineArtefact JDK");
        LoadScenario firstScenario = jdkScenario(name, "/bl-artefact");

        firstScenario.run();
        LoadTestAssert second = jdkScenario(name, "/bl-artefact").run();

        List<Path> runDirs = runDirs(firstScenario, name);
        assertEquals(runDirs.size(), 2, "one <slug>-<millis> run dir per JDK run: " + runDirs);
        for (Path dir : runDirs) {
            assertTrue(Files.isRegularFile(dir.resolve(BaselineApi.RESULT_FILE)),
                    BaselineApi.RESULT_FILE + " in " + dir);
        }
        Object loaded = BaselineApi.load(runDirs.get(1).resolve(BaselineApi.RESULT_FILE));
        LoadTestMetrics m = second.metrics();
        assertEquals(BaselineApi.scenarioName(loaded), name);
        assertEquals(BaselineApi.engine(loaded), "jdk");
        assertEquals(BaselineApi.p95(loaded), OptionalDouble.of(m.p95LatencyMs()), "the newest dir is the second run");
        assertEquals(BaselineApi.p99(loaded), OptionalDouble.of(m.p99LatencyMs()));
        assertEquals(BaselineApi.mean(loaded), OptionalDouble.of(m.meanLatencyMs()));
        assertEquals(BaselineApi.errorRate(loaded), OptionalDouble.of(m.errorRate()));
        assertEquals(BaselineApi.throughput(loaded), OptionalDouble.of(m.throughputRps()));
    }

    // ── Gatling: per-run artefact ────────────────────────────────────────

    @Test
    public void gatling_run_writesResultFile_intoItsRunDir_andItLoads() throws IOException {
        if (!GatlingBridge.isAvailable()) {
            throw new SkipException("Gatling is not available on classpath — skipping the Gatling baseline test");
        }
        requireBaselineApi();
        String name = unique("BaselineGatling");
        LoadScenario scenario = LoadScenario.named(name)
                .baseUrl(baseUrl)
                .engine("gatling")
                .step("get").get("/bl-gatling").and()
                .workload((Workload) open(atOnce(5)));

        LoadTestAssert result = scenario.run();

        assertEquals(hits.get("/bl-gatling").get(), 5, "atOnce(5)");
        List<Path> runDirs = runDirs(scenario, name);
        assertEquals(runDirs.size(), 1, "one Gatling run dir: " + runDirs);
        Path file = runDirs.get(0).resolve(BaselineApi.RESULT_FILE);
        assertTrue(Files.isRegularFile(file), BaselineApi.RESULT_FILE + " in the Gatling run dir " + runDirs.get(0));
        Object loaded = BaselineApi.load(file);
        assertEquals(BaselineApi.scenarioName(loaded), name);
        assertEquals(BaselineApi.engine(loaded), "gatling");
        assertEquals(BaselineApi.p95(loaded), OptionalDouble.of(result.metrics().p95LatencyMs()));
        assertEquals(BaselineApi.errorRate(loaded), OptionalDouble.of(result.metrics().errorRate()));
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /**
     * Fails fast with {@link UnsupportedOperationException} while the baseline API is missing, before any load
     * runs, so a red run never reports a file assertion ({@link AssertionError}) for an absent API.
     */
    private static void requireBaselineApi() {
        BaselineApi.type(BaselineApi.LOAD_BASELINE);
        BaselineApi.type(BaselineApi.TOLERANCE);
        BaselineApi.type(BaselineApi.REGRESSION_RESULT);
    }

    /**
     * Runs {@code call} and returns the {@code expected} throwable it throws. Unlike TestNG's {@code expectThrows},
     * any other throwable (notably the {@link UnsupportedOperationException} of a missing API) propagates
     * unchanged, so a red test fails because the API is absent, not with an {@link AssertionError}.
     */
    private static <T extends Throwable> T expectThrown(Class<T> expected, Runnable call) {
        try {
            call.run();
        } catch (Throwable t) {
            if (expected.isInstance(t)) {
                return expected.cast(t);
            }
            throw t;
        }
        throw new AssertionError("expected " + expected.getSimpleName() + " to be thrown, but nothing was thrown");
    }

    /** A short closed JDK run: 2 users, no ramp-up, 1 s hold, no cooldown. */
    private LoadScenario jdkScenario(String name, String path) {
        return LoadScenario.named(name)
                .baseUrl(baseUrl)
                .engine("jdk")
                .users(2)
                .rampUp(Duration.ofSeconds(0))
                .hold(Duration.ofSeconds(1))
                .cooldown(Duration.ofSeconds(0))
                .step("get").get(path).and();
    }

    /** Every {@code <resultsDir>/<scenario-slug>-<millis>} of this (UUID-unique) scenario, oldest first. */
    private List<Path> runDirs(LoadScenario scenario, String name) throws IOException {
        Path resultsDir = Path.of(LoadTestConfig.resolveFor(scenario, getClass(), null).getResultsDir())
                .toAbsolutePath();
        assertEquals(resultsDir, Path.of(RESULTS_DIR).toAbsolutePath(), "the class-unique results dir is in effect");
        String prefix = name.replaceAll("[^a-zA-Z0-9_-]", "_") + "-";
        if (!Files.isDirectory(resultsDir)) {
            return List.of();
        }
        try (Stream<Path> dirs = Files.list(resultsDir)) {
            return dirs.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith(prefix))
                    .filter(p -> p.getFileName().toString().substring(prefix.length()).matches("\\d+"))
                    .sorted(Comparator.comparingLong(p -> Long.parseLong(
                            p.getFileName().toString().substring(prefix.length()))))
                    .toList();
        }
    }

    private static String unique(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private void endpoint(String path, long sleepMs) {
        AtomicInteger counter = new AtomicInteger();
        AtomicLong latency = new AtomicLong(sleepMs);
        hits.put(path, counter);
        latencies.put(path, latency);
        server.createContext(path, ex -> {
            counter.incrementAndGet();
            try {
                ex.getRequestBody().readAllBytes();
                long ms = latency.get();
                if (ms > 0) {
                    Thread.sleep(ms);
                }
                send(ex, 200, "{}");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private static void send(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void deleteRecursive(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> files = Files.walk(dir)) {
            files.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
