---
title: "Hibrit UI + API Testleri"
---

# Hibrit UI + API Testleri

Aynı test içinde API çağrılarını ve tarayıcı etkileşimlerini karıştırın. `BaseTest` içinde `apiClient()` aracılığıyla kullanılabilir:

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
