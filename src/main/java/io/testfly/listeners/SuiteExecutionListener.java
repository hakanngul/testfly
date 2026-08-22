package io.testfly.listeners;

import io.testfly.ci.BuildThresholdEnforcer;
import io.testfly.flakiness.FlakinessAnalyzer;
import io.testfly.healing.HealLog;
import io.testfly.config.TestFlyConfig;
import io.testfly.driver.DriverManager;
import io.testfly.internal.TestFlyContext;
import io.testfly.lifecycle.FrameworkBootstrap;
import io.testfly.metrics.ExecutionMetrics;
import io.testfly.extension.PluginRegistry;
import io.testfly.hooks.HookRegistry;
import io.testfly.precondition.ApiHealthChecker;
import io.testfly.precondition.PreConditionRegistry;
import io.testfly.precondition.PreConditionRunner;
import io.testfly.reporting.JUnitXmlReporter;
import io.testfly.reporting.ReportAdapterRegistry;
import io.testfly.reporting.reportportal.ReportPortalPropertiesWriter;
import io.testfly.test.BaseApiTest;
import io.testfly.testmanagement.TestManagementReporter;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestNGListener;
import org.testng.SuiteRunner;
import org.testng.xml.XmlSuite;

/**
 * SuiteExecutionListener is responsible for framework initialization
 * before any tests are executed.
 *
 * This listener must run exactly once per TestNG suite.
 */
public final class SuiteExecutionListener implements ISuiteListener {

    @Override
    public void onStart(ISuite suite) {
        try {
//            Initialize framework(loads + validate configs)
            FrameworkBootstrap.initialize();

//            Fetch configs
            TestFlyConfig config = TestFlyContext.getConfig();
            TestFlyConfig.Execution execution = config.getExecution();

//            Apply Parallel config
            if (!"none".equalsIgnoreCase(execution.getParallel())) {
                XmlSuite xmlSuite = suite.getXmlSuite();

                XmlSuite.ParallelMode mode =
                        XmlSuite.ParallelMode.valueOf(
                                execution.getParallel().toUpperCase(java.util.Locale.ROOT)
                        );

                xmlSuite.setParallel(mode);
                xmlSuite.setThreadCount(
                        execution.getThreadCount()
                );

                System.out.println(
                        "[TestFly] Parallel mode: "
                                + mode + " | Threads: "
                                + execution.getThreadCount()
                );
            } else {
                System.out.println(
                        "[TestFly] Parallel execution disabled."
                );
            }

            ApiHealthChecker.clearCache(); // reset per-suite health check cache
            PreConditionRegistry.loadAll();
            HookRegistry.onSuiteStart();
            TestManagementReporter.getInstance().onSuiteStart();
            enrichReportPortalLaunchInfo(suite);
            registerReportPortalListenerIfEnabled(suite);

        } catch (Exception e) {
            // Abort entire suite on bootstrap failure
            throw new IllegalStateException(
                "TestFly failed to initialize. Aborting test suite execution.", e);
        }
    }

    // ── ReportPortal launch enrichment ────────────────────────────────────────

    /**
     * Detects the run type (API / Web) from the suite's test classes and
     * re-applies the ReportPortal system properties with an enriched launch
     * name and description. Must be called before the RP listener is registered
     * so the agent picks up the enriched values when it creates the launch.
     */
    private void enrichReportPortalLaunchInfo(ISuite suite) {
        if (!TestFlyContext.isInitialized()) return;
        TestFlyConfig config = TestFlyContext.getConfig();
        TestFlyConfig.Reporting reporting = config.getReporting();
        if (reporting == null
                || reporting.getReportPortal() == null
                || !reporting.getReportPortal().isEnabled()) {
            return;
        }

        String runType = detectRunType(suite);
        ReportPortalPropertiesWriter.reapplyWithRunType(config, runType);
        System.out.println("[TestFly] ReportPortal launch enriched → run type: " + runType);
    }

    /**
     * Scans the suite's test classes to determine whether this is an API or Web run.
     *
     * <p>If any test class extends {@link BaseApiTest} and none extend
     * {@code io.testfly.test.BaseTest}, the run type is "API".
     * Otherwise it is "Web".
     *
     * <p>When the config has {@code type: api} or {@code type: web}, that value
     * wins over auto-detection.
     */
    private String detectRunType(ISuite suite) {
        // Config override takes precedence
        String configType = "auto";
        try {
            configType = TestFlyContext.getConfig()
                    .getReporting().getReportPortal().getType();
        } catch (Exception ignored) {}
        if ("api".equalsIgnoreCase(configType)) return "API";
        if ("web".equalsIgnoreCase(configType)) return "Web";

        // Auto-detect from suite test classes
        boolean hasApi  = false;
        boolean hasWeb  = false;

        try {
            for (var entry : suite.getXmlSuite().getTests()) {
                for (var cls : entry.getXmlClasses()) {
                    try {
                        Class<?> testClass = Class.forName(cls.getName());
                        if (BaseApiTest.class.isAssignableFrom(testClass)) {
                            hasApi = true;
                        } else {
                            hasWeb = true;
                        }
                    } catch (ClassNotFoundException ignored) {}
                }
            }
        } catch (Exception e) {
            System.err.println("[TestFly] Failed to detect run type from suite: " + e.getMessage());
        }

        if (hasApi && !hasWeb) return "API";
        return "Web";
    }

    private static final String REPORTPORTAL_TESTNG_LISTENER =
            "com.epam.reportportal.testng.ReportPortalTestNGListener";

