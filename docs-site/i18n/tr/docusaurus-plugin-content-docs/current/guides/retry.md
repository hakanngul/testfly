---
description: "Kararsız (flaky) Selenium testleri için otomatik retry: TestFly, herhangi bir IRetryAnalyzer bağlantısı olmadan başarısız testleri yeniden çalıştırır; tek bir yapılandırma satırıyla etkinleştirilir."
id: retry
title: Selenium Retry
sidebar_label: Retry
sidebar_position: 4
---

# Retry

TestFly, başarısız testleri otomatik olarak yeniden çalıştırır. `IRetryAnalyzer` bağlantısı gerekmez.

---

## Global retry

Tüm testler için `testfly.yml` içinde retry'ı etkinleştirin:

```yaml
retry:
  enabled: true
  maxAttempts: 2   # total attempts (including the first run)
```

`maxAttempts: 2` şu anlama gelir: bir kez çalıştır, başarısız olursa bir kez daha yeniden dene.

---

## Metod bazında geçersiz kılma

Belirli bir test için global ayarı geçersiz kılmak amacıyla `@Retryable` kullanın:

```java
import io.testfly.listeners.Retryable;

@Test
@Retryable(maxAttempts = 3)
public void flakyTest() {
    // retried up to 3 times regardless of global config
}
```

---

## Global retry'ı devre dışı bırakma

```yaml
retry:
  enabled: false
```

Devre dışı bırakıldığında, tek tek metodlardaki `@Retryable` de yok sayılır.

---

## HTML raporunda

Herhangi bir test yeniden denendiğinde **Dashboard sekmesi** bir Retry Özeti (Retry Summary) kartı gösterir:

| Metrik | Anlamı |
|---|---|
| **Yeniden denen** | En az bir kez başarısız olup yeniden denen testler |
| **Kurtarıldı** | Retry sonrasında nihayetinde geçen testler |
| **Hâlâ başarısız** | Tüm denemelerde başarısız olan testler |

Yeniden denen testler, Test Cases tablosunda `↻ Nx` rozetiyle işaretlenir.

---

## Nasıl çalışır

`RetryAnnotationTransformer`, Java SPI (`META-INF/services/org.testng.ITestNGListener`) aracılığıyla kaydedilir. `testfly` classpath'te olduğunda otomatik olarak bulunur — listener kaydı gerekmez.

Çalışma zamanında `RetryListener`, global yapılandırmayı ve `@Retryable` ek açıklamasını kontrol eder, ardından TestNG'e `true` (yeniden dene) veya `false` (durdur) döndürür.