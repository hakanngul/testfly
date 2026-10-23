package io.testfly.mobile;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@link MobileFind} field of a {@link MobilePage} that must be visible before the page is
 * ready (incubating, ADR 0002). The page constructor waits for every such field, within the action
 * timeout, before {@link MobilePage#onLoad()} runs.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface WaitFor {
}
