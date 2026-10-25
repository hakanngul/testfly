package io.testfly.loadtest.internal;

import io.testfly.loadtest.CheckCondition;
import io.testfly.loadtest.LoadScenario;
import io.testfly.loadtest.LoadStep;
import io.testfly.loadtest.LoadTestConfig;
import io.testfly.loadtest.LoadTestFeeder;
import io.testfly.loadtest.LoadTestMetrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JDK-based load-test engine — zero external dependencies.
 *
 * <p>
 * Uses {@link HttpClient} (JDK 11+) for HTTP requests and
 * {@link ExecutorService} for concurrent virtual-user simulation.
 * Each virtual user runs in its own thread, executing the scenario's steps
 * in a loop for the configured hold duration.
 *
 * <h3>Execution phases</h3>
 * <ol>
 * <li><b>Ramp-up</b> — users are started evenly over {@code rampUp}</li>
 * <li><b>Hold</b> — each user executes the steps in a loop for {@code hold}, counted from its own start</li>
 * </ol>
 * {@code cooldown} is accepted but not applied (ADR-0003).
 *
 * <h3>Request outcome</h3>
 * <p>
 * Every sent request is counted. It is KO when there is no HTTP response (status {@code -1}),
 * when the default status check fails (no explicit status check and status not 2xx/304), when any
 * check fails, or when an {@code extract(..)} path is not found — the same semantics as the Gatling
 * engine. Any other error (e.g. a malformed URI) aborts the run with an {@link IllegalStateException}.
 *
 * <h3>Metrics collection</h3>
 * <p>
 * Per-request latency is recorded in synchronized lists. After execution, percentiles
 * (p50, p75, p90, p95, p99) are computed with linear interpolation ({@link PercentileCalculator}).
 *
 * <h3>Thread safety</h3>
 * <p>
 * All mutable state (counters, latency lists) uses atomic or concurrent
 * collections. The engine instance itself is stateless between executions.
 */
public final class JdkLoadEngine implements LoadTestEngine {

    /**
     * Synthetic status code recorded in {@code statusCodes} for requests that never
     * received an HTTP response (connection refused, timeout, I/O error).
     */
    public static final int TRANSPORT_ERROR_STATUS = -1;

    @Override
    public String name() {
        return "jdk";
    }

    @Override
    public boolean isAvailable() {
        return true; // JDK HttpClient is always available on Java 21+
    }

    @Override
    public LoadTestMetrics execute(LoadScenario scenario, LoadTestConfig config) {
        int users = config.getUsers();
        long rampUpSeconds = config.getRampUpSeconds();
        long holdSeconds = config.getHoldSeconds();
        long cooldownSeconds = config.getCooldownSeconds();
        int timeoutSeconds = config.getRequestTimeoutSeconds();
        String baseUrl = config.getBaseUrl();

        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(
                    "[LoadTest] No baseUrl configured. Set loadtest.baseUrl in testfly.yml " +
                            "or @LoadTest(baseUrl = \"...\") or .baseUrl(\"...\") on the scenario.");
        }

        LoadScenarioValidator.validate(scenario, name());
        List<LoadStep> steps = scenario.steps();

        LoadTestFeeder feeder = scenario.feeder();
        long thinkMinMs = scenario.thinkTimeMinMs();
        long thinkMaxMs = scenario.thinkTimeMaxMs();
        Duration thinkFixed = scenario.thinkTimeFixed();

        // ── Metrics accumulators (thread-safe) ──
        AtomicInteger totalRequests = new AtomicInteger(0);
        AtomicInteger successRequests = new AtomicInteger(0);
        AtomicInteger failedRequests = new AtomicInteger(0);
        ConcurrentHashMap<Integer, AtomicLong> statusCounts = new ConcurrentHashMap<>();
        List<double[]> latencies = Collections.synchronizedList(new ArrayList<>());
        Map<String, List<double[]>> stepLatencies = new ConcurrentHashMap<>();
        Map<String, AtomicInteger> stepTotal = new ConcurrentHashMap<>();
        Map<String, AtomicInteger> stepFailed = new ConcurrentHashMap<>();

