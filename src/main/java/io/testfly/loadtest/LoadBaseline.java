package io.testfly.loadtest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.testfly.api.TestFlyApi;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * The key metrics of one load-test run, saved as versioned JSON so a later run can be compared with it
 * ({@link LoadTestAssert#assertNoRegression(LoadBaseline, Tolerance)}). Each run also writes its own
 * {@value #RESULT_FILE} into its run directory; copy that file into the repository to use it as a baseline.
 *
 * <pre>
 * {"schemaVersion":1,"scenarioName":"Checkout","engine":"gatling","createdAt":"2026-10-25T10:15:30Z",
 *  "p95":120.5,"p99":180.0,"mean":60.2,"errorRate":0.0,"throughput":42.1}
 * </pre>
 *
 * An unmeasured value is {@code null} in the file and an empty {@link OptionalDouble} here. Immutable and thread-safe.
 */
@TestFlyApi(since = "1.1.0")
public final class LoadBaseline {

    /** File name of the per-run artefact written into each run directory. */
    public static final String RESULT_FILE = "testfly-loadtest-result.json";

    /** Reason of a {@link RegressionResult} whose metric is unmeasured on either side. */
    public static final String NOT_COMPARED = "not compared: not measured";

    static final int SCHEMA_VERSION = 1;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(LoadBaseline.class);

    /** {@code rw-r--r--}: what a plain file write gives with the usual umask; also for CI uploads and repos. */
    private static final Set<PosixFilePermission> DEFAULT_PERMISSIONS =
            PosixFilePermissions.fromString("rw-r--r--");

    private final String scenarioName;
    private final String engine;
    private final Instant createdAt;
    private final double p95;
    private final double p99;
    private final double mean;
    private final double errorRate;
    private final double throughput;

    private LoadBaseline(String scenarioName, String engine, Instant createdAt, double p95, double p99, double mean,
            double errorRate, double throughput) {
        this.scenarioName = scenarioName;
        this.engine = engine;
        this.createdAt = createdAt;
        this.p95 = p95;
        this.p99 = p99;
        this.mean = mean;
        this.errorRate = errorRate;
        this.throughput = throughput;
    }

    /**
     * A baseline of the given run's metrics, created now. A run without any request stores latency and throughput
     * as unmeasured.
     *
     * @throws IllegalArgumentException if {@code metrics} is {@code null} or partial (an incomplete run is never a
     *                                  baseline)
     */
    @TestFlyApi(since = "1.1.0")
    public static LoadBaseline of(LoadTestMetrics metrics) {
        if (metrics == null) {
            throw new IllegalArgumentException("[LoadTest] LoadBaseline.of(..) needs metrics");
        }
        if (metrics.partial()) {
            throw new IllegalArgumentException("[LoadTest] " + metrics.scenarioName() + ": metrics are partial; "
                    + "an incomplete run cannot be a baseline");
        }
        boolean empty = metrics.totalRequests() == 0;
        return new LoadBaseline(metrics.scenarioName(), metrics.engine(), Instant.now(),
                empty ? Double.NaN : metrics.p95LatencyMs(), empty ? Double.NaN : metrics.p99LatencyMs(),
                empty ? Double.NaN : metrics.meanLatencyMs(), metrics.errorRate(),
                empty ? Double.NaN : metrics.throughputRps());
    }

    /**
     * Reads a baseline file.
     *
     * @throws IllegalArgumentException if the file is missing or unreadable, is not a JSON object, has an
     *                                  unsupported {@code schemaVersion}, or holds an invalid value
     */
    @TestFlyApi(since = "1.1.0")
    public static LoadBaseline load(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("[LoadTest] Baseline file not found: " + file);
        }
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("[LoadTest] Baseline file " + file + " cannot be read: " + e, e);
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(content);
        } catch (IOException e) {
            throw new IllegalArgumentException("[LoadTest] Baseline file " + file + " is not valid JSON: "
                    + e.getMessage(), e);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("[LoadTest] Baseline file " + file + " is not a JSON object");
        }
        JsonNode version = root.path("schemaVersion");
        if (!version.isIntegralNumber() || !version.canConvertToInt() || version.asInt() != SCHEMA_VERSION) {
            throw new IllegalArgumentException("[LoadTest] Baseline file " + file + " has unsupported schemaVersion "
                    + version + " (supported: " + SCHEMA_VERSION + ")");
        }
        String created = text(root, "createdAt", file);
        Instant createdAt;
        try {
            createdAt = created != null ? Instant.parse(created) : null;
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("[LoadTest] Baseline file " + file + " has an invalid createdAt: "
                    + root.get("createdAt"), e);
        }
        return new LoadBaseline(text(root, "scenarioName", file), text(root, "engine", file), createdAt,
                number(root, "p95", file), number(root, "p99", file), number(root, "mean", file),
                number(root, "errorRate", file), number(root, "throughput", file));
    }

    /**
     * Writes this baseline as JSON, creating missing parent directories. The file is written to a unique temporary
     * sibling first and then moved into place, so a reader never sees a half-written file and concurrent writers do
     * not share a temporary file.
     *
     * @throws IllegalArgumentException if {@code file} is {@code null}
     * @throws UncheckedIOException     if the file cannot be written
     */
    @TestFlyApi(since = "1.1.0")
    public void save(Path file) {
        if (file == null) {
            throw new IllegalArgumentException("[LoadTest] LoadBaseline.save(..) needs a file");
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("schemaVersion", SCHEMA_VERSION);
        json.put("scenarioName", scenarioName);
        json.put("engine", engine);
        json.put("createdAt", createdAt != null ? createdAt.toString() : null);
        json.put("p95", nullIfNaN(p95));
        json.put("p99", nullIfNaN(p99));
        json.put("mean", nullIfNaN(mean));
        json.put("errorRate", nullIfNaN(errorRate));
        json.put("throughput", nullIfNaN(throughput));
        Path target = file.toAbsolutePath();
        Path tmp = null;
        try {
            Files.createDirectories(target.getParent());
            // the temp file is owner-only on POSIX: remember the target's permissions to restore them after the move
            Set<PosixFilePermission> existing = posixPermissions(target);
            tmp = Files.createTempFile(target.getParent(), target.getFileName() + ".", ".tmp");
            Files.writeString(tmp, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(json),
                    StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            setPermissions(target, existing != null ? existing : DEFAULT_PERMISSIONS);
        } catch (IOException e) {
            try {
                if (tmp != null) {
                    Files.deleteIfExists(tmp);
                }
            } catch (IOException ignored) {
                // best effort
            }
            throw new UncheckedIOException("[LoadTest] Could not write baseline " + file, e);
        }
    }

    @TestFlyApi(since = "1.1.0")
    public String scenarioName() {
        return scenarioName;
    }

    @TestFlyApi(since = "1.1.0")
    public String engine() {
        return engine;
    }

    /** When the baseline's metrics were taken ({@link #of}); {@code null} if a loaded file has none. */
    @TestFlyApi(since = "1.1.0")
    public Instant createdAt() {
        return createdAt;
    }

    @TestFlyApi(since = "1.1.0")
    public OptionalDouble p95() {
        return optional(p95);
    }

    @TestFlyApi(since = "1.1.0")
    public OptionalDouble p99() {
        return optional(p99);
    }

    @TestFlyApi(since = "1.1.0")
    public OptionalDouble mean() {
        return optional(mean);
    }

    @TestFlyApi(since = "1.1.0")
    public OptionalDouble errorRate() {
        return optional(errorRate);
    }

    @TestFlyApi(since = "1.1.0")
    public OptionalDouble throughput() {
        return optional(throughput);
    }

    @Override
    public String toString() {
        return "LoadBaseline[" + scenarioName + ", " + engine + ", " + createdAt + ", p95=" + p95 + ", p99=" + p99
                + ", mean=" + mean + ", errorRate=" + errorRate + ", throughput=" + throughput + "]";
    }

    /**
     * Compares a run with this baseline, one result per metric in the order p95, p99, mean, errorRate, throughput,
     * against the exact limits of {@link #limit}. Latency regresses iff {@code actual > limit}, throughput iff
     * {@code actual < limit}, the error rate iff {@code actual > limit}.
     */
    List<RegressionResult> compare(LoadTestMetrics actual, Tolerance tolerance) {
        List<RegressionResult> out = new ArrayList<>();
        out.add(result("p95", p95, actual.p95LatencyMs(), tolerance));
        out.add(result("p99", p99, actual.p99LatencyMs(), tolerance));
        out.add(result("mean", mean, actual.meanLatencyMs(), tolerance));
        out.add(result("errorRate", errorRate, actual.errorRate(), tolerance));
        out.add(result("throughput", throughput, actual.throughputRps(), tolerance));
        return out;
    }

    /**
     * The exact limit of a metric, computed in decimal so that, for example, {@code 100 × 1.15} is 115 and
     * {@code 0.7 + 0.1} is 0.8: latency {@code baseline × (1 + pct/100)}, throughput
     * {@code baseline × (1 − pct/100)}, error rate {@code baseline + errorRateDelta}.
     */
    static BigDecimal limit(String metric, double baseline, Tolerance tolerance) {
        BigDecimal base = BigDecimal.valueOf(baseline);
        BigDecimal pct = BigDecimal.valueOf(tolerance.percent()).movePointLeft(2);
        return switch (metric) {
            case "errorRate" -> base.add(BigDecimal.valueOf(tolerance.errorRateDelta()));
            case "throughput" -> base.multiply(BigDecimal.ONE.subtract(pct));
            default -> base.multiply(BigDecimal.ONE.add(pct));
        };
    }

    private static RegressionResult result(String metric, double baseline, double actual, Tolerance tolerance) {
        if (Double.isNaN(baseline) || Double.isNaN(actual)) {
            return new RegressionResult(metric, baseline, actual, false, NOT_COMPARED);
        }
        BigDecimal limit = limit(metric, baseline, tolerance);
        int cmp = BigDecimal.valueOf(actual).compareTo(limit);
        boolean lowerIsWorse = "throughput".equals(metric);
        boolean regressed = lowerIsWorse ? cmp < 0 : cmp > 0;
        if (!regressed) {
            return new RegressionResult(metric, baseline, actual, false, null);
        }
        String reason = plain(actual) + (lowerIsWorse ? " is below the limit " : " is above the limit ")
                + plain(limit);
        if (baseline == 0 && !"errorRate".equals(metric) && !lowerIsWorse) {
            reason += " " + ZERO_BASELINE;
        }
        return new RegressionResult(metric, baseline, actual, true, reason);
    }

    /** The POSIX permissions of an existing file, or {@code null} (no file, no POSIX, or unreadable). */
    private static Set<PosixFilePermission> posixPermissions(Path file) {
        if (!Files.exists(file)) {
            return null;
        }
        try {
            return Files.getPosixFilePermissions(file);
        } catch (UnsupportedOperationException | IOException e) {
            return null;
        }
    }

    /** Best effort: sets POSIX permissions where supported; a failure is only logged at DEBUG. */
    private static void setPermissions(Path file, Set<PosixFilePermission> permissions) {
        try {
            if (file.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(file, permissions);
            }
        } catch (UnsupportedOperationException | IOException | SecurityException e) {
            LOGGER.debug("[LoadTest] Could not set the permissions of {} ({})", file, e.toString());
        }
    }

    /** Added to a latency regression over a 0 ms baseline, which no relative tolerance can absorb. */
    static final String ZERO_BASELINE = "(a relative tolerance cannot absorb an increase over a 0 ms baseline)";

    /** A decimal as a plain string ({@code 153}, {@code 0.5}). */
    static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    /** A value as a plain decimal ({@code 153}, {@code 0.5}), never in exponent notation. */
    static String plain(double value) {
        if (!Double.isFinite(value)) {
            return String.valueOf(value);
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static OptionalDouble optional(double value) {
        return Double.isNaN(value) ? OptionalDouble.empty() : OptionalDouble.of(value);
    }

    private static Double nullIfNaN(double value) {
        return Double.isNaN(value) ? null : value;
    }

    private static String text(JsonNode root, String field, Path file) {
        JsonNode n = root.get(field);
        if (n == null || n.isNull()) {
            return null;
        }
        if (!n.isTextual()) {
            throw new IllegalArgumentException("[LoadTest] Baseline file " + file + ": '" + field
                    + "' must be a string or null, was " + n);
        }
        return n.asText();
    }

    private static double number(JsonNode root, String field, Path file) {
        JsonNode n = root.get(field);
        if (n == null || n.isNull()) {
            return Double.NaN;
        }
        if (!n.isNumber() || !Double.isFinite(n.asDouble()) || n.asDouble() < 0) {
            throw new IllegalArgumentException("[LoadTest] Baseline file " + file + ": '" + field
                    + "' must be a finite number >= 0 or null, was " + n);
        }
        return n.asDouble();
    }
}
