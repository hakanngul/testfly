---
description: "TestFly HTML raporu: kümülatif suite toplamları, hero telemetri kapsülü, koşum geçmişi arşivleme, AI hata analizi ve karanlık mod içeren Cupertino Lab tarzı bağımsız bir gösterge panelidir."
id: html-report
title: Selenium HTML Raporu
sidebar_label: HTML Raporu
sidebar_position: 1
---

# HTML Raporu

TestFly, her test yürütmesinden sonra interaktif, Cupertino Lab tasarım diline sahip tek sayfalık (SPA) bir HTML raporu üretir. Harici bir sunucu, veritabanı veya internet bağlantısı gerektirmez — `target/testfly-report.html` dosyasını herhangi bir modern web tarayıcısında açmanız yeterlidir.

---

## Mimari ve Dosya Konumları

Rapor, **JSON-driven** bir mimari kullanır. Test çalıştırma metrikleri yapılandırılmış JSON olarak dışa aktarılır ve %100 çevrimdışı bağımsız kullanım için HTML dosyası içine gömülür.

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

## 6 Sekmeli Arayüz Mimarisi

Rapor, hızlı hata inceleme (triage), telemetri analizi ve performans takibi için 6 özel sekmeye ayrılmıştır:

### 1. Dashboard (Gösterge Paneli)
Tüm koşumu özetleyen ana kumanda merkezi:
- **Hero Telemetry Summary Capsule:** Apple tarzı üst telemetri bileşeni:
  - **Dairesel SVG İlerleme Halkası:** Dinamik başarı yüzdesi gösteren donut grafiği.
  - **Etkileşimli Filtre Hapları:** Tıklandığında Test Cases sekmesine geçip anında filtreleme yapan `Passed`, `Healed`, `Failed` ve `Flaky` rozetleri.
  - **Telemetri Meta Satırı:** Tek satırlık monospaced özet: `<SuiteName> · <Duration> · <Browser> · <ExecutionMode>` (örn: `AgenticSuite · 1.8s · chrome 126 · Thread-isolated`).
  - **Lineer İlerleme Çubuğu:** Başarı oranını gösteren pürüzsüz yatay yeşil hat.
- **Suite Toplamları:** Toplam Test, Başarılı, Başarısız, Atlanan ve Koşu Süresini gösteren büyük KPI kartları.
- **Gecikme Yüzdelikleri (Percentiles):** Min, Mean, P50, P90, P95 ve Max çalışma süreleri tablosu.
- **En Yavaş Testler (Slowest Tests Leaderboard):** Optimizasyon gerektiren en uzun testlerin sıralı listesi.
- **Build ve CI Meta Verisi:** Aktif profil, CI sağlayıcısı (GitHub Actions, GitLab, Jenkins), build numarası, branch, commit SHA ve doğrudan CI build linkleri.

:::tip Kümülatif Test Birleştirme (Merge Runs)
Testleri farklı sınıf veya paketler halinde ardışık koşturduğunuzda `reporting.mergeRuns: true` yapın veya `-Dtestfly.merge=true` parametresi geçin. TestFly önceki test sonuçlarını silmek yerine otomatik olarak korur ve kümülatif tek raporda birleştirir.
:::

---

### 2. Test Cases (Test Senaryoları)
Yürütülen tüm testlerin hiyerarşik ve detaylı görünümü:
- **Sınıf ve Feature Bazında Gruplama:** Sınıf adı, test sayısı ve durum rozetlerini içeren açılıp kapanabilir grup başlıkları.
- **İki Kademeli Test Kimliği:** Senaryo / metot başlığını ana metin olarak, feature dosyasını veya paket yolunu ikincil monospace etiket olarak ayırır.
- **Filtre Çubuğu ve Arama:** Hızlı arama (`/` kısayolu) ve durum filtre hapları (`All`, `Passed`, `Failed`, `Skipped`, `Flaky`, `Healed`).
- **Tabular Rakamlar:** Sabit sütun geometrisi ve milisaniye bazında tek tip hizalanmış mantık/toplam süre sütunları.

---

### 3. Failure Triage (Hata İnceleme)
Yalnızca başarısız ve kırık testlere odaklanan özel çalışma alanı:
- **Telemetri Hata Rozetleri:** Monospaced tipografi ve zarif kırmızı tonlama ile uzun hata mesajlarını sütunları bozmadan elipsle (`...`) kesen akıllı rozetler.
- **Adım İcra Zaman Çizelgesi:** Testin tam olarak hangi adımda ve hangi zaman farkında (`+offset`) kırıldığını gösterir.
- **AI Kök Neden Analizi (AI Analysis):** Google Gemini veya Claude destekli hata nedeni ve aksiyon önerileri.
- **Formatlı Stack Trace:** Tek tıkla çalışan **"Copy Stack Trace"** butonu içeren kod bloğu.
- **Sıfır Hata Durumu:** Hiç hata olmadığında görünen yeşil "ZERO FAILURES" rozeti.

---