        for (LoadStep step : steps) {
            stepLatencies.put(step.name(), Collections.synchronizedList(new ArrayList<>()));
            stepTotal.put(step.name(), new AtomicInteger(0));
            stepFailed.put(step.name(), new AtomicInteger(0));
        }

        // ── HTTP client (shared, thread-safe) ──
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        // ── Execution ──
        long startTimeMs = System.currentTimeMillis();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch allDone = new CountDownLatch(users);
        AtomicReference<Failure> firstFailure = new AtomicReference<>();

        long totalDurationMs = (rampUpSeconds + holdSeconds + cooldownSeconds) * 1000;
        long rampUpPerUserMs = users > 1 ? (rampUpSeconds * 1000) / (users - 1) : 0;

        final String finalBaseUrl = baseUrl;

        for (int i = 0; i < users; i++) {
            final long userStartDelay = rampUpPerUserMs * i;

            executor.submit(() -> {
                String currentStep = "<feeder>";
                try {
                    // Ramp-up delay
                    if (userStartDelay > 0) {
                        Thread.sleep(userStartDelay);
                    }

                    long userEndTime = System.currentTimeMillis() + (holdSeconds * 1000);

                    // Feeder row for this user (never mutated — each iteration works on a copy)
                    Map<String, Object> feederRow = feeder != null && feeder.hasNext()
                            ? feeder.next()
                            : Collections.emptyMap();

                    // Hold phase — loop until hold duration expires or the run is aborted
                    while (System.currentTimeMillis() < userEndTime && firstFailure.get() == null
                            && !Thread.currentThread().isInterrupted()) {
                        // Per-iteration copy so extract() works without a feeder, with
                        // immutable feeder rows, and never leaks values across iterations.
                        Map<String, Object> vars = new HashMap<>(feederRow != null ? feederRow : Map.of());
                        for (LoadStep step : steps) {
                            if (firstFailure.get() != null) {
                                return;
                            }
                            currentStep = step.name();
                            // Build first: a malformed request is a programming error, not a KO
                            HttpRequest request = buildRequest(step, finalBaseUrl, vars, timeoutSeconds);

                            long reqStart = System.nanoTime();
                            boolean ok;
                            try {
                                HttpResponse<String> response =
                                        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                                // Record the status before checks run, so failed checks are still counted
                                recordStatus(statusCounts, response.statusCode());
                                evaluateResponse(step, response, vars);
                                ok = true;
                            } catch (CheckFailedException e) {
                                ok = false;
                            } catch (IOException e) {
                                // No HTTP response at all (connect failure, timeout, ...)
                                recordStatus(statusCounts, TRANSPORT_ERROR_STATUS);
                                ok = false;
                            }
                            double latencyMs = (System.nanoTime() - reqStart) / 1_000_000.0;

                            totalRequests.incrementAndGet();
                            (ok ? successRequests : failedRequests).incrementAndGet();
                            latencies.add(new double[] { latencyMs });
                            stepLatencies.get(step.name()).add(new double[] { latencyMs });
                            stepTotal.get(step.name()).incrementAndGet();
                            if (!ok) {
                                stepFailed.get(step.name()).incrementAndGet();
                            }

                            // Think time between steps
                            applyThinkTime(thinkMinMs, thinkMaxMs, thinkFixed);
                        }

                        // Refresh feeder variables each full iteration
                        currentStep = "<feeder>";
                        if (feeder != null && feeder.hasNext()) {
                            feederRow = feeder.next();
                        }
                    }
                } catch (InterruptedException e) {
                    // Stop this virtual user
                    Thread.currentThread().interrupt();
                } catch (Throwable t) {
                    // Programming/configuration error: abort the whole run, never count it as a KO
                    firstFailure.compareAndSet(null, new Failure(currentStep, t));
                } finally {
                    allDone.countDown();
                }
            });
        }

