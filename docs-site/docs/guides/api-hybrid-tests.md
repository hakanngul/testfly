---
title: "Hybrid UI + API Tests"
---

# Hybrid UI + API Tests

Mix API calls and browser interactions in the same test. Available in `BaseTest` via `apiClient()`:

```java
public class CheckoutTest extends BaseTest {

    @Test
    public void placeOrder() {
        // Set up order via API (fast)
        ApiResponse order = apiClient().post("/api/orders")
                .body(Map.of("productId", 42, "qty", 1))
                .send()
                .assertStatus(201);

        String orderId = order.json("$.orderId");

        // Verify in the UI
        open("/orders/" + orderId);
        assertThat(By.id("status")).hasText("Pending");
    }
}
```
