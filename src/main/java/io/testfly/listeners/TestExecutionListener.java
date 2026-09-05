package io.testfly.listeners;

import io.testfly.assertion.SoftAssertionCollector;
import io.testfly.assertion.SoftAssertions;
import io.testfly.browser.BrowserContext;
import io.testfly.browser.ConsoleErrorCollector;
import io.testfly.client.ApiAuth;
import io.testfly.client.ApiClient;
import io.testfly.client.UseAuth;
import io.testfly.config.TestFlyConfig;
import io.testfly.context.ScenarioContext;
import io.testfly.db.DbConnectionFactory;
import io.testfly.driver.DriverManager;
import io.testfly.hooks.HookRegistry;
import io.testfly.internal.TestFlyContext;
import io.testfly.metrics.ExecutionMetrics;
import io.testfly.metrics.TestTiming;
import io.testfly.ai.AiFailureAnalyzer;
import io.testfly.network.NetworkMock;
import io.testfly.precondition.ApiHealthChecker;
import io.testfly.tracing.TraceRecorder;
import io.testfly.precondition.DependsOnApi;
import io.testfly.precondition.PreConditionRunner;
import io.testfly.recording.RecordingManager;
import io.testfly.reporting.ScreenshotManager;
import io.testfly.email.MailboxClient;
import io.testfly.clock.TestClock;
import io.testfly.performance.PerformanceCollector;
import io.testfly.quarantine.QuarantineLoader;
import io.testfly.session.MultiSessionManager;
import io.testfly.steps.StepLogger;
import io.testfly.steps.StepStatus;
import io.testfly.test.BaseApiTest;
import io.testfly.test.NoBrowser;
import io.testfly.testmanagement.TestManagementReporter;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.util.List;

/**
 * TestExecutionListener manages the per-test method lifecycle within TestFly.
 *
 * <p>Responsibilities:
 * <ul>
 *     <li>Creates a WebDriver instance at the start of each test method.</li>
 *     <li>Ensures the WebDriver is terminated after test completion
 *         (success, failure, or skip).</li>
 *     <li>Captures failure artifacts (e.g., screenshots) before driver shutdown.</li>
 *     <li>Sends failure artifacts to ReportPortal from
 *         {@link IInvokedMethodListener#afterInvocation}, before the ReportPortal
 *         TestNG listener closes the failing test item.</li>
 * </ul>
 *
 * <p>Design Principles:
 * <ul>
 *     <li>One test method = one WebDriver session.</li>
 *     <li>Thread-safe execution using ThreadLocal driver management.</li>
 *     <li>Deterministic cleanup to prevent session leaks under parallel load.</li>
 * </ul>
 *
 * <p>This listener does not manage suite-level initialization.
 * Global setup is handled by {@code SuiteExecutionListener}.
 */

public final class TestExecutionListener implements ITestListener, IInvokedMethodListener {

    /** Tracks whether JS errors have already been logged for this test (prevents double-logging on failure redirect). */
    private static final ThreadLocal<Boolean> jsErrorsLogged = ThreadLocal.withInitial(() -> false);

    /**
     * Tracks whether failure artifacts (screenshot, AI analysis) were already captured
     * and sent to ReportPortal during {@link IInvokedMethodListener#afterInvocation}.
     * RP closes the test item in its own {@code onTestFailure}, so attachments must be
     * emitted early; this flag prevents double-capture / double-sending when
     * {@code onTestFailure} runs later.
     */
    private static final ThreadLocal<Boolean> failureArtifactsHandled = ThreadLocal.withInitial(() -> false);

