---
tags:
  - wiki
  - configuration
  - testfly-yml
date: 2026-09-10
status: active
type: wiki
---

# TestFly Yapılandırma Sistemi (Configuration)

TestFly projesinin temel yapılandırma dosyası proje kökündeki `testfly.yml` dosyasıdır.

---

## 1. Temel Yapı
Asgari geçerli konfigürasyon:
```yaml
execution:
  mode: local
  baseUrl: https://example.com

browser:
  name: chrome

timeouts:
  explicit: 10
  pageLoad: 30
```

## 2. Ortam Değişkenleri ve Profiller
- Şifreler ve gizli anahtarlar YAML dosyasına yazılmaz; `${ENV_VAR}` biçiminde ortam değişkeninden çekilir.
- `-Dtestfly.profile=staging` bayrağı ile `testfly-staging.yml` dosyası devreye alınır.

---

## İlgili Bağlantılar
- Mimari: `[[wiki/architecture]]`
- Ana Harita: `[[MAP]]`
- Wiki Dizin: `[[wiki/index]]`