        // Wait for all users to finish (with a safety timeout); stop promptly on abort
        long safetyTimeoutMs = totalDurationMs + 60_000;
        long deadline = System.currentTimeMillis() + safetyTimeoutMs;
        boolean finished = false;
        boolean cancelled = false;
        try {
            while (!finished && firstFailure.get() == null && System.currentTimeMillis() < deadline) {
                finished = allDone.await(Math.min(100, Math.max(1, deadline - System.currentTimeMillis())),
                        TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            cancelled = true;
        }

        // Stop every virtual user and wait (bounded) BEFORE reading any accumulator
        if (finished && !cancelled && firstFailure.get() == null) {
            executor.shutdown();
        } else {
            executor.shutdownNow();
        }
        boolean terminated = awaitTermination(executor);
        if (cancelled) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("[LoadTest] Scenario '" + scenario.name() + "' was cancelled"
                    + (terminated ? "" : "; some virtual users did not stop within 10s"));
        }
        if (firstFailure.get() != null) {
            Failure f = firstFailure.get();
            throw new IllegalStateException("[LoadTest] Scenario '" + scenario.name()
                    + "' aborted: unexpected error in step '" + f.step() + "'", f.cause());
        }
        if (!finished) {
            throw new IllegalStateException("[LoadTest] Scenario '" + scenario.name()
                    + "' exceeded safety timeout of " + safetyTimeoutMs / 1000 + "s and was stopped");
        }
        long endTimeMs = System.currentTimeMillis();
        long actualDurationMs = endTimeMs - startTimeMs;

        // ── Compute metrics ──
        double[] sortedLatencies = latencies.stream()
                .mapToDouble(d -> d[0])
                .sorted()
                .toArray();

        double throughput = actualDurationMs > 0
                ? (totalRequests.get() * 1000.0) / actualDurationMs
                : 0;

        double errorRate = totalRequests.get() > 0
                ? (double) failedRequests.get() / totalRequests.get()
                : 0;

        // Status code map
        Map<Integer, Long> statusMap = new LinkedHashMap<>();
        statusCounts.forEach((code, count) -> statusMap.put(code, count.get()));

        // Per-step metrics
        Map<String, LoadTestMetrics.StepMetrics> stepMetrics = new LinkedHashMap<>();
        for (LoadStep step : steps) {
            String stepName = step.name();
            List<double[]> stepLats = stepLatencies.get(stepName);
            double[] sorted = stepLats.stream().mapToDouble(d -> d[0]).sorted().toArray();
            int total = stepTotal.get(stepName).get();
            int failed = stepFailed.get(stepName).get();

            stepMetrics.put(stepName, new LoadTestMetrics.StepMetrics(
                    stepName,
                    total,
                    total - failed,
                    failed,
                    percentile(sorted, 95),
                    total > 0 ? (double) failed / total : 0));
        }

        return new LoadTestMetrics(
                scenario.name(),
                totalRequests.get(),
                successRequests.get(),
                failedRequests.get(),
                throughput,
                mean(sortedLatencies),
                percentile(sortedLatencies, 50),
                percentile(sortedLatencies, 90),
                percentile(sortedLatencies, 95),
                percentile(sortedLatencies, 99),
                sortedLatencies.length > 0 ? sortedLatencies[0] : 0,
                sortedLatencies.length > 0 ? sortedLatencies[sortedLatencies.length - 1] : 0,
                errorRate,
                statusMap,
                actualDurationMs,
                users,
                name(),
                stepMetrics,
                percentile(sortedLatencies, 75));
    }

    /**
     * Waits up to 10s for all virtual users to stop. A pending interrupt of the calling thread is
     * cleared for the wait and restored afterwards.
     */
    private static boolean awaitTermination(ExecutorService executor) {
        boolean interrupted = Thread.interrupted();
        boolean terminated = false;
        try {
            terminated = executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            interrupted = true;
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        return terminated;
    }

    /** First unexpected error of a run and the step it happened in. */
    private record Failure(String step, Throwable cause) {
    }

    /** A response failed a check, the default status check, or an extraction: the request is KO. */
    static final class CheckFailedException extends RuntimeException {
        CheckFailedException(String message) {
            super(message, null, false, false);
        }
    }

    // ── Request building ─────────────────────────────────────────────────

    private HttpRequest buildRequest(LoadStep step, String baseUrl, Map<String, Object> vars,
            int timeoutSeconds) {
        String path = VariableResolver.resolve(step.path(), vars);
        String url = buildUrl(baseUrl, path, step.queryParams(), vars);

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds));

        // Headers
        for (Map.Entry<String, String> h : step.headers().entrySet()) {
            reqBuilder.header(h.getKey(), VariableResolver.resolve(h.getValue(), vars));
        }

