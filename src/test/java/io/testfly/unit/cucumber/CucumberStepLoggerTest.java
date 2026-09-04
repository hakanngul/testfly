package io.testfly.unit.cucumber;

import io.cucumber.plugin.event.EventHandler;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.HookTestStep;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.Result;
import io.cucumber.plugin.event.Status;
import io.cucumber.plugin.event.TestStepFinished;
import io.cucumber.plugin.event.TestStepStarted;
import io.testfly.cucumber.CucumberStepLogger;
import io.testfly.internal.TestFlyContext;
import io.testfly.metrics.ExecutionMetrics;
import io.testfly.reporting.ScreenshotManager;
import io.testfly.steps.StepLogger;
import io.testfly.steps.StepStatus;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

/**
 * Unit tests for {@link CucumberStepLogger}.
 * Thread-safe for parallel=methods via singleThreaded.
 */
@Test(singleThreaded = true)
public class CucumberStepLoggerTest {

    private MockedStatic<StepLogger> stepLoggerMock;
    private MockedStatic<TestFlyContext> contextMock;
    private MockedStatic<ExecutionMetrics> metricsMock;
    private MockedStatic<ScreenshotManager> screenshotMock;

    @BeforeMethod
    public void setup() {
        stepLoggerMock = mockStatic(StepLogger.class);
        contextMock = mockStatic(TestFlyContext.class);
        metricsMock = mockStatic(ExecutionMetrics.class);
        screenshotMock = mockStatic(ScreenshotManager.class);
    }

    @AfterMethod
    public void tearDown() {
        if (stepLoggerMock != null)
            stepLoggerMock.close();
        if (contextMock != null)
            contextMock.close();
        if (metricsMock != null)
            metricsMock.close();
        if (screenshotMock != null)
            screenshotMock.close();
    }

    @SuppressWarnings("unchecked")
    private static <T> EventHandler<T> captureHandler(EventPublisher publisher, Class<T> eventType) {
        ArgumentCaptor<EventHandler<T>> captor = ArgumentCaptor.forClass((Class) EventHandler.class);
        verify(publisher).registerHandlerFor(eq(eventType), captor.capture());
        return captor.getValue();
    }

    // ── Step start → StepLogger records step name ─────────────────────────────

    @Test
    public void onStepStartedAndFinished_pickleStep_logsPassStatus() {
        CucumberStepLogger logger = new CucumberStepLogger();
        EventPublisher publisher = mock(EventPublisher.class);
        logger.setEventPublisher(publisher);

        EventHandler<TestStepStarted> startHandler = captureHandler(publisher, TestStepStarted.class);
        EventHandler<TestStepFinished> finishHandler = captureHandler(publisher, TestStepFinished.class);

        PickleStepTestStep pickleStep = mock(PickleStepTestStep.class);
        when(pickleStep.getStepText()).thenReturn("the user opens the login page");

        TestStepStarted startEvent = mock(TestStepStarted.class);
        when(startEvent.getTestStep()).thenReturn(pickleStep);

        Result passedResult = mock(Result.class);
        when(passedResult.getStatus()).thenReturn(Status.PASSED);

        TestStepFinished finishEvent = mock(TestStepFinished.class);
        when(finishEvent.getTestStep()).thenReturn(pickleStep);
        when(finishEvent.getResult()).thenReturn(passedResult);

        startHandler.receive(startEvent);
        finishHandler.receive(finishEvent);

        stepLoggerMock.verify(() -> StepLogger.step("the user opens the login page", StepStatus.PASS, false));
    }

    @Test
    public void onStepFinished_failedStep_takesScreenshot() {
        CucumberStepLogger logger = new CucumberStepLogger();
        EventPublisher publisher = mock(EventPublisher.class);
        logger.setEventPublisher(publisher);

        EventHandler<TestStepStarted> startHandler = captureHandler(publisher, TestStepStarted.class);
        EventHandler<TestStepFinished> finishHandler = captureHandler(publisher, TestStepFinished.class);

        PickleStepTestStep pickleStep = mock(PickleStepTestStep.class);
        when(pickleStep.getStepText()).thenReturn("the user clicks submit");

        TestStepStarted startEvent = mock(TestStepStarted.class);
        when(startEvent.getTestStep()).thenReturn(pickleStep);

        Result failedResult = mock(Result.class);
        when(failedResult.getStatus()).thenReturn(Status.FAILED);

        TestStepFinished finishEvent = mock(TestStepFinished.class);
        when(finishEvent.getTestStep()).thenReturn(pickleStep);
        when(finishEvent.getResult()).thenReturn(failedResult);

        startHandler.receive(startEvent);
        finishHandler.receive(finishEvent);

        stepLoggerMock.verify(() -> StepLogger.step("the user clicks submit", StepStatus.FAIL, true));
    }

    @Test
    public void setEventPublisher_registersBothHandlers() {
        CucumberStepLogger logger = new CucumberStepLogger();
        EventPublisher publisher = mock(EventPublisher.class);
        logger.setEventPublisher(publisher);

        verify(publisher, times(1)).registerHandlerFor(eq(TestStepStarted.class), any());
        verify(publisher, times(1)).registerHandlerFor(eq(TestStepFinished.class), any());
    }

    @Test
    public void hookStep_doesNotLogToStepLogger() {
        CucumberStepLogger logger = new CucumberStepLogger();
        EventPublisher publisher = mock(EventPublisher.class);
        logger.setEventPublisher(publisher);

        EventHandler<TestStepStarted> startHandler = captureHandler(publisher, TestStepStarted.class);
        EventHandler<TestStepFinished> finishHandler = captureHandler(publisher, TestStepFinished.class);

        HookTestStep hookStep = mock(HookTestStep.class);

        TestStepStarted startEvent = mock(TestStepStarted.class);
        when(startEvent.getTestStep()).thenReturn(hookStep);

        TestStepFinished finishEvent = mock(TestStepFinished.class);
        when(finishEvent.getTestStep()).thenReturn(hookStep);

        startHandler.receive(startEvent);
        finishHandler.receive(finishEvent);

        stepLoggerMock.verifyNoInteractions();
    }

    // ── Status mapping verification ──────────────────────────────────────────

    @Test
    public void statusMapping_allStatuses() {
        assertEquals(CucumberStepLogger.mapStatus(Status.PASSED), StepStatus.PASS);
        assertEquals(CucumberStepLogger.mapStatus(Status.FAILED), StepStatus.FAIL);
        assertEquals(CucumberStepLogger.mapStatus(Status.SKIPPED), StepStatus.WARN);
        assertEquals(CucumberStepLogger.mapStatus(Status.PENDING), StepStatus.WARN);
        assertEquals(CucumberStepLogger.mapStatus(Status.UNDEFINED), StepStatus.WARN);
        assertEquals(CucumberStepLogger.mapStatus(Status.AMBIGUOUS), StepStatus.WARN);
        assertEquals(CucumberStepLogger.mapStatus(null), StepStatus.INFO);
    }
}
