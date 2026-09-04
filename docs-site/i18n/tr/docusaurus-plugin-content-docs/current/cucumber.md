---
description: "Cucumber 7 ile BDD Selenium testi: sürücü yaşam döngüsü, paralel çalıştırma, API adım yardımcıları, ScenarioContext ile veri paylaşımı, AI hata analizi ve ReportPortal entegrasyonu."
id: cucumber
title: BDD / Cucumber
sidebar_position: 11
---

# BDD / Cucumber Entegrasyonu

TestFly, Cucumber 7 ile kutudan çıktığı gibi tam entegre çalışır. Framework tüm yaşam döngüsünü yönetir: her senaryo için bağımsız WebDriver sağlama, ThreadLocal sürücü izolasyonu, hata anında otomatik ekran görüntüsü, HTML raporunda adım zaman çizelgesi, adımlar içinde doğrudan REST API test desteği, dependency injection gerektirmeyen yerleşik `ScenarioContext` veri paylaşımı, karantina mekanizması ve yapay zeka (AI) destekli hata analizi.

---

## Kurulum

Projenizin `pom.xml` dosyasına TestFly'ın yanına Cucumber bağımlılıklarını ekleyin:

```xml title="pom.xml"
<dependencies>
    <!-- TestFly Çekirdeği -->
    <dependency>
        <groupId>io.testfly</groupId>
        <artifactId>testfly</artifactId>
        <version>1.0.0</version>
    </dependency>

    <!-- Cucumber Java ve TestNG -->
    <dependency>
        <groupId>io.cucumber</groupId>
        <artifactId>cucumber-java</artifactId>
        <version>7.20.1</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>io.cucumber</groupId>
        <artifactId>cucumber-testng</artifactId>
        <version>7.20.1</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

---

## Proje Dizini Yapısı

Standart bir TestFly + Cucumber proje yerleşimi:

```text
src/
└── test/
    ├── java/
    │   └── com/sirketiniz/
    │       ├── bdd/
    │       │   ├── CucumberRunner.java          ← BaseCucumberTest'i genişletir
    │       │   └── steps/
    │       │       ├── AuthSteps.java           ← BaseCucumberSteps'i genişletir
    │       │       ├── ProductSteps.java        ← BaseCucumberSteps'i genişletir
    │       │       └── OrderSteps.java          ← BaseCucumberSteps'i genişletir
    └── resources/
        ├── features/
        │   ├── login.feature
        │   └── checkout.feature
        ├── cucumber.properties                  ← IDE tekil senaryo koşuları için
        └── testfly.yml                          ← Framework yapılandırması
```

---

## Runner Sınıfı ve Paralel Çalıştırma

Runner sınıfınızı `@CucumberOptions` ile etiketleyin ve `BaseCucumberTest` sınıfından türetin:

```java title="src/test/java/com/sirketiniz/bdd/CucumberRunner.java"
package com.sirketiniz.bdd;

import io.cucumber.testng.CucumberOptions;
import io.testfly.cucumber.BaseCucumberTest;
import org.testng.annotations.DataProvider;

@CucumberOptions(
    features = "src/test/resources/features",
    glue     = {"com.sirketiniz.bdd.steps", "io.testfly.cucumber"},
    plugin   = {"pretty", "io.testfly.cucumber.CucumberStepLogger"}
)
public class CucumberRunner extends BaseCucumberTest {

    /**
     * TestNG Cucumber senaryolarının paralel koşması için ZORUNLUDUR!
     */
    @Override
    @DataProvider(parallel = true)
    public Object[][] scenarios() {
        return super.scenarios();
    }
}
```

:::warning KRİTİK BİLGİ: Cucumber-TestNG Paralel Senaryo Tuzağı
Varsayılan olarak Cucumber'ın `AbstractTestNGCucumberTests` sınıfı, `testfly.yml` içinde `parallel: methods` yazsa bile senaryoları **sırayla (tek thread)** çalıştırır. Senaryoların gerçekten farklı thread'lerde eşzamanlı çalışabilmesi için yukarıdaki gibi **`scenarios()` metodunu `@DataProvider(parallel = true)` ile override etmeniz ZORUNLUDUR!**
:::

### `testfly.yml` Paralel Yapılandırması

Paralel thread sayısını `testfly.yml` dosyasından belirleyin:

```yaml title="testfly.yml"
execution:
  mode: local
  parallel: methods
  threadCount: 4
  maxActiveSessions: 4