    @Override
    public void onTestStart(ITestResult result) {
        if (isCucumberScenario(result)) return;
        String testId = result.getMethod().getQualifiedName();
        failureArtifactsHandled.set(false);    // fresh attempt
        TestFlyContext.setCurrentTestId(testId);
        ExecutionMetrics.clearSteps(testId);   // discard stale steps from prior retry attempt
        ExecutionMetrics.markStart(testId);
        ExecutionMetrics.recordTestClass(testId, result.getTestClass().getRealClass().getSimpleName());
        ExecutionMetrics.recordDescription(testId, result.getMethod().getDescription());
        // Set browser override BEFORE creating driver so DriverProviderFactory can read it
        String browserOverride = result.getTestContext().getCurrentXmlTest()
                .getParameter("testfly.browser");
        if (browserOverride != null && !browserOverride.isEmpty()) {
            BrowserContext.set(browserOverride);
            ExecutionMetrics.recordBrowser(testId, browserOverride);
        }
        // Quarantine check — skip before any resource is allocated
        checkQuarantine(result);

        // API health checks — skip immediately if a dependency is down,
        // before creating a browser session so no resources are wasted.
        checkApiDependencies(result);

        if (!skipBrowser(result)) {
            DriverManager.createDriver();
            String sessionUrl = DriverManager.getCloudSessionUrl();
            if (sessionUrl != null) ExecutionMetrics.recordSessionUrl(testId, sessionUrl);
            startRecordingIfEnabled();
        }
        autoClearEmailIfEnabled();
        applyUseAuth(result);
        PreConditionRunner.run(result);
        loadTestData(result);
        HookRegistry.onTestStart(testId);
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        if (isCucumberScenario(result)) return;
        String testId = result.getMethod().getQualifiedName();

        if (!skipBrowser(result) && ConsoleErrorCollector.isEnabled()) {
            List<String> errors = ConsoleErrorCollector.collect();
            errors.forEach(e -> StepLogger.step("[JS Error] " + e, StepStatus.WARN));
            jsErrorsLogged.set(true);

            boolean failOnErrors = false;
            try { failOnErrors = TestFlyContext.getConfig().getBrowser().isFailOnConsoleErrors(); } catch (Exception ignored) {}

            if (failOnErrors && !errors.isEmpty()) {
                result.setStatus(ITestResult.FAILURE);
                result.setThrowable(new AssertionError("JS console errors detected (" + errors.size() + "): " + errors));
                onTestFailure(result);
                return;
            }
        }

        // Flush soft assertions — if any failed, redirect to failure path
        SoftAssertionCollector collector = SoftAssertions.get();
        if (collector.hasFailed()) {
            List<String> softFailures = collector.getFailures();
            // Log each failure as a step entry
            softFailures.forEach(msg ->
                StepLogger.step("[Soft Assertion Failed] " + msg, StepStatus.FAIL));
            // Single screenshot at flush time
            String screenshotPath = !skipBrowser(result) ? ScreenshotManager.capture(result.getMethod().getMethodName()) : null;
            ExecutionMetrics.recordScreenshot(result.getMethod().getQualifiedName(), screenshotPath);
            // Build combined error message
            String combined = softFailures.size() + " soft assertion(s) failed:\n" +
                String.join("\n", softFailures);
            result.setStatus(ITestResult.FAILURE);
            result.setThrowable(new AssertionError(combined));
            SoftAssertions.clear();
            onTestFailure(result);
            return;
        }
        SoftAssertions.clear();

        io.testfly.config.TestFlyConfig cfg = TestFlyContext.getConfig();
        io.testfly.config.TestFlyConfig.Recording rec = cfg != null ? cfg.getRecording() : null;
        if (rec != null && rec.isRecordAll() && !skipBrowser(result)) {
            String recPath = RecordingManager.save(testId);
            ExecutionMetrics.recordRecording(testId, recPath);
        } else {
            RecordingManager.stop(); // discard frames — test passed in retain-on-failure mode
        }
        capturePerformanceIfEnabled(testId, result);
        ExecutionMetrics.recordStatus(testId, "PASSED");
        ExecutionMetrics.markEnd(testId);
        saveTraceIfEnabled(testId, result.getMethod().getMethodName(), true);
        HookRegistry.onTestEnd(testId, "PASSED");
        TestManagementReporter.getInstance().onTestResult(
                result.getMethod().getConstructorOrMethod().getMethod(), "PASSED", null);
        TestClock.autoReset();
        if (!skipBrowser(result) && DriverManager.shouldQuitAfterTest()) DriverManager.quitDriver();
        MultiSessionManager.clearAll();
        DbConnectionFactory.closeAll();
        io.testfly.testdata.TestDataStore.clear();
        ScenarioContext.clear();
        io.testfly.client.ApiClient.clearGlobalAuth();
        BrowserContext.clear();
        NetworkMock.cleanup();
        TestFlyContext.clearCurrentTestId();
        jsErrorsLogged.set(false);
    }

