---
description: "Selenium testlerinde Core Web Vitals metriklerini doğrulayın: proxy veya harici servis olmadan satır içi olarak LCP, CLS ve diğer Google performans metriklerini kontrol edin."
id: performance
title: Performans Doğrulamaları
sidebar_position: 16
---

# Performans Doğrulamaları (Core Web Vitals)

TestFly 2.4.0, Google'ın Core Web Vitals metriklerini doğrudan Selenium testlerinizde doğrulamanıza olanak tanır — ekstra araç, proxy veya harici servis yok. `open()` işleminden sonra `assertPerformance()` çağırın ve her metrik için eşikler belirleyin.

---

## Hızlı örnek

```java
public class HomepagePerformanceTest extends BaseTest {

    @Test
    public void homepage_meetsWebVitalThresholds() {
        open("/");

        assertPerformance()
            .lcp().isBelow(2500)     // Largest Contentful Paint  < 2.5 s  (Good)
            .fcp().isBelow(1800)     // First Contentful Paint    < 1.8 s  (Good)
            .ttfb().isBelow(600)     // Time To First Byte        < 600 ms (Good)
            .cls().isBelow(0.1);     // Cumulative Layout Shift   < 0.1    (Good)
    }
}
```

---

## Google Web Vitals eşikleri

| Metrik | İyi | İyileştirme Gerekli | Kötü |
|--------|------|-------------------|------|
| **LCP** — Largest Contentful Paint | < 2500 ms | < 4000 ms | ≥ 4000 ms |
| **FCP** — First Contentful Paint | < 1800 ms | < 3000 ms | ≥ 3000 ms |
| **CLS** — Cumulative Layout Shift | < 0.1 | < 0.25 | ≥ 0.25 |
| **TTFB** — Time To First Byte | < 800 ms | < 1800 ms | ≥ 1800 ms |

---

## API başvurusu

### `assertPerformance()`

`BaseTest` ve `BaseJUnit5Test` içinde mevcuttur. Geçerli sayfadan metrikleri toplar ve akıcı bir `PerformanceAssert` oluşturucusu (builder) döndürür.

```java
assertPerformance()
    .lcp().isBelow(2500)
    .fcp().isBelow(1800)
    .ttfb().isBelow(600)
    .cls().isBelow(0.1)
    .domLoad().isBelow(3000)    // DOMContentLoaded
    .pageLoad().isBelow(5000);  // window.load (tam sayfa)
```

### `isBelow(double threshold)`

Metriğin eşiğin kesinlikle altında olduğunu doğrular. Başarısızlıkta şöyle bir mesajla `AssertionError` fırlatır:

```
[Performance] LCP exceeded threshold: LCP = 3420ms (threshold: < 2500ms)
```

### `isBelow(double threshold, String message)`

Özel bir başarısızlık mesajıyla aynısı:

```java
assertPerformance()
    .lcp().isBelow(2500, "Homepage LCP regression after hero image update");
```

### `isAbove(double threshold)`

Bir alt sınırı doğrulamak için — gerçek sunucu gecikmesinin ölçülebilir olduğunu doğrulamak için yararlıdır:

```java
assertPerformance()
    .ttfb().isAbove(0);   // gerçek bir HTTP gidiş-gelişinin oluştuğunu doğrular
```

### `collectPerformance()`

Özel doğrulamalar veya günlükleme için ham `PerformanceMetrics` döndürür:

```java
PerformanceMetrics perf = collectPerformance();

System.out.println("LCP: " + perf.lcp() + "ms");
System.out.println("CLS: " + perf.cls());

assertPerformance().lcp().isBelow(3000);
```

### Zincirleme

Her `isBelow()` / `isAbove()` çağrısı üst `PerformanceAssert` nesnesini döndürür ve tam zincirlemeyi mümkün kılar:

```java
assertPerformance()
    .lcp().isBelow(2500)
    .fcp().isBelow(1800)
    .ttfb().isBelow(600)
    .cls().isBelow(0.1);
```

---

## Tarayıcıya göre metrik kullanılabilirliği

| Metrik | Chrome | Edge | Firefox | Safari |
|--------|--------|------|---------|--------|
| LCP | ✅ | ✅ | ❌ | ❌ |
| FCP | ✅ | ✅ | ✅ | ✅ (14.5+) |
| TTFB | ✅ | ✅ | ✅ | ✅ |
| CLS | ✅ | ✅ | ❌ | ❌ |
| DOMContentLoaded | ✅ | ✅ | ✅ | ✅ |
| Sayfa Yükleme | ✅ | ✅ | ✅ | ✅ |

Bir metrik geçerli tarayıcıda kullanılamadığında (değer = `-1`), doğrulama **sessizce atlanır** — başarısız olmaz. Bu, her tarayıcıda ölçülebilen her metriği doğrulayan çapraz tarayıcı test paketleri yazmanıza olanak tanır.

---

## HTML raporunda otomatik yakalama

Başarılı her testten sonra otomatik toplamayı etkinleştirin:

```yaml title="testfly.yml"
performance:
  captureOnEveryTest: true
```

Etkinleştirildiğinde, HTML raporundaki test detay paneli, kullanılabilir her metrik için renk kodlu çipler içeren bir **⚡ Performance** şeridi gösterir:

| Renk | Anlam |
|--------|--------|
| 🟢 Yeşil | İyi eşik karşılandı |
| 🟡 Sarı | İyileştirme gerekli |
| 🔴 Kırmızı | Kötü — eşiği aşıyor |
| Gri (italik) | DOM yükleme / Sayfa yükleme (yalnızca bilgilendirme) |

---

## Ne zaman çağırmalı

`assertPerformance()`, tarayıcının performans zaman çizelgesini **çağrıldığı anda** okur. En iyi sonuçlar için:

- Sayfa tamamen yüklendikten **sonra** çağırın — geleneksel MPA sayfaları için `open()`'in hemen ardından yeterlidir
- **SPA'lar için**, koleksiyondan önce rotanın oturmasını bekleyin: `waitForAngular()`, `waitForReactHydration()` veya önce bir açık öğe beklemesi kullanın
- LCP, kullanıcı etkileşimi durduğunda kesinleşir — otomatik bir testte, doğru değeri almak için herhangi bir tıklamadan önce çağırın

---

## Diğer doğrulamalarla birleştirme

Performans doğrulamaları yalnızca normal TestNG / JUnit 5 doğrulamalarıdır — istediğiniz gibi birleştirin:

```java
@Test
public void productPage_loadsQuicklyAndRendersCorrectly() {
    open("/products/123");

    // İşlevsel doğrulama
    assertThat(By.cssSelector(".product-title")).isVisible();

    // Performans doğrulaması
    assertPerformance()
        .lcp().isBelow(2500)
        .cls().isBelow(0.1);

    // İş doğrulaması
    assertThat(By.cssSelector(".add-to-cart")).isEnabled();
}
```

---

## Yapılandırma başvurusu

```yaml title="testfly.yml"
performance:
  captureOnEveryTest: false   # otomatik yakala ve HTML raporunda göster (varsayılan kapalı)
  lcpWarnMs:  2500            # (gelecek) LCP bu değeri aştığında rapor uyarısı; 0 = devre dışı
  fcpWarnMs:  1800
  ttfbWarnMs: 800
  clsWarn:    0.1
```