---
id: fluent-api
title: Fluent Load Testing API
description: "Master TestFly's intuitive fluent builder API for single-endpoint and complex multi-step scenario load testing."
sidebar_position: 3
---

# Fluent Load Testing API

The TestFly Load Testing API is designed to feel natural, readable, and fully integrated with existing UI and API test patterns. Starting from `load(url)`, you can chain concurrency settings, headers, payloads, feeders, and multi-step actions.

---

## 1. Single-Endpoint Request

For microservice health checks or individual API endpoints, invoke `load(...)` directly:

```java
load("https://api.example.com/items")
    .get()                                    // default is GET
    .header("Accept", "application/json")
    .queryParam("category", "electronics")
    .users(25)
    .rampUp(Duration.ofSeconds(5))
    .hold(Duration.ofSeconds(20))
    .run();
```

### POST with JSON Body

```java
String jsonPayload = """
    {
      "sku": "PROD-998",
      "quantity": 1
    }
""";

load("/api/cart/add")
    .post(jsonPayload)
    .header("Content-Type", "application/json")
    .users(15)
    .run();
```

Supported HTTP verbs: `.get()`, `.post(body)`, `.put(body)`, `.delete()`, `.patch(body)`.

---

## 2. Multi-Step User Journeys

Real-world users do not bombard a single endpoint; they navigate across multiple API calls. TestFly models this using `step(name, request)`:

```java
import io.testfly.loadtest.LoadScenario;
import io.testfly.test.BaseTest;
import org.testng.annotations.Test;
import java.time.Duration;

public class CheckoutJourneyTest extends BaseTest {

    @Test
    public void simulateCheckoutFlow() {
        LoadScenario scenario = LoadScenario.named("E-Commerce User Journey")
            .step("Browse Catalog", req -> req.get("/api/products?page=1"))
            .step("View Details", req -> req.get("/api/products/42"))
            .step("Add to Cart", req -> req.post("/api/cart")
                                          .header("Content-Type", "application/json")
                                          .body("{\"productId\": 42, \"quantity\": 1}"))
            .step("Checkout", req -> req.post("/api/checkout")
                                       .header("Authorization", "Bearer mock-token"));

        load(scenario)
            .users(30)
            .rampUp(Duration.ofSeconds(10))
            .hold(Duration.ofSeconds(30))
            .run()
            .assertThroughputAbove(20.0)
            .assertStepP95Below("Checkout", 500)
            .assertErrorRateBelow(0.02);
    }
}
```

---

## 3. Concurrency Profile Options

Customize user arrival rates and ramping shapes:

```java
load("/api/search")
    .users(100)                     // Peak concurrent virtual users
    .rampUp(Duration.ofSeconds(15)) // Linear ramp-up from 1 to 100 users
    .hold(Duration.ofSeconds(45))   // Sustained peak duration
    .targetRps(250)                 // Optional rate limit: max 250 requests/sec
    .run();
```

---

## 4. Method Reference Summary

| Method | Description |
| :--- | :--- |
| `load(String url)` | Initializes a load test scenario for a single URL. |
| `load(LoadScenario scenario)` | Initializes a load test for a multi-step user journey. |
| `users(int count)` | Specifies concurrent virtual users. |
| `rampUp(Duration duration)` | Sets linear ramp-up duration. |
| `hold(Duration duration)` | Sets duration to sustain peak virtual users. |
| `targetRps(int rps)` | Throttles maximum requests per second. |
| `header(String name, String value)` | Adds an HTTP request header. |
| `queryParam(String name, String value)` | Appends query parameters to the URL. |
| `feed(LoadTestFeeder feeder)` | Binds dynamic data feeder to requests. |
| `engine(String engine)` | Overrides execution engine (`"auto"`, `"gatling"`, `"jdk"`). |
| `run()` | Triggers execution and returns a `LoadTestAssert` instance. |
