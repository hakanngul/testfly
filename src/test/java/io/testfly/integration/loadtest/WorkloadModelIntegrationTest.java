package io.testfly.integration.loadtest;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.testfly.loadtest.BaseLoadTest;
import io.testfly.loadtest.LoadScenario;
import io.testfly.loadtest.LoadTestMetrics;
import io.testfly.loadtest.internal.GatlingBridge;
import io.testfly.unit.loadtest.WorkloadApi;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.testfly.unit.loadtest.WorkloadApi.atOnce;
import static io.testfly.unit.loadtest.WorkloadApi.closed;
import static io.testfly.unit.loadtest.WorkloadApi.constantConcurrent;
import static io.testfly.unit.loadtest.WorkloadApi.constantRate;
import static io.testfly.unit.loadtest.WorkloadApi.open;
import static io.testfly.unit.loadtest.WorkloadApi.rampConcurrent;
import static org.testng.Assert.*;

/**
 * Workload models (ADR 0004 decision 3, ADR 0003 decision 4) against a real Gatling 3.13.5 run on a local
 * {@link HttpServer}: the server, not TestFly, is the witness of concurrency and arrivals.
 *
 * <p>Every endpoint has its own {@link Probe}: an in-flight counter sampled every 100 ms (timestamped),
 * and the arrival timestamps. Concurrency is judged only inside the window in which the server received
 * traffic (anchored at the first arrival), so the forked Gatling JVM's start-up time does not count.
 *
 * <p>Written before the API existed (red first); the calls go through the {@link WorkloadApi} helper.
 *
 * <p>No class-level {@code @Test}: on a {@link BaseLoadTest} subclass it would turn the inherited public
 * mixin methods into tests. Methods are parallel-safe: each uses its own endpoint, probe and scenario
 * name, and Gatling runs are serialized by the engine itself. The Gatling tests skip without Gatling;
 * the JDK rejection test runs either way.
 */
public class WorkloadModelIntegrationTest extends BaseLoadTest {

    private static final long SAMPLE_MS = 100;
    private static final long SLOW_MS = 300;

    private HttpServer server;
    private ExecutorService serverExecutor;
    private ScheduledExecutorService sampler;
    private String baseUrl;
    private final Map<String, Probe> probes = new ConcurrentHashMap<>();

