package io.testfly.mobile.spi;

import io.testfly.mobile.MobileCrashReport;
import io.testfly.mobile.MobileDeviceInfo;
import io.testfly.mobile.MobileNode;
import io.testfly.mobile.MobileOrientation;
import io.testfly.mobile.MobileScreenSize;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

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

    /**
     * Launches {@code bundleId} through the Android launcher activity {@code activity} (blank: the
     * app's default one). Drivers without activity support ignore it.
     */
    default void launchApp(String bundleId, String activity, Duration timeout) {
        launchApp(bundleId, timeout);
    }

    void terminateApp(String bundleId, Duration timeout);

    void clearAppData(String bundleId, Duration timeout);

    /** Installs an app file that is reachable from the automation server. */
    void installApp(String path, Duration timeout);

    /** Opens a URL or deep link. */
    void openUrl(String url, Duration timeout);

    // ── device settings and recording (optional: the defaults report them as unsupported) ──

    default MobileOrientation orientation(Duration timeout) {
        throw unsupported("orientation");
    }

    default void setOrientation(MobileOrientation orientation, Duration timeout) {
        throw unsupported("setOrientation");
    }

    /** Overrides the device location (degrees). */
    default void setLocation(double latitude, double longitude, Duration timeout) {
        throw unsupported("setLocation");
    }

    /** Restores the real device location. */
    default void clearLocation(Duration timeout) {
        throw unsupported("clearLocation");
    }

    /** Starts a screen recording into {@code output} (MP4), a path on the automation server's host. */
    default void startRecording(Path output, Duration timeout) {
        throw unsupported("startRecording");
    }

    /** Stops the running screen recording and finishes its file. */
    default void stopRecording(Duration timeout) {
        throw unsupported("stopRecording");
    }

    // ── gestures, device data and apps (optional: the defaults report them as unsupported) ──

    /** The view tree; {@code full} also includes windows normally left out, such as the on-screen keyboard. */
    default List<MobileNode> viewTree(boolean full, Duration timeout) {
        if (full) {
            throw unsupported("viewTree(full)");
        }
        return viewTree(timeout);
    }

    /** Two-finger pinch centred on (x, y): {@code out} spreads the fingers (zoom in), otherwise they close. */
    default void pinch(int x, int y, boolean out, int distance, Duration duration, Duration timeout) {
        throw unsupported("pinch");
    }

    /** One-finger press at (x1, y1), move to (x2, y2) over {@code duration}, release. */
    default void drag(int x1, int y1, int x2, int y2, Duration duration, Duration timeout) {
        throw unsupported("drag");
    }

    default String clipboard(Duration timeout) {
        throw unsupported("clipboard");
    }

    default void setClipboard(String text, Duration timeout) {
        throw unsupported("setClipboard");
    }

    default List<MobileCrashReport> crashReports(Duration timeout) {
        throw unsupported("crashReports");
    }

    default String crashReport(String id, Duration timeout) {
        throw unsupported("crashReport");
    }

    default void pushFile(byte[] content, String remotePath, Duration timeout) {
        throw unsupported("pushFile");
    }

    default byte[] pullFile(String remotePath, Duration timeout) {
        throw unsupported("pullFile");
    }

    /**
     * A webview operation ({@code list}, {@code url}, {@code title}, {@code content}, {@code query},
     * {@code evaluate}, {@code goto}, {@code reload}, {@code goBack}, {@code goForward},
     * {@code waitForLoadState}) on webview {@code id} ({@code null} for {@code list}); the result as
     * plain Java values ({@code Map}, {@code List}, {@code String}, numbers, booleans) or {@code null}.
     */
    default Object webView(String operation, String id, Map<String, Object> params, Duration timeout) {
        throw unsupported("webView");
    }

    /** {@code animations}: {@code on}/{@code off}; {@code appearance}: {@code light}/{@code dark}; {@code null} leaves one as it is. */
    default void applySettings(String animations, String appearance, Duration timeout) {
        throw unsupported("applySettings");
    }

    /** Bundle ids / package names of the installed apps. */
    default List<String> installedApps(Duration timeout) {
        throw unsupported("installedApps");
    }

    /** Bundle id / package name of the app in front. */
    default String foregroundApp(Duration timeout) {
        throw unsupported("foregroundApp");
    }

    default void uninstallApp(String bundleId, Duration timeout) {
        throw unsupported("uninstallApp");
    }

    private UnsupportedOperationException unsupported(String operation) {
        return new UnsupportedOperationException(getClass().getSimpleName() + " does not support " + operation);
    }

    /** Releases client-side resources; the device itself is not touched. */
    @Override
    default void close() {
    }
}
