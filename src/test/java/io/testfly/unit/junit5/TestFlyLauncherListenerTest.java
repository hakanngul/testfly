package io.testfly.unit.junit5;

import io.testfly.ci.BuildThresholdEnforcer;
import io.testfly.config.TestFlyConfig;
import io.testfly.flakiness.FlakinessAnalyzer;
import io.testfly.hooks.HookRegistry;
import io.testfly.internal.TestFlyContext;
import io.testfly.junit5.TestFlyLauncherListener;
import io.testfly.metrics.ExecutionMetrics;
import io.testfly.precondition.PreConditionRunner;
import io.testfly.reporting.JUnitXmlReporter;
import io.testfly.reporting.ReportAdapterRegistry;
import io.testfly.testmanagement.TestManagementReporter;
import org.junit.platform.launcher.TestPlan;
import org.mockito.MockedStatic;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

/**
 * Unit tests for {@link TestFlyLauncherListener}.
 * Verifies suite-level report generation and cleanup after the test plan finishes.
 */
@Test(singleThreaded = true)
public class TestFlyLauncherListenerTest {

    private TestFlyLauncherListener listener;
    private TestPlan mockTestPlan;

    private MockedStatic<ExecutionMetrics> metricsMock;
    private MockedStatic<FlakinessAnalyzer> flakinessMock;
    private MockedStatic<JUnitXmlReporter> xmlReporterMock;
    private MockedStatic<ReportAdapterRegistry> adapterRegistryMock;
    private MockedStatic<PreConditionRunner> preCondRunnerMock;
    private MockedStatic<HookRegistry> hookMock;
    private MockedStatic<TestManagementReporter> tmReporterMock;
    private MockedStatic<TestFlyContext> contextMock;
    private MockedStatic<BuildThresholdEnforcer> enforcerMock;

    private TestManagementReporter mockTmReporter;

    @BeforeMethod
    public void setup() {
        listener = new TestFlyLauncherListener();
        mockTestPlan = mock(TestPlan.class);

        metricsMock = mockStatic(ExecutionMetrics.class);
        flakinessMock = mockStatic(FlakinessAnalyzer.class);
        xmlReporterMock = mockStatic(JUnitXmlReporter.class);
        adapterRegistryMock = mockStatic(ReportAdapterRegistry.class);
        preCondRunnerMock = mockStatic(PreConditionRunner.class);
        hookMock = mockStatic(HookRegistry.class);
        enforcerMock = mockStatic(BuildThresholdEnforcer.class);

        mockTmReporter = mock(TestManagementReporter.class);
        tmReporterMock = mockStatic(TestManagementReporter.class);
        tmReporterMock.when(TestManagementReporter::getInstance).thenReturn(mockTmReporter);

        TestFlyConfig config = new TestFlyConfig();
        contextMock = mockStatic(TestFlyContext.class);
        contextMock.when(TestFlyContext::getConfig).thenReturn(config);
    }

    @AfterMethod
    public void teardown() {
        if (metricsMock != null) metricsMock.close();
        if (flakinessMock != null) flakinessMock.close();
        if (xmlReporterMock != null) xmlReporterMock.close();
        if (adapterRegistryMock != null) adapterRegistryMock.close();
        if (preCondRunnerMock != null) preCondRunnerMock.close();
        if (hookMock != null) hookMock.close();
        if (tmReporterMock != null) tmReporterMock.close();
        if (contextMock != null) contextMock.close();
        if (enforcerMock != null) enforcerMock.close();
    }

    // ----------------------------------------------------------
    // Happy path — all suite-end actions fire
    // ----------------------------------------------------------

    @Test
    public void testPlanExecutionFinished_printsMetricsSummary() {
        listener.testPlanExecutionFinished(mockTestPlan);

        metricsMock.verify(ExecutionMetrics::printSummary, times(1));
    }

    @Test
    public void testPlanExecutionFinished_exportsMetricsToJson() {
        listener.testPlanExecutionFinished(mockTestPlan);

        metricsMock.verify(ExecutionMetrics::exportToJson, times(1));
    }

    @Test
    public void testPlanExecutionFinished_runsFlakinessAnalysis() {
        listener.testPlanExecutionFinished(mockTestPlan);

        flakinessMock.verify(FlakinessAnalyzer::analyze, times(1));
    }

    @Test
    public void testPlanExecutionFinished_exportsJunitXml() {
        listener.testPlanExecutionFinished(mockTestPlan);

        xmlReporterMock.verify(() -> JUnitXmlReporter.export(any(), anyLong()), times(1));
    }

    @Test
    public void testPlanExecutionFinished_generatesAllReportAdapters() {
        listener.testPlanExecutionFinished(mockTestPlan);

        adapterRegistryMock.verify(ReportAdapterRegistry::generateAll, times(1));
    }

    @Test
    public void testPlanExecutionFinished_clearsPreConditions() {
        listener.testPlanExecutionFinished(mockTestPlan);

        preCondRunnerMock.verify(PreConditionRunner::clearAll, times(1));
    }

    @Test
    public void testPlanExecutionFinished_firesHookOnSuiteEnd() {
        listener.testPlanExecutionFinished(mockTestPlan);

        hookMock.verify(HookRegistry::onSuiteEnd, times(1));
    }

    @Test
    public void testPlanExecutionFinished_notifiesTestManagementReporter() {
        listener.testPlanExecutionFinished(mockTestPlan);

        verify(mockTmReporter).onSuiteEnd();
    }

    // ----------------------------------------------------------
    // Exception handling
    // ----------------------------------------------------------

    @Test
    public void testPlanExecutionFinished_survivesReportAdapterException() {
        // Simulate an adapter throwing a non-IllegalStateException
        adapterRegistryMock.when(ReportAdapterRegistry::generateAll)
                .thenThrow(new RuntimeException("adapter boom"));

        // Should not throw — non-IllegalStateException is caught and logged
        listener.testPlanExecutionFinished(mockTestPlan);

        // Verify we reached the point of failure
        adapterRegistryMock.verify(ReportAdapterRegistry::generateAll, times(1));
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testPlanExecutionFinished_rethrowsCiGateFailure() {
        // CI gate failures (IllegalStateException) must be re-thrown
        enforcerMock.when(() -> BuildThresholdEnforcer.enforce(any(), any()))
                .thenThrow(new IllegalStateException("Pass rate below threshold"));

        listener.testPlanExecutionFinished(mockTestPlan);
    }

    @Test
    public void testPlanExecutionFinished_survivesMetricsExportException() {
        metricsMock.when(ExecutionMetrics::exportToJson)
                .thenThrow(new RuntimeException("disk full"));

        // Should not throw — exception is caught
        listener.testPlanExecutionFinished(mockTestPlan);

        metricsMock.verify(ExecutionMetrics::exportToJson, times(1));
    }

    @Test
    public void testPlanExecutionFinished_survivesFlakinessException() {
        flakinessMock.when(FlakinessAnalyzer::analyze)
                .thenThrow(new RuntimeException("analysis failed"));

        // Should not throw — exception is caught
        listener.testPlanExecutionFinished(mockTestPlan);

        flakinessMock.verify(FlakinessAnalyzer::analyze, times(1));
    }

    @Test
    public void testPlanExecutionFinished_handlesNullConfig() {
        contextMock.when(TestFlyContext::getConfig).thenReturn(null);

        // Should not throw — null config is handled gracefully
        listener.testPlanExecutionFinished(mockTestPlan);

        // BuildThresholdEnforcer should NOT be called when config is null
        enforcerMock.verify(() -> BuildThresholdEnforcer.enforce(any(), any()), never());
    }
}
