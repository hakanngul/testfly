package io.testfly.ci;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable snapshot of CI/CD environment metadata.
 *
 * <p>Populated by {@link CiEnvironmentDetector} from well-known environment variables.
 * Unknown or unavailable fields are {@code null}.</p>
 */
public record CiMetadata(String provider, String buildNumber, String buildId, String branch, String commitSha,
                         String commitMessage, String buildUrl, String jobName, String pullRequest, String repository,
                         String actor, String agentName, String environment) {

    public static CiMetadata empty() {
        return new CiMetadata(null, null, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    /**
     * Returns a compact map for JSON/serialization, skipping null values.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        putIfNotBlank(map, "provider", provider);
        putIfNotBlank(map, "buildNumber", buildNumber);
        putIfNotBlank(map, "buildId", buildId);
        putIfNotBlank(map, "branch", branch);
        putIfNotBlank(map, "commitSha", commitSha);
        putIfNotBlank(map, "commitMessage", commitMessage);
        putIfNotBlank(map, "buildUrl", buildUrl);
        putIfNotBlank(map, "jobName", jobName);
        putIfNotBlank(map, "pullRequest", pullRequest);
        putIfNotBlank(map, "repository", repository);
        putIfNotBlank(map, "actor", actor);
        putIfNotBlank(map, "agentName", agentName);
        putIfNotBlank(map, "environment", environment);
        return map;
    }

    private static void putIfNotBlank(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }
}