    @Override
    public void onTestFailure(ITestResult result) {
        if (isCucumberScenario(result)) return;
        String testName = result.getMethod().getMethodName();
        String testId = result.getMethod().getQualifiedName();

        // Log failure IMMEDIATELY — before ReportPortal closes the test item
        org.slf4j.LoggerFactory.getLogger(TestExecutionListener.class)
            .info("❌ Test failed: {}", testId);

        if (!skipBrowser(result) && ConsoleErrorCollector.isEnabled() && !jsErrorsLogged.get()) {
            ConsoleErrorCollector.collect().forEach(e -> StepLogger.step("[JS Error] " + e, StepStatus.WARN));
        }
        String recordingPath = skipBrowser(result) ? null : RecordingManager.saveOnFailure(testId);
        ExecutionMetrics.recordRecording(testId, recordingPath);
        if (recordingPath != null) {
            System.out.println("[TestFly] 🎥 Video recording saved: " + recordingPath);
        }
        ExecutionMetrics.recordStatus(testId, "FAILED");
        ExecutionMetrics.markEnd(testId);
        if (result.getThrowable() != null) {
            ExecutionMetrics.recordError(testId, result.getThrowable());
        }
        saveTraceIfEnabled(testId, result.getMethod().getMethodName(), false);

        // Capture screenshot + AI analysis and send to ReportPortal while the item is open.
        // If afterInvocation already handled this, skip to avoid duplicates.
        if (!Boolean.TRUE.equals(failureArtifactsHandled.get())) {
            captureFailureArtifacts(result, testId, testName);
        }

        HookRegistry.onTestFailure(testId, result.getThrowable());
        String failureComment = result.getThrowable() != null ? result.getThrowable().getMessage() : null;
        TestManagementReporter.getInstance().onTestResult(
                result.getMethod().getConstructorOrMethod().getMethod(), "FAILED", failureComment);
        TestClock.autoReset();
        if (!skipBrowser(result) && DriverManager.shouldQuitAfterTest()) DriverManager.quitDriver();
        MultiSessionManager.clearAll();
        DbConnectionFactory.closeAll();
        io.testfly.testdata.TestDataStore.clear();
        ScenarioContext.clear();
        io.testfly.client.ApiClient.clearGlobalAuth();
        BrowserContext.clear();
        SoftAssertions.clear();
        NetworkMock.cleanup();
        TestFlyContext.clearCurrentTestId();
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        if (isCucumberScenario(result)) return;
        String testId = result.getMethod().getQualifiedName();
        ExecutionMetrics.recordStatus(testId, "SKIPPED");
        ExecutionMetrics.markEnd(testId);
        HookRegistry.onTestEnd(testId, "SKIPPED");
        TestManagementReporter.getInstance().onTestResult(
                result.getMethod().getConstructorOrMethod().getMethod(), "SKIPPED", null);
        TestClock.autoReset();
        if (!skipBrowser(result) && DriverManager.shouldQuitAfterTest()) DriverManager.quitDriver();
        MultiSessionManager.clearAll();
        DbConnectionFactory.closeAll();
        io.testfly.testdata.TestDataStore.clear();
        ScenarioContext.clear();
        io.testfly.client.ApiClient.clearGlobalAuth();
        BrowserContext.clear();
        SoftAssertions.clear();
        TestFlyContext.clearCurrentTestId();
    }

    private void checkQuarantine(ITestResult result) {
        try {
            io.testfly.config.TestFlyConfig.Quarantine cfg =
                    TestFlyContext.getConfig().getQuarantine();
            if (cfg != null && !cfg.isEnabled()) return;
        } catch (Exception ignored) {}

        String className  = result.getTestClass().getRealClass().getName();
        String methodName = result.getMethod().getMethodName();
        String testId     = className + "#" + methodName;

        if (QuarantineLoader.isQuarantined(testId)) {
            throw new org.testng.SkipException(
                "[Quarantined] " + testId + " — " + QuarantineLoader.getReason(testId)
            );
        }
    }

