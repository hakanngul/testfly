---
description: "Migrate from REST Assured to TestFly ApiClient: replace heavy BDD given/when/then ceremony with fluent Java HTTP calls, built-in JSON Schema validation, and unified HTML reporting."
id: from-restassured
title: Migrate from REST Assured
sidebar_label: From REST Assured
sidebar_position: 6
---

# Migrate from REST Assured

If your team uses **REST Assured**, you are familiar with fluent HTTP request builders and JsonPath validation. 

TestFly's built-in **`ApiClient`** and **`BaseApiTest`** provide the same testing power — without pulling in Apache HTTP Client, Groovy, Hamcrest, or heavy transitive dependency trees. Moreover, TestFly seamlessly integrates API tests into the same HTML report, execution hooks, and scenario context as your WebUI tests.

---

## Why Migrate?

| Capability | REST Assured | TestFly ApiClient |
|---|---|---|
| **Underlying Engine** | Apache HttpClient + Groovy runtime | Modern JDK `java.net.http.HttpClient` |
| **Dependencies** | ~20+ transitive JARs (Groovy, Hamcrest, etc.) | Zero extra dependencies (bundled in TestFly) |
| **Hybrid UI + API** | Requires manual glue code / thread locals | Built-in `ScenarioContext` & cookie injection |
| **Reporting** | Third-party plugins required | Native `StepLogger` + cURL command in HTML report |
| **Configuration** | Static setup code per test class | Centralized `testfly.yml` with named services |

---

## Syntax Comparison

### 1. Basic GET Request and Status Assertion

```java
// REST Assured
given()
    .baseUri("https://api.example.com")
    .header("Accept", "application/json")
.when()
    .get("/users/42")
.then()
    .statusCode(200);

// TestFly ApiClient
apiClient().get("/users/42")
    .header("Accept", "application/json")
    .send()
    .assertStatus(200);
```

### 2. POST with JSON Payload & Extracting Values

```java
// REST Assured
String token = given()
    .contentType("application/json")
    .body(Map.of("username", "admin", "password", "secret"))
.when()
    .post("/auth/login")
.then()
    .statusCode(200)
    .extract().path("accessToken");

// TestFly ApiClient
ApiResponse response = apiClient().post("/auth/login")
    .body(Map.of("username", "admin", "password", "secret"))
    .send();

response.assertStatus(200);
String token = response.json("$.accessToken");
```

### 3. Path & Query Parameters

```java
// REST Assured
given()
    .pathParam("orderId", 101)
    .queryParam("status", "shipped")
.when()
    .get("/orders/{orderId}");

// TestFly ApiClient
apiClient().get("/orders/{orderId}")
    .pathParam("orderId", 101)
    .queryParam("status", "shipped")
    .send();
```

### 4. JSON Schema Validation

```java
// REST Assured
given().get("/products/1")
.then()
    .body(matchesJsonSchemaInClasspath("schemas/product.json"));

// TestFly ApiClient
apiClient().get("/products/1")
    .send()
    .assertMatchesSchema("schemas/product.json");
```

---

## Hybrid UI + API Workflow

In TestFly, you can mix API and UI interactions in the same test class by inheriting from `BaseTest`:

```java
public class CheckoutHybridTest extends BaseTest {

    @Test
    public void seedViaApiAndVerifyInBrowser() {
        // 1. Seed database state via fast REST API call
        ApiResponse order = apiClient().post("/api/orders")
            .body(Map.of("item", "Laptop", "quantity", 1))
            .send()
            .assertStatus(201);
            
        String orderId = order.json("$.orderId");

        // 2. Open browser directly to the created resource
        open("/orders/" + orderId);
        assertThat(find("#order-status")).hasText("CONFIRMED");
    }
}
```
