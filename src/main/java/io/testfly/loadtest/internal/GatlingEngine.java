package io.testfly.loadtest.internal;

import io.testfly.loadtest.LoadScenario;
import io.testfly.loadtest.LoadTestConfig;
import io.testfly.loadtest.LoadTestMetrics;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Gatling-based load-test engine — uses Gatling's async Netty HTTP engine
 * for high-concurrency load testing.
 *
 * <p>
 * Every run is executed in a forked child JVM (with the {@code --add-opens} Gatling needs) and
 * its own results directory. The child is bounded by a wait budget, terminated together with its
 * descendants on timeout, interruption or JVM shutdown, and a non-zero exit code fails the run.
 */
public final class GatlingEngine implements LoadTestEngine {

    private static final ReentrantLock GATLING_LOCK = new ReentrantLock();

    /** Extra wait budget for JVM start-up and report generation on top of the simulation's maxDuration. */
    static final long WAIT_GRACE_SECONDS = 120;

    private static final int LOG_TAIL_LINES = 20;
    private static final int LOG_TAIL_MAX_CHARS = 300;

    @Override
    public String name() {
        return "gatling";
    }

    @Override
    public boolean isAvailable() {
        return GatlingBridge.isAvailable();
    }

    /**
     * One scenario of a run: the scenario, its resolved configuration and its base URL ({@code null} for the
     * configured one).
     */
    public record ScenarioRun(LoadScenario scenario, LoadTestConfig config, String baseUrl) {
    }

    @Override
    public LoadTestMetrics execute(LoadScenario scenario, LoadTestConfig config) {
        if (!GatlingBridge.isAvailable()) {
            throw new IllegalStateException(GatlingBridge.missingDependencyMessage());
        }

        LoadScenarioValidator.validate(scenario, name(), config.getMaxUsers());

        // a single scenario keeps unprefixed request names, so its metrics equal the global ones
        return executeSimulation(scenario.name(), List.of(new ScenarioRun(scenario, config, null)))
                .scenario(scenario.name());
    }

    /**
     * Runs the scenarios as one Gatling simulation in a forked JVM and returns global and per-scenario metrics.
     * The caller validates first ({@link LoadScenarioValidator#validateSimulation}). Results go to
     * {@code <resultsDir of the first scenario>/<simulation-slug>-<millis>/}.
     *
     * @throws IllegalStateException if the run fails, its metrics sidecar is missing, or no request was recorded
     */
    public GatlingResultsParser.SimulationMetrics executeSimulation(String simulationName, List<ScenarioRun> runs) {
        if (!GatlingBridge.isAvailable()) {
            throw new IllegalStateException(GatlingBridge.missingDependencyMessage());
        }
        if (runs.isEmpty()) {
            throw new IllegalArgumentException("[LoadTest] Simulation '" + simulationName + "' has no scenario");
        }

        List<GatlingRunConfig.ScenarioConfig> scenarios = new ArrayList<>();
        Map<String, Integer> users = new LinkedHashMap<>();
        for (ScenarioRun run : runs) {
            GatlingRunConfig.ScenarioConfig sc =
                    new GatlingRunConfig.ScenarioConfig(run.scenario(), run.config(), resolveBaseUrl(run));
            scenarios.add(sc);
            users.put(sc.scenarioName, sc.reportedUsers());
        }
        String resultsDir = runs.get(0).config().getResultsDir();

        GATLING_LOCK.lock();
        try {
            // Dedicated subfolder for this specific run to prevent collisions
            String runId = simulationName.replaceAll("[^a-zA-Z0-9_-]", "_") + "-" + System.currentTimeMillis();
            File specificResultsDir = new File(resultsDir, runId);
            specificResultsDir.mkdirs();

            for (ScenarioRun run : runs) {
                LoadScenario scenario = run.scenario();
                LoadTestConfig config = run.config();
                System.out.println("[LoadTest] Starting Gatling engine: " + scenario.name()
                        + (scenario.workload() != null
                                ? " (workload " + scenario.workload() + ")"
                                : " (" + config.getUsers() + " users, rampUp=" + config.getRampUp()
                                        + ", hold=" + config.getHold() + ")"));
            }

            GatlingRunConfig runConfig = GatlingRunConfig.forSimulation(simulationName, resultsDir, scenarios);
            Path sidecar = new File(specificResultsDir, GatlingResultsParser.METRICS_SIDECAR_FILE)
                    .getAbsoluteFile().toPath();
            runConfig.metricsSidecarPath = sidecar.toString();
            GatlingRunConfig.set(runConfig);

            // Always run in an isolated JVM subprocess to ensure 100% clean state between
            // consecutive simulations and prevent Gatling Scala singleton state caching.
            runGatlingForked(specificResultsDir.getAbsolutePath(), runConfig, simulationName);

            System.out.println("[LoadTest] Gatling execution complete. Parsing results...");

            GatlingResultsParser.SimulationMetrics metrics =
                    GatlingResultsParser.parseSimulation(sidecar, simulationName, users);
            if (metrics.global().totalRequests() == 0) {
                throw new IllegalStateException(
                        "[LoadTest] Gatling simulation finished but 0 requests were recorded for '" +
                                simulationName + "'. Check console logs for errors.");
            }
            return metrics;

        } catch (IllegalStateException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.toString();
            throw new IllegalStateException("[LoadTest] Gatling execution failed: " + msg, e);
        } finally {
            GatlingRunConfig.clear();
            GATLING_LOCK.unlock();
        }
    }