    private void checkApiDependencies(ITestResult result) {
        java.lang.reflect.Method method = result.getMethod().getConstructorOrMethod().getMethod();
        DependsOnApi[] methodLevel = method.getAnnotationsByType(DependsOnApi.class);
        DependsOnApi[] classLevel  = result.getTestClass().getRealClass().getAnnotationsByType(DependsOnApi.class);

        // Method-level takes precedence; fall back to class-level
        DependsOnApi[] deps = methodLevel.length > 0 ? methodLevel : classLevel;
        for (DependsOnApi dep : deps) {
            ApiHealthChecker.checkOrSkip(dep.value(), dep.timeoutSeconds());
        }
    }

    private void startRecordingIfEnabled() {
        try {
            io.testfly.config.TestFlyConfig cfg = TestFlyContext.getConfig();
            io.testfly.config.TestFlyConfig.Recording rec = cfg != null ? cfg.getRecording() : null;
            if (rec == null || !rec.shouldRecord()) return;
            org.openqa.selenium.WebDriver driver = DriverManager.getDriver();
            if (driver == null) return;
            RecordingManager.start(driver, rec.getFps(), rec.getMaxDurationSeconds(), rec.isCdp());
            System.out.println("[TestFly] 🎥 Video recording started (mode=" + rec.getMode() + ", fps=" + rec.getFps() + ")");
        } catch (Exception e) {
            System.err.println("[TestFly] Failed to start video recording: " + e.getMessage());
        }
    }

    private boolean isApiTest(ITestResult result) {
        return BaseApiTest.class.isAssignableFrom(result.getTestClass().getRealClass());
    }

    private void autoClearEmailIfEnabled() {
        try {
            io.testfly.config.TestFlyConfig.Email emailCfg =
                    TestFlyContext.getConfig().getEmail();
            if (emailCfg != null && emailCfg.isAutoClear()) {
                MailboxClient.create().clear();
            }
        } catch (Exception ignored) {}
    }

    private boolean isNoBrowserTest(ITestResult result) {
        java.lang.reflect.Method m = result.getMethod().getConstructorOrMethod().getMethod();
        return m.isAnnotationPresent(NoBrowser.class) ||
               result.getTestClass().getRealClass().isAnnotationPresent(NoBrowser.class);
    }

    /** Returns true for tests that must not create/use a WebDriver. */
    private boolean skipBrowser(ITestResult result) {
        return isApiTest(result) || isNoBrowserTest(result);
    }

    private void applyUseAuth(ITestResult result) {
        UseAuth annotation = result.getMethod().getConstructorOrMethod().getMethod()
                .getAnnotation(UseAuth.class);
        if (annotation == null) {
            annotation = result.getTestClass().getRealClass().getAnnotation(UseAuth.class);
        }
        if (annotation == null) return;

        String strategyName = annotation.value();
        try {
            TestFlyConfig.Api api = TestFlyContext.getConfig().getApi();
            if (api == null || api.getAuth() == null) return;
            TestFlyConfig.Api.AuthStrategy strategy = api.getAuth().get(strategyName);
            if (strategy == null) {
                throw new IllegalStateException("[UseAuth] No auth strategy named '" + strategyName + "' found in api.auth config");
            }
            ApiAuth auth = resolveAuthStrategy(strategy);
            if (auth != null) ApiClient.setGlobalAuth(auth);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("[UseAuth] Failed to apply auth strategy '" + strategyName + "'", e);
        }
    }

