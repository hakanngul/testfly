package io.testfly.examples.mobile;

import io.testfly.mobile.BaseMobileTest;
import io.testfly.mobile.MobilePlatform;
import io.testfly.mobile.MobileTest;
import org.testng.annotations.Test;

import static io.testfly.examples.mobile.IosLoginExampleTest.requiredEnv;

/**
 * The {@link IosLoginExampleTest} flow on Android, with the same page objects and profile
 * ({@code testfly-mobile-login.yml}). The pages need their {@code android = "<package>:id/…"}
 * selectors first: find them with {@code mobilecli dump ui} (skill {@code testfly-mobile-locator-finder}).
 *
 * <pre>
 * MOBILE_LOGIN_USER=&lt;member id&gt; MOBILE_LOGIN_PASSWORD=&lt;password&gt; mvn test -Dtest=AndroidLoginExampleTest
 * </pre>
 */
public class AndroidLoginExampleTest extends BaseMobileTest {

    static {
        String profile = System.getProperty("testfly.profile");
        if (profile == null || profile.isBlank()) {
            System.setProperty("testfly.profile", "mobile-login");
        }
    }

    @Test
    @MobileTest(platform = MobilePlatform.ANDROID)
    public void loginWithMemberId() {
        new HomePage()
                .openLogin()
                .signIn(requiredEnv("MOBILE_LOGIN_USER"), requiredEnv("MOBILE_LOGIN_PASSWORD"))
                .shouldBeClosed();
    }
}
