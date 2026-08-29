package io.testfly.unit.listeners;

import io.testfly.ci.BuildThresholdEnforcer;
import io.testfly.config.TestFlyConfig;
import io.testfly.driver.DriverManager;
import io.testfly.extension.PluginRegistry;
import io.testfly.flakiness.FlakinessAnalyzer;
import io.testfly.healing.HealLog;
import io.testfly.hooks.HookRegistry;
import io.testfly.internal.TestFlyContext;
import io.testfly.lifecycle.FrameworkBootstrap;
import io.testfly.listeners.SuiteExecutionListener;
import io.testfly.metrics.ExecutionMetrics;
import io.testfly.precondition.ApiHealthChecker;
import io.testfly.precondition.PreConditionRegistry;
import io.testfly.precondition.PreConditionRunner;
import io.testfly.reporting.JUnitXmlReporter;
import io.testfly.reporting.ReportAdapterRegistry;
import io.testfly.reporting.reportportal.ReportPortalPropertiesWriter;
import io.testfly.testmanagement.TestManagementReporter;
import org.mockito.MockedStatic;
import org.testng.ISuite;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.testng.xml.XmlSuite;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SuiteExecutionListener}.
 * Thread-safe for parallel=methods via singleThreaded.
 */
@Test(singleThreaded = true)
public class SuiteExecutionListenerTest {

    private SuiteExecutionListener listener;
    private MockedStatic<FrameworkBootstrap> bootstrapMock;
    private MockedStatic<TestFlyContext> contextMock;
    private MockedStatic<ExecutionMetrics> metricsMock;
    private MockedStatic<HookRegistry> hookMock;
    private MockedStatic<ApiHealthChecker> healthCheckerMock;
    private MockedStatic<PreConditionRegistry> preConditionRegistryMock;
    private MockedStatic<PreConditionRunner> preConditionRunnerMock;
    private MockedStatic<TestManagementReporter> testManagementMock;
    private MockedStatic<ReportAdapterRegistry> reportAdapterMock;
    private MockedStatic<JUnitXmlReporter> junitXmlMock;
    private MockedStatic<DriverManager> driverManagerMock;
    private MockedStatic<PluginRegistry> pluginRegistryMock;
    private MockedStatic<HealLog> healLogMock;
    private MockedStatic<FlakinessAnalyzer> flakinessMock;
    private MockedStatic<BuildThresholdEnforcer> buildThresholdMock;
    private MockedStatic<ReportPortalPropertiesWriter> rpPropertiesMock;
    private TestManagementReporter mockTestManagementReporter;

    @BeforeMethod
    public void setup() {
        listener = new SuiteExecutionListener();

        bootstrapMock = mockStatic(FrameworkBootstrap.class);
        contextMock = mockStatic(TestFlyContext.class);
        metricsMock = mockStatic(ExecutionMetrics.class);
        hookMock = mockStatic(HookRegistry.class);
        healthCheckerMock = mockStatic(ApiHealthChecker.class);
        preConditionRegistryMock = mockStatic(PreConditionRegistry.class);
        preConditionRunnerMock = mockStatic(PreConditionRunner.class);
        reportAdapterMock = mockStatic(ReportAdapterRegistry.class);
        junitXmlMock = mockStatic(JUnitXmlReporter.class);
        driverManagerMock = mockStatic(DriverManager.class);
        pluginRegistryMock = mockStatic(PluginRegistry.class);
        healLogMock = mockStatic(HealLog.class);
        flakinessMock = mockStatic(FlakinessAnalyzer.class);
        buildThresholdMock = mockStatic(BuildThresholdEnforcer.class);
        rpPropertiesMock = mockStatic(ReportPortalPropertiesWriter.class);

        mockTestManagementReporter = mock(TestManagementReporter.class);
        testManagementMock = mockStatic(TestManagementReporter.class);
        testManagementMock.when(TestManagementReporter::getInstance).thenReturn(mockTestManagementReporter);

        // Default: framework not initialized (for tests that don't need it)
        contextMock.when(TestFlyContext::isInitialized).thenReturn(false);
    }

    @AfterMethod
    public void tearDown() {
        if (bootstrapMock != null)
            bootstrapMock.close();
        if (contextMock != null)
            contextMock.close();
        if (metricsMock != null)
            metricsMock.close();
        if (hookMock != null)
            hookMock.close();
        if (healthCheckerMock != null)
            healthCheckerMock.close();
        if (preConditionRegistryMock != null)
            preConditionRegistryMock.close();
        if (preConditionRunnerMock != null)
            preConditionRunnerMock.close();
        if (testManagementMock != null)
            testManagementMock.close();
        if (reportAdapterMock != null)
            reportAdapterMock.close();
        if (junitXmlMock != null)
            junitXmlMock.close();
        if (driverManagerMock != null)
            driverManagerMock.close();
        if (pluginRegistryMock != null)
            pluginRegistryMock.close();
        if (healLogMock != null)
            healLogMock.close();
        if (flakinessMock != null)
            flakinessMock.close();
        if (buildThresholdMock != null)
            buildThresholdMock.close();
        if (rpPropertiesMock != null)
            rpPropertiesMock.close();
    }

