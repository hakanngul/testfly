---
id: examples
title: Yük Testi Örnekleri ve Tarifleri
sidebar_position: 9
---

# Yük Testi Örnekleri

Bu testler kendi erişilebilir backend’inizi ve [development derlemesini](./getting-started.md) gerektirir. Path, payload ve eşikleri uygulamanıza uyarlayın.

## Health smoke


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


## Login ve ödeme

Filesystem veya classpath üzerinde `testdata/users.csv` oluşturun:

```csv
username,password
demo-user,demo-password
```


```java
import io.testfly.loadtest.BaseLoadTest;
import io.testfly.loadtest.LoadTestFeeder;
import org.testng.annotations.Test;
import java.time.Duration;
import java.util.Map;

public class CheckoutLoadTest extends BaseLoadTest {
    @Test
    public void checkout() {
        loadScenario("Checkout")
            .engine("jdk").users(15).hold(Duration.ofSeconds(10))
            .feed(LoadTestFeeder.csv("testdata/users.csv"))
            .step("Login").post("/login")
                .body(Map.of("username", "${username}", "password", "${password}"))
                .check(status().is(200)).extract("token", "$.accessToken").and()
            .step("Payment").post("/payment")
                .header("Authorization", "Bearer ${token}")
                .body(Map.of("amount", 10)).check(status().is(200)).and()
            .run().assertStepP95Below("Login", 300)
                .assertStepP95Below("Payment", 800)
                .assertStepErrorRateBelow("Payment", 0.01);
    }
}
```


## UI ve yük kapsamını birleştirme

Aynı TestNG suite içinde UI testlerini `BaseTest`, yük testlerini `BaseLoadTest` sınıflarında tutun. UI kontrolünün yük sırasında servisi gözlemlemesi gerekiyorsa ayrı suite/process kullanın. `CompletableFuture.runAsync()` ile TestFly’ın thread-local test ID’sinin veya anotasyon bağlamının arka plan yük çalışmasına taşındığını varsaymayın. `BaseTest`, `load()` sağlamaz; `BaseLoadTest` WebDriver başlatmaz.
