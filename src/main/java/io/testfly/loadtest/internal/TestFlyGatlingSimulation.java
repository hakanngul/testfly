package io.testfly.loadtest.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gatling.javaapi.core.Assertion;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.CheckBuilder;
import io.gatling.javaapi.core.ClosedInjectionStep;
import io.gatling.javaapi.core.OpenInjectionStep;
import io.gatling.javaapi.core.PopulationBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import io.gatling.javaapi.http.HttpRequestActionBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Gatling Simulation subclass that reads its configuration from
 * {@link GatlingRunConfig} — set by {@link GatlingEngine} before
 * {@code Gatling.fromMap()} is invoked.
 *
 * <p>
 * This class is only loaded when Gatling is on the classpath
 * (guarded by {@link GatlingBridge#isAvailable()}).
 */
public class TestFlyGatlingSimulation extends Simulation {

    /** Schema version of the metrics sidecar written in {@link #after()}. */
    static final int METRICS_SCHEMA_VERSION = 2;

    /** Step attempts per request name: incremented by the marker before each step's request. */
    static final ConcurrentHashMap<String, LongAdder> ATTEMPTS = new ConcurrentHashMap<>();
    /** Step outcomes per request name: incremented by the marker after each step's request. */
    static final ConcurrentHashMap<String, LongAdder> OK = new ConcurrentHashMap<>();
    static final ConcurrentHashMap<String, LongAdder> KO = new ConcurrentHashMap<>();

    /**
     * Real HTTP status codes per request name, counted for every received response
     * (including responses whose checks fail and redirect hops, named {@code "<step> Redirect N"}).
     * Requests without a response are not counted here.
     */
    static final ConcurrentHashMap<String, ConcurrentHashMap<Integer, LongAdder>> STATUS_COUNTS =
            new ConcurrentHashMap<>();

    /** Response time histogram per request name: milliseconds → count. */
    static final ConcurrentHashMap<String, ConcurrentHashMap<Long, LongAdder>> LATENCY =
            new ConcurrentHashMap<>();

    private final String metricsSidecarPath;
    /**
     * Each scenario's step request names in declaration order (scenarios in order): the sidecar lists steps in
     * this order, then redirect hops, and maps each scenario to its request names.
     */
    private final Map<String, List<String>> scenarioRequests;
    private final long maxDurationMs;
    private volatile long startMs;

    {
        GatlingRunConfig cfg = GatlingRunConfig.get();
        if (cfg == null) {
            String configFile = System.getProperty("testfly.gatling.config");
            if (configFile != null && new java.io.File(configFile).exists()) {
                try {
                    cfg = GatlingRunConfig.load(new java.io.File(configFile));
                } catch (Exception e) {
                    throw new IllegalStateException("[LoadTest] Failed to load config from " + configFile, e);
                }
            }
        }
        if (cfg == null) {
            throw new IllegalStateException(
                    "[LoadTest] GatlingRunConfig not set. This simulation must be " +
                            "launched via GatlingEngine, not directly.");
        }

        metricsSidecarPath = cfg.metricsSidecarPath;
        maxDurationMs = cfg.maxDurationSeconds() * 1000L;

        Map<String, List<String>> requests = new LinkedHashMap<>();
        List<PopulationBuilder> populations = new ArrayList<>();
        for (GatlingRunConfig.ScenarioConfig sc : cfg.scenarios) {
            List<String> names = new ArrayList<>();
            for (GatlingRunConfig.GatlingStep step : sc.steps) {
                names.add(sc.requestName(step.name));
            }
            requests.put(sc.scenarioName, names.isEmpty()
                    ? List.of(sc.requestName("empty"))
                    : List.copyOf(new LinkedHashSet<>(names)));
            populations.add(population(sc));
        }
        scenarioRequests = requests;

        // ── maxDuration: graceful stop (reports are still written) if the run hangs ──
        SetUp setUp = setUp(populations);
        if (cfg.assertions != null && !cfg.assertions.isEmpty()) {
            // report only: Gatling shows them in index.html and exits with code 2 if one fails
            setUp = setUp.assertions(nativeAssertions(cfg.assertions));
        }
        setUp.maxDuration(Duration.ofSeconds(cfg.maxDurationSeconds()));
    }

    /**
     * Maps TestFly SLA rules to native Gatling assertions (report only; TestFly's own evaluation decides):
     * <ul>
     *   <li>P50..P99, MEAN, MAX → {@code responseTime().percentile(p) / mean() / max()}. Gatling takes these
     *       thresholds as whole milliseconds and its response times are whole milliseconds, so {@code BELOW t}
     *       becomes {@code lt(ceil(t))} and {@code ABOVE t} becomes {@code gt(floor(t))}: the same verdict for
     *       integer values.</li>
     *   <li>ERROR_RATE → {@code failedRequests().percent()} with {@code rate * 100} (a double).</li>
     *   <li>THROUGHPUT → {@code requestsPerSec()} (a double).</li>
     * </ul>
     * Scope: {@code global()}, or {@code details(requestName)} for a step.
     */
    static List<Assertion> nativeAssertions(List<GatlingRunConfig.GatlingAssertion> rules) {
        List<Assertion> out = new ArrayList<>();
        for (GatlingRunConfig.GatlingAssertion a : rules) {
            Assertion.WithPath path = a.requestName == null ? global() : details(a.requestName);
            boolean below = a.below();
            Assertion assertion = switch (a.metric) {
                case "P50", "P75", "P90", "P95", "P99" -> millis(
                        path.responseTime().percentile(Double.parseDouble(a.metric.substring(1))), a.threshold, below);
                case "MEAN" -> millis(path.responseTime().mean(), a.threshold, below);
                case "MAX" -> millis(path.responseTime().max(), a.threshold, below);
                case "ERROR_RATE" -> below
                        ? path.failedRequests().percent().lt(a.threshold * 100.0)
                        : path.failedRequests().percent().gt(a.threshold * 100.0);
                case "THROUGHPUT" -> below
                        ? path.requestsPerSec().lt(a.threshold)
                        : path.requestsPerSec().gt(a.threshold);
                default -> null; // a metric without a native counterpart: TestFly still evaluates it
            };
            if (assertion != null) {
                out.add(assertion);
            }
        }
        return out;
    }

    private static Assertion millis(Assertion.WithPathAndTarget<Integer> target, double threshold, boolean below) {
        if (below) {
            return target.lt((int) Math.min(Integer.MAX_VALUE, Math.max(0, Math.ceil(threshold))));
        }
        return target.gt((int) Math.min(Integer.MAX_VALUE, Math.max(0, Math.floor(threshold))));
    }

    /**
     * One population per scenario, with its own HTTP protocol: the scenario's base URL applies only to it.
     */
    private static PopulationBuilder population(GatlingRunConfig.ScenarioConfig sc) {
        HttpProtocolBuilder httpProtocol = http
                .baseUrl(sc.baseUrl)
                .acceptHeader("application/json")
                .userAgentHeader("TestFly-LoadTest/1.0")
                // Gatling's default warm-up sends a request to https://gatling.io; a warm-up against the
                // target would send an extra, uncounted request to the system under test. Disable both.
                .disableWarmUp()
                .transformResponse((response, session) -> {
                    recordResponse(response);
                    return response;
                });

        ChainBuilder stepsChain = buildStepsChain(sc);

        PopulationBuilder population;
        if (sc.workload != null) {
            // ── Workload model: each user runs the steps once; the injection steps shape the load ──
            ScenarioBuilder scn = scenario(sc.scenarioName).exec(stepsChain);
            population = sc.workload.closed()
                    ? scn.injectClosed(closedInjection(sc.workload))
                    : scn.injectOpen(openInjection(sc.workload));
        } else {
            // ── Legacy: hold-duration loop, users ramped over rampUp ──
            // exitASAP = false: an iteration in progress always completes, so every step attempt gets its
            // outcome marker (the JDK engine also finishes the iteration in progress).
            ScenarioBuilder scn = scenario(sc.scenarioName)
                    .during(Duration.ofSeconds(sc.holdSeconds), false)
                    .on(stepsChain);
            population = scn.injectOpen(rampUsers(sc.users).during(Duration.ofSeconds(sc.rampUpSeconds)));
        }
        return population.protocols(httpProtocol);
    }

    /** Step duration with millisecond precision (at least 1 ms). */
    private static Duration millis(GatlingRunConfig.GatlingWorkloadStep step) {
        return Duration.ofMillis(Math.max(1L, Math.round(step.seconds * 1000.0)));
    }

    static List<OpenInjectionStep> openInjection(GatlingRunConfig.GatlingWorkload workload) {
        List<OpenInjectionStep> steps = new ArrayList<>();
        for (GatlingRunConfig.GatlingWorkloadStep step : workload.steps) {
            Duration d = millis(step);
            steps.add(switch (step.type) {
                case "atOnce" -> atOnceUsers(step.users);
                case "ramp" -> rampUsers(step.users).during(d);
                // a rate of 0 starts nobody: the same as a pause of that length
                case "constantRate" -> step.rate > 0 ? constantUsersPerSec(step.rate).during(d) : nothingFor(d);
                case "rampRate" -> step.fromRate > 0 || step.toRate > 0
                        ? rampUsersPerSec(step.fromRate).to(step.toRate).during(d)
                        : nothingFor(d);
                case "stressPeak" -> stressPeakUsers(step.users).during(d);
                case "nothingFor" -> nothingFor(d);
                default -> throw new IllegalArgumentException("[LoadTest] Unknown open workload step: " + step.type);
            });
        }
        return steps;
    }

    static List<ClosedInjectionStep> closedInjection(GatlingRunConfig.GatlingWorkload workload) {
        List<ClosedInjectionStep> steps = new ArrayList<>();
        for (GatlingRunConfig.GatlingWorkloadStep step : workload.steps) {
            Duration d = millis(step);
            steps.add(switch (step.type) {
                case "constantConcurrent" -> constantConcurrentUsers(step.users).during(d);
                case "rampConcurrent" -> rampConcurrentUsers(step.fromUsers).to(step.toUsers).during(d);
                default -> throw new IllegalArgumentException("[LoadTest] Unknown closed workload step: " + step.type);
            });
        }
        return steps;
    }

    /**
     * Counts the real status and the response time of a received response. Must never throw: a
     * throwing transformer turns the request KO.
     */
    private static void recordResponse(io.gatling.http.response.Response response) {
        try {
            String name = response.request().getName();
            String key = name != null ? name : "";
            STATUS_COUNTS.computeIfAbsent(key, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(response.status().code(), k -> new LongAdder())
                    .increment();
            long ms = Math.max(0L, response.endTimestamp() - response.startTimestamp());
            LATENCY.computeIfAbsent(key, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(ms, k -> new LongAdder())
                    .increment();
        } catch (Throwable ignored) {
            // never fail the request because of bookkeeping
        }
    }

    /**
     * Wraps a step's request in an attempt marker and an outcome marker. Both markers reset the
     * session's failed flag, so the outcome marker sees exactly this request's result (failed check,
     * no response, or a request that could not be built). Markers never throw.
     */
    static ChainBuilder tracked(String stepName, HttpRequestActionBuilder request) {
        final String key = stepName != null ? stepName : "";
        return exec(session -> {
            try {
                ATTEMPTS.computeIfAbsent(key, k -> new LongAdder()).increment();
            } catch (Throwable ignored) {
                // bookkeeping must never fail the virtual user
            }
            return session.markAsSucceeded();
        })
                .exec(request)
                .exec(session -> {
                    try {
                        (session.isFailed() ? KO : OK).computeIfAbsent(key, k -> new LongAdder()).increment();
                    } catch (Throwable ignored) {
                        // bookkeeping must never fail the virtual user
                    }
                    return session.markAsSucceeded();
                });
    }

    /** Records the run start (epoch ms). */
    @Override
    public void before() {
        startMs = System.currentTimeMillis();
    }

    /**
     * Writes {@value GatlingResultsParser#METRICS_SIDECAR_FILE} to {@link GatlingRunConfig#metricsSidecarPath}:
     * <pre>
     * {"schemaVersion":2,"startMs":..,"endMs":..,"maxDurationMs":..,
     *  "scenarios":{"scenario":["request name", ...]},
     *  "requests":{"name":{"attempts":n,"ok":n,"ko":n,"status":{"201":n},"latencyMs":{"12":n}}}}
     * </pre>
     * {@code scenarios} maps each scenario to its step request names ({@code "<scenario> :: <step>"} with more
     * than one scenario). Steps come first in declaration order, then names that only received responses
     * (redirect hops), which have no attempts/ok/ko. {@code startMs} is omitted if {@link #before()} never ran.
     */
    @Override
    public void after() {
        long endMs = System.currentTimeMillis();
        if (metricsSidecarPath == null || metricsSidecarPath.isBlank()) {
            return;
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", METRICS_SCHEMA_VERSION);
        if (startMs > 0) {
            root.put("startMs", startMs);
        } else {
            System.err.println("[LoadTest] WARN: simulation before() hook did not run; run duration not measured");
        }
        root.put("endMs", endMs);
        root.put("maxDurationMs", maxDurationMs);
        root.put("scenarios", scenarioRequests);
        List<String> stepNames = new ArrayList<>();
        scenarioRequests.values().forEach(stepNames::addAll);
        root.put("requests", snapshot(stepNames));
        try {
            Path target = Path.of(metricsSidecarPath);
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            new ObjectMapper().writeValue(tmp.toFile(), root);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            // GatlingEngine fails the run when the sidecar is missing
            System.err.println("[LoadTest] Could not write metrics sidecar " + metricsSidecarPath + ": " + e);
        }
    }

    private static Map<String, Map<String, Object>> snapshot(List<String> stepNames) {
        Set<String> others = new TreeSet<>();
        others.addAll(ATTEMPTS.keySet());
        others.addAll(OK.keySet());
        others.addAll(KO.keySet());
        others.addAll(STATUS_COUNTS.keySet());
        others.addAll(LATENCY.keySet());
        others.removeAll(stepNames);
        Set<String> names = new LinkedHashSet<>();
        for (String step : stepNames) {
            if (ATTEMPTS.containsKey(step) || STATUS_COUNTS.containsKey(step) || LATENCY.containsKey(step)) {
                names.add(step);
            }
        }
        names.addAll(others);

        Map<String, Map<String, Object>> requests = new LinkedHashMap<>();
        for (String name : names) {
            Map<String, Object> entry = new LinkedHashMap<>();
            boolean step = ATTEMPTS.containsKey(name) || OK.containsKey(name) || KO.containsKey(name);
            if (step) {
                entry.put("attempts", sum(ATTEMPTS.get(name)));
                entry.put("ok", sum(OK.get(name)));
                entry.put("ko", sum(KO.get(name)));
            }
            Map<Integer, Long> status = new TreeMap<>();
            STATUS_COUNTS.getOrDefault(name, new ConcurrentHashMap<>()).forEach((c, n) -> status.put(c, n.sum()));
            entry.put("status", status);
            Map<Long, Long> latency = new TreeMap<>();
            LATENCY.getOrDefault(name, new ConcurrentHashMap<>()).forEach((ms, n) -> latency.put(ms, n.sum()));
            entry.put("latencyMs", latency);
            requests.put(name, entry);
        }
        return requests;
    }

    private static long sum(LongAdder adder) {
        return adder != null ? adder.sum() : 0L;
    }

    private static ChainBuilder buildStepsChain(GatlingRunConfig.ScenarioConfig cfg) {
        ChainBuilder chain = null;

        if (cfg.feeder != null) {
            Iterator<Map<String, Object>> feederIterator = createFeederIterator(cfg.feeder);
            if (feederIterator != null) {
                chain = feed(feederIterator);
            }
        }

        Duration requestTimeout = Duration.ofSeconds(cfg.effectiveRequestTimeoutSeconds());
        for (GatlingRunConfig.GatlingStep step : cfg.steps) {
            String requestName = cfg.requestName(step.name);
            HttpRequestActionBuilder req = buildRequest(step, requestName).requestTimeout(requestTimeout);
            ChainBuilder trackedStep = tracked(requestName, req);

            chain = chain == null ? trackedStep : chain.exec(trackedStep);

            // Think time between steps
            if (cfg.thinkTimeFixedMs > 0) {
                chain = chain.pause(Duration.ofMillis(cfg.thinkTimeFixedMs));
            } else if (cfg.thinkTimeMinMs >= 0 && cfg.thinkTimeMaxMs >= 0) {
                chain = chain.pause(
                        Duration.ofMillis(cfg.thinkTimeMinMs),
                        Duration.ofMillis(cfg.thinkTimeMaxMs));
            }
        }

        if (chain == null) {
            chain = tracked(cfg.requestName("empty"), http(cfg.requestName("empty")).get("/"));
        }

        return chain;
    }

    private static Iterator<Map<String, Object>> createFeederIterator(GatlingRunConfig.GatlingFeederConfig fc) {
        if (fc == null) {
            return null;
        }
        String type = fc.type != null ? fc.type.toLowerCase() : "";
        return switch (type) {
            case "sequence" -> {
                final String var = fc.variable != null ? fc.variable : "id";
                final long step = fc.step != null ? fc.step : 1L;
                final java.util.concurrent.atomic.AtomicLong counter = new java.util.concurrent.atomic.AtomicLong(fc.start != null ? fc.start : 0L);
                yield java.util.stream.Stream.generate(() -> Map.<String, Object>of(var, counter.getAndAdd(step))).iterator();
            }
            case "random" -> {
                final String var = fc.variable != null ? fc.variable : "id";
                final int min = fc.min != null ? fc.min : 0;
                final int max = fc.max != null && fc.max >= min ? fc.max : min + 1000;
                yield java.util.stream.Stream.generate(() -> Map.<String, Object>of(var, java.util.concurrent.ThreadLocalRandom.current().nextInt(min, max + 1))).iterator();
            }
            case "uuid" -> {
                final String var = fc.variable != null ? fc.variable : "uuid";
                yield java.util.stream.Stream.generate(() -> Map.<String, Object>of(var, java.util.UUID.randomUUID().toString())).iterator();
            }
            case "constant" -> {
                final String var = fc.variable != null ? fc.variable : "const";
                final String val = fc.value != null ? fc.value : "";
                yield java.util.stream.Stream.generate(() -> Map.<String, Object>of(var, val)).iterator();
            }
            case "csv", "json", "records" -> {
                final List<Map<String, Object>> rows = fc.records != null ? fc.records : List.of();
                if (rows.isEmpty()) {
                    yield java.util.stream.Stream.generate(() -> Map.<String, Object>of()).iterator();
                }
                final java.util.concurrent.atomic.AtomicInteger index = new java.util.concurrent.atomic.AtomicInteger(0);
                yield java.util.stream.Stream.generate(() -> rows.get(Math.floorMod(index.getAndIncrement(), rows.size()))).iterator();
            }
            default -> java.util.stream.Stream.generate(() -> Map.<String, Object>of()).iterator();
        };
    }

    /**
     * Converts a {@link GatlingRunConfig.GatlingStep} into a Gatling
     * {@link HttpRequestActionBuilder}.
     */
    private static HttpRequestActionBuilder buildRequest(GatlingRunConfig.GatlingStep step, String requestName) {
        String path = toGatlingEl(step.path);

        // Build base request with method + path
        HttpRequestActionBuilder req = switch (step.method != null ? step.method.toUpperCase() : "GET") {
            case "POST" -> http(requestName).post(path);
            case "PUT" -> http(requestName).put(path);
            case "DELETE" -> http(requestName).delete(path);
            case "PATCH" -> http(requestName).patch(path);
            default -> http(requestName).get(path);
        };

        // Headers
        for (Map.Entry<String, String> h : step.headers.entrySet()) {
            req = req.header(h.getKey(), toGatlingEl(h.getValue()));
        }

        // Query params
        for (Map.Entry<String, Object> qp : step.queryParams.entrySet()) {
            req = req.queryParam(qp.getKey(), toGatlingEl(String.valueOf(qp.getValue())));
        }

        // Body: JSON content type only when the user did not set one (same rule as the JDK engine)
        if (step.body != null) {
            if (!step.formParams.isEmpty()) {
                throw new IllegalArgumentException("[LoadTest] Step '" + step.name
                        + "': a step cannot have both a body and form parameters");
            }
            req = req.body(StringBody(toGatlingEl(step.body)));
            if (!hasHeader(step, "Content-Type")) {
                req = req.header("Content-Type", "application/json");
            }
        }

        // Form params (Gatling sends them as application/x-www-form-urlencoded)
        for (Map.Entry<String, String> fp : step.formParams.entrySet()) {
            req = req.formParam(fp.getKey(), toGatlingEl(fp.getValue()));
        }

        // Checks: without an explicit status check Gatling applies its default (2xx or 304)
        for (GatlingRunConfig.GatlingCheck check : step.checks) {
            req = req.check(toGatlingCheck(check));
        }

        // Extractions via jsonPath().saveAs() — a missing path makes the request KO
        for (Map.Entry<String, String> ext : step.extractions.entrySet()) {
            req = req.check(jsonPath(toGatlingEl(ext.getValue())).saveAs(ext.getKey()));
        }

        return req;
    }

    /**
     * Converts TestFly's {@code ${var}} syntax to Gatling's EL syntax
     * {@code #{var}}.
     */
    private static String toGatlingEl(String value) {
        if (value == null)
            return null;
        return value.replace("${", "#{");
    }

    private static boolean hasHeader(GatlingRunConfig.GatlingStep step, String name) {
        return step.headers.keySet().stream().anyMatch(name::equalsIgnoreCase);
    }

    /** Maps a serialized check to the Gatling check with the same semantics as the JDK engine. */
    static CheckBuilder toGatlingCheck(GatlingRunConfig.GatlingCheck check) {
        String type = check.type != null ? check.type : "";
        List<String> expected = check.expected != null ? check.expected : List.of();
        return switch (type) {
            case "STATUS_IS" -> status().is(Integer.parseInt(expected.get(0)));
            case "STATUS_IN" -> status().in(expected.stream().map(Integer::valueOf).toArray(Integer[]::new));
            case "JSON_EXISTS" -> jsonPath(requireTarget(check)).exists();
            case "JSON_EQUALS" -> jsonPath(requireTarget(check)).is(expected.get(0));
            case "JSON_IN" -> jsonPath(requireTarget(check)).in(expected.toArray(String[]::new));
            case "HEADER_PRESENT" -> header(requireTarget(check)).exists();
            case "BODY_MATCHES" -> {
                // function form: a '#{' in the regex is never parsed as Gatling EL
                final String regex = expected.get(0);
                yield regex(session -> regex).exists();
            }
            default -> throw new IllegalArgumentException("[LoadTest] Unsupported check type: " + check.type);
        };
    }

    private static String requireTarget(GatlingRunConfig.GatlingCheck check) {
        if (check.target == null || check.target.isBlank()) {
            throw new IllegalArgumentException("[LoadTest] Check " + check.type + " needs a non-blank target");
        }
        return toGatlingEl(check.target);
    }
}
