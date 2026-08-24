package io.testfly.unit;

import io.testfly.config.TestFlyConfig;
import io.testfly.cucumber.CucumberRetryContext;
import io.testfly.internal.TestFlyContext;
import io.testfly.listeners.Retryable;
import io.testfly.listeners.RetryListener;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.testng.internal.ConstructorOrMethod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.*;

/**
 * Unit tests for {@link RetryListener}.
 * Thread-safe for parallel=methods via singleThreaded + global lock on TestFlyContext.
 */
@Test(singleThreaded = true)
public class RetryListenerTest {

    @BeforeMethod
    public void setup() throws Exception {
        synchronized (TestFlyContext.class) {
            resetContextInternal();
            CucumberRetryContext.clear();
        }
    }

    @AfterMethod
    public void resetContext() throws Exception {
        synchronized (TestFlyContext.class) {
            resetContextInternal();
            CucumberRetryContext.clear();
        }
    }

    private static void resetContextInternal() throws Exception {
        Field configField = TestFlyContext.class.getDeclaredField("CONFIG");
        configField.setAccessible(true);
        AtomicReference<?> ref = (AtomicReference<?>) configField.get(null);
        ref.set(null);
        TestFlyContext.clearCurrentTestId();
    }

    @Test
    public void retry_whenContextNotInitialized_returnsFalse() {
        synchronized (TestFlyContext.class) {
            CucumberRetryContext.clear();
            RetryListener listener = new RetryListener();
            ITestResult result = mockResult(plainMethod());
            assertFalse(listener.retry(result));
        }
    }

    @Test
    public void retry_whenRetryDisabled_returnsFalse() {
        synchronized (TestFlyContext.class) {
            CucumberRetryContext.clear();
            initContext(false, 3);
            RetryListener listener = new RetryListener();
            ITestResult result = mockResult(plainMethod());
            assertFalse(listener.retry(result));
        }
    }

    @Test
    public void retry_globalEnabled_retriesUpToMaxAttempts() {
        synchronized (TestFlyContext.class) {
            CucumberRetryContext.clear();
            initContext(true, 2);
            RetryListener listener = new RetryListener();
            ITestResult result = mockResult(plainMethod());
            assertTrue(listener.retry(result));
            assertTrue(listener.retry(result));
            assertFalse(listener.retry(result));
        }
    }

    @Test
    public void retry_globalEnabled_singleAttempt_retriesOnce() {
        synchronized (TestFlyContext.class) {
            CucumberRetryContext.clear();
            initContext(true, 1);
            RetryListener listener = new RetryListener();
            ITestResult result = mockResult(plainMethod());
            assertTrue(listener.retry(result));
            assertFalse(listener.retry(result));
        }
    }

    @Test
    public void retry_retryableAnnotation_withGlobalEnabled_retries() throws Exception {
        synchronized (TestFlyContext.class) {
            CucumberRetryContext.clear();
            initContext(true, 1);
            RetryListener listener = new RetryListener();
            ITestResult result = mockResult(retryableMethod());
            assertTrue(listener.retry(result));
            assertFalse(listener.retry(result));
        }
    }

    // ----------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------

    private static void initContext(boolean enabled, int maxAttempts) {
        TestFlyConfig config = new TestFlyConfig();
        TestFlyConfig.Browser browser = new TestFlyConfig.Browser();
        browser.setName("chrome");
        config.setBrowser(browser);
        TestFlyConfig.Execution execution = new TestFlyConfig.Execution();
        execution.setMode("local");
        execution.setBaseUrl("https://example.com");
        execution.setMaxActiveSessions(5);
        config.setExecution(execution);
        TestFlyConfig.Timeouts timeouts = new TestFlyConfig.Timeouts();
        timeouts.setExplicit(10);
        timeouts.setPageLoad(30);
        config.setTimeouts(timeouts);
        TestFlyConfig.Retry retry = new TestFlyConfig.Retry();
        retry.setEnabled(enabled);
        retry.setMaxAttempts(maxAttempts);
        config.setRetry(retry);
        TestFlyContext.initialize(config);
    }

    private static ITestResult mockResult(Method method) {
        ConstructorOrMethod com = mock(ConstructorOrMethod.class);
        when(com.getMethod()).thenReturn(method);
        ITestNGMethod ngMethod = mock(ITestNGMethod.class);
        when(ngMethod.getConstructorOrMethod()).thenReturn(com);
        ITestResult result = mock(ITestResult.class);
        when(result.getMethod()).thenReturn(ngMethod);
        return result;
    }

    private static Method plainMethod() {
        try {
            return RetryListenerTest.class.getDeclaredMethod("dummyPlain");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private static Method retryableMethod() {
        try {
            return RetryListenerTest.class.getDeclaredMethod("dummyRetryable");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unused") private void dummyPlain() {}
    @Retryable @SuppressWarnings("unused") private void dummyRetryable() {}
}
