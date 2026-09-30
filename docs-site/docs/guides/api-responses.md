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

### Fluent chaining

```java
ApiClient.get("/api/users/1")
        .send()
        .assertStatus(200)
        .assertJson("$.name", "Alice")
        .assertJsonTrue("$.active")
        .assertSchema("schemas/user.json");
```
