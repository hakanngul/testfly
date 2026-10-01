---
title: "Loglama ve Raporlama"
---

# Loglama ve Raporlama

Her `ApiClient` isteği otomatik olarak adım zaman çizelgesine kaydedilir:

```
[API] GET /api/users/1 → 200 (143ms)
[API] POST /api/orders → 201 (89ms)
[API] DELETE /api/orders/5 → 404 (12ms)   ← logged as FAIL
```

`testfly.yml` içinde gövde ve cURL loglamayı etkinleştirin:

```yaml
api:
  logBody: true
  logCurl: true
```

`logBody: true` ve `logCurl: true` etkinleştirildiğinde:
- HTML raporu, doğrudan terminalinize veya Postman'e kopyalayıp çalıştırabileceğiniz genişletilebilir bir **cURL komut bloğu** sunar.
- Biçimlendirilmiş istek ve yanıt JSON gövdeleri (payload), testin detay çekmecesi içerisinde özel kod bloklarında vurgulanır.

## Interceptor ve retry raporları

Her gerçek HTTP gönderimi sıra numarasıyla INFO, retry kararı WARN olarak kaydedilir. Nihai mantıksal yanıt mevcut PASS/FAIL kuralını kullanır. Böylece 401 sonrası başarılı 200 alınması ara FAIL adımı üretmez. Sentetik sonuçlar `[synthetic]` olarak işaretlenir.

`durationMs()` dönen transport denemesinin süresidir (veya sentetik yanıtın verilen süresi); rapor retry/backoff dahil toplam çağrı süresini ayrıca gösterir. Header ve cURL gerçekten gönderilen istekten üretilir; Authorization, Cookie ve X-Api-Key dahil maskeleme büyük/küçük harfe duyarsızdır. cURL için `api.logCurl: true` ayarını etkinleştirin; maskeli komutu yeniden çalıştırmadan önce kimlik bilgilerini sağlamanız gerekir.
