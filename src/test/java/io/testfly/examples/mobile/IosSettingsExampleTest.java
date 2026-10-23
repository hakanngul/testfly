package io.testfly.examples.mobile;

import io.testfly.mobile.BaseMobileTest;
import org.testng.annotations.Test;

/**
 * Native iOS example on the simulator's built-in Settings app (no app file needed).
 *
 * <pre>
 * xcrun simctl boot "iPhone 16" &amp;&amp; open -a Simulator
 * mobilecli server start                      # keep it running
 * mobilecli agent install --device &lt;udid&gt;      # udid from: mobilecli devices
 * mvn test -Dtestfly.profile=mobile-ios -Dtest=IosSettingsExampleTest
 * </pre>
 *
 * Texts assume an English simulator.
 */
public class IosSettingsExampleTest extends BaseMobileTest {

    @Test
    public void opensGeneralAndAbout() {
        screen().getByText("General").tap();
        screen().getByText("About").tap();
        expect(screen().getByText("Model Name")).toBeVisible();
    }
}
