---
description: "Work with HTML tables in Selenium: read headers, find rows by cell text, assert cell values, and iterate without brittle XPath indexes."
id: tables
title: Work with tables
sidebar_label: Tables
---

# Work with tables

HTML tables are one of the most common sources of brittle Selenium tests. The fix is to target structure and meaning instead of absolute row indexes: read the headers, find the row that contains a known value, then read the cell you care about.

---

## Assert a cell value by row content

Find the row whose first cell contains a known key, then assert the value in another column:

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

## Count rows

```java
int visibleRows = find("#orders tbody tr").count();
softAssert().that(visibleRows > 0, "Should have at least one visible row");
```

---

## Wait for a row to appear

```java
assertThat(find("#orders tbody tr")).count(3);   // auto-waits until 3 rows exist
```

---

## Iterate over rows

Use `find(...).all()` to collect rows and assert across them:

```java
List<WebElement> rows = find("#orders tbody tr").all();
List<String> ids = rows.stream()
    .map(r -> r.findElement(By.cssSelector("td:first-child")).getText())
    .toList();

assertTrue(ids.contains("ORD-1234"));
```

---

## Tips for stable table tests

- **Never hard-code row indexes.** A new row from another test can shift every index.
- **Use header names, not column numbers.** The order of columns changes more often than their labels.
- **Scope queries to the table.** Use `#orders tbody tr` instead of global `tr` selectors.
- **Assert the visible text, not the DOM order.** A user's bug report says "Status shows Pending," not "the third `td` is wrong."

---

**Deeper reference:** [Locator](/docs/guides/smart-locator) — counting, filtering, and iterating over matching elements.
