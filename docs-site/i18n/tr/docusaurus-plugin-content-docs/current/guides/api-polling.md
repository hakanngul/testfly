---
title: "Polling"
---

# Polling

Mikroservis mimarilerinde ve event-driven sistemlerde arka plan işlerini veya nihai tutarlılığı (eventual consistency) beklemek yaygındır. Rastgele `Thread.sleep()` kullanmak yerine `pollUntil()` metodundan yararlanın:

```java
import java.time.Duration;

// Koşul sağlanana veya zaman aşımına kadar yoklar (varsayılan 500ms aralık)
ApiResponse res = ApiClient.get("/api/jobs/job-12345")
        .pollUntil(r -> "COMPLETED".equals(r.json("$.status")), Duration.ofSeconds(30));

res.assertJson("$.result", "SUCCESS");

// Özel yoklama aralığı ile
ApiResponse order = ApiClient.get("/api/orders/ord-999")
        .pollUntil(r -> r.status() == 200, Duration.ofSeconds(15), Duration.ofMillis(250));
```

Yoklama sırasında `ApiClient`, denemeleri `StepLogger` üzerine kaydeder (`[API Polling] Started...`, `[API Polling] Condition satisfied in 1420ms`). Süre aşımına kadar koşul sağlanmazsa detaylı bir `ApiException` fırlatılır.
