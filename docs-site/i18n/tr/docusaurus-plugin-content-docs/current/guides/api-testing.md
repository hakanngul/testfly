---
description: "TestFly'de API testleri: aynı suite, yapılandırma ve HTML raporunda saf API testleri veya hibrit UI artı API testleri yazın."
sidebar_position: 13
sidebar_label: Başlangıç
---

# API Testleri

TestFly, kutu dışında **saf API testlerini** ve **hibrit UI + API testlerini** destekler — aynı framework, aynı yapılandırma, aynı rapor.

## Saf API Testleri — `BaseApiTest`

`BaseTest` yerine `BaseApiTest`'i genişletin. Tarayıcı başlatılmaz; framework yaşam döngüsünün tamamı yine de geçerlidir (raporlama, `@TestData`, retry, CI eşikleri).

```java
import io.testfly.test.BaseApiTest;
import io.testfly.client.ApiClient;
import io.testfly.client.ApiResponse;
import org.testng.annotations.Test;

public class UserApiTest extends BaseApiTest {

    @Test
    public void getUserById() {
        ApiResponse res = ApiClient.get("https://api.example.com/users/1")
                .send();

        res.assertStatus(200);
        res.assertJson("$.name", "John Doe");
    }
}
```

## Konu Rehberleri

- [İstekler ve Yapılandırma](api-requests.md)
- [Yanıtlar ve Doğrulamalar](api-responses.md)
- [Kimlik Doğrulama](api-auth.md)
- [Interceptor Zinciri](api-interceptors.md)
- [Asenkron Çağrılar ve Batch](api-async-batch.md)
- [Polling](api-polling.md)
- [Şema Doğrulaması](api-schema-validation.md)
- [Hibrit UI + API Testleri](api-hybrid-tests.md)
- [Loglama ve Raporlama](api-reporting.md)
- [Senaryo ve Suite Bağlamı](scenario-context.md)
