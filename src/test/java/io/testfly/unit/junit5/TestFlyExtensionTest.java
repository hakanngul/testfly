package io.testfly.unit.junit5;

import io.testfly.config.TestFlyConfig;
import io.testfly.driver.DriverManager;
import io.testfly.hooks.HookRegistry;
import io.testfly.internal.TestFlyContext;
import io.testfly.junit5.TestFlyExtension;
import io.testfly.lifecycle.FrameworkBootstrap;
import io.testfly.metrics.ExecutionMetrics;
import io.testfly.precondition.PreConditionRegistry;
import io.testfly.testmanagement.TestManagementReporter;
import io.testfly.healing.HealLog;
import io.testfly.reporting.ScreenshotManager;
import io.testfly.session.MultiSessionManager;
import io.testfly.db.DbConnectionFactory;
import io.testfly.context.ScenarioContext;
import io.testfly.testdata.TestDataStore;
import io.testfly.client.ApiClient;
import io.testfly.browser.BrowserContext;
import io.testfly.network.NetworkMock;
import io.testfly.clock.TestClock;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.mockito.MockedStatic;
import org.openqa.selenium.WebDriver;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

/**
 * Unit tests for {@link TestFlyExtension}.
 * Uses TestNG + Mockito to mock all JUnit 5 and framework dependencies.
 */
@Test(singleThreaded = true)
public class TestFlyExtensionTest {

    private static final String TEST_ID = "io.testfly.unit.junit5.TestFlyExtensionTest$SampleTestClass#sampleMethod";

    private TestFlyExtension extension;
    private ExtensionContext mockContext;

    private MockedStatic<FrameworkBootstrap> bootstrapMock;
    private MockedStatic<PreConditionRegistry> preCondRegMock;
    private MockedStatic<TestManagementReporter> tmReporterMock;
    private MockedStatic<DriverManager> driverManagerMock;
    private MockedStatic<TestFlyContext> contextMock;
    private MockedStatic<HealLog> healLogMock;
    private MockedStatic<MultiSessionManager> multiSessionMock;
    private MockedStatic<DbConnectionFactory> dbConnMock;
    private MockedStatic<ScenarioContext> scenarioMock;
    private MockedStatic<TestDataStore> testDataMock;
    private MockedStatic<ApiClient> apiClientMock;
    private MockedStatic<BrowserContext> browserCtxMock;
    private MockedStatic<NetworkMock> networkMockMock;
    private MockedStatic<TestClock> testClockMock;
    private MockedStatic<HookRegistry> hookMock;
    private MockedStatic<ExecutionMetrics> metricsMock;
    private MockedStatic<ScreenshotManager> screenshotMock;

    private TestManagementReporter mockTmReporter;

