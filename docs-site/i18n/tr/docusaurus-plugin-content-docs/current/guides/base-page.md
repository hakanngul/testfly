---
description: "TestFly'de BasePage: bekleme destekli click, type, getText, dropdown, iframe ve upload yardımcı metodları sayesinde sayfa nesnelerinde asla ham Selenium yazmazsınız."
id: base-page
title: BasePage
sidebar_position: 2
---

# BasePage

`BasePage`, sayfa nesneleri (page object) için kullanıma hazır yardımcı metodlar sağlar; böylece testlerinizde asla ham Selenium çağrıları yazmazsınız.

---

## Sayfa nesnesi oluşturma

```java
import io.testfly.test.BasePage;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

public class LoginPage extends BasePage {

    private static final By USERNAME = By.id("username");
    private static final By PASSWORD = By.id("password");
    private static final By SUBMIT   = By.id("submit");

    public LoginPage(WebDriver driver) {
        super(driver);
    }

    public void login(String username, String password) {
        type(USERNAME, username);
        type(PASSWORD, password);
        click(SUBMIT);
    }
}
```

---

## Kullanılabilir metodlar

### `click(By locator)`
Öğenin tıklanabilir olmasını bekler, ardından tıklar.

```java
click(By.id("submit"));
```

### `type(By locator, String text)`
Alanı temizler, ardından verilen metni yazar. Önce görünür olmasını bekler.

```java
type(By.id("username"), "admin");
```

### `getText(By locator)`
Öğenin görünür olmasını bekler, metnini döndürür.

```java
String heading = getText(By.cssSelector("h1"));
```

### `getAttribute(By locator, String attribute)`
Öğenin görünür olmasını bekler, verilen öznitelik değerini döndürür.

```java
String value = getAttribute(By.id("input"), "value");
String href  = getAttribute(By.cssSelector("a.link"), "href");
```

### `isDisplayed(By locator)`
Öğe mevcutsa ve görünürse `true`, aksi halde `false` döndürür. Hata fırlatmaz.

```java
if (isDisplayed(By.id("error-banner"))) {
    // handle error
}
```

---

## WaitEngine'i doğrudan kullanma

Yukarıdaki yardımcı metodların kapsamadığı gelişmiş beklemeler için `getWait()` kullanın:

```java
import io.testfly.wait.WaitEngine;

public class DashboardPage extends BasePage {

    public DashboardPage(WebDriver driver) {
        super(driver);
    }

    public boolean isLoaded() {
        getWait().waitForInvisible(By.cssSelector(".spinner"));
        return isDisplayed(By.id("dashboard-content"));
    }
}
```

---

## Eksiksiz sayfa nesnesi örneği

```java
public class CheckoutPage extends BasePage {

    private static final By QUANTITY  = By.name("qty");
    private static final By PAY_BTN   = By.id("pay-now");
    private static final By SUCCESS   = By.cssSelector(".order-success");

    public CheckoutPage(WebDriver driver) { super(driver); }

    public void setQuantity(int qty) {
        type(QUANTITY, String.valueOf(qty));
    }

    public void pay() {
        click(PAY_BTN);
        getWait().waitForVisible(SUCCESS);
    }

    public String getConfirmationMessage() {
        return getText(SUCCESS);
    }
}
```