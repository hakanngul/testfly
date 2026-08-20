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

public final class TestExecutionListener implements ITestListener {

    /** Tracks whether JS errors have already been logged for this test (prevents double-logging on failure redirect). */
    private static final ThreadLocal<Boolean> jsErrorsLogged = ThreadLocal.withInitial(() -> false);

    @Override
    public void onTestStart(ITestResult result) {
        if (isCucumberScenario(result)) return;
        String testId = result.getMethod().getQualifiedName();
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

        capturePerformanceIfEnabled(testId, result);
        RecordingManager.stop(); // discard frames — test passed
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

        if (!skipBrowser(result) && ConsoleErrorCollector.isEnabled() && !jsErrorsLogged.get()) {
            ConsoleErrorCollector.collect().forEach(e -> StepLogger.step("[JS Error] " + e, StepStatus.WARN));
        }
        jsErrorsLogged.set(false);

        String recordingPath = skipBrowser(result) ? null : RecordingManager.saveOnFailure(testId);
        ExecutionMetrics.recordRecording(testId, recordingPath);
        ExecutionMetrics.recordStatus(testId, "FAILED");
        ExecutionMetrics.markEnd(testId);
        if (result.getThrowable() != null) {
            ExecutionMetrics.recordError(testId, result.getThrowable());
        }
        saveTraceIfEnabled(testId, result.getMethod().getMethodName(), false);
        runAiAnalysisIfEnabled(testId);
        HookRegistry.onTestFailure(testId, result.getThrowable());
        String failureComment = result.getThrowable() != null ? result.getThrowable().getMessage() : null;
        TestManagementReporter.getInstance().onTestResult(
                result.getMethod().getConstructorOrMethod().getMethod(), "FAILED", failureComment);
        String screenshotPath = skipBrowser(result) ? null : ScreenshotManager.capture(testName);
        ExecutionMetrics.recordScreenshot(testId, screenshotPath);
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
            io.testfly.config.TestFlyConfig.Recording rec =
                    TestFlyContext.getConfig().getRecording();
            if (rec == null || !rec.isEnabled()) return;
            org.openqa.selenium.WebDriver driver = DriverManager.getDriver();
            if (driver == null) return;
            RecordingManager.start(driver, rec.getFps(), rec.getMaxDurationSeconds());
        } catch (Exception ignored) {}
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
            default       -> throw new IllegalArgumentException(
                "[UseAuth] Unknown auth type: '" + type + "'. Use bearer, basic, or oauth2");
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
