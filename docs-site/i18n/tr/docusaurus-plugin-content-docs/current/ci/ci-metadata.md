---
description: "TestFly'da CI/CD meta veri algılama: GitHub Actions, Jenkins, GitLab CI, CircleCI, Travis, TeamCity ve Bitbucket Pipelines'tan otomatik olarak yakalanan sağlayıcı, derleme numarası, dal, commit, derleme URL'si ve eylemci."
id: ci-metadata
title: CI Meta Verileri
sidebar_position: 4
---

# CI Meta Verileri

TestFly, iyi bilinen CI/CD ortamlarını algılar ve pipeline çalıştırması hakkında yapılandırılmış meta verileri yakalar. Bu meta veriler şuraya gömülür:

- `target/testfly-metrics.json` — üst düzey `ci` nesnesi
- `target/testfly-report.html` — pipeline'a geri bağlantı içeren **Derleme Meta Verileri** kartı
- `target/surefire-reports/TEST-TestFly.xml` — JUnit XML'i ayrıştıran araçlar için `<properties>` bloğu

Çoğu durumda manuel yapılandırma gerekmez.

---

## Desteklenen sağlayıcılar

| Sağlayıcı | Algılama değişkeni |
|---|---|
| GitHub Actions | `GITHUB_ACTIONS` |
| Jenkins | `JENKINS_URL` |
| GitLab CI | `GITLAB_CI` |
| CircleCI | `CIRCLECI` |
| Travis CI | `TRAVIS` |
| TeamCity | `TEAMCITY_VERSION` |
| Bitbucket Pipelines | `BITBUCKET_BUILD_NUMBER` |
| Genel CI | `CI` |

---

## Yakalanan alanlar

| Alan | Örnek | Notlar |
|---|---|---|
| `provider` | `GitHub Actions` | İnsan tarafından okunabilir CI adı |
| `buildNumber` | `42` | Görüntülenecek derleme numarası |
| `buildId` | `123456789` | Dahili çalıştırma/pipeline kimliği |
| `branch` | `feature/ci-meta` | Uygun olduğunda pull request dalı tercih edilir |
| `commitSha` | `abc123def` | CI ortamındaki tam SHA, sağlanırsa |
| `commitMessage` | `Add CI metadata` | Yalnızca GitLab/Bitbucket'te kullanılabilir |
| `buildUrl` | `https://github.com/.../actions/runs/123` | Pipeline'a geri bağlantı |
| `jobName` | `unit-tests` | CI iş/aşama adı |
| `pullRequest` | `7` | PR/MR tanımlayıcısı, mevcutsa |
| `repository` | `testfly/testfly` | Depo kısa adı veya URL'si |
| `actor` | `hagul` | Derlemeyi tetikleyen kullanıcı |
| `agentName` | `agent-01` | Çalıştırıcı/aracı adı |
| `environment` | `staging` | Dağıtım ortamı adı, ayarlanmışsa |

Bir sağlayıcı için kullanılamayan alanlar, boş gösterilmek yerine atlanır.

---

## Yapılandırma

Bir CI ortamı algılandığında yakalama **otomatik olarak etkinleştirilir**. Açıkça devre dışı bırakmak için:

```yaml title="testfly.yml"
ci:
  captureMetadata: false
```

CI dışında bile zorla açmak için:

```yaml title="testfly.yml"
ci:
  captureMetadata: true
```

---

## Sağlayıcı değişken eşlemesi

### GitHub Actions

| Alan | Ortam değişkeni |
|---|---|
| Sağlayıcı | `GITHUB_ACTIONS` |
| Derleme Numarası | `GITHUB_RUN_NUMBER` |
| Derleme Kimliği | `GITHUB_RUN_ID` |
| Dal | `GITHUB_HEAD_REF` (PR) veya `GITHUB_REF_NAME` |
| Commit SHA | `GITHUB_SHA` |
| Depo | `GITHUB_REPOSITORY` |
| Eylemci | `GITHUB_ACTOR` |
| İş Adı | `GITHUB_JOB` |
| Aracı Adı | `RUNNER_NAME` |
| Derleme URL'si | `GITHUB_SERVER_URL`, `GITHUB_REPOSITORY`, `GITHUB_RUN_ID` değerlerinden üretilir |

### Jenkins

