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

Bir yük testi çalıştırıldığında HTML raporunda (`target/testfly-report.html`) otomatik olarak **⚡ Load Testing & Performance Analysis** sekmesi belirir.

### Görsel Bileşenler:
- **KPI Özet Kartları:** Toplam istekler, genel Throughput (istek/sn), genel P95 gecikmesi ve sistem hata oranı.
- **İnteraktif Grafikler (Chart.js):**
  - **Gecikme Dağılımı Çubuk Grafiği:** Min, Mean, P50, P90, P95, P99 ve Max süreleri. P75 ve P90 ayrıca her senaryo kartında kutucuk olarak görünür. İki motor da her yüzdeliği ölçer; ölçülmemiş bir değer (örneğin hiçbir isteğin yanıt almadığı bir koşudaki gecikme) asla 0 olarak değil, `n/a` olarak gösterilir.
  - **Durum Kodları Halka Grafiği:** Kaydedilen her HTTP durum kodu için bir dilim. Yanıt alamayan istekler `-1` anahtarı altında, "No response" etiketiyle görünür. Durum kodları ölçülemediyse grafik dilim çizmez, "Status codes not measured" yazar.
- **Senaryo Kartları:** Sanal kullanıcı (ölçülen zirve eşzamanlılık değil; yapılandırılan `users` değeri veya iş yükünün planlanan kullanıcıları), test süresi, toplam istek sayısı, throughput ve hata yüzdesi. Bir [simülasyon](./simulations-sla.md) her senaryo için bir kart ve bir `<simulation> (global)` kartı ekler; özet KPI'lar yalnızca senaryo kartlarını sayar.
- **Senaryo Adım Tablosu:** Her bir senaryo adımı için istek sayısı, başarılı/hatalı adetleri, P95 gecikmesi ve hata oranı.
- **Doğrudan Gatling Raporu Bağlantısı:** Gatling'in interaktif Highcharts raporunu yeni sekmede açan `📊 Open Gatling Report →` butonu.

### Müstakil Yük Testi Raporu
`loadtest.reportEnabled: true` ayarlandığında, yalnızca yük testlerine odaklanan bağımsız `target/loadtest-report.html` raporu da oluşturulur.

---

## 2. Allure Raporu Entegrasyonu

TestFly, `target/allure-results/` dizinine yazılan Allure 2 JSON dosyalarını zenginleştirir:

- **Parametreler:** `Load Engine`, `Concurrent Users`, `Total Requests`, `Throughput`, `P95 Latency` ve `Error Rate` parametreleri Allure testine basılır.
- **Rapor Linki:** Gatling HTML raporuna doğrudan giden `📊 Gatling Interactive Report` bağlantısı eklenir.
- **Ekler (Attachments):**
  - `⚡ Load Test Summary` (`text/markdown`): Yüzdelik tablosu ve adım kırılımları. Ölçülmemiş P75/P90 değerleri `n/a` olarak gösterilir.
  - `📋 Gatling Subprocess Log` (`text/plain`): Gatling sürecinin tüm konsol çıktıları.

Allure raporunu görüntülemek için:
```bash
allure serve target/allure-results
```

---

## 3. ReportPortal Entegrasyonu

ReportPortal kullanıldığında test öğesi açıkken yük testi verileri anında iletilir:

- **Markdown Özeti:** Sanal kullanıcı sayısı, Throughput, gecikme yüzdelikleri ve hata oranı `INFO` seviyesinde bir tablo olarak log akışına düşer.
- **Alt Süreç Log Eki:** `gatling-subprocess.log` dosyası ReportPortal testine ikili dosya eki (binary attachment) olarak yüklenir.
- **Hatalar:** Eğer test bir performans kriteri nedeniyle fail olursa, hata mesajı ve tüm metrikler otomatik olarak eklenir.

---

## 4. Alt Süreç Logları ve CI Yönetimi

CI ortamlarında terminal çıktılarının bozulmaması için:
- Gatling'in sürekli ekranı yeniden çizen terminal çıktıları varsayılan olarak `target/loadtest/<run-id>/gatling-subprocess.log` dosyasına yönlendirilir (`loadtest.resultsDir` üst dizini belirler).
- TestNG/Surefire konsoluna yalnızca temiz durum mesajları yazdırılır:
  ```
  [LoadTest] Starting Gatling engine: <scenario> (<users> users, rampUp=..., hold=...)
  [LoadTest] Gatling execution complete. Parsing results...
  [LoadTest] Gatling results parsed from: target/loadtest/...
  ```