browser:
  name: chrome
  headless: true
```

Her senaryo kendi iş parçacığında (thread) `DriverManager` tarafından yönetilen tamamen izole bir `WebDriver` örneğine sahip olur.

### Glue ve Plugin Gereksinimleri:
- `glue` içinde `"io.testfly.cucumber"` tanımlanması **zorunludur** — bu ayar Cucumber'a sürücü yaşam döngüsünü, metrikleri ve AI analizini yöneten `CucumberHooks`'u nerede bulacağını söyler.
- `plugin` içindeki `CucumberStepLogger`, Gherkin adım adlarını ve durumlarını TestFly HTML zaman çizelgesi raporuna anlık olarak aktarır.

---

## Adım Tanımları (`BaseCucumberSteps`)

Step definition sınıflarınız `BaseCucumberSteps` sınıfını genişletmelidir. Bu sayede TestFly'ın tüm akıcı ve anlamsal metotlarına doğrudan erişebilirsiniz:

```java title="src/test/java/com/sirketiniz/bdd/steps/LoginSteps.java"
package com.sirketiniz.bdd.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import io.testfly.cucumber.BaseCucumberSteps;
import io.testfly.locator.Role;
import org.openqa.selenium.By;

public class LoginSteps extends BaseCucumberSteps {

    @Given("kullanıcı giriş sayfasındadır")
    public void onLoginPage() {
        open("/login");
    }

    @When("kullanıcı adı {string} ve şifre {string} ile giriş yapar")
    public void signIn(String username, String password) {
        getByLabel("Kullanıcı Adı").type(username);
        getByLabel("Şifre").type(password);
        getByRole(Role.BUTTON, "Giriş Yap").click();
    }

    @Then("dashboard başlığı görüntülenir")
    public void dashboardDisplayed() {
        assertThat(getByRole(Role.HEADING, "Dashboard")).isVisible();
    }
}
```

### `BaseCucumberSteps` Tarafından Sağlanan Yetenekler:

| Yetenek | Sunulan Metotlar |
|---|---|
| **Gezinme (Navigation)** | `open()`, `open(path)`, `getDriver()`, `getWait()` |
| **Anlamsal Locator'lar** | `getByRole(Role, name)`, `getByText()`, `getByLabel()`, `getByPlaceholder()`, `getByTestId()`, `getByAltText()`, `getByTitle()` |
| **Akıcı Locator'lar** | `find(css)`, `find(By)`, `$(css)`, `$$(css)` |
| **Web-Öncelikli Doğrulamalar** | `assertThat(By)`, `assertThat(Locator)` otomatik beklemeli doğrulamalar |
| **Soft Assertions** | `softAssert(By).isVisible()`, `softAssert(By).hasText(...)` |
| **Yerleşik REST İstemcisi** | `apiClient()`, `apiGet(path)`, `apiPost(path, body)`, `apiPut()`, `apiDelete()` |
| **Adım Kaydı (Step Logging)**| `step(name)`, `step(name, takeScreenshot)` |
| **Cucumber Bağlamı** | `getScenario()` ile mevcut `io.cucumber.java.Scenario` nesnesine erişim |

---

## Adımlar Arası Durum Paylaşımı (`ScenarioContext`)

Standart Cucumber'da farklı step sınıfları arasında veri aktarımı yapmak (örneğin giriş adımında alınan token'ı veya sipariş numarasını ödeme adımında kullanmak) için PicoContainer, Spring veya Guice gibi harici dependency injection araçları yapılandırmak gerekir.

TestFly, thread-safe çalışan yerleşik **`ScenarioContext`** mekanizması ile bunu sıfır yapılandırmayla çözer:

```java title="src/test/java/com/sirketiniz/bdd/steps/OrderSteps.java"
package com.sirketiniz.bdd.steps;

import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import io.testfly.context.ScenarioContext;
import io.testfly.cucumber.BaseCucumberSteps;
import org.openqa.selenium.By;

public class OrderSteps extends BaseCucumberSteps {

    @When("kullanıcı {string} ürünü için sipariş verir")
    public void placeOrder(String item) {
        find(".buy-btn").click();
        String orderNumber = find("#confirmation-num").getText();

        // Veriyi sonraki adımlar (veya farklı step sınıfları) için saklayın
        ScenarioContext.put("orderId", orderNumber);
    }

