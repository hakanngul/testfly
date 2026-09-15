---
tags:
  - rule
  - git
  - release
  - semver
  - tagging
  - constitution
date: 2026-09-12
status: active
type: rule
---

# Otomatik Git Commit ve Tag Tabanlı Sürüm Politikası (Automated Git Release & Tag Policy)

> [!IMPORTANT]
> **Kural (Mandatory):** Kullanıcı "commit at" dediğinde gereksiz soru sorma. Doğrudan uzak depodaki (`origin`) en son tag'i kontrol et, sıradaki ardışık tag sürümünü belirle, proje dosyalarındaki versiyonları güncelle, commit oluştur ve push et.

---

## 1. Otomatik "Commit At" İş Akışı

Kullanıcı "commit at" dediğinde ajan şu adımları sırasıyla ve otonom olarak işletir:

1. **Uzak Depodaki Son Tag'i Bul:**
   ```bash
   git ls-remote --tags origin
   ```
   En son yayınlanan sürüm tespit edilir (Örn: `v1.0.4`).

2. **Sıradaki Tag Sürümünü Belirle:**
   En son tag'in ardışık sürümü hesaplanır (Örn: `v1.0.4` → `v1.0.5`).

3. **Versiyon Senkronizasyonunu Sağla:**
   * `pom.xml`: `<version>X.Y.Z</version>`
   * `CHANGELOG.md`: Yeni sürüm başlığı
   * `README.md` & `docs-site/src/data/homeData.js`: Versiyon referansları

4. **Test ve Derleme Doğrulaması:**
   * `mvn test` (sıfır hata)
   * `npm run build` (`docs-site` için, eğer doküman değiştiyse)

5. **Commit ve Push:**
   * Değişiklikleri `git add` ile sahnele.
   * Anlamlı bir conventional commit mesajı oluştur (Örn: `chore(release): bump version to 1.0.5 and prepare release`).
   * `development` dalına push et.
   * `main` dalı üzerinde oluşturulacak `vX.Y.Z` tag komutunu kullanıcıya hazır olarak sun.
