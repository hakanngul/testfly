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
- **Konu:** ScanRepo False-Positive İyileştirmeleri ve Tag Tabanlı Git Sürüm Kuralı.
- **Durum:** `.gitattributes` eklendi, `index.js` modülerleştirildi (yüksek entropi 51->1'e düştü). `[[rules/git-release-workflow]]` oluşturulup anayasaya bağlandı.

### 2. Anlık Bağlam ve Kararlar (Immediate Context)
- **Güvenlik Taraması Çözümü:** `axe.min.js`, `package-lock.json`, `.agents/**` dosyaları `.gitattributes` ile linguist-vendored/generated olarak işaretlendi. `docs-site` derlemesi (en & tr) başarıyla doğrulandı.
- **Git Commit & Tag Kuralı:** Ajanın körlemesine commit/push yapması yasaklandı. "commit at" dendiğinde değişiklikler özetlenip sürüm tipi (SemVer), tag ve checklist soruları sorularak açık onay alınacak.

### 3. Sıradaki Görevler (Next Up)
- [x] ScanRepo false-positive giderme (Adım 3 & 4: homeData.js ve .gitattributes).
- [x] `[[rules/git-release-workflow]]` kuralının anayasaya ve haritaya eklenmesi.
- [ ] Kullanıcı onay verirse `development` dalındaki değişiklikleri teyit edip sürüm sürecini başlatmak.

### 4. Hızlı Notlar (Scratch Notes)
- TestFly mimari kararları ve dokümantasyon grafiği `[[wiki/index]]` altında günceldir.
- Karakter sınırı <= 2.200 kuralına tam uyuldu (~1.680 karakter).
