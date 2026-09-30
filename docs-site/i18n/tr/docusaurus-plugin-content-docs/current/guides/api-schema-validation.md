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
