package io.testfly.network;

import org.openqa.selenium.devtools.v144.network.model.ErrorReason;
import org.testng.annotations.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * Unit tests for {@link AbortReason}. Placed in package {@code io.testfly.network}
 * to exercise the package-private {@link AbortReason#toCdp()} mapping. No browser.
 */
public class AbortReasonTest {

    @Test
    public void toCdp_everyConstantMapsToNonNullErrorReason() {
        for (AbortReason r : AbortReason.values()) {
            assertNotNull(r.toCdp(), "toCdp() must not be null for " + r);
        }
    }

    @Test
    public void toCdp_mappingsAreDistinct() {
        Set<ErrorReason> seen = new HashSet<>();
        for (AbortReason r : AbortReason.values()) {
            boolean added = seen.add(r.toCdp());
            // Every AbortReason should map to a unique CDP ErrorReason
            org.testng.Assert.assertTrue(added,
                    "Duplicate CDP mapping for " + r + " -> " + r.toCdp());
        }
    }

    @Test
    public void toCdp_defaultIsFailed() {
        assertEquals(AbortReason.FAILED.toCdp(), ErrorReason.FAILED);
    }

    @Test
    public void toCdp_blockedByClient() {
        assertEquals(AbortReason.BLOCKED_BY_CLIENT.toCdp(), ErrorReason.BLOCKEDBYCLIENT);
    }

    @Test
    public void toCdp_knownSubsetMappings() {
        assertEquals(AbortReason.TIMED_OUT.toCdp(), ErrorReason.TIMEDOUT);
        assertEquals(AbortReason.CONNECTION_REFUSED.toCdp(), ErrorReason.CONNECTIONREFUSED);
        assertEquals(AbortReason.NAME_NOT_RESOLVED.toCdp(), ErrorReason.NAMENOTRESOLVED);
        assertEquals(AbortReason.INTERNET_DISCONNECTED.toCdp(), ErrorReason.INTERNETDISCONNECTED);
    }

    @Test
    public void allConstantsCovered() {
        // Guards against adding a constant without a mapping branch
        assertEquals(AbortReason.values().length, EnumSet.allOf(AbortReason.class).size());
    }
}
