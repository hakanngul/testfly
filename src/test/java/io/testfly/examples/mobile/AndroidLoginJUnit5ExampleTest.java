package io.testfly.examples.mobile;

import io.testfly.mobile.MobilePlatform;
import io.testfly.mobile.MobileTest;
import io.testfly.mobile.junit5.BaseJUnit5MobileTest;
import org.junit.jupiter.api.Test;

import static io.testfly.examples.mobile.IosLoginJUnit5ExampleTest.requiredEnv;

/**
 * {@link AndroidLoginExampleTest} on JUnit 5: same page objects, same profile ({@code testfly-mobile-login.yml}).
 * The pages need their {@code android = "<package>:id/…"} selectors first.
 *
 * <pre>
 * MOBILE_LOGIN_USER=&lt;member id&gt; MOBILE_LOGIN_PASSWORD=&lt;password&gt; mvn test -Dtest=AndroidLoginJUnit5ExampleTest
 * </pre>
 */
class AndroidLoginJUnit5ExampleTest extends BaseJUnit5MobileTest {

    static {
        String profile = System.getProperty("testfly.profile");
        if (profile == null || profile.isBlank()) {
            System.setProperty("testfly.profile", "mobile-login");
        }
    }

    @Test
    @MobileTest(platform = MobilePlatform.ANDROID)
    void loginWithMemberId() throws InterruptedException {
        new HomePage()
                .openLogin()
                .signIn(requiredEnv("MOBILE_LOGIN_USER"), requiredEnv("MOBILE_LOGIN_PASSWORD"))
                .shouldBeClosed();
        
    }
}
