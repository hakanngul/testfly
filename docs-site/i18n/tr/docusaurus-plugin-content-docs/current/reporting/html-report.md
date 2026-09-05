---
description: "TestFly HTML raporu: kümülatif suite toplamları, koşum geçmişi arşivleme, AI hata analizi ve karanlık mod içeren Allure tarzı bağımsız bir gösterge panelidir."
id: html-report
title: Selenium HTML Raporu
sidebar_label: HTML Raporu
sidebar_position: 1
---

# HTML Raporu

TestFly, her test yürütmesinden sonra interaktif, Allure tarzı tek sayfalık (SPA) bir HTML raporu üretir. Harici bir sunucu veya veritabanı gerektirmez — `target/testfly-report.html` dosyasını herhangi bir web tarayıcısında çift tıklayarak açmanız yeterlidir.

---

## Mimari ve Dosya Konumları

Rapor, **JSON-driven (Allure tarzı)** bir mimari kullanır. Test çalıştırma metrikleri yapılandırılmış JSON olarak dışa aktarılır ve %100 çevrimdışı bağımsız kullanım için HTML dosyası içine gömülür.

```
target/
├── testfly-report.html           ← Ana interaktif HTML raporu
├── testfly-report-data.json      ← Bağımsız JSON veri dosyası
├── testfly-metrics.json          ← Ham çalıştırma metrikleri verisi
├── reports/
│   └── testfly-report-*.html     ← Zaman damgalı arşivlenmiş geçmiş koşumlar
└── metrics-history/
    └── testfly-metrics-*.json    ← Geçmiş metrik JSON anlık görüntüleri
```

---

## Öne Çıkan Özellikler

### 1. Dashboard Kısmında Kümülatif "TOTAL" Gösterimi
Yalnızca son tekil koşumu gösteren geleneksel test raporlarının aksine, TestFly paneli test paketinde kaydedilen tüm testler için **Kümülatif Suite Toplamlarını (TOTAL)** belirgin şekilde sergiler:

- **Total Tests** — Test paketindeki toplam benzersiz test senaryosu sayısı
- **Total Passed** — Kümülatif başarılı tamamlanan testler
- **Total Failed** — Kümülatif müdahale gerektiren başarısız testler
- **Total Skipped** — Kümülatif atlanan testler
- **Overall Pass Rate** — Kümülatif genel başarı yüzdesi
- **Total Duration** — Toplam kümülatif çalışma süresi

:::tip Kümülatif Test Birleştirme (Merge Runs)
Testleri farklı sınıf veya paketler halinde ardışık koşturduğunuzda `reporting.mergeRuns: true` yapın veya `-Dtestfly.merge=true` parametresi geçin. TestFly önceki test sonuçlarını silmek yerine otomatik olarak korur ve kümülatif tek raporda birleştirir.
:::

---

### 2. Allure Renk Paleti
Rapor arayüzü Allure'un ikonik QA renk tasarımını benimser:

| Durum | Renk | Hex | Açıklama |
|---|---|---|---|
| **Passed** | Allure Yeşili | `#97cc64` | Başarılı test çalıştırması |
| **Failed** | Allure Kırmızısı | `#fd5a3e` | Doğrulama veya beklenmeyen hata |
| **Broken / Warning** | Allure Sarı/Kehribar | `#ffb238` | Ortam veya başlangıç hatası |
| **Skipped** | Allure Gri | `#8c8c8c` | Atlanan veya yok sayılan test |
| **Primary / Brand** | Allure Mavi | `#1890ff` | Menü vurguları ve aktif sekmeler |

---

### 3. Koşum Geçmişi ve İnteraktif Seçici (Run Switcher)
Her test çalıştırması otomatik olarak `target/reports/testfly-report-YYYYMMDD-HHmmss.html` adıyla zaman damgalı olarak arşivlenir.

- **Run Switcher Açılır Menüsü:** Üst başlıkta bulunur; **Suite Total (All Tests)**, **Latest Run** ve geçmiş arşivlenmiş koşumlar arasında tek tıkla geçiş yapmayı sağlar.
- **Run History Sekmesi:** Kalite trend çizelgesi, geçmiş başarı oranları, test sayıları, süreler ve arşiv raporlarına doğrudan bağlantılar görüntüler.

---

### 4. Tanılama ve Hata İnceleme Araçları
Herhangi bir test satırını genişletmek, şu özelliklerle donatılmış bir detay paneli açar:

- **Adım Zaman Çizelgesi (Step Timeline):** `StepLogger` ile kaydedilen adım zaman farklarını (`+56ms`), adım durumlarını (`PASS`, `INFO`, `FAIL`) ve açıklamaları gösterir.
- **API İstek İzi ve cURL İncelemesi:** HTTP metodu, uç nokta, durum kodu, gecikme süresi, kopyalanabilir cURL komutları ve istek/yanıt JSON gövdelerini doğrudan adım çekmecesinde gösterir.
- **Tek Tıkla Stack Trace Kopyalama:** Biçimlendirilmiş hata yığın izini tek butonla panoya kopyalar.
- **AI Hata Analiz Kartı (AI Failure Analysis):** AI analizi aktif olduğunda hatanın kök nedenini ve önerilen çözüm adımlarını sunar.
- **Ekran Görüntüsü Lightbox:** Base64 gömülü küçük resimler tıklandığında yüksek çözünürlüklü pencerede (lightbox) açılır.

---

### 5. Flakiness Radarı ve Risk Analizi
Özel **Flakiness Radar** sekmesi, geçmiş koşumlardaki yürütme eğilimlerini analiz eder:

- **Yüksek Risk (High Risk):** CI hattını bozma potansiyeline sahip, karantinaya alınması tavsiye edilen (`@Quarantine`) dengesiz testler.
- **İzleme Listesi (Watch List):** Ara sıra başarısızlık sergileyen ve inceleme gerektiren testler.
- **Kararlı (Stable):** Tutarlı bir şekilde %100 başarı oranına sahip güvenilir testler.
- **Özet KPI Kartları:** Değerlendirilen test sayısı, Yüksek Risk, İzleme Listesi ve Kararlı test adetlerini anında gösterir.
- **İnteraktif Risk Tablosu:** Testleri başarısızlık yüzdesine, toplam koşum sayısına ve karantina önerisine göre sıralayıp filtreleme imkanı sunar.

---


## Yapılandırma

Raporlama davranışını [`testfly.yml`](../guides/testfly-yml-guide.md) dosyasından yapılandırın:

```yaml
reporting:
  mergeRuns: false                  # ardışık testleri kümülatif birleştirmek için true yapın veya -Dtestfly.merge=true geçin
  historyRuns: 10                   # koşum seçicide saklanacak maksimum geçmiş rapor sayısı (varsayılan: 10)
  allure:
    enabled: false                  # target/allure-results/ dizinine Allure 2 çıktıları üret
```