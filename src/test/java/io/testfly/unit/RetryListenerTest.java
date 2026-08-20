package io.testfly.unit;

import io.testfly.config.TestFlyConfig;
import io.testfly.internal.TestFlyContext;
import io.testfly.listeners.Retryable;
import io.testfly.listeners.RetryListener;
import org.mockito.Mockito;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
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
 * Verifies retry decision logic in isolation using Mockito.
 */
public class RetryListenerTest {

    @AfterMethod
    public void resetContext() throws Exception {
        Field configField = TestFlyContext.class.getDeclaredField("CONFIG");
        configField.setAccessible(true);
        AtomicReference<?> ref = (AtomicReference<?>) configField.get(null);
        ref.set(null);
    }

    // ----------------------------------------------------------
    // Uninitialized context guard
    // ----------------------------------------------------------

    @Test
    public void retry_whenContextNotInitialized_returnsFalse() {
        RetryListener listener = new RetryListener();
        ITestResult result = mockResult(plainMethod());

        assertFalse(listener.retry(result));
    }

    // ----------------------------------------------------------
    // Kill switch
    // ----------------------------------------------------------

    @Test
    public void retry_whenRetryDisabled_returnsFalse() {
        initContext(false, 3);
        RetryListener listener = new RetryListener();
        ITestResult result = mockResult(plainMethod());

        assertFalse(listener.retry(result));
    }

    // ----------------------------------------------------------
    // Global retry (retry.enabled=true)
    // ----------------------------------------------------------

    @Test
    public void retry_globalEnabled_retriesUpToMaxAttempts() {
        initContext(true, 2);
        RetryListener listener = new RetryListener();
        ITestResult result = mockResult(plainMethod());

        assertTrue(listener.retry(result));  // attempt 1
        assertTrue(listener.retry(result));  // attempt 2
        assertFalse(listener.retry(result)); // exhausted
    }

    @Test
    public void retry_globalEnabled_singleAttempt_retriesOnce() {
        initContext(true, 1);
        RetryListener listener = new RetryListener();
        ITestResult result = mockResult(plainMethod());

        assertTrue(listener.retry(result));
        assertFalse(listener.retry(result));
    }

    // ----------------------------------------------------------
    // @Retryable annotation
    // ----------------------------------------------------------

    @Test
    public void retry_retryableAnnotation_withGlobalEnabled_retries() throws Exception {
        initContext(true, 1);
        RetryListener listener = new RetryListener();
        ITestResult result = mockResult(retryableMethod());

        assertTrue(listener.retry(result));
        assertFalse(listener.retry(result));
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

    /** A plain method with no @Retryable annotation. */
    private static Method plainMethod() {
        try {
            return RetryListenerTest.class.getDeclaredMethod("dummyPlain");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    /** A method annotated with @Retryable. */
    private static Method retryableMethod() {
        try {
            return RetryListenerTest.class.getDeclaredMethod("dummyRetryable");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    // Dummy methods used only as reflection targets above
    @SuppressWarnings("unused") private void dummyPlain() {}
    @Retryable @SuppressWarnings("unused") private void dummyRetryable() {}
}
