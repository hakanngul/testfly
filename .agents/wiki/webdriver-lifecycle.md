---
tags:
  - wiki
  - webdriver
  - lifecycle
  - concurrency
date: 2026-09-10
status: active
type: wiki
---

# WebDriver Yaşam Döngüsü (Lifecycle)

TestFly içerisinde `WebDriver` nesneleri framework tarafından yönetilir. Kullanıcının `driver.quit()` veya `driver.close()` çağrılarını elle yapması gerekmez.

---

## 1. Yaşam Döngüsü Modları
`testfly.yml` dosyasında `browser.lifecycle` altında belirlenir:
- `per-test`: Her `@Test` metodu için yeni ve temiz bir tarayıcı oturumu açılır, test bitiminde kapatılır (Varsayılan ve en güvenli mod).
- `per-suite`: Tüm test paketi boyunca tek bir tarayıcı oturumu açık tutulur.

## 2. Paralel Koşum Güvenliği
- Sürücüler `ThreadLocal<WebDriver>` ile saklanır.
- Asla statik global sürücü örneği tutulmaz.
- TestNG veya JUnit 5 ile paralel çalışırken her thread kendi sürücüsüne sahiptir.

---

## İlgili Bağlantılar
- Mimari: `[[wiki/architecture]]`
- Yapılandırma: `[[wiki/configuration]]`
- Wiki Dizin: `[[wiki/index]]`
- Harita: `[[MAP]]`
