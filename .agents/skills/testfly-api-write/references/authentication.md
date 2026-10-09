# Authentication

## Supported mechanisms

Use `ApiClient.auth(ApiAuth...)` for request-local credentials:

```java
ApiClient.get("/me")
        .auth(ApiAuth.bearerToken(System.getenv("API_TOKEN")))
        .send()
        .assertStatus(200);
```

Verified factories are:

- `bearerToken(token)`
- `basicAuth(username, password)`
- `apiKey(headerName, key)`; a null header name defaults to `X-Api-Key`
- `apiKeyQuery(parameterName, key)`
- `oauth2(tokenUrl, clientId, clientSecret)` for client credentials with cached tokens
- `oauth2Password(tokenUrl, clientId, clientSecret, username, password)`
- `hmac(apiKey, secret, algorithm)`; null algorithm defaults to `HmacSHA256`
- `digest(username, password)`, which only emits a Basic-like payload with a `Digest` prefix and is not RFC challenge-response Digest authentication

For a custom scheme, implement `ApiAuth` or a per-request `ApiInterceptor`. Keep implementations stateless or thread-safe when shared.

## Named auth

TestNG and JUnit 5 lifecycle adapters resolve `@UseAuth` from the method, then the class:

```yaml
api:
  auth:
    admin:
      type: bearer
      token: ${ADMIN_API_TOKEN}
```

```java
@UseAuth("admin")
@Test
public void readsAdminResource() {
    ApiClient.get("/admin/resource").send().assertStatus(200);
}
```

Named types accept `bearer`, `basic`, `oauth2`, `apiKey`, `apiKey-query`, `digest`, `hmac`, and `oauth2_password` aliases implemented by the lifecycle adapters. There is no `ApiClient.withAuth(String)` method. Cucumber does not resolve `@UseAuth`; apply request-local `ApiAuth` in a step or helper.

## Secret and lifecycle rules

- Put secret placeholders in `testfly.yml` or profile files and supply values through `.env`, environment variables, or system properties according to the project configuration policy. Never commit populated `.env` files.
- Fail setup when a required secret is absent; do not send the unresolved placeholder or the string `null`.
- TestFly masks `Authorization`, `Cookie`, and `X-Api-Key` by default. Add custom credential headers to `api.maskedHeaders` before enabling context, body, or curl logging.
- `setGlobalAuth` is thread-local and framework cleanup removes it. Prefer request-local auth or `@UseAuth`; do not use suite setup to share mutable tokens across parallel workers.
- OAuth2 helpers perform their own token HTTP call and cache by token URL/client identity. Use a local identity stub in deterministic tests and do not assert on live token services in the default suite.
