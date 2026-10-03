# API Testleri (BaseApiTest, ApiClient, ApiResponse)

## Ne Zaman Kullanılır?
Uygulamanın backend servislerini (REST, SOAP, GraphQL vs.) tarayıcı (browser) olmadan doğrulamak için kullanılır. WebDriver başlatma maliyeti sıfırdır; testler çok daha hızlı koşar. Hibrit senaryolarda (UI + API) ise `BaseTest` içindeki `apiClient()` kullanılır.

## Anahtar Prensipler
- Test sınıfı mutlaka `BaseApiTest` (TestNG) veya `BaseJUnit5ApiTest` (JUnit 5) sınıfından türemelidir.
- Çevre (environment) bağımlı temel URL'ler `testfly.yml` içindeki `api.baseUrls` üzerinden okunmalıdır (ör. `ApiClient.toService("payment")`).
- Kimlik doğrulama (auth) bilgileri sabitlenmemeli (hardcoded), `@UseAuth` anotasyonu ile ortamdan alınmalıdır.
- JSONPath doğrulamaları `ApiResponse` üzerindeki akıcı (fluent) arayüz kullanılarak zincirleme (chained) yapılmalıdır.
- Polling (asenkron işlem bekleme) için `apiClient().pollUntil()` kullanılmalıdır.

## Şablon: BaseApiTest

### Basit CRUD ve JSONPath Doğrulama
```java
package com.acme.tests.api;

import io.testfly.test.BaseApiTest;
import io.testfly.client.ApiResponse;
import io.testfly.client.UseAuth;
import org.testng.annotations.Test;

import java.util.Map;

// testfly.yml'daki "admin" yetkisi (Bearer Token, Basic vb.) kullanılır
@UseAuth("admin")
public class UserApiTest extends BaseApiTest {

    @Test
    public void shouldCreateUser() {
        ApiResponse res = apiClient().post("/api/users")
            .body(Map.of("name", "John Doe", "job", "Developer"))
            .send();
            
        res.assertStatus(201)
           .assertJson("$.name", "John Doe")
           .assertJson("$.job", "Developer")
           .assertDurationLessThan(1000);
    }
}
```

### JSON Schema ve OpenAPI Doğrulama
```java
package com.acme.tests.api;

import io.testfly.test.BaseApiTest;
import io.testfly.client.ApiResponse;
import org.testng.annotations.Test;

public class SchemaValidationTest extends BaseApiTest {

    @Test
    public void shouldMatchUserSchema() {
        ApiResponse res = apiClient().get("/api/users/1").send();
        
        res.assertStatus(200)
           .assertSchema("schemas/user.json"); // JSON Schema dosyası `src/test/resources` altında
    }
}
```

## İyi vs Kötü Pratikler

| Kötü (Anti-Pattern) | İyi (Best Practice) |
|----------------------|----------------------|
| URL'in içine test ortamı gömmek (`http://dev...`) | `testfly.yml` konfigürasyonunu (baseUrl) kullanmak. |
| Test metodu içinde token generate edip setHeader yapmak | `ApiAuth` stratejileri veya `@UseAuth` anotasyonu kullanmak. |
| Yanıtı String olarak alıp JSON Parse işlemi ile değer aramak | `res.assertJson("$.user.name", "Ahmet")` JSONPath kullanmak. |
| Asenkron API işlemlerinde Thread.sleep() beklemek | `apiClient().pollUntil(res -> res.status() == 200, timeout)` kullanmak. |

## Checklist
- [ ] Testler sadece API'yi sınıyorsa `BaseApiTest` kullanılmış mı?
- [ ] Base URL'ler ve yetkilendirmeler `testfly.yml` dosyasından okunuyor mu?
- [ ] Doğrulamalar fluent yapıda (örn: `res.assertStatus().assertJson()`) yapılıyor mu?
- [ ] OpenAPI / JSON Schema doğrulama (assertSchema) standartlara uygun olarak kurgulanmış mı?
