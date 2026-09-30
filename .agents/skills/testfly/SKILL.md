---
name: testfly
description: >
  TestFly framework'ünün SDET otomasyon mimarisi, kaynak kodu standardları ve test yazım kuralları.
  WebUI testi (POM, Locator API, WaitEngine, Assertions), API testi (ApiClient, ApiResponse, AuthStrategy),
  Self-Healing, SPI eklentileri, raporlama, konfigürasyon ve yaşam döngüsü kurallarını kapsar.
  Yeni test senaryosu, sayfa nesnesi, API testi veya framework özelliği geliştirirken bu kuralları uygula.
---

# TestFly — SDET Otomasyon Mimarisi ve Standartları

> **Kapsam:** Bu doküman, TestFly framework'ü üzerinde kod yazan, test geliştiren ve mimari kararlar
> alan tüm AI ajanları ve geliştiriciler için bağlayıcı kurallar ve referans sözleşmeleridir.

---

## 1. Mimari İlkeler (Architectural Invariants)

Bu kurallar **istisnasız** uygulanır. İhlal eden kod review'dan geçemez.

### 1.1 Thread-Local İzolasyon
- WebDriver, API durumu, bağlam (context) ve cookie jar **her zaman ThreadLocal** olarak yönetilir.
- Statik global `WebDriver` değişkeni oluşturmak **kesinlikle yasaktır**.
- Driver'a erişim yalnızca `DriverManager.getDriver()` veya `BasePage.getDriver()` üzerinden yapılır.

### 1.2 Konfigürasyondan Önce Sözleşme (Convention over Configuration)
- Framework davranışları `testfly.yml` dosyası ile yönetilir.
- Ortam değişkenleri `${VAR}` veya `${VAR:-default}` söz dizimiyle (syntax) referans edilir.
- Profil desteği: `-Dtestfly.profile=staging` → `testfly-staging.yml` yüklenir.

### 1.3 Bekleme Politikası
- **`Thread.sleep()` kesinlikle yasaktır** — tek istisna framework-internal polling mekanizmalarıdır.
- Tüm UI beklemeleri `WaitEngine` üzerinden yönetilir:
  - `WaitEngine.waitForVisible(By)`
  - `WaitEngine.waitForClickable(By)`
  - `WaitEngine.waitForInvisible(By)`
- API polling'leri `ApiClient.pollUntil(Predicate, Duration)` ile yapılır.

### 1.4 Geriye Dönük Uyumluluk
- `@TestFlyApi(since = "x.y.z")` anotasyonu taşıyan sınıf/metot/arayüz kontratları
  **aynı major versiyon** içinde asla değiştirilemez (isim, parametre, dönüş tipi).
- Mevcut `@TestFlyApi` arayüzlerine eklenen yeni metotlar **`default` implementasyon** içermelidir.
- Kaldırma en az bir minor versiyon `@Deprecated` olarak bekledikten sonra, yalnızca
  sonraki major versiyonda yapılabilir.

---

## 2. WebUI Test Mimarisi

### 2.1 Katman Ayrımı (Layer Separation)

```
┌─────────────────────────────────────────┐
│  Test Sınıfları (extends BaseTest)      │ → Senaryo mantığı + Doğrulamalar
├─────────────────────────────────────────┤
│  Sayfa Nesneleri (extends BasePage)     │ → DOM etkileşimleri + Sayfa eylemleri
├─────────────────────────────────────────┤
│  Locator API (Locator / SmartLocator)   │ → Element keşfi + Akıllı bekleme
├─────────────────────────────────────────┤
│  WaitEngine                             │ → Merkezi açık bekleme (explicit wait)
├─────────────────────────────────────────┤
│  DriverManager (ThreadLocal)            │ → WebDriver yaşam döngüsü
└─────────────────────────────────────────┘
```

### 2.2 BaseTest — Test Sınıfı Kuralları

`BaseTest`, 16 modüler `*Support` arayüzü ile yeteneklerini **mixin/trait** deseni üzerinden
kazanır. Monolitik bir üst sınıf değildir.

