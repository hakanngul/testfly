package io.testfly.mobile.internal.rpc;

import org.testng.annotations.Test;

import java.time.Duration;

import static org.testng.Assert.*;

public class DeadlineTest {
    @Test
    public void remainingBudgetDecreasesAndExpiresAtZero() {
        Deadline deadline = Deadline.after(Duration.ofMillis(1));
        long first = deadline.remainingNanos();
        assertTrue(first >= 0);
        assertTrue(first <= Duration.ofMillis(1).toNanos());
        while (!deadline.expired()) {
            Thread.onSpinWait();
        }
        assertEquals(deadline.remainingNanos(), 0L);
        assertEquals(deadline.remaining(), Duration.ZERO);
    }

    @Test
    public void invalidBudgetsAreRejected() {
        expectThrows(IllegalArgumentException.class, () -> Deadline.after(Duration.ZERO));
        expectThrows(IllegalArgumentException.class, () -> Deadline.after(Duration.ofNanos(-1)));
        expectThrows(IllegalArgumentException.class, () -> Deadline.after(Duration.ofSeconds(Long.MAX_VALUE)));
    }
}
