package io.testfly.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.testfly.api.TestFlyApi;

import java.util.List;

/**
 * Compiled and frozen plan containing ordered action steps for a specific goal.
 *
 * @param goal       the natural language goal
 * @param urlPattern the page URL pattern this plan applies to
 * @param steps      ordered list of action steps
 * @param createdAt  epoch timestamp when compiled
 */
@TestFlyApi(since = "1.9.0")
@JsonIgnoreProperties(ignoreUnknown = true)
public record ActionPlan(
        @JsonProperty("goal") String goal,
        @JsonProperty("urlPattern") String urlPattern,
        @JsonProperty("steps") List<ActionStep> steps,
        @JsonProperty("createdAt") long createdAt
) {}
