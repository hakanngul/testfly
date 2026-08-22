---
description: "Selenium test adımları arasında güvenli şekilde durum paylaşın: yerleşik senaryo ve suite bağlam depoları, statik alanların ve thread-güvenli olmayan globallerin yerini alır."
sidebar_position: 15
---

# Senaryo ve Suite Bağlamı

TestFly, statik alanlar veya thread-güvenli olmayan globaller olmadan adımlar ve testler arasında durum paylaşmak için iki yerleşik bağlam deposu sağlar.

---

## `ScenarioContext` — Test İçi Durum

Tek bir testin süresi boyunca yaşayan thread-local depo. Her testten sonra otomatik olarak temizlenir.

### `BaseTest` / `BaseApiTest` içinde

```java
public class OrderTest extends BaseApiTest {

    @Test
    public void createAndVerifyOrder() {
        // Step 1 — create order, store ID
        ApiResponse res = ApiClient.post("/api/orders")
                .body(Map.of("productId", 42))
                .send()
                .assertStatus(201);

        ctx().set("orderId", res.json("$.orderId"));

        // Step 2 — use stored ID in next call
        String orderId = ctx().get("orderId");
        ApiClient.get("/api/orders/" + orderId)
                .send()
                .assertStatus(200);
    }
}
```

### Tipli (typed) alma

```java
ctx().set("userId", 42);
int userId = ctx().get("userId", Integer.class);
```

### Kontrol etme ve kaldırma

```java
boolean has = ctx().has("token");
ctx().remove("token");
ctx().clear();   // clear all entries (done automatically after each test)
```

---

## `SuiteContext` — Testler Arası Durum

Bir `ConcurrentHashMap` ile desteklenir — testler arasında suite çalışmasının tamamı boyunca hayatta kalır. Paralel çalıştırma için thread-güvenlidir.

Bir testin, sonraki testlerin ihtiyaç duyduğu bir kaynak oluşturduğu durumlarda bunu kullanın.

```java
public class ApiFlowTest extends BaseApiTest {

    @Test(priority = 1)
    public void createUser() {
        ApiResponse res = ApiClient.post("/api/users")
                .body(Map.of("name", "Alice"))
                .send()
                .assertStatus(201);

        // Store for later tests
        suiteCtx().set("createdUserId", res.json("$.id"));
    }

    @Test(priority = 2, dependsOnMethods = "createUser")
    public void verifyUserExists() {
        String userId = suiteCtx().get("createdUserId");

        ApiClient.get("/api/users/" + userId)
                .send()
                .assertStatus(200)
                .assertJson("$.name", "Alice");
    }

    @Test(priority = 3, dependsOnMethods = "createUser")
    public void deleteUser() {
        String userId = suiteCtx().get("createdUserId");

        ApiClient.delete("/api/users/" + userId)
                .send()
                .assertStatus(204);
    }
}
```

### Metodlar

```java
suiteCtx().set("key", value);
suiteCtx().get("key");
suiteCtx().get("key", Integer.class);   // typed
suiteCtx().has("key");
suiteCtx().remove("key");
suiteCtx().clear();
```

---

## Bağlamla Hibrit UI + API

Aynı test içinde bir API çağrısı ile bir tarayıcı adımı arasında veri paylaşın:

```java
public class CheckoutTest extends BaseTest {

    @Test
    public void addItemAndVerifyCart() {
        // API — add item to cart, store cart ID
        ApiResponse cart = apiClient().post("/api/cart/items")
                .body(Map.of("productId", 5, "qty", 2))
                .send()
                .assertStatus(200);

        ctx().set("cartId", cart.json("$.cartId"));

        // UI — open cart and verify
        open("/cart/" + ctx().get("cartId"));
        Assert.assertEquals(getText(By.cssSelector(".item-count")), "2 items");
    }
}
```

---

## Hangisi Ne Zaman Kullanılır

| İhtiyaç | Kullanılacak |
|---|---|
| Tek testte adımlar arasında veri iletmek | `ctx()` — `ScenarioContext` |
| A testinden B testine veri iletmek | `suiteCtx()` — `SuiteContext` |
| Oluşturulan bir kaynak kimliğini suite genelinde paylaşmak | `suiteCtx()` |
| Yalnızca tek bir test için token saklamak | `ctx()` |
| Suite genelinde bir kimlik doğrulama tokenı saklamak | `ApiClient.setGlobalAuth()` |