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
    Assert.assertTrue(errors.isEmpty(), "Unexpected JS errors: " + errors);
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
| Chrome | WebDriver tarayıcı logları | Otomatik çalışır, shim gerektirmez |
| Firefox | JS konsol shim'i | Sayfa yüklendikten sonra `injectShim()` çağırın |

Chrome, hataları `LogType.BROWSER` üzerinden otomatik olarak toplar. Firefox, tarayıcı loglarını WebDriver üzerinden açığa çıkarmadığı için JS shim'ini gerektirir.

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