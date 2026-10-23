package io.testfly.examples.mobile;

import io.testfly.mobile.MobileConditions;
import io.testfly.precondition.ConditionProvider;

/**
 * Mobile preconditions of the login example app. A mobile test that needs a signed-in app asks for
 * it with {@code @PreCondition("ios-loggedIn")} instead of repeating the login steps:
 *
 * <pre>
 * &#64;PreCondition("ios-loggedIn")
 * &#64;Test
 * public void showsTheBalance() { ... }
 * </pre>
 *
 * Registered in {@code src/test/resources/META-INF/services/io.testfly.mobile.MobileConditions}.
 * With {@code mobile.app.reset: none} the login runs once per device; otherwise before every test.
 */
public class LoginConditions extends MobileConditions {

    @ConditionProvider("ios-loggedIn")
    public void loggedIn() {
        new HomePage()
                .openLogin()
                .signIn(System.getenv("MOBILE_LOGIN_USER"), System.getenv("MOBILE_LOGIN_PASSWORD"))
                .shouldBeClosed();
    }
}
