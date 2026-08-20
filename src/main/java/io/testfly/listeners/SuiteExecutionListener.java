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
import io.testfly.testmanagement.TestManagementReporter;
import org.testng.ISuite;
import org.testng.ISuiteListener;
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

        } catch (Exception e) {
            // Abort entire suite on bootstrap failure
            throw new IllegalStateException(
                "TestFly failed to initialize. Aborting test suite execution.", e);
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
}