```java
public abstract class BaseTest implements
    LocatorSupport,       // find(), $(), getByRole(), getByText(), getByLabel()...
    AssertionSupport,     // assertThat(By), assertThat(Locator), assertThatPage()
    ActionSupport,        // Tıklama, yazma, hover, scroll vb.
    SessionSupport,       // Çoklu oturum: session("admin"), withSession(...)
    SoftAssertSupport,    // softAssert() — birikimli doğrulama
    ApiSupport,           // apiClient() — API çağrıları
    NavigationSupport,    // open(), open(url), getDriver(), getWait()
    BrowserSupport,       // localStorage(), cookies(), networkMock(), clipboard()
    VisualSupport,        // assertScreenshot() — piksel regresyon
    DbSupport,            // db(), db("datasource") — veritabanı işlemleri
    EmailSupport,         // mailbox(), to("email") — e-posta doğrulama
    AccessibilitySupport, // accessibility() — axe-core WCAG denetimi
    PerformanceSupport,   // collectPerformance() — Core Web Vitals
    ClockSupport,         // clock() — tarayıcı saat taklit (mock)
    TestDataSupport,      // getTestData() — CSV/Excel/DB test verisi
    ContextSupport        // ctx(), suiteCtx() — test/suite bağlamı
    { }
```

**Kurallar:**
- Test sınıflarında doğrudan `new ChromeDriver()` veya `driver.quit()` **çağrılamaz**.
- Doğrulamalar (assertions) yalnızca test sınıflarında yapılır, sayfa nesnelerinde **asla**.
- `@NoBrowser` anotasyonu ile sınıf veya metot bazında tarayıcı başlatma atlanabilir.

### 2.3 BasePage — Sayfa Nesnesi Kuralları

```java
public abstract class BasePage implements
    LocatorSupport, AssertionSupport, ActionSupport,
    StepSupport, SoftAssertSupport, BrowserSupport,
    VisualSupport, NavigationSupport, ContextSupport { }
```

**Kurallar:**
- Her sayfa/bileşen `BasePage`'den türetilmelidir.
- Sayfa nesneleri içinde **test verisi veya assertion bulundurulmaz**.
- Element tanımları TestFly `Locator` API veya `By` sabitleri ile yapılır.
- Shadow DOM desteği yerleşiktir: `shadowFind()`, `shadowClick()`, `shadowPierce()`.
- iFrame işlemleri `withinFrame(By, Runnable)` ile güvenli bağlam yönetimi sağlar.

### 2.4 Locator Stratejisi (Erişilebilirlik Öncelikli)

**Seçim Öncelik Sırası (en güvenilirden en kırılgana):**

| Öncelik | Yöntem | Örnek |
|---------|--------|-------|
| 1 | `getByRole()` | `getByRole(Role.BUTTON).withName("Gönder")` |
| 2 | `getByTestId()` | `getByTestId("submit-btn")` |
| 3 | `getByLabel()` | `getByLabel("E-posta adresi")` |
| 4 | `getByText()` | `getByText("Devam Et")` |
| 5 | `getByPlaceholder()` | `getByPlaceholder("Ara...")` |
| 6 | `find(By.cssSelector())` | `find(By.cssSelector(".btn-primary"))` |
| 7 | XPath | **Son çare** — bakımı zor, kırılgan |

**Yasak:** İç içe (nested) XPath ile DOM konumuna bağımlı kırılgan locator'lar yazılmaz.

### 2.5 Self-Healing Locator Mimarisi (4 Katman)

```
Tier 1: HealingCache     → Kalıcı önbellek (.testfly/healed-locators.json), O(1) çözümleme
Tier 2: SelfHealingLocator → Statik yedek stratejiler (id, name, text, class, data-testid)
Tier 3: FuzzyHealingEngine → Levenshtein mesafe, sıfır token, yerel heuristik eşleme
Tier 4: AiHealingEngine   → LLM tabanlı son çare (locators.aiHealing: true gerektirir)
```

