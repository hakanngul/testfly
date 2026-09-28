package io.testfly.cucumber;

import io.testfly.browser.BrowserContext;
import io.testfly.quarantine.QuarantineLoader;
import io.testfly.browser.ConsoleErrorCollector;
import io.testfly.client.ApiClient;
import io.testfly.clock.TestClock;
import io.testfly.context.ScenarioContext;
import io.testfly.driver.DriverManager;
import io.testfly.hooks.HookRegistry;
import io.testfly.internal.TestFlyContext;
import io.testfly.lifecycle.FrameworkBootstrap;
import io.testfly.metrics.ExecutionMetrics;
import io.testfly.network.NetworkMock;
import io.testfly.reporting.JUnitXmlReporter;
import io.testfly.reporting.ReportAdapterRegistry;
import io.testfly.reporting.ScreenshotManager;
import io.testfly.steps.StepLogger;
import io.testfly.steps.StepStatus;
import io.testfly.testdata.TestDataStore;
import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.testfly.config.TestFlyConfig;
import io.testfly.recording.RecordingManager;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;

/**
 * TestFly lifecycle hooks for Cucumber scenarios.
 *
 * <p>
 * Auto-discovered by Cucumber when {@code "io.testfly.cucumber"} is
 * included in {@code @CucumberOptions(glue = {...})}.
 *
 * <p>
 * Lifecycle per scenario:
 * <ol>
 * <li>{@code @Before(order=1000)} — bootstrap framework, create WebDriver,
 * start timing.</li>
 * <li>Cucumber steps run; step names logged via
 * {@link CucumberStepLogger}.</li>
 * <li>{@code @After(order=20000)} — screenshot on failure, record metrics, quit
 * driver.</li>
 * </ol>
 *
 * <p>
 * Order rationale:
 * <ul>
 * <li>{@code @Before(order=1000)}: runs before user's
 * {@code @Before(order=10000)},
 * so the driver is ready when user hooks execute.</li>
 * <li>{@code @After(order=20000)}: Cucumber runs higher-order {@code @After}
 * first,
 * so this runs before user's {@code @After(order=10000)},
 * ensuring screenshot is captured while the page is still loaded.</li>
 * </ul>
 */
public class CucumberHooks {

    @Before(order = 1000)
    public void beforeScenario(Scenario scenario) {
        // 1. Ensure framework is bootstrapped (idempotent)
        FrameworkBootstrap.initialize();

        // 2. Quarantine check — skip before creating any browser session
        checkCucumberQuarantine(scenario);

        // 3. Derive a unique, readable testId for this scenario
        String testId = buildTestId(scenario);

        // 4. Store scenario on thread so BaseCucumberSteps.getScenario() works
        CucumberContext.setScenario(scenario);

        // 5. Register testId so StepLogger and ScreenshotManager resolve it
        TestFlyContext.setCurrentTestId(testId);

        // 6. Per-scenario retry tag — set/clear before RetryListener fires
        applyRetryTag(scenario);

        // 7. Initialize metrics — detect retry when testId already exists
        if (ExecutionMetrics.getTiming(testId) != null) {
            ExecutionMetrics.recordRetry(testId);
        }
        ExecutionMetrics.clearSteps(testId);
        ExecutionMetrics.markStart(testId);
        ExecutionMetrics.recordTestClass(testId, featureTitle(scenario.getUri()));
        ExecutionMetrics.recordDescription(testId, scenario.getName());

        // 8. Create WebDriver (acquires session semaphore slot)
        boolean noBrowser = skipBrowser(scenario);
        if (!noBrowser) {
            DriverManager.createDriver();
            startRecordingIfEnabled();
        }

        // 9. Notify plugins
        HookRegistry.onTestStart(testId);
    }

    private void startRecordingIfEnabled() {
        try {
            TestFlyConfig cfg = TestFlyContext.getConfig();
            TestFlyConfig.Recording rec = cfg != null ? cfg.getRecording() : null;
            if (rec == null || !rec.shouldRecord())
                return;
            org.openqa.selenium.WebDriver driver = DriverManager.getDriver();
            if (driver == null)
                return;
            RecordingManager.start(driver, rec.getFps(), rec.getMaxDurationSeconds(), rec.isCdp());
            System.out.println(
                    "[TestFly] 🎥 Video recording started (mode=" + rec.getMode() + ", fps=" + rec.getFps() + ")");
        } catch (Exception e) {
            System.err.println("[TestFly] Failed to start video recording: " + e.getMessage());
        }
    }

