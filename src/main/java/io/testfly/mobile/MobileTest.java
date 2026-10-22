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
 * {@code @NoBrowser}, it is inherited by subclasses.
 */
@Documented
@Inherited
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface MobileTest {
}
