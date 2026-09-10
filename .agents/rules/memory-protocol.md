---
tags:
  - protocol
  - rules
  - memory
  - testfly
  - antigravity
date: 2026-09-10
status: active
type: protocol
---

# Hafıza ve Bağlam Protokolü (Memory Protocol)

Bu doküman, TestFly projesinde görev alan otonom ajanların token tasarrufu, oturumlar arası kalıcı bağlam (*persistent memory*) ve bilgi ağı yönetimi için uymak zorunda olduğu bağlayıcı anayasadır. 
Sistem, **"2 Yol & 3 Parça"** ve **"LLM Wiki / Obsidian Bilgi Grafiği"** prensipleri üzerine inşa edilmiştir.

---

## 1. Sistemin 3 Temel Parçası (The 3 Components)

Sistem bilgi dağınıklığını ve token israfını engellemek için üç kesin katmana ayrılmıştır:

```
┌────────────────────────────────────────────────────────────────────────┐
│ 1. KURAL (Anayasa)                                                     │
│    [[AGENTS]] ve [[memory-protocol]]                                   │
│    Ajanın çalışma sınırları, okuma/yazma kuralları ve token disiplini. │
├────────────────────────────────────────────────────────────────────────┤
│ 2. HARİTA (Rota ve İndeks)                                             │
│    [[MAP]]                                                             │
│    Projedeki tüm bilgi kaynaklarının koordinatları. Hangi bilginin     │
│    hangi dükkanda olduğunu gösteren GPS. Tüm linkler [[wikilink]].     │
├────────────────────────────────────────────────────────────────────────┤
│ 3. DEPO (Sentezlenmiş Arşiv)                                           │
│    [[scratchpad]] (Kısa Hafıza) & [[wiki/index]] (Kalıcı LLM Wiki)     │
│    Ham sohbet logları değil; damıtılmış, doğrulanmış ve kalıcı bilgi.  │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. İki Yol Protokolü (The Two Paths)

Ajan her adımında token harcamasını asgari düzeye indirmek için bu iki yolu izler:

### A. Okuma Yolu (Read Path — Token Koruma)
1. **Asla Kör Arama Yapma:** Proje dosyalarını, tüm `src/` ağacını veya tüm wiki dizinini önden körlemesine okuyarak token harcama.
2. **İlk Adım (Kısa Hafıza):** Oturum başında veya yeni bir istek geldiğinde **SADECE** `[[scratchpad]]` dosyasını tara. Son durumu ve aktif görevleri anla.
3. **İkinci Adım (Nokta Atışı Rota):** Görev geçmiş kararları, mimari kuralları veya derin domain bilgisini gerektiriyorsa:
   - Önce `[[MAP]]` dosyasını aç.
   - Haritadaki çift yönlü bağlantılardan (`[[wiki/...]]`) yalnızca ilgili konuya ait tekil sayfayı bul ve sadece onu oku.

### B. Yazma Yolu (Write Path — Oturum Sonu Sentezi)
1. **Anlık Durum Güncellemesi:** Her turun veya görevin sonunda güncel durumu, tamamlanan işleri ve sonraki adımları `[[scratchpad]]` içine işle.
2. **Karakter Kotası Disiplini:** Scratchpad ~2.200 karaktere yaklaştığında veya görev bittiğinde eski/geçici maddeleri buda; kalıcı kararları wiki'ye taşı.
3. **Kalıcı Bilgi Aktarımı (Wiki Sentezi):** 
   - Proje için kalıcı hale gelen bir mimari karar, kural veya domain keşfi varsa bunu `[[wiki/<konu>.md]]` olarak oluştur.
   - Yeni sayfayı derhal `[[MAP]]` ana haritasına çift yönlü `[[<konu>]]` olarak kaydet.
4. **Tekrarlanabilir Süreçler (Skills):**
   - Tekrarlanan operasyonel veya analitik bir prosedür keşfedildiğinde bunu `.agents/skills/<yetenek>/SKILL.md` altında kodsuz kontrol listesi olarak standartlaştır.

---

## 3. TestFly Ajan Hafıza Döngüsü

```
                ┌──────────────┐
                │   soul.md    │ (Değişmez Öz ve Kimlik)
                └──────┬───────┘
                       │
         ┌─────────────┴─────────────┐
         ▼                           ▼