    @Then("sipariş durumu onaylandı olmalıdır")
    public void verifyOrderStatus() {
        // Saklanan veriyi başka bir adımda okuyun
        String orderId = ScenarioContext.get("orderId", String.class);
        
        open("/orders/" + orderId);
        assertThat(By.id("order-status")).hasText("CONFIRMED");
    }
}
```

> **Otomatik Bellek Temizliği:** Her senaryo bittiğinde `CucumberHooks`, `@After(order = 20000)` kancası içinde `ScenarioContext.clear()` çağrısını otomatik yapar. Senaryolar arasında hiçbir veri veya bellek sızıntısı kalmaz.

---

## BDD Adımlarında Hibrit API ve UI Kullanımı

BDD senaryolarında kullanıcı veya ürün gibi önkoşul verilerini UI üzerinden tıklayarak oluşturmak testleri ciddi oranda yavaşlatır. `BaseCucumberSteps` içindeki yerleşik API metotlarını kullanarak önkoşulları saniyeler içinde hazırlayabilirsiniz:

```java
public class UserSteps extends BaseCucumberSteps {

    @Given("sistemde e-postası {string} olan aktif bir müşteri bulunur")
    public void seedUserViaApi(String email) {
        // Kullanıcıyı REST API ile anında oluşturun
        String json = String.format("{\"email\":\"%s\",\"role\":\"CUSTOMER\"}", email);
        String userId = apiPost("/api/users", json)
                .assertThat().statusCode(201)
                .jsonPath().getString("id");

        ScenarioContext.put("userId", userId);
    }
}
```

---

## Feature Dosyaları ve Senaryo Şablonları

Standart Gherkin sözdizimi aynen geçerlidir:

```gherkin title="src/test/resources/features/checkout.feature"
# language: tr
Özellik: Ödeme ve Sipariş Akışı

  Önkoşul:
    Diyelim ki sistemde e-postası "alici@testfly.io" olan aktif bir müşteri bulunur
    Ve kullanıcı giriş sayfasındadır

  Senaryo: Kredi kartı ile başarılı ödeme
    Eğer kullanıcı adı "alici@testfly.io" ve şifre "secret" ile giriş yapar
    Ve kullanıcı "Mekanik Klavye" ürünü için sipariş verir
    O zaman sipariş durumu onaylandı olmalıdır

  Senaryo Taslağı: İndirim kuponu uygulama
    Eğer promosyon kodu "<kod>" uygulanırsa
    O zaman indirim oranı "<oran>" olarak yansımalıdır

    Örnekler:
      | kod        | oran |
      | YAZ2026    | %20  |
      | HOSGELDIN  | %10  |
```

Senaryo Taslağındaki (Scenario Outline) her `Examples` satırı TestFly HTML raporunda kendi ekran görüntüleri, metrikleri ve adım zaman çizelgesiyle ayrı bir test kaydı olarak listelenir.

---

## IDE Tekil Senaryo Çalıştırma

IntelliJ IDEA veya Eclipse içinden tek bir senaryoya sağ tıklayıp çalıştırdığınızda (Run 'Scenario: ...'), IDE kendi çalıştırıcısını kullanır ve `@CucumberOptions` ayarlarını okuyamaz.

`CucumberHooks` ve `CucumberStepLogger`'ın IDE üzerinden de devreye girmesi için `src/test/resources/cucumber.properties` dosyasını ekleyin:

```properties title="src/test/resources/cucumber.properties"
cucumber.glue=com.sirketiniz.bdd.steps,io.testfly.cucumber
cucumber.plugin=pretty,io.testfly.cucumber.CucumberStepLogger
cucumber.monochrome=true
```

---

## Akıllı Yeniden Deneme (Smart Retry)

### 1. Global Yapılandırma (`testfly.yml`)
```yaml title="testfly.yml"
retry:
  enabled: true
  maxAttempts: 1   # 1 retry = toplam 2 deneme
```

### 2. Senaryo Bazlı `@retryable` Etiketleri
Belirli senaryolarda global kuralı geçersiz kılabilirsiniz:

| Etiket | Davranış |
|---|---|
| `@retryable` | `testfly.yml` içindeki global `retry.maxAttempts` değerini kullanır |
| `@retryable=N` | Belirtilen `N` defa yeniden dener (örn: `@retryable=2`) |

```gherkin
@retryable=2
Senaryo: Ağ gecikmelerine duyarlı üçüncü parti ödeme servisi
  Eğer ödeme bilgileri gönderilirse
  O zaman ödeme makbuzu görüntülenir
