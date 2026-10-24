package io.testfly.examples.mobile;

import io.testfly.mobile.BaseMobileTest;
import io.testfly.mobile.MobileFind;
import io.testfly.mobile.MobileLocator;
import io.testfly.mobile.MobilePlatform;
import io.testfly.mobile.MobileTest;

import org.testng.annotations.Test;

/**
 * Native iOS login with a member id on an app that is already installed on the
 * simulator.
 * Elements come from the {@link HomePage} and {@link LoginPage} page objects
 * ({@code @MobileFind}).
 *
 * <pre>
 * mobilecli agent install --device &lt;udid&gt;     # once per simulator
 * MOBILE_LOGIN_USER=&lt;member id&gt; MOBILE_LOGIN_PASSWORD=&lt;password&gt; mvn test -Dtest=IosLoginExampleTest
 * </pre>
 *
 * The profile {@code testfly-mobile-ios-login.yml} is selected when no
 * {@code -Dtestfly.profile} is
 * given; an explicit one wins. The app comes from {@code MOBILE_APP_BUNDLE_ID}
 * (default in that profile).
 * The profile {@code testfly-mobile-ios-login.yml} is selected when no
 * {@code -Dtestfly.profile} is
 * given; an explicit one wins. The app comes from {@code MOBILE_APP_BUNDLE_ID}
 * (default in that profile).
 */
public class IosLoginExampleTest extends BaseMobileTest {
    static {
        // Runs before TestFly loads the config at suite start, like IosLoginHooks does
        // for Cucumber.
        // Runs before TestFly loads the config at suite start, like IosLoginHooks does
        // for Cucumber.
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
                .signIn(MobileEnv.required("MOBILE_LOGIN_USER"), MobileEnv.required("MOBILE_LOGIN_PASSWORD"))
                .shouldBeClosed();
    }

}
