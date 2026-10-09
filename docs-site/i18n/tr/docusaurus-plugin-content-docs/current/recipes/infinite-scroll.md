---
description: "Selenium'da sonsuz kaydırmayı test edin: en alta kaydırın, yeni öğeleri bekleyin ve hedef öğe göründüğünde veya liste büyümeyi durdurduğunda durun."
id: infinite-scroll
title: Sonsuz kaydırmayı yönetme
sidebar_label: Infinite scroll
---

# Sonsuz kaydırmayı yönetme

Sonsuz kaydırma akışları, kullanıcı sayfanın altına yaklaştıkça daha fazla içerik yükler. Test deseni basittir: kaydırın, öğe sayısının artmasını bekleyin, tekrarlayın — ancak sabit sayıda yineleme veya bekletme asla kullanmayın.

---

## Hedef öğe görünene kadar kaydırma

En güvenli durma koşulu, gerçekten önemsediğiniz öğeyi bulmaktır:

```java title="ProductListPage.java"
import io.testfly.test.BasePage;
import io.testfly.wait.WaitEngine;
import org.openqa.selenium.By;

public class ProductListPage extends BasePage {

    private static final By PRODUCTS = By.cssSelector(".product-card");

    public void scrollUntilProductVisible(String productId) {
        By target = By.cssSelector("[data-product-id='" + productId + "']");

        for (int i = 0; i < 50; i++) {   // generous upper bound, not a fixed expectation
            if (find(target).count() > 0) {
                return;                  // found it
            }
            int before = find(PRODUCTS).count();
            scrollToBottom();
            WaitEngine.wait(d -> !d.findElements(target).isEmpty()
                || d.findElements(PRODUCTS).size() > before
                || !d.findElements(By.cssSelector(".catalog-end")).isEmpty());
            if (find(target).count() == 0 && find(".catalog-end").count() > 0) {
                throw new AssertionError("Catalog ended before product: " + productId);
            }
        }
        throw new AssertionError("Product not loaded after scrolling: " + productId);
    }
}
```

```java title="ProductTest.java"
import io.testfly.test.BaseTest;
import org.testng.annotations.Test;

public class ProductTest extends BaseTest {

    @Test
    public void oldProductLoadsOnScroll() {
        open("/products");
        ProductListPage list = new ProductListPage();
        list.scrollUntilProductVisible("PROD-1985");
        assertThat(find("[data-product-id='PROD-1985']")).isVisible();
    }
}
```

---

## Kataloğu bitiş işaretine kadar yükleme

Doğrulamalar yapmadan önce kataloğun tamamını yüklemek istediğinizde bunu kullanın:

```java
// ProductListPage içinde (scrollToBottom(), BasePage'in protected yardımcısıdır)
public int loadAllProducts() {
    By products = By.cssSelector(".product-card");
    By end = By.cssSelector(".catalog-end");
    for (int page = 0; page < 100; page++) {
        if (find(end).count() > 0) {
            return find(products).count();
        }
        int before = find(products).count();
        scrollToBottom();
        WaitEngine.wait(d -> d.findElements(products).size() > before
            || !d.findElements(end).isEmpty());
    }
    throw new AssertionError("Catalog did not reach its end within 100 scrolls");
}
```

`.catalog-end` gibi uygulamaya özel bir bitiş işareti kullanın. Değişmeyen öğe sayısı veya `document.readyState`, asenkron yüklemenin tamamlandığını kanıtlamaz. Yineleme sınırı sonsuz akışın testi kilitlemesini önler.

---

## Sık yapılan hatalardan kaçının

| ❌ Yapmayın | ✅ Yapın |
|---|---|
| Her kaydırmadan sonra `Thread.sleep(2000)` | Gerçek bir koşulu bekleyin: yeni öğeler, bir işaret öğesi veya büyümenin durması |
| Sabit 10 kez kaydırın | Hedef görünene veya büyüme durana kadar kaydırın |
| `scrollToBottom()` sonrasında hemen doğrulama yapın | Önce DOM'un güncellenmesini bekleyin |
| Mutlak piksel kaydırmaları kullanın | Her görüntü alanında çalışması için `document.body` öğesinin en altına kaydırın |

---

## Kaydırma tetikleyicisi bir düğme olduğunda

Bazı akışlar otomatik kaydırma yerine "Daha fazla yükle" düğmesi kullanır:

```java
// ProductListPage içinde; WaitEngine ve ExpectedConditions import edilmiş olmalı
while (find("#load-more").isVisible()) {
    int before = find(".product-card").count();
    find("#load-more").click();
    WaitEngine.wait(ExpectedConditions.numberOfElementsToBeMoreThan(
        By.cssSelector(".product-card"), before));
}
```

---

**Daha derin referans:** [WaitEngine](/docs/guides/wait-engine) — özel koşullar için `WaitEngine.wait(ExpectedCondition)`, `waitForPageLoad()` ve diğer bekleme desenleri.