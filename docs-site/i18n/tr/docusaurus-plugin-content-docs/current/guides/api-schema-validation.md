---
title: "Şema Doğrulaması"
---

# Şema Doğrulaması

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

## OpenAPI yanıt doğrulaması (development)

İmza: `ApiResponse` üzerinde `ApiResponse assertOpenApi(String specPath)`, `@TestFlyApi(since = "1.2.0")` ile işaretli. Bu annotation yayımlanmış sürüm garantisi değildir; özelliği kullanmak için güncel kaynak artifact'ini yerel olarak kurun (`mvn clean install -DskipTests`). Aynı `ApiResponse` nesnesini döndürdüğü için diğer assertion'larla zincirlenebilir.

İsteğe bağlı validator dependency'sini ekleyin (TestFly bunu `<optional>` olarak tanımlar, transitif olarak gelmez):

```xml
<dependency>
  <groupId>com.atlassian.oai</groupId>
  <artifactId>swagger-request-validator-core</artifactId>
  <version>2.39.0</version>
</dependency>
```

Dependency yoksa `assertOpenApi`, mesajında bu dependency parçacığını içeren bir `IllegalStateException` fırlatır.

`src/test/resources/contracts/users.yaml` içine örnek spec koyun:

```yaml
openapi: 3.0.3
info:
  title: Users API
  version: '1.0'
paths:
  /users/1:
    get:
      responses:
        '200':
          description: User response
          content:
            application/json:
              schema:
                type: object
                required: [id, name]
                properties:
                  id:
                    type: integer
                  name:
                    type: string
```

```java
ApiClient.get("/users/1")
    .send()
    .assertStatus(200)
    .assertOpenApi("src/test/resources/contracts/users.yaml");
```

Nasıl çalışır:

- `specPath` değiştirilmeden Atlassian `OpenApiInteractionValidator.createFor(...)` metoduna verilir; bu metot dosya yolu (çalışma dizinine göre), classpath kaynağı veya URL kabul eder. Ayrıştırılan validator, JVM ömrü boyunca `specPath` başına önbelleğe alınır.
- Yalnızca **yanıt** doğrulanır: HTTP method, request URL, status ve body kullanılır. Request body/header ve response header değerleri validator'a taşınmaz; bu tam request/response contract doğrulaması değildir. Zorunlu response header kontrolü için ayrıca `.assertHeader(...)` kullanın.
- Okunamayan veya geçersiz spec `IllegalArgumentException`, contract uyuşmazlığı ise tüm validator mesajlarını listeleyen bir `AssertionError` üretir.

:::warning Güncel kaynaktaki bilinen kısıt
Adapter, validator'ın operasyon yolu beklediği yere **mutlak** request URL'ini (örneğin `https://api.example.com/users/1`) verir ve response `Content-Type` değerini iletmez. Validator 2.39.0 ile gerçek bir `ApiClient` yanıtı bu nedenle — spec eşleşen bir `servers` girdisi tanımlasa bile — `No API path found that matches request '…'` hatasıyla başarısız olur; content type iletilmediği için body şema denetimi de yapılmaz. Adapter düzeltilene kadar body doğrulaması için OpenAPI bileşeninizden çıkardığınız bir JSON Schema ile `assertSchema(...)` kullanın.
:::
