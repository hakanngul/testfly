package io.testfly.ci;

import java.io.File;
import java.util.Map;

/**
 * Detects whether the current process is running inside a CI environment or
 * a Docker/Kubernetes container. Used by the framework to auto-apply
 * CI-friendly defaults (headless, sandbox flags, thread tuning).
 */
public final class CiEnvironmentDetector {

    private CiEnvironmentDetector() {}

    // ==========================================================
    // CI Detection
    // ==========================================================

    /**
     * Returns true if any well-known CI environment variable is set.
     * Covers GitHub Actions, Jenkins, Travis CI, CircleCI, GitLab CI,
     * TeamCity, and Bitbucket Pipelines.
     */
    public static boolean isCI() {
        return isCI(System.getenv());
    }

    public static boolean isCI(Map<String, String> env) {
        return isEnvSet(env, "CI")
                || isEnvSet(env, "GITHUB_ACTIONS")
                || isEnvSet(env, "JENKINS_URL")
                || isEnvSet(env, "TRAVIS")
                || isEnvSet(env, "CIRCLECI")
                || isEnvSet(env, "GITLAB_CI")
                || isEnvSet(env, "TEAMCITY_VERSION")
                || isEnvSet(env, "BITBUCKET_BUILD_NUMBER");
    }

    /**
     * Returns a human-readable name for the detected CI provider.
     */
    public static String ciName() {
        return ciName(System.getenv());
    }

    public static String ciName(Map<String, String> env) {
        if (isEnvSet(env, "GITHUB_ACTIONS"))        return "GitHub Actions";
        if (isEnvSet(env, "JENKINS_URL"))           return "Jenkins";
        if (isEnvSet(env, "TRAVIS"))                return "Travis CI";
        if (isEnvSet(env, "CIRCLECI"))              return "CircleCI";
        if (isEnvSet(env, "GITLAB_CI"))             return "GitLab CI";
        if (isEnvSet(env, "TEAMCITY_VERSION"))      return "TeamCity";
        if (isEnvSet(env, "BITBUCKET_BUILD_NUMBER")) return "Bitbucket Pipelines";
        if (isEnvSet(env, "CI"))                    return "CI (generic)";
        return "local";
    }

    // ==========================================================
    // Metadata Capture
    // ==========================================================

    /**
     * Captures structured CI metadata from the process environment.
     * Returns {@link CiMetadata#empty()} when not running in a recognized CI
     * environment or when no metadata variables are present.
     */
    public static CiMetadata captureMetadata() {
        return captureMetadata(System.getenv());
    }

    public static CiMetadata captureMetadata(Map<String, String> env) {
        if (!isCI(env)) {
            return CiMetadata.empty();
        }

        String provider = ciName(env);
        return new CiMetadata(
                provider,
                buildNumber(env, provider),
                buildId(env, provider),
                branch(env, provider),
                commitSha(env, provider),
                commitMessage(env, provider),
                buildUrl(env, provider),
                jobName(env, provider),
                pullRequest(env, provider),
                repository(env, provider),
                actor(env, provider),
                agentName(env, provider),
                environment(env, provider)
        );
    }

    private static String buildNumber(Map<String, String> env, String provider) {
        switch (provider) {
            case "GitHub Actions":  return env.get("GITHUB_RUN_NUMBER");
            case "Jenkins":         return env.get("BUILD_NUMBER");
            case "GitLab CI":       return env.get("CI_PIPELINE_IID");
            case "CircleCI":        return env.get("CIRCLE_BUILD_NUM");
            case "Travis CI":       return env.get("TRAVIS_BUILD_NUMBER");
            case "TeamCity":        return env.get("BUILD_NUMBER");
            case "Bitbucket Pipelines": return env.get("BITBUCKET_BUILD_NUMBER");
            default:                return env.get("CI_PIPELINE_IID");
        }
    }

    private static String buildId(Map<String, String> env, String provider) {
        switch (provider) {
            case "GitHub Actions":  return env.get("GITHUB_RUN_ID");
            case "Jenkins":         return env.get("BUILD_ID");
            case "GitLab CI":       return env.get("CI_PIPELINE_ID");
            case "CircleCI":        return env.get("CIRCLE_WORKFLOW_ID");
            case "Travis CI":       return env.get("TRAVIS_BUILD_ID");
            case "TeamCity":        return env.get("TEAMCITY_BUILD_ID");
            case "Bitbucket Pipelines": return env.get("BITBUCKET_PIPELINE_UUID");
            default:                return env.get("CI_BUILD_ID");
        }
    }

