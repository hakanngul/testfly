---
description: "Selenium'da Shadow DOM'u yönetin: TestFly, shadowFind, shadowClick ve shadowType ile gölge köklerini deler ve shadowPierce ile iç içe web bileşenleri arasında gezinir — manuel JavaScript gerekmez."
id: handle-shadow-dom
title: Shadow DOM'u yönetme
sidebar_label: Handle Shadow DOM
---

# Shadow DOM'u yönetme

Bir web bileşeninin **gölge kökü** içindeki öğelere sayfadaki sıradan CSS ile erişilemez. `BasePage` size gölge sınırını sizin için delen yardımcılar sunar — elle yazılmış JavaScript gerekmez.

```java title="SettingsPage.java"
import io.testfly.test.BasePage;
import org.openqa.selenium.By;

public class SettingsPage extends BasePage {

    public void updateEmail(String email) {
        // Host element <my-form>, then a selector scoped to its shadow root:
        shadowType(By.cssSelector("my-form"), "#email", email);
        shadowClick(By.cssSelector("my-form"), "#save");
    }

    public String bannerText() {
        return shadowGetText(By.cssSelector("my-banner"), ".message");
    }
}
```

**İç içe** gölge kökleri (bir bileşenin içindeki bir bileşen) — her seviyede ana bilgisayar seçicisini ileterek `shadowPierce(...)` ile aralarında gezinin:

```java
// <checkout-flow> → shadow → <payment-widget> → shadow → #pay-btn
WebElement payBtn = shadowPierce("checkout-flow", "payment-widget", "#pay-btn");
payBtn.click();
```

İsteğe bağlı gölge içeriğini `try/catch` olmadan koruyun — `shadowExists(...)` asla hata fırlatmaz:

```java
if (shadowExists(By.cssSelector("my-form"), ".error")) {
    // handle validation error
}
```

:::caution Yalnızca CSS
Gölge kökü seçicileri **CSS** olmalıdır — XPath bir gölge sınırını geçemez.
:::

**Daha derin referans:** [BasePage](/docs/guides/base-page) — sayfa nesneleri için temel sınıf ve Shadow DOM yardımcıları.