    @BeforeMethod
    public void setup() {
        extension = new TestFlyExtension();
        mockContext = mock(ExtensionContext.class);

        // Set up default ExtensionContext behavior
        when(mockContext.getRequiredTestClass()).thenReturn((Class) SampleTestClass.class);
        try {
            when(mockContext.getRequiredTestMethod()).thenReturn(
                    SampleTestClass.class.getDeclaredMethod("sampleMethod"));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        when(mockContext.getDisplayName()).thenReturn("sampleMethod()");
        when(mockContext.getExecutionException()).thenReturn(Optional.empty());

        // Mock all static dependencies
        bootstrapMock = mockStatic(FrameworkBootstrap.class);
        preCondRegMock = mockStatic(PreConditionRegistry.class);

        mockTmReporter = mock(TestManagementReporter.class);
        tmReporterMock = mockStatic(TestManagementReporter.class);
        tmReporterMock.when(TestManagementReporter::getInstance).thenReturn(mockTmReporter);

        driverManagerMock = mockStatic(DriverManager.class);
        driverManagerMock.when(DriverManager::getCloudSessionUrl).thenReturn(null);
        driverManagerMock.when(DriverManager::shouldQuitAfterTest).thenReturn(true);

        TestFlyConfig config = new TestFlyConfig();
        contextMock = mockStatic(TestFlyContext.class);
        contextMock.when(TestFlyContext::getConfig).thenReturn(config);
        contextMock.when(TestFlyContext::getCurrentTestId).thenReturn(TEST_ID);

        healLogMock = mockStatic(HealLog.class);
        multiSessionMock = mockStatic(MultiSessionManager.class);
        dbConnMock = mockStatic(DbConnectionFactory.class);
        scenarioMock = mockStatic(ScenarioContext.class);
        testDataMock = mockStatic(TestDataStore.class);
        apiClientMock = mockStatic(ApiClient.class);
        browserCtxMock = mockStatic(BrowserContext.class);
        networkMockMock = mockStatic(NetworkMock.class);
        testClockMock = mockStatic(TestClock.class);
        hookMock = mockStatic(HookRegistry.class);
        metricsMock = mockStatic(ExecutionMetrics.class);
        screenshotMock = mockStatic(ScreenshotManager.class);
    }

    @AfterMethod
    public void teardown() {
        if (bootstrapMock != null)
            bootstrapMock.close();
        if (preCondRegMock != null)
            preCondRegMock.close();
        if (tmReporterMock != null)
            tmReporterMock.close();
        if (driverManagerMock != null)
            driverManagerMock.close();
        if (contextMock != null)
            contextMock.close();
        if (healLogMock != null)
            healLogMock.close();
        if (multiSessionMock != null)
            multiSessionMock.close();
        if (dbConnMock != null)
            dbConnMock.close();
        if (scenarioMock != null)
            scenarioMock.close();
        if (testDataMock != null)
            testDataMock.close();
        if (apiClientMock != null)
            apiClientMock.close();
        if (browserCtxMock != null)
            browserCtxMock.close();
        if (networkMockMock != null)
            networkMockMock.close();
        if (testClockMock != null)
            testClockMock.close();
        if (hookMock != null)
            hookMock.close();
        if (metricsMock != null)
            metricsMock.close();
        if (screenshotMock != null)
            screenshotMock.close();
    }

    // ----------------------------------------------------------
    // beforeAll
    // ----------------------------------------------------------

    @Test
    public void beforeAll_initializesFrameworkBootstrap() {
        extension.beforeAll(mockContext);

        bootstrapMock.verify(FrameworkBootstrap::initialize, times(1));
    }

    @Test
    public void beforeAll_loadsPreConditions() {
        extension.beforeAll(mockContext);

        preCondRegMock.verify(PreConditionRegistry::loadAll, times(1));
    }

    @Test
    public void beforeAll_notifiesTestManagementReporter() {
        extension.beforeAll(mockContext);

        verify(mockTmReporter).onSuiteStart();
    }

    // ----------------------------------------------------------
    // afterAll
    // ----------------------------------------------------------

    @Test
    public void afterAll_quitsAllSuiteDrivers() {
        extension.afterAll(mockContext);

        driverManagerMock.verify(DriverManager::quitAllSuiteDrivers, times(1));
    }

    @Test
    public void afterAll_quitsDriver() {
        extension.afterAll(mockContext);

        driverManagerMock.verify(DriverManager::quitDriver, times(1));
    }

    @Test
    public void afterAll_exportsHealLog() {
        extension.afterAll(mockContext);

        healLogMock.verify(HealLog::export, times(1));
    }

    // ----------------------------------------------------------
    // afterEach — cleanup in finally block
    // ----------------------------------------------------------

    @Test
    public void afterEach_clearsMultiSessions() {
        extension.afterEach(mockContext);

        multiSessionMock.verify(MultiSessionManager::clearAll, times(1));
    }

    @Test
    public void afterEach_closesDbConnections() {
        extension.afterEach(mockContext);

        dbConnMock.verify(DbConnectionFactory::closeAll, times(1));
    }

    @Test
    public void afterEach_clearsScenarioContext() {
        extension.afterEach(mockContext);

        scenarioMock.verify(ScenarioContext::clear, times(1));
    }

    @Test
    public void afterEach_clearsTestDataStore() {
        extension.afterEach(mockContext);

        testDataMock.verify(TestDataStore::clear, times(1));
    }

    @Test
    public void afterEach_clearsApiAuth() {
        extension.afterEach(mockContext);

        apiClientMock.verify(ApiClient::clearGlobalAuth, times(1));
    }

    @Test
    public void afterEach_clearsBrowserContext() {
        extension.afterEach(mockContext);

        browserCtxMock.verify(BrowserContext::clear, times(1));
    }

    @Test
    public void afterEach_cleansUpNetworkMocks() {
        extension.afterEach(mockContext);

        networkMockMock.verify(NetworkMock::cleanup, times(1));
    }

    @Test
    public void afterEach_autoResetsTestClock() {
        extension.afterEach(mockContext);

        testClockMock.verify(TestClock::autoReset, times(1));
    }

    @Test
    public void afterEach_clearsCurrentTestId() {
        extension.afterEach(mockContext);

        contextMock.verify(TestFlyContext::clearCurrentTestId, times(1));
    }

    @Test
    public void afterEach_onSuccess_recordsPassedStatus() {
        extension.afterEach(mockContext);

        metricsMock.verify(() -> ExecutionMetrics.recordStatus(TEST_ID, "PASSED"), times(1));
    }

    @Test
    public void afterEach_onSuccess_marksEnd() {
        extension.afterEach(mockContext);

        metricsMock.verify(() -> ExecutionMetrics.markEnd(TEST_ID), times(1));
    }

    @Test
    public void afterEach_onSuccess_notifiesHookRegistry() {
        extension.afterEach(mockContext);

        hookMock.verify(() -> HookRegistry.onTestEnd(TEST_ID, "PASSED"), times(1));
    }

    @Test
    public void afterEach_onFailure_recordsFailedStatus() {
        when(mockContext.getExecutionException())
                .thenReturn(Optional.of(new AssertionError("test failed")));
        screenshotMock.when(() -> ScreenshotManager.capture(anyString()))
                .thenReturn("/tmp/screenshot.png");

        extension.afterEach(mockContext);

        metricsMock.verify(() -> ExecutionMetrics.recordStatus(TEST_ID, "FAILED"), times(1));
    }

    @Test
    public void afterEach_onFailure_notifiesHookRegistry() {
        Throwable cause = new RuntimeException("boom");
        when(mockContext.getExecutionException()).thenReturn(Optional.of(cause));
        screenshotMock.when(() -> ScreenshotManager.capture(anyString())).thenReturn(null);

        extension.afterEach(mockContext);

        hookMock.verify(() -> HookRegistry.onTestFailure(eq(TEST_ID), eq(cause)), times(1));
    }

    @Test
    public void afterEach_onFailure_capturesScreenshot() {
        when(mockContext.getExecutionException())
                .thenReturn(Optional.of(new RuntimeException("test blew up")));
        screenshotMock.when(() -> ScreenshotManager.capture("sampleMethod"))
                .thenReturn("/tmp/screenshot.png");

        extension.afterEach(mockContext);

        screenshotMock.verify(() -> ScreenshotManager.capture("sampleMethod"), times(1));
    }

    @Test
    public void afterEach_cleanupRunsEvenWhenTestThrows() {
        when(mockContext.getExecutionException())
                .thenReturn(Optional.of(new RuntimeException("test blew up")));
        screenshotMock.when(() -> ScreenshotManager.capture(anyString())).thenReturn(null);

        extension.afterEach(mockContext);

        // Cleanup should still happen in the finally block
        multiSessionMock.verify(MultiSessionManager::clearAll, times(1));
        contextMock.verify(TestFlyContext::clearCurrentTestId, times(1));
    }

    // ----------------------------------------------------------
    // supportsParameter / resolveParameter
    // ----------------------------------------------------------

    @Test
    public void supportsParameter_returnsTrue_forWebDriverParameter() throws Exception {
        ParameterContext paramCtx = mock(ParameterContext.class);
        Parameter param = MethodWithWebDriverParam.class
                .getDeclaredMethod("testWithDriver", WebDriver.class)
                .getParameters()[0];
        when(paramCtx.getParameter()).thenReturn(param);

        assertTrue(extension.supportsParameter(paramCtx, mockContext));
    }

    @Test
    public void supportsParameter_returnsFalse_forStringParameter() throws Exception {
        ParameterContext paramCtx = mock(ParameterContext.class);
        Parameter param = MethodWithStringParam.class
                .getDeclaredMethod("testWithString", String.class)
                .getParameters()[0];
        when(paramCtx.getParameter()).thenReturn(param);

        assertFalse(extension.supportsParameter(paramCtx, mockContext));
    }

    @Test
    public void resolveParameter_returnsDriverFromDriverManager() {
        WebDriver mockDriver = mock(WebDriver.class);
        driverManagerMock.when(DriverManager::getDriver).thenReturn(mockDriver);

        ParameterContext paramCtx = mock(ParameterContext.class);
        Object resolved = extension.resolveParameter(paramCtx, mockContext);

        assertSame(resolved, mockDriver);
    }

    // ----------------------------------------------------------
    // testId (package-private static helper, accessed via reflection)
    // ----------------------------------------------------------

    @Test
    public void testId_returnsClassHashPlusMethod() throws Exception {
        ExtensionContext ctx = mock(ExtensionContext.class);
        when(ctx.getRequiredTestClass()).thenReturn((Class) SampleTestClass.class);
        when(ctx.getRequiredTestMethod()).thenReturn(
                SampleTestClass.class.getDeclaredMethod("sampleMethod"));

        Method testIdMethod = TestFlyExtension.class.getDeclaredMethod(
                "testId", ExtensionContext.class);
        testIdMethod.setAccessible(true);
        String result = (String) testIdMethod.invoke(null, ctx);

        assertEquals(result, SampleTestClass.class.getName() + "#sampleMethod");
    }

    // ----------------------------------------------------------
    // Helper classes for test fixtures
    // ----------------------------------------------------------

    @SuppressWarnings("unused")
    static class SampleTestClass {
        public void sampleMethod() {
        }
    }

    @SuppressWarnings("unused")
    static class MethodWithWebDriverParam {
        public void testWithDriver(WebDriver driver) {
        }
    }

    @SuppressWarnings("unused")
    static class MethodWithStringParam {
        public void testWithString(String value) {
        }
    }
}