    private static String branch(Map<String, String> env, String provider) {
        switch (provider) {
            case "GitHub Actions":
                String headRef = env.get("GITHUB_HEAD_REF");
                return headRef != null && !headRef.isBlank()
                        ? headRef
                        : stripRefsPrefix(env.get("GITHUB_REF_NAME"));
            case "Jenkins":         return stripRefsPrefix(env.get("GIT_BRANCH"));
            case "GitLab CI":       return stripRefsPrefix(env.get("CI_COMMIT_REF_NAME"));
            case "CircleCI":        return stripRefsPrefix(env.get("CIRCLE_BRANCH"));
            case "Travis CI":       return stripRefsPrefix(env.get("TRAVIS_BRANCH"));
            case "TeamCity":        return stripRefsPrefix(env.get("BRANCH_NAME"));
            case "Bitbucket Pipelines": return stripRefsPrefix(env.get("BITBUCKET_BRANCH"));
            default:                return stripRefsPrefix(env.get("CI_COMMIT_REF_NAME"));
        }
    }

    private static String commitSha(Map<String, String> env, String provider) {
        switch (provider) {
            case "GitHub Actions":  return env.get("GITHUB_SHA");
            case "Jenkins":         return env.get("GIT_COMMIT");
            case "GitLab CI":       return env.get("CI_COMMIT_SHA");
            case "CircleCI":        return env.get("CIRCLE_SHA1");
            case "Travis CI":       return env.get("TRAVIS_COMMIT");
            case "TeamCity":        return env.get("BUILD_VCS_NUMBER");
            case "Bitbucket Pipelines": return env.get("BITBUCKET_COMMIT");
            default:                return env.get("CI_COMMIT_SHA");
        }
    }

    private static String commitMessage(Map<String, String> env, String provider) {
        switch (provider) {
            case "GitLab CI":       return env.get("CI_COMMIT_MESSAGE");
            case "Bitbucket Pipelines": return env.get("BITBUCKET_COMMIT_MESSAGE");
            default:                return null;
        }
    }

    private static String buildUrl(Map<String, String> env, String provider) {
        switch (provider) {
            case "GitHub Actions":
                String server = env.getOrDefault("GITHUB_SERVER_URL", "https://github.com");
                String repo = env.get("GITHUB_REPOSITORY");
                String runId = env.get("GITHUB_RUN_ID");
                if (repo != null && runId != null) {
                    return server + "/" + repo + "/actions/runs/" + runId;
                }
                return null;
            case "Jenkins":         return env.get("BUILD_URL");
            case "GitLab CI":       return env.get("CI_PIPELINE_URL");
            case "CircleCI":        return env.get("CIRCLE_BUILD_URL");
            case "Travis CI":       return env.get("TRAVIS_BUILD_WEB_URL");
            case "TeamCity":        return env.get("BUILD_URL");
            case "Bitbucket Pipelines": return env.get("BITBUCKET_BUILD_URL");
            default:                return env.get("CI_PIPELINE_URL");
        }
    }

    private static String jobName(Map<String, String> env, String provider) {
        switch (provider) {
            case "GitHub Actions":  return env.get("GITHUB_JOB");
            case "Jenkins":         return env.get("JOB_NAME");
            case "GitLab CI":       return env.get("CI_JOB_NAME");
            case "CircleCI":        return env.get("CIRCLE_JOB");
            case "Travis CI":       return env.get("TRAVIS_JOB_NAME");
            case "TeamCity":        return env.get("TEAMCITY_BUILDCONF_NAME");
            case "Bitbucket Pipelines": return env.get("BITBUCKET_STEP_UUID");
            default:                return env.get("CI_JOB_NAME");
        }
    }

    private static String pullRequest(Map<String, String> env, String provider) {
        switch (provider) {
            case "GitHub Actions":  return env.get("GITHUB_HEAD_REF") != null ? env.getOrDefault("PR_NUMBER", "") : null;
            case "Jenkins":         return env.get("CHANGE_ID");
            case "GitLab CI":       return env.get("CI_MERGE_REQUEST_IID");
            case "CircleCI":        return env.get("CIRCLE_PR_NUMBER");
            case "Travis CI":
                String pr = env.get("TRAVIS_PULL_REQUEST");
                return "false".equalsIgnoreCase(pr) ? null : pr;
            case "Bitbucket Pipelines": return env.get("BITBUCKET_PR_ID");
            default:                return env.get("CI_MERGE_REQUEST_IID");
        }
    }

