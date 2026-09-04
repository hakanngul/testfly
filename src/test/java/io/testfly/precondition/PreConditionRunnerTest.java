package io.testfly.precondition;

import io.testfly.driver.DriverManager;
import org.mockito.MockedStatic;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.testng.internal.ConstructorOrMethod;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

/**
 * Unit tests for {@link PreConditionRunner}.
 * Thread-safe for parallel=methods via singleThreaded.
 *
 * Uses unique condition names to avoid interference from providers registered
 * in other tests (the registry is a static, never-cleared list).
 */
@Test(singleThreaded = true)
public class PreConditionRunnerTest {

    private MockedStatic<DriverManager> driverManagerMock;
    private WebDriver mockDriver;

    @BeforeMethod
    public void setup() {
        Set<Cookie> cookies = new HashSet<>();
        cookies.add(new Cookie("session", "test-value"));

        mockDriver = mock(WebDriver.class, withSettings().extraInterfaces(JavascriptExecutor.class));
        WebDriver.Options options = mock(WebDriver.Options.class);
        when(mockDriver.manage()).thenReturn(options);
        when(options.getCookies()).thenReturn(cookies);
        when(((JavascriptExecutor) mockDriver).executeScript(anyString())).thenReturn(new HashMap<>());

        driverManagerMock = mockStatic(DriverManager.class);
        driverManagerMock.when(DriverManager::getDriver).thenReturn(mockDriver);

        PreconditionSessionCache.clearAll();
    }

    @AfterMethod
    public void tearDown() {
        if (driverManagerMock != null)
            driverManagerMock.close();
        PreconditionSessionCache.clearAll();
    }

    // ── Precondition passes → dependent test runs normally ────────────────────

    @Test
    public void run_noPreConditionAnnotation_isNoOp() throws Exception {
        ITestResult result = mockResult(getMethod("testWithoutPreCondition"));
        // Should not throw or do anything
        PreConditionRunner.run(result);
    }

    @Test
    public void run_withPreCondition_executesProvider() throws Exception {
        // Use a unique provider name to avoid finding stale instances from previous
        // tests
        RunnerLoginProvider provider = new RunnerLoginProvider();
        PreConditionRegistry.register(provider);

        ITestResult result = mockResult(getMethod("testWithRunnerLogin"));
        PreConditionRunner.run(result);

        assertTrue(RunnerLoginProvider.invoked, "Provider method should have been called");
        // Reset for other tests
        RunnerLoginProvider.invoked = false;
    }

    // ── Precondition fails → dependent test is skipped (not failed) ──────────

