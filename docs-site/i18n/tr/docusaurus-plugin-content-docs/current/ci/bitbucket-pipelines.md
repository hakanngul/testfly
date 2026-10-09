---
description: "TestFly testlerini Bitbucket Pipelines üzerinde Java 21, headless Chrome ve rapor artifact'leri ile çalıştırın."
id: bitbucket-pipelines
title: Bitbucket Pipelines
sidebar_position: 3
---

# Bitbucket Pipelines

`CiEnvironmentDetector`, Bitbucket build numarası, branch ve commit metadata'sını tanır. Maven projesinin köküne aşağıdaki pipeline dosyasını ekleyin:

```yaml title="bitbucket-pipelines.yml"
image: maven:3.9.6-eclipse-temurin-21
pipelines:
  default:
    - step:
        name: TestFly Tests
        caches: [maven]
        script:
          - apt-get update && apt-get install -y wget gnupg
          - wget -q -O - https://dl-ssl.google.com/linux/linux_signing_key.pub | apt-key add -
          - sh -c 'echo "deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main" >> /etc/apt/sources.list.d/google.list'
          - apt-get update && apt-get install -y google-chrome-stable
          - mvn test -B
        artifacts:
          - target/testfly-report.html
          - target/surefire-reports/**
```

## Headless yapılandırma

Seçilen tam `testfly.yml` içinde tarayıcı ayarlarını tanımlayın. `-Dtestfly.browser.headless` otomatik override değildir.

```yaml title="testfly.yml"
execution:
  mode: local
  baseUrl: https://example.com
browser:
  name: chrome
  headless: true
  arguments: [--no-sandbox, --disable-dev-shm-usage]
```

## Kalite kapıları

```yaml
ci:
  failOnPassRateBelow: 95.0
  maxFlakyTests: 2
```

`BuildThresholdEnforcer`, eşikler ihlal edildiğinde koşumu başarısız yapar. Bitbucket, `target/surefire-reports/` altındaki JUnit XML sonuçlarını Test Results bölümünde gösterebilir.
