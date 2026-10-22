package io.testfly.integration.mobile;

import io.testfly.config.TestFlyConfig;
import io.testfly.mobile.Mobile;
import io.testfly.mobile.MobileDevice;
import io.testfly.mobile.MobileLocator;
import io.testfly.mobile.MobilePlatform;
import io.testfly.mobile.MobileRole;
import io.testfly.mobile.MobileScreen;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.util.regex.Pattern;

import static io.testfly.mobile.MobileAssertions.expect;
import static org.testng.Assert.*;

/**
 * Smoke test of the native mobile module on a real Android emulator through a real mobilecli server
 * (ADR 0002 phase 3). It drives the emulator's built-in Settings app, so no test APK is needed.
 *
 * <p>Skipped unless {@code -Dtestfly.mobile.smoke=true}; the {@code Mobile Emulator Smoke} workflow
 * sets it up (KVM emulator, {@code mobilecli server start}, {@code mobilecli agent install}).
 * Optional: {@code MOBILECLI_URL}, {@code MOBILE_DEVICE_ID}.
 */
public class AndroidEmulatorSmokeIntegrationTest {

    private static TestFlyConfig.Mobile config() {
        if (!Boolean.getBoolean("testfly.mobile.smoke")) {
            throw new SkipException("Android emulator smoke test: set -Dtestfly.mobile.smoke=true with mobilecli and an emulator running");
        }
        TestFlyConfig.Mobile m = new TestFlyConfig.Mobile();
        String url = System.getenv("MOBILECLI_URL");
        if (url != null && !url.isBlank()) {
            m.setServerUrl(url);
        }
        m.setPlatform("android");
        m.setDeviceId(System.getenv().getOrDefault("MOBILE_DEVICE_ID", ""));
        m.getApp().setBundleId("com.android.settings");
        m.getApp().setReset("relaunch");
        // Emulators on CI are slow to settle after a launch.
        m.getTimeouts().setActionMs(30_000);
        m.getTimeouts().setPollMs(500);
        m.getTimeouts().setRpcMs(60_000);
        return m;
    }

    @Test
    public void drivesTheSettingsApp() {
        try (MobileDevice device = Mobile.connect(config())) {
            assertEquals(device.platform(), MobilePlatform.ANDROID);
            assertTrue(device.screenSize().width() > 0, "device.info reports a screen size");
            MobileScreen screen = device.screen();

            MobileLocator network = networkEntry(screen);
            expect(network).toBeVisible();
            assertTrue(screen.getByRole(MobileRole.TEXT).count() > 3, "the Settings list has text elements");
            assertFalse(screen.viewTree().isEmpty(), "device.dump.ui returns elements");

            network.tap();
            expect(screen.getByText(Pattern.compile("(?i)airplane mode"))).toBeVisible();

            device.pressButton("BACK");
            expect(network).toBeVisible();

            byte[] png = device.screenshot();
            assertTrue(png.length > 1000, "device.screenshot returns an image");
            assertEquals(png[1], (byte) 'P');
            assertEquals(png[2], (byte) 'N');
            assertEquals(png[3], (byte) 'G');
        }
    }

    /** "Network & internet" on API 30+; the wording differs slightly between Android versions. */
    private static MobileLocator networkEntry(MobileScreen screen) {
        return screen.getByText(Pattern.compile("^Network (&|and) internet$")).first();
    }
}