    // ── onStart → hook chain called in order ─────────────────────────────────

    @Test
    public void onStart_initializesFramework() {
        ISuite suite = mockSuite();
        setupMockConfig();

        listener.onStart(suite);

        bootstrapMock.verify(FrameworkBootstrap::initialize);
    }

    @Test
    public void onStart_clearsHealthCheckerCache() {
        ISuite suite = mockSuite();
        setupMockConfig();

        listener.onStart(suite);

        healthCheckerMock.verify(ApiHealthChecker::clearCache);
    }

    @Test
    public void onStart_loadsPreConditionProviders() {
        ISuite suite = mockSuite();
        setupMockConfig();

        listener.onStart(suite);

        preConditionRegistryMock.verify(PreConditionRegistry::loadAll);
    }

    @Test
    public void onStart_callsHookRegistry() {
        ISuite suite = mockSuite();
        setupMockConfig();

        listener.onStart(suite);

        hookMock.verify(HookRegistry::onSuiteStart);
    }

    @Test
    public void onStart_callsTestManagementReporter() {
        ISuite suite = mockSuite();
        setupMockConfig();

        listener.onStart(suite);

        verify(mockTestManagementReporter).onSuiteStart();
    }

    // ── onFinish → all hooks notified, metrics finalized ─────────────────────

    @Test
    public void onFinish_printsSummary() {
        ISuite suite = mockSuite();

        listener.onFinish(suite);

        metricsMock.verify(ExecutionMetrics::printSummary);
    }

    @Test
    public void onFinish_exportsToJson() {
        ISuite suite = mockSuite();

        listener.onFinish(suite);

        metricsMock.verify(ExecutionMetrics::exportToJson);
    }

    @Test
    public void onFinish_exportsHealLog() {
        ISuite suite = mockSuite();

        listener.onFinish(suite);

        healLogMock.verify(HealLog::export);
    }

    @Test
    public void onFinish_analyzesFlakiness() {
        ISuite suite = mockSuite();

        listener.onFinish(suite);

        flakinessMock.verify(FlakinessAnalyzer::analyze);
    }

    @Test
    public void onFinish_exportsJUnitXml() {
        ISuite suite = mockSuite();
        when(suite.getAllInvokedMethods()).thenReturn(Collections.emptyList());

        listener.onFinish(suite);

        junitXmlMock.verify(() -> JUnitXmlReporter.export(any(), anyLong()));
    }

    @Test
    public void onFinish_generatesAllReports() {
        ISuite suite = mockSuite();

        listener.onFinish(suite);

        reportAdapterMock.verify(ReportAdapterRegistry::generateAll);
    }

    @Test
    public void onFinish_clearsPreConditions() {
        ISuite suite = mockSuite();

        listener.onFinish(suite);

        preConditionRunnerMock.verify(PreConditionRunner::clearAll);
    }

    @Test
    public void onFinish_quitsAllDrivers() {
        ISuite suite = mockSuite();

        listener.onFinish(suite);

        driverManagerMock.verify(DriverManager::quitAllSuiteDrivers);
        driverManagerMock.verify(DriverManager::quitDriver);
    }

    @Test
    public void onFinish_callsHookRegistry() {
        ISuite suite = mockSuite();

        listener.onFinish(suite);

        hookMock.verify(HookRegistry::onSuiteEnd);
    }

    @Test
    public void onFinish_callsTestManagementReporter() {
        ISuite suite = mockSuite();

        listener.onFinish(suite);

        verify(mockTestManagementReporter).onSuiteEnd();
    }

    @Test
    public void onFinish_unloadsPlugins() {
        ISuite suite = mockSuite();

        listener.onFinish(suite);

        pluginRegistryMock.verify(PluginRegistry::unloadAll);
    }

    @Test
    public void onFinish_enforcesBuildThresholds() {
        ISuite suite = mockSuite();
        // Need config for BuildThresholdEnforcer
        TestFlyConfig config = new TestFlyConfig();
        config.setExecution(new TestFlyConfig.Execution());
        contextMock.when(TestFlyContext::getConfig).thenReturn(config);

        listener.onFinish(suite);

        buildThresholdMock.verify(() -> BuildThresholdEnforcer.enforce(eq(config), any()));
    }

    // ── Helper methods ───────────────────────────────────────────────────────

    private ISuite mockSuite() {
        ISuite suite = mock(ISuite.class);
        XmlSuite xmlSuite = mock(XmlSuite.class);
        when(suite.getXmlSuite()).thenReturn(xmlSuite);
        when(suite.getAllInvokedMethods()).thenReturn(Collections.emptyList());
        return suite;
    }

    private void setupMockConfig() {
        TestFlyConfig config = new TestFlyConfig();
        TestFlyConfig.Execution execution = new TestFlyConfig.Execution();
        execution.setMode("local");
        execution.setBaseUrl("https://example.com");
        execution.setParallel("none");
        config.setExecution(execution);

        contextMock.when(TestFlyContext::isInitialized).thenReturn(true);
        contextMock.when(TestFlyContext::getConfig).thenReturn(config);
    }
}
