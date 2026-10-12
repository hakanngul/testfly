package io.testfly.unit.tracing;

import io.testfly.ai.DomPruner;
import io.testfly.config.TestFlyConfig;
import io.testfly.driver.DriverManager;
import io.testfly.internal.TestFlyContext;
import io.testfly.metrics.TestTiming;
import io.testfly.steps.StepRecord;
import io.testfly.tracing.StepSnapshotCapture;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.testng.Assert.*;

/** Unit tests for {@link StepSnapshotCapture}. The browser is mocked: the probe answers canned JSON. */
@Test(singleThreaded = true)
public class StepSnapshotCaptureTest {

    private static final String TREE = "{\"truncated\":false,\"nodes\":[{\"role\":\"heading\",\"name\":\"Welcome\","
            + "\"attrs\":{\"level\":1},\"children\":[]}]}";

    private MockedStatic<TestFlyContext> contextMock;
    private MockedStatic<DriverManager> driverManagerMock;
    private TestFlyConfig config;
    private TestFlyConfig.Tracing tracing;

    @BeforeMethod
    public void setUp() {
        tracing = new TestFlyConfig.Tracing();
        tracing.setEnabled(true);
        config = new TestFlyConfig();
        config.setTracing(tracing);
        contextMock = mockStatic(TestFlyContext.class);
        contextMock.when(TestFlyContext::getConfig).thenReturn(config);
        driverManagerMock = mockStatic(DriverManager.class);
    }

    @AfterMethod
    public void tearDown() {
        contextMock.close();
        driverManagerMock.close();
    }

    private WebDriver liveDriver(Object scriptResult) {
        WebDriver d = mock(WebDriver.class, Mockito.withSettings()
                .extraInterfaces(JavascriptExecutor.class)
                .defaultAnswer(inv -> {
                    if (inv.getMethod().getName().equals("executeScript")) {
                        if (scriptResult instanceof RuntimeException e) {
                            throw e;
                        }
                        return scriptResult;
                    }
                    return Mockito.RETURNS_DEFAULTS.answer(inv);
                }));
        driverManagerMock.when(DriverManager::isDriverAlive).thenReturn(true);
        driverManagerMock.when(DriverManager::getDriver).thenReturn(d);
        return d;
    }

    @Test
    public void tracingDisabled_capturesNothing_andNeverTouchesTheDriver() {
        tracing.setEnabled(false);
        liveDriver(TREE);

        StepSnapshotCapture.Snapshots s = StepSnapshotCapture.captureForStep();

        assertNull(s.aria());
        assertNull(s.dom());
        driverManagerMock.verify(DriverManager::isDriverAlive, never());
    }

    @Test
    public void noConfigAtAll_capturesNothing() {
        contextMock.when(TestFlyContext::getConfig).thenReturn(null);
        assertNull(StepSnapshotCapture.captureForStep().aria());
    }

    @Test
    public void modeNone_capturesNothing() {
        tracing.setSnapshots("none");
        liveDriver(TREE);
        assertNull(StepSnapshotCapture.captureForStep().aria());
    }

    @Test
    public void defaultMode_capturesTheAriaTreeAsYaml() {
        liveDriver(TREE);

        StepSnapshotCapture.Snapshots s = StepSnapshotCapture.captureForStep();

        assertEquals(s.aria(), "- heading \"Welcome\" [level=1]\n");
        assertNull(s.dom(), "dom is only captured in dom or both mode");
    }

    @Test
    public void noLiveDriver_capturesNothing_andNeverCreatesOne() {
        driverManagerMock.when(DriverManager::isDriverAlive).thenReturn(false);

        StepSnapshotCapture.Snapshots s = StepSnapshotCapture.captureForStep();

        assertNull(s.aria());
        driverManagerMock.verify(DriverManager::getDriver, never());
    }

    @Test
    public void probeFailure_isSwallowed() {
        liveDriver(new org.openqa.selenium.JavascriptException("page is navigating"));
        assertNull(StepSnapshotCapture.captureForStep().aria());
    }

