---
description: "REST Assured testlerini TestFly ApiClient'a taşıyın: HTTP, JSON assertion, schema doğrulaması ve UI/API hibrit testler."
id: from-restassured
title: REST Assured'dan Geçiş
sidebar_label: REST Assured'dan
sidebar_position: 6
---

# REST Assured'dan Geçiş

TestFly `ApiClient` ve `BaseApiTest`, HTTP status, JSON ve schema assertion'ları için akıcı bir alternatif sunar. Önce tek bir testle başlayın; REST Assured bağımlılığını kalan testler taşındıktan sonra kaldırın. Tüm REST Assured matcher veya JsonPath davranışlarının birebir eşit olduğunu varsaymayın.

## Temel farklar

| Konu | TestFly yaklaşımı |
|---|---|
| HTTP motoru | JDK `java.net.http.HttpClient` |
| Assertions | `ApiResponse` üzerinden status, JSON, body ve süre kontrolleri |
| JSON Schema | İsteğe bağlı `com.networknt:json-schema-validator:1.4.3` dependency |
| UI + API | `BaseTest` içinde her iki API; `ScenarioContext` ile test kapsamındaki veri paylaşımı |
| Yapılandırma | `testfly.yml` ve isimlendirilmiş auth profilleri |

Kurulum sürümü için [Başlangıç](../getting-started.md) sayfasını kullanın. Güncel development API'leri için checkout artifact'ini yerel olarak kurmanız gerekebilir.

## GET ve durum kontrolü

```java
// REST Assured
 given().baseUri("https://api.example.com")
    .header("Accept", "application/json")
    .when().get("/users/42")
    .then().statusCode(200);

// TestFly
ApiClient.get("/users/42")
    .header("Accept", "application/json")
    .send()
    .assertStatus(200);
```

## JSON POST ve değer okuma

```java
ApiResponse response = ApiClient.post("/auth/login")
    .body(Map.of("username", "admin", "password", "secret"))
    .send()
    .assertStatus(200);
String token = response.json("$.accessToken");
```

`ApiClient`, `ApiResponse` ve `java.util.Map` import'larını ekleyin. `api.baseUrl` değerini test YAML'ında tanımlayın.

## Path ve query parametreleri

```java
ApiClient.get("/orders/{orderId}")
    .pathParam("orderId", 101)
    .queryParam("status", "shipped")
    .send();
```

## JSON Schema

Schema dosyasını `src/test/resources/schemas/product.json` içine koyun ve isteğe bağlı validator dependency'sini ekleyin:

```java
ApiClient.get("/products/1")
    .send()
    .assertSchema("schemas/product.json");
```

[Schema Validation](../guides/api-schema-validation.md) rehberi dependency ve OpenAPI kapsamını açıklar.

## UI/API hibrit test

```java
import io.testfly.test.BaseTest;
import io.testfly.client.ApiResponse;
import java.util.Map;
import org.testng.annotations.Test;

public class CheckoutHybridTest extends BaseTest {
    @Test
    public void seedViaApiAndVerifyInBrowser() {
        ApiResponse order = apiClient().post("/api/orders")
            .body(Map.of("item", "Laptop", "quantity", 1))
            .send()
            .assertStatus(201);
        String orderId = order.json("$.orderId");
        open("/orders/" + orderId);
        assertThat(find("#order-status")).hasText("CONFIRMED");
    }
}
```

Örnek endpoint ve DOM seçicileri uygulamanıza göre değiştirin; bu örnek gerçek bir backend ve tarayıcı gerektirir.
