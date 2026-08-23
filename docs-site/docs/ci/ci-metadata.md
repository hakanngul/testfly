---
description: "CI/CD metadata detection in TestFly: provider, build number, branch, commit, build URL, and actor captured automatically from GitHub Actions, Jenkins, GitLab CI, CircleCI, Travis, TeamCity, and Bitbucket Pipelines."
id: ci-metadata
title: CI Metadata
sidebar_position: 4
---

# CI Metadata

TestFly detects well-known CI/CD environments and captures structured metadata about the pipeline run. This metadata is embedded in:

- `target/testfly-metrics.json` — top-level `ci` object
- `target/testfly-report.html` — **Build Metadata** card with a link back to the pipeline
- `target/surefire-reports/TEST-TestFly.xml` — `<properties>` block for tooling that parses JUnit XML

No manual configuration is required in most cases.

---

## Supported providers

| Provider | Detection variable |
|---|---|
| GitHub Actions | `GITHUB_ACTIONS` |
| Jenkins | `JENKINS_URL` |
| GitLab CI | `GITLAB_CI` |
| CircleCI | `CIRCLECI` |
| Travis CI | `TRAVIS` |
| TeamCity | `TEAMCITY_VERSION` |
| Bitbucket Pipelines | `BITBUCKET_BUILD_NUMBER` |
| Generic CI | `CI` |

---

## Captured fields

| Field | Example | Notes |
|---|---|---|
| `provider` | `GitHub Actions` | Human-readable CI name |
| `buildNumber` | `42` | Display build number |
| `buildId` | `123456789` | Internal run/pipeline ID |
| `branch` | `feature/ci-meta` | Pull-request branch is preferred when available |
| `commitSha` | `abc123def` | Full SHA from the CI env, if provided |
| `commitMessage` | `Add CI metadata` | Only available on GitLab/Bitbucket |
| `buildUrl` | `https://github.com/.../actions/runs/123` | Link back to the pipeline |
| `jobName` | `unit-tests` | CI job/stage name |
| `pullRequest` | `7` | PR/MR identifier, when present |
| `repository` | `testfly/testfly` | Repo slug or URL |
| `actor` | `hagul` | User that triggered the build |
| `agentName` | `agent-01` | Runner/agent name |
| `environment` | `staging` | Deployment environment name, if set |

Fields that are unavailable for a provider are omitted rather than shown as empty.

---

## Configuration

Capture is **enabled automatically** when a CI environment is detected. To disable it explicitly:

```yaml title="testfly.yml"
ci:
  captureMetadata: false
```

To force it on even outside CI:

```yaml title="testfly.yml"
ci:
  captureMetadata: true
```

---

## Provider variable mapping

### GitHub Actions

| Field | Environment variable |
|---|---|
| Provider | `GITHUB_ACTIONS` |
| Build Number | `GITHUB_RUN_NUMBER` |
| Build ID | `GITHUB_RUN_ID` |
| Branch | `GITHUB_HEAD_REF` (PR) or `GITHUB_REF_NAME` |
| Commit SHA | `GITHUB_SHA` |
| Repository | `GITHUB_REPOSITORY` |
| Actor | `GITHUB_ACTOR` |
| Job Name | `GITHUB_JOB` |
| Agent Name | `RUNNER_NAME` |
| Build URL | Synthesized from `GITHUB_SERVER_URL`, `GITHUB_REPOSITORY`, `GITHUB_RUN_ID` |

### Jenkins

| Field | Environment variable |
|---|---|
| Provider | `JENKINS_URL` |
| Build Number | `BUILD_NUMBER` |
| Build ID | `BUILD_ID` |
| Branch | `GIT_BRANCH` (strips `refs/heads/` prefix) |
| Commit SHA | `GIT_COMMIT` |
| Build URL | `BUILD_URL` |
| Job Name | `JOB_NAME` |
| Pull Request | `CHANGE_ID` |
| Agent Name | `NODE_NAME` |

### GitLab CI