    @Test
    public void domMode_capturesPrunedHtml_only() {
        tracing.setSnapshots("dom");
        WebDriver d = liveDriver(TREE);
        try (MockedStatic<DomPruner> pruner = mockStatic(DomPruner.class)) {
            pruner.when(() -> DomPruner.prune(d)).thenReturn("<main>hello</main>");

            StepSnapshotCapture.Snapshots s = StepSnapshotCapture.captureForStep();

            assertEquals(s.dom(), "<main>hello</main>");
            assertNull(s.aria());
        }
    }

    @Test
    public void bothMode_capturesBoth() {
        tracing.setSnapshots("both");
        WebDriver d = liveDriver(TREE);
        try (MockedStatic<DomPruner> pruner = mockStatic(DomPruner.class)) {
            pruner.when(() -> DomPruner.prune(d)).thenReturn("<main>hello</main>");

            StepSnapshotCapture.Snapshots s = StepSnapshotCapture.captureForStep();

            assertNotNull(s.aria());
            assertNotNull(s.dom());
        }
    }

    @Test
    public void largeSnapshot_isCutWithAMarker() {
        tracing.setSnapshotMaxKb(1);
        StringBuilder nodes = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            nodes.append(i > 0 ? "," : "").append("{\"role\":\"link\",\"name\":\"Link number ").append(i)
                    .append("\",\"attrs\":{},\"children\":[]}");
        }
        liveDriver("{\"truncated\":false,\"nodes\":[" + nodes + "]}");

        String aria = StepSnapshotCapture.captureForStep().aria();

        assertTrue(aria.contains("… truncated ("), aria);
        assertTrue(aria.length() < 1024 + 100, "kept about 1 KB, got " + aria.length());
    }

    // ---------------------------------------------------------------- after a failure

    private TestTiming timingWithSteps(int count) {
        TestTiming t = new TestTiming("T#t", "main");
        for (int i = 0; i < count; i++) {
            t.addStep(new StepRecord("step " + i, i, "INFO", null, "- button \"b" + i + "\"\n", null));
        }
        return t;
    }

    @Test
    public void afterFailure_recordsASnapshotOnTheLastStepOnly() {
        liveDriver(TREE);
        TestTiming t = timingWithSteps(3);

        StepSnapshotCapture.captureAfterFailure(t);

        assertNull(t.getSteps().get(0).getAfterAriaSnapshot());
        assertNull(t.getSteps().get(1).getAfterAriaSnapshot());
        assertEquals(t.getSteps().get(2).getAfterAriaSnapshot(), "- heading \"Welcome\" [level=1]\n");
    }

    @Test
    public void afterFailure_canBeSwitchedOff() {
        tracing.setSnapshotOnFailureAfter(false);
        liveDriver(TREE);
        TestTiming t = timingWithSteps(2);

        StepSnapshotCapture.captureAfterFailure(t);

        assertNull(t.getSteps().get(1).getAfterAriaSnapshot());
    }

    @Test
    public void afterFailure_withoutSteps_orWithoutDriver_isANoOp() {
        driverManagerMock.when(DriverManager::isDriverAlive).thenReturn(false);
        TestTiming t = timingWithSteps(2);

        StepSnapshotCapture.captureAfterFailure(t);
        StepSnapshotCapture.captureAfterFailure(timingWithSteps(0));
        StepSnapshotCapture.captureAfterFailure((TestTiming) null);

        assertNull(t.getSteps().get(1).getAfterAriaSnapshot());
    }

    @Test
    public void afterFailure_isSkippedInDomOnlyMode() {
        tracing.setSnapshots("dom");
        liveDriver(TREE);
        TestTiming t = timingWithSteps(1);

        StepSnapshotCapture.captureAfterFailure(t);

        assertNull(t.getSteps().get(0).getAfterAriaSnapshot());
    }
}
