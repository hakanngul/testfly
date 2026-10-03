---
description: "Selenium'da tarayıcı uyarılarını, onay diyaloglarını ve istemlerini yönetin: TestFly'nin bekleme destekli yardımcılarıyla uyarıyı kabul edin, reddedin, metni okuyun ve istemlere yazın."
id: alerts
title: Uyarıları yönetme
sidebar_label: Alerts
---

# Uyarıları yönetme

Aşağıdaki parçalar `BaseTest` içinde `getWait().until(alertIsPresent())` kullanır. Kısa `acceptAlert`/`dismissAlert` yardımcıları korumalı `BasePage` metotlarıdır; bunları sayfa nesnesi içinde kullanın.

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
        getWait().until(org.openqa.selenium.support.ui.ExpectedConditions.alertIsPresent()).accept();

        assertThat(find("#toast")).hasText("Account deleted");
    }
}
```

---

## Uyarıyı reddetme

```java
find("#cancel-order").click();
getWait().until(org.openqa.selenium.support.ui.ExpectedConditions.alertIsPresent()).dismiss();   // clicks Cancel — keeps the order
```

---

## Uyarı metnini doğrulama

```java
find("#submit").click();
org.openqa.selenium.Alert alert = getWait().until(
    org.openqa.selenium.support.ui.ExpectedConditions.alertIsPresent());
String message = alert.getText();
softAssert().that(message.equals("Are you sure you want to submit?"), "Alert text should match");
getWait().until(org.openqa.selenium.support.ui.ExpectedConditions.alertIsPresent()).accept();
```

Ya da tek adımda kabul edip metni yakalayın:

```java
org.openqa.selenium.Alert alert = getWait().until(
    org.openqa.selenium.support.ui.ExpectedConditions.alertIsPresent());
String message = alert.getText();
alert.accept();
softAssert().that(message.equals("Item added to cart"), "Alert text should match");
```

---

## İsteme yazma

```java
find("#rename").click();
org.openqa.selenium.Alert alert = getWait().until(
    org.openqa.selenium.support.ui.ExpectedConditions.alertIsPresent());
alert.sendKeys("new-name");
alert.accept();   // types and clicks OK
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