package io.testfly.loadtest.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.testfly.loadtest.LoadTestMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Builds {@link LoadTestMetrics} for a Gatling run from TestFly's own metrics sidecar
 * ({@value #METRICS_SIDECAR_FILE}), written by {@link TestFlyGatlingSimulation} into the run's dedicated
 * results directory (ADR 0004, decision 1).
 *
 * <p>Gatling's report files ({@code js/stats.js}) and {@code simulation.log} (binary and internal since
 * Gatling 3.13) are never read.
 *
 * <ul>
 *   <li>Steps are the sidecar entries with {@code attempts}; other entries are redirect hops
 *       ({@code "<step> Redirect N"}). Totals are {@code ok + ko} summed over steps.</li>
 *   <li>Latency percentiles (p50/p75/p90/p95/p99), min, max and mean come from the merged millisecond
 *       histograms of the steps only, with {@link PercentileCalculator#linearInterpolation} semantics.
 *       Without any sample they are {@link Double#NaN}, never 0.</li>
 *   <li>Status codes are summed over all entries, including redirect hops. Step requests without a
 *       response ({@code ok + ko} minus the step's own status counts) are reported under
 *       {@link JdkLoadEngine#TRANSPORT_ERROR_STATUS}.</li>
 *   <li>Throughput is the total divided by the simulation's {@code before()}..{@code after()} duration;
 *       {@link Double#NaN} (not measured) if that duration is missing or not positive.</li>
 *   <li>If attempts and outcomes differ, or the run lasted until Gatling's {@code maxDuration} stopped it,
 *       the result is {@linkplain LoadTestMetrics#partial() partial} and a WARN is logged.</li>
 *   <li>Steps keep the sidecar's order (declaration order).</li>
 * </ul>
 */
public final class GatlingResultsParser {

    /** File name of the TestFly metrics sidecar inside a run's results directory. */
    public static final String METRICS_SIDECAR_FILE = "testfly-metrics.json";

    /** A run this close to the sidecar's {@code maxDurationMs} was stopped by Gatling's {@code maxDuration}. */
    static final long MAX_DURATION_TOLERANCE_MS = 1000;

    /** Oldest and newest sidecar schema versions this parser reads (2 adds the {@code scenarios} map). */
    static final int MIN_SCHEMA_VERSION = 1;
    static final int MAX_SCHEMA_VERSION = 2;

    private static final Logger LOGGER = LoggerFactory.getLogger(GatlingResultsParser.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GatlingResultsParser() {
    }

    /**
     * Parses {@code <resultsDir>/}{@value #METRICS_SIDECAR_FILE}.
     *
     * @param resultsDir   the run's dedicated results directory (e.g. {@code target/loadtest/<run>})
     * @param scenarioName the scenario name for the metrics record
     * @param users        configured user count
     * @return parsed metrics, or empty metrics if the directory or the sidecar is missing or unreadable
     */
    public static LoadTestMetrics parse(String resultsDir, String scenarioName, int users) {
        Path dir = Path.of(resultsDir);
        if (!Files.isDirectory(dir)) {
            LOGGER.warn("[LoadTest] Gatling results directory not found: {}", resultsDir);
            return LoadTestMetrics.empty(scenarioName);
        }
        Path sidecar = dir.resolve(METRICS_SIDECAR_FILE);
        if (!Files.isReadable(sidecar)) {
            LOGGER.warn("[LoadTest] Gatling metrics sidecar missing: {}", sidecar);
            return LoadTestMetrics.empty(scenarioName);
        }
        try {
            return parseSidecar(sidecar, scenarioName, users);
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("[LoadTest] Gatling metrics sidecar unreadable: {} ({})", sidecar, e.toString());
            return LoadTestMetrics.empty(scenarioName);
        }
    }

    /**
     * Parses one metrics sidecar strictly into the metrics of all step entries (the single-scenario result).
     *
     * @throws IOException if the file cannot be read, is not valid JSON, or has an unsupported schema
     */
    static LoadTestMetrics parseSidecar(Path sidecar, String scenarioName, int users) throws IOException {
        return Sidecar.read(sidecar).global(scenarioName, users);
    }

    /**
     * Parses {@code <runDir>/}{@value #METRICS_SIDECAR_FILE} of a multi-scenario run: global metrics named
     * {@code "<simulation> (global)"} and one {@link LoadTestMetrics} per scenario (users 0).
     *
     * @throws IllegalStateException if the sidecar is missing or unreadable
     */
    public static SimulationMetrics parseSimulation(String runDir, String simulationName) {
        return parseSimulation(Path.of(runDir).resolve(METRICS_SIDECAR_FILE), simulationName, Map.of());
    }

    /**
     * Parses a simulation sidecar; {@code usersByScenario} fills {@link LoadTestMetrics#users()} per scenario
     * (missing names count 0) and their sum for the global metrics.
     *
     * @throws IllegalStateException if the sidecar is missing or unreadable
     */
    static SimulationMetrics parseSimulation(Path sidecar, String simulationName, Map<String, Integer> usersByScenario) {
        if (!Files.isReadable(sidecar)) {
            throw new IllegalStateException("[LoadTest] Gatling metrics sidecar missing: " + sidecar);
        }
        try {
            Sidecar sc = Sidecar.read(sidecar);
            int totalUsers = usersByScenario.values().stream().mapToInt(Integer::intValue).sum();
            LoadTestMetrics global = sc.global(simulationName + " (global)", totalUsers);
            Map<String, LoadTestMetrics> perScenario = new LinkedHashMap<>();
            for (String name : sc.scenarios.keySet()) {
                perScenario.put(name, sc.scenario(name, usersByScenario.getOrDefault(name, 0)));
            }
            return new SimulationMetrics(global, perScenario);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("[LoadTest] Gatling metrics sidecar missing: " + sidecar
                    + " (unreadable: " + e.getMessage() + ")", e);
        }
    }

    /** Global and per-scenario metrics of one simulation run. Immutable. */
    public static final class SimulationMetrics {
        private final LoadTestMetrics global;
        private final Map<String, LoadTestMetrics> scenarios;

        SimulationMetrics(LoadTestMetrics global, Map<String, LoadTestMetrics> scenarios) {
            this.global = global;
            this.scenarios = Collections.unmodifiableMap(new LinkedHashMap<>(scenarios));
        }

        /** All step entries together. */
        public LoadTestMetrics global() {
            return global;
        }

        /**
         * One scenario's metrics.
         *
         * @throws IllegalArgumentException for an unknown scenario, and always for a schema-1 sidecar
         *                                  (it has no scenario map)
         */
        public LoadTestMetrics scenario(String name) {
            LoadTestMetrics m = scenarios.get(name);
            if (m == null) {
                throw new IllegalArgumentException("[LoadTest] No scenario '" + name + "' in the Gatling metrics; "
                        + "scenarios: " + scenarios.keySet());
            }
            return m;
        }

        /** Scenario names in run order (empty for a schema-1 sidecar). */
        public Map<String, LoadTestMetrics> scenarios() {
            return scenarios;
        }
    }

    /** A read sidecar: run timing, request entries in file order, and the scenario map (schema 2). */
    private static final class Sidecar {
        final Path path;
        final Map<String, JsonNode> requests = new LinkedHashMap<>();
        final Map<String, List<String>> scenarios = new LinkedHashMap<>();
        boolean durationMeasured;
        long durationMs;
        long maxDurationMs;
        boolean stoppedByMaxDuration;

        private Sidecar(Path path) {
            this.path = path;
        }

        static Sidecar read(Path path) throws IOException {
            JsonNode root = MAPPER.readTree(path.toFile());
            if (root == null || !root.isObject()) {
                throw new IOException("not a JSON object");
            }
            int schema = root.path("schemaVersion").asInt(-1);
            if (schema < MIN_SCHEMA_VERSION || schema > MAX_SCHEMA_VERSION) {
                throw new IOException("unsupported schemaVersion " + root.path("schemaVersion"));
            }
            Sidecar sc = new Sidecar(path);
            long startMs = root.path("startMs").asLong(0);
            long endMs = root.path("endMs").asLong(0);
            sc.durationMeasured = startMs > 0 && endMs > startMs;
            sc.durationMs = sc.durationMeasured ? endMs - startMs : 0L;
            if (!sc.durationMeasured) {
                LOGGER.warn("[LoadTest] Gatling run duration not measured (startMs={}, endMs={}) in {}; "
                        + "throughput is reported as not measured", root.path("startMs"), root.path("endMs"), path);
            }
            sc.maxDurationMs = root.path("maxDurationMs").asLong(0);
            sc.stoppedByMaxDuration = sc.durationMeasured && sc.maxDurationMs > 0
                    && sc.durationMs >= sc.maxDurationMs - MAX_DURATION_TOLERANCE_MS;
            if (sc.stoppedByMaxDuration) {
                LOGGER.warn("[LoadTest] Gatling partial run: stopped by maxDuration after {} ms (limit {} ms); "
                        + "the scenario did not complete, metrics are partial ({})", sc.durationMs, sc.maxDurationMs,
                        path);
            }
            for (Iterator<Map.Entry<String, JsonNode>> it = root.path("requests").fields(); it.hasNext();) {
                Map.Entry<String, JsonNode> e = it.next();
                sc.requests.put(e.getKey(), e.getValue());
            }
            if (schema >= 2) {
                for (Iterator<Map.Entry<String, JsonNode>> it = root.path("scenarios").fields(); it.hasNext();) {
                    Map.Entry<String, JsonNode> e = it.next();
                    List<String> names = new ArrayList<>();
                    e.getValue().forEach(n -> names.add(n.asText()));
                    sc.scenarios.put(e.getKey(), names);
                }
            }
            return sc;
        }

        /** All step entries; step keys are the request names. */
        LoadTestMetrics global(String label, int users) {
            List<String> steps = new ArrayList<>();
            List<String> hops = new ArrayList<>();
            requests.forEach((name, req) -> (req.has("attempts") ? steps : hops).add(name));
            return build(label, users, steps, hops, "");
        }

        /**
         * One scenario: its own step entries and its redirect hops (matched by the {@code "<scenario> :: "}
         * prefix, or every hop when the run has a single scenario); step keys without that prefix.
         */
        LoadTestMetrics scenario(String name, int users) {
            String prefix = scenarios.size() > 1 ? name + LoadScenarioValidator.REQUEST_NAME_SEPARATOR : "";
            List<String> steps = new ArrayList<>();
            for (String request : scenarios.getOrDefault(name, List.of())) {
                JsonNode req = requests.get(request);
                if (req != null && req.has("attempts")) {
                    steps.add(request);
                }
            }
            List<String> hops = new ArrayList<>();
            requests.forEach((request, req) -> {
                if (!req.has("attempts") && request.startsWith(prefix)) {
                    hops.add(request);
                }
            });
            return build(name, users, steps, hops, prefix);
        }

        private LoadTestMetrics build(String label, int users, List<String> stepNames, List<String> hopNames,
                String stripPrefix) {
            long attempts = 0;
            long ok = 0;
            long ko = 0;
            long noResponse = 0;
            TreeMap<Long, Long> merged = new TreeMap<>();
            Map<Integer, Long> statusCodes = new TreeMap<>();
            Map<String, LoadTestMetrics.StepMetrics> steps = new LinkedHashMap<>();

            for (String hop : hopNames) {
                statuses(requests.get(hop), statusCodes);
            }
            for (String name : stepNames) {
                JsonNode req = requests.get(name);
                long responded = statuses(req, statusCodes);
                long stepAttempts = req.path("attempts").asLong(0);
                long stepOk = req.path("ok").asLong(0);
                long stepKo = req.path("ko").asLong(0);
                long stepTotal = stepOk + stepKo;
                attempts += stepAttempts;
                ok += stepOk;
                ko += stepKo;
                noResponse += Math.max(0L, stepTotal - responded);

                TreeMap<Long, Long> histogram = histogram(req.path("latencyMs"));
                histogram.forEach((ms, n) -> merged.merge(ms, n, Long::sum));

                String key = !stripPrefix.isEmpty() && name.startsWith(stripPrefix)
                        ? name.substring(stripPrefix.length()) : name;
                steps.put(key, new LoadTestMetrics.StepMetrics(key, toInt(stepTotal), toInt(stepOk), toInt(stepKo),
                        percentile(histogram, 95), stepTotal > 0 ? (double) stepKo / stepTotal : 0,
                        percentile(histogram, 99)));
            }

            if (noResponse > 0) {
                statusCodes.merge(JdkLoadEngine.TRANSPORT_ERROR_STATUS, noResponse, Long::sum);
            }

            long total = ok + ko;
            boolean partial = attempts != total;
            if (partial) {
                LOGGER.warn("[LoadTest] Gatling partial run ({}): {} requests have no recorded outcome "
                        + "({} attempts, {} outcomes) in {}", label, attempts - total, attempts, total, path);
            }
            partial |= stoppedByMaxDuration;

            long samples = count(merged);
            double throughput = durationMeasured ? total / (durationMs / 1000.0) : Double.NaN;
            double errorRate = total > 0 ? (double) ko / total : 0;

            LOGGER.info("[LoadTest] Gatling results parsed from: {} ({})", path, label);

            return new LoadTestMetrics(
                    label, toInt(total), toInt(ok), toInt(ko), throughput,
                    mean(merged, samples),
                    percentile(merged, 50), percentile(merged, 90), percentile(merged, 95), percentile(merged, 99),
                    samples > 0 ? merged.firstKey() : Double.NaN,
                    samples > 0 ? merged.lastKey() : Double.NaN,
                    errorRate, new LinkedHashMap<>(statusCodes), durationMs, users,
                    "gatling", Collections.unmodifiableMap(steps), percentile(merged, 75), partial);
        }

        /** Adds the entry's positive status counts to {@code into}; returns their sum. */
        private static long statuses(JsonNode req, Map<Integer, Long> into) {
            long responded = 0;
            for (Iterator<Map.Entry<String, JsonNode>> codes = req.path("status").fields(); codes.hasNext();) {
                Map.Entry<String, JsonNode> code = codes.next();
                long n = code.getValue().asLong(0);
                if (n > 0) {
                    into.merge(Integer.parseInt(code.getKey().trim()), n, Long::sum);
                    responded += n;
                }
            }
            return responded;
        }
    }

    /** Reads a {@code {"ms": count}} histogram; non-positive counts are ignored, negative ms clamp to 0. */
    private static TreeMap<Long, Long> histogram(JsonNode node) {
        TreeMap<Long, Long> h = new TreeMap<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext();) {
            Map.Entry<String, JsonNode> e = it.next();
            long n = e.getValue().asLong(0);
            if (n > 0) {
                h.merge(Math.max(0L, Long.parseLong(e.getKey().trim())), n, Long::sum);
            }
        }
        return h;
    }

    private static long count(NavigableMap<Long, Long> histogram) {
        long n = 0;
        for (long c : histogram.values()) {
            n += c;
        }
        return n;
    }

    private static double mean(NavigableMap<Long, Long> histogram, long samples) {
        if (samples == 0) {
            return Double.NaN;
        }
        double sum = 0;
        for (Map.Entry<Long, Long> e : histogram.entrySet()) {
            sum += (double) e.getKey() * e.getValue();
        }
        return sum / samples;
    }

    /**
     * The p-th percentile of the samples described by {@code histogram}: identical to
     * {@link PercentileCalculator#linearInterpolation} on the expanded, sorted samples, computed from
     * cumulative counts. {@link Double#NaN} without samples.
     */
    static double percentile(NavigableMap<Long, Long> histogram, double p) {
        long n = count(histogram);
        if (n == 0) {
            return Double.NaN;
        }
        if (n == 1) {
            return histogram.firstKey();
        }
        double rank = (p / 100.0) * (n - 1);
        long lower = (long) Math.floor(rank);
        long upper = (long) Math.ceil(rank);
        double low = valueAt(histogram, lower);
        if (lower == upper) {
            return low;
        }
        double high = valueAt(histogram, upper);
        return low + (rank - lower) * (high - low);
    }

    /** The value at zero-based {@code index} of the expanded, sorted samples. */
    private static double valueAt(NavigableMap<Long, Long> histogram, long index) {
        long cumulative = 0;
        for (Map.Entry<Long, Long> e : histogram.entrySet()) {
            cumulative += e.getValue();
            if (index < cumulative) {
                return e.getKey();
            }
        }
        return histogram.lastKey();
    }

    private static int toInt(long value) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(Integer.MIN_VALUE, value));
    }
}
