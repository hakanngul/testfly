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
import java.util.List;

/**
 * One open device session (incubating, ADR 0002). Open with {@link Mobile#connect()}, use from one
 * thread, and close it (try-with-resources): closing terminates the app under test when
 * {@code mobile.app.reset} is not {@code none} and releases the device for other sessions.
 */
public final class MobileDevice implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(MobileDevice.class);
    private static final String CRASH_DIR = "target/reports/crashes";
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
    /** Crash report ids present when the device opened; {@code null} when the driver cannot list them. */
    private java.util.Set<String> crashesAtOpen;
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

    /** Remembers the crash reports already on the device, so a failed test attaches only new ones; never throws. */
    void rememberCrashReports() {
        try {
            crashesAtOpen = crashReports().stream().map(MobileCrashReport::id).collect(java.util.stream.Collectors.toSet());
        } catch (RuntimeException e) {
            LOGGER.debug("Crash reports are not available on {}: {}", id(), e.getMessage());
        }
    }

    /**
     * Saves the crash reports written since the device opened as {@code target/reports/crashes/<name>_<id>.txt}
     * and returns their paths; empty when there are none or the device cannot list them. Never throws.
     */
    public List<Path> saveNewCrashReports(String name) {
        if (crashesAtOpen == null) {
            return List.of();
        }
        List<Path> saved = new java.util.ArrayList<>();
        try {
            for (MobileCrashReport report : crashReports()) {
                if (crashesAtOpen.add(report.id())) {
                    Path dir = Files.createDirectories(Paths.get(CRASH_DIR));
                    String file = (name + "_" + report.id()).replaceAll("[^a-zA-Z0-9._-]", "_") + ".txt";
                    saved.add(Files.writeString(dir.resolve(file), crashReport(report.id())).toAbsolutePath());
                }
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not save the crash reports of {}: {}", id(), e.getMessage());
        }
        return saved;
    }

    // ── clipboard, files and crash reports ─────────────────────────────

    public String clipboard() {
        return session.driver().clipboard(session.rpcTimeout());
    }

    public void setClipboard(String text) {
        session.driver().setClipboard(java.util.Objects.requireNonNull(text, "text"), session.rpcTimeout());
    }

    public void clearClipboard() {
        session.driver().setClipboard("", session.rpcTimeout());
    }

    /**
     * Copies a local file to {@code remotePath} on the device (Android, iOS simulator), e.g.
     * {@code /sdcard/Download/photo.jpg} or an app container path; mobilecli takes at most 1 MB.
     */
    public void pushFile(Path local, String remotePath) {
        try {
            session.driver().pushFile(Files.readAllBytes(local), remotePath, session.rpcTimeout());
        } catch (IOException e) {
            throw new MobileException("Cannot read " + local + ": " + e.getMessage(), e);
        }
    }

    /** Copies {@code remotePath} from the device to {@code local} (at most 1 MB); returns {@code local}. */
    public Path pullFile(String remotePath, Path local) {
        byte[] content = session.driver().pullFile(remotePath, session.rpcTimeout());
        try {
            if (local.toAbsolutePath().getParent() != null) {
                Files.createDirectories(local.toAbsolutePath().getParent());
            }
            return Files.write(local, content);
        } catch (IOException e) {
            throw new MobileException("Cannot write " + local + ": " + e.getMessage(), e);
        }
    }

    /** Crash reports on the device (iOS simulator: the Mac's DiagnosticReports; Android: logcat crash buffer). */
    public List<MobileCrashReport> crashReports() {
        return session.driver().crashReports(session.rpcTimeout());
    }

    /** The text of one crash report. */
    public String crashReport(String id) {
        return session.driver().crashReport(id, session.rpcTimeout());
    }

    // ── device settings ─────────────────────────────────────────────────

    /** Turns system animations on or off; off makes waits shorter and screens steadier. */
    public void setAnimations(boolean on) {
        session.driver().applySettings(on ? "on" : "off", null, session.rpcTimeout());
    }

    /** {@code light} or {@code dark} mode. */
    public void setAppearance(String appearance) {
        String a = appearance == null ? "" : appearance.trim().toLowerCase(java.util.Locale.ROOT);
        if (!a.equals("light") && !a.equals("dark")) {
            throw new IllegalArgumentException("appearance must be light or dark, got: " + appearance);
        }
        session.driver().applySettings(null, a, session.rpcTimeout());
    }

    // ── apps ────────────────────────────────────────────────────────────

    public boolean isAppInstalled(String bundleId) {
        return session.driver().installedApps(session.rpcTimeout()).contains(bundleId);
    }

    /** Bundle id / package name of the app in front. */
    public String foregroundApp() {
        return session.driver().foregroundApp(session.rpcTimeout());
    }

    public void uninstallApp(String bundleId) {
        session.driver().uninstallApp(bundleId, session.rpcTimeout());
    }

    /** Sends the app under test ({@code mobile.app.bundleId}) to the background for {@code duration}, then brings it back. */
    public void backgroundApp(Duration duration) {
        if (bundleId.isEmpty()) {
            throw new MobileException("backgroundApp needs mobile.app.bundleId to bring the app back");
        }
        pressButton("HOME");
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MobileException("Interrupted while the app was in the background", e);
        }
        launchApp(bundleId);
    }

    // ── keyboard ────────────────────────────────────────────────────────

    /** Whether the on-screen keyboard is shown right now. */
    public boolean isKeyboardShown() {
        return io.testfly.mobile.internal.Keyboard.find(session.driver().viewTree(true, session.rpcTimeout())) != null;
    }

    /**
     * Hides the on-screen keyboard if it is shown: Android presses BACK; iOS taps the keyboard's
     * {@code Done}/{@code Return}/{@code Hide keyboard} key and fails when it has none (tap outside the field instead).
     */
    public void hideKeyboard() {
        List<MobileNode> tree = session.driver().viewTree(true, session.rpcTimeout());
        MobileNode keyboard = io.testfly.mobile.internal.Keyboard.find(tree);
        if (keyboard == null) {
            return;
        }
        if (session.platform() == MobilePlatform.ANDROID) {
            pressButton("BACK");
            return;
        }
        MobileNode key = io.testfly.mobile.internal.Keyboard.dismissKey(keyboard);
        if (key == null) {
            throw new MobileException("The keyboard has no Done, Return or Hide keyboard key; tap outside the field to close it");
        }
        session.driver().tap((int) Math.round(key.bounds().centerX()), (int) Math.round(key.bounds().centerY()), session.rpcTimeout());
    }

    // ── webviews ────────────────────────────────────────────────────────

    /** Webviews of the app in front (and visible Chrome/Safari tabs), as listed now. */
    @SuppressWarnings("unchecked")
    public List<MobileWebView> webViews() {
        Object result = session.driver().webView("list", null, null, session.rpcTimeout());
        List<MobileWebView> out = new java.util.ArrayList<>();
        if (result instanceof List<?> list) {
            for (Object o : list) {
                java.util.Map<String, Object> w = (java.util.Map<String, Object>) o;
                out.add(new MobileWebView(session, String.valueOf(w.get("id")), (String) w.get("url"), (String) w.get("title"),
                        Boolean.TRUE.equals(w.get("isVisible"))));
            }
        }
        return out;
    }

    /** The one visible webview; fails when there is none or more than one. */
    public MobileWebView webView() {
        List<MobileWebView> visible = webViews().stream().filter(MobileWebView::isVisible).toList();
        if (visible.size() != 1) {
            throw new MobileException("Expected one visible webview, found " + visible.size() + ": " + webViews());
        }
        return visible.get(0);
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