    @After(order = 20000)
    public void afterScenario(Scenario scenario) {
        String testId = TestFlyContext.getCurrentTestId();
        boolean noBrowser = skipBrowser(scenario);

        if (testId == null) {
            RecordingManager.stop();
            if (!noBrowser) {
                safeQuitDriver();
            }
            CucumberContext.clear();
            return;
        }

        try {
            boolean failed = scenario.isFailed();
            String status = resolveStatus(scenario);
            TestFlyConfig cfg = TestFlyContext.getConfig();
            TestFlyConfig.Recording rec = cfg != null ? cfg.getRecording() : null;

            if (failed) {
                // 1. Capture screenshot once for both TestFly HTML report and Cucumber report
                String screenshotPath = null;
                try {
                    screenshotPath = ScreenshotManager.capture(sanitize(scenario.getName()));
                    if (screenshotPath != null) {
                        ExecutionMetrics.recordScreenshot(testId, screenshotPath);
                        File scFile = new File(screenshotPath);
                        if (scFile.exists()) {
                            scenario.attach(Files.readAllBytes(scFile.toPath()), "image/png", "Failure Screenshot");
                        }
                    }
                } catch (Throwable ignored) {
                }

                // 2. Save video recording on failure (only if recording is enabled and browser
                // is used)
                String recordingPath = null;
                boolean shouldRecord = rec != null && rec.shouldRecord() && !noBrowser;
                if (shouldRecord) {
                    try {
                        recordingPath = RecordingManager.saveOnFailure(testId);
                        if (recordingPath != null) {
                            ExecutionMetrics.recordRecording(testId, recordingPath);
                            System.out.println("[TestFly] 🎥 Video recording saved: " + recordingPath);
                            File recFile = new File(recordingPath);
                            if (recFile.exists()) {
                                boolean isMp4 = recFile.getName().toLowerCase().endsWith(".mp4");
                                String mime = isMp4 ? "video/mp4" : "image/gif";
                                scenario.attach(Files.readAllBytes(recFile.toPath()), mime, "Execution Video");
                            }
                        }
                    } catch (Throwable e) {
                        System.err.println("[TestFly] Failed to save video recording on failure: " + e.getMessage());
                    }
                }

                try {
                    HookRegistry.onTestFailure(testId, new RuntimeException("Scenario failed: " + scenario.getName()));
                } catch (Throwable ignored) {
                }

                // 3. AI failure analysis — only query driver if AI is actually enabled and
                // configured
                if (isAiAnalysisEnabled(cfg)) {
                    try {
                        String pageUrl = null;
                        String pageTitle = null;
                        try {
                            org.openqa.selenium.WebDriver driver = DriverManager.getDriver();
                            if (driver != null) {
                                pageUrl = driver.getCurrentUrl();
                                pageTitle = driver.getTitle();
                            }
                        } catch (Throwable ignored) {
                        }
                        io.testfly.ai.AiFailureAnalyzer.analyze(testId, pageUrl, pageTitle);
                    } catch (Throwable ignored) {
                    }
                }
            } else {
                if (rec != null && rec.isRecordAll() && !noBrowser) {
                    try {
                        String recordingPath = RecordingManager.save(testId);
                        if (recordingPath != null) {
                            ExecutionMetrics.recordRecording(testId, recordingPath);
                            System.out.println("[TestFly] 🎥 Video recording saved: " + recordingPath);
                            File recFile = new File(recordingPath);
                            if (recFile.exists()) {
                                boolean isMp4 = recFile.getName().toLowerCase().endsWith(".mp4");
                                String mime = isMp4 ? "video/mp4" : "image/gif";
                                scenario.attach(Files.readAllBytes(recFile.toPath()), mime, "Execution Video");
                            }
                        }
                    } catch (Throwable e) {
                        System.err.println("[TestFly] Failed to save video recording: " + e.getMessage());
                    }
                } else {
                    RecordingManager.stop(); // discard frames — test passed in retain-on-failure mode
                }
            }

            // Collect any JS console errors captured during the scenario
            if (ConsoleErrorCollector.isEnabled()) {
                try {
                    List<String> errors = ConsoleErrorCollector.collect();
                    errors.forEach(e -> StepLogger.step("[JS Error] " + e, StepStatus.WARN));
                } catch (Throwable ignored) {
                }
            }

            // Flush soft assertions — log failures and fail scenario if not already failed
            io.testfly.assertion.SoftAssertionCollector softCollector = io.testfly.assertion.SoftAssertions.get();
            if (softCollector.hasFailed()) {
                List<String> softFailures = softCollector.getFailures();
                softFailures.forEach(msg -> StepLogger.step("[Soft Assertion Failed] " + msg, StepStatus.FAIL));
                if (!failed) {
                    throw new AssertionError(softFailures.size() + " soft assertion(s) failed:\n" + String.join("\n", softFailures));
                }
            }

            ExecutionMetrics.recordStatus(testId, status);
            ExecutionMetrics.markEnd(testId);
            try {
                HookRegistry.onTestEnd(testId, status);
            } catch (Throwable ignored) {
            }

        } finally {
            try {
                String status = resolveStatus(scenario);
                io.testfly.metrics.TestTiming timing = ExecutionMetrics.getTiming(testId);
                if (timing != null && "UNKNOWN".equals(timing.getStatus())) {
                    ExecutionMetrics.recordStatus(testId, status);
                }
                ExecutionMetrics.markEnd(testId);
            } catch (Throwable ignored) {
            }

            RecordingManager.stop();
            if (!noBrowser) {
                safeQuitDriver();
            }
            io.testfly.session.MultiSessionManager.clearAll();
            io.testfly.assertion.SoftAssertions.clear();
            ScenarioContext.clear();
            TestDataStore.clear();
            ApiClient.clearGlobalAuth();
            BrowserContext.clear();
            NetworkMock.cleanup();
            TestClock.autoReset();
            CucumberContext.clear();
            TestFlyContext.clearCurrentTestId();
        }
    }

