---
description: "TestFly API testleri için Bearer, Basic, API anahtarı, HMAC, OAuth2 client credentials ve OAuth2 password kimlik doğrulama fabrikaları."
sidebar_position: 14
---

# API Kimlik Doğrulama

Aşağıdaki örnekler en yaygın üç stratejiyle başlar: Bearer token, Basic auth ve OAuth2 client credentials. `ApiAuth` ayrıca header/query API anahtarı, HMAC, OAuth2 password ve sınırlı bir digest yer tutucusu sağlar.

---

## Bearer Token

```java
ApiClient.get("/api/me")
        .auth(ApiAuth.bearerToken("my-secret-token"))
        .send();
```

---

## Basic Auth

```java
ApiClient.get("/api/admin")
        .auth(ApiAuth.basicAuth("admin", "password"))
        .send();
```

---

## OAuth2 — Client Credentials

Token, ilk kullanımda otomatik olarak alınır ve **son kullanma süresine kadar önbelleğe alınır**. Manuel token yenilemeye gerek yoktur.

```java
ApiClient.setGlobalAuth(ApiAuth.oauth2(
    "https://auth.example.com/token",
    System.getenv("CLIENT_ID"),
    System.getenv("CLIENT_SECRET")
));
```

Framework, `grant_type=client_credentials` ile bir `POST` gönderir ve dönen `access_token` değerini son kullanma süresine kadar (yanıttaki `expires_in` alanını kullanarak) önbelleğe alır. Token yanıtı `refresh_token` içeriyorsa süresi dolan token önce refresh-token grant ile yenilenir (değişen refresh token bir sonraki seferde kullanılır); bu başarısız olursa orijinal grant tekrar çalışır. Önbellek tüm JVM'de paylaşılır ve suite sonunda temizlenir; yeni token almaya zorlamak için `ApiAuth.clearTokenCache()` çağırın. Token istekleri `api.proxy` veya `api.ssl` truststore ayarlarını kullanmaz.

---

## Ek `ApiAuth` Fabrikaları

```java
ApiAuth.apiKey("X-Api-Key", System.getenv("API_KEY"));
ApiAuth.apiKeyQuery("api_key", System.getenv("API_KEY"));
ApiAuth.oauth2Password(tokenUrl, clientId, clientSecret, username, password);
ApiAuth.oauth2Refresh(tokenUrl, clientId, clientSecret, System.getenv("REFRESH_TOKEN"));
ApiAuth.digest("user", System.getenv("DIGEST_PASSWORD"));
ApiAuth.hmacSigner().apiKey(System.getenv("API_KEY")).secret(System.getenv("API_SECRET")).build();
```

### Digest kimlik doğrulama

`ApiAuth.digest(username, password)` RFC 7616 challenge/response akışını uygular: MD5, SHA-256 ve SHA-512-256 (`-sess` varyantlarıyla), `qop=auth` ve `qop=auth-int`. Bir origin'e giden ilk istek kimlik bilgisi olmadan gönderilir. `WWW-Authenticate: Digest` içeren `401` gelirse istek `Authorization` header'ıyla bir kez daha gönderilir. Challenge, mevcut test için origin bazında önbelleğe alınır (testin `sendAsync` çağrılarıyla paylaşılır); sonraki istekler artan nonce sayacıyla doğrudan kimlik doğrular. Bayat (stale) veya değişen nonce bir kez yeniden kimlik doğrulamaya yol açar. Yanlış kimlik bilgilerinde son `401` yanıtı assertion'larınıza bırakılır. Önbellek her test sonunda temizlenir.

### HMAC istek imzalama

`ApiAuth.hmacSigner()` gerçekten gönderilen isteği imzalar. Varsayılan kanonik metin dört satırdır: metod, query dahil path, zaman damgası (epoch saniye) ve gövdenin hex SHA-256 değeri.

```java
ApiClient.setGlobalAuth(ApiAuth.hmacSigner()
        .apiKey(System.getenv("API_KEY"))          // X-Api-Key içinde gönderilir (keyHeader)
        .secret(System.getenv("API_SECRET"))
        .algorithm("HmacSHA256")                   // varsayılan
        .signatureHeader("X-Signature")            // varsayılan
        .timestampHeader("X-Timestamp")            // varsayılan; null verilirse eklenmez
        .encoding(ApiHmacSigner.Encoding.HEX)      // varsayılan BASE64
        .canonical(ctx -> ctx.method() + "\n" + ctx.pathAndQuery() + "\n" + ctx.timestamp())
        .build());
```

İmzalayıcı en içteki interceptor olarak çalışır; bu yüzden legacy `RequestInterceptor` hook'larının eklediği header'lar imzaya dahil değildir. `ApiAuth.hmac(apiKey, secret, algorithm)` deprecated'dır: yalnızca API anahtarı değerini imzalar, isteği imzalamaz. Uyumluluk için gönderdiği header'lar değişmedi.

### Özel challenge veya imzalama şemaları

