package io.testfly.unit.cucumber;

import io.testfly.cucumber.CucumberRetryContext;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Unit tests for {@link CucumberRetryContext}.
 * Thread-safe for parallel=methods via singleThreaded.
 */
@Test(singleThreaded = true)
public class CucumberRetryContextTest {

    @AfterMethod
    public void tearDown() {
        CucumberRetryContext.clear();
    }

    // ── Scenario retry → state correctly reset ────────────────────────────────

    @Test
    public void set_retryValue_getReturnsCorrectValue() {
        CucumberRetryContext.set(3);
        assertEquals(CucumberRetryContext.get(), 3);
    }

    @Test
    public void clear_removesRetryValue_fallsBackToDefault() {
        CucumberRetryContext.set(5);
        assertEquals(CucumberRetryContext.get(), 5);

        CucumberRetryContext.clear();
        assertEquals(CucumberRetryContext.get(), -1,
                "After clear, get() should return -1 (no override)");
    }

    @Test
    public void set_overridesPreviousValue() {
        CucumberRetryContext.set(2);
        assertEquals(CucumberRetryContext.get(), 2);

        CucumberRetryContext.set(7);
        assertEquals(CucumberRetryContext.get(), 7,
                "set() should overwrite previous value for the same thread");
    }

    // ── No retry → default behavior ──────────────────────────────────────────

    @Test
    public void get_noValueSet_returnsNegativeOne() {
        assertEquals(CucumberRetryContext.get(), -1,
                "Default return when no override set should be -1");
    }

    @Test
    public void set_zeroRetries_getReturnsZero() {
        CucumberRetryContext.set(0);
        assertEquals(CucumberRetryContext.get(), 0,
                "Setting 0 retries should be retrievable (means no retries)");
    }

    // ── Multiple retries → counter increments correctly ───────────────────────

    @Test
    public void multipleSetCalls_lastWins() {
        for (int i = 1; i <= 5; i++) {
            CucumberRetryContext.set(i);
            assertEquals(CucumberRetryContext.get(), i,
                    "Each set() should update the value for this thread");
        }
    }

    @Test
    public void setThenClearThenSet_worksCorrectly() {
        CucumberRetryContext.set(3);
        assertEquals(CucumberRetryContext.get(), 3);

        CucumberRetryContext.clear();
        assertEquals(CucumberRetryContext.get(), -1);

        CucumberRetryContext.set(10);
        assertEquals(CucumberRetryContext.get(), 10,
                "After clear, a new set() should work correctly");
    }

    @Test
    public void clear_isIdempotent() {
        // Clearing when nothing is set should not throw
        CucumberRetryContext.clear();
        CucumberRetryContext.clear();
        assertEquals(CucumberRetryContext.get(), -1);
    }
}
