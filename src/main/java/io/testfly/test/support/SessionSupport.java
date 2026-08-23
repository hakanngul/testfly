package io.testfly.test.support;

import io.testfly.api.TestFlyApi;
import io.testfly.session.MultiSessionManager;
import io.testfly.steps.StepLogger;
import org.openqa.selenium.WebDriver;

/**
 * Shared multi-session helpers — single source of truth for {@code session()} / {@code withSession()}.
 *
 * <p>Implemented by {@code BaseTest} and {@code BaseJUnit5Test} so the delegation to
 * {@link MultiSessionManager} lives in one place. Not needed on {@code BasePage}.
 */
@TestFlyApi(since = "1.10.0")
public interface SessionSupport {

    /**
     * Returns the named session's {@link WebDriver}.
     * Creates a new browser instance on first access; reuses it on subsequent calls.
     * The session is automatically closed at test end.
     *
     * <pre>
     * WebDriver adminDriver = session("admin");
     * adminDriver.get(baseUrl + "/admin");
     * </pre>
     */
    default WebDriver session(String name) {
        StepLogger.step("Get session: " + name);
        return MultiSessionManager.getSession(name);
    }

    /**
     * Switches the active driver to the named session, runs the action,
     * then restores the previous driver. All framework methods ({@code open()},
     * {@code $()}, {@code assertThat()}) use the session driver inside the lambda.
     *
     * <pre>
     * withSession("admin", () -> {
     *     open("/admin/approvals");
     *     $(By.id("approve-btn")).click();
     * });
     * withSession("user", () -> {
     *     open("/dashboard");
     *     assertThat(By.id("status")).hasText("Approved");
     * });
     * </pre>
     */
    default void withSession(String name, MultiSessionManager.SessionAction action) {
        StepLogger.step("Switch to session: " + name);
        MultiSessionManager.withSession(name, action);
    }
}
