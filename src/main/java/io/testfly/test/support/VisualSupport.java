package io.testfly.test.support;

import io.testfly.api.TestFlyApi;
import io.testfly.browser.DeviceEmulator;
import io.testfly.steps.StepLogger;
import io.testfly.visual.VisualAssert;
import io.testfly.visual.VisualTolerance;
import org.openqa.selenium.By;

/**
 * Shared visual-regression and device-emulation helpers.
 *
 * <p>Implemented by {@code BaseTest} and {@code BasePage} so the delegation to
 * {@link VisualAssert} and {@link DeviceEmulator} lives in one place.
 */
@TestFlyApi(since = "1.10.0")
public interface VisualSupport {

    /** Full-page screenshot comparison against the stored baseline. */
    default void assertScreenshot(String name) {
        StepLogger.step("Assert screenshot: " + name);
        VisualAssert.assertScreenshot(name);
    }

    /** Full-page screenshot comparison with a custom pixel-difference tolerance. */
    default void assertScreenshot(String name, VisualTolerance tolerance) {
        StepLogger.step("Assert screenshot: " + name);
        VisualAssert.assertScreenshot(name, tolerance);
    }

    /** Element-scoped screenshot comparison against the stored baseline. */
    default void assertScreenshot(String name, By region) {
        StepLogger.step("Assert screenshot: " + name + " (" + region + ")");
        VisualAssert.assertScreenshot(name, region);
    }

    /** Element-scoped screenshot comparison with a custom tolerance. */
    default void assertScreenshot(String name, By region, VisualTolerance tolerance) {
        StepLogger.step("Assert screenshot: " + name + " (" + region + ")");
        VisualAssert.assertScreenshot(name, region, tolerance);
    }

    /** Applies a named device profile (e.g. {@code "iPhone 14"}) to the current browser session. */
    default void emulateDevice(String deviceName) {
        StepLogger.step("Emulate device: " + deviceName);
        DeviceEmulator.emulate(deviceName);
    }

    /** Resets device emulation (restores desktop viewport and default user-agent). */
    default void resetDevice() {
        StepLogger.step("Reset device emulation");
        DeviceEmulator.reset();
    }
}
