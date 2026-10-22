package io.testfly.mobile.internal;

import io.testfly.mobile.MobileTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * JVM-wide exclusive device reservation (ADR 0002 D7). A {@code ThreadLocal} only isolates
 * handles; this registry makes sure one physical device serves one session at a time. Other
 * sessions that select the same device wait, so access is serialised rather than interleaved.
 * Separate JVMs sharing a device are not coordinated.
 */
public final class DeviceReservations {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceReservations.class);
    private static final Map<String, Semaphore> LOCKS = new ConcurrentHashMap<>();

    private DeviceReservations() {
    }

    /** A held reservation; closing it releases the device (idempotent). */
    public static final class Reservation implements AutoCloseable {
        private final String deviceId;
        private final Semaphore lock;
        private boolean released;

        private Reservation(String deviceId, Semaphore lock) {
            this.deviceId = deviceId;
            this.lock = lock;
        }

        public String deviceId() {
            return deviceId;
        }

        @Override
        public synchronized void close() {
            if (!released) {
                released = true;
                lock.release();
            }
        }
    }

    public static Reservation acquire(String deviceId, Duration wait) {
        Semaphore lock = LOCKS.computeIfAbsent(deviceId, id -> new Semaphore(1, true));
        if (lock.tryAcquire()) {
            return new Reservation(deviceId, lock);
        }
        LOGGER.warn("Device {} is in use by another session in this JVM; waiting up to {} ms. "
                + "Parallel workers share one device serially until a device pool is configured.", deviceId, wait.toMillis());
        try {
            if (lock.tryAcquire(Math.max(0, wait.toMillis()), TimeUnit.MILLISECONDS)) {
                return new Reservation(deviceId, lock);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        throw new MobileTimeoutException("Device " + deviceId + " stayed in use by another session for " + wait.toMillis()
                + " ms (mobile.timeouts.reserveMs). Run fewer parallel mobile workers or close the other session.");
    }
}
