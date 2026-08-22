---
description: "Selenium'da tarayıcı uyarılarını, onay diyaloglarını ve istemlerini yönetin: TestFly'nin bekleme destekli yardımcılarıyla uyarıyı kabul edin, reddedin, metni okuyun ve istemlere yazın."
id: alerts
title: Uyarıları yönetme
sidebar_label: Alerts
---

# Uyarıları yönetme

Tarayıcı uyarıları (`alert()`, `confirm()`, `prompt()`) WebDriver komut kuyruğunu bloke eder. Uyarı reddedilene kadar sayfa ile etkileşime geçemezsiniz. TestFly'nin `BasePage` yardımcıları uyarının görünmesini bekler, ardından uyarıyı kabul eder, reddeder, okur veya içine yazar — hepsi tek bir çağrıda.

---

## Uyarıyı kabul etme

```java title="DeleteTest.java"
import io.testfly.test.BaseTest;
import org.testng.annotations.Test;

public class DeleteTest extends BaseTest {

    @Test
    public void deleteAccountShowsConfirmation() {
        open("/account");
        find("#delete-account").click();

        // Clicks OK on the browser confirm()
        acceptAlert();

        assertThat(find("#toast")).hasText("Account deleted");
    }
}
```

---

## Uyarıyı reddetme

```java
find("#cancel-order").click();
dismissAlert();   // clicks Cancel — keeps the order
```

---

## Uyarı metnini doğrulama

```java
import org.testng.Assert;

find("#submit").click();
String message = getAlertText();
Assert.assertEquals(message, "Are you sure you want to submit?");
acceptAlert();
```

Ya da tek adımda kabul edip metni yakalayın:

```java
String message = getAndAcceptAlert();
Assert.assertEquals(message, "Item added to cart");
```

---

## İsteme yazma

```java
find("#rename").click();
typeInAlert("new-name");   // types and clicks OK
```

---

## Sayfa nesnesi yardımcısı

Uyarı etkileşimini bir sayfa nesnesinde kapsülleyin, böylece testler niyet düzeyinde okunur:

```java title="AccountPage.java"
import io.testfly.test.BasePage;
import org.openqa.selenium.By;

public class AccountPage extends BasePage {

    private static final By DELETE_BUTTON = By.id("delete-account");

    public void deleteAccount() {
        click(DELETE_BUTTON);
        acceptAlert();
    }

    public String confirmTextThenAccept() {
        click(DELETE_BUTTON);
        return getAndAcceptAlert();
    }
}
```

---

## Ya uyarı beklenmedikse?

Bir uyarı görünür ancak testiniz onu beklemiyorsa, sonraki her WebDriver komutu `UnhandledAlertException` fırlatır. Çerçevenin hata işleme mekanizması bir ekran görüntüsü alır, ancak uyarının kendisi daha sonraki etkileşimi engeller.

Testleri dayanıklı hale getirmek için:

- Uyarıları her zaman aynı sayfa nesnesi metodunda tetikleyin ve yönetin.
- Bir testin başıboş uyarılar bıraktığı biliniyorsa bir temizlik kancasında `dismissAlert()` kullanın.
- Engelleyici olmayan bildirimler için `alert()` çağıran JavaScript'ten kaçının — bunun yerine sayfa içi toast bildirimlerini kullanın.

---

**Daha derin referans:** [BasePage](/docs/guides/base-page) — tüm uyarı, fareyle üzerine gelme, kaydırma ve JavaScript yardımcıları.