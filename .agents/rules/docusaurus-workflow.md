---
tags:
  - rule
  - docusaurus
  - documentation
  - constitution
date: 2026-09-10
status: active
type: rule
---

# Docusaurus Dokümantasyon ve Yapılandırma Kuralı (Docusaurus Workflow Rule)

> [!IMPORTANT]
> **Zorunlu Kural (Mandatory):** `docs-site` üzerinde herhangi bir dokümantasyon ekleme, güncelleme, `sidebars.js` düzenleme veya konfigürasyon değişikliği yapılacağı zaman her zaman `[[.agents/skills/docusaurus-config/SKILL]]` skill yönergelerine kesin olarak uyulmalıdır.

---

## 1. Temel İlkeler ve İş Akışı

1. **Skill Çağrımı:**
   - Dokümantasyon veya site ayarlarıyla ilgili bir işlem başlamadan önce `.agents/skills/docusaurus-config/SKILL.md` kuralları referans alınır.
2. **Çift Dil (i18n) Bütünlüğü:**
   - `docs-site/docs/` altına eklenen veya güncellenen her sayfanın Türkçe karşılığı `docs-site/i18n/tr/docusaurus-plugin-content-docs/current/` altına eşzamanlı olarak eklenmelidir.
3. **Konfigürasyon Doğrulama:**
   - `docs-site/docusaurus.config.js` üzerinde değişiklik yapıldığında:
     ```bash
     node .agents/skills/docusaurus-config/scripts/validate-config.js docs-site/docusaurus.config.js
     ```
     çalıştırılarak biçimlendirme, zorunlu alanlar (`title`, `url`, `baseUrl`) ve `customFields` şeması doğrulanmalıdır.
4. **Derleme Doğrulaması (Build Verification):**
   - Her dokümantasyon değişikliğinin ardından `docs-site` dizininde:
     ```bash
     npm run build
     ```
     çalıştırılarak hem `en` hem `tr` için sıfır hata ve sıfır kırık bağlantı (broken links) olduğu teyit edilmelidir.
