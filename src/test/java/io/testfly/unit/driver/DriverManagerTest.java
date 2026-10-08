package io.testfly.unit.driver;

import io.testfly.browser.BrowserContext;
import io.testfly.config.TestFlyConfig;
import io.testfly.driver.DriverManager;
import io.testfly.driver.DriverProviderRegistry;
import io.testfly.internal.TestFlyContext;
import io.testfly.loadtest.LoadTest;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Lifecycle tests for {@link DriverManager} driven by a fake
 * {@link io.testfly.driver.NamedDriverProvider} that returns Mockito drivers.
 *
 * <p>{@code DriverManager} keeps its session semaphore in static state, so the
 * class is single-threaded and every test starts from
 * {@link DriverManager#resetForTesting()}.
 */
@Test(singleThreaded = true)
public class DriverManagerTest {

    private static final String BROWSER = "fake-driver-manager-test";
    /** Short enough to keep tests fast, long enough to avoid spurious failures. */
    private static final Duration SHORT_WAIT = Duration.ofMillis(300);

    private FakeDriverProvider provider;
    private ExecutorService executor;

    @BeforeMethod
    public void setUp() {
        BrowserContext.clear();
        provider = new FakeDriverProvider(BROWSER);
        DriverProviderRegistry.register(provider);
        configure(5, "per-test");
        executor = Executors.newCachedThreadPool();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        executor.shutdownNow();
        DriverManager.resetForTesting();
        TestFlyContext.clearCurrentTest();
        BrowserContext.clear();
        TestFlyContext.reset();
    }

    /** Installs a fresh config and discards any static semaphore built from an older one. */
    private TestFlyConfig.Execution configure(int maxActiveSessions, String lifecycle) {
        TestFlyConfig config = new TestFlyConfig();
        TestFlyConfig.Browser browser = new TestFlyConfig.Browser();
        browser.setName(BROWSER);
        browser.setLifecycle(lifecycle);
        TestFlyConfig.Execution execution = new TestFlyConfig.Execution();
        execution.setMode("local");
        execution.setMaxActiveSessions(maxActiveSessions);
        config.setBrowser(browser);
        config.setExecution(execution);
        TestFlyContext.setConfig(config);
        DriverManager.resetForTesting();
        return execution;
    }

    private <T> T onOtherThread(Callable<T> task) throws Exception {
        Future<T> future = executor.submit(task);
        return future.get(5, TimeUnit.SECONDS);
    }

    // ── createDriver ─────────────────────────────────────────────────────────

    @Test
    public void createDriver_bindsProviderDriverAndHoldsOneSession() {
        DriverManager.createDriver();

        assertSame(DriverManager.getDriver(), provider.first());
        assertEquals(provider.providerCalls(), 1);
        assertEquals(DriverManager.activeSessions(), 1);
    }

    @Test
    public void createDriver_isIdempotentOnSameThread() {
        DriverManager.createDriver();
        DriverManager.createDriver();

        assertEquals(provider.providerCalls(), 1);
        assertEquals(DriverManager.activeSessions(), 1);
    }

    @Test
    public void createDriver_providerFailureReleasesPermit() {
        configure(1, "per-test");
        DriverProviderRegistry.register(new FakeDriverProvider(BROWSER) {
            @Override
            public WebDriver createDriver() {
                throw new WebDriverException("boom");
            }
        });

        assertThrows(WebDriverException.class, DriverManager::createDriver);

        assertEquals(DriverManager.activeSessions(), 0);
    }

    @Test
    public void createDriver_refusesDuringLoadTest() {
        TestFlyContext.setCurrentTest(AnnotatedLoadTestFixture.class, null);

        assertThrows(IllegalStateException.class, DriverManager::createDriver);
        assertEquals(provider.providerCalls(), 0);
        assertEquals(DriverManager.activeSessions(), 0);
    }

    @LoadTest
    private static final class AnnotatedLoadTestFixture {
    }

    // ── quitDriver / permits (T1.9) ─────────────────────────────────────────

    @Test
    public void quitDriver_releasesPermitAndUnbindsDriver() {
        DriverManager.createDriver();

        DriverManager.quitDriver();

        verify(provider.first()).quit();
        assertEquals(DriverManager.activeSessions(), 0);
        assertThrows(IllegalStateException.class, DriverManager::getDriver);
    }

    @Test
    public void quitFailureReleasesPermit() {
        configure(1, "per-test");
        DriverManager.setSlotWaitForTesting(SHORT_WAIT);
        DriverManager.createDriver();
        doThrow(new WebDriverException("quit failed")).when(provider.first()).quit();

        DriverManager.quitDriver();

        assertEquals(DriverManager.activeSessions(), 0, "permit must be returned even if quit() throws");
        long start = System.nanoTime();
        DriverManager.createDriver(); // would time out if the permit leaked
        assertTrue(Duration.ofNanos(System.nanoTime() - start).compareTo(SHORT_WAIT) < 0,
                "second createDriver must not wait for a slot");
        assertEquals(provider.providerCalls(), 2);
    }

    @Test
    public void forceQuitFailureReleasesPermit() {
        configure(1, "per-test");
        DriverManager.createDriver();
        doThrow(new WebDriverException("quit failed")).when(provider.first()).quit();

        DriverManager.forceQuitDriver();

        assertEquals(DriverManager.activeSessions(), 0);
        assertThrows(IllegalStateException.class, DriverManager::getDriver);
    }

    @Test
    public void forceQuitDriver_withoutDriverIsNoOp() {
        DriverManager.forceQuitDriver();

        assertEquals(DriverManager.activeSessions(), 0);
    }

    @Test
    public void quitDriver_calledTwiceReleasesOnce() {
        DriverManager.createDriver();
        DriverManager.acquirePermit(); // second, unrelated holder (e.g. a named session)

        DriverManager.quitDriver();
        DriverManager.quitDriver();

        assertEquals(DriverManager.activeSessions(), 1, "named-session permit must stay held");
    }

    // ── per-suite lifecycle (T1.10) ─────────────────────────────────────────

    @Test
    public void quitDriver_perSuiteKeepsDriverAlive() {
        configure(5, "per-suite");
        DriverManager.createDriver();

        DriverManager.quitDriver();

        verify(provider.first(), never()).quit();
        assertSame(DriverManager.getDriver(), provider.first());
        assertEquals(DriverManager.activeSessions(), 1);
    }

    @Test
    public void recreateDriverPerSuite() {
        configure(5, "per-suite");
        DriverManager.createDriver();
        WebDriver dead = provider.first();
        when(dead.getTitle()).thenThrow(new WebDriverException("session gone"));

        WebDriver healed = DriverManager.getDriver();

        assertNotSame(healed, dead, "dead per-suite driver must be replaced");
        assertEquals(provider.providerCalls(), 2);
        verify(dead).quit();
        assertEquals(DriverManager.activeSessions(), 1, "replacement must not leak or double-release a permit");
    }

    @Test
    public void recreateDriverPerSuite_replacementIsTrackedForSuiteTeardown() {
        configure(5, "per-suite");
        DriverManager.createDriver();
        WebDriver first = provider.first();

        DriverManager.recreateDriver();
        DriverManager.quitAllSuiteDrivers();

        verify(first, times(1)).quit();
        verify(provider.last(), times(1)).quit();
        assertEquals(DriverManager.activeSessions(), 0);
    }

    @Test
    public void recreateDriverPerTest() {
        DriverManager.createDriver();
        WebDriver first = provider.first();

        DriverManager.recreateDriver();

        verify(first).quit();
        assertNotSame(DriverManager.getDriver(), first);
        assertEquals(provider.providerCalls(), 2);
        assertEquals(DriverManager.activeSessions(), 1);
    }

    @Test
    public void recreateDriver_whenOldQuitFailsStillCreatesReplacement() {
        configure(1, "per-test");
        DriverManager.setSlotWaitForTesting(SHORT_WAIT);
        DriverManager.createDriver();
        doThrow(new WebDriverException("quit failed")).when(provider.first()).quit();

        DriverManager.recreateDriver();

        assertEquals(provider.providerCalls(), 2);
        assertEquals(DriverManager.activeSessions(), 1);
    }

    // ── quitAllSuiteDrivers ─────────────────────────────────────────────────

    @Test
    public void quitAllSuiteDrivers_quitsEveryThreadsDriverAndReleasesEachPermit() throws Exception {
        configure(5, "per-suite");
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(executor.submit(DriverManager::createDriver));
        }
        for (Future<?> f : futures) {
            f.get(5, TimeUnit.SECONDS);
        }
        assertEquals(DriverManager.activeSessions(), 2);

        DriverManager.quitAllSuiteDrivers();

        provider.created().forEach(d -> verify(d).quit());
        assertEquals(DriverManager.activeSessions(), 0);
    }

    @Test
    public void quitAllSuiteDrivers_failingQuitStillReleasesItsPermit() throws Exception {
        configure(5, "per-suite");
        onOtherThread(() -> {
            DriverManager.createDriver();
            return null;
        });
        doThrow(new WebDriverException("quit failed")).when(provider.first()).quit();

        DriverManager.quitAllSuiteDrivers();

        assertEquals(DriverManager.activeSessions(), 0);
    }

    @Test
    public void quitAllSuiteDrivers_staleBindingOnOtherThreadDoesNotDoubleRelease() throws Exception {
        configure(5, "per-suite");
        // quitAllSuiteDrivers() runs on the main thread, so the worker keeps the
        // (already quit) driver bound; its own teardown must not hand the permit
        // back a second time.
        ExecutorService single = Executors.newSingleThreadExecutor();
        try {
            single.submit(() -> {
                DriverManager.createDriver();
                return null;
            }).get(5, TimeUnit.SECONDS);
            DriverManager.quitAllSuiteDrivers();
            single.submit(DriverManager::forceQuitDriver).get(5, TimeUnit.SECONDS);
        } finally {
            single.shutdownNow();
        }

        assertEquals(DriverManager.activeSessions(), 0, "permits must never go negative");
    }

    @Test
    public void quitAllSuiteDrivers_withNoSuiteDriversQuitsCurrentThreadDriver() {
        DriverManager.createDriver();

        DriverManager.quitAllSuiteDrivers();

        verify(provider.first()).quit();
        assertEquals(DriverManager.activeSessions(), 0);
    }

    // ── acquirePermit / releasePermit / slot wait (T1.12) ───────────────────

    @Test
    public void acquireAndReleasePermit_adjustActiveSessions() {
        DriverManager.acquirePermit();
        assertEquals(DriverManager.activeSessions(), 1);

        DriverManager.releasePermit();
        assertEquals(DriverManager.activeSessions(), 0);
    }

    @Test
    public void releasePermit_beforeInitialisationIsNoOp() {
        DriverManager.releasePermit();

        assertEquals(DriverManager.activeSessions(), 0);
    }

    @Test
    public void slotWaitConfigurable() throws Exception {
        configure(1, "per-test");
        onOtherThread(() -> {
            DriverManager.createDriver(); // holds the only slot
            return null;
        });
        DriverManager.setSlotWaitForTesting(Duration.ofMillis(150));

        long start = System.nanoTime();
        try {
            DriverManager.createDriver();
            fail("expected a slot timeout");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("session slot"), e.getMessage());
        }
        Duration waited = Duration.ofNanos(System.nanoTime() - start);

        assertTrue(waited.compareTo(Duration.ofSeconds(5)) < 0, "wait must follow the configured duration, not 30s");
        assertEquals(provider.providerCalls(), 1);
        assertEquals(DriverManager.activeSessions(), 1, "timed-out waiter must not consume a permit");
    }

    @Test
    public void slotWaitFollowsExecutionSessionWaitSeconds() throws Exception {
        TestFlyConfig.Execution execution = configure(1, "per-test");
        execution.setSessionWaitSeconds(0);
        onOtherThread(() -> {
            DriverManager.createDriver();
            return null;
        });

        long start = System.nanoTime();
        IllegalStateException e = expectThrows(IllegalStateException.class, DriverManager::createDriver);

        assertTrue(Duration.ofNanos(System.nanoTime() - start).compareTo(Duration.ofSeconds(5)) < 0);
        assertTrue(e.getMessage().contains("after 0s"), e.getMessage());
    }

    @Test
    public void acquirePermit_timesOutWithConfiguredWait() {
        configure(1, "per-test");
        DriverManager.setSlotWaitForTesting(Duration.ofMillis(100));
        DriverManager.acquirePermit();

        assertThrows(IllegalStateException.class, DriverManager::acquirePermit);
    }

    @Test
    public void slotWait_interruptRestoresInterruptFlag() {
        configure(1, "per-test");
        DriverManager.acquirePermit();
        DriverManager.setSlotWaitForTesting(Duration.ofSeconds(30));
        Thread.currentThread().interrupt();
        try {
            assertThrows(IllegalStateException.class, DriverManager::acquirePermit);
            assertTrue(Thread.interrupted(), "interrupt flag must be preserved");
        } finally {
            Thread.interrupted();
        }
    }

    // ── concurrency ─────────────────────────────────────────────────────────

    @Test
    public void maxActiveSessionsBoundsConcurrentDrivers() throws Exception {
        configure(2, "per-test");
        DriverManager.setSlotWaitForTesting(Duration.ofSeconds(10));
        AtomicInteger holding = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            futures.add(executor.submit(() -> {
                DriverManager.createDriver();
                try {
                    peak.accumulateAndGet(holding.incrementAndGet(), Math::max);
                    new CountDownLatch(1).await(20, TimeUnit.MILLISECONDS); // hold the slot briefly
                    holding.decrementAndGet();
                } finally {
                    DriverManager.quitDriver();
                }
                return null;
            }));
        }
        for (Future<?> f : futures) {
            f.get(20, TimeUnit.SECONDS);
        }

        assertTrue(peak.get() <= 2, "peak concurrent drivers was " + peak.get());
        assertEquals(provider.providerCalls(), 6);
        assertEquals(DriverManager.activeSessions(), 0);
    }

    // ── misc ────────────────────────────────────────────────────────────────

    @Test
    public void shouldQuitAfterTest_followsLifecycle() {
        assertTrue(DriverManager.shouldQuitAfterTest());
        configure(5, "per-suite");
        assertTrue(!DriverManager.shouldQuitAfterTest());
    }

    @Test
    public void resetForTesting_clearsSemaphoreAndOverrides() {
        configure(1, "per-test");
        DriverManager.setSlotWaitForTesting(SHORT_WAIT);
        DriverManager.createDriver();
        assertEquals(DriverManager.activeSessions(), 1);

        DriverManager.resetForTesting();

        assertEquals(DriverManager.activeSessions(), 0);
        assertNull(DriverManager.getCloudSessionUrl());
    }

    private static <T extends Throwable> T expectThrows(Class<T> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
            throw new AssertionError("unexpected " + t, t);
        }
        throw new AssertionError("expected " + type.getSimpleName());
    }
}
