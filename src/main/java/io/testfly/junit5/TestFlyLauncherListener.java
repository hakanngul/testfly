package io.testfly.junit5;

import io.testfly.api.TestFlyApi;
import io.testfly.ci.BuildThresholdEnforcer;
import io.testfly.config.TestFlyConfig;
import io.testfly.flakiness.FlakinessAnalyzer;
import io.testfly.hooks.HookRegistry;
import io.testfly.internal.TestFlyContext;
import io.testfly.metrics.ExecutionMetrics;
import io.testfly.reporting.JUnitXmlReporter;
import io.testfly.precondition.PreConditionRunner;
import io.testfly.reporting.ReportAdapterRegistry;
import io.testfly.testmanagement.TestManagementReporter;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

/**
 * JUnit Platform launcher listener that generates TestFly reports after
 * the entire test plan finishes — the JUnit 5 equivalent of
 * {@code SuiteExecutionListener.onFinish()} in TestNG.
 *
 * <p>Registered automatically via ServiceLoader:
 * {@code META-INF/services/org.junit.platform.launcher.TestExecutionListener}
 *
 * <p>Fires {@code testPlanExecutionFinished} once when all test classes have run,
 * regardless of how many classes were in the plan.
 */
@TestFlyApi(since = "1.9.0")
public class TestFlyLauncherListener implements TestExecutionListener {

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        try {
            ExecutionMetrics.printSummary();
            ExecutionMetrics.exportToJson();
            FlakinessAnalyzer.analyze();

            JUnitXmlReporter.export(ExecutionMetrics.getTimings(), System.currentTimeMillis());

            ReportAdapterRegistry.generateAll();
            PreConditionRunner.clearAll();
            HookRegistry.onSuiteEnd();
            TestManagementReporter.getInstance().onSuiteEnd();

            TestFlyConfig config = TestFlyContext.getConfig();
            if (config != null) {
                BuildThresholdEnforcer.enforce(config, ExecutionMetrics.getTimings());
            }
        } catch (IllegalStateException e) {
            throw e;  // re-throw CI gate failures (pass-rate / flakiness)
        } catch (Exception e) {
            System.err.println("[TestFly] Report generation failed: " + e.getMessage());
        }
    }
}