    @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = ".*No @ConditionProvider found.*")
    public void run_unknownPreCondition_throwsException() throws Exception {
        ITestResult result = mockResult(getMethod("testWithUnknownPreCondition"));
        PreConditionRunner.run(result);
    }

    @Test(expectedExceptions = RuntimeException.class, expectedExceptionsMessageRegExp = ".*Provider failed.*")
    public void run_providerThrows_wrapsException() throws Exception {
        PreConditionRegistry.register(new FailingProvider());

        ITestResult result = mockResult(getMethod("testWithFailingPreCondition"));
        PreConditionRunner.run(result);
    }

    // ── Multiple preconditions → all checked ─────────────────────────────────

    @Test
    public void run_multiplePreConditions_allExecuted() throws Exception {
        MultiProvider provider = new MultiProvider();
        PreConditionRegistry.register(provider);

        ITestResult result = mockResult(getMethod("testWithMultiplePreConditions"));
        PreConditionRunner.run(result);

        assertTrue(MultiProvider.loginInvoked, "login provider should have been called");
        assertTrue(MultiProvider.cookiesInvoked, "acceptCookies provider should have been called");
        // Reset
        MultiProvider.loginInvoked = false;
        MultiProvider.cookiesInvoked = false;
    }

    @Test
    public void run_methodLevelAnnotation_worksCorrectly() throws Exception {
        MethodProvider provider = new MethodProvider();
        PreConditionRegistry.register(provider);

        Method method = getMethod("testWithMethodLevelCondition");
        PreConditionRunner.run(method, false);

        assertTrue(MethodProvider.invoked, "Provider should be called for method-level annotation");
        MethodProvider.invoked = false;
    }

    @Test
    public void run_classLevelAnnotation_worksCorrectly() throws Exception {
        ClassProvider provider = new ClassProvider();
        PreConditionRegistry.register(provider);

        Method method = TestClassWithClassLevelAnnotation.class.getMethod("testMethod");
        PreConditionRunner.run(method, false);

        assertTrue(ClassProvider.invoked, "Provider should be called for class-level annotation");
        ClassProvider.invoked = false;
    }

    @Test
    public void clearAll_clearsSessionCache() throws Exception {
        CacheTestProvider provider = new CacheTestProvider();
        PreConditionRegistry.register(provider);

        ITestResult result = mockResult(getMethod("testWithCacheCondition"));
        PreConditionRunner.run(result);
        assertTrue(CacheTestProvider.invoked);

        // Reset and run again — should use cache (provider NOT called again)
        CacheTestProvider.invoked = false;
        PreConditionRunner.run(result);
        assertFalse(CacheTestProvider.invoked, "Second run should use cached session");

        // Clear and run again — should call provider
        PreConditionRunner.clearAll();
        PreConditionRunner.run(result);
        assertTrue(CacheTestProvider.invoked, "After clearAll, provider should be called again");
    }

    @Test
    public void run_noAnnotationOnMethodOrClass_isNoOp() throws Exception {
        Method method = getMethod("testWithoutPreCondition");
        PreConditionRunner.run(method, false);
        // No exception — success
    }

    @Test
    public void run_dataProviderMultipleInvocations_keepsCache() throws Exception {
        CacheTestProvider provider = new CacheTestProvider();
        PreConditionRegistry.register(provider);

        ITestResult result1 = mockResult(getMethod("testWithCacheCondition"));
        PreConditionRunner.run(result1);
        assertTrue(CacheTestProvider.invoked);

        // Second data provider row (invocation count 1, wasRetried false)
        CacheTestProvider.invoked = false;
        ITestResult result2 = mockResult(getMethod("testWithCacheCondition"));
        when(result2.getMethod().getCurrentInvocationCount()).thenReturn(1);
        when(result2.wasRetried()).thenReturn(false);

        PreConditionRunner.run(result2);
        assertFalse(CacheTestProvider.invoked, "Data provider subsequent rows should still use cached session");
    }

    @Test
    public void run_onRetry_invalidatesCacheAndRerunsProvider() throws Exception {
        CacheTestProvider provider = new CacheTestProvider();
        PreConditionRegistry.register(provider);

        ITestResult result1 = mockResult(getMethod("testWithCacheCondition"));
        PreConditionRunner.run(result1);
        assertTrue(CacheTestProvider.invoked);

        // Test fails and is retried: wasRetried = true
        CacheTestProvider.invoked = false;
        ITestResult retryResult = mockResult(getMethod("testWithCacheCondition"));
        when(retryResult.wasRetried()).thenReturn(true);

        PreConditionRunner.run(retryResult);
        assertTrue(CacheTestProvider.invoked, "Retry should invalidate cache and rerun provider");
    }

    // ── Helper methods and classes ───────────────────────────────────────────

    private static ITestResult mockResult(Method method) {
        ConstructorOrMethod com = mock(ConstructorOrMethod.class);
        when(com.getMethod()).thenReturn(method);
        ITestNGMethod ngMethod = mock(ITestNGMethod.class);
        when(ngMethod.getConstructorOrMethod()).thenReturn(com);
        when(ngMethod.getCurrentInvocationCount()).thenReturn(0);
        ITestResult result = mock(ITestResult.class);
        when(result.getMethod()).thenReturn(ngMethod);
        return result;
    }

    private Method getMethod(String name) throws Exception {
        return PreConditionRunnerTest.class.getDeclaredMethod(name);
    }

    @SuppressWarnings("unused")
    private void testWithoutPreCondition() {
    }

    @PreCondition("runner-login")
    @SuppressWarnings("unused")
    private void testWithRunnerLogin() {
    }

    @PreCondition("runner-unknown")
    @SuppressWarnings("unused")
    private void testWithUnknownPreCondition() {
    }

    @PreCondition("runner-failing")
    @SuppressWarnings("unused")
    private void testWithFailingPreCondition() {
    }

    @PreCondition({ "runner-multi-login", "runner-multi-cookies" })
    @SuppressWarnings("unused")
    private void testWithMultiplePreConditions() {
    }

    @PreCondition("runner-method-level")
    @SuppressWarnings("unused")
    private void testWithMethodLevelCondition() {
    }

    @PreCondition("runner-cache-condition")
    @SuppressWarnings("unused")
    private void testWithCacheCondition() {
    }

    @PreCondition("runner-class-level")
    public static class TestClassWithClassLevelAnnotation {
        public void testMethod() {
        }
    }

    // ── Provider implementations with unique condition names ─────────────────

    public static class RunnerLoginProvider extends BaseConditions {
        static boolean invoked = false;

        @ConditionProvider("runner-login")
        public void login() {
            invoked = true;
        }
    }

    public static class FailingProvider extends BaseConditions {
        @ConditionProvider("runner-failing")
        public void failing() {
            throw new RuntimeException("Provider intentionally failed");
        }
    }

    public static class MultiProvider extends BaseConditions {
        static boolean loginInvoked = false;
        static boolean cookiesInvoked = false;

        @ConditionProvider("runner-multi-login")
        public void login() {
            loginInvoked = true;
        }

        @ConditionProvider("runner-multi-cookies")
        public void acceptCookies() {
            cookiesInvoked = true;
        }
    }

    public static class MethodProvider extends BaseConditions {
        static boolean invoked = false;

        @ConditionProvider("runner-method-level")
        public void provide() {
            invoked = true;
        }
    }

    public static class ClassProvider extends BaseConditions {
        static boolean invoked = false;

        @ConditionProvider("runner-class-level")
        public void provide() {
            invoked = true;
        }
    }

    public static class CacheTestProvider extends BaseConditions {
        static boolean invoked = false;

        @ConditionProvider("runner-cache-condition")
        public void provide() {
            invoked = true;
        }
    }
}
