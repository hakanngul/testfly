package io.testfly.driver;

import io.testfly.api.TestFlyApi;
import io.testfly.config.TestFlyConfig;
import io.testfly.internal.TestFlyContext;
import io.testfly.metrics.ExecutionMetrics;
import org.openqa.selenium.WebDriver;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * DriverManager controls the WebDriver lifecycle.
 *
 * Rules:
 * <li>One WebDriver per thread</li>
 * <li>ThreadLocal ownership</li>
 * <li>Framework-managed creation &amp; destruction only</li>
 * <li>Session limit is enforced with a blocking Semaphore — tests wait for a
 * slot
 * rather than failing fast, preventing spurious failures under parallel
 * load</li>
 */
@TestFlyApi(since = "1.0.0")
public final class DriverManager {

    private static final ThreadLocal<WebDriver> DRIVER = ThreadLocal.withInitial(() -> null);

    /**
     * Session dashboard URL set after driver creation for cloud providers
     * (BrowserStack / Sauce Labs).
     */
    private static final ThreadLocal<String> CLOUD_SESSION_URL = ThreadLocal.withInitial(() -> null);

    /**
     * Stack of named-session driver overrides pushed by
     * {@code MultiSessionManager.withSession()}.
     * When non-empty, {@code getDriver()} returns the top of the stack instead of
     * the primary driver.
     * Stack allows nested {@code withSession()} calls to restore the correct
     * previous session.
     */
    private static final ThreadLocal<java.util.Deque<WebDriver>> SESSION_STACK = ThreadLocal
            .withInitial(java.util.ArrayDeque::new);

    /** Tracks all drivers created under per-suite lifecycle for bulk teardown. */
    private static final java.util.Set<WebDriver> SUITE_DRIVERS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Drivers that currently own a session permit. A permit is returned only by
     * the call that removes the driver from this set, which guarantees exactly one
     * release per driver however many teardown paths reach it.
     */
    private static final java.util.Set<WebDriver> PERMIT_HOLDERS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static volatile Semaphore SESSION_SEMAPHORE;
    private static volatile int MAX_SESSIONS;

    /**
     * Test seam: when non-null, replaces {@code execution.sessionWaitSeconds} as the
     * slot-wait duration so unit tests can control the wait without sleeping.
     */
    private static volatile Duration SLOT_WAIT_OVERRIDE;

    static {
        registerShutdownHook();
    }

