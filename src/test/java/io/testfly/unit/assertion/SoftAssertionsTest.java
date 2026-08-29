package io.testfly.unit.assertion;

import io.testfly.assertion.SoftAssertionCollector;
import io.testfly.assertion.SoftAssertions;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link SoftAssertions} and {@link SoftAssertionCollector}.
 * Verifies failure collection, ordering, thread safety, and lifecycle.
 */
public class SoftAssertionsTest {

    @BeforeMethod
    public void setUp() {
        SoftAssertions.clear();
    }

    @AfterMethod
    public void tearDown() {
        SoftAssertions.clear();
    }

    // ---------------------------------------------------------------
    // SoftAssertionCollector — failure collection
    // ---------------------------------------------------------------

    @Test
    public void multipleFailures_allCollected() {
        SoftAssertionCollector collector = SoftAssertions.get();
        collector.that(false, "failure 1");
        collector.that(false, "failure 2");
        collector.that(false, "failure 3");

        assertTrue(collector.hasFailed());
        assertEquals(collector.getFailures().size(), 3);
    }

    @Test
    public void failureOrder_preserved() {
        SoftAssertionCollector collector = SoftAssertions.get();
        collector.that(false, "first");
        collector.that(false, "second");
        collector.that(false, "third");

        List<String> failures = collector.getFailures();
        assertEquals(failures.get(0), "first");
        assertEquals(failures.get(1), "second");
        assertEquals(failures.get(2), "third");
    }

    @Test
    public void noFailures_noError() {
        SoftAssertionCollector collector = SoftAssertions.get();
        assertFalse(collector.hasFailed());
        assertTrue(collector.getFailures().isEmpty());
    }

    @Test
    public void passingAssertions_noFailuresRecorded() {
        SoftAssertionCollector collector = SoftAssertions.get();
        collector.that(true, "should pass");
        collector.that(true, "should also pass");
        collector.that(true, "and this too");

        assertFalse(collector.hasFailed());
        assertTrue(collector.getFailures().isEmpty());
    }

    @Test
    public void mixedPassAndFail_onlyFailuresReported() {
        SoftAssertionCollector collector = SoftAssertions.get();
        collector.that(true, "pass 1");
        collector.that(false, "fail 1");
        collector.that(true, "pass 2");
        collector.that(false, "fail 2");
        collector.that(true, "pass 3");

        assertEquals(collector.getFailures().size(), 2);
        assertEquals(collector.getFailures().get(0), "fail 1");
        assertEquals(collector.getFailures().get(1), "fail 2");
    }

    // ---------------------------------------------------------------
    // SoftAssertionCollector — chaining
    // ---------------------------------------------------------------

    @Test
    public void chaining_returnsSameCollector() {
        SoftAssertionCollector collector = SoftAssertions.get();
        SoftAssertionCollector returned = collector.that(true, "msg");
        assertTrue(returned == collector, "that() should return 'this' for chaining");
    }

    // ---------------------------------------------------------------
    // SoftAssertionCollector — lifecycle
    // ---------------------------------------------------------------

    @Test
    public void clear_removesAllFailures() {
        SoftAssertionCollector collector = SoftAssertions.get();
        collector.that(false, "failure 1");
        collector.that(false, "failure 2");
        assertTrue(collector.hasFailed());

        collector.clear();
        assertFalse(collector.hasFailed());
        assertTrue(collector.getFailures().isEmpty());
    }

    @Test
    public void clear_thenNewFailures_onlyNewOnesReported() {
        SoftAssertionCollector collector = SoftAssertions.get();
        collector.that(false, "old failure");
        collector.clear();

        collector.that(false, "new failure");
        assertEquals(collector.getFailures().size(), 1);
        assertEquals(collector.getFailures().get(0), "new failure");
    }

    @Test
    public void getFailures_returnsUnmodifiableList() {
        SoftAssertionCollector collector = SoftAssertions.get();
        collector.that(false, "failure");

        List<String> failures = collector.getFailures();
        try {
            failures.add("should fail");
            // If no exception, the list is not unmodifiable — that's a bug
            assertTrue(false, "getFailures() should return an unmodifiable list");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    // ---------------------------------------------------------------
    // SoftAssertions — ThreadLocal isolation
    // ---------------------------------------------------------------

    @Test
    public void get_returnsSameInstanceForSameThread() {
        SoftAssertionCollector a = SoftAssertions.get();
        SoftAssertionCollector b = SoftAssertions.get();
        assertNotNull(a);
        assertNotNull(b);
        assertTrue(a == b, "get() should return the same collector for the same thread");
    }

    @Test
    public void threadSafety_parallelAssertionsDontInterfere() throws Exception {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<SoftAssertionCollector> ref1 = new AtomicReference<>();
        AtomicReference<SoftAssertionCollector> ref2 = new AtomicReference<>();

        Thread t1 = new Thread(() -> {
            try { startLatch.await(); } catch (InterruptedException e) { return; }
            SoftAssertionCollector c = SoftAssertions.get();
            c.that(false, "thread1-fail");
            ref1.set(c);
            doneLatch.countDown();
        });

        Thread t2 = new Thread(() -> {
            try { startLatch.await(); } catch (InterruptedException e) { return; }
            SoftAssertionCollector c = SoftAssertions.get();
            c.that(false, "thread2-fail-A");
            c.that(false, "thread2-fail-B");
            ref2.set(c);
            doneLatch.countDown();
        });

        t1.start();
        t2.start();
        startLatch.countDown(); // release both threads simultaneously

        assertTrue(doneLatch.await(5, TimeUnit.SECONDS), "Threads should complete within 5s");

        // Verify isolation: thread1 has 1 failure, thread2 has 2
        assertEquals(ref1.get().getFailures().size(), 1, "Thread 1 should have exactly 1 failure");
        assertEquals(ref1.get().getFailures().get(0), "thread1-fail");

        assertEquals(ref2.get().getFailures().size(), 2, "Thread 2 should have exactly 2 failures");
        assertEquals(ref2.get().getFailures().get(0), "thread2-fail-A");
        assertEquals(ref2.get().getFailures().get(1), "thread2-fail-B");

        // Verify collectors are distinct instances
        assertTrue(ref1.get() != ref2.get(),
                "Each thread should get its own SoftAssertionCollector instance");

        t1.join();
        t2.join();
    }

    @Test
    public void clear_onlyAffectsCurrentThread() throws Exception {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(1);

        AtomicReference<SoftAssertionCollector> ref = new AtomicReference<>();

        Thread otherThread = new Thread(() -> {
            try { startLatch.await(); } catch (InterruptedException e) { return; }
            SoftAssertionCollector c = SoftAssertions.get();
            c.that(false, "other-thread-fail");
            ref.set(c);
            doneLatch.countDown();
        });

        otherThread.start();
        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));

        // Main thread clears its own collector
        SoftAssertions.get().that(false, "main-thread-fail");
        SoftAssertions.clear();

        // Main thread collector should be cleared
        assertFalse(SoftAssertions.get().hasFailed(),
                "Main thread collector should be empty after clear()");

        // Other thread's collector should be unaffected
        assertTrue(ref.get().hasFailed(),
                "Other thread's collector should still have its failure");
        assertEquals(ref.get().getFailures().get(0), "other-thread-fail");

        otherThread.join();
    }
}