    private static String repository(Map<String, String> env, String provider) {
        switch (provider) {
            case "GitHub Actions":  return env.get("GITHUB_REPOSITORY");
            case "Jenkins":         return env.get("GIT_URL");
            case "GitLab CI":       return env.get("CI_PROJECT_PATH");
            case "CircleCI":        return env.get("CIRCLE_REPOSITORY_URL");
            case "Travis CI":       return env.get("TRAVIS_REPO_SLUG");
            case "Bitbucket Pipelines": return env.get("BITBUCKET_REPO_FULL_NAME");
            default:                return env.get("CI_REPOSITORY_URL");
        }
    }

    private static String actor(Map<String, String> env, String provider) {
        switch (provider) {
            case "GitHub Actions":  return env.get("GITHUB_ACTOR");
            case "Jenkins":         return env.get("CHANGE_AUTHOR");
            case "GitLab CI":       return env.get("GITLAB_USER_LOGIN");
            case "CircleCI":        return env.get("CIRCLE_USERNAME");
            case "Travis CI":       return env.get("TRAVIS_PULL_REQUEST_AUTHOR");
            case "Bitbucket Pipelines": return env.get("BITBUCKET_STEP_TRIGGERER_UUID");
            default:                return env.get("CI_COMMIT_AUTHOR");
        }
    }

    private static String agentName(Map<String, String> env, String provider) {
        switch (provider) {
            case "GitHub Actions":  return env.get("RUNNER_NAME");
            case "Jenkins":         return env.get("NODE_NAME");
            case "GitLab CI":       return env.get("CI_RUNNER_DESCRIPTION");
            case "CircleCI":        return env.get("CIRCLE_INTERNAL_TASK_DATA");
            case "TeamCity":        return env.get("AGENT_NAME");
            default:                return env.get("CI_RUNNER_DESCRIPTION");
        }
    }

    private static String environment(Map<String, String> env, String provider) {
        switch (provider) {
            case "GitHub Actions":  return env.get("GITHUB_ENVIRONMENT_NAME");
            case "GitLab CI":       return env.get("CI_ENVIRONMENT_NAME");
            default:                return env.get("CI_ENVIRONMENT_NAME");
        }
    }

    private static String stripRefsPrefix(String ref) {
        if (ref == null) return null;
        if (ref.startsWith("refs/heads/")) return ref.substring("refs/heads/".length());
        if (ref.startsWith("refs/tags/"))  return ref.substring("refs/tags/".length());
        return ref;
    }

    // ==========================================================
    // Container Detection
    // ==========================================================

    /**
     * Returns true if the process is running inside a Docker container or
     * a Kubernetes pod. Used to auto-apply Chrome sandbox / shared-memory flags.
     */
    public static boolean isContainer() {
        // Docker writes /.dockerenv on startup
        if (new File("/.dockerenv").exists()) {
            return true;
        }
        // Kubernetes injects KUBERNETES_SERVICE_HOST into every pod
        if (isEnvSet("KUBERNETES_SERVICE_HOST")) {
            return true;
        }
        // Fallback: inspect /proc/1/cgroup for "docker" or "kubepods" (Linux only)
        File cgroupFile = new File("/proc/1/cgroup");
        if (cgroupFile.exists()) {
            try {
                String content = new String(java.nio.file.Files.readAllBytes(cgroupFile.toPath()));
                return content.contains("docker") || content.contains("kubepods");
            } catch (Exception ignored) {
                // not readable — skip
            }
        }
        return false;
    }

    // ==========================================================
    // Thread Tuning
    // ==========================================================

    /**
     * Returns the recommended thread count for CI execution.
     * Uses available CPU cores, capped at {@code maxAllowed}.
     */
    public static int recommendedThreadCount(int maxAllowed) {
        int cores = Runtime.getRuntime().availableProcessors();
        return Math.min(cores, maxAllowed);
    }

    // ==========================================================
    // Internal
    // ==========================================================

    private static boolean isEnvSet(String name) {
        return isEnvSet(System.getenv(), name);
    }

    private static boolean isEnvSet(Map<String, String> env, String name) {
        String value = env.get(name);
        return value != null && !value.isBlank();
    }
}
