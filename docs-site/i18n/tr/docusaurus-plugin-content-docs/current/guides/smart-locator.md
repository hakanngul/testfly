---
description: "SmartLocator, birden fazla Selenium locator stratejisini sırayla dener ve görünür ilk eşleşmeyi döndürür: kırılgan seçiciler için dayanıklı locator'lar."
id: smart-locator
title: SmartLocator
sidebar_position: 9
---

# SmartLocator

`SmartLocator`, birden fazla locator stratejisini sırayla dener ve bulunup görünür olan ilk öğeyi döndürür. Bir öğenin locator'ının ortamlar, tarayıcılar veya uygulama sürümleri arasında farklılık gösterebileceği durumlarda kullanın.

---

## Temel kullanım

```java
import io.testfly.test.SmartLocator;

// Önce CSS'i deneyin, XPath'e geri dönün
WebElement btn = SmartLocator.find(driver,
    By.cssSelector(".submit-btn"),
    By.xpath("//button[@type='submit']"),
    By.id("submit")
);
```

---

## Sayfa nesnesinin içinden

```java
public class LoginPage extends BasePage {

    public void clickSubmit() {
        // dayanıklı — bu locator'lardan herhangi biri eşleşirse çalışır
        WebElement btn = SmartLocator.find(driver,
            By.id("submit"),
            By.cssSelector("button[type='submit']"),
            By.xpath("//button[contains(text(),'Log in')]")
        );
        btn.click();
    }
}
```

---

## Hata fırlatmadan görünürlüğü kontrol edin

```java
boolean anyVisible = SmartLocator.isAnyVisible(driver,
    By.id("error-banner"),
    By.cssSelector(".alert-error")
);

if (anyVisible) {
    // hata durumunu işle
}
```

---

## SmartLocator ne zaman kullanılır

| Durum | SmartLocator kullanılsın mı? |
|---|---|
| Öğe locator'ı ortamlar arasında değişiyor | Evet |
| Küçük farklılıkları olan birden fazla tarayıcı | Evet |
| Seçicileri istikrarsız olan aktif geliştirmedeki uygulama | Evet |
| Kararlı, iyi korunan locator'lar | Hayır — doğrudan `By` kullanın |

---

## Loglama

`SmartLocator` bir öğeyi çözdüğünde, hangi stratejinin başarılı olduğunu loglar:

```
[SmartLocator] Resolved using: By.cssSelector: .submit-btn
```

Bu, CI loglarında hangi locator'ın gerçekte kullanıldığını belirlemeyi kolaylaştırır.

---

## İlgili: Kendini Onaran Locator'lar

`SmartLocator` **önden** hareket eder — aday locator'ları kendiniz listelersiniz.
[Kendini Onaran Locator'lar](./self-healing), bir locator başarısız olduktan **sonra**
hareket eder ve orijinal seçiciden otomatik olarak geri dönüş operatörlerini
türetir ve her onarımı loglar. Varyasyon beklediğiniz yerlerde
`SmartLocator` kullanın ve öngörmediğiniz kaymalar için güvenlik ağı olarak
kendini onarmayı açık bırakın.