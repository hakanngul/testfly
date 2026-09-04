---
description: "Selenium'da HTML tablolarıyla çalışın: başlıkları okuyun, hücre metnine göre satırları bulun, hücre değerlerini doğrulayın ve kırılgan XPath dizinleri olmadan yineleyin."
id: tables
title: Tablolarla çalışma
sidebar_label: Tables
---

# Tablolarla çalışma

HTML tabloları, kırılgan Selenium testlerinin en yaygın kaynaklarından biridir. Çözüm, mutlak satır dizinleri yerine yapıyı ve anlamı hedeflemektir: başlıkları okuyun, bilinen bir değeri içeren satırı bulun, ardından önemsediğiniz hücreyi okuyun.

---

## Satır içeriğine göre bir hücre değerini doğrulama

İlk hücresi bilinen bir anahtar içeren satırı bulun, ardından başka bir sütundaki değeri doğrulayın:

```java title="OrdersPage.java"
import io.testfly.test.BasePage;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

public class OrdersPage extends BasePage {

    private static final By TABLE = By.id("orders");

    public String statusOf(String orderId) {
        return cellByRowText(TABLE, orderId, "Status");
    }

    private String cellByRowText(By table, String rowKey, String columnName) {
        WebElement tableEl = find(table).first();

        // Map header text to column index
        int columnIndex = -1;
        for (WebElement th : tableEl.findElements(By.tagName("th"))) {
            columnIndex++;
            if (th.getText().trim().equals(columnName)) break;
        }

        // Find the row whose first cell (or any cell) contains rowKey
        for (WebElement row : tableEl.findElements(By.cssSelector("tbody tr"))) {
            if (row.getText().contains(rowKey)) {
                return row.findElements(By.tagName("td")).get(columnIndex).getText().trim();
            }
        }
        throw new AssertionError("Row not found: " + rowKey);
    }
}
```

```java title="OrdersTest.java"
public class OrdersTest extends BaseTest {

    @Test
    public void orderStatusIsShipped() {
        open("/orders");
        OrdersPage page = new OrdersPage();
        softAssert().that(page.statusOf("ORD-1234").equals("Shipped"), "Order ORD-1234 should be Shipped");
    }
}
```

---

## Satırları sayma

```java
int visibleRows = find("#orders tbody tr").count();
softAssert().that(visibleRows > 0, "Should have at least one visible row");
```

---

## Bir satırın görünmesini bekleme

```java
assertThat(find("#orders tbody tr")).count(3);   // auto-waits until 3 rows exist
```

---

## Bir satırın varlığını doğrulama

Eşleşen satıra odaklanmak için `withText(...)` kullanın ve ardından görünürlüğünü doğrulayın:

```java
assertThat(find("#orders tbody tr").withText("ORD-1234")).isVisible();
```

---

## Kararlı tablo testleri için ipuçları

- **Asla satır dizinlerini sabit kodlamayın.** Başka bir testten gelen yeni bir satır her dizini kaydırabilir.
- **Sütun numaralarını değil, başlık adlarını kullanın.** Sütunların sırası, etiketlerinden daha sık değişir.
- **Sorguları tabloyla sınırlandırın.** Genel `tr` seçicileri yerine `#orders tbody tr` kullanın.
- **DOM sırasını değil, görünür metni doğrulayın.** Bir kullanıcının hata raporu "üçüncü `td` yanlış" değil, "Durum Beklemede gösteriyor" der.

---

**Daha derin referans:** [Locator](/docs/guides/smart-locator) — eşleşen öğeleri sayma, filtreleme ve üzerlerinde yineleme.