| Alan | Ortam değişkeni |
|---|---|
| Sağlayıcı | `JENKINS_URL` |
| Derleme Numarası | `BUILD_NUMBER` |
| Derleme Kimliği | `BUILD_ID` |
| Dal | `GIT_BRANCH` (`refs/heads/` önekini kaldırır) |
| Commit SHA | `GIT_COMMIT` |
| Derleme URL'si | `BUILD_URL` |
| İş Adı | `JOB_NAME` |
| Pull Request | `CHANGE_ID` |
| Aracı Adı | `NODE_NAME` |

### GitLab CI

| Alan | Ortam değişkeni |
|---|---|
| Sağlayıcı | `GITLAB_CI` |
| Derleme Numarası | `CI_PIPELINE_IID` |
| Derleme Kimliği | `CI_PIPELINE_ID` |
| Dal | `CI_COMMIT_REF_NAME` |
| Commit SHA | `CI_COMMIT_SHA` |
| Commit Mesajı | `CI_COMMIT_MESSAGE` |
| Derleme URL'si | `CI_PIPELINE_URL` |
| İş Adı | `CI_JOB_NAME` |
| Pull Request | `CI_MERGE_REQUEST_IID` |
| Depo | `CI_PROJECT_PATH` |
| Eylemci | `GITLAB_USER_LOGIN` |
| Aracı Adı | `CI_RUNNER_DESCRIPTION` |
| Ortam | `CI_ENVIRONMENT_NAME` |

### CircleCI

| Alan | Ortam değişkeni |
|---|---|
| Sağlayıcı | `CIRCLECI` |
| Derleme Numarası | `CIRCLE_BUILD_NUM` |
| Derleme Kimliği | `CIRCLE_WORKFLOW_ID` |
| Dal | `CIRCLE_BRANCH` |
| Commit SHA | `CIRCLE_SHA1` |
| Derleme URL'si | `CIRCLE_BUILD_URL` |
| İş Adı | `CIRCLE_JOB` |
| Pull Request | `CIRCLE_PR_NUMBER` |
| Depo | `CIRCLE_REPOSITORY_URL` |
| Eylemci | `CIRCLE_USERNAME` |

### Travis CI

| Alan | Ortam değişkeni |
|---|---|
| Sağlayıcı | `TRAVIS` |
| Derleme Numarası | `TRAVIS_BUILD_NUMBER` |
| Derleme Kimliği | `TRAVIS_BUILD_ID` |
| Dal | `TRAVIS_BRANCH` |
| Commit SHA | `TRAVIS_COMMIT` |
| Derleme URL'si | `TRAVIS_BUILD_WEB_URL` |
| İş Adı | `TRAVIS_JOB_NAME` |
| Pull Request | `TRAVIS_PULL_REQUEST` (`false` → atlanır) |
| Depo | `TRAVIS_REPO_SLUG` |
| Eylemci | `TRAVIS_PULL_REQUEST_AUTHOR` |

### TeamCity

| Alan | Ortam değişkeni |
|---|---|
| Sağlayıcı | `TEAMCITY_VERSION` |
| Derleme Numarası | `BUILD_NUMBER` |
| Derleme Kimliği | `TEAMCITY_BUILD_ID` |
| Dal | `BRANCH_NAME` |
| Commit SHA | `BUILD_VCS_NUMBER` |
| Derleme URL'si | `BUILD_URL` |
| İş Adı | `TEAMCITY_BUILDCONF_NAME` |
| Aracı Adı | `AGENT_NAME` |

### Bitbucket Pipelines

| Alan | Ortam değişkeni |
|---|---|
| Sağlayıcı | `BITBUCKET_BUILD_NUMBER` |
| Derleme Numarası | `BITBUCKET_BUILD_NUMBER` |
| Derleme Kimliği | `BITBUCKET_PIPELINE_UUID` |
| Dal | `BITBUCKET_BRANCH` |
| Commit SHA | `BITBUCKET_COMMIT` |
| Commit Mesajı | `BITBUCKET_COMMIT_MESSAGE` |
| Derleme URL'si | `BITBUCKET_BUILD_URL` |
| İş Adı | `BITBUCKET_STEP_UUID` |
| Pull Request | `BITBUCKET_PR_ID` |
| Depo | `BITBUCKET_REPO_FULL_NAME` |

---

## Meta verileri aşağı akışta tüketme

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

Bu özellikler Jenkins, GitHub Actions test raporlayıcıları, SonarQube ve daha birçok CI aracı tarafından alınır.