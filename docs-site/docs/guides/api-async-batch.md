---
title: "Async Calls & Batch"
---

# Async Calls & Batch

`sendAsync()` captures the request, auth, test ID/class/method, interceptor lists and retry/logging settings on the caller thread, then executes the blocking chain on a virtual thread. Changes to the fluent builder after submission do not affect that call. Preparation errors produce an exceptionally completed future.

Auth, cookie and test context are installed for the worker and cleaned in `finally`; arbitrary application ThreadLocals are not copied. Calls from the same test share a concurrent cookie jar (last writer wins for the same cookie); different tests remain isolated. Interceptor instances and token suppliers shared by concurrent calls must be thread-safe. `ApiBatchRunner.concurrently(n)` limits active logical calls and preserves result order.

Always await your futures before ending the test. Test cleanup cancels unfinished futures and suppresses late steps from their closed context. `cancel(true)` interrupts the worker and prevents subsequent sends/retries; an already processed server operation cannot be undone. The API executor is closed at suite/engine shutdown and is recreated for a later suite.

See [Interceptor Chain](api-interceptors.md) for sync and async middleware examples.

### Partial batch results

`execute()` fails as soon as one request fails. `executeAll()` waits for every request and returns each response or error in submission order:

```java
ApiBatchResult result = ApiBatchRunner.fire(50).concurrently(10)
        .request(() -> apiPost("/api/orders").body(order))
        .executeAll();

assertEquals(result.failureCount(), 0);
assertTrue(result.countStatus(429) <= 5);
result.failures().forEach(f -> System.out.println(f.index() + " -> " + f.error()));
result.assertAllSucceeded();   // ApiException naming the first failed index
```

An HTTP error status is still a successful outcome (a response was received). Errors are transport failures, cancellations and exceptions from interceptors or the request supplier.
