# TestFly API Test Altyapısı — İyileştirme Planı

> **Oluşturulma:** 22 Ağustos 2026  
> **Durum:** Plan aşaması — implementasyon bekliyor  
> **Kapsam:** `ApiClient`, `ApiResponse`, `ApiAuth`, `BaseApiTest` ve ilgili tüm API bileşenleri

---

## İçindekiler

- [1. Mevcut Durum Özeti](#1-mevcut-durum-özetİ)
- [2. Kritik Sorunlar (P0)](#2-kritik-sorunlar-p0)
- [3. Önemli Eksiklikler (P1)](#3-Önemli-eksiklikler-p1)
- [4. İyileştirme Önerileri (P2)](#4-i̇yileştirme-Önerileri-p2)
- [5. Küçük Kazanımlar (P3)](#5-küçük-kazanımlar-p3)
- [6. Paralel Test Güvenlik Planı](#6-paralel-test-güvenlik-planı)
- [7. Implementasyon Sırası ve Tahmini Efor](#7-i̇mplementasyon-sırası-ve-tahmini-efor)
- [8. Test Stratejisi](#8-test-stratejisi)

---

## 1. Mevcut Durum Özeti

### İncelenen Dosyalar

| Dosya | Sorumluluk |
|-------|-----------|
| `client/ApiClient.java` | Fluent HTTP client — JDK `HttpClient` üzerine kurulu |
| `client/ApiResponse.java` | Response wrapper — assertions, JSON extraction |
| `client/ApiAuth.java` | Auth stratejileri — Bearer, Basic, OAuth2 + token cache |
| `client/UseAuth.java` | Declarative auth annotation |
| `client/SchemaValidator.java` | JSON Schema validation (networknt) |
| `test/BaseApiTest.java` | API test base class — lifecycle, context, soft assert |
| `context/ScenarioContext.java` | Thread-local test context |
| `context/SuiteContext.java` | Suite-level shared context (ConcurrentHashMap) |
| `precondition/ApiHealthChecker.java` | `@DependsOnApi` health check + cache |
| `internal/TestFlyContext.java` | Framework-wide config (AtomicReference) |
| `config/TestFlyConfig.Api` | API config model — baseUrl, timeout, logBody, auth strategies |

### Güçlü Yönler (Korunacak)

- ✅ Fluent API tasarımı — `apiClient().post("/users").body(map).send().assertStatus(201)`
- ✅ JSONPath extraction — `$.data[0].name` → Jackson JsonPointer dönüşümü
- ✅ OAuth2 client credentials — token fetch + cache (expiry - 60s buffer)
- ✅ Thread-local auth — `ThreadLocal<ApiAuth>` ile paralel test izolasyonu
- ✅ JSON Schema validation — optional dependency, classpath'ten yükleme
- ✅ ScenarioContext — `ThreadLocal<Map>` test-scoped, otomatik clear
- ✅ SuiteContext — `ConcurrentHashMap` suite-scoped, testler arası paylaşım
- ✅ `@DependsOnApi` — API erişilemezse skip, cache'li
- ✅ StepLogger entegrasyonu — her API çağrısı otomatik loglanır
- ✅ Soft assertions — çoklu assertion, test sonunda toplu rapor
- ✅ `@UseAuth` annotation — YAML'dan auth stratejisi resolution
- ✅ Sıfır dış bağımlılık — JDK HttpClient + Jackson

---

## 2. Kritik Sorunlar (P0)

### 2.1 HTTP-Level Retry Mekanizması Yok

**Dosya:** `ApiClient.java` → `send()` metodu  
**Etki:** 🔴 Yüksek | **Efor:** 🟢 Düşük (1-2 saat)

**Mevcut durum:**
```java
// ApiClient.send() — exception fırlatır, retry yok
} catch (Exception e) {
    StepLogger.step("[API] ... → ERROR: " + e.getMessage(), StepStatus.FAIL);
    throw new RuntimeException("[ApiClient] Request failed: ...", e);
}
```

**Sorun:** Transient network hataları (connection reset, DNS timeout, 502/503/504) testi doğrudan fail eder. API testing'te retry standart bir ihtiyaç.

**Çözüm:**

Config'e retry bloğu ekle:
```yaml
# testfly.yml
api:
  baseUrl: https://api.example.com
  retry:
    enabled: true
    maxAttempts: 3
    backoffMs: 500
    retryOnStatus: [502, 503, 504]
    retryOnException: true
```

`ApiClient.send()` içine retry loop ekle:
```java
public ApiResponse send() {
    int maxAttempts = resolveRetryMaxAttempts();
    List<Integer> retryOnStatus = resolveRetryStatusCodes();
    long backoffMs = resolveBackoffMs();

    RuntimeException lastException = null;
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
        try {
            ApiResponse response = executeRequest();
            if (attempt < maxAttempts && retryOnStatus.contains(response.status())) {
                StepLogger.step("[API] Retry " + attempt + "/" + maxAttempts
                    + " — status " + response.status(), StepStatus.WARN);
                sleep(backoffMs * attempt); // exponential backoff
                continue;
            }
            return response;
        } catch (RuntimeException e) {
            lastException = e;
            if (attempt < maxAttempts && shouldRetryOnException()) {
                StepLogger.step("[API] Retry " + attempt + "/" + maxAttempts
                    + " — " + e.getMessage(), StepStatus.WARN);
                sleep(backoffMs * attempt);
            }
        }
    }
    throw lastException;
}
```

**Config model güncellemesi:**
```java
public static final class Api {
    // ... mevcut alanlar ...
    private RetryConfig retry = new RetryConfig();

    public static final class RetryConfig {
        private boolean enabled = false;
        private int maxAttempts = 3;
        private long backoffMs = 500;
        private List<Integer> retryOnStatus = List.of(502, 503, 504);
        private boolean retryOnException = true;
        // getters + setters
    }
}
```

---

## 3. Önemli Eksiklikler (P1)

### 3.1 Per-Request Timeout Override

**Dosya:** `ApiClient.java`  
**Etki:** 🟡 Orta | **Efor:** 🟢 Düşük (30 dk)

**Mevcut:** Tüm request'ler aynı timeout değerini kullanır (config'den gelen `api.timeoutSeconds` veya default 30s).

**Sorun:** Yavaş endpoint'ler (report generation, batch export) 30s timeout'a takılır.

**Çözüm:**
```java
public class ApiClient {
    private Integer timeoutOverride; // null = use config default

    public ApiClient timeout(int seconds) {
        this.timeoutOverride = seconds;
        return this;
    }

    private int resolveTimeout() {
        if (timeoutOverride != null) return timeoutOverride;
        // ... mevcut config resolution ...
    }
}
```

**Kullanım:**
```java
apiClient().get("/reports/annual-export")
    .timeout(120)
    .send()
    .assertStatus(200);
```

---

### 3.2 Query Parameter Builder

**Dosya:** `ApiClient.java`  
**Etki:** 🟡 Orta | **Efor:** 🟢 Düşük (45 dk)

**Mevcut:**
```java
apiClient().get("/users?page=" + page + "&limit=" + limit + "&sort=name").send();
```

**Sorun:** Elle string birleştirme — URL encoding eksik, okunabilirlik düşük, bug-prone.

**Çözüm:**
```java
public class ApiClient {
    private final Map<String, String> queryParams = new LinkedHashMap<>();

    public ApiClient queryParam(String name, Object value) {
        queryParams.put(name, String.valueOf(value));
        return this;
    }

    public ApiClient queryParams(Map<String, ?> params) {
        params.forEach((k, v) -> queryParams.put(k, String.valueOf(v)));
        return this;
    }

    // send() içinde URL'ye ekle
    private String buildUrl() {
        String url = resolveBaseUrl() + path;
        if (!queryParams.isEmpty()) {
            String query = queryParams.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), UTF_8)
                    + "=" + URLEncoder.encode(e.getValue(), UTF_8))
                .collect(Collectors.joining("&"));
            url += (url.contains("?") ? "&" : "?") + query;
        }
        return url;
    }
}
```

**Kullanım:**
```java
apiClient().get("/users")
    .queryParam("page", 1)
    .queryParam("limit", 10)
    .queryParam("sort", "name")
    .queryParam("filter", "active")
    .send();
```

---

### 3.3 Response Time Assertion

**Dosya:** `ApiResponse.java`  
**Etki:** 🟡 Orta | **Efor:** 🟢 Düşük (20 dk)

**Mevcut:**
```java
res.durationMs();  // long döner, assertion yok
```

**Çözüm:**
```java
public ApiResponse assertDurationLessThan(long maxMs) {
    StepLogger.step("Assert API duration < " + maxMs + "ms");
    if (durationMs > maxMs) {
        throw new AssertionError(
            "[ApiResponse] Request took " + durationMs + "ms, expected < " + maxMs + "ms");
    }
    return this;
}

public ApiResponse assertDurationLessThan(long max, TimeUnit unit) {
    return assertDurationLessThan(unit.toMillis(max));
}
```

**Kullanım:**
```java
res.assertStatus(200)
   .assertDurationLessThan(500)
   .assertJson("$.status", "ok");
```

---

## 4. İyileştirme Önerileri (P2)

### 4.1 Statik HttpClient Singleton → Thread-Local veya Configurable

**Dosya:** `ApiClient.java`  
**Etki:** 🔴 Yüksek (paralel testler için) | **Efor:** 🟡 Orta (2 saat)

**Mevcut:**
```java
private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
```

**Sorun:** 8+ thread paralel testlerde connection pool starvation. JDK HttpClient pool boyutu konfigüre edilemez.

**Çözüm A — Thread-local HttpClient:**
```java
private static final ThreadLocal<HttpClient> HTTP = ThreadLocal.withInitial(() ->
    HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build());

// send() içinde:
HttpResponse<String> raw = HTTP.get().send(builder.build(), ...);
```

**Çözüm B — Configurable executor (önerilen):**
```java
private static final HttpClient HTTP;
static {
    int poolSize = resolveApiPoolSize(); // config'den: api.connectionPoolSize
    HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .executor(Executors.newFixedThreadPool(poolSize))
        .build();
}
```

**Config ekleme:**
```yaml
api:
  connectionPoolSize: 10  # paralel thread sayısına göre ayarla
```

**Not:** `ThreadLocal<HttpClient>` daha güvenli ama her thread kendi connection pool'u tutar → memory overhead. Configurable executor daha verimli.

---

### 4.2 OAuth2 Token Cache — Race Condition Fix

**Dosya:** `ApiAuth.java` → `OAuth2TokenCache.getToken()`  
**Etki:** 🟡 Orta | **Efor:** 🟢 Düşük (30 dk)

**Mevcut:**
```java
static String getToken(String tokenUrl, String clientId, String clientSecret) {
    String key = tokenUrl + "|" + clientId;
    CachedToken cached = CACHE.get(key);
    if (cached != null && !cached.isExpired()) return cached.token;
    // ⚠️ Race: 10 thread aynı anda buraya gelebilir → 10 token fetch
    CachedToken fresh = fetchToken(tokenUrl, clientId, clientSecret);
    CACHE.put(key, fresh);
    return fresh.token;
}
```

**Çözüm — `computeIfAbsent` + double-check:**
```java
static String getToken(String tokenUrl, String clientId, String clientSecret) {
    String key = tokenUrl + "|" + clientId;
    CachedToken cached = CACHE.get(key);
    if (cached != null && !cached.isExpired()) return cached.token;

    // Sadece bir thread fetch yapar, diğerleri bekler
    synchronized (CACHE) {
        cached = CACHE.get(key); // double-check
        if (cached != null && !cached.isExpired()) return cached.token;
        CachedToken fresh = fetchToken(tokenUrl, clientId, clientSecret);
        CACHE.put(key, fresh);
        return fresh.token;
    }
}
```

**Alternatif — `ConcurrentHashMap.compute`:**
```java
static String getToken(String tokenUrl, String clientId, String clientSecret) {
    String key = tokenUrl + "|" + clientId;
    CachedToken result = CACHE.compute(key, (k, existing) -> {
        if (existing != null && !existing.isExpired()) return existing;
        return fetchToken(tokenUrl, clientId, clientSecret);
    });
    return result.token;
}
```

---

### 4.3 Multipart / File Upload Desteği

**Dosya:** `ApiClient.java`  
**Etki:** 🟡 Orta | **Efor:** 🟡 Orta (2-3 saat)

**Mevcut:** Sadece JSON body desteği var.

**Çözüm:**
```java
public class ApiClient {
    private final List<MultipartPart> multipartParts = new ArrayList<>();
    private boolean isMultipart = false;

    public ApiClient multipart() {
        this.isMultipart = true;
        return this;
    }

    public ApiClient file(String fieldName, Path filePath) {
        multipartParts.add(new MultipartPart.FilePart(fieldName, filePath));
        return this;
    }

    public ApiClient file(String fieldName, Path filePath, String contentType) {
        multipartParts.add(new MultipartPart.FilePart(fieldName, filePath, contentType));
        return this;
    }

    public ApiClient field(String name, String value) {
        multipartParts.add(new MultipartPart.FieldPart(name, value));
        return this;
    }

    // send() içinde:
    private HttpRequest.BodyPublisher buildPublisher() {
        if (isMultipart) {
            return buildMultipartPublisher();
        }
        // ... mevcut body serialization ...
    }

    private HttpRequest.BodyPublisher buildMultipartPublisher() {
        String boundary = UUID.randomUUID().toString();
        this.header("Content-Type", "multipart/form-data; boundary=" + boundary);

        StringBuilder sb = new StringBuilder();
        List<byte[]> fileBytes = new ArrayList<>();

        for (MultipartPart part : multipartParts) {
            sb.append("--").append(boundary).append("\r\n");
            if (part instanceof MultipartPart.FieldPart fp) {
                sb.append("Content-Disposition: form-data; name=\"")
                  .append(fp.name).append("\"\r\n\r\n")
                  .append(fp.value).append("\r\n");
            } else if (part instanceof MultipartPart.FilePart fp) {
                sb.append("Content-Disposition: form-data; name=\"")
                  .append(fp.name).append("\"; filename=\"")
                  .append(fp.filePath.getFileName()).append("\"\r\n")
                  .append("Content-Type: ").append(fp.contentType).append("\r\n\r\n");
                // ... file bytes ...
            }
        }
        sb.append("--").append(boundary).append("--\r\n");
        // ... combine into byte array and return BodyPublishers.ofByteArray() ...
    }
}
```

**Kullanım:**
```java
apiClient().post("/api/upload")
    .multipart()
    .field("description", "Q4 report")
    .file("document", Path.of("report.pdf"), "application/pdf")
    .send()
    .assertStatus(201);
```

---

### 4.4 Cookie / Session Yönetimi

**Dosya:** `ApiClient.java`  
**Etki:** 🟡 Orta | **Efor:** 🟡 Orta (2 saat)

**Mevcut:** Her request bağımsız. Set-Cookie header'ı kaydedilmiyor, sonraki request'lere gönderilmiyor.

**Çözüm — Basit cookie jar:**
```java
public class ApiClient {
    private static final ThreadLocal<Map<String, String>> COOKIE_JAR =
        ThreadLocal.withInitial(HashMap::new);

    public ApiClient withCookies() {
        // send() sonrası Set-Cookie'leri kaydet
        // send() öncesi Cookie header ekle
        return this;
    }

    // send() sonunda:
    private void captureCookies(HttpResponse<String> response) {
        response.headers().allValues("Set-Cookie").forEach(cookie -> {
            String name = cookie.split("=")[0];
            String value = cookie.split(";")[0];
            COOKIE_JAR.get().put(name, value);
        });
    }

    // send() başında:
    private void applyCookies(HttpRequest.Builder builder) {
        if (!COOKIE_JAR.get().isEmpty()) {
            String cookieHeader = COOKIE_JAR.get().entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("; "));
            builder.header("Cookie", cookieHeader);
        }
    }

    public static void clearCookies() {
        COOKIE_JAR.remove();
    }
}
```

**Kullanım:**
```java
// Login → cookie kaydet → sonraki request'lerde otomatik gönder
apiClient().post("/login").body(creds).withCookies().send().assertStatus(200);
apiClient().get("/profile").withCookies().send().assertStatus(200); // Cookie otomatik eklenir
```

---

### 4.5 Request / Response Interceptor

**Dosya:** `ApiClient.java`  
**Etki:** 🟡 Orta | **Efor:** 🟢 Düşük (1 saat)

**Mevcut:** Interceptor yok. Her request'e manuel header eklemek gerekiyor.

**Çözüm:**
```java
@FunctionalInterface
public interface RequestInterceptor {
    void intercept(HttpRequest.Builder builder);
}

@FunctionalInterface
public interface ResponseInterceptor {
    void intercept(ApiResponse response);
}

public class ApiClient {
    private static final List<RequestInterceptor> REQUEST_INTERCEPTORS = new CopyOnWriteArrayList<>();
    private static final List<ResponseInterceptor> RESPONSE_INTERCEPTORS = new CopyOnWriteArrayList<>();

    public static void addRequestInterceptor(RequestInterceptor interceptor) {
        REQUEST_INTERCEPTORS.add(interceptor);
    }

    public static void addResponseInterceptor(ResponseInterceptor interceptor) {
        RESPONSE_INTERCEPTORS.add(interceptor);
    }

    public static void clearInterceptors() {
        REQUEST_INTERCEPTORS.clear();
        RESPONSE_INTERCEPTORS.clear();
    }

    // send() içinde:
    REQUEST_INTERCEPTORS.forEach(i -> i.intercept(builder));
    // ... execute request ...
    RESPONSE_INTERCEPTORS.forEach(i -> i.intercept(response));
}
```

**Kullanım:**
```java
// @BeforeSuite
ApiClient.addRequestInterceptor(builder ->
    builder.header("X-Correlation-Id", UUID.randomUUID().toString()));

ApiClient.addResponseInterceptor(response ->
    metrics.record("api.response." + response.status(), response.durationMs()));
```

---

## 5. Küçük Kazanımlar (P3)

### 5.1 Header Assertion

```java
// ApiResponse.java
public ApiResponse assertHeader(String name, String expectedValue) {
    String actual = header(name);
    if (!expectedValue.equals(actual)) {
        throw new AssertionError(
            "[ApiResponse] Header '" + name + "': expected '" + expectedValue
            + "' but got '" + actual + "'");
    }
    return this;
}

public ApiResponse assertHeaderPresent(String name) {
    if (header(name) == null) {
        throw new AssertionError("[ApiResponse] Expected header '" + name + "' to be present");
    }
    return this;
}
```

### 5.2 Body Regex Assertion

```java
// ApiResponse.java
public ApiResponse assertBodyMatches(String regex) {
    if (!response.body().matches("(?s).*" + regex + ".*")) {
        throw new AssertionError(
            "[ApiResponse] Body does not match regex: '" + regex + "'. "
            + "Body: " + truncate(response.body(), 300));
    }
    return this;
}
```

### 5.3 JSON Array Length Assertion

```java
// ApiResponse.java
public ApiResponse assertJsonArraySize(String path, int expectedSize) {
    JsonNode node = jsonNode(path);
    if (node == null || !node.isArray()) {
        throw new AssertionError("[ApiResponse] '" + path + "' is not an array");
    }
    if (node.size() != expectedSize) {
        throw new AssertionError(
            "[ApiResponse] Array '" + path + "': expected size " + expectedSize
            + " but got " + node.size());
    }
    return this;
}
```

### 5.4 JSON Null / Exists Assertion

```java
public ApiResponse assertJsonExists(String path) {
    JsonNode node = jsonNode(path);
    if (node == null || node.isMissingNode()) {
        throw new AssertionError("[ApiResponse] JSON path '" + path + "' does not exist");
    }
    return this;
}

public ApiResponse assertJsonNull(String path) {
    JsonNode node = jsonNode(path);
    if (node != null && !node.isNull() && !node.isMissingNode()) {
        throw new AssertionError(
            "[ApiResponse] JSON path '" + path + "': expected null but got '" + node.asText() + "'");
    }
    return this;
}
```

### 5.5 Truncation Limit Configurable

```yaml
# testfly.yml
api:
  truncationLimit: 1000  # assertion hata mesajlarında body kesme limiti (default: 300)
```

---

## 6. Paralel Test Güvenlik Planı

### Mevcut Thread-Safety Durumu

| Bileşen | Thread-Safe? | Mekanizma | Risk |
|---------|:-----------:|-----------|------|
| `ApiClient` instance | ✅ | Her çağrı yeni instance | — |
| `GLOBAL_AUTH` | ✅ | `ThreadLocal<ApiAuth>` | — |
| `HttpClient HTTP` | ⚠️ | Shared singleton | 8+ thread'te connection starvation |
| `OAuth2TokenCache` | ⚠️ | `ConcurrentHashMap` | Thundering herd (token expire anı) |
| `ScenarioContext` | ✅ | `ThreadLocal<Map>` | — |
| `SuiteContext` | ⚠️ | `ConcurrentHashMap` | Check-then-act race condition |
| `TestFlyContext` | ✅ | `AtomicReference` + immutable | — |
| `ObjectMapper MAPPER` | ✅ | Thread-safe for reads | Config değişirse risk |
| `ApiHealthChecker` | ✅ | `computeIfAbsent` | — |

### Paralel Test Tavsiyeleri

**4 thread'e kadar:** Güvenli. Hiçbir değişiklik gerekmez.

**4-8 thread:** Güvenli ama dikkatli olunmalı:
- `SuiteContext` üzerinden aynı key'e paralel yazma yapılmamalı
- OAuth2 token expire anında thundering herd yaşanabilir (düzeltme P2'de)

**8+ thread:** `HttpClient` singleton düzeltilmeli:
- `api.connectionPoolSize` config'e eklenmeli (P2-4.1)
- Veya `ThreadLocal<HttpClient>` kullanılmalı

### SuiteContext Kullanım Kılavuzu

```java
// ✅ DOĞRU — Test 1 yazar, Test 2 okur (sıralı dependency)
// Test 1
suiteCtx().set("orderId", res.json("$.id"));
// Test 2 (Test 1'den sonra çalışır)
String orderId = suiteCtx().get("orderId");

// ❌ YANLIŞ — İki paralel test aynı key'e yazıyor
// Thread A: suiteCtx().set("token", tokenA);
// Thread B: suiteCtx().set("token", tokenB);  → Thread A'nın token'ı kaybolur

// ✅ DOĞRU — Thread-specific key kullan
suiteCtx().set("token_" + Thread.currentThread().getId(), token);
// veya ScenarioContext kullan (zaten thread-local)
ctx().set("token", token);
```

---

## 7. Implementasyon Sırası ve Tahmini Efor

| # | Öncelik | Madde | Efor | Risk | Bağımlılık |
|---|:-------:|-------|:----:|:----:|------------|
| 1 | **P0** | HTTP-level retry | 1-2 saat | Düşük | Config model güncellemesi |
| 2 | **P1** | Per-request timeout | 30 dk | Düşük | — |
| 3 | **P1** | Query param builder | 45 dk | Düşük | — |
| 4 | **P1** | Response time assertion | 20 dk | Düşük | — |
| 5 | **P2** | HttpClient thread-safety | 2 saat | Orta | Config model güncellemesi |
| 6 | **P2** | OAuth2 cache race fix | 30 dk | Düşük | — |
| 7 | **P2** | Multipart / file upload | 2-3 saat | Orta | — |
| 8 | **P2** | Cookie / session mgmt | 2 saat | Orta | — |
| 9 | **P2** | Request/response interceptor | 1 saat | Düşük | — |
| 10 | **P3** | Header assertion | 15 dk | Düşük | — |
| 11 | **P3** | Body regex assertion | 10 dk | Düşük | — |
| 12 | **P3** | JSON array/null assertion | 20 dk | Düşük | — |
| 13 | **P3** | Truncation limit config | 15 dk | Düşük | Config model güncellemesi |

**Toplam tahmini efor:** ~12-15 saat

**Önerilen sprint planı:**
- **Sprint 1:** #1 (retry) + #2-4 (quick wins) → ~3 saat
- **Sprint 2:** #5-6 (thread-safety) + #9 (interceptor) → ~3.5 saat
- **Sprint 3:** #7-8 (multipart + cookies) → ~4-5 saat
- **Sprint 4:** #10-13 (P3 assertions) → ~1 saat

---

## 8. Test Stratejisi

### Unit Test Yaklaşımı

Her yeni özellik için `src/test/java/io/testfly/unit/` altında test yazılmalı:

```java
// ApiClientRetryTest.java
@Test
public void retry_onTransientFailure_succeedsOnSecondAttempt() {
    // Mock HTTP to fail first, succeed second
    // Assert: response status 200, retry log present
}

@Test
public void retry_exhausted_throwsLastException() {
    // Mock HTTP to always fail
    // Assert: RuntimeException thrown after maxAttempts
}
```

### Integration Test Yaklaşımı

`src/test/java/io/testfly/integration/` altında gerçek backend ile:

```java
// ApiIntegrationTest.java (real-backends profile)
@Test
public void parallelApiTests_threadIsolation() {
    // 8 thread paralel API çağrısı
    // Assert: tüm response'lar doğru, karışma yok
}
```

### Mock Stratejisi

- `HttpClient` mock: JDK `HttpClient` final class olduğu için Mockito `mockStatic` veya WireMock kullanılmalı
- `TestFlyContext` mock: Config injection ile test-specific config sağlanmalı

---

## Notlar

- Tüm değişiklikler backward-compatible olmalı — mevcut `BaseApiTest` kullanan projeler kırılmamalı
- Yeni config alanları optional olmalı — default değerler mevcut davranışı korumalı
- `@TestFlyApi(since = "x.y.z")` annotation'ı yeni public API'lere eklenmeli
- Optional dependency'ler (`json-schema-validator` gibi) `<optional>true</optional>` kalmalı
