package io.testfly.mobile;

import io.testfly.mobile.internal.DeviceReservations.Reservation;
import io.testfly.mobile.internal.MobileSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * One open device session (incubating, ADR 0002). Open with {@link Mobile#connect()}, use from one
 * thread, and close it (try-with-resources): closing terminates the app under test when
 * {@code mobile.app.reset} is not {@code none} and releases the device for other sessions.
 */
public final class MobileDevice implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(MobileDevice.class);

    private final MobileSession session;
    private final MobileScreen screen;
    private final Reservation reservation;
    private final String bundleId;
    private final boolean terminateOnClose;
    private boolean closed;

    MobileDevice(MobileSession session, Reservation reservation, String bundleId, boolean terminateOnClose) {
        this.session = session;
        this.screen = new MobileScreen(session);
        this.reservation = reservation;
        this.bundleId = bundleId;
        this.terminateOnClose = terminateOnClose;
    }

    /** Locators, swipes and screenshots of the current screen. */
    public MobileScreen screen() {
        return screen;
    }

    /** mobilecli device id (Android emulator: AVD name; iOS: UDID). */
    public String id() {
        return session.device().id();
    }

    public MobileDeviceInfo info() {
        return session.device();
    }

    public MobilePlatform platform() {
        return session.platform();
    }

    public MobileScreenSize screenSize() {
        return session.driver().screenSize(session.rpcTimeout());
    }

    /** PNG bytes of the current screen. */
    public byte[] screenshot() {
        return session.driver().screenshot(session.rpcTimeout());
    }

    /** The configured app ({@code mobile.app.bundleId}), or empty. */
    public String bundleId() {
        return bundleId;
    }

    public void launchApp(String bundleId) {
        session.driver().launchApp(bundleId, session.rpcTimeout());
    }

    public void terminateApp(String bundleId) {
        session.driver().terminateApp(bundleId, session.rpcTimeout());
    }

    /** Clears the app's data (Android also resets its runtime permissions). */
    public void clearAppData(String bundleId) {
        session.driver().clearAppData(bundleId, session.rpcTimeout());
    }

    /** Installs an app file reachable from the mobilecli server. */
    public void installApp(String path) {
        session.driver().installApp(path, session.rpcTimeout());
    }

    /** Opens a URL or deep link. */
    public void openUrl(String url) {
        session.driver().openUrl(url, session.rpcTimeout());
    }

    /** Hardware button: {@code HOME}, {@code BACK}, {@code ENTER}, {@code VOLUME_UP}, ... */
    public void pressButton(String button) {
        session.driver().pressButton(button, session.rpcTimeout());
    }

    /** Default auto-wait budget of locators created from {@link #screen()}. */
    public Duration actionTimeout() {
        return session.actionTimeout();
    }

    /** Whether the app keeps its state from one test to the next ({@code mobile.app.reset: none}, or no bundle id). */
    public boolean keepsAppState() {
        return !terminateOnClose || bundleId.isEmpty();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            if (terminateOnClose && !bundleId.isEmpty()) {
                try {
                    session.driver().terminateApp(bundleId, session.rpcTimeout());
                } catch (RuntimeException e) {
                    LOGGER.debug("Could not terminate {} on {}: {}", bundleId, id(), e.getMessage());
                }
            }
        } finally {
            try {
                session.driver().close();
            } catch (Exception e) {
                LOGGER.debug("Closing the mobile driver failed: {}", e.getMessage());
            } finally {
                reservation.close();
            }
        }
    }

    @Override
    public String toString() {
        return "MobileDevice(" + session.device().describe() + ")";
    }
}
