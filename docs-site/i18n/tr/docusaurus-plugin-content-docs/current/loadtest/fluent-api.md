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


## Metot referansı

| Nesne | Metotlar |
| --- | --- |
| `BaseLoadTest` | `load(String path)`, `loadScenario(String name)`, `lastLoadMetrics()`, `status()`, `jsonPath(String)` |
| `LoadScenario` | `users`, `rampUp`, `hold`, `cooldown`, `engine`, `baseUrl`, `feed`, `feedCsv`, `feedJson`, `thinkTime`, `step`, `run` |
| `LoadStep` | `get(path)`, `post(path)`, `put(path)`, `patch(path)`, `delete(path)`, `header`, `queryParam`, `formParam`, `body`, `check`, `extract`, `and`, `run` |

`thinkTime(Duration)` sabit, `thinkTime(minMs, maxMs)` rastgele istekler arası bekleme belirler. `extract`, yanıt değerini aynı sanal kullanıcının sonraki adımlarına aktarır. `load(LoadScenario)` overload’u veya `targetRps()` throttling API’si yoktur. Yük ayarlarını senaryoya, istek ayarlarını adımlarına uygulayın.

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
