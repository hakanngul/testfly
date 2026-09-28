---
tags:
  - memory
  - log
  - chronological
date: 2026-09-28
status: active
type: log
---

# Wiki Kronolojik Günlük (Log)

Append-only kayıt. Her giriş `## [YYYY-MM-DD] eylem | konu` formatındadır.
Arama: `grep "^## \[" memories/log.md | tail -10`

---

## [2026-09-28] init | LLM Wiki Log Dosyası Oluşturuldu
- **Eylem:** `memories/log.md` dosyası LLM Wiki pattern audit sonrası oluşturuldu.
- **Kapsam:** 5 eksiklik tespit edildi; log.md, app.json, graph renk grupları, wiki-lint skill, MAP referansı.
- **Bağlantılar:** [[MAP]], [[wiki/index]], [[memories/scratchpad]]

## [2026-09-28] ingest | Mevcut Wiki Yapısı Geriye Dönük Kayıt
- **Eylem:** Mevcut 17 wiki sayfası, 3 rule, soul.md, MAP.md ve scratchpad.md'nin oluşturulma geçmişi geriye dönük kaydedildi.
- **Wiki Sayfaları (2026-09-10):** architecture, webdriver-lifecycle, api-testing, webui-testing, api-webui-testing, load-testing, spi-extensions, ai-mcp-automation, accessibility-visual-testing, cucumber-bdd, test-management, quarantine-engine, ci-quality-gates, memory-system, roadmap, configuration, index.
- **Kurallar (2026-09-10):** memory-protocol, docusaurus-workflow, git-release-workflow.
- **Bağlantılar:** [[wiki/index]], [[MAP]]

## [2026-09-27] update | OutOfMemoryError Çözümü Scratchpad'e Kaydedildi
- **Eylem:** RecordingManager bellek optimizasyonu (%99 düşüş) tamamlandı, TestFly 1.0.5 olarak yerel deploy edildi.
- **Bağlantılar:** [[memories/scratchpad]], [[wiki/roadmap]]

## [2026-09-28] feature | Cucumber CLI Rapor Tetikleme & Timeline INFO/DEBUG Filtre Butonu
- **Eylem:** 
  1. `CucumberHooks.afterAllScenarios()` metoduna TestNG `SuiteExecutionListener` ile eşdeğer rapor üretim zinciri (`ReportAdapterRegistry.generateAll()`, `JUnitXmlReporter.export()`, metrikler) eklendi. Cucumber CLI doğrudan çalıştırıldığında da HTML rapor üretimi sağlandı.
  2. `report-template.html` içerisindeki Step Execution Timeline bölümüne INFO/DEBUG adımlarını gizleyip açan modern hap (pill) toggle butonu (`Hide INFO / DEBUG` / `Show INFO / DEBUG`), sayaç rozeti, `localStorage` kalıcılığı ve üst çubuk genel kontrol butonu eklendi.
- **Bağlantılar:** [[io.testfly.cucumber.CucumberHooks]], [[io.testfly.reporting.HtmlReportGenerator]], [[memories/scratchpad]]

