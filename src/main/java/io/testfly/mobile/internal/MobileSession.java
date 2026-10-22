package io.testfly.mobile.internal;

import io.testfly.mobile.MobileDeviceInfo;
import io.testfly.mobile.MobilePlatform;
import io.testfly.mobile.spi.MobileDriver;

import java.time.Duration;

/**
 * Everything one open device needs at runtime: the driver bound to the device, the platform and
 * the time budgets. Owned by one {@code MobileDevice} and used from one thread at a time.
 */
public final class MobileSession {

    private final MobileDriver driver;
    private final MobileDeviceInfo device;
    private final MobilePlatform platform;
    private final Duration actionTimeout;
    private final Duration pollInterval;
    private final Duration rpcTimeout;

    public MobileSession(MobileDriver driver, MobileDeviceInfo device, MobilePlatform platform,
                         Duration actionTimeout, Duration pollInterval, Duration rpcTimeout) {
        this.driver = driver;
        this.device = device;
        this.platform = platform;
        this.actionTimeout = actionTimeout;
        this.pollInterval = pollInterval;
        this.rpcTimeout = rpcTimeout;
    }

    public MobileDriver driver() {
        return driver;
    }

    public MobileDeviceInfo device() {
        return device;
    }

    public MobilePlatform platform() {
        return platform;
    }

    /** Default auto-wait budget of an action or assertion ({@code mobile.timeouts.actionMs}). */
    public Duration actionTimeout() {
        return actionTimeout;
    }

    public Duration pollInterval() {
        return pollInterval;
    }

    /** Upper bound of one request ({@code mobile.timeouts.rpcMs}). */
    public Duration rpcTimeout() {
        return rpcTimeout;
    }

    /** {@code min(rpcMs, time left until deadline)}, never below 1 ms. */
    public Duration requestTimeout(long deadlineNanos) {
        long left = deadlineNanos - System.nanoTime();
        long bounded = Math.min(rpcTimeout.toNanos(), left);
        return Duration.ofNanos(Math.max(bounded, 1_000_000L));
    }
}