| Field | Environment variable |
|---|---|
| Provider | `GITLAB_CI` |
| Build Number | `CI_PIPELINE_IID` |
| Build ID | `CI_PIPELINE_ID` |
| Branch | `CI_COMMIT_REF_NAME` |
| Commit SHA | `CI_COMMIT_SHA` |
| Commit Message | `CI_COMMIT_MESSAGE` |
| Build URL | `CI_PIPELINE_URL` |
| Job Name | `CI_JOB_NAME` |
| Pull Request | `CI_MERGE_REQUEST_IID` |
| Repository | `CI_PROJECT_PATH` |
| Actor | `GITLAB_USER_LOGIN` |
| Agent Name | `CI_RUNNER_DESCRIPTION` |
| Environment | `CI_ENVIRONMENT_NAME` |

### CircleCI

| Field | Environment variable |
|---|---|
| Provider | `CIRCLECI` |
| Build Number | `CIRCLE_BUILD_NUM` |
| Build ID | `CIRCLE_WORKFLOW_ID` |
| Branch | `CIRCLE_BRANCH` |
| Commit SHA | `CIRCLE_SHA1` |
| Build URL | `CIRCLE_BUILD_URL` |
| Job Name | `CIRCLE_JOB` |
| Pull Request | `CIRCLE_PR_NUMBER` |
| Repository | `CIRCLE_REPOSITORY_URL` |
| Actor | `CIRCLE_USERNAME` |

### Travis CI

| Field | Environment variable |
|---|---|
| Provider | `TRAVIS` |
| Build Number | `TRAVIS_BUILD_NUMBER` |
| Build ID | `TRAVIS_BUILD_ID` |
| Branch | `TRAVIS_BRANCH` |
| Commit SHA | `TRAVIS_COMMIT` |
| Build URL | `TRAVIS_BUILD_WEB_URL` |
| Job Name | `TRAVIS_JOB_NAME` |
| Pull Request | `TRAVIS_PULL_REQUEST` (`false` → omitted) |
| Repository | `TRAVIS_REPO_SLUG` |
| Actor | `TRAVIS_PULL_REQUEST_AUTHOR` |

### TeamCity

| Field | Environment variable |
|---|---|
| Provider | `TEAMCITY_VERSION` |
| Build Number | `BUILD_NUMBER` |
| Build ID | `TEAMCITY_BUILD_ID` |
| Branch | `BRANCH_NAME` |
| Commit SHA | `BUILD_VCS_NUMBER` |
| Build URL | `BUILD_URL` |
| Job Name | `TEAMCITY_BUILDCONF_NAME` |
| Agent Name | `AGENT_NAME` |

### Bitbucket Pipelines

| Field | Environment variable |
|---|---|
| Provider | `BITBUCKET_BUILD_NUMBER` |
| Build Number | `BITBUCKET_BUILD_NUMBER` |
| Build ID | `BITBUCKET_PIPELINE_UUID` |
| Branch | `BITBUCKET_BRANCH` |
| Commit SHA | `BITBUCKET_COMMIT` |
| Commit Message | `BITBUCKET_COMMIT_MESSAGE` |
| Build URL | `BITBUCKET_BUILD_URL` |
| Job Name | `BITBUCKET_STEP_UUID` |
| Pull Request | `BITBUCKET_PR_ID` |
| Repository | `BITBUCKET_REPO_FULL_NAME` |

---

## Consuming metadata downstream

### Metrics JSON

```json title="target/testfly-metrics.json"
{
  "ci": {
    "provider": "GitHub Actions",
    "buildNumber": "42",
    "branch": "main",
    "commitSha": "abc123",
    "buildUrl": "https://github.com/hakanngul/testfly/actions/runs/123"
  }
}
```

### JUnit XML

```xml title="target/surefire-reports/TEST-TestFly.xml"
<testsuite ...>
  <properties>
    <property name="provider" value="GitHub Actions"/>
    <property name="buildNumber" value="42"/>
    ...
  </properties>
  ...
</testsuite>
```

These properties are picked up by Jenkins, GitHub Actions test reporters, SonarQube, and many other CI tools.
