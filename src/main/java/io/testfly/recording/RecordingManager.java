package io.testfly.recording;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.HasDevTools;
import org.openqa.selenium.devtools.v152.page.Page;
import org.openqa.selenium.devtools.v152.page.Page.StartScreencastFormat;
import org.openqa.selenium.devtools.v152.page.model.ScreencastFrame;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Captures a screen recording during Web UI test execution.
 *
 * <p>Supports two recording backends:
 * <ul>
 *   <li><b>CDP Screencast:</b> Uses Chrome DevTools Protocol {@code Page.startScreencast}
 *       on Chromium browsers (Chrome/Edge) for smooth, non-blocking frame streaming.</li>
 *   <li><b>Scheduled Screenshot Poller:</b> Fallback mechanism for non-Chromium browsers
 *       (Firefox, Safari, or remote grids without CDP) capturing periodic frames via {@link TakesScreenshot}.</li>
 * </ul>
 *
 * <p>On test pass (in {@code retain-on-failure} mode), frames are discarded.
 * On test failure, frames are assembled into an animated GIF saved to {@code target/recordings/}.
 *
 * <p>ThreadLocal-based — safe for concurrent parallel test execution.
 */
public final class RecordingManager {

    private static final Logger LOG = Logger.getLogger(RecordingManager.class.getName());
    private static final ThreadLocal<RecordingSession> SESSION = new ThreadLocal<>();

    private RecordingManager() {}

    /**
     * Starts a screen recording session for the current thread with CDP preference enabled.
     *
     * @param driver             the WebDriver instance to record
     * @param fps                frames per second (1–10 recommended)
     * @param maxDurationSeconds hard cap on recording length
     */
    public static void start(WebDriver driver, int fps, int maxDurationSeconds) {
        start(driver, fps, maxDurationSeconds, true);
    }

    /**
     * Starts a screen recording session for the current thread.
     *
     * @param driver             the WebDriver instance to record
     * @param fps                frames per second (1–10 recommended)
     * @param maxDurationSeconds hard cap on recording length
     * @param preferCdp          whether to use CDP screencast if available on the driver
     */
    public static void start(WebDriver driver, int fps, int maxDurationSeconds, boolean preferCdp) {
        stop(); // discard any leftover session from a previous test
        if (driver == null) return;

        int  maxFrames  = Math.max(1, fps) * Math.max(1, maxDurationSeconds);
        long intervalMs = 1000L / Math.max(1, fps);

        RecordingSession session = new RecordingSession(driver, maxFrames, fps, preferCdp);
        SESSION.set(session);
        session.start(intervalMs);
    }

    /**
     * Stops recording and discards all captured frames (called on test success in retain-on-failure mode).
     */
    public static void stop() {
        RecordingSession session = SESSION.get();
        if (session != null) {
            session.cancel();
            SESSION.remove();
        }
    }

    /**
     * Stops recording and saves captured frames as an animated GIF.
     * Called on test failure or when recording mode is set to always record.
     *
     * @param testId the fully-qualified test method name (used as filename)
     * @return the path to the saved GIF, or {@code null} if saving failed or no frames were captured
     */
    public static String saveOnFailure(String testId) {
        return save(testId);
    }