    private ApiAuth resolveAuthStrategy(TestFlyConfig.Api.AuthStrategy s) {
        String type = s.getType();
        if (type == null) return null;
        return switch (type.toLowerCase()) {
            case "bearer" -> ApiAuth.bearerToken(resolveEnvVar(s.getToken()));
            case "basic"  -> ApiAuth.basicAuth(resolveEnvVar(s.getUsername()), resolveEnvVar(s.getPassword()));
            case "oauth2" -> ApiAuth.oauth2(resolveEnvVar(s.getTokenUrl()),
                                            resolveEnvVar(s.getClientId()),
                                            resolveEnvVar(s.getClientSecret()));
            case "apikey", "api_key", "apikey-header" -> ApiAuth.apiKey(
                    s.getHeaderName() != null ? s.getHeaderName() : "X-Api-Key",
                    resolveEnvVar(s.getApiKey() != null ? s.getApiKey() : s.getToken()));
            case "apikey-query", "api_key_query" -> ApiAuth.apiKeyQuery(
                    s.getHeaderName() != null ? s.getHeaderName() : "api_key",
                    resolveEnvVar(s.getApiKey() != null ? s.getApiKey() : s.getToken()));
            case "digest" -> ApiAuth.digest(resolveEnvVar(s.getUsername()), resolveEnvVar(s.getPassword()));
            case "hmac"   -> ApiAuth.hmac(resolveEnvVar(s.getApiKey()), resolveEnvVar(s.getSecret()), s.getAlgorithm());
            case "oauth2_password", "oauth2-password", "password" -> ApiAuth.oauth2Password(
                    resolveEnvVar(s.getTokenUrl()), resolveEnvVar(s.getClientId()),
                    resolveEnvVar(s.getClientSecret()), resolveEnvVar(s.getUsername()), resolveEnvVar(s.getPassword()));
            default       -> throw new IllegalArgumentException(
                "[UseAuth] Unknown auth type: '" + type + "'. Use bearer, basic, oauth2, apiKey, digest, hmac, oauth2_password");
        };
    }

    private String resolveEnvVar(String value) {
        if (value == null) return null;
        if (value.startsWith("${") && value.endsWith("}")) {
            String varName = value.substring(2, value.length() - 1);
            String resolved = System.getenv(varName);
            if (resolved == null) resolved = System.getProperty(varName);
            return resolved != null ? resolved : value;
        }
        return value;
    }

    private void loadTestData(ITestResult result) {
        io.testfly.testdata.TestData annotation =
                result.getMethod().getConstructorOrMethod().getMethod()
                      .getAnnotation(io.testfly.testdata.TestData.class);
        if (annotation == null) {
            annotation = result.getTestClass().getRealClass()
                               .getAnnotation(io.testfly.testdata.TestData.class);
        }
        if (annotation != null) {
            io.testfly.testdata.TestDataStore.set(
                io.testfly.testdata.TestDataLoader.load(
                    annotation.value(), annotation.sheet(), annotation.row()
                )
            );
        }
    }

    private void capturePerformanceIfEnabled(String testId, ITestResult result) {
        try {
            TestFlyConfig.Performance cfg = TestFlyContext.getConfig().getPerformance();
            if (cfg == null || !cfg.isCaptureOnEveryTest()) return;
            if (skipBrowser(result)) return;
            io.testfly.performance.PerformanceMetrics metrics = PerformanceCollector.collect();
            ExecutionMetrics.recordPerformance(testId, metrics);
        } catch (Exception ignored) {}
    }

    private void runAiAnalysisIfEnabled(String testId) {
        try {
            String pageUrl   = null;
            String pageTitle = null;
            try {
                org.openqa.selenium.WebDriver driver = DriverManager.getDriver();
                if (driver != null) {
                    pageUrl   = driver.getCurrentUrl();
                    pageTitle = driver.getTitle();
                }
            } catch (Exception ignored) {}
            AiFailureAnalyzer.analyze(testId, pageUrl, pageTitle);
        } catch (Exception ignored) {}
    }

