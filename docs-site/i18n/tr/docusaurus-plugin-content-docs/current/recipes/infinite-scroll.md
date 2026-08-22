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
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

public class ProductListPage extends BasePage {

    private static final By PRODUCTS = By.cssSelector(".product-card");

    public void scrollUntilProductVisible(String productId) {
        By target = By.cssSelector("[data-product-id='" + productId + "']");

        for (int i = 0; i < 50; i++) {   // generous upper bound, not a fixed expectation
            if (find(target).count() > 0) {
                return;                  // found it
            }
            scrollToBottom();
            // Wait for the DOM to settle and at least one new card to render
            getWait().wait(ExpectedConditions.numberOfElementsToBeMoreThan(PRODUCTS, i * 10));
        }
        throw new AssertionError("Product not loaded after scrolling: " + productId);
    }
}
```

```java title="ProductTest.java"
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

## Liste büyümeyi durdurana kadar kaydırma

Doğrulamalar yapmadan önce kataloğun tamamını yüklemek istediğinizde bunu kullanın:

```java
public int loadAllProducts() {
    int previousCount = 0;
    int sameCountIterations = 0;

    while (sameCountIterations < 2) {
        scrollToBottom();
        getWait().waitForPageLoad();

        int currentCount = find(".product-card").count();
        if (currentCount == previousCount) {
            sameCountIterations++;
        } else {
            sameCountIterations = 0;
            previousCount = currentCount;
        }
    }
    return previousCount;
}
```

Ardışık iki yinelemede aynı sayının görülmesi genellikle akışın sona ulaştığı anlamına gelir.

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
while (find("#load-more").isVisible()) {
    int before = find(".product-card").count();
    find("#load-more").click();
    getWait().wait(ExpectedConditions.numberOfElementsToBeMoreThan(
        By.cssSelector(".product-card"), before));
}
```

---

**Daha derin referans:** [WaitEngine](/docs/guides/wait-engine) — `waitForPageLoad`, özel `ExpectedConditions` ve diğer bekleme desenleri.