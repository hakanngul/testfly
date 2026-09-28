---
tags:
  - memory
  - scratchpad
  - ephemeral
date: 2026-09-27
status: active
char_limit: 2200
---

# Aktif Çalışma Not Defteri (Scratchpad)

### 1. Aktif Odak ve Son Durum
- **Konu:** Cucumber CLI Rapor Tetikleme & Timeline INFO/DEBUG Filtreleme
- **Durum:** TAMAMLANDI & LOCAL DEPLOY EDİLDİ (TestFly 1.1.0).
- **Kazanımlar:**
  1. `CucumberHooks`: `@AfterAll` hook'una TestNG'deki gibi metrics/report üretim zinciri (`ReportAdapterRegistry.generateAll()`, `JUnitXmlReporter.export()`) eklendi. Artık doğrudan Cucumber CLI veya IDE üzerinden koşturulduğunda da HTML rapor üretiliyor.
  2. `report-template.html`: Step Execution Timeline başlığına her test için bağımsız çalışan, `localStorage` ile durumunu hatırlayan `Hide INFO / DEBUG` / `Show INFO / DEBUG` toggle butonu eklendi. Üst çubuğa da tüm testler için genel toggle butonu yerleştirildi.
  3. `mvn test` ve yerel `mvn install` başarıyla tamamlandı.

### 2. Kaynaklar & Bağlantılar
- [[io.testfly.cucumber.CucumberHooks]]
- [[io.testfly.reporting.HtmlReportGenerator]]
- [[report-template.html]]


