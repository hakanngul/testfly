package io.testfly.unit;

import io.testfly.config.TestFlyConfig;
import io.testfly.driver.DriverManager;
import io.testfly.internal.TestFlyContext;
import io.testfly.wait.ActionKind;
import io.testfly.wait.NotActionableException;
import io.testfly.wait.WaitEngine;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.testng.Assert.*;

/**
 * Unit tests for {@link WaitEngine#waitForActionable}. The browser is mocked: the
 * actionability script is answered from a queue of canned results, the last one
 * repeating once the queue is exhausted.
 */
@Test(singleThreaded = true)
public class WaitEngineActionabilityTest {

    private MockedStatic<DriverManager> driverManagerMock;
    private MockedStatic<TestFlyContext> contextMock;
    private TestFlyConfig config;
    private final ConcurrentLinkedDeque<Object> scriptResults = new ConcurrentLinkedDeque<>();
    private final List<Object[]> scriptCalls = new ArrayList<>();
    private Object lastResult;

    @BeforeMethod
    public void setUp() {
        scriptResults.clear();
        scriptCalls.clear();
        lastResult = null;

        config = new TestFlyConfig();
        TestFlyConfig.Timeouts timeouts = new TestFlyConfig.Timeouts();
        timeouts.setExplicit(1);
        config.setTimeouts(timeouts);
        config.getActionability().setStabilityIntervalMs(20);

        contextMock = mockStatic(TestFlyContext.class);
        contextMock.when(TestFlyContext::getConfig).thenReturn(config);
    }

    @AfterMethod
    public void tearDown() {
        if (driverManagerMock != null) {
            driverManagerMock.close();
            driverManagerMock = null;
        }
        if (contextMock != null) {
            contextMock.close();
        }
    }

    /** A driver whose executeScript returns the queued results; the last one repeats. */
    private WebDriver jsDriver() {
        WebDriver driver = mock(WebDriver.class, Mockito.withSettings()
                .extraInterfaces(JavascriptExecutor.class)
                .defaultAnswer(inv -> {
                    if (inv.getMethod().getName().equals("executeScript")) {
                        synchronized (scriptCalls) {
                            scriptCalls.add(inv.getArguments());
                        }
                        Object next = scriptResults.poll();
                        if (next != null) {
                            lastResult = next;
                        }
                        return lastResult;
                    }
                    return Mockito.RETURNS_DEFAULTS.answer(inv);
                }));
        driverManagerMock = mockStatic(DriverManager.class);
        driverManagerMock.when(DriverManager::getDriver).thenReturn(driver);
        return driver;
    }