### 2.6 Web-First Assertions (DOM-Polling)

```java
// ✅ Doğru — DOM'u otomatik olarak yoklar (polls), zamanlama sorunu yok
assertThat(submitButton).isVisible();
assertThat(errorMessage).hasText("Geçersiz giriş");
assertThatPage().hasTitle("Dashboard");

// ❌ Yanlış — Anlık kontrol, flaky testlere yol açar
assertTrue(driver.findElement(By.id("msg")).isDisplayed());
```

- `LocatorAssert`: Element düzeyinde doğrulamalar (`isVisible`, `hasText`, `hasAttribute`, `isEnabled`...)
- `PageAssert`: Sayfa düzeyinde doğrulamalar (`hasTitle`, `hasUrl`, `hasUrlContaining`...)
- `softAssert()`: Birden fazla doğrulamayı biriktirir, test sonunda toplu rapor eder.

---

## 3. API Test Mimarisi

### 3.1 Sıfır Tarayıcı Ek Yükü

```java
public abstract class BaseApiTest implements
    SoftAssertSupport, TestDataSupport, ApiSupport,
    ContextSupport, StepSupport, DbSupport, EmailSupport { }
```

`BaseApiTest`'ten türeyen sınıflarda WebDriver **asla başlatılmaz**. Framework, listener
düzeyinde (`TestExecutionListener.skipBrowser()`) bunu otomatik olarak devre dışı bırakır.

### 3.2 ApiClient — Akıcı HTTP İstemcisi

**Fabrika Metotları (Static Factory):**
```java
ApiClient.get("/users/{id}")          // GET
ApiClient.post("/users")              // POST
ApiClient.to("https://other.api.com") // Farklı baseUrl
ApiClient.toService("payment")        // testfly.yml → api.baseUrls.payment
```

**Akıcı Zincir (Fluent Chain):**
```java
apiClient().post("/users")
    .pathParam("id", 42)
    .header("X-Request-Id", uuid)
    .queryParam("include", "profile")
    .body(new CreateUserRequest("John", "john@mail.com"))
    .auth(ApiAuth.bearer(token))
    .timeout(30)
    .send();
```

