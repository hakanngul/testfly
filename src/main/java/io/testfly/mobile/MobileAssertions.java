package io.testfly.mobile;

/**
 * Web-first assertions for mobile locators (incubating, ADR 0002):
 * {@code expect(screen.getByText("Welcome")).toBeVisible()}. Every assertion retries against fresh
 * view trees until it passes or the locator's budget ({@code mobile.timeouts.actionMs}) runs out.
 */
public final class MobileAssertions {

    private MobileAssertions() {
    }

    public static MobileLocatorAssert expect(MobileLocator locator) {
        return new MobileLocatorAssert(locator, false, null);
    }

    /** Same, with {@code message} prefixed to a failure. */
    public static MobileLocatorAssert expect(MobileLocator locator, String message) {
        return new MobileLocatorAssert(locator, false, message);
    }

    /**
     * Retries {@code supplier} until its value passes the assertion or the timeout (5 s, see
     * {@link MobilePollAssert#withTimeout}) runs out: {@code expectPoll(device::orientation).toBe(LANDSCAPE)}.
     */
    public static <T> MobilePollAssert<T> expectPoll(java.util.function.Supplier<T> supplier) {
        return new MobilePollAssert<>(supplier, java.time.Duration.ofSeconds(5));
    }
}
