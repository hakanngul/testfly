---
id: baselines
title: Baseline Karşılaştırması
description: "Bir yük testi koşusunun temel metriklerini sürümlü bir baseline dosyası olarak kaydedin ve toleransı aşan gerilemelerde sonraki koşuları başarısız kılın."
sidebar_position: 6.2
---

# Baseline Karşılaştırması

Bir baseline, tek bir koşunun temel metriklerini saklar: p95, p99 ve ortalama gecikme, hata oranı ve throughput. Sonraki koşular onunla karşılaştırılır; böylece bir test sabit bir eşik yerine bir gerilemede ("p95 %15'ten fazla arttı") başarısız olabilir. Baseline'lar iki motorda da çalışır.

---

## 1. Kaydetme ve Karşılaştırma

```java
import io.testfly.loadtest.BaseLoadTest;
import io.testfly.loadtest.LoadBaseline;
import io.testfly.loadtest.LoadTestAssert;
import io.testfly.loadtest.Tolerance;
import org.testng.annotations.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public class CheckoutRegressionTest extends BaseLoadTest {
    private static final Path BASELINE = Path.of("src/test/resources/baselines/checkout.json");

    @Test
    public void checkoutHasNotRegressed() {
        LoadTestAssert result = loadScenario("Checkout")
            .users(20).rampUp(Duration.ofSeconds(10)).hold(Duration.ofSeconds(30))
            .step("Cart").get("/cart").and()
            .run();

        if (Files.notExists(BASELINE)) {
            LoadBaseline.of(result.metrics()).save(BASELINE);
            return;
        }
        result.assertNoRegression(LoadBaseline.load(BASELINE),
                Tolerance.percent(15).errorRateDelta(0.005));
    }
}
```

- `LoadBaseline.of(metrics)` biten bir koşunun metriklerini alır. Eksik (partial) metriklerde `IllegalArgumentException` fırlatır: eksik bir koşu asla baseline olmaz. Hiç istek içermeyen bir koşuda gecikme ve throughput ölçülmemiş olarak saklanır.
- `save(path)` JSON yazar ve eksik üst dizinleri oluşturur. Önce geçici bir dosyaya yazar ve sonra yerine taşır; böylece okuyan taraf asla yarım dosya görmez.
- `load(path)`, dosya yoksa, bir JSON nesnesi değilse, desteklenmeyen bir `schemaVersion` içeriyorsa veya geçersiz bir değer tutuyorsa `IllegalArgumentException` fırlatır.

Baseline'ları kodda kaydetmek zorunda değilsiniz: her koşu metriklerini bu biçimde zaten kendi koşu dizinindeki `testfly-loadtest-result.json` dosyasına yazar (bkz. [Raporlama](./reporting.md#run-directory)). Baseline olarak kullanmak için bu dosyayı deponuza kopyalayın.

---

## 2. Dosya Biçimi

```json
{
  "schemaVersion" : 1,
  "scenarioName" : "Checkout",
  "engine" : "gatling",
  "createdAt" : "2026-10-25T10:15:30Z",
  "p95" : 120.5,
  "p99" : 180.0,
  "mean" : 60.2,
  "errorRate" : 0.0,
  "throughput" : 42.1
}
```

Gecikmeler milisaniye, `errorRate` 0.0 ile 1.0 arası, `throughput` saniyedeki istek sayısıdır. Ölçülmemiş bir değer `null` olur; `LoadBaseline` bunu `p95()`, `p99()`, `mean()`, `errorRate()` ve `throughput()` metotlarından boş bir `OptionalDouble` olarak döndürür. Yalnızca `schemaVersion` 1 desteklenir.

---

## 3. Tolerans ve Sınırlar

`Tolerance.percent(p)`, gecikme ve throughput için göreli bir tolerans belirler. `errorRateDelta(d)` hata oranı için mutlak bir tolerans belirler; varsayılanı `0.01`'dir (bir yüzde puanı) ve yüzde değeri hata oranına uygulanmaz.

| Metrik | Gerileme sayılır, eğer | Örnek: baseline, `percent(15)`, varsayılan delta |
| :--- | :--- | :--- |
| `p95`, `p99`, `mean` | gerçek > baseline × (1 + p/100) | p95 100 ms: 115 ms üstünde başarısız |
| `throughput` | gerçek < baseline × (1 − p/100) | 40 istek/sn: 34 istek/sn altında başarısız |
| `errorRate` | gerçek > baseline + d | 0.02: 0.03 üstünde başarısız |

- Karşılaştırmalar katıdır ve sınırlar ondalık olarak tam hesaplanır: `percent(15)` ile 100 ms'lik baseline üzerinde tam 115 ms'lik bir p95 geçer.
- 100 veya daha büyük bir yüzde, throughput denetimini kapatır (sınırı 0 veya altı olur). Hiçbir göreli tolerans 0 ms'lik bir baseline üzerindeki gecikme artışını karşılayamaz.
- `Tolerance.percent(..)` ve `errorRateDelta(..)`, negatif veya sonlu olmayan bir değerde `IllegalArgumentException` fırlatır.

---

## 4. Sonuçlar

`assertNoRegression(baseline, tolerance)` hiçbir şey gerilemediğinde `LoadTestAssert` döndürür. Aksi halde gerileyen her metriği baseline, gerçek değer ve sınırıyla listeleyen tek bir `AssertionError` fırlatır.

- **Karşılaştırılmayan asla geçmiş sayılmaz.** Baseline'da veya koşuda ölçülmemiş bir metrik `not compared: not measured` olarak raporlanır. Hiçbir metrik karşılaştırılamadıysa doğrulama başarısız olur. Bazı metrikler karşılaştırılamadan geçerse bir WARN bunları listeler.
- **Eksik koşular** doğrulamayı `metrics are partial` ile başarısız kılar.
- **Motor veya senaryo uyuşmazlığı** yalnızca bir WARN loglar: motorlar gecikmeyi farklı ölçer (bkz. [Motorlar](./engines.md)), bu yüzden aynı motorun koşularını karşılaştırın.

`regressionResults(baseline, tolerance)` karşılaştırmayı doğrulama yapmadan döndürür: p95, p99, mean, errorRate, throughput sırasıyla metrik başına bir `RegressionResult`. Yukarıdaki örneğin devamı olarak, `io.testfly.loadtest.RegressionResult` import edilmişken:

```java
LoadBaseline baseline = LoadBaseline.load(BASELINE);
for (RegressionResult r : result.regressionResults(baseline, Tolerance.percent(15))) {
    System.out.printf("%s baseline=%s actual=%s regressed=%s %s%n",
            r.metric(), r.baseline(), r.actual(), r.regressed(), r.reason());
}
```

`reason`, tolerans içindeki bir metrik için `null`, gerileyen bir metrik için nedeni, bir taraf ölçülmediğinde ise `not compared: not measured` (`LoadBaseline.NOT_COMPARED`) olur; bu durumda `baseline` veya `actual` değeri `NaN` olur.

Bir [simülasyonda](./simulations-sla.md) her senaryoyu `result.scenario(name).assertNoRegression(..)` ile, tüm koşuyu `result.global().assertNoRegression(..)` ile karşılaştırın; birden çok senaryo varsa koşu dizini her senaryo için bir sonuç dosyası ve genel metrikler için bir dosya tutar.