### 4. Run History & Quality Trends (Koşum Geçmişi)
`target/reports/` altında arşivlenen koşumların tarihsel dökümü:
- **Aktif Koşu Göstergesi (CURRENT):** İncelenmekte olan mevcut rapor `CURRENT` rozetiyle işaretlenir ve gereksiz yere aynı sayfayı açan link yerine pasif buton gösterilir.
- **Geçmiş Koşular:** Eski koşuların başarı yüzdeleri, sayısal metrikleri ve o koşunun arşivlenmiş bağımsız HTML raporunu yeni sekmede açan `View →` butonları.
- **Run Switcher:** Rapor başlığında bulunan ve **Suite Total (All Tests)**, **Latest Run** ve geçmiş arşivler arasında anında geçiş sağlayan açılır menü.

---

### 5. Flakiness Radar (Kararsızlık Radarı)
Geçmiş koşumlar arasındaki kararsızlık modellerini inceleyen stabilite matrisi:
- **Risk Seviyeleri:**
  - **HIGH (≥ %33 hata oranı):** `@Quarantine` altına alınması önerilen dengesiz testler.
  - **WATCH (%10 - %33 hata oranı):** İnceleme gerektiren testler.
  - **STABLE (< %10 hata oranı):** %100'e yakın başarı oranına sahip güvenilir testler.
- **Aksiyon Sütunu:** Karantina durumu (`Quarantine` vs `Monitored`) ve doğrudan testin detay çekmecesine odaklanan `Inspect →` butonu.

---

### 6. Load Testing & Performance (Yük Testleri)
*(Yalnızca performans veya Gatling yük testleri çalıştırıldığında otomatik görünür)*:
- **Gecikme Dağılım Grafiği:** Min, Mean, P50, P90, P95, P99 ve Max gecikmelerini karşılaştıran renkli çubuk grafik.
- **HTTP Statü Kodları Donut Grafiği:** 2xx, 3xx, 4xx ve 5xx dağılımı.
- **Senaryo Kırılımı:** Sanal kullanıcı (VU), saniye başına istek (RPS), toplam istek ve hata oranları.
- **Gatling Rapor Linki:** Orijinal Gatling Highcharts raporuna doğrudan bağlantı.

---

## Tanılama ve İnceleme Yetenekleri

Herhangi bir test satırını genişletmek, zengin tanılama araçları içeren bir çekmece açar:

| Araç | Açıklama |
|---|---|
| **Adım Zaman Çizelgesi** | `StepLogger` ile kaydedilen zaman farkları (`+45ms`), durum rozetleri (`PASS`, `INFO`, `FAIL`) ve açıklamalar. |
| **API İstek İzi ve cURL** | HTTP metodu, uç nokta, durum kodu, gecikme ve kopyalanabilir cURL komutu ile JSON istek/yanıt gövdeleri. |
| **HTML5 Video Oynatıcı** | Çekmece içine gömülü, oynat/duraklat, sarma ve tam ekran modalı destekleyen Base64 MP4/GIF video oynatıcısı. |
| **Ekran Görüntüsü Lightbox** | Tıklandığında yüksek çözünürlükte tam ekran açılan Base64 hata ekran görüntüleri. |
| **Self-Healing Telemetrisi** | Bozuk seçicilerin AI/fallback motoruyla onarıldığını belirten `HEALED` rozetleri ve deneme sayıları. |
| **AI Hata Analiz Kartı** | Hatanın kök nedenini ve önerilen çözüm adımlarını sunan yapay zeka kartı. |

---

## Tasarım Sistemi ve Renk Paleti

TestFly, **Cupertino Lab** tasarım sistemini (`DESIGN.md`) kullanır. Yuvarlatılmış hap aksiyon butonları, nötr derinlik ve yüksek kontrastlı tipografi barındırır:

| Token | Adı | Hex | Kullanım Alanı |
|---|---|---|---|
| `--primary` | Apple Blue | `#0071e3` | Aktif sekmeler, ana butonlar, linkler |
| `--good` | Telemetry Emerald | `#97cc64` | Başarılı testler, 2xx yanıtları, stabil risk |
| `--bad` | Telemetry Crimson | `#fd5a3e` | Başarısız testler, 5xx yanıtları, karantina |
| `--warn` | Telemetry Amber | `#ffb238` | Flaky testler, 4xx uyarıları, izleme listesi |
| `--text-muted` | Neutral Slate | `#8c8c8c` | Atlanan testler, ikincil dosya meta verileri |

Arayüz, üst bardaki butonla tek tıkla OLED Dark Mode (`#000000`) ve Studio Light Mode (`#f5f5f7`) arasında geçiş yapabilir.

---

## Yapılandırma

Raporlama davranışını [`testfly.yml`](../guides/testfly-yml-guide.md) dosyasından yapılandırın:

```yaml
reporting:
  htmlReport: true                  # target/testfly-report.html üretimini kontrol eder (varsayılan: true)
  screenshotOnFailure: true         # hata anında Base64 ekran görüntüsü gömer (varsayılan: true)
  mergeRuns: false                  # ardışık testleri kümülatif birleştirmek için true yapın veya -Dtestfly.merge=true geçin
  historyRuns: 10                   # koşum seçicide saklanacak maksimum geçmiş rapor sayısı (varsayılan: 10)
  allure:
    enabled: false                  # target/allure-results/ dizinine Allure 2 çıktıları üret
  reportPortal:
    enabled: false                  # ReportPortal'a gerçek zamanlı log ve launch gönder
```