```

Yeniden deneme sırasında TestFly **1. adımdan itibaren taze bir tarayıcı başlatır** ve HTML raporunda senaryoya **↻ Nx** rozeti ekler.

---

## Senaryoları Karantinaya Alma (Quarantine)

Geliştirme aşamasındaki veya geçici olarak hatalı senaryoları test kodunu silmeden devre dışı bırakın:

### Yöntem 1: Etiket ile Karantina
Senaryoya doğrudan `@quarantine` etiketi ekleyin:

```gherkin
@quarantine
Senaryo: Yenilenmekte olan eski rapor indirme akışı
  Eğer rapor indir butonuna tıklanırsa
  O zaman dosya başarıyla iner
```

Etiket adını `testfly.yml` üzerinden özelleştirebilirsiniz:
```yaml title="testfly.yml"
quarantine:
  enabled: true
  cucumberTag: "flaky"   # @quarantine yerine @flaky etiketi kullanılır
```

### Yöntem 2: YAML Dosyası ile Karantina (`testfly-quarantine.yml`)
Feature dosyalarına dokunmadan merkezi yapılandırma dosyasından karantinaya alın:

```yaml title="testfly-quarantine.yml"
quarantine:
  - scenario: "checkout.feature#Kredi kartı ile başarılı ödeme"
    reason: "Ödeme simülatöründe bakım çalışması var"
  - feature: "export.feature"
    reason: "Modül yeniden yazılıyor"
```

Karantinaya alınan senaryolar WebDriver başlatılmadan anında atlanır (SKIPPED).

---

## Cucumber Senaryolarında AI Hata Analizi

Bir senaryo fail ettiğinde `CucumberHooks`:
- Sayfanın son URL'i ve başlığını,
- Fail eden Gherkin adımını ve satır numarasını,
- Hata mesajı ve stack trace'i yakalar.

Bu veriler Google Gemini veya Anthropic Claude modeline iletilerek HTML raporunun altında Türkçe kök neden analizi sunulur:

```markdown
**Kök Neden:** `O zaman sipariş durumu onaylandı olmalıdır` adımı, `By.id("order-status")` elemanı 10 saniye boyunca `PENDING` durumunda kaldığı için zaman aşımına uğradı.
**Önerilen Çözüm:**
- Arka plandaki sipariş onay işleyicisinin (worker) ayakta olduğunu kontrol edin.
- Adım tanımına durum geçişi için açık bekleme ekleyin: `assertThat(By.id("order-status")).hasText("CONFIRMED")`.
```

`testfly.yml` ayarı:
```yaml title="testfly.yml"
ai:
  failureAnalysis: true
  provider: gemini
  apiKey: ${GEMINI_API_KEY}
```

---

## Kurumsal Raporlama Entegrasyonları

### ReportPortal Otomatik Adım Hiyerarşisi
`BaseCucumberTest`, ReportPortal Cucumber 7 eklentisini (`com.epam.reportportal.cucumber.ScenarioReporter`) otomatik olarak algılar ve bağlar.

Classpath'te `agent-java-cucumber7` mevcut ve `reporting.reportportal.enabled=true` ise:
- Özellikler (Feature) Root Launch/Suite olarak,
- Senaryolar Test Item olarak,
- Adımlar (`Given`, `When`, `Then`) iç içe geçmiş (nested) log adımları olarak ve hata anında ekran görüntüsü eklenerek ReportPortal'a aktarılır.

### JavaScript Konsol Hataları Takibi
Senaryo koşumu sırasında `ConsoleErrorCollector` tarayıcı konsolunu izler. Yakalanan JavaScript hataları raporda uyarı adımı (`[JS Error]`) olarak listelenir.

`browser.failOnConsoleErrors: true` yapılandırılmışsa, konsol hatası tespit edilen senaryolar doğrudan fail edilir.

---

## Maven ile Senaryoları Çalıştırma

```bash
# Tüm Cucumber senaryolarını çalıştır
mvn test -Dtest=CucumberRunner

# Belirli bir feature dosyasını çalıştır
mvn test -Dtest=CucumberRunner -Dcucumber.features=src/test/resources/features/checkout.feature

# Belirli etiketlere sahip senaryoları çalıştır
mvn test -Dtest=CucumberRunner -Dcucumber.filter.tags="@smoke and not @quarantine"

# Staging ortam profili ile çalıştır (testfly-staging.yml yüklenir)
mvn test -Dtest=CucumberRunner -Dtestfly.profile=staging
```