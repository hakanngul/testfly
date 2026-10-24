package io.testfly.examples.mobile;

import io.testfly.mobile.BaseMobileTest;
import io.testfly.mobile.MobilePlatform;
import io.testfly.mobile.MobileTest;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Native iOS login with a member id on an app that is already installed on the simulator.
 * Elements come from the {@link HomePage} and {@link LoginPage} page objects ({@code @MobileFind});
 * {@link AndroidLoginExampleTest} runs the same flow on Android.
 *
 * <pre>
 * mobilecli agent install --device &lt;udid&gt;     # once per simulator
 * MOBILE_LOGIN_USER=&lt;member id&gt; MOBILE_LOGIN_PASSWORD=&lt;password&gt; mvn test -Dtest=IosLoginExampleTest
 * </pre>
 *
 * The profile {@code testfly-mobile-login.yml} (iOS and Android blocks) is selected when no
 * {@code -Dtestfly.profile} is given; an explicit one wins.
 */
public class IosLoginExampleTest extends BaseMobileTest {

    static {
        // Runs before TestFly loads the config at suite start, like IosLoginHooks does for Cucumber.
        String profile = System.getProperty("testfly.profile");
        if (profile == null || profile.isBlank()) {
            System.setProperty("testfly.profile", "mobile-login");
        }
    }

    @Test
    @MobileTest(platform = MobilePlatform.IOS)
    public void loginWithMemberId() {
        new HomePage()
                .openLogin()
                .signIn(requiredEnv("MOBILE_LOGIN_USER"), requiredEnv("MOBILE_LOGIN_PASSWORD"))
                .shouldBeClosed();
    }

    static String requiredEnv(String name) {
        String value = System.getenv(name);
        Assert.assertTrue(value != null && !value.isBlank(), "Environment variable " + name + " is required");
        return value;
    }
}
