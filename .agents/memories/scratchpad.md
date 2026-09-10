---
tags:
  - memory
  - scratchpad
  - ephemeral
date: 2026-09-10
status: active
char_limit: 2200
---

# Aktif Çalışma Not Defteri (Scratchpad)

> [!WARNING]
> **2.200 Karakter Kuralı:** Bu dosya oturumlar arası anlık bağlamı tutar. Karakter sayısı 2.200'ü aştığında `[[skills/memory-sync/SKILL]]` çalıştırılarak tamamlanan işler budanmalı, kalıcı kararlar `[[wiki/index]]` altına aktarılmalıdır.

---

### 1. Aktif Odak ve Son Durum (Current Focus)
- **Konu:** TestFly API ve WebUI test veri akışlarının ayrı ayrı Archify ile modellenmesi ve teslimi.
- **Durum:** `testfly-api-dataflow.html` ve `testfly-webui-dataflow.html` şemaları 9 showcase kontrolünden 0 hatayla geçti. `[[wiki/api-testing]]`, `[[wiki/webui-testing]]`, `wiki/index.md` ve `MAP.md` güncellendi.

### 2. Anlık Bağlam ve Kararlar (Immediate Context)
- API Akışı: TestNG & Auth -> ApiClient -> Dispatch -> ApiResponse -> Schema/Response Assert (5 aşama).
- WebUI Akışı: Config -> DriverManager -> WebDriver/BasePage -> SmartLocator/WaitEngine -> Assert/Report (5 aşama).
- Her iki akış için bağımsız interaktif HTML diyagramları `docs-site/static/diagrams/` altına yerleştirildi.

### 3. Tamamlanan Görevler (Completed Tasks)
- [x] Archify kurulumu ve ortam doğrulaması.
- [x] TestFly çekirdek mimari diyagramı (`testfly-architecture.html`).
- [x] TestFly K6 yük testi veri akışı diyagramı (`testfly-k6-dataflow.html`).
- [x] TestFly API test veri akışı diyagramı (`testfly-api-dataflow.html`).
- [x] TestFly WebUI test veri akışı diyagramı (`testfly-webui-dataflow.html`).
- [x] `[[wiki/api-testing]]` ve `[[wiki/webui-testing]]` bilgi sayfalarının oluşturulması, `MAP.md`'ye bağlanması.

### 4. Hızlı Notlar (Scratch Notes)
- Obsidian Graph View'da tüm sayfalar çift yönlü `[[...]]` standartlarına bağlıdır.
- Docusaurus derlemesi (`npm run build`) diyagramları static varlık olarak sorunsuz paketler.