    /** The scenario's own base URL, else the run's, else the resolved config's, else {@code execution.baseUrl}. */
    private static String resolveBaseUrl(ScenarioRun run) {
        String baseUrl = run.scenario().baseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = run.baseUrl();
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = run.config().getBaseUrl();
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            try {
                baseUrl = io.testfly.internal.TestFlyContext.getConfig().getExecution().getBaseUrl();
            } catch (Exception e) {
                baseUrl = null;
            }
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("[LoadTest] No baseUrl configured for Gatling engine (scenario '"
                    + run.scenario().name() + "').");
        }
        return baseUrl;
    }

    /**
     * Reads the run's metrics sidecar. A missing or unreadable sidecar is a failed run, not empty metrics.
     */
    static LoadTestMetrics readMetrics(Path sidecar, String scenarioName, int users) {
        if (!Files.isReadable(sidecar)) {
            throw new IllegalStateException("[LoadTest] Gatling metrics sidecar missing: " + sidecar);
        }
        try {
            return GatlingResultsParser.parseSidecar(sidecar, scenarioName, users);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("[LoadTest] Gatling metrics sidecar missing: " + sidecar
                    + " (unreadable: " + e.getMessage() + ")", e);
        }
    }

    /**
     * Forks a new JVM process with
     * {@code --add-opens java.base/java.lang=ALL-UNNAMED}
     * so Gatling can run without requiring manual IDE VM options.
     */
    private void runGatlingForked(String resultsDir, GatlingRunConfig runConfig, String runDescription)
            throws IOException {
        Path tempConfig = createPrivateTempFile();
        Thread shutdownHook = null;
        try {
            GatlingRunConfig.save(runConfig, tempConfig.toFile());

            String javaBin = ProcessHandle.current().info().command()
                    .orElse(System.getProperty("java.home") + "/bin/java");

            List<String> cmd = new ArrayList<>();
            cmd.add(javaBin);
            cmd.add("--add-opens");
            cmd.add("java.base/java.lang=ALL-UNNAMED");
            cmd.add("-Dtestfly.gatling.config=" + tempConfig.toAbsolutePath());
            // Pin the percentile slots of Gatling's own report: percentiles1..4 are p50/p75/p95/p99
            // regardless of any gatling.conf on the classpath (system properties win). TestFly's metrics
            // come from its sidecar; the pinned slots keep the HTML report and the test-only stats.js
            // cross-check aligned with them.
            cmd.add("-Dgatling.charting.indicators.percentile1=50");
            cmd.add("-Dgatling.charting.indicators.percentile2=75");
            cmd.add("-Dgatling.charting.indicators.percentile3=95");
            cmd.add("-Dgatling.charting.indicators.percentile4=99");
            // No outbound usage analytics from the child JVM: load tests only talk to the configured target
            cmd.add("-Dgatling.data.enableAnalytics=false");
            cmd.add("-cp");
            cmd.add(System.getProperty("java.class.path"));
            cmd.add("io.gatling.app.Gatling");
            cmd.add("-s");
            cmd.add(TestFlyGatlingSimulation.class.getName());
            cmd.add("-rf");
            cmd.add(resultsDir);
            cmd.add("-rd");
            String cleanDesc = runDescription != null ? runDescription.replaceAll("[^a-zA-Z0-9_-]", "_") : "loadtest";
            cmd.add(cleanDesc);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            File logFile = new File(resultsDir, "gatling-subprocess.log");
            pb.redirectErrorStream(true);
            pb.redirectOutput(logFile);
            Process process = pb.start();

            final Process child = process;
            shutdownHook = new Thread(() -> terminateTree(child), "testfly-gatling-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdownHook);

            long waitSeconds = runConfig.maxDurationSeconds() + WAIT_GRACE_SECONDS;
            boolean finished;
            try {
                finished = process.waitFor(waitSeconds, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                terminateTree(process);
                Thread.currentThread().interrupt();
                throw new IllegalStateException("[LoadTest] Gatling run for '" + runDescription
                        + "' was cancelled; child JVM terminated", e);
            }
            if (!finished) {
                terminateTree(process);
                throw new IllegalStateException("[LoadTest] Gatling run exceeded " + waitSeconds
                        + "s and was terminated");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new IllegalStateException("[LoadTest] Gatling subprocess failed with exit code "
                        + exitCode + " (" + exitCodeMeaning(exitCode) + "). Last lines of "
                        + logFile.getAbsolutePath() + " (redacted):" + logTail(logFile));
            }
        } finally {
            if (shutdownHook != null) {
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                } catch (IllegalStateException ignored) {
                    // JVM is already shutting down; the hook is running
                }
            }
            try {
                Files.deleteIfExists(tempConfig);
            } catch (IOException ignored) {
                // best effort
            }
        }
    }

    private static String exitCodeMeaning(int exitCode) {
        return switch (exitCode) {
            case 1 -> "invalid arguments or crash";
            case 2 -> "Gatling assertions failed";
            default -> "unexpected";
        };
    }

    private static String logTail(File logFile) {
        if (!logFile.exists()) {
            return "\n  [gatling] <no subprocess log>";
        }
        try {
            List<String> lines = Files.readAllLines(logFile.toPath(), StandardCharsets.UTF_8);
            return LogRedactor.redactedTail(lines, LOG_TAIL_LINES, LOG_TAIL_MAX_CHARS);
        } catch (IOException | RuntimeException e) {
            try {
                List<String> lines = Files.readAllLines(logFile.toPath(), StandardCharsets.ISO_8859_1);
                return LogRedactor.redactedTail(lines, LOG_TAIL_LINES, LOG_TAIL_MAX_CHARS);
            } catch (IOException | RuntimeException e2) {
                return "\n  [gatling] <subprocess log unreadable: " + e2.getClass().getSimpleName() + ">";
            }
        }
    }

    /**
     * Terminates the child and all its descendants: {@code destroy()}, wait up to 10s, then
     * {@code destroyForcibly()}, wait up to 5s.
     *
     * @return {@code true} if no process of the tree is alive afterwards
     */
    static boolean terminateTree(Process process) {
        List<ProcessHandle> tree = new ArrayList<>();
        try {
            process.descendants().forEach(tree::add);
        } catch (RuntimeException ignored) {
            // descendants may be unavailable; still handle the child
        }
        tree.add(process.toHandle());

        tree.forEach(ProcessHandle::destroy);
        awaitExit(tree, 10);
        tree.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
        awaitExit(tree, 5);

        List<Long> alive = tree.stream().filter(ProcessHandle::isAlive).map(ProcessHandle::pid).toList();
        if (!alive.isEmpty()) {
            System.err.println("[LoadTest] Gatling child process(es) still alive after termination: " + alive);
            return false;
        }
        return true;
    }

    private static void awaitExit(List<ProcessHandle> tree, long seconds) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        for (ProcessHandle p : tree) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                return;
            }
            try {
                p.onExit().get(remaining, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                // keep terminating; restore the flag for the caller
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ignored) {
                // timeout or failure: checked by isAlive() afterwards
            }
        }
    }

    private static Path createPrivateTempFile() throws IOException {
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            return Files.createTempFile("testfly-gatling-", ".json",
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        }
        return Files.createTempFile("testfly-gatling-", ".json");
    }
}