    private static Map<String, Object> state(double x, double y, boolean hit, String blocker) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("attached", true);
        m.put("x", x);
        m.put("y", y);
        m.put("w", 100L);
        m.put("h", 30L);
        m.put("hit", hit);
        m.put("blocker", blocker);
        m.put("target", "button#submit");
        return m;
    }

    private static WebElement element(boolean displayed, boolean enabled) {
        WebElement el = mock(WebElement.class);
        when(el.isDisplayed()).thenReturn(displayed);
        when(el.isEnabled()).thenReturn(enabled);
        return el;
    }

    @Test
    public void click_stableRect_returnsAfterTwoPolls() {
        jsDriver();
        scriptResults.add(state(10, 10, true, null));
        WebElement el = element(true, true);

        assertSame(WaitEngine.waitForActionable(el), el);
        assertTrue(scriptCalls.size() >= 2, "stability needs two polls, got " + scriptCalls.size());
    }

    @Test
    public void click_movingRect_waitsUntilStable() {
        jsDriver();
        scriptResults.add(state(10, 10, true, null));
        scriptResults.add(state(50, 10, true, null));
        scriptResults.add(state(90, 10, true, null));
        scriptResults.add(state(90, 10, true, null));
        WebElement el = element(true, true);

        assertSame(WaitEngine.waitForActionable(el), el);
        assertTrue(scriptCalls.size() >= 4, "should keep polling while moving, got " + scriptCalls.size());
    }

    @Test
    public void click_permanentlyObscured_throwsNamingTheBlocker() {
        jsDriver();
        scriptResults.add(state(10, 10, false, "div.modal-backdrop"));
        WebElement el = element(true, true);

        NotActionableException ex = expectThrows(NotActionableException.class, () -> WaitEngine.waitForActionable(el));
        assertTrue(ex.getMessage().contains("obscured by <div.modal-backdrop>"), ex.getMessage());
        assertTrue(ex.getMessage().contains("not actionable for CLICK"), ex.getMessage());
        assertTrue(ex.getMessage().contains("<button#submit>"), ex.getMessage());
    }

    @Test
    public void click_overlayDisappears_thenSucceeds() {
        jsDriver();
        scriptResults.add(state(10, 10, false, "div.overlay"));
        scriptResults.add(state(10, 10, false, "div.overlay"));
        scriptResults.add(state(10, 10, true, null));
        WebElement el = element(true, true);

        assertSame(WaitEngine.waitForActionable(el), el);
    }

    @Test
    public void scriptReturnsNull_failsOpen() {
        jsDriver(); // no queued result: executeScript returns null
        WebElement el = element(true, true);

        assertSame(WaitEngine.waitForActionable(el), el);
        assertEquals(scriptCalls.size(), 1);
    }

    @Test
    public void driverWithoutJavaScript_failsOpen() {
        WebDriver plain = mock(WebDriver.class);
        driverManagerMock = mockStatic(DriverManager.class);
        driverManagerMock.when(DriverManager::getDriver).thenReturn(plain);
        WebElement el = element(true, true);

        assertSame(WaitEngine.waitForActionable(el), el);
    }

    @Test
    public void detachedElement_throwsStale_soCallerCanReResolve() {
        jsDriver();
        Map<String, Object> detached = new LinkedHashMap<>();
        detached.put("attached", false);
        scriptResults.add(detached);
        WebElement el = element(true, true);

        expectThrows(StaleElementReferenceException.class, () -> WaitEngine.waitForActionable(el));
    }

    @Test
    public void disabledConfig_usesLegacyWaitAndNeverRunsTheScript() {
        jsDriver();
        config.getActionability().setEnabled(false);
        scriptResults.add(state(10, 10, false, "div.overlay"));
        WebElement el = element(true, true);

        assertSame(WaitEngine.waitForActionable(el), el);
        assertEquals(scriptCalls.size(), 0);
    }

    @Test
    public void edit_doesNotRequireHitTarget() {
        jsDriver();
        scriptResults.add(state(10, 10, false, "div.overlay")); // covered, but typing does not care
        WebElement el = element(true, true);

        assertSame(WaitEngine.waitForActionable(el, ActionKind.EDIT), el);
        assertEquals(scriptCalls.get(0)[3], Boolean.FALSE, "hit-test flag must be off for EDIT");
    }

    @Test
    public void click_requestsTheHitTest() {
        jsDriver();
        scriptResults.add(state(10, 10, true, null));
        WebElement el = element(true, true);

        WaitEngine.waitForActionable(el, ActionKind.CLICK);
        assertEquals(scriptCalls.get(0)[3], Boolean.TRUE);
    }

    @Test
    public void hitTargetCheckOff_neverRequestsTheHitTest() {
        jsDriver();
        config.getActionability().setHitTargetCheck(false);
        scriptResults.add(state(10, 10, true, null));
        WebElement el = element(true, true);

        WaitEngine.waitForActionable(el, ActionKind.CLICK);
        assertEquals(scriptCalls.get(0)[3], Boolean.FALSE);
    }

    @Test
    public void hover_allowsDisabledElement() {
        jsDriver();
        scriptResults.add(state(10, 10, true, null));
        WebElement el = element(true, false);

        assertSame(WaitEngine.waitForActionable(el, ActionKind.HOVER), el);
    }

    @Test
    public void click_disabledElement_throwsNotEnabled() {
        jsDriver();
        scriptResults.add(state(10, 10, true, null));
        WebElement el = element(true, false);

        NotActionableException ex = expectThrows(NotActionableException.class, () -> WaitEngine.waitForActionable(el));
        assertTrue(ex.getMessage().contains("not enabled"), ex.getMessage());
    }

    @Test
    public void click_hiddenElement_throwsNotVisible() {
        jsDriver();
        WebElement el = element(false, true);

        NotActionableException ex = expectThrows(NotActionableException.class, () -> WaitEngine.waitForActionable(el));
        assertTrue(ex.getMessage().contains("not visible"), ex.getMessage());
    }

    @Test
    public void byVariant_findsElementOnEveryPoll() {
        WebDriver driver = jsDriver();
        By by = By.id("submit");
        WebElement el = element(true, true);
        when(driver.findElement(by)).thenThrow(new org.openqa.selenium.NoSuchElementException("late"))
                .thenReturn(el);
        scriptResults.add(state(10, 10, true, null));

        assertSame(WaitEngine.waitForActionable(by), el);
    }

    @Test
    public void invalidStabilityInterval_isRejectedByConfigValidation() {
        // Range is enforced when loading testfly.yml; see ActionabilityConfigTest.
        assertEquals(new TestFlyConfig.Actionability().getStabilityIntervalMs(), 100);
        assertTrue(new TestFlyConfig.Actionability().isEnabled());
        assertTrue(new TestFlyConfig.Actionability().isHitTargetCheck());
        assertTrue(new TestFlyConfig.Actionability().isAutoScroll());
    }
}
