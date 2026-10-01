---
title: "İstekler ve Yapılandırma"
---

# İstekler ve Yapılandırma

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
