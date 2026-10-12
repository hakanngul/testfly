---
title: "Yanıtlar ve Doğrulamalar"
---

# Yanıtlar ve Doğrulamalar

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

Desteklenen yol sözdizimi (ek bağımlılık yok):

| Sözdizimi | Örnek | Sonuç |
|---|---|---|
| Kök | `$` | tüm doküman |
| Alan / iç içe alan | `$.user.name` | tek değer |
| Dizi indeksi | `$.items[0].id` | tek değer |
| Tırnaklı anahtar | `$['a.b']`, `$["x/y"]` | `.`, `/`, `~` veya boşluk içeren anahtar |
| Joker | `$.items[*].id`, `$.map.*` | tüm eşleşmelerin dizisi |

```java
List<Integer> ids = res.jsonList("$.items[*].id", Integer.class);
res.assertJsonArraySize("$.items[*].tags", 3);
```

Bulunamayan yol `json(...)` için `null` döner. Filtre ifadeleri (`[?(...)]`), dilimler ve `..` `IllegalArgumentException` ile reddedilir.

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

### Durum aileleri ve anlamsal JSON karşılaştırması

```java
res.assertStatusIn(200, 201);
res.assertStatusRange(200, 299);   // veya res.assertSuccessful()

// Tüm gövde veya alt ağaç: anahtar sırası ve 1 / 1.0 farkı yok sayılır, dizi sırası sayılmaz
res.assertJsonEquals("{\"id\":1,\"name\":\"Alice\"}");
res.assertJsonEquals("$.user.roles", "[\"admin\",\"dev\"]", true); // dizi sırasını yok say

// Dizi üyeliği: skaler değerler metin olarak, nesneler anlamsal olarak eşleşir
res.assertJsonArrayContains("$.ids", 42);
res.assertJsonArrayContains("$.users", Map.of("id", 1, "name", "Alice"));
```

Başarısız karşılaştırma ilk farkı gösterir; örneğin `at /items/1/name: expected "a" but got "b"` veya `unexpected field 'extra'`.

### Nesneye dönüştürme (deserialization)

```java
User user = res.asObject(User.class);
```

### Şema doğrulaması

Yanıt yapısını bir JSON Schema dosyasına göre doğrulayın:

```java
res.assertStatus(200).assertSchema("schemas/user.json");
```

Şema dosyalarını `src/test/resources/schemas/` altına yerleştirin. Aşağıdaki [Şema Doğrulaması](api-schema-validation.md) bölümüne bakın.

### Ham erişim

```java
int    status   = res.status();
String body     = res.body();
long   duration = res.durationMs();
```

İkili içerik ve header'lar:

```java
byte[] pdf  = res.asBytes();                       // ham byte'lar, yeniden kodlanmaz
Path saved  = res.saveTo(Path.of("target/report.pdf"));
String type = res.contentType();                   // Content-Type header'ı
Charset cs  = res.charset();                       // body() için kullanılan charset, varsayılan UTF-8
List<String> cookies = res.headerValues("Set-Cookie");
Map<String, List<String>> all = res.headers();
boolean ok  = res.isSuccessful();                  // 2xx
System.out.println(res.prettyBody());              // biçimlendirilmiş JSON veya düz gövde
```

### Hata tipleri

Assertion ve istek hataları `ApiException` (bir `RuntimeException`) fırlatır. Sözleşme kontrolleri (`assertSchema`, `assertOpenApi`) `AssertionError`'dan türeyen `ApiAssertionError` fırlatır. İkisi de `ApiFailure` uygular; bu yüzden her ikisinde de `getStatus()`, `getMethod()`, `getUrl()` ve `getBody()` kullanılabilir.

### Akıcı zincirleme

```java
ApiClient.get("/api/users/1")
        .send()
        .assertStatus(200)
        .assertJson("$.name", "Alice")
        .assertJsonTrue("$.active")
        .assertSchema("schemas/user.json");
```
