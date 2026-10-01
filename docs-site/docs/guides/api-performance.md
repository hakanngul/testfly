---
title: "Timeouts & Performance"
---

# Timeouts & Performance

Connection establishment timeout and request timeout are separate settings. Request timeout is not a socket read-idle timeout.

```yaml
api:
  connectTimeoutSeconds: 30
  timeoutSeconds: 30
  maxConcurrentRequests: 0
```

```java
ApiClient.get("/report").requestTimeout(Duration.ofSeconds(5)).send();
ApiClient.get("/report").timeout(5).send();
```

The last fluent timeout setting wins; durations must be positive. `maxConcurrentRequests=0` is unlimited; a positive value limits actual HTTP sends across the runtime. This limit cannot change while test scopes are active. Mock responses consume no permits; refresh/retry sends acquire separate permits. Waiting is interruptible and included in total call time, separate from transport duration.

Batch concurrency limits logical calls; this setting limits physical sends. Default clients/connections are shared and the virtual-thread pipeline is retained.

JDK idle pool/keepalive options can be passed at JVM startup using `-Djdk.httpclient.connectionPoolSize=...` and `-Djdk.httpclient.keepalive.timeout=...`. The former is not an active-connection limit; runtime System.setProperty is not guaranteed.

`ApiTransportBenchmark.main()` reports localhost throughput, p50/p95 and observed TCP connections. Results are informational; there is no CI timing threshold.