┌──────────────────┐       ┌──────────────────┐
│  memories/       │       │      wiki/       │
│  scratchpad.md   │ ────▶ │  index & sayfalar│
│ (Max 2.200 Kar.) │ Sentez│ (Kalıcı Ağ)      │
└──────────────────┘       └──────────────────┘
         ▲                           ▲
         └─────────────┬─────────────┘
                       │
                ┌──────┴───────┐
                │    MAP.md    │ (Tüm Bağlantıların Düğümü)
                └──────────────┘
```

### A. `soul.md` (Ajanın Özü)
- Tek bir paragraftır.
- Görevi, kıdemi, kırmızı çizgileri ve kullanıcıyla iletişim tarzını tanımlar.
- Asla kod parçası, konfigürasyon veya geçici görev listesi içermez.

### B. `scratchpad.md` (2.200 Karakter Yasası)
- Ajanın kısa vadeli çalışma masasıdır.
- **Kesin Sınır:** 2.200 karakter (~350-400 kelime).
- Çöplüğe dönüşmesine izin verilmez. Yeni bilgi girebilmesi için biten görevler silinir, kalıcı kararlar wiki'ye taşınır.

### C. `skills/memory-sync/` (Sentez ve Temizlik Rutini)
- Hafıza dolduğunda veya sprint tamamlandığında devreye giren mekanik prosedürdür.

---

## 4. Obsidian Graph ve Markdown Standartları

Obsidian Graph View üzerinde projenin tüm hafızasını görselleştirmek için:
1. **YAML Frontmatter:** Tüm `.agents/` altındaki Markdown dosyaları şu başlıkla başlar:
   ```yaml
   ---
   tags: [kategori, etiket]
   date: YYYY-MM-DD
   status: active | draft | archived
   type: map | protocol | wiki | memory | soul
   ---
   ```
2. **Çift Yönlü Bağlantılar (`[[...]]`):** 
   - Dosyalar arası referanslar `[[DosyaAdı]]` veya `[[wiki/KonuAdı]]` formatında yazılır.
   - Bu bağlantılar Obsidian Graph View'da bilgi kümelerini ve bağlam düğümlerini oluşturur.

---

## 5. Kırmızı Çizgiler ve Anti-Pattern'ler

| Hatalı Davranış | Neden Yasak? | Doğru Yaklaşım |
|-----------------|--------------|----------------|
| Görev başında tüm `src/` ağacını okumak | Token tüketir, dikkat dağınıklığı yaratır | Önce `[[scratchpad]]`, gerekirse `[[MAP]]` üzerinden tek dosya oku |
| Scratchpad'i 2.200 karakterden fazla şişirmek | Hızlı bağlam enjeksiyonunu engeller | `[[memory-sync]]` prosedürüyle buda ve wiki'ye aktar |
| Ham konsol/hata loglarını hafızaya yapıştırmak | Bağlam penceresini kirletir | Hatayı ve çözüm kararını tek cümleye damıtıp kaydet |
| `[[MAP]]` dosyasına eklemeden wiki sayfası açmak | Bilgi kopuk kalır (Orphan node) | Her wiki sayfasını `[[MAP]]` veya `[[wiki/index]]` düğümüne bağla |

---
İlgili Bağlantılar:
- Harita: `[[MAP]]`
- Kimlik: `[[soul]]`
- Anlık Notlar: `[[scratchpad]]`
- Wiki Ana Sayfa: `[[wiki/index]]`
- Hafıza Eşitleme Becerisi: `[[memory-sync]]`
