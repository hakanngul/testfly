# JUnit 5 Testleri (BaseJUnit5Test, @EnableTestFly)

## Ne Zaman Kullanılır?
Kurum standardı olarak TestNG yerine JUnit 5 (Jupiter) tercih ediliyorsa kullanılır. TestFly, JUnit 5 için özel bridge (köprü) yapıları sunarak WebDriver lifecycle'ını ve raporlama modüllerini Jupiter Engine üzerinden çalıştırır.

## Anahtar Prensipler
- JUnit 5 testleri TestFly uzantısını (extension) aktif etmek için `@EnableTestFly` anotasyonu kullanmalıdır.
- Sınıflar `BaseJUnit5Test` veya (sadece API için) `BaseJUnit5ApiTest` sınıflarından türetilir.
- TestNG `@Test` yerine `org.junit.jupiter.api.Test` kullanılır.
- Özel Not: JUnit 5 ortamında, TestFly'ın `@Retryable` anotasyonu sadece test metodunun gövdesini (method body) tekrar çalıştırır, `@BeforeEach` ve `@AfterEach` yaşam döngüleri **tekrarlanmaz**. TestNG'den farklı davranır.
- JUnit 5 test sınıflarının yaşam döngüsü (lifecycle) varsayılan olarak `PER_METHOD` şeklindedir; `testfly.yml` içindeki `browser.lifecycle: per-suite` ayarı JUnit 5'te **test sınıfı başına** (per test class) tekrar kullanımı ifade eder.

## Şablon: BaseJUnit5Test

### UI Testi (JUnit 5)
```java
package com.acme.tests;

import io.testfly.junit5.BaseJUnit5Test;
import io.testfly.junit5.EnableTestFly;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

@EnableTestFly
public class JUnit5DemoTest extends BaseJUnit5Test {

    @Test
    @DisplayName("Ana sayfa başlığının doğrulanması")
    void shouldLoadHomepage() {
        open("https://example.com");
        
        // Assertions API'leri TestNG ile tamamen aynıdır
        assertThatPage().hasTitle("Example Domain");
    }
}
```

### Yalnızca API Testi (JUnit 5)
```java
package com.acme.tests.api;

import io.testfly.junit5.BaseJUnit5ApiTest;
import io.testfly.junit5.EnableTestFly;
import io.testfly.client.ApiResponse;
import org.junit.jupiter.api.Test;

@EnableTestFly
public class JUnit5ApiDemoTest extends BaseJUnit5ApiTest {

    @Test
    void shouldFetchData() {
        ApiResponse res = apiClient().get("/api/data").send();
        
        res.assertStatus(200);
    }
}
```

## İyi vs Kötü Pratikler

| Kötü (Anti-Pattern) | İyi (Best Practice) |
|----------------------|----------------------|
| `BaseTest` (TestNG) taban sınıfını JUnit 5 `@Test` ile kullanmak | `BaseJUnit5Test` taban sınıfını kullanmak. |
| Test classına extension eklemeyi unutmak | Sınıfa `@EnableTestFly` anotasyonu eklemek. |
| Kırılgan testlerde datanın `@BeforeEach`'de sıfırlanmasını beklemek (@Retryable) | JUnit 5 retry mekanizmasında test içi (method body) state'ini yönetmek. |

## Checklist
- [ ] Test sınıfında `@EnableTestFly` anotasyonu var mı?
- [ ] Test sınıfı `BaseJUnit5Test` / `BaseJUnit5ApiTest` sınıfından miras alıyor mu?
- [ ] Doğru (JUnit 5) `org.junit.jupiter.api.Test` import edildi mi?
- [ ] Retry durumunda sadece metodun gövdesinin çalıştırılacağı (BeforeEach hariç) göz önüne alındı mı?
