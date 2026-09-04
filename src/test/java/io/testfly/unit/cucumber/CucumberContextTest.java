package io.testfly.unit.cucumber;

import io.cucumber.java.Scenario;
import io.testfly.cucumber.CucumberContext;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.mock;
import static org.testng.Assert.*;

/**
 * Unit tests for {@link CucumberContext}.
 * Thread-safe for parallel=methods via singleThreaded.
 */
@Test(singleThreaded = true)
public class CucumberContextTest {

    @AfterMethod
    public void tearDown() {
        CucumberContext.clear();
    }

    // ── Scenario-scoped state isolation ──────────────────────────────────────

    @Test
    public void setScenario_getScenario_returnsSameInstance() {
        Scenario scenario = mock(Scenario.class);
        CucumberContext.setScenario(scenario);

        assertSame(CucumberContext.getScenario(), scenario);
    }

    @Test
    public void getScenario_noScenarioSet_returnsNull() {
        assertNull(CucumberContext.getScenario(),
                "Should return null when no scenario has been set on this thread");
    }

    // ── State set in one scenario not visible in another ─────────────────────

    @Test
    public void clear_removesScenarioFromThread() {
        Scenario scenario = mock(Scenario.class);
        CucumberContext.setScenario(scenario);
        assertNotNull(CucumberContext.getScenario());

        CucumberContext.clear();
        assertNull(CucumberContext.getScenario(),
                "After clear, getScenario should return null");
    }

    @Test
    public void setScenario_overridesPreviousScenario() {
        Scenario first = mock(Scenario.class);
        Scenario second = mock(Scenario.class);

        CucumberContext.setScenario(first);
        assertSame(CucumberContext.getScenario(), first);

        CucumberContext.setScenario(second);
        assertSame(CucumberContext.getScenario(), second,
                "Setting a new scenario should replace the previous one");
    }

    @Test
    public void scenarioOnDifferentThread_isIsolated() throws Exception {
        Scenario mainScenario = mock(Scenario.class);
        CucumberContext.setScenario(mainScenario);

        AtomicReference<Scenario> otherThreadScenario = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread otherThread = new Thread(() -> {
            otherThreadScenario.set(CucumberContext.getScenario());
            latch.countDown();
        });
        otherThread.start();
        latch.await();

        assertNull(otherThreadScenario.get(),
                "Scenario set on main thread should not be visible on another thread");
        assertSame(CucumberContext.getScenario(), mainScenario,
                "Main thread scenario should be unchanged");
    }

    // ── Context reset between scenarios ──────────────────────────────────────

    @Test
    public void setClearSet_simulatesScenarioTransition() {
        // First scenario
        Scenario first = mock(Scenario.class);
        CucumberContext.setScenario(first);
        assertSame(CucumberContext.getScenario(), first);

        // Scenario ends — clear
        CucumberContext.clear();
        assertNull(CucumberContext.getScenario());

        // Second scenario starts
        Scenario second = mock(Scenario.class);
        CucumberContext.setScenario(second);
        assertSame(CucumberContext.getScenario(), second,
                "After clear, a new scenario should be set correctly");
    }

    @Test
    public void clear_isIdempotent() {
        // Clearing when nothing is set should not throw
        CucumberContext.clear();
        CucumberContext.clear();
        assertNull(CucumberContext.getScenario());
    }
}
