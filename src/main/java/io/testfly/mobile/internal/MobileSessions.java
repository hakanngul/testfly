package io.testfly.mobile.internal;

import io.testfly.config.TestFlyConfig;
import io.testfly.internal.TestFlyContext;
import io.testfly.mobile.Mobile;
import io.testfly.mobile.MobileDevice;
import io.testfly.mobile.MobileException;
import io.testfly.mobile.MobilePlatform;
import io.testfly.steps.StepLogger;
import io.testfly.steps.StepStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * One device session per test thread (ADR 0002 D4), opened and closed by the engine bridges
 * (TestNG listener, JUnit 5 extension, Cucumber hooks). Sessions are never shared between threads;
 * a shutdown hook closes whatever a crashed run left open.
 */
public final class MobileSessions {

    private static final Logger LOGGER = LoggerFactory.getLogger(MobileSessions.class);
    private static final String SCREENSHOT_DIR = "target/reports/screenshots";

    private static final ThreadLocal<MobileDevice> CURRENT = new ThreadLocal<>();
    private static final Set<MobileDevice> OPEN = ConcurrentHashMap.newKeySet();
    private static volatile Function<MobilePlatform, MobileDevice> opener = Mobile::connect;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> OPEN.forEach(MobileSessions::closeQuietly),
                "testfly-mobile-shutdown"));
    }

    private MobileSessions() {
    }

    /** Opens the device for the current thread, closing a session left over from a previous attempt. */
    public static MobileDevice open() {
        return open(null);
    }

    /** Like {@link #open()}, for {@code platform} ({@code null}: the configured {@code mobile.platform}). */
    public static MobileDevice open(MobilePlatform platform) {
        close();
        MobileDevice device = opener.apply(platform);
        OPEN.add(device);
        CURRENT.set(device);
        return device;
    }

    /** The current thread's device. */
    public static MobileDevice current() {
        MobileDevice device = CURRENT.get();
        if (device == null) {
            throw new MobileException("No mobile device is open on this thread. Extend BaseMobileTest or "
                    + "BaseJUnit5MobileTest, annotate the test with @MobileTest, tag the scenario @mobile, "
                    + "or open one yourself with Mobile.connect().");
        }
        return device;
    }

    public static boolean isOpen() {
        return CURRENT.get() != null;
    }

    /** Closes the current thread's device, if any. Never throws. */
    public static void close() {
        MobileDevice device = CURRENT.get();
        CURRENT.remove();
        if (device != null) {
            OPEN.remove(device);
            closeQuietly(device);
        }
    }

    /**
     * Saves a screenshot of the current device next to the web failure screenshots, logs it as a
     * step, and returns its absolute path; {@code null} when no device is open, the feature is off or
     * the capture fails (the original failure stays visible).
     */
    public static String captureFailure(String testName) {
        MobileDevice device = CURRENT.get();
        if (device == null) {
            return null;
        }
        // A running mobile.recordVideo recording is kept for a failed test, also without screenshots.
        Path video = device.saveRecording(testName);
        if (video != null) {
            LOGGER.info("[TestFly] Device recording of {} saved: {}", testName, video);
            StepLogger.step("Device recording: " + video, StepStatus.FAIL);
        }
        for (Path crash : device.saveNewCrashReports(testName)) {
            LOGGER.info("[TestFly] Crash report of {} saved: {}", testName, crash);
            StepLogger.step("Crash report: " + crash, StepStatus.FAIL);
        }
        if (!screenshotOnFailure()) {
            return null;
        }
        try {
            byte[] png = device.screenshot();
            Path dir = Paths.get(SCREENSHOT_DIR);
            Files.createDirectories(dir);
            Path file = dir.resolve(sanitize(testName) + "_mobile_" + System.currentTimeMillis() + ".png");
            Files.write(file, png);
            StepLogger.stepWithScreenshot("Device screenshot (" + device.id() + ")", StepStatus.FAIL,
                    Base64.getEncoder().encodeToString(png));
            return file.toAbsolutePath().toString();
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not capture a device screenshot for {}: {}", testName, e.getMessage());
            return null;
        }
    }

    private static boolean screenshotOnFailure() {
        try {
            if (!TestFlyContext.isInitialized()) {
                return true;
            }
            TestFlyConfig.Mobile mobile = TestFlyContext.getConfig().getMobile();
            return mobile == null || mobile.isScreenshotOnFailure();
        } catch (RuntimeException e) {
            return true;
        }
    }

    /**
     * Replaces how sessions are opened (tests, custom runners); it gets the requested platform or
     * {@code null}. {@code null} restores {@link Mobile#connect(MobilePlatform)}.
     */
    public static void setOpener(Function<MobilePlatform, MobileDevice> newOpener) {
        opener = newOpener != null ? newOpener : Mobile::connect;
    }

    private static void closeQuietly(MobileDevice device) {
        try {
            device.close();
        } catch (RuntimeException e) {
            LOGGER.debug("Closing {} failed: {}", device, e.getMessage());
        }
    }

    private static String sanitize(String name) {
        return name == null ? "test" : name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
