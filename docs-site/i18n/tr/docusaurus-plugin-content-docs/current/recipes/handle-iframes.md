---
description: "Selenium'da iframe'leri manuel switchTo() takibi olmadan yönetin: TestFly'nin withinFrame yöntemi eylemlerinizi çerçevenin içinde çalıştırır ve iç içe olsa bile önceki bağlamı otomatik olarak geri yükler."
id: handle-iframes
title: Iframe'leri yönetme
sidebar_label: Handle iframes
---

# Iframe'leri yönetme

Bir `<iframe>` içindeki içerikle etkileşime geçmek normalde `driver.switchTo().frame(...)` çağırmak ve geri geçiş yapmayı hatırlamak anlamına gelir. `BasePage` içindeki `withinFrame(...)` bu işin takibini sizin için yapar: içeri geçer, eyleminizi çalıştırır ve önceki bağlamı geri yükler — iç içe olsa bile.

```java title="CheckoutPage.java"
import io.testfly.test.BasePage;
import org.openqa.selenium.By;

public class CheckoutPage extends BasePage {

    public void payWithCard(String number) {
        withinFrame(By.id("payment-iframe"), () -> {
            type(By.id("card-number"), number);
            click(By.id("pay"));
        });
        // Back in the main document automatically here.
    }
}
```

İç içe yerleştirme güvenlidir — iç çerçeveler en üst belgeye değil, **üst** çerçevelerine geri döner:

```java
withinFrame(By.id("outer-iframe"), () -> {
    withinFrame(By.id("inner-iframe"), () -> {
        click(By.id("confirm"));
    });
    click(By.id("next"));   // still inside outer-iframe
});
```

Bir çerçeveyi dizine göre mi yoksa `name`/`id` özniteliğine göre mi hedeflemeyi tercih edersiniz? `withinFrameIndex(int, ...)` veya `withinFrameName(String, ...)` kullanın.

**Daha derin referans:** [BasePage](/docs/guides/base-page) — sayfa nesneleri için temel sınıf ve çerçeve yardımcıları.