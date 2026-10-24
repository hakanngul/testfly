package io.testfly.mobile;

import io.testfly.mobile.internal.MobileConditionRunner;

/**
 * Base class of mobile precondition providers (incubating, ADR 0002). Annotate public no-argument
 * methods with {@link io.testfly.precondition.ConditionProvider} and write them with page objects;
 * a mobile test asks for them with {@link io.testfly.precondition.PreCondition}.
 *
 * <pre>
 * public class AppConditions extends MobileConditions {
 *     &#64;ConditionProvider("loggedIn")
 *     public void loggedIn() {
 *         new HomePage().openLogin().signIn(System.getenv("APP_USER"), System.getenv("APP_PASSWORD"));
 *     }
 * }
 *
 * &#64;PreCondition("loggedIn")
 * &#64;Test
 * public void showsTheBalance() { ... }
 * </pre>
 *
 * Register providers in {@code META-INF/services/io.testfly.mobile.MobileConditions} or with
 * {@link #register(MobileConditions)}. App state cannot be cached like browser cookies: when the
 * app keeps its state between tests ({@code mobile.app.reset: none}) a condition runs once per
 * device, otherwise before every test that asks for it. A retry runs it again.
 */
public abstract class MobileConditions implements MobileSupport {

    /** Adds a provider next to the ones found through {@code ServiceLoader}. */
    public static void register(MobileConditions provider) {
        MobileConditionRunner.register(provider);
    }
}
