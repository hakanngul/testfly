---
description: "Selenium API testleri için API kimlik doğrulama: Bearer token, Basic auth ve OAuth2 client credentials kutu dışında desteklenir."
sidebar_position: 14
---

# API Kimlik Doğrulama

TestFly, kutu dışında üç kimlik doğrulama stratejisini destekler: Bearer token, Basic auth ve OAuth2 client credentials.

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

Framework, `grant_type=client_credentials` ile bir `POST` gönderir ve dönen `access_token` değerini son kullanma süresine kadar (yanıttaki `expires_in` alanını kullanarak) önbelleğe alır.

---

## Global Auth — Bir Kez Ayarla, Her Yerde Kullan

Kimlik doğrulamayı `@BeforeSuite` içinde bir kez ayarlayın; o thread üzerindeki sonraki her istek otomatik olarak onu içerir. Her istekte `.auth()` çağrısı gerekmez.

```java
import io.testfly.test.BaseApiTest;
import io.testfly.client.ApiAuth;
import io.testfly.client.ApiClient;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

public class UserApiTest extends BaseApiTest {

    @BeforeSuite
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
```

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