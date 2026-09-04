package io.testfly.unit.listeners;

import io.testfly.browser.BrowserContext;
import io.testfly.client.ApiClient;
import io.testfly.context.ScenarioContext;
import io.testfly.db.DbConnectionFactory;
import io.testfly.driver.DriverManager;
import io.testfly.hooks.HookRegistry;
import io.testfly.internal.TestFlyContext;
import io.testfly.listeners.TestExecutionListener;
import io.testfly.metrics.ExecutionMetrics;
import io.testfly.network.NetworkMock;
import io.testfly.recording.RecordingManager;
import io.testfly.reporting.ScreenshotManager;
import io.testfly.testmanagement.TestManagementReporter;
import org.mockito.MockedStatic;
import org.testng.ITestClass;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.testng.internal.ConstructorOrMethod;

import java.lang.reflect.Method;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

/**
 * Unit tests for {@link TestExecutionListener}.
 * Thread-safe for parallel=methods via singleThreaded.
 */
@Test(singleThreaded = true)
public class TestExecutionListenerTest {

    private TestExecutionListener listener;
    private MockedStatic<ExecutionMetrics> metricsMock;
    private MockedStatic<DriverManager> driverManagerMock;
    private MockedStatic<HookRegistry> hookMock;
    private MockedStatic<ScreenshotManager> screenshotMock;
    private MockedStatic<TestManagementReporter> testManagementMock;
    private MockedStatic<RecordingManager> recordingMock;
    private MockedStatic<NetworkMock> networkMockMock;
    private MockedStatic<ApiClient> apiClientMock;
    private MockedStatic<ScenarioContext> scenarioContextMock;
    private MockedStatic<DbConnectionFactory> dbConnectionFactoryMock;
    private MockedStatic<BrowserContext> browserContextMock;
    private MockedStatic<TestFlyContext> testFlyContextMock;
    private TestManagementReporter mockTestManagementReporter;

    @BeforeMethod
    public void setup() {
        listener = new TestExecutionListener();

        metricsMock = mockStatic(ExecutionMetrics.class);
        driverManagerMock = mockStatic(DriverManager.class);
        hookMock = mockStatic(HookRegistry.class);
        screenshotMock = mockStatic(ScreenshotManager.class);
        recordingMock = mockStatic(RecordingManager.class);
        networkMockMock = mockStatic(NetworkMock.class);
        apiClientMock = mockStatic(ApiClient.class);
        scenarioContextMock = mockStatic(ScenarioContext.class);
        dbConnectionFactoryMock = mockStatic(DbConnectionFactory.class);
        browserContextMock = mockStatic(BrowserContext.class);
        testFlyContextMock = mockStatic(TestFlyContext.class);

        mockTestManagementReporter = mock(TestManagementReporter.class);
        testManagementMock = mockStatic(TestManagementReporter.class);
        testManagementMock.when(TestManagementReporter::getInstance).thenReturn(mockTestManagementReporter);

        testFlyContextMock.when(TestFlyContext::isInitialized).thenReturn(false);
    }

    @AfterMethod
    public void tearDown() {
        if (metricsMock != null)
            metricsMock.close();
        if (driverManagerMock != null)
            driverManagerMock.close();
        if (hookMock != null)
            hookMock.close();
        if (screenshotMock != null)
            screenshotMock.close();
        if (testManagementMock != null)
            testManagementMock.close();
        if (recordingMock != null)
            recordingMock.close();
        if (networkMockMock != null)
            networkMockMock.close();
        if (apiClientMock != null)
            apiClientMock.close();
        if (scenarioContextMock != null)
            scenarioContextMock.close();
        if (dbConnectionFactoryMock != null)
            dbConnectionFactoryMock.close();
        if (browserContextMock != null)
            browserContextMock.close();
        if (testFlyContextMock != null)
            testFlyContextMock.close();
    }

    // ── onTestSuccess → metrics updated (pass count incremented) ─────────────

    @Test
    public void onTestSuccess_recordsPassedStatus() throws Exception {
        ITestResult result = mockTestResult("testSuccess", false);

        listener.onTestSuccess(result);

        metricsMock.verify(() -> ExecutionMetrics.recordStatus(
                eq("com.example.TestClass#testSuccess"), eq("PASSED")));
    }

    @Test
    public void onTestSuccess_marksEndTime() throws Exception {
        ITestResult result = mockTestResult("testSuccess", false);

        listener.onTestSuccess(result);

        metricsMock.verify(() -> ExecutionMetrics.markEnd(
                eq("com.example.TestClass#testSuccess")));
    }