    private static boolean isAiAnalysisEnabled(TestFlyConfig cfg) {
        if (cfg == null)
            return false;
        TestFlyConfig.Ai ai = cfg.getAi();
        return ai != null && ai.isEnabled() && (ai.isFailureAnalysis() || ai.isGeneratePatch());
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds a unique, readable testId.
     *
     * Format: {@code <feature-filename>#<scenario-name>[L<line>]}
     *
     * Examples:
     * {@code login.feature#User logs in with valid credentials[L12]}
     * {@code checkout.feature#Purchase as {string}[L34]} ← outline example at line
     * 34
     */
    static String buildTestId(Scenario scenario) {
        String feature = extractFileName(scenario.getUri());
        String name = scenario.getName() != null ? scenario.getName() : "unnamed";
        int line = scenario.getLine() != null ? scenario.getLine() : 0;
        return feature + "#" + name + "[L" + line + "]";
    }

    private static String extractFileName(URI uri) {
        if (uri == null)
            return "unknown.feature";
        String path = uri.toString();
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static String featureTitle(URI uri) {
        String name = extractFileName(uri);
        return name.endsWith(".feature") ? name.substring(0, name.length() - 8) : name;
    }

    private static String resolveStatus(Scenario scenario) {
        if (scenario.isFailed())
            return "FAILED";
        io.cucumber.java.Status s = scenario.getStatus();
        if (s == io.cucumber.java.Status.SKIPPED
                || s == io.cucumber.java.Status.PENDING
                || s == io.cucumber.java.Status.UNDEFINED)
            return "SKIPPED";
        return "PASSED";
    }

    private static String sanitize(String input) {
        return input == null ? "unnamed" : input.replaceAll("[^a-zA-Z0-9\\-_.]", "_");
    }

    /**
     * Reads the {@code @retryable} or {@code @retryable=N} tag from the scenario
     * and stores the max-retry count in {@link CucumberRetryContext} so that
     * {@link io.testfly.listeners.RetryListener} can apply it after the scenario
     * finishes.
     *
     * <p>
     * Tag formats:
     * <ul>
     * <li>{@code @retryable} — use the global {@code retry.maxAttempts} from
     * config</li>
     * <li>{@code @retryable=2} — exactly 2 retries regardless of config</li>
     * </ul>
     *
     * <p>
     * If no {@code @retryable} tag is present, any prior override is cleared so the
     * global config applies unchanged.
     */
    private static void applyRetryTag(Scenario scenario) {
        for (String tag : scenario.getSourceTagNames()) {
            String normalized = tag.startsWith("@") ? tag.substring(1) : tag;
            if (normalized.equalsIgnoreCase("retryable")) {
                // No value — use global config (signal with -1 cleared, rely on config)
                CucumberRetryContext.clear();
                return;
            }
            if (normalized.toLowerCase().startsWith("retryable=")) {
                String value = normalized.substring("retryable=".length()).trim();
                try {
                    CucumberRetryContext.set(Integer.parseInt(value));
                } catch (NumberFormatException e) {
                    CucumberRetryContext.clear();
                }
                return;
            }
        }
        // No @retryable tag — clear any leftover from a prior scenario on this thread
        CucumberRetryContext.clear();
    }

    private static void checkCucumberQuarantine(Scenario scenario) {
        try {
            io.testfly.config.TestFlyConfig.Quarantine cfg = TestFlyContext.getConfig().getQuarantine();
            if (cfg != null && !cfg.isEnabled())
                return;

            // ── 1. In-file tag check (user adds @quarantine to the scenario) ──
            String configuredTag = (cfg != null && cfg.getCucumberTag() != null)
                    ? cfg.getCucumberTag()
                    : "quarantine";
            for (String t : scenario.getSourceTagNames()) {
                String normalized = t.startsWith("@") ? t.substring(1) : t;
                if (normalized.equalsIgnoreCase(configuredTag)) {
                    throw new org.testng.SkipException(
                            "[Quarantined] " + scenario.getName() + " — @" + configuredTag + " tag present");
                }
            }

            // ── 2. YAML-based check (tag, feature file, or feature#scenario entries) ──
            String featureUri = scenario.getUri() != null ? scenario.getUri().toString() : "";
            String scenarioName = scenario.getName() != null ? scenario.getName() : "";
            java.util.Collection<String> tags = scenario.getSourceTagNames();

            if (QuarantineLoader.isQuarantinedScenario(tags, featureUri, scenarioName)) {
                throw new org.testng.SkipException(
                        "[Quarantined] " + scenarioName + " — "
                                + QuarantineLoader.getScenarioReason(tags, featureUri, scenarioName));
            }
        } catch (org.testng.SkipException e) {
            throw e;
        } catch (Exception ignored) {
        }
    }

    private void safeQuitDriver() {
        try {
            if (DriverManager.shouldQuitAfterTest()) {
                DriverManager.quitDriver();
            } else {
                org.openqa.selenium.WebDriver driver = DriverManager.getDriver();
                if (driver != null) {
                    try {
                        driver.manage().deleteAllCookies();
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[CucumberHooks] Driver teardown failed: " + e.getMessage());
        }
    }

    private boolean skipBrowser(Scenario scenario) {
        if (scenario == null || scenario.getSourceTagNames() == null) {
            return false;
        }
        return scenario.getSourceTagNames().stream().anyMatch(t -> {
            String lower = t.toLowerCase();
            return lower.equals("@nobrowser") || lower.equals("@api") || lower.contains("loadtest");
        });
    }

    /**
     * Suite-level teardown for Cucumber runs.
     *
     * <p>
     * Exports metrics, generates HTML/JUnit XML reports, and quits all
     * persistent per-suite drivers when running via Cucumber CLI, JUnit, or IDE,
     * ensuring browsers never remain open and reports are always produced —
     * even when TestNG's {@code SuiteExecutionListener} is not in the picture.
     */
    @AfterAll(order = 0)
    public static void afterAllScenarios() {
        try {
            // ── Report generation (mirrors SuiteExecutionListener.onFinish) ──
            ExecutionMetrics.printSummary();
            ExecutionMetrics.exportToJson();
            io.testfly.healing.HealLog.export();
            io.testfly.flakiness.FlakinessAnalyzer.analyze();
            JUnitXmlReporter.export(ExecutionMetrics.getTimings(), System.currentTimeMillis());
            ReportAdapterRegistry.generateAll();
            HookRegistry.onSuiteEnd();
        } catch (Throwable t) {
            System.err.println("[CucumberHooks] Report generation failed: " + t.getMessage());
        }

        try {
            io.testfly.precondition.PreConditionRunner.clearAll();
            DriverManager.quitAllSuiteDrivers();
            DriverManager.forceQuitDriver();
        } catch (Throwable t) {
            System.err.println("[CucumberHooks] afterAll driver cleanup failed: " + t.getMessage());
        }
    }
}
