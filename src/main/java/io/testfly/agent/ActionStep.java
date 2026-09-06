package io.testfly.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.testfly.api.TestFlyApi;

/**
 * Single executable action step in an agent action plan.
 *
 * @param action      the type of action to perform
 * @param locator     CSS selector or XPath expression
 * @param value       optional value for inputs (e.g. text for TYPE)
 * @param description human-readable description for timeline logging
 */
@TestFlyApi(since = "1.9.0")
@JsonIgnoreProperties(ignoreUnknown = true)
public record ActionStep(
        @JsonProperty("action") ActionType action,
        @JsonProperty("locator") String locator,
        @JsonProperty("value") String value,
        @JsonProperty("description") String description
) {}