    /**
     * Sends screenshot and AI analysis to ReportPortal immediately while the
     * current RP test item is still open. ReportPortal rejects log/attachment
     * calls after the test item finishes, so this must run inside
     * {@link #onTestFailure(ITestResult)} before the RP listener closes the item.
     *
     * <p>Reflection is used so {@code TestExecutionListener} remains safe when the
     * optional {@code agent-java-testng} dependency is absent on the consumer classpath.
     *
     * <p>Allure receives the same data post-hoc from the metrics JSON, but
     * ReportPortal is a live service and requires in-flight attachment.
     */
    private void sendFailureArtifactsToReportPortal(String testId) {
        try {
            TestTiming timing = ExecutionMetrics.getTiming(testId);
            if (timing == null) return;

            String screenshotPath = timing.getScreenshotPath();
            String aiAnalysis = timing.getAiAnalysis();
            if ((screenshotPath == null || screenshotPath.isBlank())
                    && (aiAnalysis == null || aiAnalysis.isBlank())) {
                return;
            }

            Class<?> senderClass = Class.forName(
                    "io.testfly.reporting.reportportal.ReportPortalAttachmentSender");
            java.lang.reflect.Method sendImmediate = senderClass.getMethod(
                    "sendImmediate", String.class, String.class, String.class);
            sendImmediate.invoke(null, testId, screenshotPath, aiAnalysis);
        } catch (Throwable e) {
            // Non-critical: never affect the real test outcome.
            // NoClassDefFoundError is possible when RP agent is not on the classpath.
            System.err.println("[TestFly] Failed to send failure artifacts to ReportPortal: " + e.getMessage());
        }
    }

    /**
     * Captures a failure screenshot and AI analysis, stores them in
     * {@link ExecutionMetrics}, and sends them to ReportPortal while the RP test
     * item is still open. Callers must guard with
     * {@link #failureArtifactsHandled} to avoid double capture / double send.
     */
    private void captureFailureArtifacts(ITestResult result, String testId, String testName) {
        // Capture screenshot (stored in metrics for post-hoc adapters like Allure)
        String screenshotPath = skipBrowser(result) ? null : ScreenshotManager.capture(testName);
        ExecutionMetrics.recordScreenshot(testId, screenshotPath);

        // AI analysis (stored in metrics for post-hoc adapters like Allure)
        runAiAnalysisIfEnabled(testId);

        // ReportPortal needs attachments while the current RP test item is still open.
        sendFailureArtifactsToReportPortal(testId);
        failureArtifactsHandled.set(true);
    }

    // ------------------------------------------------------------------
    // IInvokedMethodListener — sends RP attachments before RP's own
    // onTestFailure closes the test item.
    // ------------------------------------------------------------------

    @Override
    public void beforeInvocation(IInvokedMethod method, ITestResult testResult) {
        // No-op: lifecycle handled by onTestStart.
    }

    @Override
    public void afterInvocation(IInvokedMethod method, ITestResult testResult) {
        if (!method.isTestMethod()) return;
        if (isCucumberScenario(testResult)) return;
        if (testResult.getStatus() != ITestResult.FAILURE) return;

        String testId = testResult.getMethod().getQualifiedName();
        String testName = testResult.getMethod().getMethodName();

        // Record error details early so AI analysis has the stack trace.
        if (testResult.getThrowable() != null) {
            ExecutionMetrics.recordError(testId, testResult.getThrowable());
        }

        // Capture and send artifacts now, while the RP test item is guaranteed open.
        // IInvokedMethodListener.afterInvocation runs before ITestListener.onTestFailure,
        // so this emits attachments before ReportPortalTestNGListener closes the item.
        captureFailureArtifacts(testResult, testId, testName);
    }

    private void saveTraceIfEnabled(String testId, String testName, boolean isPassing) {
        try {
            TestFlyConfig.Tracing tracing = TestFlyContext.getConfig().getTracing();
            if (tracing == null || !tracing.isEnabled()) return;
            if (isPassing && !tracing.isCaptureOnPass()) return;
            TraceRecorder.save(testId, testName);
        } catch (Exception ignored) {}
    }

    @Override
    public void onStart(ITestContext context) {
    }

    /**
     * Returns true when the result represents a Cucumber scenario execution
     * (AbstractTestNGCucumberTests#runScenario). CucumberHooks owns the full
     * lifecycle for those tests — this listener must be a no-op to avoid
     * duplicate entries in ExecutionMetrics.
     */
    private boolean isCucumberScenario(ITestResult result) {
        if (!"runScenario".equals(result.getMethod().getMethodName())) return false;
        try {
            Class<?> base = Class.forName(
                "io.cucumber.testng.AbstractTestNGCucumberTests",
                false,
                result.getTestClass().getRealClass().getClassLoader()
            );
            return base.isAssignableFrom(result.getTestClass().getRealClass());
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            return false;
        }
    }

    @Override
    public void onFinish(ITestContext context) {
    }
}
