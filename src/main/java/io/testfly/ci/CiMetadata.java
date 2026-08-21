package io.testfly.ci;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable snapshot of CI/CD environment metadata.
 *
 * <p>Populated by {@link CiEnvironmentDetector} from well-known environment variables.
 * Unknown or unavailable fields are {@code null}.</p>
 */
public final class CiMetadata {

    private final String provider;
    private final String buildNumber;
    private final String buildId;
    private final String branch;
    private final String commitSha;
    private final String commitMessage;
    private final String buildUrl;
    private final String jobName;
    private final String pullRequest;
    private final String repository;
    private final String actor;
    private final String agentName;
    private final String environment;

    public CiMetadata(String provider, String buildNumber, String buildId, String branch,
                      String commitSha, String commitMessage, String buildUrl, String jobName,
                      String pullRequest, String repository, String actor, String agentName,
                      String environment) {
        this.provider = provider;
        this.buildNumber = buildNumber;
        this.buildId = buildId;
        this.branch = branch;
        this.commitSha = commitSha;
        this.commitMessage = commitMessage;
        this.buildUrl = buildUrl;
        this.jobName = jobName;
        this.pullRequest = pullRequest;
        this.repository = repository;
        this.actor = actor;
        this.agentName = agentName;
        this.environment = environment;
    }

    public static CiMetadata empty() {
        return new CiMetadata(null, null, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    public String getProvider()      { return provider; }
    public String getBuildNumber()   { return buildNumber; }
    public String getBuildId()       { return buildId; }
    public String getBranch()        { return branch; }
    public String getCommitSha()     { return commitSha; }
    public String getCommitMessage() { return commitMessage; }
    public String getBuildUrl()      { return buildUrl; }
    public String getJobName()       { return jobName; }
    public String getPullRequest()   { return pullRequest; }
    public String getRepository()    { return repository; }
    public String getActor()         { return actor; }
    public String getAgentName()     { return agentName; }
    public String getEnvironment()   { return environment; }

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
