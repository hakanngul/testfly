---
description: "Cucumber 7 ile BDD Selenium testi: sürücü yaşam döngüsü, hata anında ekran görüntüsü ve HTML raporunda adım zaman çizelgesi — kutudan çıktığı gibi bağlanmış."
id: cucumber
title: BDD / Cucumber
sidebar_position: 11
---

# BDD / Cucumber Entegrasyonu

TestFly, Cucumber 7 ile kutudan çıktığı gibi entegre çalışır. Sürücü yaşam döngüsü, HTML raporu adım zaman çizelgesi, hata anında ekran görüntüsü, metrikler ve tüm framework özellikleri otomatik çalışır — adım tanımlarınızda hiçbir kalıp kod (boilerplate) gerekmez.

---

## Kurulum

Cucumber'ı TestFly ile birlikte projenize ekleyin:

```xml title="pom.xml"
<dependency>
    <groupId>io.testfly</groupId>
    <artifactId>testfly</artifactId>
    <version>1.11.0</version>
</dependency>

<dependency>
    <groupId>io.cucumber</groupId>
    <artifactId>cucumber-java</artifactId>
    <version>7.20.1</version>
</dependency>

<dependency>
    <groupId>io.cucumber</groupId>
    <artifactId>cucumber-testng</artifactId>
    <version>7.20.1</version>
</dependency>
```

---

## Proje yapısı

```
src/
└── test/
    ├── java/
    │   └── com/yourcompany/
    │       ├── bdd/
    │       │   ├── CucumberRunner.java       ← koşturucu (runner) sınıfı
    │       │   └── steps/
    │       │       ├── LoginSteps.java        ← BaseCucumberSteps'i genişletir
    │       │       └── NavigationSteps.java
    └── resources/
        ├── features/
        │   └── login.feature
        └── cucumber.properties               ← IDE tek-senaryo çalıştırmaları için
```

---

## Koşturucu (runner) sınıfı

`@CucumberOptions` ile işaretleyin ve `BaseCucumberTest`'i genişletin. Başka koda gerek yok:

```java
@CucumberOptions(
    features = "src/test/resources/features",
    glue     = {"com.yourcompany.bdd.steps", "io.testfly.cucumber"},
    plugin   = {"pretty", "io.testfly.cucumber.CucumberStepLogger"}
)
public class CucumberRunner extends BaseCucumberTest {}
```

`glue` içindeki `"io.testfly.cucumber"` değeri gereklidir — Cucumber'a sürücü yaşam döngüsünü yöneten `CucumberHooks`'u nerede bulacağını söyler.

`plugin` içindeki `CucumberStepLogger`, Gherkin adım adlarını TestFly HTML raporu adım zaman çizelgesine akıtır.

---

## Adım tanımları

`getDriver()`, `open()`, `$()`, `assertThat()` almak için `BaseCucumberSteps`'i genişletin:

```java
public class LoginSteps extends BaseCucumberSteps {

    private LoginPage loginPage;

    @Given("the user is on the login page")
    public void onLoginPage() {
        open();                                 // execution.baseUrl adresine gider
        loginPage = new LoginPage(getDriver());
    }

    @When("they login as {string} with password {string}")
    public void login(String username, String password) {
        loginPage.login(username, password);
    }

    @Then("the dashboard is visible")
    public void dashboardVisible() {
        assertThat(By.id("dashboard")).isVisible();   // otomatik yeniden deneyen doğrulama
    }
}
```

`BaseCucumberSteps` şunları sağlar:

| Metot | Açıklama |
|---|---|
| `getDriver()` | Geçerli iş parçacığının `WebDriver` nesnesi |
| `getWait()` | `testfly.yml` içindeki `timeouts.explicit` değerini kullanan `WebDriverWait` |
| `open()` | `execution.baseUrl` adresine gider |
| `open(path)` | `baseUrl + path` adresine gider |
| `$(css)` | Zincirlenebilir akıcı (fluent) locator |
| `$(By)` | Zincirlenebilir akıcı (fluent) locator |
| `assertThat(By)` | Otomatik yeniden deneyen doğrulama |
| `assertThat(Locator)` | Bir locator zinciri üzerinde otomatik yeniden deneyen doğrulama |
| `getScenario()` | Geçerli Cucumber `Scenario` nesnesi |

---

## Özellik (feature) dosyaları

Standart Gherkin — framework'e özgü bir sözdizimi yok:

```gherkin title="src/test/resources/features/login.feature"
Feature: User Login

  Scenario: Valid credentials grant access
    Given the user is on the login page
    When they login as "admin" with password "secret"
    Then the dashboard is visible

  Scenario Outline: Multiple accounts can log in
    Given the user is on the login page
    When they login as "<username>" with password "<password>"
    Then the dashboard is visible

    Examples:
      | username | password |
      | admin    | secret   |
      | editor   | pass123  |
```

Her Scenario Outline örnek satırı, kendi adım zaman çizelgesi ve ekran görüntüsüyle HTML raporunda ayrı bir giriş üretir.

---

## IDE tek-senaryo çalıştırma

