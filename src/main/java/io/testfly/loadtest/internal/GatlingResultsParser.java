package io.testfly.loadtest.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.testfly.loadtest.LoadTestMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Parses Gatling's output files into {@link LoadTestMetrics}.
 *
 * <p>
 * Gatling writes results to {@code <resultsDir>/<simulation>-<timestamp>/}; this parser reads the
 * aggregated statistics from {@code js/stats.js} (or {@code stats.json}). {@code simulation.log}
 * is a binary, internal format since Gatling 3.13 and is never parsed.
 *
 * <p>HTTP status codes come from the status sidecar ({@value #STATUS_SIDECAR_FILE}) written by
 * {@link TestFlyGatlingSimulation}: real per-response status counts. Requests that got no response
 * (connection refused, timeout) are reported under {@link JdkLoadEngine#TRANSPORT_ERROR_STATUS}.
 * Without a readable sidecar, status codes are reported as not measured (empty map).
 *
 * <p>Percentile slots are pinned by {@link GatlingEngine}: percentiles1..4 = p50/p75/p95/p99.
 * Gatling does not compute p90, so it is {@link Double#NaN}; a missing or {@code "-"} value is
 * also {@code NaN}, never 0.
 */
public final class GatlingResultsParser {

    /** File name of the status-code sidecar inside a run's results directory. */
    public static final String STATUS_SIDECAR_FILE = "testfly-status.json";

    private static final Logger LOGGER = LoggerFactory.getLogger(GatlingResultsParser.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
            .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);

    private GatlingResultsParser() {
    }

    /**
     * Finds the most recent Gatling results directory under {@code resultsDir} and parses it,
     * reading status codes from {@code <resultsDir>/testfly-status.json} if present.
     *
     * @param resultsDir   base results directory (e.g. {@code target/loadtest/<run>})
     * @param scenarioName the scenario name for the metrics record
     * @param users        configured user count
     * @return parsed metrics, or empty metrics if no results found
     */
    public static LoadTestMetrics parse(String resultsDir, String scenarioName, int users) {
        return parse(resultsDir, scenarioName, users, Path.of(resultsDir, STATUS_SIDECAR_FILE));
    }

    /**
     * Same as {@link #parse(String, String, int)} with an explicit status sidecar path.
     *
     * @param statusSidecar the sidecar file; {@code null} or unreadable means "status codes not measured"
     */
    public static LoadTestMetrics parse(String resultsDir, String scenarioName, int users, Path statusSidecar) {
        File baseDir = new File(resultsDir);
        if (!baseDir.exists() || !baseDir.isDirectory()) {
            System.err.println("[LoadTest] Gatling results directory not found: " + resultsDir);
            return LoadTestMetrics.empty(scenarioName);
        }

        // Find the most recent simulation directory
        File[] dirs = baseDir.listFiles(File::isDirectory);
        if (dirs == null || dirs.length == 0) {
            System.err.println("[LoadTest] No Gatling simulation results in: " + resultsDir);
            return LoadTestMetrics.empty(scenarioName);
        }

        File latestDir = Arrays.stream(dirs)
                .max(Comparator.comparingLong(File::lastModified))
                .orElse(null);

        if (latestDir == null) {
            return LoadTestMetrics.empty(scenarioName);
        }

        File statsFile = findStatsJson(latestDir);
        if (statsFile != null && statsFile.exists()) {
            return parseStatsJson(statsFile, scenarioName, users, latestDir.getName(), statusSidecar);
        }

        System.err.println("[LoadTest] No Gatling stats (js/stats.js) found in: " + latestDir);
        return LoadTestMetrics.empty(scenarioName);
    }

    private static File findStatsJson(File dir) {
        // Gatling 3.x with Highcharts: js/stats.json, stats.json, js/stats.js, stats.js
        File jsStats = new File(dir, "js/stats.json");
        if (jsStats.exists())
            return jsStats;
        File stats = new File(dir, "stats.json");
        if (stats.exists())
            return stats;
        File jsStatsJs = new File(dir, "js/stats.js");
        if (jsStatsJs.exists())
            return jsStatsJs;
        File statsJs = new File(dir, "stats.js");
        if (statsJs.exists())
            return statsJs;
        File[] files = dir.listFiles((d, name) -> name.equals("stats.json") || name.equals("stats.js"));
        return files != null && files.length > 0 ? files[0] : null;
    }

    /**
     * Parses Gatling's aggregated stats.json or stats.js.
     */
    private static LoadTestMetrics parseStatsJson(File statsFile, String scenarioName,
            int users, String runDir, Path statusSidecar) {
        try {
            JsonNode root;
            if (statsFile.getName().endsWith(".js")) {
                String text = Files.readString(statsFile.toPath(), java.nio.charset.StandardCharsets.ISO_8859_1);
                int start = text.indexOf('{');
                int end = text.indexOf("function fillStats");
                int lastBrace = end > 0 ? text.lastIndexOf('}', end) : text.lastIndexOf('}');
                if (start >= 0 && lastBrace > start) {
                    root = MAPPER.readTree(text.substring(start, lastBrace + 1));
                } else {
                    root = MAPPER.readTree(statsFile);
                }
            } else {
                root = MAPPER.readTree(statsFile);
            }

            JsonNode stats = root.path("stats");

            int totalReqs = stats.path("numberOfRequests").path("total").asInt(0);
            int koReqs = stats.path("numberOfRequests").path("ko").asInt(0);
            int okReqs = totalReqs - koReqs;

            double minLat = measured(stats.path("minResponseTime"));
            double maxLat = measured(stats.path("maxResponseTime"));
            double meanLat = measured(stats.path("meanResponseTime"));
            double p50 = measured(stats.path("percentiles1"));
            double p75 = measured(stats.path("percentiles2"));
            double p95 = measured(stats.path("percentiles3"));
            double p99 = measured(stats.path("percentiles4"));
            double p90 = Double.NaN; // Gatling does not compute p90 with the pinned slots
            double rps = stats.path("meanNumberOfRequestsPerSecond").path("total").asDouble(
                    stats.path("meanRequestsPerSecond").path("total").asDouble(0));

            double errorRate = totalReqs > 0 ? (double) koReqs / totalReqs : 0;

            long durationMs = stats.path("duration").asLong(0);
            if (durationMs == 0 && rps > 0) {
                durationMs = (long) (totalReqs / rps * 1000);
            }

            // Per-step (per-request) breakdown from "contents"
            Map<String, LoadTestMetrics.StepMetrics> stepMetrics = new LinkedHashMap<>();
            JsonNode contents = root.path("contents");
            if (contents.isObject()) {
                var fields = contents.fields();
                while (fields.hasNext()) {
                    var entry = fields.next();
                    JsonNode stepNode = entry.getValue();
                    String stepName = stepNode.has("name") && !stepNode.path("name").asText().isBlank()
                            ? stepNode.path("name").asText()
                            : entry.getKey();
                    JsonNode stepStats = stepNode.path("stats");

                    int stepTotal = stepStats.path("numberOfRequests").path("total").asInt(0);
                    int stepKo = stepStats.path("numberOfRequests").path("ko").asInt(0);
                    double stepP95 = measured(stepStats.path("percentiles3"));

                    stepMetrics.put(stepName, new LoadTestMetrics.StepMetrics(
                            stepName, stepTotal, stepTotal - stepKo, stepKo,
                            stepP95, stepTotal > 0 ? (double) stepKo / stepTotal : 0));
                }
            }

            Map<Integer, Long> statusCodes = statusCodes(statusSidecar, totalReqs);

            System.out.println("[LoadTest] Gatling results parsed from: " + runDir);

            return new LoadTestMetrics(
                    scenarioName, totalReqs, okReqs, koReqs, rps,
                    meanLat, p50, p90, p95, p99, minLat, maxLat,
                    errorRate, statusCodes, durationMs, users,
                    "gatling", stepMetrics, p75);

        } catch (IOException e) {
            System.err.println("[LoadTest] Failed to parse Gatling stats: " + e.getMessage());
            return LoadTestMetrics.empty(scenarioName);
        }
    }

    /**
     * Reads the {@code total} of a stats node; a missing, non-numeric or {@code "-"} value is
     * {@link Double#NaN} (not measured).
     */
    private static double measured(JsonNode node) {
        JsonNode total = node.path("total");
        if (total.isNumber()) {
            return total.asDouble();
        }
        if (total.isTextual()) {
            try {
                return Double.parseDouble(total.asText().trim());
            } catch (NumberFormatException e) {
                return Double.NaN;
            }
        }
        return Double.NaN;
    }

    /**
     * Sums the sidecar counts over all request names; the difference to Gatling's request total is
     * the number of requests without any HTTP response, reported as status {@code -1}.
     */
    static Map<Integer, Long> statusCodes(Path sidecar, int statsTotal) {
        Map<Integer, Long> codes = new TreeMap<>();
        if (sidecar == null || !Files.isReadable(sidecar)) {
            warnNotMeasured(sidecar, "missing");
            return new LinkedHashMap<>();
        }
        try {
            JsonNode root = MAPPER.readTree(sidecar.toFile());
            if (root == null || !root.isObject()) {
                warnNotMeasured(sidecar, "not a JSON object");
                return new LinkedHashMap<>();
            }
            long responded = 0;
            for (var req = root.fields(); req.hasNext();) {
                for (var code = req.next().getValue().fields(); code.hasNext();) {
                    var e = code.next();
                    long n = e.getValue().asLong(0);
                    if (n > 0) {
                        codes.merge(Integer.parseInt(e.getKey()), n, Long::sum);
                        responded += n;
                    }
                }
            }
            long noResponse = statsTotal - responded;
            if (noResponse > 0) {
                codes.merge(JdkLoadEngine.TRANSPORT_ERROR_STATUS, noResponse, Long::sum);
            } else if (noResponse < 0) {
                // e.g. redirect hops or requests outside the stats; keep the real counts, but say so
                LOGGER.warn("[LoadTest] Gatling status sidecar {} counted {} responses but stats.js reports {} "
                        + "requests; status counts may include responses not in the request total",
                        sidecar, responded, statsTotal);
            }
            return new LinkedHashMap<>(codes);
        } catch (IOException | RuntimeException e) {
            warnNotMeasured(sidecar, e.toString());
            return new LinkedHashMap<>();
        }
    }

    private static void warnNotMeasured(Path sidecar, String reason) {
        LOGGER.warn("[LoadTest] Gatling HTTP status codes not measured: status sidecar {} unreadable ({})",
                sidecar, reason);
    }
}
