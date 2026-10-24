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

    /** Platform per test: the class says Android, one method says iOS, one says nothing. */
    @io.testfly.mobile.MobileTest(platform = io.testfly.mobile.MobilePlatform.ANDROID)
    public static class TestNgPlatformSample extends BaseMobileTest {

        @Test
        @io.testfly.mobile.MobileTest(platform = io.testfly.mobile.MobilePlatform.IOS)
        public void onIos() {
        }

        @Test
        public void onTheClassPlatform() {
        }
    }

    public static class JUnitPlatformSample extends BaseJUnit5MobileTest {

        @org.junit.jupiter.api.Test
        @io.testfly.mobile.MobileTest(platform = io.testfly.mobile.MobilePlatform.IOS)
        void onIos() {
        }

        @org.junit.jupiter.api.Test
        void onTheConfiguredPlatform() {
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

    /** Mobile precondition provider: signs in on the fake app with page-object style steps. */
    public static class SignInConditions extends io.testfly.mobile.MobileConditions {

        @io.testfly.precondition.ConditionProvider("bridge-signedIn")
        public void signedIn() {
            synchronized (SEEN) {
                SEEN.add("provider:" + device().id());
            }
            screen().getByTestId("username").fill("ada");
            screen().getByRole(MobileRole.BUTTON, "Sign in").tap();
            expect(screen().getByTestId("greeting")).toBeVisible();
        }
    }

    /** Two TestNG tests that start signed in. */
    public static class TestNgPreConditionSample extends BaseMobileTest {

        @io.testfly.precondition.PreCondition("bridge-signedIn")
        @Test(priority = 1)
        public void first() {
            expect(screen().getByTestId("greeting")).toHaveText("Hello, ada");
        }

        @io.testfly.precondition.PreCondition("bridge-signedIn")
        @Test(priority = 2)
        public void second() {
            expect(screen().getByTestId("greeting")).toHaveText("Hello, ada");
        }
    }

    /** A JUnit 5 test that starts signed in. */
    public static class JUnitPreConditionSample extends BaseJUnit5MobileTest {

        @io.testfly.precondition.PreCondition("bridge-signedIn")
        @org.junit.jupiter.api.Test
        void signedIn() {
            expect(screen().getByTestId("greeting")).toHaveText("Hello, ada");
        }
    }
}
