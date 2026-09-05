package io.testfly.unit.recording;

import io.testfly.recording.RecordingManager;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

/**
 * Unit tests for {@link RecordingManager}.
 *
 * <p>
 * All tests use mocked WebDriver — no real browser is required.
 * Reflection is used to reset the ThreadLocal session between tests.
 */
@Test(singleThreaded = true)
public class RecordingManagerTest {

    /** Driver that also implements TakesScreenshot — the normal recording path. */
    interface ScreenshotCapableDriver extends WebDriver, TakesScreenshot {
    }

    @Mock
    private ScreenshotCapableDriver driver;

    private AutoCloseable mocks;

    @BeforeMethod
    public void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);

        // Default stub: return a small valid PNG so ImageIO.read() succeeds
        byte[] pngBytes = createTestPng();
        when(driver.getScreenshotAs(any(OutputType.class))).thenReturn(pngBytes);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        RecordingManager.stop();
        clearRecordingSession();
        mocks.close();

        // Clean up any files saveOnFailure may have created
        deleteRecordingsDir();
    }

    // ----------------------------------------------------------
    // Start / Stop lifecycle
    // ----------------------------------------------------------

    @Test
    public void startAndStop_completesWithoutException() {
        RecordingManager.start(driver, 2, 10);
        RecordingManager.stop();
        // Reaching this point without an exception is the assertion
    }

    @Test
    public void stop_clearsActiveSession() {
        RecordingManager.start(driver, 2, 10);
        RecordingManager.stop();

        // saveOnFailure should find no session and return null
        assertNull(RecordingManager.saveOnFailure("should.be.empty"),
                "No session should remain after stop()");
    }

    @Test
    public void stop_whenNoSessionActive_doesNotThrow() {
        // Calling stop with no prior start should be a safe no-op
        RecordingManager.stop();
    }

    // ----------------------------------------------------------
    // Output file naming
    // ----------------------------------------------------------

    @Test
    public void saveOnFailure_sanitizesTestIdForFileName() throws Exception {
        RecordingManager.start(driver, 5, 10);
        waitForCaptures(1); // wait until background thread captures at least one frame

        String testId = "com.example.MyTest#testMethod(arg)";
        String path = RecordingManager.saveOnFailure(testId);

        assertNotNull(path, "GIF should be saved when frames were captured");
        String fileName = new File(path).getName();
        // Special characters should be replaced with underscores
        assertFalse(fileName.contains("#"), "File name must not contain '#'");
        assertFalse(fileName.contains("("), "File name must not contain '('");
        assertFalse(fileName.contains(")"), "File name must not contain ')'");
        assertTrue(fileName.endsWith(".gif"), "Output must be a .gif file");
    }

    @Test
    public void saveOnFailure_outputFileExistsOnDisk() throws Exception {
        RecordingManager.start(driver, 5, 10);
        waitForCaptures(1);

        String testId = "com.example.SimpleTest#testOne";
        String path = RecordingManager.saveOnFailure(testId);

        assertNotNull(path);
        assertTrue(Files.exists(Path.of(path)),
                "The returned path should point to an existing file");
        assertTrue(Files.size(Path.of(path)) > 0,
                "The GIF file should not be empty");
    }

    // ----------------------------------------------------------
    // Recording disabled / unsupported → zero side effects
    // ----------------------------------------------------------

    @Test
    public void start_driverDoesNotSupportScreenshots_noSessionCreated() {
        // Plain WebDriver mock — does NOT implement TakesScreenshot
        WebDriver plainDriver = mock(WebDriver.class);

        RecordingManager.start(plainDriver, 2, 10);

        // No session was created because the driver can't take screenshots
        assertNull(RecordingManager.saveOnFailure("should.be.null"),
                "No session should be created for a non-screenshot-capable driver");
    }

    @Test
    public void saveOnFailure_whenNoSessionStarted_returnsNull() {
        // No prior call to start() — session is null
        String result = RecordingManager.saveOnFailure("com.example.Test#method");
        assertNull(result, "saveOnFailure must return null when no session exists");
    }

    // ----------------------------------------------------------
    // Graceful degradation
    // ----------------------------------------------------------

    @Test
    public void saveOnFailure_screenshotsFail_returnsNullGracefully() {
        // Screenshots always throw — simulates a closed or crashed driver
        when(driver.getScreenshotAs(any(OutputType.class)))
                .thenThrow(new WebDriverException("driver closed"));

        RecordingManager.start(driver, 2, 10);

        // Let the background thread attempt (and fail) a capture
        waitForAttempts(1);

        // Graceful degradation: returns null instead of crashing
        String result = RecordingManager.saveOnFailure("failed.test");
        assertNull(result,
                "saveOnFailure should return null when no frames were captured");
    }

    @Test
    public void start_calledTwice_replacesFirstSession() {
        RecordingManager.start(driver, 2, 10);
        // Second start() should stop the first session and start a new one
        RecordingManager.start(driver, 2, 10);
        RecordingManager.stop();
        // No exception means the lifecycle handled the overlap gracefully
    }

    @Test
    public void saveOnFailure_clearsSessionAfterSave() throws Exception {
        RecordingManager.start(driver, 5, 10);
        waitForCaptures(1);

        String path = RecordingManager.saveOnFailure("some.test");
        assertNotNull(path);

        // A second call should find no session
        assertNull(RecordingManager.saveOnFailure("some.test"),
                "Session should be cleared after saveOnFailure");
    }

    // ----------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------

    private static byte[] createTestPng() throws Exception {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private void waitForCaptures(int minCaptures) {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            try {
                Field sessionField = RecordingManager.class.getDeclaredField("SESSION");
                sessionField.setAccessible(true);
                ThreadLocal<Object> sessionTl = (ThreadLocal<Object>) sessionField.get(null);
                Object session = sessionTl.get();
                if (session != null) {
                    Field framesField = session.getClass().getDeclaredField("frames");
                    framesField.setAccessible(true);
                    List<?> frames = (List<?>) framesField.get(session);
                    if (frames != null && frames.size() >= minCaptures) {
                        return;
                    }
                }
            } catch (Exception ignored) {
            }
            LockSupport.parkNanos(10_000_000L); // 10ms pause
        }
    }

    @Test
    public void start_withPreferCdp_whenDriverNotHasDevTools_fallsBackGracefully() {
        RecordingManager.start(driver, 2, 10, true);
        RecordingManager.stop();
    }

    @Test
    public void save_savesRecordingSameAsSaveOnFailure() throws Exception {
        RecordingManager.start(driver, 5, 10, false);
        waitForCaptures(1);
        String path = RecordingManager.save("com.example.DirectSaveTest#test");
        assertNotNull(path, "RecordingManager.save() should produce a valid file path");
        assertTrue(new File(path).exists(), "Saved recording file should exist on disk");
    }

    @Test
    public void recordingConfig_handlesModesAndCdpFlags() {
        io.testfly.config.TestFlyConfig.Recording rec = new io.testfly.config.TestFlyConfig.Recording();
        assertFalse(rec.isEnabled());
        assertEquals(rec.getMode(), "retain-on-failure");
        assertTrue(rec.isRetainOnFailure());
        assertFalse(rec.isRecordAll());
        assertFalse(rec.shouldRecord());

        rec.setEnabled(true);
        assertTrue(rec.shouldRecord());

        rec.setMode("on");
        assertTrue(rec.isRecordAll());
        assertFalse(rec.isRetainOnFailure());

        rec.setMode("off");
        assertFalse(rec.shouldRecord());

        assertTrue(rec.isCdp());
        rec.setCdp(false);
        assertFalse(rec.isCdp());
    }


    private void waitForAttempts(int minAttempts) {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            try {
                verify(driver, atLeast(minAttempts)).getScreenshotAs(any(OutputType.class));
                return;
            } catch (Throwable e) {
                LockSupport.parkNanos(10_000_000L); // 10ms pause
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void clearRecordingSession() throws Exception {
        Field sessionField = RecordingManager.class.getDeclaredField("SESSION");
        sessionField.setAccessible(true);
        ThreadLocal<Object> session = (ThreadLocal<Object>) sessionField.get(null);
        session.remove();
    }

    private static void deleteRecordingsDir() {
        File recordingsDir = new File("target", "recordings");
        if (recordingsDir.exists() && recordingsDir.isDirectory()) {
            File[] files = recordingsDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
            recordingsDir.delete();
        }
    }
}
