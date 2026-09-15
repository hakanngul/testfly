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
- **Konu:** Changelog Modernizasyonu & Release 1.0.5 Docusaurus Senkronizasyonu.
- **Durum:** Kök `CHANGELOG.md`, `docs-site/docs/changelog.md` ve `docs-site/i18n/tr/.../changelog.md` [1.0.5] ve [1.0.4] maddeleriyle güncellendi. `npm run build` ile çift dil doğrulandı ve `development` dalına pushlandı.

### 2. Anlık Bağlam ve Kararlar (Immediate Context)
- **1.0.5 Güncellemeleri:** Interactive Recorder & Web Studio (`:8765`), TestFly MCP Server (88 araç), SmartTestSharder (LPT), Gatling/VT Yük Testi, Cupertino HTML Rapor tasarımı ve ScanRepo güvenlik optimizasyonları changelog'lara işlendi.
- **Docusaurus Uyumluluğu:** MDX v3 JSX hatası önlendi (`<50ms` -> `` `<50ms` ``), EN & TR derlemeleri 0 hata ile tamamlandı.

### 3. Sıradaki Görevler (Next Up)
- [x] ScanRepo false-positive giderme (homeData.js ve .gitattributes).
- [x] Changelog modernizasyonu (Kök + Docusaurus EN/TR).
- [x] Sürüm 1.0.5 hazırlığı ve commit/push (`development`).

### 4. Hızlı Notlar (Scratch Notes)
- TestFly mimari kararları ve dokümantasyon grafiği `[[wiki/index]]` altında günceldir.
- Karakter sınırı <= 2.200 kuralına tam uyuldu (~1.650 karakter).

