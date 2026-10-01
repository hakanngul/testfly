---
title: "Polling"
---

# Polling

Microservices and event-driven architectures often require waiting for asynchronous jobs or eventual consistency. Instead of arbitrary `Thread.sleep()`, use `pollUntil()`:

```java
import java.time.Duration;

// Poll until condition is met or timeout (default 500ms interval)
ApiResponse res = ApiClient.get("/api/jobs/job-12345")
        .pollUntil(r -> "COMPLETED".equals(r.json("$.status")), Duration.ofSeconds(30));

res.assertJson("$.result", "SUCCESS");

// Custom polling interval
ApiResponse order = ApiClient.get("/api/orders/ord-999")
        .pollUntil(r -> r.status() == 200, Duration.ofSeconds(15), Duration.ofMillis(250));
```

During polling, `ApiClient` logs attempts to `StepLogger` (`[API Polling] Started...`, `[API Polling] Condition satisfied in 1420ms`). If the condition is not satisfied before timeout, an `ApiException` with details is thrown.
