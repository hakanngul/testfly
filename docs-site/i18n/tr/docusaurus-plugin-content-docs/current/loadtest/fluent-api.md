---
id: fluent-api
title: Akıcı (Fluent) Yük Testi API'si
sidebar_position: 3
---

# Fluent Yük Testi API’si

Tek GET adımı için `load(path)` kullanın. Header, body, kontrol ve çoklu adım için `loadScenario(name)` ile başlayın. `step(name)`, `LoadStep` döndürür; `and()` üst `LoadScenario` nesnesine döner.

## POST isteği

Aşağıdaki parça `BaseLoadTest` test metodunun içinde kullanılır; `java.util.Map` import edilmelidir:


```java
loadScenario("Create order").users(10)
    .step("Create").post("/orders")
        .header("Content-Type", "application/json")
        .body(Map.of("sku", "PROD-998", "quantity", 1))
        .check(status().is(201)).and()
    .run().assertErrorRateBelow(0.01);
```


## Çok adımlı akış


```java
import io.testfly.loadtest.BaseLoadTest;
import io.testfly.loadtest.LoadTestFeeder;
import org.testng.annotations.Test;
import java.time.Duration;
import java.util.Map;

public class CheckoutLoadTest extends BaseLoadTest {
    @Test
    public void checkout() {
        loadScenario("Checkout")
            .engine("jdk").users(15).hold(Duration.ofSeconds(10))
            .feed(LoadTestFeeder.csv("testdata/users.csv"))
            .step("Login").post("/login")
                .body(Map.of("username", "${username}", "password", "${password}"))
                .check(status().is(200)).extract("token", "$.accessToken").and()
            .step("Payment").post("/payment")
                .header("Authorization", "Bearer ${token}")
                .body(Map.of("amount", 10)).check(status().is(200)).and()
            .run().assertStepP95Below("Login", 300)
                .assertStepP95Below("Payment", 800)
                .assertStepErrorRateBelow("Payment", 0.01);
    }
}
```


## İş yükü modelleri (yalnızca Gatling) {#workload-models}

`users`, `rampUp` ve `hold` tek bir sabit biçimi tanımlar: kullanıcılar `rampUp` boyunca eşit aralıklarla başlar ve her biri `hold` süresince döngüde kalır (bkz. [Konfigürasyon](./configuration.md)). Başka her biçim için senaryoya `workload(Workload.open(...))` veya `workload(Workload.closed(...))` ile bir iş yükü verin. Adımlar sırayla çalışır ve her sanal kullanıcı senaryo adımlarını **bir kez** çalıştırır; `hold` döngüsü yoktur.

- **Kapalı (closed) model:** aynı anda kaç kullanıcının çalışacağını siz belirlersiniz. İşini bitiren kullanıcının yerine yenisi başlar.
- **Açık (open) model:** o anda kaç kullanıcı çalıştığından bağımsız olarak kullanıcıların nasıl geleceğini siz belirlersiniz. Eşzamanlılık bir sonuçtur: yavaş bir uç noktada kullanıcılar birikir.

```java
import io.testfly.loadtest.BaseLoadTest;
import io.testfly.loadtest.ClosedStep;
import io.testfly.loadtest.OpenStep;
import io.testfly.loadtest.Workload;
import org.testng.annotations.Test;
import java.time.Duration;

public class CartWorkloadLoadTest extends BaseLoadTest {
    @Test
    public void cartUnderConstantConcurrency() {
        loadScenario("Cart")
            .engine("gatling")
            .workload(Workload.closed(
                ClosedStep.rampConcurrent(0, 50, Duration.ofSeconds(30)),
                ClosedStep.constantConcurrent(50, Duration.ofMinutes(2)),
                ClosedStep.rampConcurrent(50, 0, Duration.ofSeconds(30)))) // kademeli azalış
            .step("Cart").get("/cart").and()
            .run()
            .assertP95Below(300)
            .assertErrorRateBelow(0.01);
    }

    @Test
    public void searchArrivalRate() {
        loadScenario("Search")
            .engine("gatling")
            .workload(Workload.open(
                OpenStep.atOnce(10),
                OpenStep.rampRate(1, 20, Duration.ofSeconds(30)),
                OpenStep.constantRate(20, Duration.ofMinutes(2)),
                OpenStep.nothingFor(Duration.ofSeconds(10)),
                OpenStep.stressPeak(100, Duration.ofSeconds(20))))
            .step("Search").get("/search?q=shoes").and()
            .run()
            .assertErrorRateBelow(0.01);
    }
}
```

