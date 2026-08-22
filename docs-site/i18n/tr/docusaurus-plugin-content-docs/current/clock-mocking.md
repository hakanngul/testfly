---
description: "Selenium testlerinde tarayıcı saatini taklit edin: geri sayımları, deneme sürelerini ve zamana duyarlı arayüzleri deterministik olarak test etmek için JavaScript Date'i dondurun veya ilerletin."
id: clock-mocking
title: Saat Taklidi
sidebar_position: 14
---

# Saat Taklidi (Clock Mocking)

`TestClock`, tarayıcının `Date` nesnesini dondurmanıza veya ilerletmenize olanak tanır; böylece zamana duyarlı arayüzü veritabanına veya sistem saatine dokunmadan test edebilirsiniz.

---

## Nasıl çalışır

`clock().set(isoString)` aktif tarayıcı sayfasına bir `Date` geçersiz kılma (override) enjekte eder. İstemci tarafı JavaScript'teki sonraki her `new Date()` ve `Date.now()` çağrısı, taklit edilen saati döndürür. Gerçek `Date`, `window.__sbOriginalDate` altında saklanır ve her testin sonunda otomatik olarak geri yüklenir.

---

## Hızlı örnek

```java
public class TrialBannerTest extends BaseTest {

    @Test
    public void showsExpiredBanner_when30DaysPast() {
        open("/dashboard");
        clock().set("2030-06-01T00:00:00Z");    // deneme süresi 30 gün önce doldu
        getDriver().navigate().refresh();        // sayfa taklit edilen saatle yeniden çizilir

        assertThat(By.id("trial-banner")).hasText("Your trial expired 30 days ago");
    }
}
```

:::tip `open()` işlemini önce çağırın
`clock().set()` JavaScript enjekte ettiği için aktif bir sayfa gerektirir. Önce `open()` çağırın, ardından saati ayarlayın ve son olarak istemci tarafı yeniden çizmeyi (refresh, SPA gezinmesi veya tarih getiren bir tıklama) tetikleyin.
:::

---

## API başvurusu

### `clock().set(String isoDateTime)`

Tarayıcıdaki `new Date()` ve `Date.now()` işlemlerini verilen ana geçersiz kılar.

```java
clock().set("2030-01-01T00:00:00Z");
```

- Herhangi bir ISO 8601 UTC dizesini kabul eder (`Instant.parse` uyumlu)
- Zincirleme için `this` döndürür

### `clock().advance(Duration duration)`

Taklit edilen saati mevcut taklitten itibaren `duration` kadar ilerletir. Etkin bir taklit yoksa, gerçek zamandan ilerletir.

```java
clock().set("2030-01-01T00:00:00Z");
clock().advance(Duration.ofDays(30));   // artık 2030-01-31 olarak taklit edilir
```

Zincirleme için `this` döndürür. Yaygın süreler:

```java
Duration.ofSeconds(30)
Duration.ofMinutes(5)
Duration.ofHours(1)
Duration.ofDays(90)
```

### `clock().reset()`

Tarayıcıdaki gerçek `Date` uygulamasını geri yükler. Her testten sonra otomatik olarak çağrılır — açıkça çağrı yapmanız isteğe bağlıdır.

```java
clock().set("2030-01-01T00:00:00Z");
// ... doğrulamalar ...
clock().reset();  // isteğe bağlı — framework bunu otomatik yapar
```

### `clock().getMockedTimeMs()`

Şu anda taklit edilen saati milisaniye cinsinden epoch değeri olarak döndürür; etkin bir taklit yoksa `null` döndürür.

---

## Yaygın kullanım örnekleri

### Deneme / abonelik sona ermesi

```java
// Deneme bitiş tarihinin 30 gün ötesine geç
clock().set("2030-06-01T00:00:00Z");
getDriver().navigate().refresh();
assertThat(By.id("trial-expired-banner")).isVisible();
```

### Henüz aktif olmayan yaklaşan kampanya

```java
// Kampanya başlamadan bir gün önce — buton gizli olmalı
clock().set("2030-04-30T23:59:59Z");
getDriver().navigate().refresh();
assertThat(By.id("promo-btn")).isHidden();

// Kampanya günü — buton görünür olmalı
clock().advance(Duration.ofSeconds(2));
getDriver().navigate().refresh();
assertThat(By.id("promo-btn")).isVisible();
```

### Geri sayım sayaçları

```java
open("/sale");
clock().set("2030-07-04T11:00:00Z");
getDriver().navigate().refresh();
assertThat(By.id("countdown")).containsText("1 hour remaining");
```

### Zamanlanmış iş göstergesi

```java
clock().set("2030-12-31T23:59:00Z");
getDriver().navigate().refresh();
assertThat(By.id("year-end-notice")).isVisible();
```

---

## Zincirleme

```java
open("/account");
clock()
    .set("2030-01-01T00:00:00Z")
    .advance(Duration.ofDays(365));   // artık 2031-01-01
getDriver().navigate().refresh();
assertThat(By.id("anniversary-badge")).isVisible();
```

---

## Otomatik sıfırlama

Framework, her testten sonra tüm sonuç yollarında (başarı, başarısızlık, atlama) `TestClock.autoReset()` çağırır. Her test temiz, gerçek bir `Date` ile başlar. `clock().reset()` çağrısını hiçbir zaman açıkça yapmanız gerekmez.

---

## Kapsam

`TestClock`, `Date` nesnesini yalnızca tarayıcıda şu anda yüklü sayfa içinde kontrol eder. Şunları etkilemez:

- Sunucu tarafı tarih/saat kontrolleri (onlar için sunucu tarafı tarih geçersiz kılma veya ortam değişkeni kullanın)
- Diğer tarayıcı sekmeleri / pencereleri
- Test JVM'inde çalışan Java kodu

---

## Yapılandırma

```yaml title="testfly.yml"
clock:
  injectHeader: false      # her istekle sunucuya X-Mock-Date başlığı gönder
  headerName: X-Mock-Date  # başlık adı (tarayıcı CDP desteği gerektirir)
```

`injectHeader` varsayılan olarak kapalıdır. Etkinleştirildiğinde, her tarayıcı isteği geçerli taklit saate ayarlanmış `X-Mock-Date` başlığını içerir; böylece bu başlığa saygı duyan arka uç servisleri aynı tarihi sunucu tarafında simüle edebilir.