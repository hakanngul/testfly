---
description: "TestFly'de API testleri: aynı suite, yapılandırma ve HTML raporunda saf API testleri veya hibrit UI artı API testleri yazın."
sidebar_position: 13
---

# API Testleri

TestFly, kutu dışında **saf API testlerini** ve **hibrit UI + API testlerini** destekler — aynı framework, aynı yapılandırma, aynı rapor.

---

## Saf API Testleri — `BaseApiTest`

`BaseTest` yerine `BaseApiTest`'i genişletin. Tarayıcı başlatılmaz; framework yaşam döngüsünün tamamı yine de geçerlidir (raporlama, `@TestData`, retry, CI eşikleri).

```java
import io.testfly.test.BaseApiTest;
import io.testfly.client.ApiClient;
import io.testfly.client.ApiResponse;
import org.testng.annotations.Test;

public class UserApiTest extends BaseApiTest {

    @Test
    public void getUserById() {
        ApiResponse res = ApiClient.get("https://api.example.com/users/1")
                .send();

        res.assertStatus(200);
        res.assertJson("$.name", "John Doe");
    }
}
```

---

## `ApiClient` — Akıcı HTTP İstemcisi

### Desteklenen metodlar

```java
ApiClient.get("/api/users")
ApiClient.post("/api/users")
ApiClient.put("/api/users/1")
ApiClient.patch("/api/users/1")
ApiClient.delete("/api/users/1")
```

### Taban URL

Varsayılan olarak `ApiClient`, `testfly.yml` içindeki `api.baseUrl` değerini kullanır. Ayarlanmadığında `execution.baseUrl` değerine geri döner.

```yaml
api:
  baseUrl: https://api.example.com
  timeoutSeconds: 30
  logBody: false   # set true to log request/response body in step timeline
```

İstek bazında `ApiClient.to(url)` ile geçersiz kılın:

```java
ApiClient.to("https://other-service.com").get("/health").send();
```

### İstek başlıkları ve gövdesi

```java
ApiClient.post("/api/users")
        .header("X-Request-ID", "abc123")
        .contentType("application/json")
        .body(Map.of("name", "Alice", "email", "alice@example.com"))
        .send();
```

---

## `ApiResponse` — Doğrulamalar ve Çıkarma

### Durum doğrulaması

```java
res.assertStatus(201);
```

### Gövde doğrulamaları

```java
res.assertBodyContains("success");
res.assertJson("$.user.name", "Alice");
```

### JSONPath çıkarma

```java
String token = res.json("$.token");
int    id     = res.json("$.user.id", Integer.class);
JsonNode node = res.jsonNode("$.user.metadata"); // doğrudan Jackson JsonNode erişimi
```

### Gelişmiş JSON Doğrulamaları

```java
// Sayısal karşılaştırmalar
res.assertJsonGreaterThan("$.score", 85.0);
res.assertJsonLessThan("$.responseTimeMs", 500.0);

// Alt metin (substring) kontrolü
res.assertJsonContains("$.email", "@company.com");

// Mantıksal (boolean) kontroller
res.assertJsonTrue("$.verified");
res.assertJsonFalse("$.isSuspended");

// Özel fonksiyonel koşullar (predicates)
res.assertJson("$.roles", node -> node.isArray() && node.size() >= 2, "en az 2 kullanıcı rolü");
```

### Nesneye dönüştürme (deserialization)

```java
User user = res.asObject(User.class);
```

### Şema doğrulaması

Yanıt yapısını bir JSON Schema dosyasına göre doğrulayın:

```java
res.assertStatus(200).assertSchema("schemas/user.json");
```

Şema dosyalarını `src/test/resources/schemas/` altına yerleştirin. Aşağıdaki [Şema Doğrulaması](#schema-validation) bölümüne bakın.

### Ham erişim

```java
int    status   = res.status();
String body     = res.body();
long   duration = res.durationMs();
```

### Akıcı zincirleme

```java
ApiClient.get("/api/users/1")
        .send()
        .assertStatus(200)
        .assertJson("$.name", "Alice")
        .assertJsonTrue("$.active")
        .assertSchema("schemas/user.json");
```

---

## Asenkron Polling — `pollUntil`

Mikroservis mimarilerinde ve event-driven sistemlerde arka plan işlerini veya nihai tutarlılığı (eventual consistency) beklemek yaygındır. Rastgele `Thread.sleep()` kullanmak yerine `pollUntil()` metodundan yararlanın:

```java
import java.time.Duration;

// Koşul sağlanana veya zaman aşımına kadar yoklar (varsayılan 500ms aralık)
ApiResponse res = ApiClient.get("/api/jobs/job-12345")
        .pollUntil(r -> "COMPLETED".equals(r.json("$.status")), Duration.ofSeconds(30));

res.assertJson("$.result", "SUCCESS");

// Özel yoklama aralığı ile
ApiResponse order = ApiClient.get("/api/orders/ord-999")
        .pollUntil(r -> r.status() == 200, Duration.ofSeconds(15), Duration.ofMillis(250));
```

Yoklama sırasında `ApiClient`, denemeleri `StepLogger` üzerine kaydeder (`[API Polling] Started...`, `[API Polling] Condition satisfied in 1420ms`). Süre aşımına kadar koşul sağlanmazsa detaylı bir `ApiException` fırlatılır.


---

## Şema Doğrulaması {#schema-validation}

Bir yanıtın bir JSON Schema (Draft-07) ile eşleştiğini doğrulayın:

**`src/test/resources/schemas/user.json`**
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["id", "name", "email"],
  "properties": {
    "id":    { "type": "integer" },
    "name":  { "type": "string", "minLength": 1 },
    "email": { "type": "string" }
  }
}
```

```java
ApiClient.get("/api/users/1")
        .send()
        .assertStatus(200)
        .assertSchema("schemas/user.json");
```

`pom.xml` dosyanızda `com.networknt:json-schema-validator` gerekir:

```xml
<dependency>
  <groupId>com.networknt</groupId>
  <artifactId>json-schema-validator</artifactId>
  <version>1.4.3</version>
</dependency>
```

---

## Hibrit UI + API Testleri

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

---

## Adım Zaman Çizelgesi ve API İstek İzi

Her `ApiClient` isteği otomatik olarak adım zaman çizelgesine kaydedilir:

```
[API] GET /api/users/1 → 200 (143ms)
[API] POST /api/orders → 201 (89ms)
[API] DELETE /api/orders/5 → 404 (12ms)   ← logged as FAIL
```

`testfly.yml` içinde gövde ve cURL loglamayı etkinleştirin:

```yaml
api:
  logBody: true
```

`logBody: true` etkinleştirildiğinde:
- HTML raporu, doğrudan terminalinize veya Postman'e kopyalayıp çalıştırabileceğiniz genişletilebilir bir **cURL komut bloğu** sunar.
- Biçimlendirilmiş istek ve yanıt JSON gövdeleri (payload), testin detay çekmecesi içerisinde özel kod bloklarında vurgulanır.