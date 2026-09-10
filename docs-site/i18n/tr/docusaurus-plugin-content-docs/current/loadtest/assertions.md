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
java.lang.AssertionError: [LoadTest] P95 latency assertion failed: expected <= 200 ms, but was 348 ms (P50: 120ms, P90: 280ms, P99: 512ms)
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
    .assertStatusCodeCount(200, 1000);   // Tam olarak 1.000 adet 200 OK yanıtı
```

---

## 4. Çok Adımlı Akış Doğrulamaları

Çok adımlı senaryolarda adımlara özel performans eşikleri belirleyebilirsiniz:

```java
LoadScenario akis = LoadScenario.named("Ödeme Akışı")
    .step("Giriş", req -> req.post("/api/login").body("..."))
    .step("Ödeme", req -> req.post("/api/pay").body("..."));

load(akis)
    .users(15)
    .run()
    .assertStepP95Below("Giriş", 150)
    .assertStepP95Below("Ödeme", 800)
    .assertStepErrorRateBelow("Ödeme", 0.0);
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