| Adım | Model | Anlamı |
| --- | --- | --- |
| `OpenStep.atOnce(users)` | açık | `users` kullanıcıyı aynı anda başlatır (süresi 0). |
| `OpenStep.ramp(users, over)` | açık | `users` kullanıcıyı `over` süresine eşit aralıklarla yayarak başlatır. |
| `OpenStep.constantRate(usersPerSec, during)` | açık | `during` boyunca saniyede `usersPerSec` yeni kullanıcı başlatır. |
| `OpenStep.rampRate(fromPerSec, toPerSec, during)` | açık | Geliş hızını `fromPerSec` değerinden `toPerSec` değerine doğrusal olarak değiştirir. |
| `OpenStep.stressPeak(users, over)` | açık | `users` kullanıcıyı `over` boyunca yumuşak (Heaviside) bir sıçrama eğrisiyle başlatır. |
| `OpenStep.nothingFor(duration)` | açık | `duration` boyunca hiç kullanıcı başlatmaz. |
| `ClosedStep.constantConcurrent(users, during)` | kapalı | `during` boyunca aynı anda `users` kullanıcıyı çalışır tutar. |
| `ClosedStep.rampConcurrent(fromUsers, toUsers, during)` | kapalı | Eşzamanlı kullanıcı sayısını `fromUsers` değerinden `toUsers` değerine doğrusal olarak değiştirir. |

Kurallar; hepsi herhangi bir istek gönderilmeden önce denetlenir:

- **Yalnızca Gatling.** JDK motorunda veya Gatling sınıf yolunda yokken `auto` ile `run()`, `IllegalArgumentException` ("workload(...) requires the Gatling engine") ile başarısız olur.
- **Karıştırma yok.** Açık ve kapalı adımlar farklı tiplerdir, bu yüzden tek bir iş yükünde karıştırılamaz. İş yükü aynı senaryoda `users`, `rampUp`, `hold` veya `cooldown` ile birlikte kullanılamaz (`IllegalArgumentException`). Bu anahtarların `@LoadTest` veya `testfly.yml` içindeki değerleri yok sayılır ve bir INFO satırı bunu belirtir.
- **Argümanlar.** Fabrika metotları 1'den küçük kullanıcı sayısında (`rampConcurrent` 0 kabul eder), negatif veya sonlu olmayan hızda ya da pozitif olmayan sürede hemen `IllegalArgumentException` fırlatır. Hiç kullanıcı başlatmayan bir iş yükü (yalnızca beklemeler, yalnızca sıfır hızlar veya zirvesi 0 olan kapalı model) reddedilir.
- **`loadtest.maxUsers`.** Kapalı iş yükünde, rampa uç noktaları dahil zirve eşzamanlılığı sınırlar. Açık iş yükünde zirve geliş hızını (`constantRate` ve `rampRate` için kullanıcı/sn) ve her `atOnce`, `ramp` ve `stressPeak` adımının kullanıcı sayısını sınırlar. Açık iş yükünün sonucunda oluşan eşzamanlılığı sınırlamaz.
- **Kademeli azalış.** `rampConcurrent(n, 0, süre)` olarak yazın. `cooldown` hâlâ `users/rampUp/hold` biçimine uygulanmaz.
- **Raporlanan kullanıcılar.** `metrics().users()` planlanan kullanıcı sayısıdır: kapalı iş yükünde zirve eşzamanlılık; açık iş yükünde `atOnce`, `ramp` ve `stressPeak` adımlarının kullanıcıları ile hız adımlarının yukarı yuvarlanmış `hız × süre` toplamı.

