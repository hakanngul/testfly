---
description: "Test infinite scroll in Selenium: scroll to the bottom, wait for new items, and stop when the target element appears or the list stops growing."
id: infinite-scroll
title: Handle infinite scroll
sidebar_label: Infinite scroll
---

# Handle infinite scroll

Infinite-scroll feeds load more content as the user nears the bottom of the page. The test pattern is simple: scroll, wait for the item count to grow, repeat — but never use a fixed number of iterations or sleeps.

---

## Scroll until the target item appears

The safest stop condition is finding the element you actually care about:

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

## Load the catalog until its end marker

Use this when you want to load the entire catalog before making assertions:

```java
// Inside ProductListPage (scrollToBottom() is a protected BasePage helper)
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

Use an application-specific end marker such as `.catalog-end`. An unchanged count or `document.readyState` does not prove that asynchronous loading has finished. The iteration bound prevents an endless feed from hanging the test.

---

## Avoid the common pitfalls

| ❌ Don't | ✅ Do |
|---|---|
| `Thread.sleep(2000)` after each scroll | Wait for a real condition: new items, a sentinel element, or no growth |
| Scroll a fixed 10 times | Scroll until the target appears or growth stops |
| Assert immediately after `scrollToBottom()` | Wait for the DOM to update first |
| Use absolute pixel scrolls | Scroll to the bottom of `document.body` so it works at any viewport |

---

## When the scroll trigger is a button

Some feeds use a "Load more" button instead of automatic scroll:

```java
// Inside ProductListPage; imports WaitEngine and ExpectedConditions
while (find("#load-more").isVisible()) {
    int before = find(".product-card").count();
    find("#load-more").click();
    WaitEngine.wait(ExpectedConditions.numberOfElementsToBeMoreThan(
        By.cssSelector(".product-card"), before));
}
```

---

**Deeper reference:** [WaitEngine](/docs/guides/wait-engine) — `WaitEngine.wait(ExpectedCondition)` for custom conditions, `waitForPageLoad()`, and other wait patterns.