    private static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                quitAllSuiteDrivers();
                forceQuitDriver();
            } catch (Throwable ignored) {
            }
        }, "testfly-driver-shutdown"));
    }

    private static Semaphore getOrInitSemaphore() {
        if (SESSION_SEMAPHORE == null) {
            synchronized (DriverManager.class) {
                if (SESSION_SEMAPHORE == null) {
                    MAX_SESSIONS = TestFlyContext.getConfig()
                            .getExecution()
                            .getMaxActiveSessions();
                    SESSION_SEMAPHORE = new Semaphore(MAX_SESSIONS, true); // fair
                }
            }
        }
        return SESSION_SEMAPHORE;
    }

    /**
     * Number of session permits currently held (primary drivers plus named
     * sessions). {@code 0} when the semaphore has not been initialised.
     */
    public static int activeSessions() {
        Semaphore semaphore = SESSION_SEMAPHORE;
        return semaphore == null ? 0 : MAX_SESSIONS - semaphore.availablePermits();
    }

    /**
     * Framework-internal test hook: discards the static session semaphore, the
     * suite-driver registry, the slot-wait override and the calling thread's
     * bindings so the next {@link #createDriver()} re-reads the configuration.
     * Does not quit any driver; production code must not call this.
     * <p>Not part of the {@code @TestFlyApi} stability contract; it may change or
     * move to an internal accessor in any release.
     */
    public static synchronized void resetForTesting() {
        SESSION_SEMAPHORE = null;
        MAX_SESSIONS = 0;
        SLOT_WAIT_OVERRIDE = null;
        SUITE_DRIVERS.clear();
        PERMIT_HOLDERS.clear();
        DRIVER.remove();
        CLOUD_SESSION_URL.remove();
        SESSION_STACK.remove();
    }

    /**
     * Framework-internal test hook: overrides how long {@link #createDriver()} and
     * {@link #acquirePermit()} wait for a free session slot. Pass {@code null} to
     * fall back to {@code execution.sessionWaitSeconds}. Cleared by
     * {@link #resetForTesting()}.
     * <p>Not part of the {@code @TestFlyApi} stability contract; it may change or
     * move to an internal accessor in any release.
     */
    public static void setSlotWaitForTesting(Duration wait) {
        SLOT_WAIT_OVERRIDE = wait;
    }

    private DriverManager() {
    }

    // ==========================================================
    // Lifecycle helpers
    // ==========================================================

    private static boolean isPerSuite() {
        try {
            TestFlyConfig.Browser b = TestFlyContext.getConfig().getBrowser();
            return b != null && "per-suite".equalsIgnoreCase(b.getLifecycle());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns {@code true} when the framework should quit the driver after
     * each test method (default {@code per-test} lifecycle).
     * {@code false} means the driver is kept alive until suite end.
     */
    public static boolean shouldQuitAfterTest() {
        return !isPerSuite();
    }

    // ==========================================================
    // Driver Creation
    // ==========================================================

    /**
     * Create and bind WebDriver to current thread.
     * Idempotent — safe for retry scenarios.
     */
    public static void createDriver() {

        if (DRIVER.get() != null) {
            return; // retry-safe — semaphore permit already held by this thread
        }

        if (isLoadTestActive()) {
            throw new IllegalStateException(
                    "[TestFly] WebDriver creation is strictly forbidden during Load Testing. Load tests must never launch browser sessions.");
        }

        Semaphore semaphore = getOrInitSemaphore();

        acquireSlot(semaphore);

        boolean permitOwned = false; // true once PERMIT_HOLDERS owns the permit
        try {

            long startTime = System.currentTimeMillis();

            DriverProvider provider = DriverProviderFactory.getProvider();

            WebDriver driver = provider.createDriver();

            if (driver == null) {
                throw new IllegalStateException(
                        "DriverProvider returned null WebDriver");
            }

            long startupDuration = System.currentTimeMillis() - startTime;

            PERMIT_HOLDERS.add(driver);
            permitOwned = true;
            DRIVER.set(driver);

            // Capture cloud session URL for BrowserStack / Sauce Labs
            try {
                String mode = TestFlyContext.getConfig().getExecution().getMode();
                if (driver instanceof org.openqa.selenium.remote.RemoteWebDriver rdw) {
                    String sessionId = rdw.getSessionId().toString();
                    if ("browserstack".equalsIgnoreCase(mode)) {
                        CLOUD_SESSION_URL.set(BrowserStackProvider.SESSION_URL_PREFIX + sessionId);
                    } else if ("saucelabs".equalsIgnoreCase(mode)) {
                        CLOUD_SESSION_URL.set(SauceLabsProvider.SESSION_URL_PREFIX + sessionId);
                    }
                }
            } catch (Exception ignored) {
            }

            // Register in suite-driver registry when lifecycle is per-suite
            if (isPerSuite()) {
                SUITE_DRIVERS.add(driver);
                System.out.println(
                        "[TestFly] Browser lifecycle: per-suite — driver will be reused across tests on this thread.");
            }

            // Record driver startup timing
            String testId = TestFlyContext.getCurrentTestId();

            if (testId != null) {
                ExecutionMetrics.recordDriverStartup(
                        testId,
                        startupDuration);
            }

            System.out.println("[TestFly] Active sessions: " + activeSessions());

        } finally {
            if (!permitOwned) {
                semaphore.release(); // return the permit — driver was never stored
            }
        }
    }

    // ==========================================================
    // Semaphore permit helpers (used by MultiSessionManager)
    // ==========================================================

    /**
     * Acquires a single session permit from the session semaphore, blocking for
     * up to {@code execution.sessionWaitSeconds}. Used by {@code MultiSessionManager} when creating named
     * session drivers so that {@code maxActiveSessions} is enforced across all
     * drivers (primary + named sessions).
     *
     * @throws IllegalStateException if the permit cannot be acquired within the
     *                               timeout or the thread is interrupted
     */
    public static void acquirePermit() {
        acquireSlot(getOrInitSemaphore());
    }

    /** How long a thread waits for a free session slot before giving up. */
    private static Duration slotWait() {
        Duration override = SLOT_WAIT_OVERRIDE;
        if (override != null) {
            return override;
        }
        int seconds = TestFlyContext.getConfig().getExecution().getSessionWaitSeconds();
        return Duration.ofSeconds(Math.max(0, seconds));
    }

    private static String describe(Duration wait) {
        return wait.toMillis() % 1000 == 0 ? wait.toSeconds() + "s" : wait.toMillis() + "ms";
    }

    private static void acquireSlot(Semaphore semaphore) {
        Duration wait = slotWait();
        try {
            boolean acquired = semaphore.tryAcquire(wait.toNanos(), TimeUnit.NANOSECONDS);
            if (!acquired) {
                throw new IllegalStateException(
                        "Timed out waiting for an available session slot after " + describe(wait) + ". " +
                                "Consider increasing execution.maxActiveSessions or " +
                                "execution.sessionWaitSeconds in configuration.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for a session slot", e);
        }
    }

    /**
     * Releases a single session permit back to the session semaphore.
     * Used by {@code MultiSessionManager} when quitting a named session driver.
     * No-op when the semaphore has not been initialised (e.g. in unit tests
     * that never created a real driver).
     */
    public static void releasePermit() {
        if (SESSION_SEMAPHORE != null) {
            SESSION_SEMAPHORE.release();
        }
    }

    // ==========================================================
    // Named-session override (MultiSessionManager)
    // ==========================================================

    /**
     * Pushes a named-session driver onto the override stack.
     * While on the stack, {@link #getDriver()} returns this driver.
     * Call {@link #popSessionOverride()} to restore the previous driver.
     */
    public static void pushSessionOverride(WebDriver driver) {
        SESSION_STACK.get().push(driver);
    }

    /**
     * Pops the topmost named-session driver from the override stack.
     * After popping, {@link #getDriver()} returns the driver that was active before
     * the push.
     */
    /**
     * Returns the cloud session dashboard URL (BrowserStack or Sauce Labs) for the
     * current thread's driver, or {@code null} when running locally or against a
     * custom Selenium Grid.
     */
    public static String getCloudSessionUrl() {
        return CLOUD_SESSION_URL.get();
    }

    public static void popSessionOverride() {
        java.util.Deque<WebDriver> stack = SESSION_STACK.get();
        if (!stack.isEmpty())
            stack.pop();
        if (stack.isEmpty())
            SESSION_STACK.remove();
    }

    // ==========================================================
    // Driver Access
    // ==========================================================

    /**
     * Get WebDriver bound to current thread.
     * When a named-session override is active (via {@code withSession()}), returns
     * that driver.
     * Otherwise performs a health check and returns the primary thread driver.
     */
    public static WebDriver getDriver() {

        if (isLoadTestActive()) {
            throw new IllegalStateException(
                    "[TestFly] WebDriver is not available during Load Testing. Load tests must never interact with browser sessions.");
        }

        java.util.Deque<WebDriver> stack = SESSION_STACK.get();
        if (!stack.isEmpty())
            return stack.peek();

        WebDriver driver = DRIVER.get();

        if (driver == null) {
            throw new IllegalStateException(
                    "WebDriver not initialized for current thread. "
                            + "TestFly creates the driver just before a @Test method starts and quits it "
                            + "right after the test finishes, so the driver is only available inside @Test "
                            + "(and @PreCondition / @ConditionProvider methods). It does not exist yet in "
                            + "@BeforeMethod or @BeforeClass, and it is already closed in @AfterMethod. "
                            + "Move browser setup (such as logging in) into @PreCondition, or into the @Test itself.");
        }

        if (!isDriverAlive()) {

            System.err.println(
                    "[TestFly] Driver session invalid. Recreating...");

            recreateDriver();
            driver = DRIVER.get();
        }

        return driver;
    }

    // ==========================================================
    // Driver Recreation (Self-Healing)
    // ==========================================================

    /**
     * Discards the current thread's driver and creates a fresh one. Works for both
     * lifecycles: the old driver is torn down like {@link #forceQuitDriver()} (so a
     * dead {@code per-suite} driver is not kept) and removed from the suite registry.
     */
    public static void recreateDriver() {

        forceQuitDriver();

        createDriver();
    }

    /**
     * Lightweight session health check.
     */
    public static boolean isDriverAlive() {

        WebDriver driver = DRIVER.get();

        if (driver == null) {
            return false;
        }

        try {
            driver.getTitle(); // lightweight call
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ==========================================================
    // Driver Teardown
    // ==========================================================

    /**
     * Quit and unbind WebDriver from current thread.
     * No-op when lifecycle is {@code per-suite} — driver stays alive until
     * {@link #quitAllSuiteDrivers()} is called at suite end.
     */
    public static void quitDriver() {

        if (isPerSuite()) {
            return; // driver intentionally kept alive for next test on this thread
        }

        teardownCurrentDriver("Driver quit failed: ");

        System.out.println("[TestFly] Active sessions: " + activeSessions());
    }

    /**
     * Forcibly quits the current thread's WebDriver instance, regardless of lifecycle setting.
     */
    public static void forceQuitDriver() {
        teardownCurrentDriver("Driver force quit failed: ");
    }

    /**
     * Quits the current thread's driver, then unbinds it and returns its permit even
     * when {@code quit()} throws. No-op when no driver is bound.
     */
    private static void teardownCurrentDriver(String failurePrefix) {
        WebDriver driver = DRIVER.get();
        if (driver == null) {
            return;
        }
        try {
            SUITE_DRIVERS.remove(driver);
            driver.quit();
        } catch (Exception e) {
            System.err.println("[TestFly] " + failurePrefix + e.getMessage());
        } finally {
            releasePermitOf(driver);
            DRIVER.remove();
            CLOUD_SESSION_URL.remove();
        }
    }

    /** Returns the permit held by {@code driver}, at most once per driver. */
    private static void releasePermitOf(WebDriver driver) {
        if (PERMIT_HOLDERS.remove(driver)) {
            releasePermit();
        }
    }

    /**
     * Quits all drivers tracked under {@code per-suite} lifecycle and releases
     * their semaphore permits. Called once by
     * {@code SuiteExecutionListener.onFinish}.
     * Safe to call in {@code per-test} mode — no-op when registry is empty.
     */
    public static void quitAllSuiteDrivers() {
        if (SUITE_DRIVERS.isEmpty()) {
            forceQuitDriver();
            return;
        }
        java.util.List<WebDriver> drivers = new java.util.ArrayList<>(SUITE_DRIVERS);
        java.util.concurrent.atomic.AtomicInteger released = new java.util.concurrent.atomic.AtomicInteger();
        drivers.parallelStream().forEach(driver -> {
            try {
                driver.quit();
            } catch (Exception e) {
                System.err.println("[TestFly] Error quitting suite driver: " + e.getMessage());
            } finally {
                SUITE_DRIVERS.remove(driver);
                if (PERMIT_HOLDERS.remove(driver)) {
                    releasePermit();
                    released.incrementAndGet();
                }
            }
        });
        DRIVER.remove();
        CLOUD_SESSION_URL.remove();
        System.out.println("[TestFly] All suite drivers quit in parallel. Released " + released.get()
                + " session slot(s).");
    }

    /**
     * Checks whether the current thread or test context is executing a load test.
     * During load testing, no WebDriver instance may ever be launched.
     */
    public static boolean isLoadTestActive() {
        if (io.testfly.loadtest.LoadTestRunner.isExecuting()) {
            return true;
        }
        try {
            return io.testfly.loadtest.internal.LoadTestDetector.isLoadTest(
                    TestFlyContext.getCurrentTestClass(), TestFlyContext.getCurrentTestMethod());
        } catch (Exception ignored) {
            return false;
        }
    }
}
