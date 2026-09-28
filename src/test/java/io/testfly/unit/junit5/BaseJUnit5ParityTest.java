package io.testfly.unit.junit5;

import io.testfly.assertion.SoftAssertions;
import io.testfly.config.TestFlyConfig;
import io.testfly.driver.DriverManager;
import io.testfly.hooks.HookRegistry;
import io.testfly.internal.TestFlyContext;
import io.testfly.junit5.BaseJUnit5ApiTest;
import io.testfly.junit5.BaseJUnit5Test;
import io.testfly.junit5.TestFlyExtension;
import io.testfly.metrics.ExecutionMetrics;
import io.testfly.reporting.ScreenshotManager;
import io.testfly.test.NoBrowser;
import io.testfly.test.support.*;
import io.testfly.testmanagement.TestManagementReporter;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.MockedStatic;
import org.opentest4j.TestAbortedException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

/**
 * Unit tests verifying JUnit 5 parity with TestNG (BaseTest / BaseApiTest).
 */
@Test(singleThreaded = true)
public class BaseJUnit5ParityTest {

    private static final String TEST_ID = "io.testfly.unit.junit5.BaseJUnit5ParityTest$SampleTestClass#testMethod";

    private TestFlyExtension extension;
    private ExtensionContext mockContext;

    private MockedStatic<DriverManager> driverManagerMock;
    private MockedStatic<TestFlyContext> contextMock;
    private MockedStatic<HookRegistry> hookMock;
    private MockedStatic<ExecutionMetrics> metricsMock;
    private MockedStatic<ScreenshotManager> screenshotMock;
    private MockedStatic<TestManagementReporter> tmReporterMock;

    @BeforeMethod
    public void setup() throws Exception {
        extension = new TestFlyExtension();
        mockContext = mock(ExtensionContext.class);

        when(mockContext.getRequiredTestClass()).thenReturn((Class) SampleTestClass.class);
        when(mockContext.getRequiredTestMethod()).thenReturn(SampleTestClass.class.getDeclaredMethod("testMethod"));
        when(mockContext.getDisplayName()).thenReturn("testMethod()");
        when(mockContext.getExecutionException()).thenReturn(Optional.empty());

        driverManagerMock = mockStatic(DriverManager.class);
        driverManagerMock.when(DriverManager::shouldQuitAfterTest).thenReturn(true);

        TestFlyConfig config = new TestFlyConfig();
        contextMock = mockStatic(TestFlyContext.class);
        contextMock.when(TestFlyContext::getConfig).thenReturn(config);
        contextMock.when(TestFlyContext::getCurrentTestId).thenReturn(TEST_ID);

        hookMock = mockStatic(HookRegistry.class);
        metricsMock = mockStatic(ExecutionMetrics.class);
        screenshotMock = mockStatic(ScreenshotManager.class);

        TestManagementReporter tm = mock(TestManagementReporter.class);
        tmReporterMock = mockStatic(TestManagementReporter.class);
        tmReporterMock.when(TestManagementReporter::getInstance).thenReturn(tm);
    }

    @AfterMethod
    public void teardown() {
        if (driverManagerMock != null) driverManagerMock.close();
        if (contextMock != null) contextMock.close();
        if (hookMock != null) hookMock.close();
        if (metricsMock != null) metricsMock.close();
        if (screenshotMock != null) screenshotMock.close();
        if (tmReporterMock != null) tmReporterMock.close();
        SoftAssertions.clear();
    }

