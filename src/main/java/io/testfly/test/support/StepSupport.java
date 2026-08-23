package io.testfly.test.support;

import io.testfly.api.TestFlyApi;
import io.testfly.steps.StepLogger;
import io.testfly.steps.StepStatus;

/**
 * Shared step-logging helpers — single source of truth for {@code step()}.
 *
 * <p>Implemented by {@code BaseTest}, {@code BaseJUnit5Test}, {@code BasePage} and
 * {@code BaseCucumberSteps} so the delegation to {@link StepLogger} lives in one place.
 * All overloads delegate to the static methods in {@link StepLogger}.
 */
@TestFlyApi(since = "1.10.0")
public interface StepSupport {

    /** Logs a named step into the HTML report step timeline. */
    default void step(String name) {
        StepLogger.step(name);
    }

    /** Logs a named step with an optional screenshot into the HTML report step timeline. */
    default void step(String name, boolean screenshot) {
        StepLogger.step(name, screenshot);
    }

    /** Logs a named step with an explicit status. */
    default void step(String name, StepStatus status) {
        StepLogger.step(name, status);
    }

    /** Logs a named step with an explicit status and optional screenshot. */
    default void step(String name, StepStatus status, boolean screenshot) {
        StepLogger.step(name, status, screenshot);
    }

    /** Logs a named step with a pre-captured base64 screenshot (e.g. diff image). */
    default void stepWithScreenshot(String name, StepStatus status, String base64Screenshot) {
        StepLogger.stepWithScreenshot(name, status, base64Screenshot);
    }
}
