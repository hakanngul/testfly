package io.testfly.agent;

import io.testfly.api.TestFlyApi;

/**
 * Elemental UI action types supported by the TestFly Agent.
 */
@TestFlyApi(since = "1.9.0")
public enum ActionType {
    CLICK,
    TYPE,
    CLEAR,
    HOVER,
    WAIT_VISIBLE,
    PRESS_ENTER,
    /** Chooses an option from a {@code <select>} dropdown by its visible text. */
    SELECT,
    /**
     * Navigates the browser to an absolute URL or a path relative to
     * {@code execution.baseUrl}.
     */
    NAVIGATE
}
