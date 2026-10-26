---
id: simulations-sla
title: Simülasyonlar ve SLA'lar
description: "Birden çok yük senaryosunu tek bir Gatling simülasyonu olarak birlikte çalıştırın ve TestFly'ın iki motorda da değerlendirdiği hizmet seviyesi kurallarını tanımlayın."
sidebar_position: 6.1
---

# Simülasyonlar ve SLA'lar

Bir **simülasyon**, her biri kendi yük biçimine sahip birden çok senaryoyu aynı anda, tek bir Gatling koşusu olarak çalıştırır. Bir **SLA**, TestFly'ın koşu bittiğinde denetlediği gecikme, hata oranı ve throughput kurallarıdır. SLA'lar iki motorda da çalışır; simülasyonlar Gatling gerektirir.

---

## 1. Simülasyonlar (yalnızca Gatling) {#simulations}

Simülasyonu `loadSimulation(name)` ile başlatın, senaryoları `scenario(...)` ile ekleyin ve `run()` çağırın. Sonuç bir `LoadSimulationResult` nesnesidir.

```java
import io.testfly.loadtest.BaseLoadTest;
import io.testfly.loadtest.ClosedStep;
import io.testfly.loadtest.LoadSimulationResult;
import io.testfly.loadtest.LoadTestMetrics;
import io.testfly.loadtest.OpenStep;
import io.testfly.loadtest.Workload;
import org.testng.annotations.Test;
import java.time.Duration;
import java.util.Map;

public class ShopSimulationTest extends BaseLoadTest {
    @Test
    public void shop() {
        LoadSimulationResult result = loadSimulation("Shop")
            .baseUrl("https://shop.example.com")
            .scenario(loadScenario("Browse")
                .workload(Workload.closed(ClosedStep.constantConcurrent(20, Duration.ofMinutes(2))))
                .step("home").get("/").and()
                .step("product").get("/products/42").and())
            .scenario(loadScenario("Buy")
                .workload(Workload.open(OpenStep.constantRate(2, Duration.ofMinutes(2))))
                .sla(sla().p95Below(800).step("pay").errorRateBelow(0.01))
                .step("pay").post("/pay").body(Map.of("amount", 10)).check(status().is(201)).and())
            .sla(sla().errorRateBelow(0.02).step("Browse :: product").p99Below(1500))
            .run();

        result.global().assertThroughputAbove(5);
        result.scenario("Browse").assertStepP95Below("product", 400);
        result.global().assertStepP95Below("Browse :: product", 400);

        LoadTestMetrics buy = result.scenarioMetrics("Buy");
        System.out.println(result.scenarioNames() + ": Buy p90 = " + buy.p90LatencyMs() + " ms");
    }
}
```

| `LoadSimulationResult` metodu | Döndürdüğü |
| :--- | :--- |
| `global()` | Tüm senaryolar birlikte için `LoadTestAssert` |
| `scenario(name)` | Tek bir senaryo için `LoadTestAssert` |
| `globalMetrics()`, `scenarioMetrics(name)` | Bunların arkasındaki `LoadTestMetrics` |
| `scenarioNames()` | Eklenme sırasıyla senaryo adları |
| `slaResults()` | Tüm SLA sonuçları: önce simülasyonun kuralları, sonra senaryo sırasıyla her senaryonun kuralları |

`scenario(name)` ve `scenarioMetrics(name)`, bilinmeyen bir ad için `IllegalArgumentException` fırlatır. Düz `slaResults()` listesi bir kuralın hangi senaryoya ait olduğunu söylemez; bunun için `globalMetrics().slaResults()` ve `scenarioMetrics(name).slaResults()` kullanın.

**Sonuçlar nasıl oluşur**

