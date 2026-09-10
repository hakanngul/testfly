---
id: reporting
title: Yük Testi Raporlama ve Gösterge Panelleri
description: "Performans metriklerini, gecikme dağılım grafiklerini, Gatling Highcharts raporlarını, Allure ve ReportPortal entegrasyonlarını keşfedin."
sidebar_position: 7
---

# Yük Testi Raporlama ve Gösterge Panelleri

TestFly; yerleşik **HTML Raporu**, **Allure** ve **ReportPortal** dahil olmak üzere tüm desteklenen raporlama kanallarına yük testi sonuçlarını otomatik olarak işler.

---

## 1. TestFly HTML Raporu

Bir yük testi çalıştırıldığında HTML raporunda (`target/reports/testfly-report.html`) otomatik olarak **⚡ Load Testing & Performance Analysis** sekmesi belirir.

### Görsel Bileşenler:
- **KPI Özet Kartları:** Toplam istekler, genel Throughput (istek/sn), genel P95 gecikmesi ve sistem hata oranı.
- **İnteraktif Grafikler (Chart.js):**
  - **Gecikme Dağılımı Çubuk Grafiği:** Min, P50, P75, P90, P95, P99 ve Max süreleri.
  - **Durum Kodları Halka Grafiği:** 2xx, 4xx ve 5xx yanıtlarının dağılımı.
- **Senaryo Kartları:** Eşzamanlı kullanıcı (VUs), test süresi, toplam istek sayısı, throughput ve hata yüzdesi.
- **Senaryo Adım Tablosu:** Her bir senaryo adımı için istek sayısı, başarılı/hatalı adetleri, P95 gecikmesi ve hata oranı.
- **Doğrudan Gatling Raporu Bağlantısı:** Gatling'in interaktif Highcharts raporunu yeni sekmede açan `📊 Open Gatling Report →` butonu.

### Müstakil Yük Testi Raporu
`loadtest.reportEnabled: true` ayarlandığında, yalnızca yük testlerine odaklanan bağımsız `target/reports/loadtest-report.html` raporu da oluşturulur.

---

## 2. Allure Raporu Entegrasyonu

TestFly, `target/allure-results/` dizinine yazılan Allure 2 JSON dosyalarını zenginleştirir:

- **Parametreler:** `Load Engine`, `Concurrent Users`, `Total Requests`, `Throughput`, `P95 Latency` ve `Error Rate` parametreleri Allure testine basılır.
- **Rapor Linki:** Gatling HTML raporuna doğrudan giden `📊 Gatling Interactive Report` bağlantısı eklenir.
- **Ekler (Attachments):**
  - `⚡ Load Test Summary` (`text/markdown`): Yüzdelik tablosu ve adım kırılımları.
  - `📋 Gatling Subprocess Log` (`text/plain`): Gatling sürecinin tüm konsol çıktıları.

Allure raporunu görüntülemek için:
```bash
allure serve target/allure-results
```

---

## 3. ReportPortal Entegrasyonu

ReportPortal kullanıldığında test öğesi açıkken yük testi verileri anında iletilir:

- **Markdown Özeti:** Kullanıcı sayısı, Throughput, gecikme yüzdelikleri ve hata oranı `INFO` seviyesinde bir tablo olarak log akışına düşer.
- **Alt Süreç Log Eki:** `gatling-subprocess.log` dosyası ReportPortal testine ikili dosya eki (binary attachment) olarak yüklenir.
- **Hatalar:** Eğer test bir performans kriteri nedeniyle fail olursa, hata mesajı ve tüm metrikler otomatik olarak eklenir.

---

## 4. Alt Süreç Logları ve CI Yönetimi

CI ortamlarında terminal çıktılarının bozulmaması için:
- Gatling'in sürekli ekranı yeniden çizen terminal çıktıları `target/reports/loadtest/<run-id>/gatling-subprocess.log` dosyasına yönlendirilir.
- TestNG/Surefire konsoluna yalnızca temiz durum mesajları yazdırılır:
  ```
  [LoadTest] Starting Gatling engine (simulation: ..., results: ...)
  [LoadTest] Gatling results parsed from target/reports/loadtest/...
  ```
- Eğer Gatling süreci hata ile sonlanırsa, log dosyasının son 25 satırı doğrudan `System.err` ile ekrana dökülür.
