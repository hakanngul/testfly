# TestNG Kullanımı (Suite, Retry, Data, PreCondition)

## Ne Zaman Kullanılır?
TestFly'ın ana (varsayılan) test runner'ı TestNG'dir. Paralel çalışma, testleri gruplama, test veri kümesi yükleme (Data-driven testing), ön koşul bağlamı sağlama ve tekrar deneme (retry) ihtiyaçlarında testleri düzenlemek için kullanılır.

## Anahtar Prensipler
- Test sınıfları için ortam `testfly.yml` dosyasından yüklenir, profiller `-Dtestfly.profile=staging` ile değiştirilir.
- TestNG `suite.xml` ile paralel koşumlar (`parallel="methods"` veya `parallel="classes"`) kolayca konfigüre edilir; framework statik değişken kullanmadığından güvenlidir.
- Kırılgan testlerde hata durumunda otomatik tekrarlama için `@Retryable` anotasyonu kullanılır.
- Tekrar tekrar login olmak yerine oturum bilgisini paylaşmak için `@PreCondition` mekanizması kullanılır.
- Veri yönlendirmeli (Data-driven) testler için yerleşik `@TestData` anotasyonu, harici dosyalardan JSON, YAML, CSV veya DB okumak için kullanılır.

## Şablon: TestNG Özellikleri

### Veri Yönlendirmeli ve Tekrar Denenebilir Test (Data & Retry)
```java
package com.acme.tests;

import io.testfly.test.BaseTest;
import io.testfly.listeners.Retryable;
import io.testfly.testdata.TestData;
import org.testng.annotations.Test;

import java.util.Map;

public class DataDrivenTest extends BaseTest {

    // testdata/users.csv dosyasından ilk veri satırını çeker (başlık atlanarak).
    @Test
    @Retryable(maxAttempts = 3)
    @TestData("csv:testdata/users.csv") 
    public void loginWithDataFile(Map<String, String> data) {
        String user = data.get("username");
        String pass = data.get("password");
        
        open("/login");
        // ... login işlemleri
        assertThatPage().hasUrlContaining("dashboard");
    }
}
```

### Ön Koşul İle Oturum Ön Bellekleme (PreCondition)
```java
package com.acme.tests;

import io.testfly.test.BaseTest;
import io.testfly.precondition.PreCondition;
import org.testng.annotations.Test;

public class ProfileTest extends BaseTest {

    // ConditionProvider kayıtlı "loginAsAdmin" metodunu çalıştırır veya
    // önceki çalıştırmanın cookies/localStorage verisini kullanarak login atlar.
    @Test
    @PreCondition("loginAsAdmin")
    public void shouldEditProfile() {
        open("/profile/settings"); // Artık giriş yapılmış durumda!
        
        // ... işlemler
    }
}
```

## İyi vs Kötü Pratikler

| Kötü (Anti-Pattern) | İyi (Best Practice) |
|----------------------|----------------------|
| Özel RetryAnalyzer sınıfı yazıp XML'den bağlamak | Dahili `@Retryable` anotasyonunu kullanmak. |
| Tüm testlerde `@BeforeMethod` ile baştan login olmak | `@PreCondition` ile cookie/state restore (session) etmek. |
| CSV okumak için Apache POI'yi test içinde açıp kodlamak | `@TestData("csv:file.csv")` framework özelliğini kullanmak. |

## Checklist
- [ ] Yeniden denenebilirlik için `@Retryable` kullanıldı mı?
- [ ] Ön koşul ve oturum gerektiren testler `@PreCondition` ile hızlandırıldı mı?
- [ ] Veriye dayalı testlerde statik veriler yerine `@TestData` anotasyonu kullanıldı mı?
- [ ] Çoklu iş parçacığı durumları için suite konfigürasyonu (`parallel` attributeleri) kontrol edildi mi?
