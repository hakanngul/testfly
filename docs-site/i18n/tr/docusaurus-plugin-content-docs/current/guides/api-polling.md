---
title: "Polling"
---

# Polling

Mikroservis mimarilerinde ve event-driven sistemlerde arka plan işlerini veya nihai tutarlılığı (eventual consistency) beklemek yaygındır. Rastgele `Thread.sleep()` kullanmak yerine `pollUntil()` metodundan yararlanın:

```java
import java.time.Duration;

// Koşul sağlanana veya zaman aşımına kadar yoklar (varsayılan 1 sn aralık)
ApiResponse res = ApiClient.get("/api/jobs/job-12345")
        .pollUntil(r -> "COMPLETED".equals(r.json("$.status")), Duration.ofSeconds(30));

res.assertJson("$.result", "SUCCESS");

// Özel yoklama aralığı ile
ApiResponse order = ApiClient.get("/api/orders/ord-999")
        .pollUntil(r -> r.status() == 200, Duration.ofSeconds(15), Duration.ofMillis(250));
```

Yoklama sırasında `ApiClient`, denemeleri `StepLogger` üzerine kaydeder (`[API Polling] Started...`, `[API Polling] Condition satisfied in 1420ms`). Süre aşımına kadar koşul sağlanmazsa detaylı bir `ApiException` fırlatılır.

### Polling seçenekleri

`ApiPolling`; hemen durdurulan durum kodları, deneme sınırı ve katı predicate davranışı ekler:

```java
ApiResponse done = ApiClient.get("/api/jobs/{id}").pathParam("id", jobId)
        .pollUntil(r -> "COMPLETED".equals(r.json("$.status")),
                ApiPolling.within(Duration.ofSeconds(30))
                        .interval(Duration.ofMillis(500))   // en az 50 ms
                        .failFastOn(404, 500)               // bu durumlarda hemen dur
                        .maxAttempts(20)                    // zaman aşımına ek, opsiyonel sınır
                        .rethrowPredicateErrors(true));     // hata fırlatan koşul polling'i durdurur
```

Varsayılan olarak transport hataları ve koşulun fırlattığı istisnalar uyarı olarak loglanır ve polling devam eder. Son `ApiException` son durum kodunu ve gövdeyi, cause olarak da son hatayı taşır.