Küçük sürümlerde yeni adım tipleri eklenebilir; bu yüzden `OpenStep` veya `ClosedStep` üzerindeki bir `switch` ifadesinin `default` dalı olmalıdır. Birden çok iş yükünü birlikte çalıştırmak için bkz. [Simülasyonlar ve SLA'lar](./simulations-sla.md).

## Metot referansı

| Nesne | Metotlar |
| --- | --- |
| `BaseLoadTest` | `load(String path)`, `loadScenario(String name)`, `loadSimulation(String name)`, `sla()`, `lastLoadMetrics()`, `status()`, `jsonPath(String)` |
| `LoadScenario` | `users`, `rampUp`, `hold`, `cooldown`, `workload`, `sla`, `engine`, `baseUrl`, `feed`, `feedCsv`, `feedJson`, `thinkTime`, `step`, `run` |
| `LoadStep` | `get(path)`, `post(path)`, `put(path)`, `patch(path)`, `delete(path)`, `header`, `queryParam`, `formParam`, `body`, `check`, `extract`, `and`, `run` |
| `LoadSimulation` | `scenario`, `baseUrl`, `engine`, `sla`, `run` (bkz. [Simülasyonlar ve SLA'lar](./simulations-sla.md)) |

`thinkTime(Duration)` sabit, `thinkTime(minMs, maxMs)` rastgele istekler arası bekleme belirler. `extract`, yanıt değerini aynı sanal kullanıcının sonraki adımlarına aktarır. `load(LoadScenario)` overload’u veya `targetRps()` throttling API’si yoktur; geliş hızı için `constantRate` içeren açık bir iş yükü kullanın. Yük ayarlarını senaryoya, istek ayarlarını adımlarına uygulayın.

## Kontroller ve istek seçenekleri

Kontroller JDK ve Gatling motorlarında aynı şekilde çalışır:

| Kontrol | Anlamı |
| --- | --- |
| `status().is(code)`, `status().in(codes...)` | HTTP durum kodu `code` değerine eşittir veya `codes` içindedir. |
| *(durum kontrolü yok)* | Varsayılan kural: durum kodu 2xx (200–299) veya 304 olmalıdır. |
| `jsonPath(p).exists()` | `p` JSONPath'inde bir değer vardır. |
| `jsonPath(p).is(n)`, `jsonPath(p).in(n...)` | `p` yolundaki JSON değeri `n` değerine eşittir veya değerlerden biridir (metin olarak karşılaştırılır; JSON sayısı `5`, `5` ile eşleşir). |
| `status().matches(regex)`, `jsonPath(p).matches(regex)` | Regex **yanıt gövdesinin tamamında** herhangi bir yerde bulunur (`Matcher.find()`, örtük DOTALL yok). JSONPath kullanılmaz. |

- `status().exists()` `IllegalStateException` fırlatır; `exists()` yalnızca `jsonPath(...)` üzerinde geçerlidir.
- `extract(var, path)`, `path` bulunamazsa isteği hatalı sayar.
- `formParam(name, value)` iki motorda da `application/x-www-form-urlencoded` gövdesi gönderir (`Content-Type` header'ı ayarlamadıysanız). Bir adım `body(...)` ile `formParam(...)` öğesini birlikte kullanamaz.
- `queryParam` değerleri `${var}` yerleştirmesinden sonra URL-encode edilir (UTF-8, boşluk `%20`).
- `body(...)` içeren ve `Content-Type` header'ı olmayan bir adım `application/json` olarak gönderilir.

Desteklenmeyen girdi, herhangi bir istek gönderilmeden önce `IllegalArgumentException` ile başarısız olur. Örnekler: hem body hem form parametresi içeren bir adım, boş bir JSONPath veya geçersiz bir regex. Başında `$` olmayan bir yol iki motorda da `$.` ile başlıyormuş gibi okunur (`token`, `$.token` demektir). JDK motoru yalnızca `$.a.b`, `$.a[0].b` ve `$[0]` JSONPath biçimlerini destekler. İki motorda da JSON `null` değeri mevcut sayılır: `exists()` geçer ve `extract(...)` değişkene `null` yazar. Joker karakterler, derin tarama (`..`), filtreler, dilimler ve `['x']` gibi köşeli parantezli anahtarlar JDK motorunda hemen hata verir ve `engine("gatling")` gerektirir.
