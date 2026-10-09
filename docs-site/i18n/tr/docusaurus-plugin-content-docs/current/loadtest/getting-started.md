---
id: getting-started
title: Yük Testine Başlarken
sidebar_position: 1
---

# Yük Testine Başlangıç

Java 21 ve TestNG yük testleri için `io.testfly.loadtest.BaseLoadTest` kullanın. Bu sınıf tarayıcı başlatmadan yük yardımcılarını ve framework yaşam döngüsünü sağlar. `BaseTest`, `BaseApiTest` ve `BaseJUnit5Test`, `load()` yardımcılarını sağlamaz.

## Kullanılabilirlik

Yük testi modülü `1.0.5` sürümünde eklendi (bkz. changelog) ve güncel `1.0.7` sürümüne dahildir. Bağımlılığı Maven Central'dan ekleyin:

```xml
<dependency>
    <groupId>io.github.hakanngul</groupId>
    <artifactId>testfly</artifactId>
    <version>1.0.7</version>
    <scope>test</scope>
</dependency>
```

## Yapılandırma

`loadtest.baseUrl` değerini açıkça ayarlayın; yük motorunda `execution.baseUrl` geri dönüşüne güvenmeyin.

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

## İlk test


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

Eşikler örnektir; servis SLO’larınıza ve ölçülmüş başlangıç değerlerine göre belirleyin. JDK motoru ek yük motoru bağımlılığı istemez. `auto`, Gatling varsa onu, yoksa JDK motorunu seçer; [motorlar](./engines.md) sayfasına bakın.

## Sonraki adımlar

[Yapılandırma](./configuration.md) · [Fluent API](./fluent-api.md) · [Anotasyonlar](./annotations.md) · [Feeder](./feeders.md) · [Assertion](./assertions.md)
