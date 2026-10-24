package io.testfly.mobile;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test class or method as a native mobile test (incubating, ADR 0002): TestFly skips the
 * browser, opens the device from {@code testfly.yml}'s {@code mobile:} section before the test and
 * closes it afterwards. {@link BaseMobileTest} and the JUnit 5 base class carry it already. Unlike
 * {@code @NoBrowser}, it is inherited by subclasses. It does not replace the engine's own
 * {@code @Test}:
 *
 * <pre>
 * &#64;Test
 * &#64;MobileTest(platform = MobilePlatform.IOS)
 * public void loginOnIos() { ... }
 * </pre>
 */
@Documented
@Inherited
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface MobileTest {

    /**
     * The platform this test runs on, overriding {@code mobile.platform}; the {@code mobile.ios:} or
     * {@code mobile.android:} block of the configuration supplies its device and app. Empty: the
     * configured platform. A method's value wins over its class's.
     */
    MobilePlatform[] platform() default {};
}
