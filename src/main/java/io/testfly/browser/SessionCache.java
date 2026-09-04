package io.testfly.browser;

/**
 * @deprecated Use {@link BrowserSessionCache} instead.
 *             This alias will be removed in TestFly 2.0.0.
 */
@Deprecated(since = "1.1.0")
public final class SessionCache {

    private SessionCache() {
    }

    /** @deprecated Use {@link BrowserSessionCache#store(String)} instead. */
    @Deprecated(since = "1.1.0")
    public static void store(String name) {
        BrowserSessionCache.store(name);
    }

    /** @deprecated Use {@link BrowserSessionCache#restore(String)} instead. */
    @Deprecated(since = "1.1.0")
    public static boolean restore(String name) {
        return BrowserSessionCache.restore(name);
    }

    /** @deprecated Use {@link BrowserSessionCache#exists(String)} instead. */
    @Deprecated(since = "1.1.0")
    public static boolean exists(String name) {
        return BrowserSessionCache.exists(name);
    }

    /** @deprecated Use {@link BrowserSessionCache#invalidate(String)} instead. */
    @Deprecated(since = "1.1.0")
    public static void invalidate(String name) {
        BrowserSessionCache.invalidate(name);
    }

    /** @deprecated Use {@link BrowserSessionCache#clear()} instead. */
    @Deprecated(since = "1.1.0")
    public static void clear() {
        BrowserSessionCache.clear();
    }
}