    @Test
    public void baseJUnit5Test_implementsAllSupportInterfaces() {
        Class<?> clazz = BaseJUnit5Test.class;
        assertTrue(LocatorSupport.class.isAssignableFrom(clazz));
        assertTrue(AssertionSupport.class.isAssignableFrom(clazz));
        assertTrue(ActionSupport.class.isAssignableFrom(clazz));
        assertTrue(SessionSupport.class.isAssignableFrom(clazz));
        assertTrue(SoftAssertSupport.class.isAssignableFrom(clazz));
        assertTrue(TestDataSupport.class.isAssignableFrom(clazz));
        assertTrue(ApiSupport.class.isAssignableFrom(clazz));
        assertTrue(ContextSupport.class.isAssignableFrom(clazz));
        assertTrue(NavigationSupport.class.isAssignableFrom(clazz));
        assertTrue(BrowserSupport.class.isAssignableFrom(clazz));
        assertTrue(VisualSupport.class.isAssignableFrom(clazz));
        assertTrue(DbSupport.class.isAssignableFrom(clazz));
        assertTrue(EmailSupport.class.isAssignableFrom(clazz));
        assertTrue(AccessibilitySupport.class.isAssignableFrom(clazz));
        assertTrue(PerformanceSupport.class.isAssignableFrom(clazz));
        assertTrue(ClockSupport.class.isAssignableFrom(clazz));
        assertTrue(StepSupport.class.isAssignableFrom(clazz));
    }

    @Test
    public void baseJUnit5Test_isAnnotatedWithExtendWithTestFlyExtension() {
        ExtendWith ext = BaseJUnit5Test.class.getAnnotation(ExtendWith.class);
        assertNotNull(ext);
        assertEquals(ext.value()[0], TestFlyExtension.class);
    }

    @Test
    public void baseJUnit5ApiTest_implementsExpectedInterfacesAndHasNoBrowser() {
        Class<?> clazz = BaseJUnit5ApiTest.class;
        assertTrue(SoftAssertSupport.class.isAssignableFrom(clazz));
        assertTrue(TestDataSupport.class.isAssignableFrom(clazz));
        assertTrue(ApiSupport.class.isAssignableFrom(clazz));
        assertTrue(ContextSupport.class.isAssignableFrom(clazz));
        assertTrue(StepSupport.class.isAssignableFrom(clazz));
        assertTrue(DbSupport.class.isAssignableFrom(clazz));
        assertTrue(EmailSupport.class.isAssignableFrom(clazz));

        assertNotNull(clazz.getAnnotation(NoBrowser.class));
        ExtendWith ext = clazz.getAnnotation(ExtendWith.class);
        assertNotNull(ext);
        assertEquals(ext.value()[0], TestFlyExtension.class);
    }

    @Test
    public void afterEach_failsWhenSoftAssertionsFailed() {
        SoftAssertions.get().that(false, "Expected heading to be visible");

        assertThrows(AssertionError.class, () -> extension.afterEach(mockContext));

        metricsMock.verify(() -> ExecutionMetrics.recordStatus(TEST_ID, "FAILED"), times(1));
        hookMock.verify(() -> HookRegistry.onTestFailure(eq(TEST_ID), any()), times(1));
    }

    @Test
    public void afterEach_abortedAssumption_recordsSkipped() {
        when(mockContext.getExecutionException()).thenReturn(Optional.of(new TestAbortedException("quarantined")));

        extension.afterEach(mockContext);

        metricsMock.verify(() -> ExecutionMetrics.recordStatus(TEST_ID, "SKIPPED"), times(1));
        hookMock.verify(() -> HookRegistry.onTestEnd(TEST_ID, "SKIPPED"), times(1));
        hookMock.verify(() -> HookRegistry.onTestFailure(anyString(), any()), never());
    }

    @Test
    public void testDisabled_recordsSkipped() {
        extension.testDisabled(mockContext, Optional.of("disabled reason"));

        metricsMock.verify(() -> ExecutionMetrics.recordStatus(TEST_ID, "SKIPPED"), times(1));
        hookMock.verify(() -> HookRegistry.onTestEnd(TEST_ID, "SKIPPED"), times(1));
    }

    @Test
    public void beforeEach_setsCurrentTestClassAndMethod() {
        extension.beforeEach(mockContext);

        contextMock.verify(() -> TestFlyContext.setCurrentTest(SampleTestClass.class, mockContext.getRequiredTestMethod()), times(1));
    }

    static class SampleTestClass {
        public void testMethod() {}
    }
}
