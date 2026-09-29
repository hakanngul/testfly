package io.testfly.unit.cucumber;

import io.cucumber.java.Scenario;
import io.cucumber.java.Status;
import io.testfly.config.TestFlyConfig;
import io.testfly.cucumber.CucumberContext;
import io.testfly.cucumber.CucumberHooks;
import io.testfly.driver.DriverManager;
import io.testfly.internal.TestFlyContext;
import io.testfly.metrics.ExecutionMetrics;
import io.testfly.recording.RecordingManager;
import io.testfly.reporting.ScreenshotManager;
import org.mockito.MockedStatic;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

/**
 * Unit tests for {@link CucumberHooks}.
 */
@Test(singleThreaded = true)
public class CucumberHooksTest {

    private MockedStatic<DriverManager> driverManagerMock;
    private MockedStatic<RecordingManager> recordingManagerMock;
    private MockedStatic<ScreenshotManager> screenshotManagerMock;
    private MockedStatic<io.testfly.lifecycle.FrameworkBootstrap> frameworkBootstrapMock;

    @BeforeMethod
    public void setup() {
        driverManagerMock = mockStatic(DriverManager.class);
        recordingManagerMock = mockStatic(RecordingManager.class);
        screenshotManagerMock = mockStatic(ScreenshotManager.class);
        frameworkBootstrapMock = mockStatic(io.testfly.lifecycle.FrameworkBootstrap.class);

        if (!TestFlyContext.isInitialized()) {
            TestFlyConfig config = new TestFlyConfig();
            TestFlyConfig.Execution execution = new TestFlyConfig.Execution();
            execution.setMode("local");
            config.setExecution(execution);
            TestFlyContext.setConfig(config);
        }
    }

    @AfterMethod
    public void tearDown() {
        if (driverManagerMock != null) driverManagerMock.close();
        if (recordingManagerMock != null) recordingManagerMock.close();
        if (screenshotManagerMock != null) screenshotManagerMock.close();
        if (frameworkBootstrapMock != null) frameworkBootstrapMock.close();
        CucumberContext.clear();
        TestFlyContext.clearCurrentTestId();
    }

    @Test
    public void testBeforeScenario_initializesContextAndStartsDriver() {
        Scenario scenario = mock(Scenario.class);
        when(scenario.getUri()).thenReturn(URI.create("classpath:features/login.feature"));
        when(scenario.getName()).thenReturn("Successful login");
        when(scenario.getLine()).thenReturn(10);
        when(scenario.getSourceTagNames()).thenReturn(Collections.emptyList());

        CucumberHooks hooks = new CucumberHooks();
        hooks.beforeScenario(scenario);

        assertEquals(CucumberContext.getScenario(), scenario);
        assertEquals(TestFlyContext.getCurrentTestId(), "login.feature#Successful login[L10]");
        driverManagerMock.verify(DriverManager::createDriver, times(1));
    }

    @Test
    public void testBeforeScenario_skipBrowserTag_doesNotCreateDriver() {
        Scenario scenario = mock(Scenario.class);
        when(scenario.getUri()).thenReturn(URI.create("classpath:features/api.feature"));
        when(scenario.getName()).thenReturn("API test");
        when(scenario.getLine()).thenReturn(5);
        when(scenario.getSourceTagNames()).thenReturn(List.of("@api"));

        CucumberHooks hooks = new CucumberHooks();
        hooks.beforeScenario(scenario);

        driverManagerMock.verify(DriverManager::createDriver, never());
    }

    @Test
    public void testAfterScenario_onFailure_capturesScreenshotOnceWithoutCallingCaptureAsBase64() throws IOException {
        Scenario scenario = mock(Scenario.class);
        when(scenario.getUri()).thenReturn(URI.create("classpath:features/cart.feature"));
        when(scenario.getName()).thenReturn("Checkout fails");
        when(scenario.getLine()).thenReturn(15);
        when(scenario.getSourceTagNames()).thenReturn(Collections.emptyList());
        when(scenario.isFailed()).thenReturn(true);
        when(scenario.getStatus()).thenReturn(Status.FAILED);

        File tempScreenshot = File.createTempFile("test_failure", ".png");
        tempScreenshot.deleteOnExit();
        Files.write(tempScreenshot.toPath(), new byte[]{1, 2, 3});

        screenshotManagerMock.when(() -> ScreenshotManager.capture(anyString()))
                .thenReturn(tempScreenshot.getAbsolutePath());

        CucumberHooks hooks = new CucumberHooks();
        hooks.beforeScenario(scenario);
        hooks.afterScenario(scenario);

        // Verify ScreenshotManager.capture was called once
        screenshotManagerMock.verify(() -> ScreenshotManager.capture(anyString()), times(1));
        // Verify ScreenshotManager.captureAsBase64 was NEVER called
        screenshotManagerMock.verify(ScreenshotManager::captureAsBase64, never());

        // Verify screenshot was attached to Cucumber scenario
        verify(scenario).attach(any(byte[].class), eq("image/png"), eq("Failure Screenshot"));
        assertNull(TestFlyContext.getCurrentTestId());
    }

    @Test
    public void testAfterScenario_onPass_quitsDriverAndStopsRecording() {
        Scenario scenario = mock(Scenario.class);
        when(scenario.getUri()).thenReturn(URI.create("classpath:features/pass.feature"));
        when(scenario.getName()).thenReturn("Passes");
        when(scenario.getLine()).thenReturn(20);
        when(scenario.getSourceTagNames()).thenReturn(Collections.emptyList());
        when(scenario.isFailed()).thenReturn(false);
        when(scenario.getStatus()).thenReturn(Status.PASSED);

        driverManagerMock.when(DriverManager::shouldQuitAfterTest).thenReturn(true);

        CucumberHooks hooks = new CucumberHooks();
        hooks.beforeScenario(scenario);
        hooks.afterScenario(scenario);

        recordingManagerMock.verify(RecordingManager::stop, atLeastOnce());
        driverManagerMock.verify(DriverManager::quitDriver, times(1));
        screenshotManagerMock.verify(() -> ScreenshotManager.capture(anyString()), never());
        assertNull(TestFlyContext.getCurrentTestId());
    }
}
