# Yük Testleri (BaseLoadTest, LoadScenario)

## Ne Zaman Kullanılır?
Uygulamanın performansını, eşzamanlı (concurrent) kullanıcı yükü altında yanıt sürelerini (P95, P99) ve hata oranlarını ölçeceğiniz zaman kullanılır. (1.0.5 versiyonundan beri desteklenmektedir). Bu testlerde **WebDriver/Tarayıcı kesinlikle açılmaz**.

## Anahtar Prensipler
- Yük testleri `BaseLoadTest` taban sınıfından (veya metod üzerinde `@LoadTest` anotasyonu ile) çalıştırılmalıdır.
- Yük konfigürasyonu 3 katmanlıdır: 1) `testfly.yml` içindeki `loadtest` bloğu 2) Sınıf/Metot seviyesindeki `@LoadTest` anotasyonu 3) Senaryo DSL'indeki (`.users(100)`) tanımlar.
- Çok adımlı API istek (User Journey) senaryolarında akıcı (fluent) `LoadScenario` DSL (Domain Specific Language) kullanılır.
- Altyapı motoru (engine) olarak, büyük ölçekte `gatling`, CI veya hafif ölçekte yerleşik Java (JDK) thread havuzu (`jdk`) seçilebilir.
- Test bitiminde metrikler (Throughput, P95, Error Rate) üzerinden kesinlikle assert (doğrulama) yapılmalıdır.

## Şablon: Yük Testleri

### Hızlı Tek İstekli (Single-Request) Yük Testi
```java
package com.acme.tests.performance;

import io.testfly.loadtest.BaseLoadTest;
import io.testfly.loadtest.LoadTest;
import org.testng.annotations.Test;

public class SearchLoadTest extends BaseLoadTest {

    @Test
    @LoadTest(users = 100, rampUp = "10s", engine = "jdk")
    public void testSearchUnderLoad() {
        load("/api/search?q=test")
            .assertP95Below(500)
            .assertErrorRateBelow(0.01); // %1'den az hata olmalı
    }
}
```

### Çok Adımlı (Multi-Step) Akıcı (Fluent) Yük Senaryosu
```java
package com.acme.tests.performance;

import io.testfly.loadtest.BaseLoadTest;
import org.testng.annotations.Test;
import java.util.Map;

public class CheckoutLoadTest extends BaseLoadTest {

    @Test
    public void testCheckoutFlow() {
        loadScenario("Checkout Journey")
            .users(200)
            .engine("gatling")
            .feedCsv("users.csv")
            .step("Login")
                .post("/api/auth/login")
                .body(Map.of("user", "${username}"))
                .extract("token", "$.accessToken") // Korelasyon
            .step("Order")
                .post("/api/orders")
                .header("Authorization", "Bearer ${token}")
            .thinkTime(500, 2000)
            .run()
            .assertP95Below(800)
            .assertThroughputAbove(50); // Saniyede 50+ istek
    }
}
```

## İyi vs Kötü Pratikler

| Kötü (Anti-Pattern) | İyi (Best Practice) |
|----------------------|----------------------|
| `BaseTest` kullanarak load test denemek | Mutlaka `BaseLoadTest` / `@LoadTest` kullanmak (Tarayıcı açılmasını engeller). |
| Metrikleri sadece rapora basmak, doğrulamamak | `assertP95Below`, `assertErrorRateBelow` ile CI/CD kalite kapısını işletmek. |
| Yük testi sırasında test verisi karmaşası | Data-driven feeder (`feedCsv()`) kullanıp veriyi dışardan enjekte etmek. |

## Checklist
- [ ] Test `BaseLoadTest`'ten mi türedi?
- [ ] `@LoadTest` anotasyonunda `users` ve `rampUp` gibi yük değerleri tanımlandı mı?
- [ ] İş mantığını yansıtacak (korelasyon / token taşıma) multi-step testler için `LoadScenario` yapısı kullanıldı mı?
- [ ] P95, Throughput ve hata oranlarına dayalı `assert*` metotlarıyla kalite kapısı güvence altına alındı mı?