- TestFly'ın metrik dosyası `testfly-metrics.json` ve sonuç dosyası `testfly-loadtest-result.json` aynı koşu dizinine yazılır (bkz. [5. bölüm](#run-directory)).
- Gatling süreci sıfırdan farklı bir kodla çıkarsa koşu, alt süreç logundaki kök hata satırlarını (ör. exception mesajı) ve son 20 satırını kimlik bilgileri `***` ile maskelenmiş olarak içeren bir exception ile başarısız olur. Ham log satırları artık `System.err` akışına yazdırılmaz. Bkz. [Gatling Koşu Yaşam Döngüsü](./engines.md#4-gatling-koşu-yaşam-döngüsü).

---

## 5. Koşu Dizini ve Sonuç Dosyaları {#run-directory}

Her koşu kendi dizinini oluşturur: `<resultsDir>/<ad>-<millis>/` (varsayılan olarak `target/loadtest/<run-id>/`). `<ad>`, senaryo veya simülasyon adıdır; harf, rakam, `_` ve `-` dışındaki her karakter `_` ile değiştirilir. JDK koşuları da bir dizin alır.

| Dosya | Motor | İçerik |
| :--- | :--- | :--- |
| `testfly-metrics.json` | Gatling | Gatling koşusu içinde TestFly'ın kaydettiği metrikler: istek adı başına deneme, OK ve KO sayıları, durum kodları ve milisaniyelik gecikme histogramı. `LoadTestMetrics` yalnızca bu dosyadan oluşturulur. |
| `testfly-loadtest-result.json` | İkisi de | Koşunun temel metrikleri, [baseline](./baselines.md) biçiminde. Birden çok senaryolu bir simülasyonda genel (global) metrikleri tutar. |
| `testfly-loadtest-result-<senaryo>.json` | Gatling (birden çok senaryolu simülasyon) | Senaryo başına bir sonuç dosyası. İki senaryo adı aynı dosya adını verirse sonraki `-2`, `-3`, … ekini alır ve bir WARN loglanır. |
| `gatling-subprocess.log` ve Gatling HTML raporu | Gatling | Alt JVM'in çıktısı ve Gatling'in kendi raporu. |

Sonuç dosyası en iyi çaba (best effort) ile yazılır: yazma hatası bir WARN loglar ve koşuyu asla başarısız kılmaz; [eksik (partial)](./engines.md#4-gatling-koşu-yaşam-döngüsü) bir koşu hiç dosya yazmaz. Bir koşuyu baseline olarak kullanmak için `testfly-loadtest-result.json` dosyasını deponuza kopyalayın.

:::caution Disk kullanımı
TestFly koşu dizinlerini asla silmez. Her koşu küçük bir dizin ekler (JDK koşusu yalnızca sonuç dosyasını, Gatling koşusu ayrıca Gatling raporunu ve logunu); bu yüzden `resultsDir` her koşuyla büyür. CI'da temizleyin veya `mvn clean` silsin diye `target/` altında tutun.
:::

HTML raporunu besleyen suite düzeyindeki `target/testfly-metrics.json`, aynı ada sahip farklı bir dosyadır. Bu dosyadaki yük testi girdileri ayrıca `partial`, `aggregate` (simülasyonun genel girdisi için `true`) ve `sla` alanlarını taşır: SLA kuralı başına `scope`, `metric`, `comparison`, `threshold`, `actual`, `passed` ve `reason` içeren bir girdi. HTML gösterge paneli SLA sonuçlarını henüz göstermez; bunları bu dosyadan veya `slaResults()` üzerinden okuyun.

### Yüzdelikler: TestFly ve Gatling raporu {#percentiles}

Gatling'de TestFly p50, p75, p90, p95 ve p99 değerlerini, yanıt alan istekler üzerinden kendi milisaniyelik histogramından doğrusal interpolasyonla (JDK motorunun yöntemiyle) kesin olarak hesaplar. Gatling'in HTML raporu yüzdelikleri bir t-digest ile tahmin eder; bu yüzden değerleri TestFly'ınkilerden farklı olabilir, en belirgin olarak küçük örneklemlerde yüksek yüzdeliklerde. İstek toplamları da farklı olabilir: TestFly oluşturulamayan bir isteği KO sayar, Gatling'in raporu ise onu dışarıda bırakır. Doğrulamalarda, SLA'larda, baseline'larda ve TestFly raporlarında TestFly'ın değerleri kullanılır. TestFly'ın SLA kararı ile Gatling'in yerel assertion'ları aynı kurallarda farklı sonuç verirse bir WARN ikisini de belirtir.
