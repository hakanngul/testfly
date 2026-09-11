---
tags:
  - wiki
  - roadmap
  - architecture
  - autonomous
date: 2026-09-10
status: active
type: wiki
---

# TestFly Geliştirme Yol Haritası (Roadmap)

TestFly projesinin tamamlanan aşamaları ve planlanan gelecek nesil otonom yetenekleri bu sayfada sentezlenmiştir.

---

## Tamamlanan Temel Fazlar (v0.1 - v1.1)

- **Phase 0-1 (Core Foundation):** ThreadLocal WebDriver yaşam döngüsü, `BaseTest`, `BasePage`, `WaitEngine` ve HTML raporlama.
- **Phase 2 (Gözlemlenebilirlik):** `@Retryable`, flakiness skoru, ekran görüntüleri ve zamanlama metrikleri.
- **Phase 3 (Genişletilebilirlik):** Java SPI eklentileri (`NamedDriverProvider`, `ReportAdapter`, `ExecutionHook`, `TestFlyPlugin`).
- **Phase 4-5 (CI/CD ve Ekosistem):** Ortam algılama (`CiEnvironmentDetector`), `BuildThresholdEnforcer`, TestNG/JUnit5/Cucumber desteği.

---

## Phase 6 — Otonom Test & Dağıtık Akıllı Koşum (v1.2+)

| Özellik | Durum | Kapsam |
|:---|:---:|:---|
| **TestFly CLI & Scaffolder (`testfly init`)** | ✅ Tamamlandı | TestNG, JUnit 5 ve Cucumber için tek komutla üretime hazır proje iskeleti oluşturan CLI aracı. |
| **Smart Test Sharder (LPT Bin-Packing)** | ✅ Tamamlandı | Test geçmişi metriklerini analiz ederek testleri en az yüklü worker'lara matematiksel optimumda dağıtan CI motoru. |
| **TestFly Autonomous Explorer (`testfly explore`)** | ⏳ Sırada | Web uygulamasını otonom tarayarak formları ve akışları keşfeden, sıfırdan Page Object ve test sınıfları üreten AI ajanı. |
| **AI Root-Cause & Auto-Fix Analyzer** | ⏳ Planlandı | Test patladığında CDP ağ/konsol logları ve DOM geçmişini inceleyerek hata kök nedenini belirleyen ve PR düzeltme önerisi sunan analiz motoru. |
| **Visual Layout Shift & Smart UI AI** | ⏳ Planlandı | Piksel yerine görsel düzen kaymalarını ve taşmaları algılayan yapay zeka destekli görsel regresyon motoru. |
| **Synthetic Test Data Factory (`@TestDataFactory`)** | ⏳ Planlandı | Testler için yerelleştirilmiş (TC kimlik, kart, adres) ve dinamik mock verisi üreten dahili veri fabrikası. |

---

## İlgili Bağlantılar
- CI Dağıtım Motoru: `[[wiki/ci-quality-gates]]`
- Yapay Zeka & MCP: `[[wiki/ai-mcp-automation]]`
- Proje Yol Haritası (Kök): `[[ROADMAP]]`
- Ana Harita: `[[MAP]]`
