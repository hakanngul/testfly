package io.testfly.mobile.internal.session;

import io.testfly.mobile.internal.rpc.Deadline;
import io.testfly.mobile.internal.rpc.JsonRpcOptions;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * JVM-local exclusive ownership of a device before any state-changing operation.
 * Endpoint aliases and other processes require external coordination. This registry
 * makes no remote calls and does not establish that a device is online.
 */
public final class DeviceReservations {
    private static final Object MONITOR = new Object();
    private static final Map<Key, Lease> ACTIVE = new HashMap<>();
    private static final Map<Key, UnavailableReason> UNAVAILABLE = new HashMap<>();

    private DeviceReservations() {}

    /** Acquire before install/reset/launch/input, with interruptible, bounded waiting. */
    public static Lease acquire(JsonRpcOptions server, String deviceId, Duration timeout) {
        return acquire(server, deviceId, Deadline.after(timeout));
    }

    /** Reuse the connection deadline so waiting does not start another full budget. */
    public static Lease acquire(JsonRpcOptions server, String deviceId, Deadline deadline) {
        Key key = key(server, deviceId);
        Objects.requireNonNull(deadline, "deadline");
        Thread requester = Thread.currentThread();
        synchronized (MONITOR) {
            while (true) {
                if (requester.isInterrupted()) {
                    throw failure(Failure.INTERRUPTED, "Device reservation interrupted");
                }
                UnavailableReason reason = UNAVAILABLE.get(key);
                if (reason != null) {
                    throw failure(Failure.UNAVAILABLE,
                            "Device unavailable after " + reason + "; reconcile the device before explicit recovery");
                }
                Lease owner = ACTIVE.get(key);
                if (owner != null && owner.owner == requester) {
                    throw failure(Failure.NESTED, "This thread already owns the selected device");
                }
                long remaining = deadline.remainingNanos();
                if (remaining <= 0) {
                    String suffix = owner == null ? "" : " (owner thread id " + owner.owner.threadId() + ")";
                    throw failure(Failure.TIMEOUT, "Device reservation deadline exhausted" + suffix);
                }
                if (owner == null) {
                    Lease lease = new Lease(key, requester);
                    ACTIVE.put(key, lease);
                    return lease;
                }
                try {
                    MONITOR.wait(remaining / 1_000_000L, (int) (remaining % 1_000_000L));
                } catch (InterruptedException e) {
                    requester.interrupt();
                    throw new ReservationException(Failure.INTERRUPTED, "Device reservation interrupted", e);
                }
            }
        }
    }

    /**
     * Clear a quarantine only after the caller has reconciled the remote device out
     * of band. Cancellation/closing a Java handle alone does not prove quiescence.
     * An active lease can never be reset by recovery.
     */
    public static void recoverAfterReconciliation(JsonRpcOptions server, String deviceId) {
        Key key = key(server, deviceId);
        synchronized (MONITOR) {
            if (ACTIVE.containsKey(key)) {
                throw failure(Failure.ACTIVE_RECOVERY, "Cannot recover a device while a session owns it");
            }
            UNAVAILABLE.remove(key);
            MONITOR.notifyAll();
        }
    }

    private static Key key(JsonRpcOptions server, String deviceId) {
        Objects.requireNonNull(server, "server");
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId must not be blank");
        }
        return new Key(server.normalizedServerUrl(), deviceId);
    }

    private static ReservationException failure(Failure failure, String message) {
        return new ReservationException(failure, message, null);
    }

    private record Key(URI server, String deviceId) {}

    public enum UnavailableReason { MUTATION_OUTCOME_UNKNOWN, CLEANUP_FAILED }
    public enum Failure { TIMEOUT, INTERRUPTED, UNAVAILABLE, NESTED, CROSS_THREAD, CLOSED, ACTIVE_RECOVERY }

    /** An internal failure category; diagnostics intentionally exclude input values/headers. */
    public static final class ReservationException extends RuntimeException {
        private final Failure failure;

        private ReservationException(Failure failure, String message, Throwable cause) {
            super(message, cause);
            this.failure = failure;
        }

        public Failure failure() { return failure; }
    }

    /** Thread-confined token. Closing releases ownership, but never clears quarantine. */
    public static final class Lease implements AutoCloseable {
        private final Key key;
        private final Thread owner;
        private boolean closed;

        private Lease(Key key, Thread owner) {
            this.key = key;
            this.owner = owner;
        }

        /** Mark unknown device state before releasing the session, even if cleanup then fails. */
        public void markUnavailable(UnavailableReason reason) {
            Objects.requireNonNull(reason, "reason");
            synchronized (MONITOR) {
                requireOwner();
                if (closed) {
                    throw failure(Failure.CLOSED, "Device reservation is already closed");
                }
                UNAVAILABLE.put(key, reason);
                MONITOR.notifyAll();
            }
        }

        @Override
        public void close() {
            synchronized (MONITOR) {
                requireOwner();
                if (closed) return;
                ACTIVE.remove(key, this);
                closed = true;
                MONITOR.notifyAll();
            }
        }

        private void requireOwner() {
            if (Thread.currentThread() != owner) {
                throw failure(Failure.CROSS_THREAD, "Device reservation belongs to another thread");
            }
        }
    }
}