- Her senaryo kendi iş yükünü veya `users/rampUp/hold` biçimini, adımlarını, besleyicisini (feeder) ve düşünme süresini korur. Senaryonun kendi `baseUrl` değeri simülasyonun `baseUrl(...)` değerinden, o da yapılandırılan değerden önce gelir.
- Birden çok senaryo varsa istek adları `"<senaryo> :: <adım>"` olur. Genel (global) metrikler bu adları adım anahtarı olarak kullanır; her senaryonun kendi metrikleri düz adım adlarını korur. Tek senaryoda adlara önek eklenmez.
- Bir senaryonun throughput değeri, istek sayısının **tüm** koşunun süresine bölümüdür; bu yüzden senaryo throughput'larının toplamı genel throughput'a eşittir.
- Genel metriklerin adı `"<simulation> (global)"` olur. Her senaryo ve genel girdi raporlar için kaydedilir; `lastLoadMetrics()` genel metrikleri döndürür.
- Bazı isteklerinin kaydedilmiş sonucu yoksa bir senaryo tek başına eksik (partial) olur. Gatling'in `maxDuration` değeri koşuyu durdurursa tüm senaryolar ve genel metrikler eksik olur.

**Herhangi bir istekten önce doğrulama** (`IllegalArgumentException`):

- Çözümlenen motor Gatling olmalıdır. `engine("jdk")` veya Gatling sınıf yolunda yokken `auto` reddedilir. Simülasyonunkinden (veya `auto` değerinden) farklı bir motor belirleyen senaryo reddedilir.
- En az bir senaryo olmalıdır. Senaryo adları boş olmamalı ve benzersiz olmalı, `#{`, `${` veya `" :: "` içermemeli ve `" ::"` ile bitmemelidir.
- Her senaryo tek başına çalışıyormuş gibi doğrulanır (adımlar, kontroller, iş yükü kuralları).
- `loadtest.maxUsers`, kapalı zirvelerin **toplamını** (`users/rampUp/hold` senaryosu kendi kullanıcılarını sayar) ve ayrıca açık zirve geliş hızlarının toplamını sınırlar. Kendi `users(..)` değeri `maxUsers` değerini aşan bir senaryo, tek bir `run()` çağrısında olduğu gibi kırpılmak yerine burada reddedilir; böylece toplam yük sessizce azaltılmaz.
- Simülasyon SLA'sındaki yalın (önek olmayan) bir adım adı yalnızca tek bir senaryoda bulunmalıdır (aşağıya bakın).

---

## 2. SLA'lar {#slas}

SLA'yı `sla()` ile (`BaseLoadTest` üzerinde veya `LoadSla.sla()`) oluşturun ve `sla(...)` ile bir senaryoya ya da simülasyona ekleyin. Önce genel kurallar gelir; `step(name)` tek bir adım için kurallar başlatır ve her adım kuralı SLA'yı döndürür, böylece zincir devam eder.

```java
loadScenario("Checkout")
    .users(20).rampUp(Duration.ofSeconds(10)).hold(Duration.ofSeconds(30))
    .sla(sla().p95Below(300).errorRateBelow(0.01)
              .step("Pay").p99Below(800))
    .step("Cart").get("/cart").and()
    .step("Pay").post("/pay").body(Map.of("amount", 10)).check(status().is(201)).and()
    .run();
```

| Kapsam | Kurallar |
| :--- | :--- |
| Genel (`LoadSla`) | `p50Below`, `p75Below`, `p90Below`, `p95Below`, `p99Below`, `meanBelow`, `maxBelow` (ms), `errorRateBelow` (0.0 ile 1.0 arası), `throughputAbove` (istek/sn) |
| Adım (`StepSla`, `step(name)` ile) | `p95Below`, `p99Below` (ms), `errorRateBelow` (0.0 ile 1.0 arası) |

**Değerlendirme**

