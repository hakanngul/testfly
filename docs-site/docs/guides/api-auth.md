---
description: "API authentication for TestFly API tests: Bearer, Basic, API key, HMAC, OAuth2 client credentials, and OAuth2 password factories."
sidebar_position: 14
---

# API Authentication

The examples below start with the three most common strategies: Bearer token, Basic auth, and OAuth2 client credentials. `ApiAuth` also provides API-key header/query, HMAC, OAuth2 password, and a limited digest placeholder.

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

Token is fetched automatically on first use and **cached until expiry**. No manual token refresh needed.

```java
ApiClient.setGlobalAuth(ApiAuth.oauth2(
    "https://auth.example.com/token",
    System.getenv("CLIENT_ID"),
    System.getenv("CLIENT_SECRET")
));
```

The framework sends a `POST` with `grant_type=client_credentials` and caches the returned `access_token` until it expires (using the `expires_in` field from the response). When the token response contains a `refresh_token`, an expired token is renewed with the refresh-token grant first (a rotated refresh token is used next time); if that fails, the original grant runs again. The cache is shared by the whole JVM and cleared at suite end; call `ApiAuth.clearTokenCache()` to force a new token. Token requests do not use `api.proxy` or the `api.ssl` truststore.

---

## Additional `ApiAuth` Factories

```java
ApiAuth.apiKey("X-Api-Key", System.getenv("API_KEY"));
ApiAuth.apiKeyQuery("api_key", System.getenv("API_KEY"));
ApiAuth.oauth2Password(tokenUrl, clientId, clientSecret, username, password);
ApiAuth.oauth2Refresh(tokenUrl, clientId, clientSecret, System.getenv("REFRESH_TOKEN"));
ApiAuth.digest("user", System.getenv("DIGEST_PASSWORD"));
ApiAuth.hmacSigner().apiKey(System.getenv("API_KEY")).secret(System.getenv("API_SECRET")).build();
```

### Digest authentication

`ApiAuth.digest(username, password)` implements RFC 7616 challenge/response: MD5, SHA-256 and SHA-512-256 (with `-sess` variants), `qop=auth` and `qop=auth-int`. The first request to an origin goes out without credentials. On `401` with `WWW-Authenticate: Digest` it is re-sent once with an `Authorization` header. The challenge is then cached per origin for the current test (shared with its `sendAsync` calls), so later requests authenticate up front with an increasing nonce count. A stale or rotated nonce triggers one re-authentication. Wrong credentials leave the final `401` response to your assertions. The cache is cleared after each test.

### HMAC request signing

`ApiAuth.hmacSigner()` signs the request that is actually sent. The default canonical string is four lines: method, path with query, timestamp (epoch seconds) and the hex SHA-256 of the body.

```java
ApiClient.setGlobalAuth(ApiAuth.hmacSigner()
        .apiKey(System.getenv("API_KEY"))          // sent in X-Api-Key (keyHeader)
        .secret(System.getenv("API_SECRET"))
        .algorithm("HmacSHA256")                   // default
        .signatureHeader("X-Signature")            // default
        .timestampHeader("X-Timestamp")            // default; null omits it
        .encoding(ApiHmacSigner.Encoding.HEX)      // default BASE64
        .canonical(ctx -> ctx.method() + "\n" + ctx.pathAndQuery() + "\n" + ctx.timestamp())
        .build());
```

The signer runs as the innermost interceptor, so headers added by legacy `RequestInterceptor` hooks are not part of the signature. `ApiAuth.hmac(apiKey, secret, algorithm)` is deprecated: it signs only the API key value, never the request. Its headers are unchanged for compatibility.

### Custom challenge or signing schemes

Implement `ApiAuth.Intercepting` when an auth scheme needs the final request or the response. `ApiClient` installs it as the innermost interceptor, and it may call `chain.proceed(...)` more than once.

```java
ApiAuth retryOn401 = (ApiAuth.Intercepting) chain -> {
    ApiResponse first = chain.proceed(chain.request());
    if (first.status() != 401) return first;
    return chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer " + refreshToken()).build());
};
```

---

## Global Auth — Set for Each Test

Set auth for each test in `@BeforeMethod`; subsequent requests in that test include it. Global auth is thread-local and is cleared at test cleanup. It is not suite-wide; parallel tests must each install their own auth. No `.auth()` call is needed on each request.

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

The framework clears global auth automatically after each test, so tests don't bleed into each other.

To clear it manually:

```java
ApiClient.clearGlobalAuth();
```

---

## `@UseAuth` — Config-Based Auth Strategies

Define named auth strategies in `testfly.yml` and apply them per test or per class with `@UseAuth`.

### Configuration

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
      type: hmac-signed            # request signing; "hmac" is the deprecated key-only variant
      apiKey: ${PARTNER_KEY}
      secret: ${PARTNER_SECRET}
      signatureHeader: X-Signature # optional; keyHeader, timestampHeader ("none" disables), encoding (base64|hex), algorithm

    longLived:
      type: oauth2-refresh
      tokenUrl: https://auth.example.com/token
      clientId: ${CLIENT_ID}
      clientSecret: ${CLIENT_SECRET}
      refreshToken: ${REFRESH_TOKEN}
```

Supported types: `bearer`, `basic`, `oauth2`, `oauth2_password`, `oauth2-refresh`, `apiKey`, `apikey-query`, `digest`, `hmac` (deprecated), `hmac-signed`. `@UseAuth` is applied by the TestNG listener and the JUnit 5 extension; Cucumber steps set auth in code.

Token values support `${ENV_VAR}` interpolation — resolved from environment variables or system properties at runtime.

### Usage

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

Apply to an entire class:

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

Method-level `@UseAuth` takes precedence over class-level.

---

## Per-Request vs Global Auth

| Approach | Scope | Best for |
|---|---|---|
| `.auth(ApiAuth.bearerToken(...))` | Single request | One-off calls with different tokens |
| `ApiClient.setGlobalAuth(...)` | All requests on thread | Runtime tokens (login response) |
| `@UseAuth("name")` | Test method or class | Config/env-var based tokens in CI |