**Desteklenen İstek Tipleri:**
- JSON body (Jackson serileştirme), raw string, form-urlencoded, multipart dosya yükleme
- Path parametreleri (`{name}` → otomatik URL-encode, çözümlenmemiş placeholder'da fail-fast)
- Query parametreleri (otomatik URL-encode)
- Cookie Jar (thread-local, `withCookies()` ile aktif)

**İleri Düzey Özellikler:**
- **Polling:** `pollUntil(r -> "DONE".equals(r.json("$.status")), Duration.ofSeconds(30))`
- **Retry:** `testfly.yml` → `api.retry.enabled`, `maxAttempts`, `backoffMs`, `retryOnStatus`
- **Interceptor'lar:** `addRequestInterceptor()` / `addResponseInterceptor()` — global hook'lar
- **Global Spec:** `setGlobalSpec(ApiRequestSpec)` — tüm isteklere uygulanır

### 3.3 ApiResponse — Değişmez Yanıt Nesnesi

```java
ApiResponse res = apiClient().get("/users/1").send();

// Durum ve süre
res.status();                    // int
res.durationMs();                // long

// Veri çıkarma
res.body();                      // Ham JSON string
res.json("$.user.name");         // JsonPath string
res.json("$.age", Integer.class); // Tipli JsonPath
res.jsonList("$.items", Item.class); // Liste
res.asObject(User.class);        // Tam POJO deserializasyon
res.asList(User.class);          // Liste deserializasyon
res.header("Content-Type");      // Yanıt header'ı
```

**Akıcı Doğrulamalar (Fluent Assertions):**
```java
res.assertStatus(200)
   .assertJson("$.name", "John")
   .assertSchema("schemas/user.json")      // JSON Schema Draft 7
   .assertDurationLessThan(500)             // SLA (ms)
   .assertHeader("Content-Type", "application/json")
   .assertBodyContains("success");
```

### 3.4 Kimlik Doğrulama (Authentication Strategy)

**Programatik Kullanım:**
```java
ApiAuth.bearer("token123")                    // Bearer Token
ApiAuth.bearer(() -> tokenService.refresh())  // Dinamik/yenilenebilir token
ApiAuth.basic("user", "pass")                 // Basic Auth
ApiAuth.apiKey("X-Api-Key", key)              // Header API Key
ApiAuth.apiKeyParam("api_key", key)           // Query Param API Key
ApiAuth.oauth2(tokenUrl, clientId, secret, scope) // OAuth2 Client Credentials
ApiAuth.named("admin")                        // testfly.yml → api.auth.admin
ApiAuth.none()                                // Açıkça kimlik doğrulama yok
```

**Anotasyon Tabanlı:**
```java
@UseAuth("admin")                 // Sınıf düzeyinde
public class AdminApiTest extends BaseApiTest {

    @Test
    @UseAuth("readonly")          // Metot düzeyinde override
    public void denyWrite() {
        apiClient().post("/settings").send().assertStatus(403);
    }
}
```

**Kural:** Token, şifre veya API key **asla** test koduna sabit (hardcoded) olarak gömülmez.
Framework'ün `ApiAuth` stratejileri veya `testfly.yml` referansları kullanılır.

### 3.5 Yeniden Kullanılabilir Spesifikasyonlar (Reusable Specs)

```java
// İstek şablonu
ApiRequestSpec adminSpec = ApiRequestSpec.builder()
    .baseUrl("https://admin-api.example.com")
    .header("X-Tenant", "acme")
    .auth(ApiAuth.bearer(token))
    .build();

apiClient().spec(adminSpec).get("/users").send();

// Yanıt doğrulama şablonu
ApiResponseSpec successSpec = ApiResponseSpec.builder()
    .expectStatus(200)
    .expectContentType("application/json")
    .expectJsonPathExists("$.data")
    .expectResponseTimeLessThan(2000)
    .build();

res.assertSpec(successSpec);
```

---

## 4. Load Test Mimarisi (Performans Testleri)

### 4.1 BaseLoadTest ve Sıfır Tarayıcı Koruması

```java
public abstract class BaseLoadTest implements
    LoadTestSupport, ApiSupport, ContextSupport,
    SoftAssertSupport, StepSupport, TestDataSupport { }
```

Load (yük) testlerinde WebDriver **kesinlikle** başlatılmaz. `DriverManager`,
`isLoadTestActive()` kontrolü yaparak `BaseLoadTest`'ten türeyen veya `@LoadTest` anotasyonuna
sahip sınıflarda tarayıcı açılmasını donanımsal olarak (hard block) engeller.

### 4.2 Üç Katmanlı Konfigürasyon Hiyerarşisi

Yük testi parametreleri (`users`, `rampUp`, `hold`, vb.) 3 katmanlı bir ezme (override) mantığıyla çalışır:

1. **Framework Geneli:** `testfly.yml` → `loadtest:` bloğu
2. **Sınıf/Metot Düzeyi:** `@LoadTest(users = 500, rampUp = "30s", engine = "gatling")` anotasyonu
3. **Senaryo Düzeyi:** Akıcı (fluent) API zincirindeki `.users(1000)` metotları

### 4.3 LoadScenario — Akıcı Senaryo DSL'i (Domain Specific Language)

**Hızlı Tek Adımlı Test:**
```java
@Test
@LoadTest(users = 200, hold = "1m")
public void searchUnderLoad() {
    load("/api/products?search=laptop")   // LoadTestSupport.load()
        .assertP95Below(300)              // 95. yüzdelik (percentile) gecikme < 300ms
        .assertErrorRateBelow(0.02);      // Hata oranı < %2
}
```

**Çok Adımlı Karmaşık Senaryolar (User Journey):**
```java
@Test
public void checkoutFlow() {
    loadScenario("Checkout Flow")
        .users(300)
        .feedCsv("users.csv")             // Veri besleme (Data Driven)
        .step("Login")
            .post("/api/auth/login")
            .body(Map.of("user", "${username}")) // CSV'den ${username} gelir
            .extract("token", "$.accessToken")   // Korelasyon: Yanıttan token al
        .step("Order")
            .post("/api/orders")
            .header("Authorization", "Bearer ${token}") // Token'ı sonraki istekte kullan
        .thinkTime(500, 2000)             // 500ms - 2000ms arası rastgele bekleme (think time)
        .run()                            // Simülasyonu başlat (LoadTestRunner)
        .assertP95Below(500)
        .assertThroughputAbove(100);      // Saniyede 100+ istek
}
```

### 4.4 Engine Seçimi (LoadTestRunner)

Yük testi altyapısı (engine) tak-çalıştır mimarisindedir:
- **Gatling:** Kurumsal ölçekli yük testleri için Gatling köprüsü (bridge) kullanılır (`engine = "gatling"`).
- **JDK Fallback:** Küçük ölçekli veya CI ortamında Gatling kurulu değilse, framework'ün yerleşik (native) Java thread havuzunu kullanan `jdk` motoru otomatik devreye girer.

---

## 5. Raporlama ve İzlenebilirlik

### 5.1 StepLogger
- Her anlamlı eylem `step("Açıklama")` ile HTML rapora kaydedilir.
- `BasePage` metotları (click, type, hover vb.) otomatik olarak step üretir.
- API istekleri cURL komutu dahil otomatik loglama yapar.

### 5.2 Screenshot ve Video
- Test başarısızlığında otomatik ekran görüntüsü (`ScreenshotManager`).
- Video/GIF kaydı (`RecordingManager`) — `recording.recordAll: true` ile her test için.
- Base64 olarak HTML rapora gömülür (ek dosya yönetimi gereksiz).

### 5.3 Report Adapter SPI
```java
public interface ReportAdapter {
    String getName();
    void generate(File metricsJson);
}
```
- Yerleşik: HTML, JUnit XML, Allure, Slack, Teams, ReportPortal.
- Özel: `META-INF/services/io.testfly.reporting.ReportAdapter` veya `ReportAdapterRegistry.register()`.

---

## 6. Framework Yaşam Döngüsü

### 6.1 Bootstrap Sırası (FrameworkBootstrap.initialize)

```
1. DotEnvLoader.load()              → .env dosyasından ortam değişkenleri
2. HealingCache.load()              → Kalıcı iyileştirilmiş locator'lar
3. ConfigurationLoader.load()       → testfly.yml ayrıştırma + env placeholder çözümleme
4. TestFlyDefaults.applyMissing()   → Eksik konfigürasyon varsayılanları
5. CI Auto-Override                 → CI'da headless=true, threadCount=CPU çekirdekleri
6. ExecutionValidator.validate()    → Mod, URL ve timeout doğrulama
7. TestFlyContext.initialize()      → Global bağlam başlatma
8. SPI Discovery                    → DriverProvider, Hook, ReportAdapter, Plugin yükleme
```

### 6.2 Test Yaşam Döngüsü (TestExecutionListener)

```
onTestStart:
  ├── Quarantine kontrolü → Karantina listesindeyse SkipException
  ├── @DependsOnApi kontrolü → API bağımlılığı erişilemezse atla
  ├── UI test ise → DriverManager.createDriver() + RecordingManager.start()
  ├── @UseAuth → ApiClient'a auth enjekte et
  ├── @PreCondition → Ön koşul metotlarını çalıştır
  ├── @TestData → Test verisini yükle (CSV/Excel/DB)
  └── HookRegistry.onTestStart()

onTestSuccess:
  ├── Console hata kontrolü (failOnConsoleErrors)
  ├── Soft assertion flush (birikmiş hatalar varsa → fail)
  ├── Performance metrikleri kaydet
  └── Cleanup (driver, context, cookies, auth, DB, NetworkMock)

onTestFailure:
  ├── SmartTriageEngine → SYSTEM_FLAKY mı APPLICATION_BUG mu?
  ├── Screenshot + AI kök neden analizi
  ├── Video kaydetme (failure recording)
  └── Cleanup (aynı)
```

---

## 7. SPI Eklenti Noktaları

| Eklenti | Kayıt Yöntemi |
|---------|---------------|
| Driver Provider | `META-INF/services/io.testfly.driver.NamedDriverProvider` veya `DriverProviderRegistry.register()` |
| Report Adapter | `META-INF/services/io.testfly.reporting.ReportAdapter` veya `ReportAdapterRegistry.register()` |
| Lifecycle Hook | `META-INF/services/io.testfly.hooks.ExecutionHook` veya `HookRegistry.register()` |
| Framework Plugin | `META-INF/services/io.testfly.extension.TestFlyPlugin` veya `PluginRegistry.register()` |

```java
// ExecutionHook arayüzü (tüm metotlar default no-op)
public interface ExecutionHook {
    default void onSuiteStart() {}
    default void onSuiteEnd() {}
    default void onTestStart(String testId) {}
    default void onTestEnd(String testId, String status) {}
    default void onTestFailure(String testId, Throwable cause) {}
}

// TestFlyPlugin arayüzü
public interface TestFlyPlugin {
    String getName();
    default String minFrameworkVersion() { return "0.0.0"; }
    default void onLoad(TestFlyConfig config) {}
    default void onUnload() {}
}
```

---

## 8. Birim Test Standartları (src/test/java/io/testfly/unit/)

- **Test Framework:** TestNG + Mockito 5.11.
- **Gerçek tarayıcı açılmaz** — tüm WebDriver etkileşimleri `mock()` ve `mockStatic()` ile.
- Tek test çalıştırma: `mvn test -Dtest=WaitEngineTest`
- Tam suite: `mvn test` (tüm unit testler)
- Entegrasyon testleri: `mvn verify -Preal-backends` (gerçek backend/cloud gerektirir)

---

## 9. Tasarım Desenleri Referansı

| Desen | Uygulama Yeri |
|-------|---------------|
| **Fluent Builder** | `ApiClient`, `ApiRequestSpec`, `ApiResponseSpec`, `Locator` |
| **Strategy** | `ApiAuth` (Bearer, Basic, OAuth2, Custom), `NamedDriverProvider` |
| **Immutable Value Object** | `ApiResponse`, `ApiRequestSpec`, `Locator` |
| **Mixin / Trait** | `BaseTest` ve `BasePage` — 16 `*Support` arayüzü |
| **Template Method** | `SuiteExecutionListener`, `TestExecutionListener` yaşam döngüsü |
| **ThreadLocal Isolation** | `DriverManager`, `ApiClient` (cookie jar, global auth) |
| **Chain of Responsibility** | `SmartLocator` (fallback), Self-Healing (4 tier) |
| **Observer** | `ExecutionHook`, `ReportAdapter`, interceptor'lar |
| **Service Provider Interface** | Driver, Hook, Report, Plugin kayıt/keşif |
| **Specification** | `ApiRequestSpec`, `ApiResponseSpec` — yeniden kullanılabilir test kontratları |
| **Retry with Backoff** | `ApiClient.send()` — konfigüre edilebilir yeniden deneme |
| **Polling** | `ApiClient.pollUntil()` — koşullu tekrarlı istek |

---

## 10. Hızlı Komut Referansı

```bash
mvn test                              # Tüm unit testler (tarayıcısız)
mvn test -Dtest=ApiClientTest         # Tek test sınıfı
mvn clean verify                      # Tam build + test + paket
mvn clean install -DskipTests         # Yerel kurulum (consumer test için)
mvn verify -Preal-backends            # Entegrasyon testleri
mvn clean verify -Pquality            # Kalite kapıları (JaCoCo, SpotBugs, Checkstyle, PMD)
```
