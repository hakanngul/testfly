package io.testfly.reporting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.testfly.config.TestFlyConfig;
import io.testfly.internal.TestFlyContext;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates an Allure-style single-page interactive HTML report driven by JSON data.
 *
 * <p>The report computes cumulative totals across sequential and historical test runs
 * so that tests are never lost when running new tests, and exports both a standalone
 * {@code testfly-report-data.json} and a self-contained {@code testfly-report.html}
 * with embedded JSON for seamless offline viewing.</p>
 */
public final class HtmlReportGenerator {

    private HtmlReportGenerator() {}

    public static void generate() {
        try {
            File jsonFile = ReportPaths.metricsJson();
            if (!jsonFile.exists()) {
                System.err.println("[TestFly] Metrics JSON not found. Skipping HTML report.");
                return;
            }

            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            JsonNode root = mapper.readTree(jsonFile);

            // 1. Build environment / build metadata map and HTML block
            Map<String, Object> envMap = buildEnvironmentMap(root);
            String metadataSection = buildMetadataSection(root);

            // 2. Load run history from metrics-history directory
            List<Map<String, Object>> runHistory = loadRunHistory(mapper);

            // 3. Collect unique cumulative tests across history + current run
            Map<String, Map<String, Object>> cumulativeTestsMap = new LinkedHashMap<>();

            // Historical tests first
            File historyDir = ReportPaths.metricsHistoryDir();
            if (historyDir.exists() && historyDir.isDirectory()) {
                File[] histFiles = historyDir.listFiles((dir, name) -> name.startsWith("testfly-metrics-") && name.endsWith(".json"));
                if (histFiles != null) {
                    Arrays.sort(histFiles, Comparator.comparing(File::getName));
                    for (File hf : histFiles) {
                        try {
                            JsonNode hRoot = mapper.readTree(hf);
                            if (hRoot.has("tests") && hRoot.get("tests").isArray()) {
                                for (JsonNode t : hRoot.get("tests")) {
                                    if (t.has("testId")) {
                                        @SuppressWarnings("unchecked")
                                        Map<String, Object> tMap = mapper.convertValue(t, Map.class);
                                        cumulativeTestsMap.put(t.get("testId").asText(), tMap);
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }

            // Current run tests (updates latest status and attaches base64 screenshot if present)
            List<Map<String, Object>> currentRunTests = new ArrayList<>();
            if (root.has("tests") && root.get("tests").isArray()) {
                for (JsonNode t : root.get("tests")) {
                    if (t.has("testId")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> tMap = mapper.convertValue(t, Map.class);
                        if (t.has("screenshotPath")) {
                            File scFile = new File(t.get("screenshotPath").asText());
                            if (scFile.exists()) {
                                try {
                                    byte[] bytes = Files.readAllBytes(scFile.toPath());
                                    tMap.put("screenshotBase64", "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes));
                                } catch (Exception ignored) {}
                            }
                        }
                        currentRunTests.add(tMap);
                        cumulativeTestsMap.put(t.get("testId").asText(), tMap);
                    }
                }
            }

            // 4. Calculate Cumulative Suite Totals
            int cumTotal = cumulativeTestsMap.size();
            long cumPassed, cumFailed, cumSkipped, cumFlaky, cumDuration;
            double cumPassRate;

            if (cumTotal > 0) {
                cumPassed = cumulativeTestsMap.values().stream().filter(t -> "PASSED".equals(t.get("status"))).count();
                cumFailed = cumulativeTestsMap.values().stream().filter(t -> "FAILED".equals(t.get("status"))).count();
                cumSkipped = cumulativeTestsMap.values().stream().filter(t -> "SKIPPED".equals(t.get("status"))).count();
                cumFlaky = cumulativeTestsMap.values().stream().filter(t -> {
                    Object r = t.get("retryCount");
                    return r instanceof Number && ((Number) r).intValue() > 0;
                }).count();
                cumDuration = cumulativeTestsMap.values().stream().mapToLong(t -> t.get("totalMs") instanceof Number ? ((Number) t.get("totalMs")).longValue() : 0L).sum();
                cumPassRate = Math.round((cumPassed * 1000.0) / cumTotal) / 10.0;
            } else {
                // Fallback to summary root metrics when tests array was empty or omitted
                cumTotal = root.has("totalTests") ? root.get("totalTests").asInt() : 0;
                cumPassed = root.has("passedTests") ? root.get("passedTests").asLong() : 0L;
                cumFailed = root.has("failedTests") ? root.get("failedTests").asLong() : 0L;
                cumSkipped = root.has("skippedTests") ? root.get("skippedTests").asLong() : 0L;
                cumFlaky = root.has("flakyTests") ? root.get("flakyTests").asLong() : 0L;
                cumDuration = root.has("totalTimeMs") ? root.get("totalTimeMs").asLong() : 0L;
                cumPassRate = root.has("passRate") ? root.get("passRate").asDouble() : (cumTotal == 0 ? 0.0 : Math.round((cumPassed * 1000.0) / cumTotal) / 10.0);
            }

            Map<String, Object> cumulativeTotals = new LinkedHashMap<>();
            cumulativeTotals.put("totalTests", cumTotal);
            cumulativeTotals.put("passedTests", cumPassed);
            cumulativeTotals.put("failedTests", cumFailed);
            cumulativeTotals.put("skippedTests", cumSkipped);
            cumulativeTotals.put("flakyTests", cumFlaky);
            cumulativeTotals.put("totalTimeMs", cumDuration);
            cumulativeTotals.put("averageTimeMs", cumTotal == 0 ? 0 : cumDuration / cumTotal);
            cumulativeTotals.put("passRate", cumPassRate);

            String timestamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            // 5. Build full reportData JSON payload
            Map<String, Object> reportData = new LinkedHashMap<>();
            @SuppressWarnings("unchecked")
            Map<String, Object> summaryMap = mapper.convertValue(root, Map.class);
            reportData.put("summary", summaryMap);
            reportData.put("cumulativeTotals", cumulativeTotals);
            reportData.put("environment", envMap);
            reportData.put("tests", new ArrayList<>(cumulativeTestsMap.values()));
            reportData.put("currentRunTests", currentRunTests);
            // Flakiness scores
            Map<String, Object> flakinessData = new LinkedHashMap<>();
            try {
                File flakinessFile = new File("target/flakiness-report.json");
                if (flakinessFile.exists()) {
                    flakinessData = mapper.readValue(flakinessFile, Map.class);
                } else {
                    List<io.testfly.flakiness.FlakinessScore> scores = io.testfly.flakiness.FlakinessAnalyzer.getLastResult();
                    if (scores != null && !scores.isEmpty()) {
                        List<Map<String, Object>> scoreList = new ArrayList<>();
                        long high = 0, watch = 0, stable = 0;
                        for (io.testfly.flakiness.FlakinessScore s : scores) {
                            Map<String, Object> sm = new LinkedHashMap<>();
                            sm.put("testId", s.testId());
                            sm.put("runsAnalysed", s.runsAnalysed());
                            sm.put("failCount", s.failCount());
                            sm.put("failureRate", Math.round(s.failureRate() * 10.0) / 10.0);
                            sm.put("risk", s.risk().name());
                            scoreList.add(sm);
                            if (s.risk() == io.testfly.flakiness.FlakinessScore.Risk.HIGH) high++;
                            else if (s.risk() == io.testfly.flakiness.FlakinessScore.Risk.WATCH) watch++;
                            else stable++;
                        }
                        flakinessData.put("analysedTests", scores.size());
                        flakinessData.put("highRisk", high);
                        flakinessData.put("watch", watch);
                        flakinessData.put("stable", stable);
                        flakinessData.put("scores", scoreList);
                    }
                }
            } catch (Exception ignored) {}

            reportData.put("flakiness", flakinessData);
            reportData.put("history", runHistory);
            reportData.put("runTimestamp", timestamp);

            String reportDataJson = mapper.writeValueAsString(reportData);

            // 6. Export standalone testfly-report-data.json
            File dataJsonFile = ReportPaths.reportDataJson();
            File dataJsonDir = dataJsonFile.getParentFile();
            if (dataJsonDir != null && !dataJsonDir.exists()) {
                dataJsonDir.mkdirs();
            }
            try (FileWriter dataWriter = new FileWriter(dataJsonFile)) {
                dataWriter.write(reportDataJson);
            }

            // 7. Render HTML report
            String html = buildHtml(reportDataJson, metadataSection, cumulativeTotals, timestamp, runHistory.size(), mapper.writeValueAsString(runHistory));

            File reportFile = ReportPaths.htmlReport();
            File reportDir = reportFile.getParentFile();
            if (reportDir != null && !reportDir.exists()) {
                reportDir.mkdirs();
            }
            try (FileWriter writer = new FileWriter(reportFile)) {
                writer.write(html);
            }

            // 8. Archive a timestamped copy in target/reports/
            String fileTimestamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            File reportsHistoryDir = ReportPaths.reportsHistoryDir();
            if (!reportsHistoryDir.exists()) {
                reportsHistoryDir.mkdirs();
            }
            File archiveFile = new File(reportsHistoryDir, "testfly-report-" + fileTimestamp + ".html");
            try (FileWriter archiveWriter = new FileWriter(archiveFile)) {
                archiveWriter.write(html);
            }

            System.out.println("[TestFly] HTML report generated at " + reportFile.getPath());
            System.out.println("[TestFly] JSON report data at      " + dataJsonFile.getPath());
            System.out.println("[TestFly] HTML report archived at  " + archiveFile.getPath());

        } catch (Exception e) {
            System.err.println("[TestFly] HTML report generation failed: " + e.getMessage());
        }
    }

    private static Map<String, Object> buildEnvironmentMap(JsonNode root) {
        String profile = System.getProperty("testfly.profile", "default");
        TestFlyConfig config = TestFlyContext.getConfig();

        TestFlyConfig.Browser browserCfg = config.getBrowser();
        TestFlyConfig.Execution executionCfg = config.getExecution();
        TestFlyConfig.Retry retryCfg = config.getRetry();
        TestFlyConfig.Timeouts timeoutsCfg = config.getTimeouts();

        Map<String, Object> env = new LinkedHashMap<>();
        env.put("profile", profile);
        env.put("browser", (browserCfg != null ? browserCfg.getName() : "unknown") + (browserCfg != null && browserCfg.isHeadless() ? " (headless)" : ""));
        env.put("executionMode", executionCfg != null ? executionCfg.getMode() : "unknown");
        env.put("baseUrl", executionCfg != null && executionCfg.getBaseUrl() != null ? executionCfg.getBaseUrl() : "—");
        env.put("gridUrl", executionCfg != null && executionCfg.getGridUrl() != null ? executionCfg.getGridUrl() : "—");
        env.put("parallel", executionCfg != null ? executionCfg.getParallel() : "none");
        env.put("threadCount", executionCfg != null ? executionCfg.getThreadCount() : 1);
        env.put("maxSessions", executionCfg != null ? executionCfg.getMaxActiveSessions() : 5);
        env.put("retry", retryCfg != null && retryCfg.isEnabled() ? "Enabled (max " + retryCfg.getMaxAttempts() + ")" : "Disabled");
        env.put("explicitTimeout", (timeoutsCfg != null ? timeoutsCfg.getExplicit() : 10) + "s");
        env.put("pageLoadTimeout", (timeoutsCfg != null ? timeoutsCfg.getPageLoad() : 30) + "s");

        if (root.has("ci")) {
            env.put("ci", root.get("ci"));
        }
        return env;
    }

    private static String buildMetadataSection(JsonNode root) {
        String profile = System.getProperty("testfly.profile", "default");
        TestFlyConfig config = TestFlyContext.getConfig();

        TestFlyConfig.Browser browserCfg = config.getBrowser();
        TestFlyConfig.Execution executionCfg = config.getExecution();
        TestFlyConfig.Retry retryCfg = config.getRetry();
        TestFlyConfig.Timeouts timeoutsCfg = config.getTimeouts();

        String browser = browserCfg != null ? browserCfg.getName() : "unknown";
        boolean headless = browserCfg != null && browserCfg.isHeadless();
        String executionMode = executionCfg != null ? executionCfg.getMode() : "unknown";
        String baseUrl = executionCfg != null ? executionCfg.getBaseUrl() : null;
        String gridUrl = executionCfg != null ? executionCfg.getGridUrl() : null;
        String parallel = executionCfg != null ? executionCfg.getParallel() : "none";
        int threadCount = executionCfg != null ? executionCfg.getThreadCount() : 1;
        int maxSessions = executionCfg != null ? executionCfg.getMaxActiveSessions() : 5;
        boolean retryEnabled = retryCfg != null && retryCfg.isEnabled();
        int maxAttempts = retryCfg != null ? retryCfg.getMaxAttempts() : 1;
        int explicitTimeout = timeoutsCfg != null ? timeoutsCfg.getExplicit() : 10;
        int pageLoadTimeout = timeoutsCfg != null ? timeoutsCfg.getPageLoad() : 30;

        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        JsonNode ci = root.has("ci") ? root.get("ci") : null;

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"card metadata-card\" style=\"margin-bottom:24px;\">\n");
        sb.append("  <div class=\"card-header\">Build Metadata</div>\n");
        sb.append("  <div class=\"card-body\">\n");
        sb.append("    <div class=\"meta-grid\">\n");

        appendMetaItem(sb, "Profile", profile);
        appendMetaItem(sb, "Execution Mode", executionMode);
        appendMetaItem(sb, "Browser", browser + (headless ? " (headless)" : ""));
        appendMetaItem(sb, "Base URL", baseUrl != null ? baseUrl : "—");
        appendMetaItem(sb, "Grid URL", gridUrl != null ? gridUrl : "—");
        appendMetaItem(sb, "Parallel", parallel);
        appendMetaItem(sb, "Thread Count", String.valueOf(threadCount));
        appendMetaItem(sb, "Max Sessions", String.valueOf(maxSessions));
        appendMetaItem(sb, "Retry", retryEnabled ? "Enabled (max " + maxAttempts + ")" : "Disabled");
        appendMetaItem(sb, "Explicit Timeout", explicitTimeout + "s");
        appendMetaItem(sb, "Page Load Timeout", pageLoadTimeout + "s");
        appendMetaItem(sb, "CI Provider", ciText(ci, "provider"));
        appendMetaItem(sb, "Build Number", ciText(ci, "buildNumber"));
        appendMetaItem(sb, "Build ID", ciText(ci, "buildId"));
        appendMetaItem(sb, "Branch", ciText(ci, "branch"));
        appendMetaItem(sb, "Commit", ciText(ci, "commitSha"));
        appendMetaItem(sb, "Repository", ciText(ci, "repository"));
        appendMetaItem(sb, "Actor", ciText(ci, "actor"));
        appendMetaItem(sb, "Job Name", ciText(ci, "jobName"));
        appendMetaItem(sb, "Pull Request", ciText(ci, "pullRequest"));
        appendMetaItem(sb, "Agent Name", ciText(ci, "agentName"));
        appendMetaItem(sb, "Environment", ciText(ci, "environment"));
        appendMetaItem(sb, "Generated At", timestamp);

        String buildUrl = ciUrl(ci);
        if (buildUrl != null) {
            sb.append("      <div class=\"meta-item\">\n");
            sb.append("        <span class=\"meta-label\">Build URL</span>\n");
            sb.append("        <span class=\"meta-value\"><a href=\"").append(escapeHtml(buildUrl)).append("\" target=\"_blank\">").append(escapeHtml(buildUrl)).append("</a></span>\n");
            sb.append("      </div>\n");
        }

        sb.append("    </div>\n");
        sb.append("  </div>\n");
        sb.append("</div>\n");

        return sb.toString();
    }

    private static String ciText(JsonNode ci, String field) {
        if (ci == null || !ci.has(field)) return "—";
        String value = ci.get(field).asText();
        return value != null && !value.isBlank() ? value : "—";
    }

    private static String ciUrl(JsonNode ci) {
        if (ci == null || !ci.has("buildUrl")) return null;
        String value = ci.get("buildUrl").asText();
        return value != null && !value.isBlank() ? value : null;
    }

    private static void appendMetaItem(StringBuilder sb, String label, String value) {
        sb.append("      <div class=\"meta-item\">\n");
        sb.append("        <span class=\"meta-label\">").append(label).append("</span>\n");
        sb.append("        <span class=\"meta-value\">").append(value).append("</span>\n");
        sb.append("      </div>\n");
    }

    private static List<Map<String, Object>> loadRunHistory(ObjectMapper mapper) {
        List<Map<String, Object>> list = new ArrayList<>();
        File historyDir = ReportPaths.metricsHistoryDir();
        if (!historyDir.exists() || !historyDir.isDirectory()) {
            return list;
        }
        File[] files = historyDir.listFiles((dir, name) -> name.startsWith("testfly-metrics-") && name.endsWith(".json"));
        if (files == null || files.length == 0) {
            return list;
        }
        Arrays.sort(files, Comparator.comparing(File::getName).reversed());

        int limit = 10;
        try {
            TestFlyConfig cfg = TestFlyContext.getConfig();
            if (cfg != null && cfg.getReporting() != null) {
                limit = cfg.getReporting().getHistoryRuns();
            }
        } catch (Exception ignored) {}

        for (int i = 0; i < Math.min(files.length, limit); i++) {
            File f = files[i];
            try {
                JsonNode h = mapper.readTree(f);
                String fileName = f.getName();
                String rawTs = fileName.replace("testfly-metrics-", "").replace(".json", "");
                String formattedTs = formatHistoryTimestamp(rawTs);

                int tot = h.has("totalTests") ? h.get("totalTests").asInt() : 0;
                int p = h.has("passedTests") ? h.get("passedTests").asInt() : 0;
                int fail = h.has("failedTests") ? h.get("failedTests").asInt() : 0;
                int skip = h.has("skippedTests") ? h.get("skippedTests").asInt() : 0;
                double rate = h.has("passRate") ? h.get("passRate").asDouble() : 0.0;
                long dur = h.has("totalTimeMs") ? h.get("totalTimeMs").asLong() : 0L;

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("rawTimestamp", rawTs);
                item.put("timestamp", formattedTs);
                item.put("totalTests", tot);
                item.put("passedTests", p);
                item.put("failedTests", fail);
                item.put("skippedTests", skip);
                item.put("passRate", rate);
                item.put("totalTimeMs", dur);
                item.put("reportPath", "reports/testfly-report-" + rawTs + ".html");
                list.add(item);
            } catch (Exception ignored) {}
        }
        return list;
    }

    private static String formatHistoryTimestamp(String raw) {
        if (raw == null || raw.length() < 15) return raw;
        try {
            String y = raw.substring(0, 4);
            String m = raw.substring(4, 6);
            String d = raw.substring(6, 8);
            String hr = raw.substring(9, 11);
            String min = raw.substring(11, 13);
            String sec = raw.substring(13, 15);
            return y + "-" + m + "-" + d + " " + hr + ":" + min + ":" + sec;
        } catch (Exception e) {
            return raw;
        }
    }

    private static String buildHtml(String reportDataJson, String metadataSection, Map<String, Object> cumTotals, String timestamp, int historyCount, String runHistoryJson) {
        int cumTotal = (int) cumTotals.getOrDefault("totalTests", 0);
        long cumPassed = (long) cumTotals.getOrDefault("passedTests", 0L);
        long cumFailed = (long) cumTotals.getOrDefault("failedTests", 0L);
        long cumSkipped = (long) cumTotals.getOrDefault("skippedTests", 0L);
        double cumPassRate = (double) cumTotals.getOrDefault("passRate", 0.0);
        long cumDuration = (long) cumTotals.getOrDefault("totalTimeMs", 0L);
        long cumAvg = (long) cumTotals.getOrDefault("averageTimeMs", 0L);

        String passRateClass = cumPassRate >= 80 ? "rate-good" : cumPassRate >= 60 ? "rate-warn" : "rate-bad";
        String passRateStr = String.format("%.1f", cumPassRate);

        try (InputStream is = HtmlReportGenerator.class.getResourceAsStream("/report-template.html")) {
            if (is == null) {
                throw new RuntimeException("report-template.html not found in classpath");
            }
            String template = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            String failureBadge = cumFailed > 0
                    ? "<span class=\"nav-count status-failed\">" + cumFailed + "</span>"
                    : "";

            return template
                    .replace("{{TESTFLY_DATA_JSON}}", reportDataJson)
                    .replace("{{RUN_HISTORY_JSON}}", runHistoryJson)
                    .replace("{{TOTAL_TESTS}}", String.valueOf(cumTotal))
                    .replace("{{PASSED}}", String.valueOf(cumPassed))
                    .replace("{{FAILED}}", String.valueOf(cumFailed))
                    .replace("{{SKIPPED}}", String.valueOf(cumSkipped))
                    .replace("{{PASS_RATE}}", passRateStr)
                    .replace("{{PASS_RATE_CLASS}}", passRateClass)
                    .replace("{{TOTAL_TIME_MS}}", String.valueOf(cumDuration))
                    .replace("{{AVG_TIME_MS}}", String.valueOf(cumAvg))
                    .replace("{{METADATA}}", metadataSection)
                    .replace("{{RUN_TIMESTAMP}}", timestamp)
                    .replace("{{HISTORY_COUNT}}", String.valueOf(historyCount))
                    .replace("{{FAILURE_BADGE}}", failureBadge)
                    .replace("{{ROWS}}", "")
                    .replace("{{FAILURE_ROWS}}", "")
                    .replace("{{SLOWEST_TESTS}}", "")
                    .replace("{{RUN_HISTORY_SECTION}}", "")
                    .replace("{{RETRY_SECTION}}", "")
                    .replace("{{FLAKINESS_SECTION}}", "")
                    .replace("{{DONUT_DATA}}", String.format("{\"passed\":%d,\"failed\":%d,\"skipped\":%d}", cumPassed, cumFailed, cumSkipped))
                    .replace("{{EXECUTION_PERCENTILES}}", "{}");

        } catch (Exception e) {
            throw new RuntimeException("Failed to render HTML report template", e);
        }
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}