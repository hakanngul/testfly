---
title: "Schema Validation"
---

# Schema Validation

Validate that a response matches a JSON Schema (Draft-07):

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

Requires `com.networknt:json-schema-validator` in your `pom.xml`:

```xml
<dependency>
  <groupId>com.networknt</groupId>
  <artifactId>json-schema-validator</artifactId>
  <version>1.4.3</version>
</dependency>
```

## OpenAPI response validation

Signature: `ApiResponse assertOpenApi(String specPath)` on `ApiResponse`; the `1.0.7` source contains this method, but its `@TestFlyApi(since = "1.2.0")` annotation conflicts with the source version. Confirm the published artifact and API contract before depending on it. It returns the same `ApiResponse`, so it chains with other assertions.

Add the optional validator dependency (TestFly declares it `<optional>`, so it is not pulled in transitively):

```xml
<dependency>
  <groupId>com.atlassian.oai</groupId>
  <artifactId>swagger-request-validator-core</artifactId>
  <version>2.39.0</version>
</dependency>
```

Without it, `assertOpenApi` throws `IllegalStateException` whose message contains this dependency snippet.

Place this example specification in `src/test/resources/contracts/users.yaml`:

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

How it works:

- `specPath` is passed unchanged to the Atlassian `OpenApiInteractionValidator.createFor(...)`, which accepts a file path (relative to the working directory), a classpath resource, or a URL. The parsed validator is cached per `specPath` for the lifetime of the JVM.
- Only the **response** is validated, using the HTTP method, request URL, status and body. Request bodies/headers and response headers are not forwarded, so this is not full request/response contract validation — assert required response headers separately with `.assertHeader(...)`.
- An unreadable or invalid spec raises `IllegalArgumentException`; contract violations raise `AssertionError` listing every validator message.

:::warning Known limitation in the current source
The adapter passes the **absolute** request URL (for example `https://api.example.com/users/1`) where the validator expects an operation path, and it does not forward the response `Content-Type`. With validator 2.39.0, a real `ApiClient` response therefore fails with `No API path found that matches request '…'` — even when the spec declares a matching `servers` entry — and response bodies would not be schema-checked because no content type is supplied. Until the adapter is fixed, validate bodies with `assertSchema(...)` using a JSON Schema extracted from your OpenAPI component.
:::
