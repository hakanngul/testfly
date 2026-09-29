---
name: wiki-lint
description: >
  Wiki sağlık kontrolü — orphan sayfalar, kırık [[wikilink]]'ler, güncelliğini yitirmiş bilgiler,
  eksik YAML frontmatter ve çapraz referans bütünlüğünü denetleyen periyodik bakım prosedürü.
  LLM Wiki pattern'inin "Lint" operasyonunu uygular.
---

# Wiki Lint — Bilgi Ağı Sağlık Kontrolü

Bu prosedür, `.agents/` altındaki LLM Wiki'nin tutarlılığını ve sağlığını periyodik olarak denetler.
Prompt referansı: *"Lint. Periodically, ask the LLM to health-check the wiki."*

---

## Ne Zaman Çalıştırılır?

- Her 5-10 wiki güncellemesinden sonra
- Sprint / fazın sonunda
- Kullanıcı "wiki lint", "wiki health check", "wiki sağlık kontrolü" dediğinde

---

## Kontrol Listesi (Step by Step)

### 1. Orphan Sayfa Kontrolü
- `.agents/wiki/` altındaki her `.md` dosyası için:
  - `[[wiki/<dosya-adı>]]` referansı `MAP.md`'de var mı?
  - `[[wiki/<dosya-adı>]]` referansı `wiki/index.md`'de var mı?
  - En az bir başka wiki sayfası bu sayfaya `[[...]]` ile bağlanıyor mu?
- **Orphan bulunursa:** MAP.md ve index.md'ye ekle, ilgili sayfalardan çapraz referans oluştur.

### 2. Kırık Wikilink Kontrolü
- Tüm `.agents/` altındaki `.md` dosyalarından `[[...]]` formatındaki linkleri çıkar.
- Her linkin işaret ettiği dosyanın fiziksel olarak var olduğunu doğrula.
- **Kırık link bulunursa:** Hedef dosyayı oluştur veya linki düzelt.

### 3. YAML Frontmatter Tutarlılık
- Her `.md` dosyasında şu alanların mevcut olduğunu doğrula:
  ```yaml
  tags: [...]
  date: YYYY-MM-DD
  status: active | draft | archived
  type: map | protocol | wiki | memory | soul | skill | log
  ```
- **Eksik frontmatter:** Dosyayı düzelt ve uygun değerleri ata.

### 4. Güncellik Kontrolü (Stale Check)
- `date` alanı 90 günden eski olan wiki sayfalarını listele.
- İçerik hâlâ doğru mu? Yeni bilgi gerektiriyor mu?
- **Güncel değilse:** Sayfayı güncelle ve `date` alanını bugüne çek.

### 5. Çelişki Tarama
- Aynı kavramı ele alan birden fazla sayfa var mı?
- Bir sayfadaki bilgi başka bir sayfayla çelişiyor mu?
- **Çelişki bulunursa:** Tek bir doğru kaynağı belirle, diğerini güncelle veya arşivle.

### 6. Eksik Kavram Sayfaları
- Wiki sayfalarında adı geçen ama kendi sayfası olmayan kavramları tespit et.
- Sık geçen kavramlar için yeni wiki sayfası oluşturmayı öner.

### 7. MAP.md Bütünlük
- `MAP.md`'deki tüm `[[...]]` linklerin hedefleri gerçekten var mı?
- Wiki'de var ama MAP'te referansı olmayan dosyalar var mı?

---

## Çıktı Formatı

Lint sonuçlarını şu formatta `memories/log.md`'ye kaydet:

```markdown
## [YYYY-MM-DD] lint | Wiki Sağlık Kontrolü
- **Orphan sayfalar:** (sayı) — (listele veya "yok")
- **Kırık linkler:** (sayı) — (listele veya "yok")
- **Eksik frontmatter:** (sayı) — (listele veya "yok")
- **Güncelliğini yitirmiş:** (sayı) — (listele veya "yok")
- **Çelişkiler:** (sayı) — (listele veya "yok")
- **Düzeltmeler uygulandı:** (özet)
```

---

## İlgili Bağlantılar
- [[MAP]]
- [[wiki/index]]
- [[memories/log]]
- [[rules/memory-protocol]]
- [[skills/memory-sync/SKILL]]
