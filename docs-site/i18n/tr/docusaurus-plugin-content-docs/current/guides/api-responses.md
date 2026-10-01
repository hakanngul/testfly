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

Şema dosyalarını `src/test/resources/schemas/` altına yerleştirin. Aşağıdaki [Şema Doğrulaması](api-schema-validation.md) bölümüne bakın.

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
