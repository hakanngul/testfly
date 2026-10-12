---
title: "Polling"
---

# Polling

Microservices and event-driven architectures often require waiting for asynchronous jobs or eventual consistency. Instead of arbitrary `Thread.sleep()`, use `pollUntil()`:

```java
import java.time.Duration;

// Poll until condition is met or timeout (default 1 s interval)
ApiResponse res = ApiClient.get("/api/jobs/job-12345")
        .pollUntil(r -> "COMPLETED".equals(r.json("$.status")), Duration.ofSeconds(30));

res.assertJson("$.result", "SUCCESS");

// Custom polling interval
ApiResponse order = ApiClient.get("/api/orders/ord-999")
        .pollUntil(r -> r.status() == 200, Duration.ofSeconds(15), Duration.ofMillis(250));
```

During polling, `ApiClient` logs attempts to `StepLogger` (`[API Polling] Started...`, `[API Polling] Condition satisfied in 1420ms`). If the condition is not satisfied before timeout, an `ApiException` with details is thrown.

### Polling options

`ApiPolling` adds fail-fast statuses, an attempt limit and strict predicate handling:

```java
ApiResponse done = ApiClient.get("/api/jobs/{id}").pathParam("id", jobId)
        .pollUntil(r -> "COMPLETED".equals(r.json("$.status")),
                ApiPolling.within(Duration.ofSeconds(30))
                        .interval(Duration.ofMillis(500))   // minimum 50 ms
                        .failFastOn(404, 500)               // stop at once on these statuses
                        .maxAttempts(20)                    // optional limit besides the timeout
                        .rethrowPredicateErrors(true));     // a throwing condition stops polling
```

By default, transport errors and exceptions thrown by the condition are logged as warnings and polling continues. The final `ApiException` carries the last status and body, and the last error as its cause.
