---
title: "Interceptor Zinciri"
---

# Interceptor Zinciri

`ApiInterceptor`, hazırlanmış isteği değiştirebilir, yanıtı inceleyip değiştirebilir, sonraki halkaları tekrar çalıştırabilir veya ağa çıkmadan sentetik yanıt döndürebilir. Aynı blocking sözleşme `send()` içinde ve yönetilen virtual thread üzerindeki `sendAsync()` içinde çalışır.

```java
ApiClient.addInterceptor(chain -> chain.proceed(
    chain.request().newBuilder().header("X-Tenant", "test").build()));

ApiClient.get("https://api.example.com/users")
    .interceptor(chain -> chain.proceed(chain.request()))
    .send();

ApiClient.clearChainInterceptors();
```

`addInterceptor()` geçerli test thread’ine; `.interceptor()` ilgili request instance’ına kayıt yapar. Önce test, ardından istek interceptor’ları kayıt sırasıyla çalışır. Framework temizliği test kayıtlarını, auth ve cookie bağlamını temizler, tamamlanmamış API future’larını iptal eder. Başka testin kayıtlarına dokunmaz. `clearInterceptors()` eski global hook’ları ve çağıran thread’in yeni zincir kayıtlarını temizler.

### Yürütme sözleşmesi

- `proceed(request)` bir sonraki halkayı çalıştırır. Ardışık tekrarlar aynı downstream bölümünü yeniden başlatır; geçerli interceptor yeniden çağrılmaz.
- Chain yalnızca `intercept()` çalışırken sahibinin thread’inde geçerlidir. Kaydedip sonradan veya başka thread’de kullanmak `IllegalStateException` üretir. Null istek/yanıt reddedilir.
- İstek; çözümlenmiş URI, method, timeout, çok değerli header ve kopyalanmış body byte’ları içerir. `header()` büyük/küçük harf gözetmeden değiştirir; `addHeader()` ekler; `removeHeader()` kaldırır. JSON/form/multipart body mantıksal çağrı başında bir kez hazırlanır ve aynı byte’larla tekrar gönderilir.
- 401/503 dahil geçerli HTTP yanıtları normal response olarak döner; assertion test kodundadır. `ApiResponse.builder()` request ve 100–599 arası status gerektirir; varsayılan body boş string, süre sıfırdır. `newBuilder()` yanıtı değiştirmek için mevcut verileri korur.
- YAML API retry zincirin dışındadır. Her otomatik denemede auth ve middleware özgün snapshot’tan yeniden çalışır. `maxAttempts` dış denemeleri sayar; ek `proceed()` çağrıları gerçek HTTP gönderim sayısını artırabilir. Retry varsayılan olarak kapalıdır.
- Yalnızca transport I/O hataları ve yapılandırılmış status’lar retry tetikler. Interceptor/hook hataları ve assertion başarısızlıkları tekrarlanmaz. Interrupt/cancellation çağrıyı ve backoff’u sonlandırır.
- Legacy request hook’ları her gerçek gönderimden önce çalışır; sentetik yanıtta çalışmaz. Eski method/body atama davranışı korunur. Legacy response hook’ları sentetik sonuç dahil her dış denemenin nihai zincir yanıtını bir kez görür. Refresh içindeki ara yanıtlar yeni middleware tarafından gözlemlenir.
- Redirect takibi JDK HttpClient’tadır; her redirect hop’u ayrı interceptor çağrısı üretmez.

### Derlenen örnekler

Aşağıdaki örnekler framework test kaynaklarıyla derlenir. Refresh örneği her interceptor çağrısında en fazla bir ek gönderim yapar, token endpoint’ini hariç tutar ve ikinci 401’i kullanıcıya döndürür. Korumalı isteğe kaydedin. Token supplier, token endpoint’ini bu istek kapsamlı refresh interceptor’ını eklemeden çağırmalıdır. Örnek OAuth2 cache invalidation veya paralel yenileme koordinasyonu sağlamaz; paralel kullanımda supplier thread-safe olmalıdır.

```java
package io.testfly.examples.api;

import io.testfly.client.*;
import io.testfly.steps.StepLogger;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Compile-checked examples shared with the API testing guide. */
public final class ApiInterceptorExamples {
    private ApiInterceptorExamples() {}

    public static final class LoggingInterceptor implements ApiInterceptor {
        @Override public ApiResponse intercept(Chain chain) {
            StepLogger.step("API start: " + chain.request().method() + " " + chain.request().uri());
            try { return chain.proceed(chain.request()); }
            finally { StepLogger.step("API end"); }
        }
    }

    public static final class AuthRefreshInterceptor implements ApiInterceptor {
        private final URI tokenEndpoint;
        private final Supplier<String> refreshToken;
        public AuthRefreshInterceptor(URI tokenEndpoint, Supplier<String> refreshToken) {
            this.tokenEndpoint = Objects.requireNonNull(tokenEndpoint);
            this.refreshToken = Objects.requireNonNull(refreshToken);
        }
        @Override public ApiResponse intercept(Chain chain) {
            ApiRequest request = chain.request();
            ApiResponse response = chain.proceed(request);
            // Token calls are excluded; the second response is returned without another refresh.
            if (response.status() != 401 || request.uri().equals(tokenEndpoint)) return response;
            String token = Objects.requireNonNull(refreshToken.get(), "refreshed token");
            if (token.isBlank()) throw new IllegalStateException("Empty refreshed token");
            return chain.proceed(request.newBuilder().header("Authorization", "Bearer " + token).build());
        }
    }

    public static ApiInterceptor paymentMock() {
        return chain -> {
            ApiRequest request = chain.request();
            if (request.method().equals("POST") && request.uri().getPath().equals("/payment")) {
                return ApiResponse.builder().request(request).status(200)
                        .header("Content-Type", "application/json")
                        .body("{\"status\":\"mocked\"}").build();
            }
            return chain.proceed(request);
        };
    }

    public static ApiResponse syncPayment(String baseUrl) {
        return ApiClient.post(baseUrl + "/payment")
                .interceptor(new LoggingInterceptor()).interceptor(paymentMock()).send();
    }

    public static CompletableFuture<ApiResponse> asyncPayment(String baseUrl) {
        return ApiClient.post(baseUrl + "/payment")
                .interceptor(new LoggingInterceptor()).interceptor(paymentMock()).sendAsync();
    }

    public static ApiResponse protectedRequest(String baseUrl, String expiredToken, Supplier<String> tokenProvider) {
        return ApiClient.get(baseUrl + "/protected").auth(ApiAuth.bearerToken(expiredToken))
                .interceptor(new AuthRefreshInterceptor(URI.create(baseUrl + "/token"), tokenProvider)).send();
    }

    public static ApiResponse negativeAuthTest(String baseUrl, String invalidToken) {
        // No refresh interceptor: a genuine 401 remains available for the assertion.
        return ApiClient.get(baseUrl + "/protected").auth(ApiAuth.bearerToken(invalidToken)).send().assertStatus(401);
    }
}
```

Asenkron bağlam ve iptal için [Asenkron Çağrılar ve Batch](api-async-batch.md), çıktı kuralları için [Loglama ve Raporlama](api-reporting.md) sayfalarına bakın.
