---
tags:
  - memory
  - scratchpad
  - ephemeral
date: 2026-09-29
status: active
char_limit: 2200
---

# Aktif Çalışma Not Defteri (Scratchpad)

### 1. Aktif Odak ve Son Durum
- **Konu:** Release v1.0.7 & Git Commit Hazırlığı
- **Durum:** HAZIR. Tüm changelog ve sürüm referansları 1.0.7'ye yükseltildi. Docusaurus derlemesi ve TestNG testleri sıfır hata ile doğrulandı.
- **Yapılanlar:**
  1. **Versiyon Yükseltme (1.0.7):** `pom.xml`, `README.md`, `CHANGELOG.md`, `docs-site` sayfaları (homeData, index, getting-started, junit5, loadtest) `1.0.7` olarak senkronize edildi.
  2. **Changelog Senkronizasyonu:** Kök `CHANGELOG.md`, `docs-site/docs/changelog.md` ve TR eşdeğeri `[1.0.7] — 2026-09-29` başlığı ile güncellendi.
  3. **Çekirdek & Doküman:** `Locator.cssSelector(String)` birincil metod yapıldı, `Locator.css` `@Deprecated` alias oldu. Dokümanlar Java 21 LTS standardına getirildi.
  4. **Doğrulama:** `npm run build` (EN & TR) ve `mvn test` (1319 test) hatasız tamamlandı.

### 2. Kaynaklar & Bağlantılar
- [[wiki/assertion-system]]
- [[wiki/architecture]]
- [[wiki/webui-testing]]
- [[MAP]]
