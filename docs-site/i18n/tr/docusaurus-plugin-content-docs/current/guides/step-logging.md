---
description: "Selenium'da adlandırılmış test adımlarını loglayın: StepLogger, HTML raporundaki her testin ayrıntı panelinde ekran görüntüleriyle birlikte bir zaman çizelgesi oluşturur."
id: step-logging
title: Adım Loglama
sidebar_position: 6
---

# Adım Loglama

`StepLogger`, test yürütme sırasında adlandırılmış adımları loglamanızı sağlar. Adımlar, HTML raporunda her testin ayrıntı panelinin içinde bir zaman çizelgesi olarak görünür.

---

## Temel kullanım

```java
import io.testfly.steps.StepLogger;
import io.testfly.steps.StepStatus;

public class LoginTest extends BaseTest {

    @Test(description = "Valid user can log in")
    public void loginTest() {
        StepLogger.step("Open login page");
        open();

        StepLogger.step("Enter credentials");
        loginPage.login("admin", "secret");

        StepLogger.step("Assert dashboard visible", StepStatus.PASS);
        softAssert().that(dashboardPage.isLoaded(), "Dashboard should be loaded");
    }
}
```

---

## API

### `StepLogger.step(String name)`
`INFO` durumuyla ve ekran görüntüsü olmadan bir adım loglar.

```java
StepLogger.step("Navigate to login page");
```

### `StepLogger.step(String name, boolean screenshot)`
`INFO` durumuyla bir adım loglar. `true` olduğunda, satır içi ekran görüntüsü yakalar.

```java
StepLogger.step("After form submission", true);  // burada ekran görüntüsü yakalanır
```

### `StepLogger.step(String name, StepStatus status)`
Açık bir durumla bir adım loglar — `INFO`, `PASS` veya `FAIL`.

```java
StepLogger.step("Verify order total", StepStatus.PASS);
StepLogger.step("Payment rejected", StepStatus.FAIL);
```

### `StepLogger.step(String name, StepStatus status, boolean screenshot)`
Açık durumu isteğe bağlı ekran görüntüsüyle birleştirir.

```java
StepLogger.step("Assert confirmation page", StepStatus.PASS, true);
```

---

## Adım durumları

| Durum | Ne zaman kullanılır |
|---|---|
| `INFO` | Varsayılan — nötr adım, hiçbir sonuç ima edilmez |
| `PASS` | Bir doğrulamanın geçtiğini açıkça işaretlemek |
| `FAIL` | Bir adımın başarısız olduğunu açıkça işaretlemek (test devam eder) |

---

## HTML raporunda

Adımlar, bir test satırını genişlettiğinizde **ayrıntı panelinde** (Test Cases sekmesi) veya ayrıntı panellerinin önceden açıldığı Failures sekmesinde görünür.

Her adım şunları gösterir:
- Adım numarası
- Adım adı
- Test başlangıcından itibaren zaman farkı (örn. `+312ms`)
- Durum rozeti (INFO / PASS / FAIL)
- Bir ekran görüntüsü yakalanmışsa küçük resim (tam boyutlu lightbox'ı açmak için tıklayın)

---

## Thread güvenliği

`StepLogger`, adımları doğru teste bağlamak için `TestFlyContext.getCurrentTestId()` kullanır. **Thread güvenlidir** — paralel test yürütmede kullanım güvenlidir. Her thread'in adımları bağımsız olarak kaydedilir.

---

## Retry davranışı

Bir test yeniden denendiğinde, önceki denemenin adımları retry başlamadan önce **otomatik olarak temizlenir**. Rapor yalnızca son denemenin adımlarını gösterir.

---

## Örnek — adımlarla tam test

```java
@Test(description = "Complete checkout flow")
public void checkoutTest() {
    StepLogger.step("Open home page");
    open();

    StepLogger.step("Search for product");
    homePage.search("wireless headphones");

    StepLogger.step("Select first result", true);  // sonuçların ekran görüntüsü
    searchPage.selectFirstResult();

    StepLogger.step("Add to cart");
    productPage.addToCart();

    StepLogger.step("Proceed to checkout", true);  // sepetin ekran görüntüsü
    cartPage.checkout();

    StepLogger.step("Enter payment details");
    checkoutPage.fillPayment("4111111111111111", "12/28", "123");

    StepLogger.step("Place order");
    checkoutPage.placeOrder();

    StepLogger.step("Verify confirmation", StepStatus.PASS, true);  // onay ekran görüntüsü
    softAssert().that(confirmationPage.isSuccess(), "Order should be successful");
}
```