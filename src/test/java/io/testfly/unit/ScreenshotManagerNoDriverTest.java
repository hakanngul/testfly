package io.testfly.unit;

import io.testfly.driver.DriverManager;
import io.testfly.reporting.ScreenshotManager;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNull;

/**
 * When driver creation failed there is no session; screenshot capture on the
 * failure path must return {@code null} instead of throwing and masking the
 * original error.
 */
@Test(singleThreaded = true)
public class ScreenshotManagerNoDriverTest {

    @BeforeMethod
    public void noDriver() {
        DriverManager.resetForTesting();
    }

    @Test
    public void capture_withoutDriver_returnsNull() {
        assertNull(ScreenshotManager.capture("noDriver"));
    }

    @Test
    public void captureAsBase64_withoutDriver_returnsNull() {
        assertNull(ScreenshotManager.captureAsBase64());
    }
}
