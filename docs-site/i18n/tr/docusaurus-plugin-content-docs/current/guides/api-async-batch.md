---
title: "Asenkron Çağrılar ve Batch"
---

# Asenkron Çağrılar ve Batch

`sendAsync()` istek, auth, test ID/class/method, interceptor listeleri ve retry/logging ayarlarını çağıran thread’de yakalar; blocking zinciri virtual thread’de çalıştırır. Gönderimden sonra fluent builder’ın değiştirilmesi bu çağrıyı etkilemez. Hazırlama hataları exceptional future olarak döner.

Worker auth/cookie/test bağlamını kurar ve `finally` içinde temizler; uygulamanın rastgele ThreadLocal değerleri taşınmaz. Aynı testin çağrıları concurrent cookie jar paylaşır (aynı cookie’de son yazan kazanır); farklı testler ayrıdır. Paralel çağrılarda paylaşılan interceptor ve token supplier thread-safe olmalıdır. `ApiBatchRunner.concurrently(n)` aktif mantıksal çağrı sayısını sınırlar ve sonuç sırasını korur.

Test bitmeden future’ları bekleyin. Test temizliği tamamlanmamış future’ları iptal eder ve kapalı bağlamdan gelen geç adımları bastırır. `cancel(true)` worker’ı interrupt eder ve sonraki gönderim/retry’leri önler; sunucuda işlenmiş bir işlemi geri alamaz. API executor suite/engine sonunda kapanır, sonraki suite için yeniden oluşturulur.

Zincir örnekleri için [Interceptor Zinciri](api-interceptors.md) sayfasına bakın.
