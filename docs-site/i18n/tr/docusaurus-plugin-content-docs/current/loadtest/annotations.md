---
id: annotations
title: Deklaratif Anotasyonlar (@LoadTest)
sidebar_position: 4
---

# Deklaratif Anotasyonlar

`@LoadTest` sınıfa veya test metoduna uygulanır. Ayarlanmayan alanlar bir sonraki yapılandırma düzeyinden alınır. Ayrı `@LoadEngine` yoktur; `@LoadTest(engine = "jdk")` kullanın.

| Alan | Tip | Ayarlanmayan varsayılan |
| --- | --- | --- |
| `users` | `int` | `-1` |
| `rampUp` | `String` | `""` |
| `hold` | `String` | `""` |
| `cooldown` | `String` | `""` |
| `engine` | `String` | `""` |
| `baseUrl` | `String` | `""` |

Süreler `5s`, `2m`, `1h` gibi string değerlerdir. Motorlar `auto`, `gatling`, `jdk` olabilir. Öncelik fluent senaryo → metot anotasyonu → sınıf anotasyonu → YAML → varsayılanlar şeklindedir. Kullanıcı sayısı `maxUsers` ile sınırlandırılır.


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

`search` içinde fluent ayar 40 kullanıcıyı seçer; metot 30 saniyelik hold verir ve sınıftan motor ile ramp-up değerlerini alır.