    @Test
    public void onTestSuccess_callsHooks() throws Exception {
        ITestResult result = mockTestResult("testSuccess", false);

        listener.onTestSuccess(result);

        hookMock.verify(() -> HookRegistry.onTestEnd(
                eq("com.example.TestClass#testSuccess"), eq("PASSED")));
    }

    @Test
    public void onTestSuccess_quitsDriverWhenShouldQuit() throws Exception {
        ITestResult result = mockTestResult("testSuccess", false);
        driverManagerMock.when(DriverManager::shouldQuitAfterTest).thenReturn(true);

        listener.onTestSuccess(result);

        driverManagerMock.verify(DriverManager::quitDriver);
    }

    @Test
    public void onTestSuccess_clearsContexts() throws Exception {
        ITestResult result = mockTestResult("testSuccess", false);

        listener.onTestSuccess(result);

        scenarioContextMock.verify(ScenarioContext::clear);
        apiClientMock.verify(ApiClient::clearGlobalAuth);
        browserContextMock.verify(BrowserContext::clear);
        networkMockMock.verify(NetworkMock::cleanup);
        testFlyContextMock.verify(TestFlyContext::clearCurrentTestId);
    }

    // ── onTestFailure → screenshot triggered, metrics updated (fail count) ─────

    @Test
    public void onTestFailure_recordsFailedStatus() throws Exception {
        ITestResult result = mockTestResult("testFailure", false);
        when(result.getThrowable()).thenReturn(new AssertionError("Test failed"));

        listener.onTestFailure(result);

        metricsMock.verify(() -> ExecutionMetrics.recordStatus(
                eq("com.example.TestClass#testFailure"), eq("FAILED")));
    }

    @Test
    public void onTestFailure_marksEndTime() throws Exception {
        ITestResult result = mockTestResult("testFailure", false);
        when(result.getThrowable()).thenReturn(new AssertionError("Test failed"));

        listener.onTestFailure(result);

        metricsMock.verify(() -> ExecutionMetrics.markEnd(
                eq("com.example.TestClass#testFailure")));
    }

    @Test
    public void onTestFailure_recordsError() throws Exception {
        ITestResult result = mockTestResult("testFailure", false);
        AssertionError error = new AssertionError("Test failed");
        when(result.getThrowable()).thenReturn(error);

        listener.onTestFailure(result);

        metricsMock.verify(() -> ExecutionMetrics.recordError(
                eq("com.example.TestClass#testFailure"), eq(error)));
    }

    @Test
    public void onTestFailure_callsFailureHook() throws Exception {
        ITestResult result = mockTestResult("testFailure", false);
        AssertionError error = new AssertionError("Test failed");
        when(result.getThrowable()).thenReturn(error);

        listener.onTestFailure(result);

        hookMock.verify(() -> HookRegistry.onTestFailure(
                eq("com.example.TestClass#testFailure"), eq(error)));
    }

    @Test
    public void onTestFailure_recordsRecordingPath() throws Exception {
        ITestResult result = mockTestResult("testFailure", false);
        when(result.getThrowable()).thenReturn(new AssertionError("Test failed"));
        recordingMock.when(() -> RecordingManager.saveOnFailure(any())).thenReturn("/rec/failure.mp4");

        listener.onTestFailure(result);

        metricsMock.verify(() -> ExecutionMetrics.recordRecording(
                eq("com.example.TestClass#testFailure"), eq("/rec/failure.mp4")));
    }

    @Test
    public void onTestFailure_clearsAllContexts() throws Exception {
        ITestResult result = mockTestResult("testFailure", false);
        when(result.getThrowable()).thenReturn(new AssertionError("Test failed"));

        listener.onTestFailure(result);

        scenarioContextMock.verify(ScenarioContext::clear);
        apiClientMock.verify(ApiClient::clearGlobalAuth);
        browserContextMock.verify(BrowserContext::clear);
        testFlyContextMock.verify(TestFlyContext::clearCurrentTestId);
    }

    // ── onTestSkipped → skip count incremented ──────────────────────────────

    @Test
    public void onTestSkipped_recordsSkippedStatus() throws Exception {
        ITestResult result = mockTestResult("testSkipped", false);

        listener.onTestSkipped(result);

        metricsMock.verify(() -> ExecutionMetrics.recordStatus(
                eq("com.example.TestClass#testSkipped"), eq("SKIPPED")));
    }

