---
id: getting-started
title: Getting Started with Load Testing
sidebar_position: 1
---

# Getting Started with Load Testing

Use Java 21 and `io.testfly.loadtest.BaseLoadTest` for TestNG load tests. This base class provides load helpers and framework lifecycle without starting a browser. `BaseTest`, `BaseApiTest` and `BaseJUnit5Test` do not provide `load()` helpers.

## Availability

The load testing module was added in `1.0.5` (see the changelog) and is included in the current release `1.0.7`. Add this dependency from Maven Central:

```xml
<dependency>
    <groupId>io.github.hakanngul</groupId>
    <artifactId>testfly</artifactId>
    <version>1.0.7</version>
    <scope>test</scope>
</dependency>
```

## Configuration

Set `loadtest.baseUrl` explicitly; do not rely on `execution.baseUrl` as a load engine fallback.

```yaml
execution:
  mode: local
  baseUrl: https://example.com
browser:
  name: chrome
timeouts:
  explicit: 10
  pageLoad: 30
loadtest:
  enabled: true
  baseUrl: https://api.example.com
  engine: jdk
  users: 10
  rampUp: 2s
  hold: 5s
  cooldown: 1s
```

## First test


```java
import io.testfly.loadtest.BaseLoadTest;
import org.testng.annotations.Test;
import java.time.Duration;

public class HealthLoadTest extends BaseLoadTest {
    @Test
    public void healthUnderLoad() {
        load("/health").engine("jdk").users(10)
            .rampUp(Duration.ofSeconds(2)).hold(Duration.ofSeconds(5))
            .run().assertP95Below(250).assertErrorRateBelow(0.01);
    }
}
```

Thresholds are examples; set them from your service SLOs and measured baseline. The JDK engine requires no extra load engine dependency. `auto` selects Gatling if available, otherwise JDK; see [engines](./engines.md).

## Next steps

[Configuration](./configuration.md) · [Fluent API](./fluent-api.md) · [Annotations](./annotations.md) · [Feeders](./feeders.md) · [Assertions](./assertions.md)
