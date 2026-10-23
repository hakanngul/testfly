package io.testfly.examples.mobile;

import io.testfly.mobile.BaseMobileTest;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Native iOS login with a member id on an app that is already installed on the simulator.
 * Elements come from the {@link HomePage} and {@link LoginPage} page objects ({@code @MobileFind}).
 *
 * <pre>
 * mobilecli server start                      # keep it running
 * mobilecli agent install --device &lt;udid&gt;
 * MOBILE_DEVICE_ID=&lt;udid&gt; MOBILE_LOGIN_USER=&lt;member id&gt; MOBILE_LOGIN_PASSWORD=&lt;password&gt; \
 *   mvn test -Dtest=IosLoginExampleTest
 * </pre>
 *
 * The profile {@code testfly-mobile-ios-login.yml} is selected when no {@code -Dtestfly.profile} is
 * given; an explicit one wins. The app comes from {@code MOBILE_APP_BUNDLE_ID} (default in that profile).
 */
public class IosLoginExampleTest extends BaseMobileTest {

    static {
        // Runs before TestFly loads the config at suite start, like IosLoginHooks does for Cucumber.
        String profile = System.getProperty("testfly.profile");
        if (profile == null || profile.isBlank()) {
            System.setProperty("testfly.profile", "mobile-ios-login");
        }
    }

    @Test
    public void loginWithMemberId() {
        String memberId = requiredEnv("MOBILE_LOGIN_USER");
        String password = requiredEnv("MOBILE_LOGIN_PASSWORD");

        new HomePage()
                .openLogin()
                .signIn(memberId, password)
                .shouldBeClosed();
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        Assert.assertTrue(value != null && !value.isBlank(), "Environment variable " + name + " is required");
        return value;
    }
}
