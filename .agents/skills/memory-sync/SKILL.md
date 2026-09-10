---
name: memory-sync
description: Hafızayı sentezleme, scratchpad budama, kalıcı bilgileri wiki'ye aktarma ve MAP.md rotasını güncelleme prosedürü.
---

# Hafıza Senkronizasyonu ve Budama Prosedürü (memory-sync)

## Genel Bakış
Bu beceri, TestFly hafıza döngüsünün "Yazma Yolu" (Write Path) aşamasını standartlaştıran kodsuz bir operasyon prosedürüdür.
Kısa hafızanın (`[[scratchpad]]`) 2.200 karakter sınırında kalmasını, hafızanın çöplüğe dönmemesini ve kalıcı bilgilerin `[[wiki/index]]` bilgi grafiğine aktarılmasını sağlar.

---

## Ne Zaman Kullanılır?
- Her geliştirme oturumu veya büyük görev tamamlandığında.
- `[[scratchpad]]` karakter sayısı 2.000 sınırını aştığında.
- Projede kalıcı bir mimari karar, yeni kural veya domain bilgisi keşfedildiğinde.
- Kullanıcı "/memory-sync" istediğinde veya bağlamı temizle talimatı verdiğinde.

---

## Adım Adım İşleyiş Prosedürü

### Adım 1: Karakter Bütçesi Kontrolü
1. `[[scratchpad]]` dosyasını oku.
2. Toplam karakter sayısını hesapla. Karakter sayısı 2.200'e yakınsa budama ve sentez zorunludur.

### Adım 2: Kalıcı Bilgiyi Sentezleme (Extract)
1. Not defterindeki maddeleri ikiye ayır:
   - **Geçici/Anlık:** Sadece o oturuma ait hata mesajları, geçici komut çıktıları, tamamlanmış alt görevler.
   - **Kalıcı/Değerli:** Mimari kararlar, değişen kural ve sözleşmeler, yeni eklenen kütüphaneler, tasarım tercihleri.
2. Kalıcı bilgileri tek cümlelik veya maddeli özetler halinde damıt.

### Adım 3: Wiki Depolama (LLM Wiki Write)
1. Damıtılan kalıcı bilgi mevcut bir wiki konusuna aitse ilgili `[[wiki/<konu>.md]]` dosyasını güncelle.
2. Yepyeni bir konu veya kavram ise `.agents/wiki/<yeni-konu>.md` dosyasını oluştur:
   - YAML frontmatter ekle (`tags`, `date`, `status`, `type: wiki`).
   - `[[wiki/index]]` ve `[[MAP]]` bağlantılarını ekle.

### Adım 4: Harita Güncellemesi (MAP Sync)
1. `.agents/MAP.md` dosyasını aç.
2. Yeni oluşturulan wiki sayfasını veya beceriyi ilgili kategorinin altına `[[wiki/<yeni-konu>]]` formatında ekle.
3. `.agents/wiki/index.md` sayfasındaki kategori fihristini de güncelle.

### Adım 5: Scratchpad Budama (Pruning)
1. `[[scratchpad]]` dosyasındaki tamamlanmış `[x]` görevleri sil veya tek satırda arşivle.
2. Wiki'ye aktarılmış detaylı açıklamaları defterden temizle.
3. Sadece:
   - **Aktif Odak (Current Focus)**
   - **Anlık Bağlam (Immediate Context)**
   - **Sonraki Açık Görevler (Open Tasks)**
   bölümlerini net ve kısa tut.
4. Dosya boyutunun kesinlikle 2.200 karakter altında olduğunu teyit et.

---

## Bahaneler ve Yanıtlar (Anti-Rationalizations)

| Bahane | Gerçek |
|--------|--------|
| "Scratchpad biraz 2.200 karakteri geçsin, bir şey olmaz." | Karakter aşıldığında bağlam şişer, modelin dikkat penceresi dağılır ve token maliyeti artar. Sınır kesindir. |
| "Bu kararı wiki'ye yazmasam da olur, aklımda kalır." | Modelin oturumlar arası 'aklı' yoktur. Yazılmayan bilgi bir sonraki oturumda yok olur. |
| "MAP.md dosyasını güncellemeye gerek yok, dosya adı belli." | Haritaya işlenmeyen sayfa yetim düğümdür (orphan node); ajan onu Read Path sırasında keşfedemez. |

---

## Doğrulama Kontrol Listesi
- [ ] `[[scratchpad]]` karakter sayısı 2.200 karakterin altında mı?
- [ ] Biten geçici işler temizlendi mi?
- [ ] Yeni kalıcı kararlar `[[wiki/]]` altına işlendi mi?
- [ ] `[[MAP]]` dosyasında tüm yeni sayfaların `[[...]]` çift yönlü linkleri var mı?
- [ ] Obsidian uyumlu YAML frontmatter eksiksiz mi?
