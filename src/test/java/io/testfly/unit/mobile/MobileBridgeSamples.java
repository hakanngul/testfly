package io.testfly.unit.mobile;

import io.testfly.driver.DriverManager;
import io.testfly.mobile.BaseMobileTest;
import io.testfly.mobile.MobileRole;
import io.testfly.mobile.junit5.BaseJUnit5MobileTest;
import org.testng.annotations.Test;

import java.time.Duration;

import static io.testfly.unit.mobile.MobileEngineBridgeTest.SEEN;

/**
 * Sample mobile tests that {@link MobileEngineBridgeTest} runs in-process. The file name does not
 * match surefire's test patterns, so surefire never runs these classes itself.
 */
final class MobileBridgeSamples {

    private MobileBridgeSamples() {
    }

    public static class TestNgSample extends BaseMobileTest {

        @Test
        public void signsIn() {
            synchronized (SEEN) {
                SEEN.add("testng:" + device().id() + ":browser=" + DriverManager.hasDriver());
            }
            screen().getByTestId("username").fill("ada");
            screen().getByRole(MobileRole.BUTTON, "Sign in").tap();
            expect(screen().getByTestId("greeting")).toHaveText("Hello, ada");
        }

        @Test
        public void failsOnPurpose() {
            expect(screen().getByText("Not on this screen").withTimeout(Duration.ofMillis(200))).toBeVisible();
        }
    }

    public static class JUnitSample extends BaseJUnit5MobileTest {

        @org.junit.jupiter.api.Test
        void signsIn() {
            synchronized (SEEN) {
                SEEN.add("junit:" + device().id() + ":browser=" + DriverManager.hasDriver());
            }
            screen().getByTestId("username").fill("grace");
            screen().getByRole(MobileRole.BUTTON, "Sign in").tap();
            expect(screen().getByTestId("greeting")).toHaveText("Hello, grace");
        }

        @org.junit.jupiter.api.Test
        void failsOnPurpose() {
            expect(screen().getByText("Not on this screen").withTimeout(Duration.ofMillis(200))).toBeVisible();
        }
    }


    /** A web test class whose one method is a mobile test. */
    public static class WebClassWithMobileMethod extends io.testfly.test.BaseTest {

        @io.testfly.mobile.MobileTest
        @Test
        public void mobileMethod() {
            synchronized (SEEN) {
                SEEN.add("mixed:" + io.testfly.mobile.internal.MobileSessions.current().id() + ":browser=" + DriverManager.hasDriver());
            }
        }
    }

    /** A plain JUnit 5 class with the TestFly extension and one mobile method. */
    @org.junit.jupiter.api.extension.ExtendWith(io.testfly.junit5.TestFlyExtension.class)
    public static class JUnitAnnotated {

        @io.testfly.mobile.MobileTest
        @org.junit.jupiter.api.Test
        void mobileMethod() {
            synchronized (SEEN) {
                SEEN.add("junit-annotated:" + io.testfly.mobile.internal.MobileSessions.current().id() + ":browser=" + DriverManager.hasDriver());
            }
        }
    }

    /** Two mobile tests that hold their device for a moment; run in parallel against a pool. */
    public static class ParallelSample extends BaseMobileTest {

        private void hold() throws InterruptedException {
            synchronized (SEEN) {
                SEEN.add("parallel:" + device().id());
            }
            expect(screen().getByText("Welcome back")).toBeVisible();
            Thread.sleep(300);
        }

        @Test
        public void first() throws InterruptedException {
            hold();
        }

        @Test
        public void second() throws InterruptedException {
            hold();
        }
    }
}
