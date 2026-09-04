---
description: "TestFly'ı JUnit 5 ile çalıştırın: BaseJUnit5Test, @EnableTestFly veya @ExtendWith(TestFlyExtension) ile UI, API, veritabanı ve erişilebilirlik dahil TestNG ile %100 özellik eşitliği."
id: junit5
title: JUnit 5 Desteği
sidebar_position: 10
---

# JUnit 5 Desteği

TestFly, hem **TestNG** (yerleşik) hem de **JUnit 5** (tercihe bağlı) test çatılarını birinci sınıf vatandaş olarak destekler. JUnit 5 entegrasyonu yalnızca basit bir çalıştırıcı (runner) sunmakla kalmaz; TestNG `BaseTest` ile **%100 özellik eşitliği** sağlar: framework tarafından yönetilen WebDriver yaşam döngüsü, ThreadLocal sürücü izolasyonu, akıcı locator'lar, web-öncelikli ve soft assertion'lar, yerleşik REST API testi, çoklu kullanıcı oturumları (multi-session), HTML zaman çizelgesi raporlaması, AI hata analizi ve flakiness takibi.

---

## Kurulum

### Maven

Projenizin `pom.xml` dosyasına TestFly'ın yanına JUnit 5 bağımlılıklarını ekleyin:

```xml title="pom.xml"
<dependencies>
    <!-- TestFly Çekirdeği -->
    <dependency>
        <groupId>io.testfly</groupId>
        <artifactId>testfly</artifactId>
        <version>1.0.0</version>
    </dependency>

    <!-- JUnit 5 Jupiter ve Platform Launcher -->
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>5.10.2</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.junit.platform</groupId>
        <artifactId>junit-platform-launcher</artifactId>
        <version>1.10.2</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

Maven Surefire 3.x, ek bir eklenti yapılandırmasına ihtiyaç duymadan JUnit 5'i otomatik algılar.

### Gradle

```groovy title="build.gradle"
dependencies {
    testImplementation 'io.testfly:testfly:1.0.0'
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher:1.10.2'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

---

## Entegrasyon Seçenekleri

TestFly, mimarinize uyum sağlayacak 3 farklı JUnit 5 kullanım modeli sunar.

### Seçenek A — `BaseJUnit5Test` Sınıfını Genişletme (Önerilen)

En kolay ve en zengin yaklaşımdır. TestNG'deki `BaseTest` ile birebir aynı kolaylık metotlarını sunar:

```java
import io.testfly.junit5.BaseJUnit5Test;
import io.testfly.locator.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

class LoginTest extends BaseJUnit5Test {

    @Test
    @DisplayName("Kullanıcı geçerli bilgilerle giriş yapabilir")
    void validLogin() {
        open("/login");

        step("Kullanıcı bilgileri girilir");
        getByLabel("Kullanıcı Adı").type("admin");
        getByLabel("Şifre").type("secret");
        getByRole(Role.BUTTON, "Giriş Yap").click();

        step("Dashboard ekran görüntüsüyle doğrulanır", true);
        assertThat(By.id("dashboard")).isVisible();
    }
}
```

#### `BaseJUnit5Test`'in Hazır Olarak Sağladığı Yetenekler:

| Kategori | Sunulan Metotlar ve Yetenekler |
|---|---|
| **Gezinme (Navigation)** | `open()`, `open(path)`, `getDriver()`, `getWait()` |
| **Anlamsal (Semantic) Locator'lar** | `getByRole(Role, name)`, `getByText()`, `getByLabel()`, `getByPlaceholder()`, `getByTestId()`, `getByAltText()`, `getByTitle()` |
| **Akıcı (Fluent) Locator'lar** | `find(css)`, `find(By)`, `$(css)`, `$$(css)` |
| **Web-Öncelikli Doğrulamalar** | `assertThat(By).isVisible()`, `assertThat(Locator).hasText(...)`, `assertThat(...).count(n)` |
| **Soft Doğrulamalar (SoftAssert)** | `softAssert(By).isVisible()`, `softAssert(By).hasText(...)`, `softAssert().that(...)` |
| **Yerleşik REST API Testi** | `apiClient()`, `apiGet(path)`, `apiPost(path, body)`, `apiPut()`, `apiPatch()`, `apiDelete()` |
| **Çoklu Oturum (Multi-Session)** | `session(name)`, `withSession(name, runnable)` ile çoklu kullanıcı / chat / pazar yeri akışları |
| **Veritabanı Doğrulama** | `db()`, `db("datasourceName")` ile SQL sorguları ve veri kontrolleri |
| **E-Posta Doğrulama** | `mailbox()`, `to("user@example.com")` ile gelen kutusundan OTP, link ve içerik kontrolleri |
| **Erişilebilirlik (a11y)**| `accessibility().scan()`, `assertAccessibility()` ile axe-core taramaları |
| **Adım Kaydı (Step Logging)** | `step(name)`, `step(name, takeScreenshot)` ile HTML raporunda zaman çizelgesi |

---

### Seçenek B — Kendi Taban Sınıfınızda `@EnableTestFly` Kullanımı

Projenizde halihazırda var olan bir sınıf hiyerarşisi varsa, taban sınıfınıza `@EnableTestFly` eklemeniz yeterlidir:

```java
import io.testfly.driver.DriverManager;
import io.testfly.junit5.EnableTestFly;
import org.openqa.selenium.WebDriver;

@EnableTestFly
public abstract class CustomAppTest {

    protected WebDriver getDriver() {
        return DriverManager.getDriver();
    }
}
```

`@EnableTestFly`, arka planda `TestFlyExtension` eklentisini otomatik olarak kaydeder.

---

### Seçenek C — `@ExtendWith(TestFlyExtension.class)` ve Parametre Enjeksiyonu

Hiçbir sınıftan kalıtım almadan (POJO), doğrudan test metoduna `WebDriver` enjekte etmek istediğinizde:

```java
import io.testfly.junit5.TestFlyExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

@ExtendWith(TestFlyExtension.class)
class DirectInjectionTest {

    @Test
    void loginWithInjectedDriver(WebDriver driver) {
        driver.get("https://example.com/login");
        driver.findElement(By.id("username")).sendKeys("admin");
        driver.findElement(By.cssSelector("button[type='submit']")).click();
    }
}
```

`TestFlyExtension`, ilgili thread için sürücüyü otomatik başlatır, metoda geçirir ve test bittiğinde temizler.

---

## Tarayıcısız Testler: `@NoBrowser` Desteği

Bir JUnit 5 test sınıfı veya metodu yalnızca REST API, veritabanı veya e-posta servislerini test ediyorsa, `@NoBrowser` ekleyebilirsiniz. TestFly gereksiz tarayıcı başlatma sürecini atlayarak testin saniyeler içinde tamamlanmasını sağlar:

```java
import io.testfly.client.ApiResponse;
import io.testfly.junit5.BaseJUnit5Test;
import io.testfly.test.NoBrowser;
import org.junit.jupiter.api.Test;

class UserApiIntegrationTest extends BaseJUnit5Test {

    @Test
    @NoBrowser  // Tarayıcı açılmaz; doğrudan HTTP üzerinden koşar
    void verifyUserCreationViaApi() {
        ApiResponse response = apiPost("/api/users", "{\"name\":\"John Doe\",\"email\":\"john@example.com\"}");
        
        response.assertThat()
                .statusCode(201)
                .bodyContains("John Doe");

        // Veritabanından kaydı doğrula
        db().table("users")
            .where("email", "john@example.com")
            .assertExists();
    }
}
```

`@NoBrowser` anotasyonunu sınıf düzeyinde tanımlayarak tüm metotları tarayıcısız hale de getirebilirsiniz.

---

## Hibrit API ve UI Testi Örneği

`BaseJUnit5Test`, `ApiSupport` arayüzünü içerdiğinden, yavaş form doldurma adımları yerine arka planda API ile veri hazırlayıp doğrudan UI ekranına geçebilirsiniz:

```java
import io.testfly.junit5.BaseJUnit5Test;
import io.testfly.locator.Role;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

class OrderHistoryTest extends BaseJUnit5Test {

    @Test
    void userCanViewCreatedOrder() {
        // 1. Sipariş verisini REST API ile anında oluşturun
        step("Sipariş verisi API ile oluşturulur");
        String orderId = apiPost("/api/orders", "{\"item\":\"Widget\",\"qty\":2}")
                .jsonPath().getString("id");

        // 2. Sipariş geçmişi sayfasına doğrudan gidin
        step("Sipariş detay sayfası tarayıcıda açılır");
        open("/orders/" + orderId);

        // 3. Anlamsal erişilebilirlik locator'ları ve soft assertion ile doğrulayın
        softAssert(getByRole(Role.HEADING, "Sipariş Detayı")).isVisible();
        softAssert(By.id("order-id")).hasText(orderId);
        softAssert(By.className("order-status")).hasText("CONFIRMED");
    }
}
```

---

## Çoklu Kullanıcı / Multi-Session Senaryoları

Canlı sohbet (chat), ortak doküman düzenleme veya pazar yeri (alıcı ve satıcı) akışlarını test etmek için `session()` veya `withSession()` kullanabilirsiniz:

```java
import io.testfly.junit5.BaseJUnit5Test;
import io.testfly.locator.Role;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

class MarketplaceChatTest extends BaseJUnit5Test {

    @Test
    void buyerAndSellerCanChat() {
        // Oturum 1: Alıcı mesaj gönderir
        session("buyer");
        open("/chat/101");
        getByPlaceholder("Mesajınızı yazın...").type("Ürün hala satılık mı?");
        getByRole(Role.BUTTON, "Gönder").click();

        // Oturum 2: Satıcı izole bir pencerede mesajı görüntüler
        session("seller");
        open("/chat/101");
        assertThat(By.cssSelector(".message.received"))
                .hasText("Ürün hala satılık mı?");

        getByPlaceholder("Mesajınızı yazın...").type("Evet, hemen kargolayabilirim!");
        getByRole(Role.BUTTON, "Gönder").click();

        // Alıcı oturumuna dön ve gelen yanıtı doğrula
        session("buyer");
        assertThat(By.cssSelector(".message.incoming"))
                .hasText("Evet, hemen kargolayabilirim!");
    }
}
```

Her oturum aynı thread üzerinde tamamen izole çerezler, oturum depolama alanı (localStorage) ve tarayıcı profiliyle çalışır. Test bittiğinde tüm açık oturumlar güvenli şekilde kapatılır.

---

## `@PreCondition` ile Oturum Önbellekleme

Ağır giriş (login) adımlarını her testte tekrarlamak yerine `@PreCondition` ile oturumu (çerezler + localStorage) önbelleğe alıp sonraki testlerde saniyeler içinde geri yükleyebilirsiniz:

```java
import io.testfly.junit5.BaseJUnit5Test;
import io.testfly.precondition.PreCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

class DashboardTest extends BaseJUnit5Test {

    @Test
    @PreCondition("loginAsAdmin")
    @DisplayName("Dashboard görüntüleme — önbelleğe alınmış oturum geri yüklenir")
    void viewDashboard() {
        open("/dashboard");
        assertThat(By.id("welcome-header")).isVisible();
    }

    @Test
    @PreCondition("loginAsAdmin")
    @DisplayName("Profil düzenleme — aynı oturum tekrar kullanılır")
    void editProfile() {
        open("/profile");
        assertThat(By.id("profile-form")).isVisible();
    }
}
```

### Koşul Sağlayıcının (Condition Provider) Tanımlanması

`BaseConditions` sınıfını uygulayın ve Java SPI üzerinden kaydedin:

```java
import io.testfly.precondition.BaseConditions;
import io.testfly.precondition.ConditionProvider;

public class AppConditions extends BaseConditions {

    @ConditionProvider("loginAsAdmin")
    public void loginAsAdmin() {
        open("/login");
        find("#username").type("admin");
        find("#password").type("secret");
        find("#login-btn").click();
    }
}
```

SPI kayıt dosyası:

```text title="src/test/resources/META-INF/services/io.testfly.precondition.BaseConditions"
com.yourcompany.conditions.AppConditions
```

> **Yeniden Denemede Otomatik Temizlik:** Bir `@PreCondition` testi hata alıp yeniden denendiğinde (retry), TestFly önbellekteki oturumu otomatik olarak temizler ve sağlayıcının sıfırdan taze çalışmasını sağlar.

---

## `@Retryable` ile Akıllı Yeniden Deneme

Flaky (kararsız) testleri otomatik olarak yeniden denemek için sınıf veya metot düzeyinde `@Retryable` kullanabilirsiniz. Her deneme **yepyeni ve temiz bir WebDriver örneğiyle** başlatılır:

```java
import io.testfly.junit5.BaseJUnit5Test;
import io.testfly.listeners.Retryable;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

class PaymentTest extends BaseJUnit5Test {

    @Test
    @Retryable(maxAttempts = 2) // Hata durumunda en fazla 2 kez yeniden dener (toplam 3 deneme)
    void processPayment() {
        open("/checkout");
        find("#pay-btn").click();
        assertThat(By.id("receipt")).isVisible();
    }
}
```

- `@Retryable`'ı sınıf düzeyine eklerseniz sınıftaki tüm test metotları için geçerli olur.
- `maxAttempts` belirtilmezse `testfly.yml` içindeki global `retry.maxAttempts` değeri kullanılır:

```yaml title="testfly.yml"
retry:
  enabled: true
  maxAttempts: 1
```

Yeniden denenmiş testler HTML test raporunda **↻ Nx** rozeti ile işaretlenir.

---

## Tarayıcı Yaşam Döngüsü: Per-Test ve Per-Suite

`testfly.yml` dosyanızdan yaşam döngüsünü ayarlayabilirsiniz:

```yaml title="testfly.yml"
browser:
  name: chrome
  lifecycle: per-test   # veya 'per-suite'
```

- **`per-test` (Varsayılan):** Her test metodundan önce temiz bir tarayıcı açılır (`beforeEach`) ve test biter bitmez kapatılır (`afterEach`). Maksimum test izolasyonu sağlar.
- **`per-suite`:** Test sınıfı içindeki tüm testler boyunca tek bir tarayıcı açık tutulur. `TestFlyExtension.afterAll()` sınıfın son testi bittiğinde süitteki tüm sürücüleri otomatik olarak sonlandırır.

---

## Paralel Çalıştırma

JUnit 5 paralel test çalıştırmayı yerleşik olarak destekler. `src/test/resources/junit-platform.properties` dosyasını oluşturun:

```properties title="src/test/resources/junit-platform.properties"
junit.jupiter.execution.parallel.enabled=true
junit.jupiter.execution.parallel.mode.default=concurrent
junit.jupiter.execution.parallel.mode.classes.default=concurrent
junit.jupiter.execution.parallel.config.strategy=fixed
junit.jupiter.execution.parallel.config.fixed.parallelism=4
```

TestFly'ın `ThreadLocal` sürücü mimarisi, paralel çalışan iş parçacıkları arasında tam oturum ve bellek izolasyonu sağlar.

---

## Kurumsal Entegrasyonlar

JUnit 5 testleriniz TestFly'ın tüm kurumsal yeteneklerinden sıfır kod değişikliğiyle faydalanır:

### 1. Google Gemini & Claude AI Hata Analizi
Bir JUnit 5 testi fail ettiğinde, TestFly sayfa URL'ini, sayfa başlığını, DOM bağlamını ve stack trace'i toplayarak Google Gemini veya Anthropic Claude üzerinden kök neden analizi ve çözüm önerisi üretir:

```yaml title="testfly.yml"
ai:
  failureAnalysis: true
  provider: gemini
  apiKey: ${GEMINI_API_KEY}
```

### 2. Test Yönetim Sistemleri (TestRail ve Xray)
Test sonuçları, çalışma süreleri ve hata mesajları TestRail veya Jira Xray sistemlerine otomatik olarak aktarılır.

### 3. Otomatik ReportPortal Köprüsü
`reporting.reportportal.enabled=true` ise `TestFlyExtension` otomatik olarak `ReportPortalJUnit5Bridge` üzerinden test başlatma, adım logları ve sonuçları ReportPortal'a aktarır.

### 4. Karantina Desteği (`testfly-quarantine.yml`)
Flaky testleri koda dokunmadan `testfly-quarantine.yml` ile karantinaya alabilirsiniz:

```yaml title="testfly-quarantine.yml"
quarantine:
  - test: com.yourcompany.tests.FlakyCheckoutTest#testPayment
    reason: "Ödeme altyapısındaki timeout inceleniyor"
```

Karantinaya alınan testler henüz tarayıcı ayağa kaldırılmadan güvenle atlanır (SKIPPED).

---

## Özellik Eşitliği: TestNG vs. JUnit 5

| Özellik | TestNG | JUnit 5 |
|---|:---:|:---:|
| Otomatik WebDriver Yaşam Döngüsü | ✅ | ✅ |
| ThreadLocal Sürücü İzolasyonu | ✅ | ✅ |
| Akıcı Locator'lar (`$()`, `find()`) | ✅ | ✅ |
| Anlamsal Locator'lar (`getByRole`, `getByText` vb.) | ✅ | ✅ |
| Web-Öncelikli Doğrulamalar (`assertThat`) | ✅ | ✅ |
| Soft Doğrulamalar (`softAssert`) | ✅ | ✅ |
| Yerleşik REST İstemcisi (`apiClient`, `apiGet/Post`) | ✅ | ✅ |
| Çoklu Kullanıcı Oturumları (`session()`, `withSession()`) | ✅ | ✅ |
| Veritabanı Doğrulamaları (`db()`) | ✅ | ✅ |
| E-Posta Servis Testleri (`mailbox()`) | ✅ | ✅ |
| Erişilebilirlik Taramaları (`accessibility().scan()`) | ✅ | ✅ |
| `@NoBrowser` ile Tarayıcısız Çalışma | ✅ | ✅ |
| HTML Raporu ve Adım Zaman Çizelgesi | ✅ | ✅ |
| Hata Anında Otomatik Ekran Görüntüsü | ✅ | ✅ |
| Gemini / Claude AI Kök Neden Analizi | ✅ | ✅ |
| Yürütme İzi (Trace) ve Ekran Kaydı (Video) | ✅ | ✅ |
| JavaScript Konsol Hataları Denetimi | ✅ | ✅ |
| `@PreCondition` Oturum Önbellekleme | ✅ | ✅ |
| `@Retryable` Akıllı Yeniden Deneme Mekanizması | ✅ | ✅ |
| `testfly-quarantine.yml` Karantina Desteği | ✅ | ✅ |
| ReportPortal, TestRail ve Xray Entegrasyonu | ✅ | ✅ |