Bir senaryoyu IDE'den tek başına çalıştırdığınızda (sağ tık → Run), IDE kendi koşturucusunu kullanır ve `@CucumberOptions` değerini okumaz. `CucumberHooks`'un her zaman bulunması için bir `cucumber.properties` dosyası ekleyin:

```properties title="src/test/resources/cucumber.properties"
cucumber.glue=com.yourcompany.bdd.steps,io.testfly.cucumber
cucumber.plugin=pretty,io.testfly.cucumber.CucumberStepLogger
cucumber.monochrome=true
```

---

## Yeniden deneme (Retry)

### Global yeniden deneme

`testfly.yml` içinde yeniden denemeyi etkinleştirin — başarısız olan tüm senaryolar otomatik olarak yeniden denenir:

```yaml title="testfly.yml"
retry:
  enabled: true
  maxAttempts: 1   # 1 yeniden deneme = senaryo başına toplam 2 deneme
```

### Senaryo bazlı yeniden deneme etiketi

`@retryable` veya `@retryable=N` etiketini kullanarak global yapılandırmayı tek tek senaryolar için geçersiz kılın:

```gherkin
# testfly.yml içindeki global retry.maxAttempts değerini kullanır
@retryable
Scenario: Login sometimes flakes on slow CI
  Given the user is on the login page
  When they submit valid credentials
  Then the dashboard is visible

# Global yapılandırmadan bağımsız olarak tam 2 yeniden deneme
@retryable=2
Scenario: Very flaky third-party widget
  Given the widget is loaded
  Then it should display the correct value
```

Etiket biçimleri:

| Etiket | Davranış |
|---|---|
| `@retryable` | Yapılandırmadaki `retry.maxAttempts` değerini kullanarak yeniden dener |
| `@retryable=N` | Tam olarak N kez yeniden dener (yapılandırmayı geçersiz kılar) |

### Nasıl çalışır

Bir senaryo başarısız olduğunda, **senaryonun tamamı 1. adımdan itibaren** taze bir sürücüyle yeniden çalıştırılır. Uygulama her yeniden deneme için temiz bir durumdadır.

Yeniden denenmiş senaryolar HTML raporunda bir **↻ 1x** rozeti gösterir. Raporlarda görünen, son durumdur (tüm denemeler sonrası PASSED veya FAILED).

---

## Maven ile çalıştırma

```bash
# Tüm Cucumber senaryolarını çalıştır
mvn test -Dtest=CucumberRunner

# Belirli bir özellik dosyasını çalıştır
mvn test -Dtest=CucumberRunner -Dcucumber.features=src/test/resources/features/login.feature

# @smoke etiketli senaryoları çalıştır
mvn test -Dtest=CucumberRunner -Dcucumber.filter.tags="@smoke"
```

---

## Neler otomatiktir

`CucumberHooks` (`io.testfly.cucumber` glue paketinde bulunur) senaryo başına her şeyi yönetir:

| Olay | Ne olur |
|---|---|
| Senaryo başlangıcı | Sürücü oluşturulur, metrik zamanlayıcı başlar, test kimliği kaydedilir |
| Adım yürütme | `CucumberStepLogger` her adım adını + başarı/başarısızlık durumunu HTML raporu zaman çizelgesine yazar |
| Senaryo başarısızlığı | Ekran görüntüsü yakalanır ve hem TestFly raporuna hem de Cucumber'ın kendi HTML raporuna gömülür |
| Senaryo sonu | Sürücü kapatılır, metrikler kaydedilir, durum (PASSED / FAILED / SKIPPED) yazılır |
| Paket sonu | `SuiteExecutionListener.onFinish()` tam HTML raporunu, flakiness radarını ve JSON dışa aktarımını üretir |

---

## Paralel yürütme

`testfly.yml` içinde `parallel` ve `threadCount` ayarlayın — framework'ün ThreadLocal sürücü izolasyonu Cucumber senaryolarını iş parçacığı güvenli yapar:

```yaml title="testfly.yml"
execution:
  parallel: methods
  threadCount: 4
  maxActiveSessions: 4
```

Her senaryo, kendi sürücü örneğiyle kendi iş parçacığında çalışır.

---

## Tek pakette Cucumber ve TestNG karışımı

TestFly, ikisini de aynı Maven çağrısında destekler. HTML raporu, TestNG test sonuçlarını ve Cucumber senaryo sonuçlarını tek bir panelde birleştirir. `TestExecutionListener`, yinelenen girişleri önlemek için Cucumber koşturucu testlerinin kaydını otomatik olarak atlar.

---

## Cucumber raporuna veri ekleme

Cucumber'ın kendi HTML raporuna ekran görüntüsü, metin veya JSON eklemek için herhangi bir adım tanımından geçerli `Scenario` nesnesine erişin:

```java
public class MySteps extends BaseCucumberSteps {

    @Then("attach current screenshot")
    public void attachScreenshot() {
        String base64 = ScreenshotManager.captureAsBase64();
        if (base64 != null) {
            getScenario().attach(
                Base64.getDecoder().decode(base64),
                "image/png",
                "Current state"
            );
        }
    }
}
```