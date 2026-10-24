package io.testfly.mobile.internal;

import io.testfly.mobile.MobileTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * JVM-wide exclusive device reservation (ADR 0002 D7). A {@code ThreadLocal} only isolates
 * handles; this registry makes sure one physical device serves one session at a time. A session
 * asks for any free device of a candidate list (a pool, or a single device); when all are held it
 * waits until one is released, so access is serialised rather than interleaved. Separate JVMs
 * sharing a device are not coordinated.
 */
public final class DeviceReservations {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceReservations.class);
    private static final ReentrantLock LOCK = new ReentrantLock(true);
    private static final Condition RELEASED = LOCK.newCondition();
    private static final Set<String> HELD = new HashSet<>();

    private DeviceReservations() {
    }

    /** A held reservation; closing it releases the device (idempotent). */
    public static final class Reservation implements AutoCloseable {
        private final String deviceId;
        private boolean released;

        private Reservation(String deviceId) {
            this.deviceId = deviceId;
        }

        public String deviceId() {
            return deviceId;
        }

        @Override
        public void close() {
            LOCK.lock();
            try {
                if (!released) {
                    released = true;
                    HELD.remove(deviceId);
                    RELEASED.signalAll();
                }
            } finally {
                LOCK.unlock();
            }
        }
    }

    public static Reservation acquire(String deviceId, Duration wait) {
        return acquireAny(List.of(deviceId), wait);
    }

    /**
     * Reserves the first free device of {@code candidates} (in their order), waiting up to
     * {@code wait} for one to be released.
     */
    public static Reservation acquireAny(List<String> candidates, Duration wait) {
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("no candidate devices");
        }
        long deadline = System.nanoTime() + Math.max(0, wait.toNanos());
        boolean warned = false;
        LOCK.lock();
        try {
            while (true) {
                for (String id : candidates) {
                    if (HELD.add(id)) {
                        return new Reservation(id);
                    }
                }
                long left = deadline - System.nanoTime();
                if (left <= 0) {
                    throw busy(candidates, wait);
                }
                if (!warned) {
                    warned = true;
                    LOGGER.warn("{} in use by other sessions in this JVM; waiting up to {} ms.", describe(candidates), wait.toMillis());
                }
                RELEASED.awaitNanos(left);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MobileTimeoutException("Interrupted while waiting for " + describe(candidates));
        } finally {
            LOCK.unlock();
        }
    }

    private static MobileTimeoutException busy(List<String> candidates, Duration wait) {
        String advice = candidates.size() == 1
                ? "Run fewer parallel mobile workers, add devices to mobile.devices, or close the other session."
                : "Run at most " + candidates.size() + " parallel mobile workers or add devices to the pool.";
        return new MobileTimeoutException(describe(candidates) + " stayed in use for " + wait.toMillis()
                + " ms (mobile.timeouts.reserveMs). " + advice);
    }

    private static String describe(List<String> candidates) {
        return candidates.size() == 1 ? "Device " + candidates.get(0)
                : "All " + candidates.size() + " pool devices (" + String.join(", ", candidates) + ")";
    }
}
