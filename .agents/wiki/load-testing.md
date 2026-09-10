---
tags:
  - wiki
  - loadtest
  - k6
  - gatling
  - dataflow
date: 2026-09-10
status: active
type: wiki
---

# K6 ve Gatling Yük Testi Mimarisi (Load Testing)

TestFly, yüksek eşzamanlılıklı (high-concurrency) API ve web yük testlerini harici araç çalıştırma karmaşıklığı olmadan doğrudan Java testleri içerisinden koşturmayı sağlar.

---

## 1. Temel Bileşenler
- **`LoadScenario` DSL:** Çok adımlı kullanıcı gezintilerini (`step("Home").get("/").and().step("News").get("/news.php")`) akıcı API ile tanımlar.
- **`@LoadTest` Anotasyonu:** Sınıf veya metot düzeyinde sanal kullanıcı sayısı (`users`), ısınma süresi (`rampUp`) ve tutma süresi (`hold`) belirler.
- **`LoadTestFeeder`:** CSV, JSON veya dinamik veri kaynaklarından yük testine parametreli veri enjekte eder.
- **`LoadTestAssert`:** Yük testi sonrası p50, p90, p95, p99 gecikme sınırlarını (`assertP95Below`) ve hata oranlarını (`assertErrorRateBelow`) doğrular.

---

## 2. İnteraktif Veri Akışı Şeması (Archify Dataflow)
Archify ile derlenmiş interaktif, izlenebilir ve sunum modu destekli veri akışı şeması:
- [TestFly K6 Load Test Dataflow Diagram (HTML)](file:///Users/hagul/Projects/TestFramework/testfly/docs-site/static/diagrams/testfly-k6-dataflow.html)

---

## İlgili Bağlantılar
- Mimari: `[[wiki/architecture]]`
- Ana Harita: `[[MAP]]`
- Wiki Dizin: `[[wiki/index]]`
- Archify Becerisi: `[[skills/archify/SKILL]]`
