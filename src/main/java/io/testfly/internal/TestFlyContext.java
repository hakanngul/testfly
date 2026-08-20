package io.testfly.internal;

import io.testfly.config.TestFlyConfig;
import java.util.concurrent.atomic.AtomicReference;

/**
 * TestFlyContext holds immutable, framework-wide state.
 * It is initialized once and remains read-only during execution.
 *
 * <p>Thread-safety guarantee: config is published via AtomicReference,
 * ensuring all threads see the fully-constructed object after initialize().
 */
public final class TestFlyContext {

    private static final AtomicReference<TestFlyConfig> CONFIG = new AtomicReference<>();
    private static final ThreadLocal<String> CURRENT_TEST = new ThreadLocal<>();

    private TestFlyContext() {
        // utility class
    }

    public static void initialize(TestFlyConfig testFlyConfig) {
        if (testFlyConfig == null) {
            throw new IllegalArgumentException("TestFlyConfig must not be null");
        }
        // compareAndSet ensures exactly one initialization; subsequent calls are no-ops
        CONFIG.compareAndSet(null, testFlyConfig);
    }

    // ==========================================================
    // Config
    // ==========================================================

    public static TestFlyConfig getConfig() {
        TestFlyConfig cfg = CONFIG.get();
        if (cfg == null) {
            throw new IllegalStateException(
                "TestFlyContext accessed before framework initialization");
        }
        return cfg;
    }

    public static boolean isInitialized() {
        return CONFIG.get() != null;
    }

    // ==========================================================
    // Current Test Tracking (Per Thread)
    // ==========================================================

    public static void setCurrentTestId(String testId) {
        CURRENT_TEST.set(testId);
    }

    public static String getCurrentTestId() {
        return CURRENT_TEST.get();
    }

    public static void clearCurrentTestId() {
        CURRENT_TEST.remove();
    }
}
