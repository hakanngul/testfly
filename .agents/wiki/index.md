---
tags:
  - wiki
  - index
  - knowledge-base
date: 2026-09-10
status: active
type: wiki
---

# LLM Wiki — Bilgi Deposu (Knowledge Base)

TestFly projesinin kalıcı hafıza ve mimari kararlar deposudur. 
Bu sayfalar ham sohbet logları içermez; doğrudan projeyle ilgili damıtılmış kuralları, mimari tercihleri ve domain bilgisini barındırır.

Tüm sayfalar birbirine ve `[[MAP]]` ana haritasına çift yönlü bağlantılarla (`[[...]]`) bağlıdır.

---

## 📚 Kategori Dizinleri

### 1. Sistem ve Mimari (Architecture)
- `[[wiki/architecture]]` — TestFly'ın temel mimarisi, katmanlı tasarım ve modül sınırları.
- `[[wiki/webdriver-lifecycle]]` — ThreadLocal izole WebDriver yaşam döngüsü, yönetim kuralları.
- `[[wiki/api-testing]]` — REST & GraphQL API test veri akışı ve mimarisi.
- `[[wiki/webui-testing]]` — WebUI, SmartLocator ve WaitEngine veri akışı ve mimarisi.
- `[[wiki/api-webui-testing]]` — API ve WebUI hibrit test veri akışı ve entegrasyonu.
- `[[wiki/load-testing]]` — K6 ve Gatling yük testi veri akışı ve mimarisi.
- `[[wiki/spi-extensions]]` — Java SPI eklenti mimarisi (Driver, Report, Hook, Plugin).
- `[[wiki/ai-mcp-automation]]` — testfly-mcp AI sunucusu, araçlar ve self-healing seçiciler.

### 2. Test Yöntemleri ve Kalite (Testing & Quality)
- `[[wiki/accessibility-visual-testing]]` — axe-core WCAG 2.2 AA ve VisualAssert piksel regresyonu.
- `[[wiki/cucumber-bdd]]` — Cucumber 7 BDD entegrasyonu ve BaseCucumberSteps.
- `[[wiki/test-management]]` — TestRail ve Xray otomatik sonuç senkronizasyonu.

### 3. Güvenilirlik ve CI/CD (Reliability & DevOps)
- `[[wiki/quarantine-engine]]` — Flakiness karantina motoru, risk puanlama ve @Retryable.
- `[[wiki/ci-quality-gates]]` — CI kalite kapıları, BuildThresholdEnforcer ve ortam tespiti.

### 4. Kalıcı Hafıza ve Ajan Altyapısı (Agent & Memory)
- `[[wiki/memory-system]]` — "2 Yol & 3 Parça", TestFly hafıza döngüsü ve Obsidian Graph yapısı.
- `[[rules/memory-protocol]]` — Ajanlar için bağlayıcı hafıza kuralları.

### 5. Yapılandırma ve Ortamlar (Configuration)
- `[[wiki/configuration]]` — `testfly.yml`, profil yönetimi ve ortam değişkenleri.

---

## 🔗 Hızlı Gezinti
- Ana Harita: `[[MAP]]`
- Ajan Anayasası: `[[AGENTS]]`
- Ajan Ruhu: `[[soul]]`
- Çalışma Masası: `[[memories/scratchpad]]`
- Hafıza Senkronizasyonu: `[[skills/memory-sync/SKILL]]`
