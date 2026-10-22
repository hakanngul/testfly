package io.testfly.mobile.spi;

import io.testfly.mobile.MobileDeviceInfo;
import io.testfly.mobile.MobileNode;
import io.testfly.mobile.MobileScreenSize;

import java.time.Duration;
import java.util.List;

/**
 * The replaceable seam between TestFly's mobile API and whatever drives the device (incubating,
 * ADR 0002). The built-in implementation talks to mobilecli; an Appium, cloud or recorded-replay
 * adapter can implement it too.
 *
 * <p>Every call receives the time it may take. Implementations must give up when it runs out and
 * must not retry state-changing calls (tap, text, launch). One instance serves one device session
 * and is used from one thread at a time.
 */
public interface MobileDriver extends AutoCloseable {

    // ── server and discovery (no device bound yet) ──────────────────────

    /** Version of the automation server, e.g. {@code 1.0.20}. */
    String serverVersion(Duration timeout);

    /** Every device the server knows, online or not. */
    List<MobileDeviceInfo> devices(Duration timeout);

    /** Binds this driver to {@code device}; every following call targets it. */
    void useDevice(MobileDeviceInfo device);

    // ── device ──────────────────────────────────────────────────────────

    MobileScreenSize screenSize(Duration timeout);

    /** The current view tree, roots first. */
    List<MobileNode> viewTree(Duration timeout);

    /** PNG bytes of the current screen. */
    byte[] screenshot(Duration timeout);

    void tap(int x, int y, Duration timeout);

    void longPress(int x, int y, Duration pressDuration, Duration timeout);

    /** Types {@code text} into the focused element. */
    void typeText(String text, Duration timeout);

    /** Clears the focused text field. */
    void clearText(Duration timeout);

    void swipe(int x1, int y1, int x2, int y2, Duration timeout);

    /** Presses a hardware button: {@code HOME}, {@code BACK}, {@code ENTER}, {@code VOLUME_UP}, ... */
    void pressButton(String button, Duration timeout);

    void launchApp(String bundleId, Duration timeout);

    void terminateApp(String bundleId, Duration timeout);

    void clearAppData(String bundleId, Duration timeout);

    /** Installs an app file that is reachable from the automation server. */
    void installApp(String path, Duration timeout);

    /** Opens a URL or deep link. */
    void openUrl(String url, Duration timeout);

    /** Releases client-side resources; the device itself is not touched. */
    @Override
    default void close() {
    }
}
