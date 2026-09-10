package io.testfly.loadtest.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.testfly.loadtest.LoadTestMetrics;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses Gatling's output files into {@link LoadTestMetrics}.
 *
 * <p>
 * Gatling writes results to {@code <resultsDir>/<simulation>-<timestamp>/}:
 * <ul>
 * <li>{@code simulation.log} — raw event log (RUN, USER, REQUEST, etc.)</li>
 * <li>{@code js/stats.json} or {@code stats.json} — aggregated statistics (with
 * Highcharts)</li>
 * <li>{@code index.html} — Gatling HTML report</li>
 * </ul>
 *
 * <p>
 * This parser reads the aggregated stats JSON when available, falling back
 * to parsing {@code simulation.log} for raw request timings.
 */
public final class GatlingResultsParser {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
            .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);

    private GatlingResultsParser() {
    }

    /**
     * Finds the most recent Gatling results directory and parses it.
     *
     * @param resultsDir   base results directory (e.g. {@code target/loadtest})
     * @param scenarioName the scenario name for the metrics record
     * @param users        configured user count
     * @return parsed metrics, or empty metrics if no results found
     */
    public static LoadTestMetrics parse(String resultsDir, String scenarioName, int users) {
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

        // Try stats.json / stats.js first (Highcharts bundle)
        File statsFile = findStatsJson(latestDir);
        if (statsFile != null && statsFile.exists()) {
            return parseStatsJson(statsFile, scenarioName, users, latestDir.getName());
        }

        // Fallback: parse simulation.log
        File simLog = new File(latestDir, "simulation.log");
        if (simLog.exists()) {
            return parseSimulationLog(simLog, scenarioName, users);
        }

        System.err.println("[LoadTest] No Gatling results found in: " + latestDir);
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
        // Search recursively
        File[] files = dir.listFiles((d, name) -> name.equals("stats.json") || name.equals("stats.js"));
        return files != null && files.length > 0 ? files[0] : null;
    }

    /**
     * Parses Gatling's aggregated stats.json or stats.js.
     */
    private static LoadTestMetrics parseStatsJson(File statsFile, String scenarioName,
            int users, String runDir) {
        try {
            JsonNode root;
            if (statsFile.getName().endsWith(".js")) {
                String text = java.nio.file.Files.readString(statsFile.toPath(), java.nio.charset.StandardCharsets.ISO_8859_1);
                int start = text.indexOf('{');
                int end = text.indexOf("function fillStats");
                int lastBrace = end > 0 ? text.lastIndexOf('}', end) : text.lastIndexOf('}');
                if (start >= 0 && lastBrace > start) {
                    String json = text.substring(start, lastBrace + 1);
                    root = MAPPER.readTree(json);
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

            double minLat = Math.max(0, stats.path("minResponseTime").path("total").asDouble(0));
            double maxLat = Math.max(0, stats.path("maxResponseTime").path("total").asDouble(0));
            double meanLat = Math.max(0, stats.path("meanResponseTime").path("total").asDouble(0));
            double p50 = stats.path("percentiles1").path("total").asDouble(0);
            double p95 = stats.path("percentiles3").path("total").asDouble(0);
            double p99 = stats.path("percentiles4").path("total").asDouble(0);
            double rps = stats.path("meanNumberOfRequestsPerSecond").path("total").asDouble(
                    stats.path("meanRequestsPerSecond").path("total").asDouble(0));

            // p90 is not directly in Gatling stats — approximate between p50 and p95
            double p90 = p50 + (p95 - p50) * 0.8;

            double errorRate = totalReqs > 0 ? (double) koReqs / totalReqs : 0;

            // Duration from the run directory name or stats
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
                    double stepP95 = stepStats.path("percentiles3").path("total").asDouble(0);

                    stepMetrics.put(stepName, new LoadTestMetrics.StepMetrics(
                            stepName, stepTotal, stepTotal - stepKo, stepKo,
                            stepP95, stepTotal > 0 ? (double) stepKo / stepTotal : 0));
                }
            }

            File simLog = new File(statsFile.getParentFile(), "simulation.log");
            if (!simLog.exists() && statsFile.getParentFile() != null && statsFile.getParentFile().getParentFile() != null) {
                simLog = new File(statsFile.getParentFile().getParentFile(), "simulation.log");
            }
            Map<Integer, Long> statusCodes = parseStatusCodes(simLog);
            if (statusCodes.isEmpty()) {
                if (okReqs > 0) statusCodes.put(200, (long) okReqs);
                if (koReqs > 0) statusCodes.put(500, (long) koReqs);
            }

            System.out.println("[LoadTest] Gatling results parsed from: " + runDir);

            return new LoadTestMetrics(
                    scenarioName, totalReqs, okReqs, koReqs, rps,
                    meanLat, p50, p90, p95, p99, minLat, maxLat,
                    errorRate, statusCodes, durationMs, users,
                    "gatling", stepMetrics);

        } catch (IOException e) {
            System.err.println("[LoadTest] Failed to parse Gatling stats: " + e.getMessage());
            return LoadTestMetrics.empty(scenarioName);
        }
    }

    /**
     * Fallback parser for Gatling's simulation.log when stats.json is unavailable.
     *
     * <p>
     * Log format (tab-separated):
     * 
     * <pre>
     * REQUEST	ScenarioName	userId	startTimestamp	endTimestamp	OK|KO	message
     * </pre>
     */
    private static LoadTestMetrics parseSimulationLog(File simLog, String scenarioName, int users) {
        try {
            var lines = java.nio.file.Files.readAllLines(simLog.toPath(), java.nio.charset.StandardCharsets.ISO_8859_1);
            var latencies = new java.util.ArrayList<Double>();
            var statusCounts = new LinkedHashMap<Integer, java.util.concurrent.atomic.AtomicLong>();
            int total = 0, failed = 0;
            long minTime = Long.MAX_VALUE, maxTime = 0;

            for (String line : lines) {
                if (!line.startsWith("REQUEST"))
                    continue;
                String[] parts = line.split("\t");
                if (parts.length < 7)
                    continue;

                total++;
                long start = Long.parseLong(parts[3].trim());
                long end = Long.parseLong(parts[4].trim());
                String status = parts[5].trim();

                double latency = end - start;
                latencies.add(latency);

                if ("KO".equalsIgnoreCase(status))
                    failed++;

                minTime = Math.min(minTime, start);
                maxTime = Math.max(maxTime, end);

                // Extract HTTP status code from message if present
                String msg = parts.length > 6 ? parts[6].trim() : "";
                if (msg.matches("\\d{3}.*")) {
                    int code = Integer.parseInt(msg.substring(0, 3));
                    statusCounts.computeIfAbsent(code, k -> new java.util.concurrent.atomic.AtomicLong(0))
                            .incrementAndGet();
                }
            }

            double[] sorted = latencies.stream().mapToDouble(Double::doubleValue).sorted().toArray();
            long durationMs = maxTime > minTime ? maxTime - minTime : 0;
            double throughput = durationMs > 0 ? (total * 1000.0) / durationMs : 0;

            Map<Integer, Long> statusMap = new LinkedHashMap<>();
            statusCounts.forEach((k, v) -> statusMap.put(k, v.get()));

            return new LoadTestMetrics(
                    scenarioName, total, total - failed, failed, throughput,
                    JdkLoadEngine.mean(sorted),
                    JdkLoadEngine.percentile(sorted, 50),
                    JdkLoadEngine.percentile(sorted, 90),
                    JdkLoadEngine.percentile(sorted, 95),
                    JdkLoadEngine.percentile(sorted, 99),
                    sorted.length > 0 ? sorted[0] : 0,
                    sorted.length > 0 ? sorted[sorted.length - 1] : 0,
                    total > 0 ? (double) failed / total : 0,
                    statusMap, durationMs, users, "gatling", Map.of());

        } catch (Exception e) {
            System.err.println("[LoadTest] Failed to parse simulation.log: " + e.getMessage());
            return LoadTestMetrics.empty(scenarioName);
        }
    }

    private static final java.util.regex.Pattern STATUS_CODE_PATTERN =
            java.util.regex.Pattern.compile("(?:found\\s+|status\\s+)?([1-5]\\d{2})");

    private static Map<Integer, Long> parseStatusCodes(File simLog) {
        Map<Integer, Long> codes = new LinkedHashMap<>();
        if (simLog == null || !simLog.exists())
            return codes;
        try {
            for (String line : java.nio.file.Files.readAllLines(simLog.toPath(), java.nio.charset.StandardCharsets.ISO_8859_1)) {
                if (!line.startsWith("REQUEST"))
                    continue;
                String[] parts = line.split("\t");
                if (parts.length >= 6) {
                    String status = parts[5].trim();
                    String msg = parts.length > 6 ? parts[6].trim() : "";
                    if ("OK".equalsIgnoreCase(status)) {
                        codes.merge(200, 1L, Long::sum);
                    } else {
                        java.util.regex.Matcher m = STATUS_CODE_PATTERN.matcher(msg);
                        if (m.find()) {
                            int code = Integer.parseInt(m.group(1));
                            codes.merge(code, 1L, Long::sum);
                        } else {
                            codes.merge(500, 1L, Long::sum);
                        }
                    }
                }
            }
        } catch (IOException ignored) {
        }
        return codes;
    }
}