    @BeforeClass
    public void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // Unbounded pool: the server must never be what caps the observed concurrency.
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        endpoint("/wl-closed-steady", SLOW_MS);
        endpoint("/wl-closed-rampdown", SLOW_MS);
        endpoint("/wl-open-rate", 0);
        endpoint("/wl-atonce-a", 0);
        endpoint("/wl-atonce-b", 0);
        endpoint("/wl-jdk", 0);
        endpoint("/wl-open-ramp", 0);
        endpoint("/wl-open-stress", 0);
        endpoint("/wl-open-pause", 0);
        endpoint("/wl-open-pause-anchor", 0);
        endpoint("/wl-open-ramprate", 0);
        endpoint("/wl-closed-rampup", SLOW_MS);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        sampler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "workload-it-sampler");
            t.setDaemon(true);
            return t;
        });
        sampler.scheduleAtFixedRate(() -> probes.values().forEach(Probe::sample), 0, SAMPLE_MS,
                TimeUnit.MILLISECONDS);
    }

    @AfterClass(alwaysRun = true)
    public void stopServer() {
        if (sampler != null) {
            sampler.shutdownNow();
        }
        if (server != null) {
            server.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
    }

    // ── closed: constantConcurrent(5, 4s) ────────────────────────────────

    @Test
    public void closed_constantConcurrent_holdsFiveInFlight() {
        requireGatling();
        Probe p = probes.get("/wl-closed-steady");
        Object workload = closed(constantConcurrent(5, Duration.ofSeconds(4)));

        LoadTestMetrics m = run("WlClosedSteady", workload, "/wl-closed-steady");

        List<Sample> steady = p.samplesBetween(p.firstArrival(), p.lastArrival());
        assertTrue(steady.size() >= 20, "expected ~40 samples over the 4 s run, got " + steady.size()
                + "; " + p.describe());
        int peak = steady.stream().mapToInt(Sample::inFlight).max().orElse(0);
        assertTrue(peak >= 5, "closed constantConcurrent(5): sampled peak must reach 5, was " + peak
                + "; " + p.describe());
        assertTrue(p.allSamples().stream().allMatch(x -> x.inFlight() <= 6),
                "closed constantConcurrent(5): no sample may exceed 6; " + p.describe());
        long atLeast4 = steady.stream().filter(x -> x.inFlight() >= 4).count();
        assertTrue(atLeast4 >= 0.6 * steady.size(), "at least 60% of steady samples must be >= 4, got "
                + atLeast4 + "/" + steady.size() + "; " + p.describe());
        assertEquals(m.totalRequests(), p.arrivals(), "metrics.totalRequests() vs server-observed count");
    }

    // ── closed: constantConcurrent(5, 3s) then rampConcurrent(5, 0, 3s) ──

    @Test
    public void closed_rampConcurrentToZero_lowersConcurrencyAtTheEnd() {
        requireGatling();
        Probe p = probes.get("/wl-closed-rampdown");
        Object workload = closed(constantConcurrent(5, Duration.ofSeconds(3)),
                rampConcurrent(5, 0, Duration.ofSeconds(3)));

        run("WlClosedRampDown", workload, "/wl-closed-rampdown");

        long start = p.firstArrival();
        long sec = TimeUnit.SECONDS.toNanos(1);
        List<Sample> steadyPhase = p.samplesBetween(start + sec / 2, start + 3 * sec);
        List<Sample> lastSecond = p.samplesBetween(start + 5 * sec, start + 6 * sec);
        assertTrue(steadyPhase.size() >= 15 && lastSecond.size() >= 5,
                "too few samples: steady=" + steadyPhase.size() + " last1s=" + lastSecond.size() + "; "
                        + p.describe());
        assertTrue(p.arrivalsBetween(start + 3 * sec + sec / 3, start + 6 * sec) > 0,
                "the rampConcurrent(5, 0, 3s) phase must still send requests; " + p.describe());
        double steadyMean = mean(steadyPhase);
        double tailMean = mean(lastSecond);
        assertTrue(tailMean < steadyMean, "mean concurrency in the last 1 s of the injection window ("
                + tailMean + ") must be lower than in the steady phase (" + steadyMean + "); " + p.describe());
        assertTrue(tailMean < 3, "mean concurrency in the last 1 s must be below 3, was " + tailMean + "; "
                + p.describe());
    }

    // ── open: constantRate(10, 4s) ───────────────────────────────────────

    @Test
    public void open_constantRate_arrivalsMatchRate_andMetricsMatchServer() {
        requireGatling();
        Probe p = probes.get("/wl-open-rate");
        Object workload = open(constantRate(10, Duration.ofSeconds(4)));

        LoadTestMetrics m = run("WlOpenRate", workload, "/wl-open-rate");

        int arrivals = p.arrivals();
        assertTrue(arrivals >= 34 && arrivals <= 46,
                "open constantRate(10/s, 4s): server arrivals must be 40 +-15% (34..46), was " + arrivals);
        assertEquals(m.totalRequests(), arrivals, "metrics.totalRequests() vs server-observed count");
    }

    // ── open: atOnce(3) ──────────────────────────────────────────────────

    @Test
    public void open_atOnce_runsTheScenarioExactlyThreeTimes() {
        requireGatling();
        Probe a = probes.get("/wl-atonce-a");
        Probe b = probes.get("/wl-atonce-b");
        String name = "WlAtOnce_" + UUID.randomUUID().toString().replace("-", "");
        LoadScenario scenario = LoadScenario.named(name)
                .baseUrl(baseUrl)
                .engine("gatling")
                .step("atOnceA").get("/wl-atonce-a").and()
                .step("atOnceB").get("/wl-atonce-b").and();

        LoadTestMetrics m = WorkloadApi.workload(scenario, open(atOnce(3))).run().metrics();

        assertEquals(a.arrivals(), 3, "each of the 3 users runs step A exactly once");
        assertEquals(b.arrivals(), 3, "each of the 3 users runs step B exactly once");
        assertEquals(m.totalRequests(), 3 * 2, "server count = 3 users x 2 steps");
    }

    // ── open: ramp and stressPeak start an exact number of users ─────────

    @Test
    public void open_ramp_runsTheScenarioExactlyFourTimes() {
        requireGatling();
        Probe p = probes.get("/wl-open-ramp");
        LoadTestMetrics m = run("WlOpenRamp", open(WorkloadApi.ramp(4, Duration.ofSeconds(2))), "/wl-open-ramp");

        assertEquals(p.arrivals(), 4, "ramp(4, 2s) starts exactly 4 users; " + p.describe());
        assertEquals(m.totalRequests(), 4);
    }

    @Test
    public void open_stressPeak_runsTheScenarioExactlyFourTimes() {
        requireGatling();
        Probe p = probes.get("/wl-open-stress");
        LoadTestMetrics m = run("WlOpenStress", open(WorkloadApi.stressPeak(4, Duration.ofSeconds(2))),
                "/wl-open-stress");

        assertEquals(p.arrivals(), 4, "stressPeak(4, 2s) starts exactly 4 users; " + p.describe());
        assertEquals(m.totalRequests(), 4);
    }

    // ── open: nothingFor pauses the injection ────────────────────────────

    /**
     * The server cannot see when the forked JVM starts injecting, so a leading pause is checked by count
     * only. Its timing is checked inside one run instead: an anchor user arrives first, then
     * {@code nothingFor(1s)}, then two users; the server-observed gap must be about a second. This avoids
     * comparing two forked runs whose JVM start-up times differ by hundreds of milliseconds.
     */
    @Test
    public void open_nothingFor_thenAtOnce_runsExactlyTwice_andDelaysArrivals() {
        requireGatling();
        Probe leading = probes.get("/wl-open-pause");
        run("WlOpenPause", open(WorkloadApi.nothingFor(Duration.ofSeconds(1)), atOnce(2)), "/wl-open-pause");
        assertEquals(leading.arrivals(), 2, "nothingFor(1s), atOnce(2): exactly 2 users; " + leading.describe());

        Probe anchored = probes.get("/wl-open-pause-anchor");
        run("WlOpenPauseAnchor", open(atOnce(1), WorkloadApi.nothingFor(Duration.ofSeconds(1)), atOnce(2)),
                "/wl-open-pause-anchor");
        List<Long> t = anchored.sortedArrivals();
        assertEquals(t.size(), 3, "atOnce(1), nothingFor(1s), atOnce(2): exactly 3 users; " + anchored.describe());
        long gapMs = TimeUnit.NANOSECONDS.toMillis(t.get(1) - t.get(0));
        assertTrue(gapMs >= 800, "nothingFor(1s) must delay the next users by ~1 s, gap was " + gapMs + " ms; "
                + anchored.describe());
        assertTrue(TimeUnit.NANOSECONDS.toMillis(t.get(2) - t.get(1)) < 500,
                "atOnce(2) starts both users together; " + anchored.describe());
    }

    // ── open: rampRate ───────────────────────────────────────────────────

    @Test
    public void open_rampRate_arrivalsMatchTheAverageRate() {
        requireGatling();
        Probe p = probes.get("/wl-open-ramprate");
        LoadTestMetrics m = run("WlOpenRampRate", open(WorkloadApi.rampRate(2, 6, Duration.ofSeconds(2))),
                "/wl-open-ramprate");

        // (2 + 6) / 2 users/s x 2 s = 8 arrivals, +-25%
        assertTrue(p.arrivals() >= 6 && p.arrivals() <= 10,
                "rampRate(2 -> 6/s, 2s): server arrivals must be 8 +-25% (6..10), was " + p.describe());
        assertEquals(m.totalRequests(), p.arrivals(), "metrics.totalRequests() vs server-observed count");
    }

    // ── closed: rampConcurrent from 0 ────────────────────────────────────

    @Test
    public void closed_rampConcurrentFromZero_sendsRequests_andStaysAtMostFour() {
        requireGatling();
        Probe p = probes.get("/wl-closed-rampup");
        LoadTestMetrics m = run("WlClosedRampUp", closed(rampConcurrent(0, 3, Duration.ofSeconds(2))),
                "/wl-closed-rampup");

        assertTrue(p.arrivals() > 0, "rampConcurrent(0, 3, 2s) must start users; " + p.describe());
        int peak = p.allSamples().stream().mapToInt(Sample::inFlight).max().orElse(0);
        assertTrue(peak <= 4, "rampConcurrent(0, 3): sampled peak must stay <= 4, was " + peak + "; "
                + p.describe());
        assertEquals(m.totalRequests(), p.arrivals(), "metrics.totalRequests() vs server-observed count");
    }

    // ── JDK engine rejects any workload before sending a request ─────────

    @Test
    public void workloadOnJdkEngine_throwsBeforeAnyRequest() {
        Probe p = probes.get("/wl-jdk");
        LoadScenario scenario = LoadScenario.named("WlJdk_" + UUID.randomUUID().toString().replace("-", ""))
                .baseUrl(baseUrl)
                .engine("jdk")
                .step("jdk").get("/wl-jdk").and();
        LoadScenario withWorkload = WorkloadApi.workload(scenario, closed(constantConcurrent(2, Duration.ofSeconds(1))));

        IllegalArgumentException e = expectThrows(IllegalArgumentException.class, withWorkload::run);

        assertTrue(e.getMessage() != null && e.getMessage().contains("requires the Gatling engine"),
                "message must say the workload needs Gatling, was: " + e.getMessage());
        assertEquals(p.arrivals(), 0, "the JDK engine must reject the workload before any request");
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static void requireGatling() {
        if (!GatlingBridge.isAvailable()) {
            throw new SkipException("Gatling is not available on classpath — skipping workload Gatling tests");
        }
    }

    private LoadTestMetrics run(String prefix, Object workload, String path) {
        String name = prefix + "_" + UUID.randomUUID().toString().replace("-", "");
        LoadScenario scenario = LoadScenario.named(name)
                .baseUrl(baseUrl)
                .engine("gatling")
                .step(prefix).get(path).and();
        return WorkloadApi.workload(scenario, workload).run().metrics();
    }

    private static double mean(List<Sample> samples) {
        return samples.stream().mapToInt(Sample::inFlight).average().orElse(Double.NaN);
    }

    private void endpoint(String path, long sleepMs) {
        Probe probe = new Probe();
        probes.put(path, probe);
        server.createContext(path, ex -> {
            probe.enter();
            boolean exited = false;
            try {
                ex.getRequestBody().readAllBytes();
                if (sleepMs > 0) {
                    Thread.sleep(sleepMs);
                }
                // leave the in-flight count before the response is sent: a closed workload replaces the user
                // as soon as it gets the response, and a later decrement would count both users at once
                probe.exit();
                exited = true;
                send(ex, 200, "{}");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                if (!exited) {
                    probe.exit();
                }
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

    private record Sample(long nanos, int inFlight) {
    }

    /** In-flight counter, its 100 ms samples and the arrival timestamps of one endpoint. */
    private static final class Probe {
        private final AtomicInteger inFlight = new AtomicInteger();
        private final ConcurrentLinkedQueue<Long> arrivals = new ConcurrentLinkedQueue<>();
        private final ConcurrentLinkedQueue<Sample> samples = new ConcurrentLinkedQueue<>();

        void enter() {
            arrivals.add(System.nanoTime());
            inFlight.incrementAndGet();
        }

        void exit() {
            inFlight.decrementAndGet();
        }

        void sample() {
            // Only sample once traffic has started: an idle endpoint would grow the queue for nothing.
            if (!arrivals.isEmpty()) {
                samples.add(new Sample(System.nanoTime(), inFlight.get()));
            }
        }

        int arrivals() {
            return arrivals.size();
        }

        long firstArrival() {
            return arrivals.stream().mapToLong(Long::longValue).min()
                    .orElseThrow(() -> new AssertionError("the server received no request"));
        }

        long lastArrival() {
            return arrivals.stream().mapToLong(Long::longValue).max()
                    .orElseThrow(() -> new AssertionError("the server received no request"));
        }

        List<Long> sortedArrivals() {
            return arrivals.stream().sorted().toList();
        }

        long arrivalsBetween(long fromNanos, long toNanos) {
            return arrivals.stream().filter(t -> t >= fromNanos && t <= toNanos).count();
        }

        List<Sample> allSamples() {
            return new ArrayList<>(samples);
        }

        List<Sample> samplesBetween(long fromNanos, long toNanos) {
            return samples.stream().filter(x -> x.nanos() >= fromNanos && x.nanos() <= toNanos).toList();
        }

        /** Arrivals per second and the sample series (ms since first arrival → in-flight), for diagnostics. */
        String describe() {
            if (arrivals.isEmpty()) {
                return "no arrivals";
            }
            long start = firstArrival();
            Map<Long, Integer> perSecond = new java.util.TreeMap<>();
            for (long t : arrivals) {
                perSecond.merge(TimeUnit.NANOSECONDS.toSeconds(t - start), 1, Integer::sum);
            }
            StringBuilder sb = new StringBuilder("arrivals=").append(arrivals.size())
                    .append(" perSecond=").append(perSecond).append(" samples=[");
            for (Sample x : samples) {
                sb.append(TimeUnit.NANOSECONDS.toMillis(x.nanos() - start)).append(':').append(x.inFlight())
                        .append(' ');
            }
            return sb.append(']').toString();
        }
    }
}