    /**
     * Stops recording and saves captured frames as an animated GIF.
     *
     * @param testId the fully-qualified test method name (used as filename)
     * @return the path to the saved GIF, or {@code null} if saving failed or no frames were captured
     */
    public static String save(String testId) {
        RecordingSession session = SESSION.get();
        if (session == null) return null;
        session.cancel();
        SESSION.remove();

        List<BufferedImage> frames = session.getFrames();
        if (frames.isEmpty() && session.getDriver() instanceof TakesScreenshot) {
            try {
                byte[] png = ((TakesScreenshot) session.getDriver()).getScreenshotAs(OutputType.BYTES);
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
                if (img != null) {
                    frames.add(img);
                }
            } catch (Exception ignored) {}
        }
        if (frames.isEmpty()) return null;

        // Duplicate single frame so animated GIF loops gracefully
        if (frames.size() == 1) {
            frames.add(frames.get(0));
        }

        String safeId = testId.replaceAll("[^a-zA-Z0-9._-]", "_");
        File   dir    = new File("target/recordings");
        dir.mkdirs();

        String format = "mp4";
        try {
            if (io.testfly.internal.TestFlyContext.isInitialized()) {
                io.testfly.config.TestFlyConfig cfg = io.testfly.internal.TestFlyContext.getConfig();
                if (cfg != null && cfg.getRecording() != null && cfg.getRecording().getFormat() != null) {
                    format = cfg.getRecording().getFormat().toLowerCase();
                }
            }
        } catch (Exception ignored) {}

        if ("gif".equalsIgnoreCase(format)) {
            File output = new File(dir, safeId + ".gif");
            int delayMs = 1000 / Math.max(1, session.getFps());
            try {
                GifEncoder.write(frames, output, delayMs);
                return output.getPath();
            } catch (IOException e) {
                System.err.println("[TestFly] Failed to save GIF recording for '" + testId + "': " + e.getMessage());
                return null;
            }
        } else {
            File output = new File(dir, safeId + ".mp4");
            try {
                Mp4Encoder.encode(frames, output, session.getFps());
                return output.getPath();
            } catch (Exception e) {
                System.err.println("[TestFly] MP4 encoding failed, falling back to GIF: " + e.getMessage());
                File gifOutput = new File(dir, safeId + ".gif");
                int delayMs = 1000 / Math.max(1, session.getFps());
                try {
                    GifEncoder.write(frames, gifOutput, delayMs);
                    return gifOutput.getPath();
                } catch (IOException ioException) {
                    return null;
                }
            }
        }
    }

    // ── Inner class ──────────────────────────────────────────────────────────

    private static final class RecordingSession {

        private final WebDriver               driver;
        private final int                     maxFrames;
        private final int                     fps;
        private final boolean                 preferCdp;
        private final List<BufferedImage>     frames   = new CopyOnWriteArrayList<>();
        private       ScheduledExecutorService executor;
        private       ScheduledFuture<?>       future;
        private       DevTools                devTools;
        private       boolean                 cdpActive;

        RecordingSession(WebDriver driver, int maxFrames, int fps, boolean preferCdp) {
            this.driver    = driver;
            this.maxFrames = maxFrames;
            this.fps       = fps;
            this.preferCdp = preferCdp;
        }

        void start(long intervalMs) {
            if (preferCdp && driver instanceof HasDevTools) {
                try {
                    DevTools dt = ((HasDevTools) driver).getDevTools();
                    dt.createSessionIfThereIsNotOne();
                    try {
                        dt.send(Page.enable(Optional.empty()));
                    } catch (Exception ignored) {}

                    dt.addListener(Page.screencastFrame(), frame -> {
                        if (frames.size() >= maxFrames) {
                            return;
                        }
                        try {
                            byte[] bytes = Base64.getDecoder().decode(frame.getData());
                            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
                            if (img != null) {
                                frames.add(img);
                            }
                        } catch (Exception ignored) {
                        } finally {
                            try {
                                dt.send(Page.screencastFrameAck(frame.getSessionId()));
                            } catch (Exception ignored) {}
                        }
                    });

                    // everyNthFrame = 1, quality = 80
                    dt.send(Page.startScreencast(
                            Optional.of(StartScreencastFormat.JPEG),
                            Optional.of(80),
                            Optional.of(1280),
                            Optional.of(720),
                            Optional.of(1)
                    ));

                    this.devTools = dt;
                    this.cdpActive = true;
                    return;
                } catch (Throwable t) {
                    LOG.log(Level.FINE, "[RecordingManager] CDP screencast unavailable, falling back to screenshot sampler: " + t.getMessage());
                    this.cdpActive = false;
                }
            }

            // Fallback: Periodic screenshot sampler
            if (!(driver instanceof TakesScreenshot)) return;

            executor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "testfly-recorder");
                t.setDaemon(true);
                return t;
            });
            future = executor.scheduleAtFixedRate(this::captureFallback, 0, intervalMs, TimeUnit.MILLISECONDS);
        }

        private void captureFallback() {
            if (frames.size() >= maxFrames) {
                cancel();
                return;
            }
            try {
                byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
                if (img != null) frames.add(img);
            } catch (Exception ignored) {
                // Driver may be in the middle of navigation or closing; silently skip
            }
        }

        void cancel() {
            if (cdpActive && devTools != null) {
                try {
                    devTools.send(Page.stopScreencast());
                } catch (Exception ignored) {}
            }
            if (future   != null) future.cancel(false);
            if (executor != null) executor.shutdownNow();
        }

        List<BufferedImage> getFrames() { return new ArrayList<>(frames); }
        int getFps()                     { return fps; }
        WebDriver getDriver()            { return driver; }
    }
}
