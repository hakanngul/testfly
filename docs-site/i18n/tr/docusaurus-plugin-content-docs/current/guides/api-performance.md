---
title: "Timeout ve Performans"
---

# Timeout ve Performans

Bağlantı kurma süresi ve request timeout ayrı ayarlardır. Request timeout socket read-idle timeout değildir.

```yaml
api:
  connectTimeoutSeconds: 30
  timeoutSeconds: 30
  maxConcurrentRequests: 0
```

```java
ApiClient.get("/report").requestTimeout(Duration.ofSeconds(5)).send();
ApiClient.get("/report").timeout(5).send();
```

Son fluent timeout seçimi kazanır; süreler pozitif olmalıdır. `maxConcurrentRequests=0` sınırsızdır; pozitif değer runtime genelinde gerçek HTTP gönderimlerini sınırlar. Aktif test kapsamları varken bu limit değiştirilemez. Mock yanıtlar permit kullanmaz; refresh/retry gönderimleri ayrı permit alır. Bekleme kesilebilir; permit bekleme toplam süreye dahil, transport süresinden ayrıdır.

Batch concurrency mantıksal çağrıları, bu ayar fiziksel gönderimleri sınırlar. Default client ve bağlantılar paylaşılır; virtual-thread pipeline korunur.

JDK idle pool/keepalive ayarları JVM başlatılırken `-Djdk.httpclient.connectionPoolSize=...` ve `-Djdk.httpclient.keepalive.timeout=...` ile verilebilir. İlk ayar aktif bağlantı limiti değildir; runtime System.setProperty garantisi yoktur.

`ApiTransportBenchmark.main()` localhost üzerinden throughput, p50/p95 ve gözlenen TCP bağlantı sayısını yazdırır. Sonuçlar bilgilendiricidir; CI performans eşiği yoktur.