        // Body: JSON body, or urlencoded form params (never both — rejected by validation)
        String bodyStr = null;
        String defaultContentType = null;
        Object body = step.body();
        if (body != null) {
            Object resolved = VariableResolver.resolveBody(body, vars);
            bodyStr = resolved instanceof String s ? s : toJson(resolved);
            defaultContentType = "application/json";
        } else if (!step.formParams().isEmpty()) {
            bodyStr = formBody(step.formParams(), vars);
            defaultContentType = "application/x-www-form-urlencoded";
        }

        if (bodyStr != null) {
            reqBuilder.method(step.method(), HttpRequest.BodyPublishers.ofString(bodyStr, StandardCharsets.UTF_8));
            if (!hasHeader(step, "Content-Type")) {
                reqBuilder.header("Content-Type", defaultContentType);
            }
        } else {
            switch (step.method()) {
                case "POST" -> reqBuilder.POST(HttpRequest.BodyPublishers.noBody());
                case "PUT" -> reqBuilder.PUT(HttpRequest.BodyPublishers.noBody());
                case "DELETE" -> reqBuilder.DELETE();
                case "PATCH" -> reqBuilder.method("PATCH", HttpRequest.BodyPublishers.noBody());
                default -> reqBuilder.GET();
            }
        }
        return reqBuilder.build();
    }

    private static boolean hasHeader(LoadStep step, String name) {
        return step.headers().keySet().stream().anyMatch(name::equalsIgnoreCase);
    }

    private static String formBody(Map<String, String> formParams, Map<String, Object> vars) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> fp : formParams.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            String value = VariableResolver.resolve(fp.getValue() != null ? fp.getValue() : "", vars);
            sb.append(URLEncoder.encode(fp.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static void recordStatus(ConcurrentHashMap<Integer, AtomicLong> statusCounts, int status) {
        statusCounts.computeIfAbsent(status, k -> new AtomicLong()).incrementAndGet();
    }

    // ── Checks (same semantics as the Gatling engine) ────────────────────

    /**
     * Applies the default status check (unless the step has an explicit status check), every
     * check, and every extraction. Throws {@link CheckFailedException} on the first failure.
     */
    private static void evaluateResponse(LoadStep step, HttpResponse<String> response, Map<String, Object> vars) {
        boolean explicitStatus = step.checks().stream().anyMatch(c ->
                c.type() == CheckCondition.Type.STATUS_IS || c.type() == CheckCondition.Type.STATUS_IN);
        int status = response.statusCode();
        if (!explicitStatus && !isDefaultOk(status)) {
            throw new CheckFailedException("[LoadTest] Status " + status + " is not 2xx or 304");
        }

        JsonBody json = new JsonBody(response.body());
        for (CheckCondition check : step.checks()) {
            evaluateCheck(check, response, json);
        }

        for (Map.Entry<String, String> ext : step.extractions().entrySet()) {
            JsonNode extracted = json.nodeAt(ext.getValue());
            if (extracted == null) {
                throw new CheckFailedException("[LoadTest] extract('" + ext.getKey() + "'): JSONPath '"
                        + ext.getValue() + "' not found in response");
            }
            // JSON null: found (OK, like Gatling 3.13.5), stored as null → ${var} stays unresolved
            vars.put(ext.getKey(), extracted.isNull() ? null : asString(extracted));
        }
    }

    /** Gatling's default status check: 2xx (200..299) or 304. */
    static boolean isDefaultOk(int status) {
        return (status >= 200 && status <= 299) || status == 304;
    }

    private static void evaluateCheck(CheckCondition check, HttpResponse<String> response, JsonBody json) {
        switch (check.type()) {
            case STATUS_IS -> {
                int expected = (int) check.expected();
                if (response.statusCode() != expected) {
                    throw new CheckFailedException("[LoadTest] Expected status " + expected +
                            " but got " + response.statusCode());
                }
            }
            case STATUS_IN -> {
                int[] allowed = (int[]) check.expected();
                int actual = response.statusCode();
                if (Arrays.stream(allowed).noneMatch(s -> s == actual)) {
                    throw new CheckFailedException("[LoadTest] Expected status in " +
                            Arrays.toString(allowed) + " but got " + actual);
                }
            }
            case JSON_EXISTS -> {
                // JSON null counts as existing (observed Gatling 3.13.5 behaviour)
                if (json.nodeAt(check.target()) == null) {
                    throw new CheckFailedException("[LoadTest] JSONPath '" + check.target() +
                            "' not found in response");
                }
            }
            case JSON_EQUALS -> {
                String actual = json.valueAt(check.target());   // JSON null → null → KO, like Gatling
                String expected = String.valueOf(check.expected());
                if (!expected.equals(actual)) {
                    throw new CheckFailedException("[LoadTest] JSONPath '" + check.target() + "' expected "
                            + expected + " but was " + actual);
                }
            }
            case JSON_IN -> {
                String actual = json.valueAt(check.target());
                int[] allowed = (int[]) check.expected();
                if (actual == null || Arrays.stream(allowed).mapToObj(String::valueOf).noneMatch(actual::equals)) {
                    throw new CheckFailedException("[LoadTest] JSONPath '" + check.target() + "' expected one of "
                            + Arrays.toString(allowed) + " but was " + actual);
                }
            }
            case BODY_MATCHES -> {
                String regex = (String) check.expected();
                String body = response.body() != null ? response.body() : "";
                if (!Pattern.compile(regex).matcher(body).find()) {
                    throw new CheckFailedException("[LoadTest] Response body does not match: " + regex);
                }
            }
            case HEADER_PRESENT -> {
                String header = check.target();
                if (response.headers().firstValue(header).isEmpty()) {
                    throw new CheckFailedException("[LoadTest] Header '" + header + "' not present");
                }
            }
            default -> throw new IllegalArgumentException("[LoadTest] Unsupported check type " + check.type());
        }
    }

    /** Lazily parsed JSON response body (parsed at most once per response). */
    private static final class JsonBody {
        private final String raw;
        private JsonNode tree;
        private boolean parsed;

        JsonBody(String raw) {
            this.raw = raw;
        }

        /** Node at the path ({@code NullNode} for JSON null), or {@code null} if missing / not JSON. */
        JsonNode nodeAt(String path) {
            if (!parsed) {
                parsed = true;
                tree = readTree(raw);
            }
            return tree == null ? null : JdkLoadEngine.nodeAt(tree, path);
        }

        /** String form of the value at the path; {@code null} if missing, not JSON, or JSON null. */
        String valueAt(String path) {
            JsonNode node = nodeAt(path);
            return node == null || node.isNull() ? null : asString(node);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private String buildUrl(String baseUrl, String path, Map<String, Object> queryParams,
            Map<String, Object> vars) {
        StringBuilder url = new StringBuilder();

        if (path.startsWith("http://") || path.startsWith("https://")) {
            url.append(path);
        } else {
            String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            String p = path.startsWith("/") ? path : "/" + path;
            url.append(base).append(p);
        }

        if (!queryParams.isEmpty()) {
            url.append(url.indexOf("?") >= 0 ? '&' : '?');
            boolean first = true;
            for (Map.Entry<String, Object> qp : queryParams.entrySet()) {
                if (!first)
                    url.append('&');
                String value = VariableResolver.resolve(String.valueOf(qp.getValue()), vars);
                // Values are URL-encoded after variable resolution (spaces as %20), like Gatling
                url.append(qp.getKey()).append('=')
                        .append(URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"));
                first = false;
            }
        }

        return url.toString();
    }

    private static void applyThinkTime(long minMs, long maxMs, Duration fixed) throws InterruptedException {
        if (fixed != null) {
            Thread.sleep(fixed.toMillis());
        } else if (minMs >= 0 && maxMs >= minMs) {
            long sleep = ThreadLocalRandom.current().nextLong(minMs, maxMs + 1);
            Thread.sleep(sleep);
        }
    }

    // ── JSONPath (supported subset) ──────────────────────────────────────

    private static final ObjectMapper JSON = new ObjectMapper();

    /** One segment: {@code .name} or {@code [index]}. */
    private static final Pattern JSON_PATH_SEGMENT = Pattern.compile("\\.([^.\\[\\]*?@()'\"\\s$:,]+)|\\[(\\d+)]");

    /**
     * Returns {@code true} if the JDK engine supports the JSONPath: a {@code $} root followed by
     * {@code .name} and {@code [index]} segments ({@code $.a.b}, {@code $.a[0].b}, {@code $[0]}).
     * Wildcards, deep scan, filters, slices and bracket-quoted names are not supported.
     */
    public static boolean isSupportedJsonPath(String path) {
        return parseJsonPath(LoadScenarioValidator.normalizeJsonPath(path)) != null;
    }

    /** @return segments (String names / Integer indexes), or {@code null} if unsupported */
    private static List<Object> parseJsonPath(String path) {
        if (path == null || path.length() < 2 || path.charAt(0) != '$') {
            return null;
        }
        List<Object> segments = new ArrayList<>();
        Matcher m = JSON_PATH_SEGMENT.matcher(path);
        int pos = 1;
        while (pos < path.length()) {
            if (!m.find(pos) || m.start() != pos) {
                return null;
            }
            if (m.group(1) != null) {
                segments.add(m.group(1));
            } else {
                try {
                    segments.add(Integer.valueOf(m.group(2)));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            pos = m.end();
        }
        return segments;
    }

    private static IllegalArgumentException unsupportedJsonPath(String path) {
        return new IllegalArgumentException("[LoadTest] JSONPath '" + path
                + "' is not supported by the JDK engine (supported: $.a.b, $.a[0].b, $[0]); "
                + "the Gatling engine supports it");
    }

    private static JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSON.readTree(json);
        } catch (IOException e) {
            return null; // not JSON: every JSON check/extraction fails
        }
    }

    /**
     * Node at the (normalised) path: {@code NullNode} for JSON null, {@code null} if the path
     * is not found.
     */
    private static JsonNode nodeAt(JsonNode root, String path) {
        String normalized = LoadScenarioValidator.normalizeJsonPath(path);
        List<Object> segments = parseJsonPath(normalized);
        if (segments == null) {
            throw unsupportedJsonPath(path);
        }
        JsonNode node = root;
        for (Object seg : segments) {
            node = seg instanceof Integer i ? node.path(i) : node.path((String) seg);
            if (node.isMissingNode()) {
                return null;
            }
        }
        return node;
    }

    /** Scalars as their string form (number 5 → "5"), objects/arrays as compact JSON. */
    private static String asString(JsonNode node) {
        return node.isValueNode() ? node.asText() : node.toString();
    }

    /**
     * Extracts the value at a JSONPath from a JSON document, using the supported subset
     * ({@code $.a.b}, {@code $.a[0].b}, {@code $[0]}). Scalars are returned in their string form
     * (JSON number {@code 5} → {@code "5"}), objects/arrays as compact JSON.
     *
     * A path without a leading {@code $} ({@code "token"}, {@code "data.token"}) is read as
     * {@code "$." + path}, as before.
     *
     * @return the value, or {@code null} if the input is {@code null}, not JSON, or the path is
     *         not found (or holds JSON {@code null})
     * @throws IllegalArgumentException if the path uses syntax outside the supported subset
     */
    public static String extractJsonPath(String json, String path) {
        if (json == null || path == null)
            return null;
        if (!isSupportedJsonPath(path)) {
            throw unsupportedJsonPath(path);
        }
        JsonNode root = readTree(json);
        JsonNode node = root == null ? null : nodeAt(root, path);
        return node == null || node.isNull() ? null : asString(node);
    }

    private String toJson(Object obj) {
        if (obj instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first)
                    sb.append(',');
                sb.append('"').append(e.getKey()).append("\":");
                Object v = e.getValue();
                if (v instanceof String s) {
                    sb.append('"').append(s.replace("\"", "\\\"")).append('"');
                } else if (v instanceof Map) {
                    sb.append(toJson(v));
                } else {
                    sb.append(v);
                }
                first = false;
            }
            sb.append('}');
            return sb.toString();
        }
        return obj.toString();
    }

    // ── Percentile calculation ───────────────────────────────────────────

    public static double percentile(double[] sorted, int p) {
        return PercentileCalculator.linearInterpolation(sorted, p);
    }

    public static double mean(double[] values) {
        return PercentileCalculator.mean(values);
    }
}
