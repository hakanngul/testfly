package io.testfly.unit;

import java.util.HashMap;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;

import io.testfly.ci.CiEnvironmentDetector;
import io.testfly.ci.CiMetadata;

/**
 * Unit tests for {@link CiEnvironmentDetector}.
 *
 * CI env vars cannot be injected in-process during tests, so these tests
 * focus on the logic that CAN be exercised without env manipulation:
 * container detection via filesystem, thread-count capping, and the
 * ciName() fallback path.
 */
public class CiEnvironmentDetectorTest {

    // ----------------------------------------------------------
    // recommendedThreadCount
    // ----------------------------------------------------------

    @Test
    public void recommendedThreadCount_neverExceedsMax() {
        int result = CiEnvironmentDetector.recommendedThreadCount(2);
        assertTrue(result <= 2,
                "Thread count must not exceed the configured max");
    }

    @Test
    public void recommendedThreadCount_atLeastOne() {
        int result = CiEnvironmentDetector.recommendedThreadCount(100);
        assertTrue(result >= 1,
                "Thread count must be at least 1");
    }

    @Test
    public void recommendedThreadCount_maxOneReturnsOne() {
        assertEquals(1, CiEnvironmentDetector.recommendedThreadCount(1));
    }

    @Test
    public void recommendedThreadCount_derivedFromCpuCores() {
        int cores = Runtime.getRuntime().availableProcessors();
        int result = CiEnvironmentDetector.recommendedThreadCount(Integer.MAX_VALUE);
        assertEquals(cores, result,
                "Without a cap, result should equal available CPU cores");
    }

    // ----------------------------------------------------------
    // ciName() — local fallback
    // ----------------------------------------------------------

    @Test
    public void ciName_returnsNonNull() {
        // whatever environment we're in, ciName() must return a non-null string
        assertNotNull(CiEnvironmentDetector.ciName());
    }

    @Test
    public void ciName_returnsNonEmpty() {
        assertFalse(CiEnvironmentDetector.ciName().isBlank());
    }

    // ----------------------------------------------------------
    // isContainer() — no /.dockerenv in dev machines
    // ----------------------------------------------------------

    @Test
    public void isContainer_returnsBooleanWithoutThrowing() {
        // Just verify it doesn't throw on any OS — actual value depends on the host
        boolean result = CiEnvironmentDetector.isContainer();
        assertTrue(result || !result); // always true — guards against NPE / exception
    }

    // ----------------------------------------------------------
    // isCI() — returns boolean without throwing
    // ----------------------------------------------------------

    @Test
    public void isCI_returnsBooleanWithoutThrowing() {
        boolean result = CiEnvironmentDetector.isCI();
        assertTrue(result || !result);
    }

    // ----------------------------------------------------------
    // captureMetadata — provider-specific env variable mappings
    // ----------------------------------------------------------

    @Test
    public void captureMetadata_emptyEnv_returnsEmpty() {
        CiMetadata meta = CiEnvironmentDetector.captureMetadata(Map.of());
        assertNotNull(meta);
        assertTrue(meta.toMap().isEmpty(), "No CI env vars means empty metadata");
    }

    @Test
    public void captureMetadata_genericCI_returnsProviderOnly() {
        Map<String, String> env = Map.of("CI", "true");
        CiMetadata meta = CiEnvironmentDetector.captureMetadata(env);
        assertEquals(meta.provider(), "CI (generic)");
        assertNull(meta.buildNumber());
    }

    @Test
    public void captureMetadata_gitHubActions_mapsFields() {
        Map<String, String> env = new HashMap<>();
        env.put("GITHUB_ACTIONS", "true");
        env.put("GITHUB_RUN_NUMBER", "42");
        env.put("GITHUB_RUN_ID", "123456789");
        env.put("GITHUB_REF_NAME", "feature/ci-meta");
        env.put("GITHUB_SHA", "abc123");
        env.put("GITHUB_REPOSITORY", "testfly/testfly");
        env.put("GITHUB_ACTOR", "hagul");
        env.put("GITHUB_JOB", "unit-tests");
        env.put("RUNNER_NAME", "GitHub Actions 1");

        CiMetadata meta = CiEnvironmentDetector.captureMetadata(env);

        assertEquals(meta.provider(), "GitHub Actions");
        assertEquals(meta.buildNumber(), "42");
        assertEquals(meta.buildId(), "123456789");
        assertEquals(meta.branch(), "feature/ci-meta");
        assertEquals(meta.commitSha(), "abc123");
        assertEquals(meta.repository(), "testfly/testfly");
        assertEquals(meta.actor(), "hagul");
        assertEquals(meta.jobName(), "unit-tests");
        assertEquals(meta.agentName(), "GitHub Actions 1");
        assertTrue(meta.buildUrl().contains("testfly/testfly/actions/runs/123456789"));
    }

    @Test
    public void captureMetadata_gitHubActions_pullRequest_prefersHeadRef() {
        Map<String, String> env = new HashMap<>();
        env.put("GITHUB_ACTIONS", "true");
        env.put("GITHUB_HEAD_REF", "pr-branch");
        env.put("GITHUB_REF_NAME", "main");

        CiMetadata meta = CiEnvironmentDetector.captureMetadata(env);

        assertEquals(meta.branch(), "pr-branch");
    }

