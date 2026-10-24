package io.testfly.mobile;

import io.testfly.mobile.internal.DeviceReservations.Reservation;
import io.testfly.mobile.internal.MobileSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.testfly.internal.TestFlyContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/**
 * One open device session (incubating, ADR 0002). Open with {@link Mobile#connect()}, use from one
 * thread, and close it (try-with-resources): closing terminates the app under test when
 * {@code mobile.app.reset} is not {@code none} and releases the device for other sessions.
 */
public final class MobileDevice implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(MobileDevice.class);
    private static final String VIDEO_DIR = "target/reports/videos";

    private final MobileSession session;
    private final MobileScreen screen;
    private final Reservation reservation;
    private final String bundleId;
    private final boolean terminateOnClose;
    private boolean closed;
    private boolean locationOverridden;
    private MobileOrientation orientationBefore;
    /** The running recording's file, or {@code null}. */
    private Path recording;
    /** {@code off}, {@code failures} or {@code always} for the recording started with the device. */
    private String recordMode = "off";

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

    public MobileOrientation orientation() {
        return session.driver().orientation(session.rpcTimeout());
    }

    /** Rotates the screen; the orientation before the first change is restored when the device closes. */
    public void setOrientation(MobileOrientation orientation) {
        if (orientationBefore == null) {
            orientationBefore = orientation();
        }
        session.driver().setOrientation(orientation, session.rpcTimeout());
    }

    /** Overrides the device location (degrees); cleared again when the device closes. */
    public void setLocation(double latitude, double longitude) {
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("latitude must be in [-90, 90] and longitude in [-180, 180], got "
                    + latitude + ", " + longitude);
        }
        session.driver().setLocation(latitude, longitude, session.rpcTimeout());
        locationOverridden = true;
    }

    /** Restores the real device location. */
    public void clearLocation() {
        session.driver().clearLocation(session.rpcTimeout());
        locationOverridden = false;
    }

    /**
     * Starts recording the screen into {@code output} (MP4). mobilecli writes the file, so the path is
     * on the machine that runs the mobilecli server.
     */
    public void startRecording(Path output) {
        if (recording != null) {
            throw new IllegalStateException("A screen recording is already running: " + recording);
        }
        try {
            Files.createDirectories(output.toAbsolutePath().getParent());
        } catch (IOException e) {
            throw new MobileException("Cannot create the directory of " + output + ": " + e.getMessage(), e);
        }
        session.driver().startRecording(output, session.rpcTimeout());
        recording = output;
    }

    /** Stops the running recording and returns its file; {@code null} when nothing is recording. */
    public Path stopRecording() {
        Path file = recording;
        if (file == null) {
            return null;
        }
        recording = null;
        session.driver().stopRecording(session.rpcTimeout());
        return file;
    }

    /**
     * Stops the automatic recording ({@code mobile.recordVideo}) and keeps it as
     * {@code target/reports/videos/<name>.mp4}; {@code null} when nothing was recording or saving failed.
     */
    public Path saveRecording(String name) {
        if (recording == null) {
            return null;
        }
        try {
            Path file = stopRecording();
            Path dir = Paths.get(VIDEO_DIR);
            Files.createDirectories(dir);
            Path target = dir.resolve(name.replaceAll("[^a-zA-Z0-9._-]", "_") + "_mobile_" + System.currentTimeMillis() + ".mp4");
            return Files.move(file, target, StandardCopyOption.REPLACE_EXISTING).toAbsolutePath();
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not save the screen recording of {}: {}", id(), e.getMessage());
            return null;
        }
    }

    /** Starts the recording that {@code mobile.recordVideo} asks for; a failure is logged, never thrown. */
    void startAutoRecording(String mode) {
        if (mode.equals("off")) {
            return;
        }
        try {
            startRecording(Paths.get("target", "mobile-recordings", id() + "_" + System.nanoTime() + ".mp4").toAbsolutePath());
            recordMode = mode;
        } catch (RuntimeException e) {
            LOGGER.warn("[TestFly] Screen recording is not available on {}: {}", id(), e.getMessage());
        }
    }

    MobileSession session() {
        return session;
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
            finishDeviceState();
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

    /** Ends what this session changed on the device: the recording, the location and the orientation. */
    private void finishDeviceState() {
        if (recording != null) {
            if (recordMode.equals("always")) {
                String test = TestFlyContext.getCurrentTestId();
                Path saved = saveRecording(test != null ? test : id());
                if (saved != null) {
                    LOGGER.info("[TestFly] Device recording saved: {}", saved);
                }
            } else {
                try {
                    Files.deleteIfExists(stopRecording());
                } catch (IOException | RuntimeException e) {
                    LOGGER.debug("Could not discard the recording of {}: {}", id(), e.getMessage());
                }
            }
        }
        try {
            if (locationOverridden) {
                clearLocation();
            }
            if (orientationBefore != null) {
                session.driver().setOrientation(orientationBefore, session.rpcTimeout());
            }
        } catch (RuntimeException e) {
            LOGGER.debug("Could not restore the location or orientation of {}: {}", id(), e.getMessage());
        }
    }

    @Override
    public String toString() {
        return "MobileDevice(" + session.device().describe() + ")";
    }
}
