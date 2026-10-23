package io.testfly.mobile;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a {@link MobileLocator} field of a page object for iOS and Android (incubating, ADR 0002).
 * {@link MobilePages#init} picks the selector of the running platform, else {@link #value()}.
 *
 * <pre>
 * &#64;MobileFind(ios = "LoginScreen.loginButton", android = "com.example:id/btnLogin")
 * MobileLocator loginButton;
 *
 * &#64;MobileFind("role=button;name=Sign in")   // same selector on both platforms
 * MobileLocator signIn;
 * </pre>
 *
 * Selectors: a bare string is a test id (iOS accessibility identifier, Android resource-id);
 * otherwise {@code testid=}, {@code text=}, {@code label=}, {@code placeholder=}, {@code type=} or
 * {@code role=<MobileRole>[;name=…]}. See {@link MobileScreen#locate(String)}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface MobileFind {

    /** Selector for both platforms; used when the platform's own selector is empty. */
    String value() default "";

    /** iOS selector. */
    String ios() default "";

    /** Android selector. */
    String android() default "";
}