    @Test
    public void captureMetadata_jenkins_mapsFields() {
        Map<String, String> env = new HashMap<>();
        env.put("JENKINS_URL", "https://jenkins.example.com");
        env.put("BUILD_NUMBER", "17");
        env.put("BUILD_ID", "17");
        env.put("GIT_BRANCH", "refs/heads/main");
        env.put("GIT_COMMIT", "deadbeef");
        env.put("BUILD_URL", "https://jenkins.example.com/job/myjob/17/");
        env.put("JOB_NAME", "myjob");
        env.put("CHANGE_ID", "99");
        env.put("NODE_NAME", "agent-01");

        CiMetadata meta = CiEnvironmentDetector.captureMetadata(env);

        assertEquals(meta.provider(), "Jenkins");
        assertEquals(meta.buildNumber(), "17");
        assertEquals(meta.buildId(), "17");
        assertEquals(meta.branch(), "main");
        assertEquals(meta.commitSha(), "deadbeef");
        assertEquals(meta.buildUrl(), "https://jenkins.example.com/job/myjob/17/");
        assertEquals(meta.jobName(), "myjob");
        assertEquals(meta.pullRequest(), "99");
        assertEquals(meta.agentName(), "agent-01");
    }

    @Test
    public void captureMetadata_gitLabCI_mapsFields() {
        Map<String, String> env = new HashMap<>();
        env.put("GITLAB_CI", "true");
        env.put("CI_PIPELINE_IID", "5");
        env.put("CI_PIPELINE_ID", "1005");
        env.put("CI_COMMIT_REF_NAME", "develop");
        env.put("CI_COMMIT_SHA", "gitlabsha");
        env.put("CI_COMMIT_MESSAGE", "Add CI metadata");
        env.put("CI_PIPELINE_URL", "https://gitlab.example.com/pipelines/1005");
        env.put("CI_JOB_NAME", "test");
        env.put("CI_MERGE_REQUEST_IID", "12");
        env.put("CI_PROJECT_PATH", "testfly/testfly");
        env.put("GITLAB_USER_LOGIN", "hagul");
        env.put("CI_RUNNER_DESCRIPTION", "gitlab-runner-1");

        CiMetadata meta = CiEnvironmentDetector.captureMetadata(env);

        assertEquals(meta.provider(), "GitLab CI");
        assertEquals(meta.buildNumber(), "5");
        assertEquals(meta.buildId(), "1005");
        assertEquals(meta.branch(), "develop");
        assertEquals(meta.commitSha(), "gitlabsha");
        assertEquals(meta.commitMessage(), "Add CI metadata");
        assertEquals(meta.buildUrl(), "https://gitlab.example.com/pipelines/1005");
        assertEquals(meta.jobName(), "test");
        assertEquals(meta.pullRequest(), "12");
        assertEquals(meta.repository(), "testfly/testfly");
        assertEquals(meta.actor(), "hagul");
        assertEquals(meta.agentName(), "gitlab-runner-1");
    }

    @Test
    public void captureMetadata_circleCI_mapsFields() {
        Map<String, String> env = new HashMap<>();
        env.put("CIRCLECI", "true");
        env.put("CIRCLE_BUILD_NUM", "88");
        env.put("CIRCLE_SHA1", "circle-sha");
        env.put("CIRCLE_BRANCH", "main");
        env.put("CIRCLE_BUILD_URL", "https://circleci.com/gh/testfly/testfly/88");
        env.put("CIRCLE_JOB", "build");
        env.put("CIRCLE_PR_NUMBER", "7");
        env.put("CIRCLE_REPOSITORY_URL", "https://github.com/hakanngul/testfly");
        env.put("CIRCLE_USERNAME", "hagul");

        CiMetadata meta = CiEnvironmentDetector.captureMetadata(env);

        assertEquals(meta.provider(), "CircleCI");
        assertEquals(meta.buildNumber(), "88");
        assertEquals(meta.commitSha(), "circle-sha");
        assertEquals(meta.branch(), "main");
        assertEquals(meta.buildUrl(), "https://circleci.com/gh/testfly/testfly/88");
        assertEquals(meta.jobName(), "build");
        assertEquals(meta.pullRequest(), "7");
        assertEquals(meta.repository(), "https://github.com/hakanngul/testfly");
        assertEquals(meta.actor(), "hagul");
    }

    @Test
    public void captureMetadata_stripsRefsPrefix() {
        Map<String, String> env = Map.of(
                "CI", "true",
                "CI_COMMIT_REF_NAME", "refs/heads/release/1.0"
        );
        CiMetadata meta = CiEnvironmentDetector.captureMetadata(env);
        assertEquals(meta.branch(), "release/1.0");
    }

    @Test
    public void captureMetadata_toMap_omitsBlankValues() {
        Map<String, String> env = Map.of("CI", "true");
        CiMetadata meta = CiEnvironmentDetector.captureMetadata(env);
        Map<String, Object> map = meta.toMap();
        assertTrue(map.containsKey("provider"));
        assertFalse(map.containsKey("buildNumber"), "Blank/null fields should be omitted");
    }
}