Bir auth şeması son isteğe veya yanıta ihtiyaç duyuyorsa `ApiAuth.Intercepting` uygulayın. `ApiClient` bunu en içteki interceptor olarak kurar ve `chain.proceed(...)` birden fazla kez çağrılabilir.

```java
ApiAuth retryOn401 = (ApiAuth.Intercepting) chain -> {
    ApiResponse first = chain.proceed(chain.request());
    if (first.status() != 401) return first;
    return chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer " + refreshToken()).build());
};
```

---

## Global Auth — Her Test İçin Ayarla

Kimlik doğrulamayı her test için `@BeforeMethod` içinde ayarlayın. Global auth thread-local test kapsamındadır ve test kapanışında temizlenir; suite genelinde paylaşılmaz. Paralel testlerin her biri kendi auth ayarını kurmalıdır. Test içindeki her istekte `.auth()` çağrısı gerekmez.

```java
import io.testfly.test.BaseApiTest;
import io.testfly.client.ApiAuth;
import io.testfly.client.ApiClient;
import io.testfly.client.ApiResponse;
import java.util.Map;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class UserApiTest extends BaseApiTest {

    @BeforeMethod
    public void authenticate() {
        ApiResponse login = ApiClient.post("/api/auth/login")
                .body(Map.of("username", "admin", "password", "pass"))
                .send();

        ApiClient.setGlobalAuth(ApiAuth.bearerToken(login.json("$.token")));
    }

    @Test
    public void getUsers() {
        // Token applied automatically — no .auth() needed
        ApiClient.get("/api/users").send().assertStatus(200);
    }
}
```

Framework, global kimlik doğrulamayı her testten sonra otomatik olarak temizler; böylece testler birbirine karışmaz.

Manuel olarak temizlemek için:

```java
ApiClient.clearGlobalAuth();
```

---

## `@UseAuth` — Yapılandırma Tabanlı Kimlik Doğrulama Stratejileri

`testfly.yml` içinde adlandırılmış kimlik doğrulama stratejileri tanımlayın ve bunları `@UseAuth` ile test bazında veya sınıf bazında uygulayın.

### Yapılandırma

```yaml
api:
  auth:
    adminToken:
      type: bearer
      token: ${ADMIN_TOKEN}        # resolved from environment variable

    basicUser:
      type: basic
      username: user
      password: ${USER_PASSWORD}

    serviceAccount:
      type: oauth2
      tokenUrl: https://auth.example.com/token
      clientId: ${CLIENT_ID}
      clientSecret: ${CLIENT_SECRET}

    legacyApp:
      type: digest
      username: app
      password: ${DIGEST_PASSWORD}

    partnerApi:
      type: hmac-signed            # istek imzalama; "hmac" yalnızca anahtarı imzalayan deprecated varyant
      apiKey: ${PARTNER_KEY}
      secret: ${PARTNER_SECRET}
      signatureHeader: X-Signature # opsiyonel; keyHeader, timestampHeader ("none" kapatır), encoding (base64|hex), algorithm

    longLived:
      type: oauth2-refresh
      tokenUrl: https://auth.example.com/token
      clientId: ${CLIENT_ID}
      clientSecret: ${CLIENT_SECRET}
      refreshToken: ${REFRESH_TOKEN}
```

Desteklenen tipler: `bearer`, `basic`, `oauth2`, `oauth2_password`, `oauth2-refresh`, `apiKey`, `apikey-query`, `digest`, `hmac` (deprecated), `hmac-signed`. `@UseAuth` TestNG listener'ı ve JUnit 5 extension'ı tarafından uygulanır; Cucumber adımlarında auth kod içinde ayarlanır.

Token değerleri `${ENV_VAR}` interpolasyonunu destekler — çalışma zamanında ortam değişkenlerinden veya sistem özelliklerinden çözümlenir.

### Kullanım

```java
@Test
@UseAuth("adminToken")
public void createUser() {
    apiClient().post("/api/users")
            .body(Map.of("name", "Alice"))
            .send()
            .assertStatus(201);
}
```

Tüm bir sınıfa uygulayın:

```java
@UseAuth("serviceAccount")
public class OrderApiTest extends BaseApiTest {

    @Test
    public void listOrders() {
        ApiClient.get("/api/orders").send().assertStatus(200);
    }

    @Test
    public void createOrder() {
        ApiClient.post("/api/orders").body(...).send().assertStatus(201);
    }
}
```

Metod düzeyindeki `@UseAuth`, sınıf düzeyine göre önceliklidir.

---

## İstek Bazında ve Global Auth

| Yaklaşım | Kapsam | En uygun |
|---|---|---|
| `.auth(ApiAuth.bearerToken(...))` | Tek istek | Farklı tokenlarla yapılan tek seferlik çağrılar |
| `ApiClient.setGlobalAuth(...)` | Thread üzerindeki tüm istekler | Çalışma zamanı tokenları (giriş yanıtı) |
| `@UseAuth("name")` | Test metodu veya sınıfı | CI'da yapılandırma/ortam değişkeni tabanlı tokenlar |