    @Test
    public void onTestSkipped_marksEndTime() throws Exception {
        ITestResult result = mockTestResult("testSkipped", false);

        listener.onTestSkipped(result);

        metricsMock.verify(() -> ExecutionMetrics.markEnd(
                eq("com.example.TestClass#testSkipped")));
    }

    @Test
    public void onTestSkipped_callsHooks() throws Exception {
        ITestResult result = mockTestResult("testSkipped", false);

        listener.onTestSkipped(result);

        hookMock.verify(() -> HookRegistry.onTestEnd(
                eq("com.example.TestClass#testSkipped"), eq("SKIPPED")));
    }

    @Test
    public void onTestSkipped_quitsDriverWhenShouldQuit() throws Exception {
        ITestResult result = mockTestResult("testSkipped", false);
        driverManagerMock.when(DriverManager::shouldQuitAfterTest).thenReturn(true);

        listener.onTestSkipped(result);

        driverManagerMock.verify(DriverManager::quitDriver);
    }

    @Test
    public void onTestSkipped_clearsContexts() throws Exception {
        ITestResult result = mockTestResult("testSkipped", false);

        listener.onTestSkipped(result);

        scenarioContextMock.verify(ScenarioContext::clear);
        apiClientMock.verify(ApiClient::clearGlobalAuth);
        browserContextMock.verify(BrowserContext::clear);
        testFlyContextMock.verify(TestFlyContext::clearCurrentTestId);
    }

    // ── Status correctly recorded in TestTiming ──────────────────────────────

    @Test
    public void onTestSuccess_recordsPassedInMetrics() throws Exception {
        ITestResult result = mockTestResult("statusTest", false);

        listener.onTestSuccess(result);

        metricsMock.verify(() -> ExecutionMetrics.recordStatus(any(), eq("PASSED")));
    }

    @Test
    public void onTestFailure_recordsFailedInMetrics() throws Exception {
        ITestResult result = mockTestResult("statusTest", false);
        when(result.getThrowable()).thenReturn(new AssertionError("Failed"));

        listener.onTestFailure(result);

        metricsMock.verify(() -> ExecutionMetrics.recordStatus(any(), eq("FAILED")));
    }

    @Test
    public void onTestSkipped_recordsSkippedInMetrics() throws Exception {
        ITestResult result = mockTestResult("statusTest", false);

        listener.onTestSkipped(result);

        metricsMock.verify(() -> ExecutionMetrics.recordStatus(any(), eq("SKIPPED")));
    }

    @Test
    public void onTestSuccess_reportsToTestManagement() throws Exception {
        ITestResult result = mockTestResult("mgmtTest", false);

        listener.onTestSuccess(result);

        verify(mockTestManagementReporter).onTestResult(any(), eq("PASSED"), isNull());
    }

    @Test
    public void onTestSkipped_reportsToTestManagement() throws Exception {
        ITestResult result = mockTestResult("mgmtTest", false);

        listener.onTestSkipped(result);

        verify(mockTestManagementReporter).onTestResult(any(), eq("SKIPPED"), isNull());
    }

    // ── Helper methods ───────────────────────────────────────────────────────

    private ITestResult mockTestResult(String methodName, boolean isApiTest) throws Exception {
        Method method = TestExecutionListenerTest.class.getDeclaredMethod("dummyTestMethod");

        ConstructorOrMethod com = mock(ConstructorOrMethod.class);
        when(com.getMethod()).thenReturn(method);

        ITestNGMethod ngMethod = mock(ITestNGMethod.class);
        when(ngMethod.getConstructorOrMethod()).thenReturn(com);
        when(ngMethod.getQualifiedName()).thenReturn("com.example.TestClass#" + methodName);
        when(ngMethod.getMethodName()).thenReturn(methodName);
        when(ngMethod.getDescription()).thenReturn("Test description");

        Class<?> testClass = isApiTest ? ApiTestClass.class : RegularTestClass.class;
        ITestClass iTestClass = mock(ITestClass.class);
        when(iTestClass.getRealClass()).thenReturn((Class) testClass);

        ITestResult result = mock(ITestResult.class);
        when(result.getMethod()).thenReturn(ngMethod);
        when(result.getTestClass()).thenReturn(iTestClass);

        return result;
    }

    @SuppressWarnings("unused")
    private void dummyTestMethod() {
    }

    public static class RegularTestClass {
    }

    public static class ApiTestClass extends io.testfly.test.BaseApiTest {
    }
}
