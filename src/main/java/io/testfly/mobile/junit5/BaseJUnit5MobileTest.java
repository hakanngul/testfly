package io.testfly.mobile.junit5;

import io.testfly.junit5.TestFlyExtension;
import io.testfly.mobile.MobileSupport;
import io.testfly.mobile.MobileTest;
import io.testfly.test.support.ApiSupport;
import io.testfly.test.support.ContextSupport;
import io.testfly.test.support.DbSupport;
import io.testfly.test.support.EmailSupport;
import io.testfly.test.support.SoftAssertSupport;
import io.testfly.test.support.StepSupport;
import io.testfly.test.support.TestDataSupport;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Superclass for native mobile tests on JUnit 5 (incubating, ADR 0002); the JUnit 5 counterpart of
 * {@link io.testfly.mobile.BaseMobileTest}. It lives in its own package because JUnit is an
 * optional dependency.
 */
@MobileTest
@ExtendWith(TestFlyExtension.class)
public abstract class BaseJUnit5MobileTest
        implements MobileSupport, SoftAssertSupport, TestDataSupport, ApiSupport, ContextSupport, StepSupport, DbSupport, EmailSupport {
}
