package io.testfly.mobile.internal.session;

import io.testfly.mobile.internal.rpc.Deadline;
import io.testfly.mobile.internal.rpc.JsonRpcOptions;
import org.testng.annotations.Test;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.testfly.mobile.internal.session.DeviceReservations.Failure.*;
import static org.testng.Assert.*;

public class DeviceReservationsTest {
    private static final Duration BUDGET = Duration.ofSeconds(3);

    private static JsonRpcOptions server() {
        return JsonRpcOptions.defaults(URI.create("http://127.0.0.1:12000/" + UUID.randomUUID()));
    }

    @Test
    public void exclusiveOwnerBlocksAnotherThreadUntilRelease() throws Exception {
        JsonRpcOptions server = server();
        try (var workers = Executors.newSingleThreadExecutor()) {
            CountDownLatch attempting = new CountDownLatch(1);
            var owner = DeviceReservations.acquire(server, "emulator", BUDGET);
            try {
                var next = workers.submit(() -> {
                    attempting.countDown();
                    try (var ignored = DeviceReservations.acquire(server, "emulator", BUDGET)) {
                        return true;
                    }
                });
                assertTrue(attempting.await(1, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> next.get(50, TimeUnit.MILLISECONDS));
                owner.close();
                assertTrue(next.get(2, TimeUnit.SECONDS));
            } finally {
                owner.close();
            }
        }
    }

    @Test
    public void waiterDeadlineDoesNotReleaseExistingOwner() throws Exception {
        JsonRpcOptions server = server();
        try (var workers = Executors.newSingleThreadExecutor();
             var owner = DeviceReservations.acquire(server, "emulator", BUDGET)) {
            var failure = workers.submit(() -> expectFailure(TIMEOUT,
                    () -> DeviceReservations.acquire(server, "emulator", Duration.ofMillis(30))));
            failure.get(2, TimeUnit.SECONDS);
            expectFailure(NESTED, () -> DeviceReservations.acquire(server, "emulator", BUDGET));
        }
        try (var ignored = DeviceReservations.acquire(server, "emulator", BUDGET)) {
            // A timed-out waiter left no ownership behind.
        }
    }

    @Test
    public void interruptionStopsWaitingAndPreservesInterruptStatus() throws Exception {
        JsonRpcOptions server = server();
        CountDownLatch attempting = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        try (var owner = DeviceReservations.acquire(server, "emulator", BUDGET)) {
            Thread waiter = new Thread(() -> {
                attempting.countDown();
                try {
                    expectFailure(INTERRUPTED, () -> DeviceReservations.acquire(server, "emulator", BUDGET));
                    interrupted.set(Thread.currentThread().isInterrupted());
                } catch (Throwable e) {
                    error.set(e);
                }
            });
            waiter.start();
            try {
                assertTrue(attempting.await(1, TimeUnit.SECONDS));
                waiter.interrupt();
                waiter.join(2_000);
                assertFalse(waiter.isAlive());
                assertNull(error.get());
                assertTrue(interrupted.get());
            } finally {
                waiter.interrupt();
                waiter.join(2_000);
            }
        }
    }

    @Test
    public void endpointAndDeviceBothParticipateInIdentity() {
        JsonRpcOptions firstServer = server();
        JsonRpcOptions secondServer = server();
        try (var first = DeviceReservations.acquire(firstServer, "one", BUDGET);
             var otherDevice = DeviceReservations.acquire(firstServer, "two", BUDGET);
             var otherServer = DeviceReservations.acquire(secondServer, "one", BUDGET)) {
            expectFailure(NESTED, () -> DeviceReservations.acquire(firstServer, "one", BUDGET));
        }
    }

    @Test
    public void unknownMutationOutcomeSurvivesCloseUntilExplicitReconciliation() {
        JsonRpcOptions server = server();
        try {
            try (var owner = DeviceReservations.acquire(server, "emulator", BUDGET)) {
                owner.markUnavailable(DeviceReservations.UnavailableReason.MUTATION_OUTCOME_UNKNOWN);
                expectFailure(ACTIVE_RECOVERY,
                        () -> DeviceReservations.recoverAfterReconciliation(server, "emulator"));
            }
            var failure = expectFailure(UNAVAILABLE,
                    () -> DeviceReservations.acquire(server, "emulator", BUDGET));
            assertTrue(failure.getMessage().contains("reconcile"));
            DeviceReservations.recoverAfterReconciliation(server, "emulator");
            try (var ignored = DeviceReservations.acquire(server, "emulator", BUDGET)) {
                // Explicit reconciliation restored availability.
            }
        } finally {
            DeviceReservations.recoverAfterReconciliation(server, "emulator");
        }
    }

    @Test
    public void quarantineWakesWaitersWithoutHandingOutDevice() throws Exception {
        JsonRpcOptions server = server();
        try (var workers = Executors.newSingleThreadExecutor()) {
            var owner = DeviceReservations.acquire(server, "emulator", BUDGET);
            try {
                CountDownLatch attempting = new CountDownLatch(1);
                var waiting = workers.submit(() -> {
                    attempting.countDown();
                    expectFailure(UNAVAILABLE, () -> DeviceReservations.acquire(server, "emulator", BUDGET));
                });
                assertTrue(attempting.await(1, TimeUnit.SECONDS));
                owner.markUnavailable(DeviceReservations.UnavailableReason.CLEANUP_FAILED);
                waiting.get(2, TimeUnit.SECONDS);
            } finally {
                owner.close();
                DeviceReservations.recoverAfterReconciliation(server, "emulator");
            }
        }
    }

    @Test
    public void crossThreadCloseAndQuarantineCannotAffectOwner() throws Exception {
        JsonRpcOptions server = server();
        try (var workers = Executors.newSingleThreadExecutor();
             var owner = DeviceReservations.acquire(server, "emulator", BUDGET)) {
            workers.submit(() -> {
                expectFailure(CROSS_THREAD, owner::close);
                expectFailure(CROSS_THREAD,
                        () -> owner.markUnavailable(DeviceReservations.UnavailableReason.CLEANUP_FAILED));
            }).get(2, TimeUnit.SECONDS);
            expectFailure(NESTED, () -> DeviceReservations.acquire(server, "emulator", BUDGET));
        }
    }

    @Test
    public void closeIsIdempotentAndClosedLeaseCannotQuarantineNewOwner() {
        JsonRpcOptions server = server();
        var old = DeviceReservations.acquire(server, "emulator", BUDGET);
        old.close();
        try (var current = DeviceReservations.acquire(server, "emulator", BUDGET)) {
            old.close();
            expectFailure(CLOSED,
                    () -> old.markUnavailable(DeviceReservations.UnavailableReason.CLEANUP_FAILED));
            expectFailure(NESTED, () -> DeviceReservations.acquire(server, "emulator", BUDGET));
        }
    }

    @Test
    public void expiredConnectionBudgetNeverAllocates() {
        JsonRpcOptions server = server();
        Deadline deadline = Deadline.after(Duration.ofNanos(1));
        expectFailure(TIMEOUT, () -> DeviceReservations.acquire(server, "emulator", deadline));
        try (var ignored = DeviceReservations.acquire(server, "emulator", BUDGET)) {
            // An expired connection left nothing allocated.
        }
    }

    @Test
    public void invalidDeviceOrBudgetDoesNotCreateReservation() {
        JsonRpcOptions server = server();
        assertThrows(IllegalArgumentException.class, () -> DeviceReservations.acquire(server, " ", BUDGET));
        assertThrows(IllegalArgumentException.class,
                () -> DeviceReservations.acquire(server, "emulator", Duration.ofSeconds(-1)));
        assertThrows(NullPointerException.class, () -> DeviceReservations.acquire(null, "emulator", BUDGET));
    }

    private static DeviceReservations.ReservationException expectFailure(
            DeviceReservations.Failure expected, Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected reservation failure " + expected);
        } catch (DeviceReservations.ReservationException e) {
            assertEquals(e.failure(), expected);
            return e;
        }
    }
}
