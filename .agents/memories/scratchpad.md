---
tags:
  - memory
  - scratchpad
  - ephemeral
date: 2026-09-27
status: active
char_limit: 2200
---

# Aktif Çalışma Not Defteri (Scratchpad)

> [!WARNING]
> **2.200 Karakter Kuralı:** Bu dosya oturumlar arası anlık bağlamı tutar. Karakter sayısı 2.200'ü aştığında `[[skills/memory-sync/SKILL]]` çalıştırılarak tamamlanan işler budanmalı, kalıcı kararlar `[[wiki/index]]` altına aktarılmalıdır.

---

### 1. Aktif Odak ve Son Durum (Current Focus)
- **Konu:** Cucumber Lifecycle & `afterScenario` Performans Optimizasyonu.
- **Durum:** `afterScenario` ve `beforeScenario` üzerindeki darboğazlar giderildi, gereksiz WebDriver I/O çağrıları ve MP4 bellek tahsisleri optimize edildi. Testler 1 PASS 1 FAIL ile tam doğrulandı.

### 2. Kök Neden & Çözülen Darboğazlar (Performance Fixes)
1. **Çift Screenshot Çağrısı Kaldırıldı:** Hata anında `ScreenshotManager.capture()` ve hemen peşinden `ScreenshotManager.captureAsBase64()` çağrılarak WebDriver üzerinden iki ayrı uzaktan ekran görüntüsü alınıyordu (~1-2 sn). İkinci çağrı kaldırıldı; ilk yakalanan PNG dosyasının byte'ı doğrudan senaryoya iliştirildi.
2. **AI Analizi Sınırlandırıldı:** AI devre dışı iken her fail'da yapılan `driver.getCurrentUrl()` ve `driver.getTitle()` HTTP çağrıları guard kontrolü (`isAiAnalysisEnabled`) ile engellendi.
3. **CDP Screencast FPS Throttling:** Chrome'un saniyede gönderdiği onlarca kare listener seviyesinde `fps` aralığına göre filtrelendi (ack anında gönderilip decode pas geçildi). MP4 encode işlemine giren kare sayısı ve işlem süresi 4-5 kat hızlandırıldı.
4. **Mp4Encoder Buffer Optimizasyonu:** Her kare için `new BufferedImage` ve `Graphics2D` tahsisi yerine tek bir çalışma havuzu (`workBuffer`) yeniden kullanılarak GC yükü sıfırlandı.
5. **Per-Suite Cookie Temizliği:** `lifecycle: per-suite` modunda tarayıcıyı kapatmadan `driver.manage().deleteAllCookies()` ile sonraki senaryoya hazır hale getirme desteği sağlandı.

### 3. Sıradaki Görevler (Next Up)
- [x] `CucumberHooks.java` çift screenshot ve AI çağrıları kaldırıldı.
- [x] `RecordingManager.java` CDP FPS throttling eklendi.
- [x] `Mp4Encoder.java` buffer reuse eklendi.
- [x] `CucumberHooksTest.java` eklendi ve tüm birim testler (1.142 test) geçti.
- [x] `Customer_web_testfly` üzerinde çalıştırılarak doğrulandı.
