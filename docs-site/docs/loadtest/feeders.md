---
id: feeders
title: Data Feeders & Parameterization
description: "Feed dynamic test datasets into load scenarios from CSV files, JSON payloads, round-robin iterators, or lambda suppliers."
sidebar_position: 5
---

# Data Feeders & Parameterization

Static load tests often cause artificial cache hits on database queries or application layer caching. TestFly's `LoadTestFeeder` provides dynamic parameterization to simulate realistic, varied user requests across virtual users.

---

## 1. Creating Feeders

TestFly supports multiple feeder sources:

### A. CSV Feeder
Load tabular test data directly from classpath or filesystem:

```java
import io.testfly.loadtest.LoadTestFeeder;

// Reads data/users.csv (columns: username, password)
LoadTestFeeder userFeeder = LoadTestFeeder.fromCsv("data/users.csv");
```

### B. In-Memory List / Maps
```java
List<Map<String, Object>> searchTerms = List.of(
    Map.of("query", "laptop", "maxPrice", 1200),
    Map.of("query", "keyboard", "maxPrice", 80),
    Map.of("query", "monitor", "maxPrice", 300)
);

LoadTestFeeder searchFeeder = LoadTestFeeder.fromList(searchTerms);
```

### C. Dynamic Lambda Generator
Generate random or computed values on each invocation:

```java
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

LoadTestFeeder dynamicFeeder = LoadTestFeeder.fromSupplier(() -> Map.of(
    "orderId", UUID.randomUUID().toString(),
    "amount", ThreadLocalRandom.current().nextInt(10, 500)
));
```

---

## 2. Feeding Strategies

Control how records are distributed across virtual users:

- **Circular / Round-Robin (Default):** Loops back to the start when reaching the end of the dataset.
  ```java
  LoadTestFeeder feeder = LoadTestFeeder.fromCsv("users.csv").circular();
  ```
- **Random:** Picks records randomly with uniform distribution.
  ```java
  LoadTestFeeder feeder = LoadTestFeeder.fromCsv("products.csv").random();
  ```

---

## 3. Using Feeders in Scenarios

Feeders inject variables into request URLs, headers, and JSON bodies using `${variable}` placeholders:

```java
@Test
public void testAuthenticatedSearch() {
    LoadTestFeeder feeder = LoadTestFeeder.fromCsv("data/search-data.csv");

    load("/api/search?q=${query}&limit=${limit}")
        .feed(feeder)
        .header("X-Client-ID", "${clientId}")
        .users(20)
        .run()
        .assertP95Below(200);
}
```

### Injecting into POST Payloads

```java
String template = """
    {
      "sku": "${sku}",
      "quantity": ${quantity},
      "postalCode": "${zip}"
    }
""";

load("/api/shipping/calculate")
    .feed(LoadTestFeeder.fromCsv("data/shipments.csv"))
    .post(template)
    .header("Content-Type", "application/json")
    .users(15)
    .run()
    .assertErrorRateBelow(0.01);
```
