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
    PRESS_ENTER
}
