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

## Scroll until the list stops growing

Use this when you want to load the entire catalog before making assertions:

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

Two consecutive iterations with the same count usually means the feed has reached the end.

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
while (find("#load-more").isVisible()) {
    int before = find(".product-card").count();
    find("#load-more").click();
    getWait().wait(ExpectedConditions.numberOfElementsToBeMoreThan(
        By.cssSelector(".product-card"), before));
}
```

---

**Deeper reference:** [WaitEngine](/docs/guides/wait-engine) — `waitForPageLoad`, custom `ExpectedConditions`, and other wait patterns.
