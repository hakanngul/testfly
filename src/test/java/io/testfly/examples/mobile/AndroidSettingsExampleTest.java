package io.testfly.examples.mobile;

import io.testfly.mobile.BaseMobileTest;
import org.testng.annotations.Test;

import java.util.regex.Pattern;

/**
 * Native Android example on the emulator's built-in Settings app (no APK needed).
 *
 * <pre>
 * emulator -avd &lt;name&gt;
 * mobilecli server start                      # keep it running
 * mobilecli agent install --device &lt;id&gt;       # id from: mobilecli devices (the AVD name)
 * mvn test -Dtestfly.profile=mobile-android -Dtest=AndroidSettingsExampleTest
 * </pre>
 *
 * Texts assume an English emulator (API 30+).
 */
public class AndroidSettingsExampleTest extends BaseMobileTest {

    @Test
    public void opensNetworkSettings() {
        screen().getByText(Pattern.compile("^Network (&|and) internet$")).first().tap();
        expect(screen().getByText(Pattern.compile("(?i)airplane mode"))).toBeVisible();
        device().pressButton("BACK");
    }
}
