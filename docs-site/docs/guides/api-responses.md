---
title: "Responses & Assertions"
---

# Responses & Assertions

### Status assertion

```java
res.assertStatus(201);
```

### Body assertions

```java
res.assertBodyContains("success");
res.assertJson("$.user.name", "Alice");
```

### JSONPath extraction

```java
String token = res.json("$.token");
int    id     = res.json("$.user.id", Integer.class);
JsonNode node = res.jsonNode("$.user.metadata"); // direct Jackson JsonNode
```

Supported path syntax (no extra dependency):

| Syntax | Example | Result |
|---|---|---|
| Root | `$` | whole document |
| Field / nested field | `$.user.name` | single value |
| Array index | `$.items[0].id` | single value |
| Quoted key | `$['a.b']`, `$["x/y"]` | key containing `.`, `/`, `~` or spaces |
| Wildcard | `$.items[*].id`, `$.map.*` | array of all matches |

```java
List<Integer> ids = res.jsonList("$.items[*].id", Integer.class);
res.assertJsonArraySize("$.items[*].tags", 3);
```

A missing path returns `null` from `json(...)`. Filter expressions (`[?(...)]`), slices and `..` are rejected with an `IllegalArgumentException`.

### Advanced JSON Assertions

```java
// Numeric comparisons
res.assertJsonGreaterThan("$.score", 85.0);
res.assertJsonLessThan("$.responseTimeMs", 500.0);

// Substring matching
res.assertJsonContains("$.email", "@company.com");

// Boolean checks
res.assertJsonTrue("$.verified");
res.assertJsonFalse("$.isSuspended");

// Custom functional predicates
res.assertJson("$.roles", node -> node.isArray() && node.size() >= 2, "at least 2 user roles");
```

### Status families and semantic JSON comparison

```java
res.assertStatusIn(200, 201);
res.assertStatusRange(200, 299);   // or res.assertSuccessful()

// Whole body or a sub-tree: key order and 1 vs 1.0 are ignored, array order is not
res.assertJsonEquals("{\"id\":1,\"name\":\"Alice\"}");
res.assertJsonEquals("$.user.roles", "[\"admin\",\"dev\"]", true); // ignore array order

// Array membership: scalars by text, objects semantically
res.assertJsonArrayContains("$.ids", 42);
res.assertJsonArrayContains("$.users", Map.of("id", 1, "name", "Alice"));
```

A failed comparison names the first difference, for example `at /items/1/name: expected "a" but got "b"` or `unexpected field 'extra'`.

### Deserialize to object

```java
User user = res.asObject(User.class);
```

### Schema validation

Validate the response structure against a JSON Schema file:

```java
res.assertStatus(200).assertSchema("schemas/user.json");
```

Place schema files under `src/test/resources/schemas/`. See [Schema Validation](api-schema-validation.md).

### Raw access

```java
int    status   = res.status();
String body     = res.body();
long   duration = res.durationMs();
```

Binary payloads and headers:

```java
byte[] pdf  = res.asBytes();                       // raw bytes, never re-encoded
Path saved  = res.saveTo(Path.of("target/report.pdf"));
String type = res.contentType();                   // Content-Type header
Charset cs  = res.charset();                       // charset used by body(), UTF-8 by default
List<String> cookies = res.headerValues("Set-Cookie");
Map<String, List<String>> all = res.headers();
boolean ok  = res.isSuccessful();                  // 2xx
System.out.println(res.prettyBody());              // pretty JSON or the plain body
```

### Failure types

Assertion and request failures throw `ApiException` (a `RuntimeException`). Contract checks (`assertSchema`, `assertOpenApi`) throw `ApiAssertionError`, which extends `AssertionError`. Both implement `ApiFailure`, so `getStatus()`, `getMethod()`, `getUrl()` and `getBody()` are available on either.

### Fluent chaining

```java
ApiClient.get("/api/users/1")
        .send()
        .assertStatus(200)
        .assertJson("$.name", "Alice")
        .assertJsonTrue("$.active")
        .assertSchema("schemas/user.json");
```