- TestFly kuralları `LoadTestMetrics` üzerinden, iki motorda da aynı şekilde değerlendirir. Karar hiçbir zaman Gatling'in çıktısına bağlı değildir.
- Karşılaştırmalar katıdır: `xBelow(t)` yalnızca değer `t`'nin altındaysa, `throughputAbove(t)` yalnızca üstündeyse geçer.
- Bir kural şu nedenlerden biriyle, bu sırayla denetlenerek başarısız olur: `metrics are partial` (koşu [eksik](./engines.md#4-gatling-koşu-yaşam-döngüsü)), `step not found` (metriklerde o adda adım yok), `not measured` (değer `NaN`) veya `250.0 is not below 200.0` gibi bir karşılaştırma.
- `run()` önce koşuyu SLA sonuçlarıyla birlikte raporlar ve `lastLoadMetrics()` için kaydeder. Ardından başarısız bir kural varsa, her başarısız kuralı kapsamı, metriği, eşiği, gerçek değeri ve nedeniyle listeleyen tek bir `AssertionError` fırlatır.
- Geçersiz eşikler kural tanımlanırken `IllegalArgumentException` fırlatır: sonlu olmayan bir değer veya 0.0 ile 1.0 dışında bir hata oranı. `step("global")` ve boş bir adım adı da reddedilir.
- `rules()` bir anlık kopya döndürür; bir koşu, başladığında gördüğü kuralları değerlendirir.

Test başarısız olmadan önce sonuçları loglamak için hatayı yakalayın ve `lastLoadMetrics()` değerini okuyun (`io.testfly.loadtest.SlaResult` import edilmiş bir `BaseLoadTest` test metodu içindeki parça):

```java
try {
    loadScenario("Checkout")
        .users(20)
        .sla(sla().p95Below(300).throughputAbove(50))
        .step("Cart").get("/cart").and()
        .run();
} catch (AssertionError e) {
    for (SlaResult r : lastLoadMetrics().slaResults()) {
        System.out.println(r.rule().scope() + " " + r.rule().metric() + " " + r.rule().threshold()
                + " actual=" + r.actual() + " passed=" + r.passed() + " " + r.reason());
    }
    throw e;
}
```

SLA geçtiğinde `run()` her zamanki gibi `LoadTestAssert` döndürür; `slaResults()` ve `assertSlaPassed()` kullanılabilir. `assertP95Below` ve diğer doğrulamalardan farkları için bkz. [SLA mı, doğrulama mı?](./assertions.md#slas-or-assertions).

### Simülasyonda SLA'lar

- **Senaryo SLA'sı** (`loadScenario(..).sla(..)`): o senaryonun kendi metrikleri üzerinde, düz adım adlarıyla (`"pay"`) değerlendirilir.
- **Simülasyon SLA'sı** (`loadSimulation(..).sla(..)`): genel metrikler üzerinde değerlendirilir. Adımı `"<senaryo> :: <adım>"` olarak veya o adda adımı yalnızca bir senaryo içeriyorsa yalın adıyla belirtin. Birden çok senaryoda bulunan yalın bir ad, koşudan önce doğrulamada başarısız olur. Hiçbir yerde bulunmayan bir adım adı değerlendirmede `step not found` ile başarısız olur.
- Herhangi bir SLA başarısız olursa `run()`, başarısız kuralları senaryo başına ve genel metrikler için gruplayan tek bir `AssertionError` fırlatır.

### Gatling raporunda yerel assertion'lar

Gatling'de TestFly kuralları ayrıca Gatling assertion'ları olarak kaydeder; böylece kurallar Gatling'in HTML raporunda görünür. Bunlardan biri başarısız olursa Gatling 2 koduyla çıkar; TestFly bu kodu çökme olarak değil, "assertion'lar başarısız" olarak ele alır. Geçerli karar TestFly'ınkidir: aynı kurallarda Gatling'in kararıyla çelişirse bir WARN, TestFly'ın kendi histogramından kesin yüzdelikler kullandığını, Gatling'in ise yüzdelikleri tahmin ettiğini açıklar (bkz. [Raporlama](./reporting.md#percentiles)). Birden çok senaryolu bir simülasyonda bir senaryonun genel kuralları ve koşuda bulunmayan adımlara ait kurallar için yerel karşılık yoktur; bunları yalnızca TestFly değerlendirir.
