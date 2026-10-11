---
description: "Selenium testlerinde JavaScript hatalarını yakalayın: ConsoleErrorCollector, tarayıcı konsol hatalarını yakalar; böylece sessiz JS başarısızlıkları testi başarısız eder."
id: console-errors
title: Konsol Hata Toplayıcı
sidebar_position: 11
---

# Konsol Hata Toplayıcı

`ConsoleErrorCollector`, test yürütme sırasında JavaScript konsol hatalarını yakalar. Görünür test başarısızlıklarına yol açmayan sessiz JS başarısızlıklarını yakalamak için kullanın.

---

## Yapılandırma

```yaml title="testfly.yml"
browser:
  captureConsoleErrors: true    # JS hatalarını topla (varsayılan: false)
  failOnConsoleErrors: false    # herhangi bir JS hatasında testi başarısız kıl (varsayılan: false)
```

---

## Bir testte manuel toplama

```java
import io.testfly.browser.ConsoleErrorCollector;

@Test
public void noJsErrorsOnLogin() {
    open("/login");
    ConsoleErrorCollector.injectShim();   // yakalamayı başlat

    type(By.id("username"), "admin");
    type(By.id("password"), "secret");
    click(By.id("submit"));

    List<String> errors = ConsoleErrorCollector.getErrors();
    softAssert().that(errors.isEmpty(), "Unexpected JS errors: " + errors);
}
```

---

## Etkileşimler arasında temizleme

Hangi eylemin hataları ürettiğini izole edin:

```java
open("/checkout");
ConsoleErrorCollector.injectShim();

click(By.id("add-to-cart"));
List<String> addErrors = ConsoleErrorCollector.getErrors();

ConsoleErrorCollector.clear();   // sonraki eylemden önce sıfırla

click(By.id("proceed-to-payment"));
List<String> paymentErrors = ConsoleErrorCollector.getErrors();
```

---

## Chrome vs Firefox

| Tarayıcı | Metod | Notlar |
|---|---|---|
| Chrome, Edge | WebDriver tarayıcı logları | Otomatik çalışır, shim gerektirmez. Sayfa yüklenirken ve link ile gidilen sayfalarda oluşan hatalar yakalanır. |
| Firefox | JS konsol shim'i | Sayfa yüklendikten sonra `injectShim()` çağırın. Yalnızca o andan sonra yapılan `console.error` çağrıları görülür. |
| Firefox, `browser.bidi: true` ile | WebDriver BiDi log olayları | Sayfa yüklenmeye başladığı andan itibaren her hata, yakalanmamış istisnalar ve link ile gidilen sayfalar dahil. |

Chrome, hataları `LogType.BROWSER` üzerinden otomatik olarak toplar. Firefox tarayıcı loglarını WebDriver
üzerinden açığa çıkarmaz. Bu yüzden BiDi olmadan JS shim'ini gerektirir ve shim yalnızca enjekte
edildikten sonra oluşan hataları görebilir. Yani Firefox'ta sayfa yüklenirken hata oluşsa bile
`failOnConsoleErrors` geçebilir.

### BiDi kullanımı

```yaml title="testfly.yml"
browser:
  captureConsoleErrors: true
  failOnConsoleErrors: true
  bidi: true                    # WebDriver BiDi bağlantısı aç (varsayılan: false)
```

`bidi: true` ile tarayıcı bir BiDi bağlantısıyla başlar ve TestFly, test tarayıcısını aldığı andan
itibaren log olaylarını dinler. Shim gerekmez. Bir test başlarken önceki testlerin hataları unutulur,
tarayıcı yeniden kullanılsa bile (`browser.lifecycle: per-suite`). Hangi yakalamanın kullanıldığını
`ConsoleErrorCollector.isBiDiActive()` ile görebilirsiniz.

- Yerelde başlatılan Chrome, Edge veya Firefox ile çalışır. Safari'de BiDi yoktur ve TestFly uzak
  sürücülerde (Grid, BrowserStack, Sauce Labs) BiDi bağlantısı açmaz. Orada bir uyarı yazar ve olağan
  yakalamayı kullanır.
- Olaylar websocket üzerinden, sayfa çalıştıktan kısa süre sonra gelir. Hataları sayfa oturduktan sonra okuyun.
- BiDi yakalaması projenin entegrasyon testlerinde Chrome'a karşı denenmiştir. Firefox desteği onun
  BiDi uygulamasına bağlıdır ve deneysel Firefox CI işinde çalıştırılır.

---

## API

### `ConsoleErrorCollector.injectShim()`
`console.error` çağrılarını yakalayan bir JavaScript shim'i enjekte eder. Firefox'ta sayfa yüklendikten sonra veya tarayıcılar arası tutarlılık için çağırın.

### `ConsoleErrorCollector.collect()`
Son `clear()` çağrısından bu yana yakalanan tüm JS hatalarını döndürür. WebDriver loglarından (Chrome) veya JS shim tamponundan okur.

### `ConsoleErrorCollector.getErrors()`
`collect()` için bir takma addır.

### `ConsoleErrorCollector.clear()`
Geçerli sayfadaki JS shim hata tamponunu sıfırlar.

### `ConsoleErrorCollector.isEnabled()`
Yapılandırmada `browser.captureConsoleErrors: true` ayarlanmışsa `true` döndürür.