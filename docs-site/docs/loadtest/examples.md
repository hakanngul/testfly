---
id: examples
title: Load Testing Examples & Recipes
description: "Production-ready examples: single API endpoints, multi-step checkout flows, feeder parameterization, and hybrid UI + load test patterns."
sidebar_position: 9
---

# Load Testing Examples & Recipes

Below are real-world, production-grade load testing recipes using TestFly.

---

## 1. Fast Health Check Smoke Test

Quickly verify that an API gateway responds within SLAs under 20 concurrent connections:

```java
package com.example.load;

import io.testfly.test.BaseTest;
import org.testng.annotations.Test;
import java.time.Duration;

public class HealthCheckLoadTest extends BaseTest {

    @Test
    public void testHealthCheckSla() {
        load("/actuator/health")
            .users(20)
            .rampUp(Duration.ofSeconds(2))
            .hold(Duration.ofSeconds(10))
            .run()
            .assertThroughputAbove(50.0)
            .assertP95Below(100)
            .assertNoStatus(500)
            .assertNoStatus(503);
    }
}
```

---

## 2. Multi-Step Checkout Flow with CSV Feeder

Simulate real customer behavior: searching, viewing details, adding to cart, and placing an order:

```java
package com.example.load;

import io.testfly.loadtest.LoadScenario;
import io.testfly.loadtest.LoadTestFeeder;
import io.testfly.test.BaseTest;
import org.testng.annotations.Test;
import java.time.Duration;

public class CustomerJourneyLoadTest extends BaseTest {

    @Test
    public void testCustomerJourney() {
        LoadTestFeeder productFeeder = LoadTestFeeder.fromCsv("testdata/products.csv").random();

        LoadScenario scenario = LoadScenario.named("Store Customer Journey")
            .step("Browse Products", req -> req.get("/api/products?query=${searchQuery}"))
            .step("Product Detail", req -> req.get("/api/products/${productId}"))
            .step("Add to Cart", req -> req.post("/api/cart")
                                          .header("Content-Type", "application/json")
                                          .body("{\"productId\": \"${productId}\", \"qty\": 1}"))
            .step("Place Order", req -> req.post("/api/checkout")
                                          .header("Content-Type", "application/json")
                                          .body("{\"paymentMethod\": \"CARD\", \"amount\": ${price}}"));

        load(scenario)
            .feed(productFeeder)
            .users(30)
            .rampUp(Duration.ofSeconds(10))
            .hold(Duration.ofSeconds(30))
            .run()
            .assertThroughputAbove(15.0)
            .assertStepP95Below("Browse Products", 200)
            .assertStepP95Below("Place Order", 600)
            .assertErrorRateBelow(0.01);
    }
}
```

---

## 3. Hybrid UI & Load Test Workflow

Verify backend system stability while simultaneously validating UI user experience in Selenium:

```java
package com.example.hybrid;

import io.testfly.test.BaseTest;
import org.testng.annotations.Test;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class HybridUiAndLoadTest extends BaseTest {

    @Test
    public void verifyUiResponsivenessUnderBackgroundLoad() {
        // 1. Launch background API load simulation asynchronously
        CompletableFuture<Void> loadTask = CompletableFuture.runAsync(() -> {
            load("/api/orders/simulate")
                .users(50)
                .hold(Duration.ofSeconds(20))
                .run()
                .assertErrorRateBelow(0.02);
        });

        // 2. Perform frontend browser automation using BaseTest WebDriver
        open("/dashboard");
        assertVisible(getByRole("heading", "System Status"));
        click(getByRole("button", "Refresh Metrics"));
        assertVisible(getByText("All systems operational"));

        // 3. Ensure background load test completed successfully
        loadTask.join();
    }
}
```
