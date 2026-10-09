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
    .assertP90Below(150)          // 90. yüzdelik < 150ms
    .assertP95Below(250)          // 95. yüzdelik < 250ms
    .assertP99Below(500)          // 99. yüzdelik < 500ms
    .assertMaxLatencyBelow(1200); // En yavaş tekil istek < 1.2s
```

Doğrulama başarısız olduğunda TestFly ayrıntılı hata mesajı üretir:

```
java.lang.AssertionError: [LoadTest] /api/v1/feed: p95 latency 348ms is not below 250ms
```

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
JDK motorunda bir istek yalnızca exception fırlattığında (bağlantı reddi, zaman aşımı, I/O hatası) veya `check(...)` koşullarından biri başarısız olduğunda hatalı sayılır. `check(status().is(...))` olmayan bir adımda HTTP 4xx veya 5xx yanıtı şu anda **başarılı** istek olarak sayılır ve hata oranını yükseltmez. Hata yanıtlarını yakalamak için adıma `check(status().is(200))` ekleyin veya `assertNoStatus(...)` kullanın (bölüm 3). Hiç yanıt alamayan istekler `-1` durum koduyla kaydedilir.
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
```
