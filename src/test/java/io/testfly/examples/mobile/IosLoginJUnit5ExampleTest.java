package io.testfly.examples.mobile;

import io.testfly.mobile.MobilePlatform;
import io.testfly.mobile.MobileTest;
import io.testfly.mobile.junit5.BaseJUnit5MobileTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link IosLoginExampleTest} on JUnit 5: same page objects, same profile ({@code testfly-mobile-login.yml}).
 *
 * <pre>
 * MOBILE_LOGIN_USER=&lt;member id&gt; MOBILE_LOGIN_PASSWORD=&lt;password&gt; mvn test -Dtest=IosLoginJUnit5ExampleTest
 * </pre>
 */
class IosLoginJUnit5ExampleTest extends BaseJUnit5MobileTest {

    static {
        // Runs when JUnit loads the class, before TestFly loads the config.
        String profile = System.getProperty("testfly.profile");
        if (profile == null || profile.isBlank()) {
            System.setProperty("testfly.profile", "mobile-login");
        }
    }

    @Test
    @MobileTest(platform = MobilePlatform.IOS)
    void loginWithMemberId() {
        new HomePage()
                .openLogin()
                .signIn(requiredEnv("MOBILE_LOGIN_USER"), requiredEnv("MOBILE_LOGIN_PASSWORD"))
                .shouldBeClosed();
    }

    static String requiredEnv(String name) {
        String value = System.getenv(name);
        assertTrue(value != null && !value.isBlank(), "Environment variable " + name + " is required");
        return value;
    }
}
