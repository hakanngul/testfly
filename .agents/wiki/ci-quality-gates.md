---
tags:
  - wiki
  - ci-cd
  - quality-gates
  - thresholds
  - devops
date: 2026-09-10
status: active
type: wiki
---

# CI/CD Kalite Kapıları ve Otomasyon Entegrasyonu

TestFly, sürekli entegrasyon hatlarında (GitHub Actions, Jenkins, Bitbucket Pipelines) test sonuçlarını otomatik değerlendiren ve kabul kriterleri sağlanmadığında derlemeyi kıran kalite kapılarına sahiptir.

---

## 1. Eşik Değer Denetleyicisi (`BuildThresholdEnforcer`)

`testfly.yml` üzerinden başarı oranı ve flaky test sınırları belirlenir:

```yaml
ci:
  failOnPassRateBelow: 95.0 # Başarı oranı %95'in altına inerse build fail edilir
  maxFlakyTests: 2          # 2'den fazla flaky test tespit edilirse build kırılır
```

---

## 2. Otomatik Ortam Algılama (`CiEnvironmentDetector`)

TestFly, çalıştığı ortamı sistem değişkenlerinden otomatik olarak tanır:
- **GitHub Actions:** `GITHUB_RUN_ID`, `GITHUB_SHA`, `GITHUB_REF`
- **Bitbucket Pipelines:** `BITBUCKET_BUILD_NUMBER`, `BITBUCKET_BRANCH`
- **Jenkins:** `BUILD_NUMBER`, `JOB_NAME`, `GIT_COMMIT`

Bu veriler TestFly HTML raporunun ve Allure/ReportPortal gönderimlerinin başlığına otomatik olarak işlenir.

---

## İlgili Bağlantılar
- Karantina Motoru: `[[wiki/quarantine-engine]]`
- Temel Mimari: `[[wiki/architecture]]`
- Ana Harita: `[[MAP]]`
- Wiki Dizin: `[[wiki/index]]`
