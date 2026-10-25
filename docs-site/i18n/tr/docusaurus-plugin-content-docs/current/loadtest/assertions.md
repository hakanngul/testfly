---
id: assertions
title: Akıcı Performans Doğrulamaları (Assertions)
description: "LoadTestAssert ile gecikme yüzdelikleri, Throughput RPS, hata oranı ve HTTP durum kodları için sıkı kalite kapıları oluşturun."
sidebar_position: 6
---

# Akıcı Performans Doğrulamaları (Assertions)

TestFly, yük testi sonuçlarını birinci sınıf doğrulama kurallarıyla değerlendirir. `run()` metodu tamamlandığında bir `LoadTestAssert` nesnesi döner ve böylece SLA (Hizmet Seviyesi Anlaşması) eşiklerinizi doğrudan CI/CD süreçlerinizde test edebilirsiniz.

---

## 1. Gecikme ve Yüzdelik (Percentile) Doğrulamaları

Temel yanıt süresi eşiklerini doğrulayın:

```java
load("/api/v1/feed")
    .users(50)
    .hold(Duration.ofSeconds(15))
    .run()
    .assertMeanLatencyBelow(100)  // Ortalama gecikme < 100ms
    .assertP50Below(80)           // Medyan gecikme < 80ms
    .assertP75Below(120)          // 75. yüzdelik < 120ms
    .assertP90Below(150)          // 90. yüzdelik < 150ms (yalnızca JDK motoru)
    .assertP95Below(250)          // 95. yüzdelik < 250ms
    .assertP99Below(500)          // 99. yüzdelik < 500ms
    .assertMaxLatencyBelow(1200); // En yavaş tekil istek < 1.2s
```

Doğrulama başarısız olduğunda TestFly ayrıntılı hata mesajı üretir:

```
java.lang.AssertionError: [LoadTest] /api/v1/feed: p95 latency 348ms is not below 250ms
```

### Hangi motor hangi yüzdelikleri ölçer

| Yüzdelik | JDK motoru | Gatling motoru |
| :--- | :--- | :--- |
| p50, p75, p95, p99 | Ölçülür | Ölçülür (Gatling istatistiklerinden okunur) |
| p90 | Ölçülür | **Ölçülmez** |

Motorun ölçmediği bir değer `LoadTestMetrics` içinde `Double.NaN` (kontrol için `LoadTestMetrics.isMeasured(value)`), JSON sonuçlarında `null`, raporlarda `n/a` olarak görünür. Asla `0` olarak raporlanmaz. Ölçülmemiş bir değer üzerindeki gecikme doğrulaması geçmek yerine başarısız olur:

```
java.lang.AssertionError: [LoadTest] /api/v1/feed: p90 latency was not measured by engine 'gatling'
```

Bir adımın p95 değeri ölçülmediğinde `assertStepP95Below(...)` da aynı şekilde davranır.

---

## 2. Throughput ve Hata Oranı Doğrulamaları

Sisteminizin güvenilirlikten ödün vermeden hedef trafiği karşıladığından emin olun:

```java
load("/api/orders")
    .users(30)
    .run()
    .assertThroughputAbove(120.0)    // Saniyede en az 120 istek
    .assertErrorRateBelow(0.01)     // Hata oranı %1'in altında olmalı
    .assertSuccessRateAbove(0.99);  // İsteklerin en az %99'u başarılı olmalı
```

:::caution Neyin hata sayıldığı
İki motor da istekleri aynı şekilde sınıflandırır. Bir istek HTTP yanıtı alamadığında (bağlantı reddi, zaman aşımı, I/O hatası), `check(...)` koşullarından biri başarısız olduğunda veya bir `extract(...)` yolu yanıtta bulunamadığında hatalı sayılır. Adımda `status().is(...)` veya `status().in(...)` kontrolü yoksa varsayılan durum kontrolü uygulanır: yanıt 2xx (200–299) veya 304 olmalıdır, dolayısıyla 4xx veya 5xx yanıtı hata oranını yükseltir. Başka kodları kabul etmek için açık bir durum kontrolü ekleyin, örneğin `check(status().in(200, 404))`.
:::

---

## 3. HTTP Durum Kodu Doğrulamaları

Yük altında sunucuların 5xx veya 429 gibi hatalar üretmediğini teyit edin:

```java
load("/api/payments")
    .users(20)
    .run()
    .assertNoStatus(500)                 // Internal Server Error olmamalı
    .assertNoStatus(502)                 // Bad Gateway olmamalı
    .assertNoStatus(503)                 // Service Unavailable olmamalı
    .assertNoStatus(504)                 // Gateway Timeout olmamalı
    .assertNoStatus(429)                 // Too Many Requests olmamalı
    .assertStatusCodeCount(200, 1000);   // En az 1.000 adet 200 OK yanıtı
```

İki motor da her yanıtın gerçek HTTP durum kodunu kaydeder; kontrolü başarısız olan yanıtlar da buna dahildir. Hiç yanıt alamayan istekler `-1` anahtarı altında sayılır. `LoadScenario.assertStatus(n)` yalnızca tüm istekler `n` döndürdüğünde geçer; bu yüzden `-1` kaydı onu başarısız kılar.

Motor bir koşuda durum kodlarını kaydedemediyse (örneğin Gatling durum dosyası `testfly-status.json` yoksa) `statusCodes()` boştur ve `LoadTestMetrics.statusCodesMeasured()` `false` döner. Bu durumda `assertStatus`, `assertStatusCodeCount` ve `assertNoStatus` eksik veriyle geçmek yerine `HTTP status codes were not measured by engine '<engine>'` mesajıyla başarısız olur.

---

## 4. Çok Adımlı Akış Doğrulamaları

Çok adımlı senaryolarda adımlara özel performans eşikleri belirleyebilirsiniz:

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

---

## 5. Ham Metriklere Erişim

Özel istatistikler veya özel raporlama ihtiyaçları için `LoadTestMetrics` nesnesini alabilirsiniz:

```java
LoadTestMetrics metrics = load("/api/summary").run().metrics();

System.out.println("Toplam istek: " + metrics.totalRequests());
System.out.println("P95 Gecikme: " + metrics.p95LatencyMs() + " ms");
System.out.println("HTTP 200 adedi: " + metrics.statusCodeCount(200));
System.out.println("Yanıtsız: " + metrics.statusCodeCount(-1));
if (LoadTestMetrics.isMeasured(metrics.p90LatencyMs())) {
    System.out.println("P90 Gecikme: " + metrics.p90LatencyMs() + " ms");
}
```
