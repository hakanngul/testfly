package io.testfly.steps;

import io.testfly.api.TestFlyApi;

@TestFlyApi(since = "0.7.0")
public enum StepStatus {
    INFO, PASS, FAIL, WARN
}