    /**
     * Registers the ReportPortal TestNG listener when {@code reporting.reportportal.enabled}
     * is {@code true} and the ReportPortal agent is present on the classpath.
     *
     * <p>The listener class is referenced by name so that TestFly remains compilable
     * even when the optional {@code agent-java-testng} dependency is not present.
     */
    private void registerReportPortalListenerIfEnabled(ISuite suite) {
        if (!TestFlyContext.isInitialized()) {
            return;
        }
        TestFlyConfig config = TestFlyContext.getConfig();
        TestFlyConfig.Reporting reporting = config.getReporting();
        if (reporting == null
                || reporting.getReportPortal() == null
                || !reporting.getReportPortal().isEnabled()) {
            return;
        }

        // Skip TestNG listener only when the Cucumber 7 agent is ACTIVELY running
        // (i.e., cucumber.plugin system property contains the RP Cucumber plugin).
        // This is set by BaseCucumberTest's static block for Cucumber runners only.
        if (isCucumber7AgentActive()) {
            System.out.println("[TestFly] ReportPortal Cucumber 7 agent is active — "
                    + "skipping TestNG listener to avoid duplicate reporting");
            return;
        }

        try {
            Class<?> listenerClass = Class.forName(REPORTPORTAL_TESTNG_LISTENER);
            Object listener = listenerClass.getDeclaredConstructor().newInstance();

            // ReportPortalTestNGListener is added dynamically here, which happens after
            // TestNG has already fired IExecutionListener.onExecutionStart() and
            // ISuiteListener.onStart(ISuite). Replay those two calls manually so the
            // launch and suite are created in ReportPortal; TestNG will route the
            // remaining test-method events to the listener registered below.
            invokeListenerMethod(listenerClass, listener, "onExecutionStart", new Class<?>[0], new Object[0]);
            invokeListenerMethod(listenerClass, listener, "onStart", new Class<?>[]{ISuite.class}, new Object[]{suite});

            // Register the same instance with TestNG so it receives test-method events.
            // XmlSuite.addListener(String) would create a second instance and miss the
            // replayed lifecycle calls above, so we use the runtime SuiteRunner API.
            addListenerInstance(suite, listener);
            System.out.println("[TestFly] ReportPortal TestNG listener registered");
        } catch (ClassNotFoundException e) {
            System.err.println(
                    "[TestFly] ReportPortal is enabled but the TestNG agent is not on the classpath. "
                            + "Add com.epam.reportportal:agent-java-testng to your project dependencies.");
        } catch (Exception e) {
            System.err.println("[TestFly] Failed to register ReportPortal listener: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void invokeListenerMethod(Class<?> listenerClass, Object listener,
                                      String methodName, Class<?>[] paramTypes, Object[] args) {
        try {
            java.lang.reflect.Method method = listenerClass.getMethod(methodName, paramTypes);
            method.invoke(listener, args);
        } catch (NoSuchMethodException e) {
            // Older agent versions may not expose this lifecycle method; ignore.
        } catch (Exception e) {
            System.err.println("[TestFly] Failed to replay ReportPortal listener method "
                    + methodName + ": " + e.getMessage());
        }
    }

    private void addListenerInstance(ISuite suite, Object listener) {
        try {
            if (suite instanceof SuiteRunner runner && listener instanceof ITestNGListener testNgListener) {
                runner.addListener(testNgListener);
            } else {
                // Runtime shape doesn't match expectations; fall back to class-name registration
                // (accepts duplicated lifecycle calls).
                suite.getXmlSuite().addListener(REPORTPORTAL_TESTNG_LISTENER);
            }
        } catch (Exception e) {
            System.err.println("[TestFly] Failed to add ReportPortal listener instance: " + e.getMessage());
            suite.getXmlSuite().addListener(REPORTPORTAL_TESTNG_LISTENER);
        }
    }

    @Override
    public void onFinish(ISuite suite) {
        ExecutionMetrics.printSummary();
        ExecutionMetrics.exportToJson();
        HealLog.export();
        FlakinessAnalyzer.analyze();

        // Machine-readable JUnit XML for CI test result parsing
        JUnitXmlReporter.export(
                ExecutionMetrics.getTimings(),
                suite.getAllInvokedMethods().size() > 0
                        ? System.currentTimeMillis() : 0L);

        ReportAdapterRegistry.generateAll();
        PreConditionRunner.clearAll();
        DriverManager.quitAllSuiteDrivers(); // per-suite lifecycle — quits all kept-alive drivers
        DriverManager.quitDriver();          // per-test safety net — no-op if already quit
        HookRegistry.onSuiteEnd();
        TestManagementReporter.getInstance().onSuiteEnd();
        PluginRegistry.unloadAll();

        // Build quality gates — must run last so all metrics are recorded
        TestFlyConfig config = TestFlyContext.getConfig();
        BuildThresholdEnforcer.enforce(config, ExecutionMetrics.getTimings());
    }

    /**
     * Returns {@code true} when the ReportPortal Cucumber 7 agent is ACTIVELY running,
     * i.e., the {@code cucumber.plugin} system property contains the RP Cucumber plugin class.
     *
     * <p>This is set by {@link io.testfly.cucumber.BaseCucumberTest}'s static block
     * only when a Cucumber runner (extending {@code BaseCucumberTest}) is executed.
     * Regular TestNG tests do not set this property, so the TestNG listener is used.
     */
    private static boolean isCucumber7AgentActive() {
        String plugins = System.getProperty("cucumber.plugin", "");
        return plugins.contains("com.epam.reportportal.cucumber.ScenarioReporter");
    }
}
