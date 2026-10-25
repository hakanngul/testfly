package io.testfly.loadtest.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.CheckBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import io.gatling.javaapi.http.HttpRequestActionBuilder;

import java.io.File;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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

    /**
     * Real HTTP status codes per request name, counted for every received response
     * (including responses whose checks fail). Requests without a response are not counted here.
     */
    static final ConcurrentHashMap<String, ConcurrentHashMap<Integer, LongAdder>> STATUS_COUNTS =
            new ConcurrentHashMap<>();

    private final String statusSidecarPath;

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

        statusSidecarPath = cfg.statusSidecarPath;

        // ── HTTP Protocol ──
        HttpProtocolBuilder httpProtocol = http
                .baseUrl(cfg.baseUrl)
                .acceptHeader("application/json")
                .userAgentHeader("TestFly-LoadTest/1.0")
                // Gatling's default warm-up sends a request to https://gatling.io; a warm-up against the
                // target would send an extra, uncounted request to the system under test. Disable both.
                .disableWarmUp()
                .transformResponse((response, session) -> {
                    countStatus(response);
                    return response;
                });

        // ── Build step chain ──
        ChainBuilder stepsChain = buildStepsChain(cfg);

        // ── Scenario with hold-duration loop ──
        ScenarioBuilder scn = scenario(cfg.scenarioName)
                .during(Duration.ofSeconds(cfg.holdSeconds))
                .on(stepsChain);

        // ── Injection: ramp users over rampUp duration ──
        // ── maxDuration: graceful stop (reports are still written) if the run hangs ──
        setUp(
                scn.injectOpen(
                        rampUsers(cfg.users).during(Duration.ofSeconds(cfg.rampUpSeconds))))
                .protocols(httpProtocol)
                .maxDuration(Duration.ofSeconds(cfg.maxDurationSeconds()));
    }

    /**
     * Counts the real status of a received response. Must never throw: a throwing
     * transformer turns the request KO.
     */
    private static void countStatus(io.gatling.http.response.Response response) {
        try {
            String name = response.request().getName();
            STATUS_COUNTS.computeIfAbsent(name != null ? name : "", k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(response.status().code(), k -> new LongAdder())
                    .increment();
        } catch (Throwable ignored) {
            // never fail the request because of bookkeeping
        }
    }

    /** Writes the status counts as JSON ({@code {"request": {"201": n}}}) to the sidecar path. */
    @Override
    public void after() {
        if (statusSidecarPath == null || statusSidecarPath.isBlank()) {
            return;
        }
        Map<String, Map<Integer, Long>> snapshot = new TreeMap<>();
        STATUS_COUNTS.forEach((name, codes) -> {
            Map<Integer, Long> c = new TreeMap<>();
            codes.forEach((code, n) -> c.put(code, n.sum()));
            snapshot.put(name, c);
        });
        try {
            new ObjectMapper().writeValue(new File(statusSidecarPath), snapshot);
        } catch (Exception e) {
            // Parser treats a missing sidecar as "status codes not measured"
            System.err.println("[LoadTest] Could not write status sidecar " + statusSidecarPath + ": " + e);
        }
    }

    private static ChainBuilder buildStepsChain(GatlingRunConfig cfg) {
        ChainBuilder chain = null;

        if (cfg.feeder != null) {
            Iterator<Map<String, Object>> feederIterator = createFeederIterator(cfg.feeder);
            if (feederIterator != null) {
                chain = feed(feederIterator);
            }
        }

        Duration requestTimeout = Duration.ofSeconds(cfg.effectiveRequestTimeoutSeconds());
        for (GatlingRunConfig.GatlingStep step : cfg.steps) {
            HttpRequestActionBuilder req = buildRequest(step).requestTimeout(requestTimeout);

            if (chain == null) {
                chain = exec(req);
            } else {
                chain = chain.exec(req);
            }

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
            chain = exec(http("empty").get("/"));
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
    private static HttpRequestActionBuilder buildRequest(GatlingRunConfig.GatlingStep step) {
        String path = toGatlingEl(step.path);

        // Build base request with method + path
        HttpRequestActionBuilder req = switch (step.method != null ? step.method.toUpperCase() : "GET") {
            case "POST" -> http(step.name).post(path);
            case "PUT" -> http(step.name).put(path);
            case "DELETE" -> http(step.name).delete(path);
            case "PATCH" -> http(step.name).patch(path);
            default -> http(step.name).get(path);
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
