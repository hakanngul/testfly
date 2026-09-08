package io.testfly.loadtest.internal;

import io.testfly.loadtest.LoadScenario;
import io.testfly.loadtest.LoadTestConfig;
import io.testfly.loadtest.LoadTestMetrics;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Gatling-based load-test engine — uses Gatling's async Netty HTTP engine
 * for high-concurrency load testing.
 *
 * <p>
 * Supports in-process reflection execution when {@code --add-opens} is present,
 * and seamlessly forks a subprocess with the required JVM argument when running
 * in IDE environments without pre-configured VM options.
 */
public final class GatlingEngine implements LoadTestEngine {

    private static final ReentrantLock GATLING_LOCK = new ReentrantLock();

    @Override
    public String name() {
        return "gatling";
    }

    @Override
    public boolean isAvailable() {
        return GatlingBridge.isAvailable();
    }

    @Override
    public LoadTestMetrics execute(LoadScenario scenario, LoadTestConfig config) {
        if (!GatlingBridge.isAvailable()) {
            throw new IllegalStateException(GatlingBridge.missingDependencyMessage());
        }

        if (scenario.steps().isEmpty()) {
            throw new IllegalArgumentException(
                    "[LoadTest] Scenario '" + scenario.name() + "' has no steps. " +
                            "Add at least one step via .get(), .post(), or .step().");
        }

        String baseUrl = config.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            try {
                baseUrl = io.testfly.internal.TestFlyContext.getConfig().getExecution().getBaseUrl();
            } catch (Exception e) {
                throw new IllegalStateException(
                        "[LoadTest] No baseUrl configured for Gatling engine.");
            }
        }

        // Dedicated subfolder for this specific run to prevent collisions in parallel
        // mode
        String runId = scenario.name().replaceAll("[^a-zA-Z0-9_-]", "_") + "-" + System.currentTimeMillis();
        File specificResultsDir = new File(config.getResultsDir(), runId);
        specificResultsDir.mkdirs();

        GATLING_LOCK.lock();
        try {
            System.out.println("[LoadTest] Starting Gatling engine: " + scenario.name()
                    + " (" + config.getUsers() + " users, rampUp=" + config.getRampUp()
                    + ", hold=" + config.getHold() + ")");

            GatlingRunConfig runConfig = new GatlingRunConfig(scenario, config, baseUrl);
            GatlingRunConfig.set(runConfig);

            if (GatlingBridge.isJavaLangOpened()) {
                // In-process run (fast, used in maven with --add-opens)
                runGatlingInProcess(specificResultsDir.getAbsolutePath(), TestFlyGatlingSimulation.class.getName(),
                        scenario.name());
            } else {
                // Forked run with required JVM flags (used in IDE runs with zero extra config)
                runGatlingForked(specificResultsDir.getAbsolutePath(), runConfig, scenario.name());
            }

            System.out.println("[LoadTest] Gatling execution complete. Parsing results...");

            // Parse results from the dedicated results directory
            LoadTestMetrics metrics = GatlingResultsParser.parse(specificResultsDir.getAbsolutePath(), scenario.name(),
                    config.getUsers());
            if (metrics.totalRequests() == 0) {
                throw new IllegalStateException(
                        "[LoadTest] Gatling simulation finished but 0 requests were recorded for scenario '" +
                                scenario.name() + "'. Check console logs for errors.");
            }
            return metrics;

        } catch (Exception e) {
            Throwable root = e instanceof java.lang.reflect.InvocationTargetException ite ? ite.getCause() : e;
            String msg = root != null && root.getMessage() != null ? root.getMessage() : e.getMessage();
            System.err.println("[LoadTest] Gatling execution failed: " + msg);
            throw new IllegalStateException("[LoadTest] Gatling execution failed: " + msg, root);
        } finally {
            GatlingRunConfig.clear();
            GATLING_LOCK.unlock();
        }
    }

    /**
     * Invokes {@code io.gatling.app.Gatling$.MODULE$.fromArgs(args)} via
     * reflection.
     */
    private void runGatlingInProcess(String resultsDir, String simulationClass, String runDescription)
            throws Exception {
        Class<?> gatlingCompanion = Class.forName("io.gatling.app.Gatling$");
        Field moduleField = gatlingCompanion.getField("MODULE$");
        Object module = moduleField.get(null);
        Method fromArgsMethod = gatlingCompanion.getMethod("fromArgs", String[].class);

        String[] args = new String[] {
                "-s", simulationClass,
                "-rf", resultsDir,
                "-rd", runDescription
        };

        int exitCode = (int) fromArgsMethod.invoke(module, (Object) args);
        if (exitCode != 0) {
            System.err.println("[LoadTest] Gatling in-process finished with exit code: " + exitCode);
        }
    }

    /**
     * Forks a new JVM process with
     * {@code --add-opens java.base/java.lang=ALL-UNNAMED}
     * so Gatling can run without requiring manual IDE VM options.
     */
    private void runGatlingForked(String resultsDir, GatlingRunConfig runConfig, String runDescription)
            throws Exception {
        File tempConfig = File.createTempFile("testfly-gatling-", ".json");
        try {
            GatlingRunConfig.save(runConfig, tempConfig);

            String javaBin = ProcessHandle.current().info().command()
                    .orElse(System.getProperty("java.home") + "/bin/java");

            List<String> cmd = new ArrayList<>();
            cmd.add(javaBin);
            cmd.add("--add-opens");
            cmd.add("java.base/java.lang=ALL-UNNAMED");
            cmd.add("-Dtestfly.gatling.config=" + tempConfig.getAbsolutePath());
            cmd.add("-cp");
            cmd.add(System.getProperty("java.class.path"));
            cmd.add("io.gatling.app.Gatling");
            cmd.add("-s");
            cmd.add(TestFlyGatlingSimulation.class.getName());
            cmd.add("-rf");
            cmd.add(resultsDir);
            cmd.add("-rd");
            cmd.add(runDescription);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.inheritIO();
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                System.err.println("[LoadTest] Gatling subprocess finished with exit code: " + exitCode);
            }
        } finally {
            tempConfig.delete();
        }
    }
}
