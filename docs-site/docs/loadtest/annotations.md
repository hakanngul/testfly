---
id: annotations
title: Declarative Annotations (@LoadTest)
sidebar_position: 4
---

# Declarative Annotations

`@LoadTest` supports classes and test methods. Unset fields fall through to the next configuration level. There is no separate `@LoadEngine`; use `@LoadTest(engine = "jdk")`.

| Attribute | Type | Unset default |
| --- | --- | --- |
| `users` | `int` | `-1` |
| `rampUp` | `String` | `""` |
| `hold` | `String` | `""` |
| `cooldown` | `String` | `""` |
| `engine` | `String` | `""` |
| `baseUrl` | `String` | `""` |

Duration values use strings such as `5s`, `2m`, `1h`. Engines are `auto`, `gatling`, `jdk`. Resolution is fluent scenario → method annotation → class annotation → YAML → defaults. User count is clamped to `maxUsers`.


```java
import io.testfly.loadtest.BaseLoadTest;
import io.testfly.loadtest.LoadTest;
import org.testng.annotations.Test;

@LoadTest(users = 25, rampUp = "5s", hold = "15s", engine = "jdk")
public class CatalogLoadTest extends BaseLoadTest {
    @Test
    public void catalog() {
        load("/products").run().assertP95Below(300);
    }

    @Test
    @LoadTest(users = 50, hold = "30s")
    public void search() {
        load("/products?q=phone").users(40).run().assertP95Below(350);
    }
}
```

In `search`, 40 users are selected by the fluent override; the method supplies a 30-second hold and inherits the class engine and ramp-up.
