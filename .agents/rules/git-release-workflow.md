---
tags:
  - rule
  - git
  - release
  - semver
  - tagging
  - protected-branch
  - constitution
date: 2026-09-30
status: active
type: rule
---

# Git İş Akışı ve Protected Branch Politikası (Git Workflow & Protected Main Policy)

> [!CAUTION]
> ## 🚨 KIRMIZI ÇİZGİ: `main` DALINA COMMIT VE PUSH KESİNLİKLE YASAKTIR
> **`main` dalı korumalı (protected) üretim ve kararlı sürüm dalıdır.**
> 1. Ajanlar veya otomasyon araçları `main` dalına **DOĞRUDAN COMMIT ATAMAZ** ve **DOĞRUDAN PUSH YAPAMAZ**.
> 2. Tüm geliştirmeler, hata düzeltmeleri, özellik eklemeleri ve commit'ler istisnasız **`development`** dalında yürütülür.
> 3. Herhangi bir `git commit` veya `git push` komutundan önce **aktif dal kontrolü (`git branch --show-current`) ZORUNLUDUR**.

---

## 1. Zorunlu Ön Kontrol Protokolü (Pre-Flight Branch Check)

Herhangi bir dosyayı sahnelemeden (`git add`), commit oluşturmadan (`git commit`) veya push yapmadan (`git push`) önce **mutlaka** şu adım işletilir:

```bash
git branch --show-current
```

### Karar Matrisi:
* **Eğer çıktı `development` ise:** Normal iş akışına devam edilir.
* **Eğer çıktı `main` ise:**
  * 🛑 **DERHAL DUR! Kesinlikle commit veya push komutu çalıştırma.**
  * Değişiklikler henüz commit edilmediyse:
    ```bash
    git checkout development
    ```
    *(Gerekirse `git stash` -> `git checkout development` -> `git stash pop` kullanılır).*
  * Aktif dalın `development` olduğu teyit edildikten sonra işleme devam edilir.

---

## 2. Standart Geliştirme ve Commit Akışı

Tüm rutin geliştirme adımları aşağıdaki döngüyü takip eder:

1. **Aktif Dalı Doğrula:** `git branch --show-current` -> `development`
2. **Durumu İncele:** `git status -s`
3. **Sahnele:** `git add <dosyalar>` (Geçici, gereksiz ve gizli dosyalar eklenmez)
4. **Commit Oluştur:** Conventional Commits formatında açık ve anlamlı mesaj (Örn: `feat(api): ...`, `fix(wait): ...`, `refactor(driver): ...`)
5. **Push Et:** Sadece `origin/development` hedeflenir:
   ```bash
   git push origin development
   ```

---

## 3. Otomatik Sürüm ve "Commit At" İş Akışı

Kullanıcı "commit at" veya "sürüm hazırla" dediğinde ajan şu adımları sırasıyla ve otonom olarak işletir:

1. **Ön Kontrol:** `git branch --show-current` komutunun `development` döndürdüğünden emin ol.
2. **Uzak Depodaki Son Tag'i Bul:**
   ```bash
   git ls-remote --tags origin
   ```
   En son yayınlanan sürüm tespit edilir (Örn: `v1.0.6`).
3. **Sıradaki Tag Sürümünü Belirle:**
   En son tag'in ardışık sürümü hesaplanır (Örn: `v1.0.6` → `v1.0.7`).
4. **Versiyon Senkronizasyonunu Sağla:**
   * `pom.xml`: `<version>X.Y.Z</version>`
   * `CHANGELOG.md`: Yeni sürüm başlığı ve değişiklik özeti
   * `README.md` & `docs-site/src/data/homeData.js`: Versiyon referansları
   * Dokümantasyon kurulum pin'leri, `docs-site/src/pages/index.js` rozeti ve TR yansıları: tam liste için `AGENTS.md` içindeki "Version-bump checklist"
5. **Test ve Derleme Doğrulaması:**
   * `mvn test` (sıfır hata)
   * `npm run build` (`docs-site` için, dokümantasyon değiştiyse)
6. **Commit ve Push (`development`):**
   * Değişiklikleri `development` dalında commit'le:
     ```bash
     git commit -m "chore(release): bump version to X.Y.Z and prepare release"
     git push origin development
     ```
7. **Release & Tag Bilgilendirmesi:**
   * `main` dalı doğrudan güncellenmez.
   * `development` dalının `main`'e merge edilmesi ve `vX.Y.Z` tag'inin basılması için gereken komutlar kullanıcıya onay/bilgi olarak sunulur.

---

## 4. Acil Durum / Kaza Kurtarma Protokolü (Emergency Rollback)

Eğer bir insan hatası veya yanlış yönlendirme sonucu `main` dalına commit/push yapılmışsa:
1. **Panik Yapma:** `main` dalındaki son kararlı commit hash'ini tespit et (`git log -n 5 origin/main`).
2. **Değişiklikleri Taşı:** Hatalı commit'i `development` dalına taşı (`cherry-pick` veya branch reset).
3. **`main` Dalını Kurtar:** `main` dalını önceki kararlı commit'e resetle (`git checkout main && git reset --hard <kararli-hash>`).
4. **Uzak Depoyu Düzelt:** Gerekirse force-push ile `origin/main`'i temizle ve `origin/development`'a doğru commit'i push et.
5. **Kullanıcıya Raporla:** Yapılan işlemi ve her iki dalın güncel hash değerlerini açıkça sun.
