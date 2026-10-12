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
ApiClient.head("/api/users/1")      // yalnızca header, boş gövde
ApiClient.options("/api/users")     // örn. Allow header'ını oku
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
ApiClient.to("https://other-service.com").path("/health").get().send();
```

### İstek başlıkları ve gövdesi

```java
ApiClient.post("/api/users")
        .header("X-Request-ID", "abc123")
        .contentType("application/json")
        .body(Map.of("name", "Alice", "email", "alice@example.com"))
        .send();
```

### Query parametreleri

`queryParam` bir değer atar ve aynı isimli önceki değerlerin yerine geçer. Tekrarlanan anahtarlar için `addQueryParam` / `addQueryParams` kullanın:

```java
ApiClient.get("/api/products")
        .addQueryParam("id", 1).addQueryParam("id", 2)   // ?id=1&id=2
        .addQueryParams("tag", List.of("new", "sale"))   // &tag=new&tag=sale
        .queryParam("page", 1)
        .send();
```

### İkili (binary) gövdeler

`byte[]` gövde olduğu gibi gönderilir; başka bir tip ayarlamadıysanız `Content-Type: application/octet-stream` kullanılır. String'ler olduğu gibi gönderilir, diğer nesneler JSON'a çevrilir.

```java
ApiClient.put("/api/avatars/42")
        .contentType("image/png")
        .body(Files.readAllBytes(Path.of("avatar.png")))
        .send();
```

### İstek bazında retry

Varsayılan, `testfly.yml` içindeki `api.retry` ayarıdır. Bir istek bunu geçersiz kılabilir; açık bir override, `api.retry.enabled` `false` olsa bile retry'ı etkinleştirir:

```java
ApiClient.get("/api/flaky").retry(3).send();                                   // toplam 3 deneme
ApiClient.get("/api/rate-limited").retry(5, Duration.ofMillis(200)).retryOn(429, 503).send();
ApiClient.post("/api/payments").noRetry().send();                             // bu çağrıyı asla tekrarlama
```

Bekleme doğrusaldır (`backoff × deneme`). Ayarlanmayan değerler `api.retry`'dan gelir (varsayılanlar: 502/503/504 durumları, 500 ms, transport hataları tekrarlanır).

### Proxy

```yaml
api:
  proxy:
    host: proxy.corp.local
    port: 3128
    nonProxyHosts: [localhost, "127.*", "*.internal"]
```

```java
ApiClient.get("https://partner.example.com/ping").proxy("127.0.0.1", 8888).send(); // örn. debug proxy
ApiClient.get("http://localhost:8080/health").noProxy().send();                    // api.proxy'yi atla
```

`nonProxyHosts` girdileri host adlarıyla büyük/küçük harfe duyarsız eşleşir ve `*` joker karakterini kabul eder. Proxy kimlik doğrulaması desteklenmez.

### Yeniden kullanılabilir istek spec'leri

```java
ApiRequestSpec tenant = ApiRequestSpec.builder()
        .header("X-Tenant", "acme")
        .queryParam("locale", "tr")
        .build();

ApiClient.setTestSpec(tenant);    // yalnızca mevcut test thread'i, her test sonunda temizlenir
ApiClient.setGlobalSpec(tenant);  // JVM genelinde: tüm thread'ler, otomatik temizlenmez
apiClient().get("/api/me").spec(tenant).send(); // açık kullanım: isteğin değerlerinin üzerine yazar
```

Öncelik: istekte ayarlanan değerler kazanır, sonra test spec'i, sonra global spec gelir. `ApiClient.addRequestInterceptor` / `addResponseInterceptor` hook'ları da JVM geneldir; test kapsamlı middleware için `ApiClient.addInterceptor` kullanın ([Interceptor Zinciri](api-interceptors.md)).

### İstek gövdesi logu

`api.logBody: true` olduğunda step logu yanıt gövdesini ve istek gövdesini içerir. JSON, metin, XML ve form gövdeleri loglanır (`api.truncationLimit` ile kısaltılır). İkili ve multipart gövdeler yalnızca `<binary, N bytes, content-type>` olarak görünür. `api.maskedHeaders` içindeki header'lar maskelenir; gövde alanları maskelenmez, bu yüzden gizli veri taşıyan isteklerde `logBody` kapalı kalmalıdır.
