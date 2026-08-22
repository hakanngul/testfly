---
description: "Selenide'den TestFly'a geçiş: sevdiğiniz akıcı Selenium ergonomisini koruyun ve bunun yanında framework tarafından yönetilen yaşam döngüsü, yapılandırma odaklı bekleme, erişilebilirlik öncelikli locator'lar ve kurumsal raporlama elde edin."
id: from-selenide
title: Selenide'den Geçiş
sidebar_label: Selenide'den
sidebar_position: 4
---

# Selenide'den Geçiş

Selenide ve TestFly aynı hedefi paylaşır: **Selenium testlerini kısa ve kararlı hale getirmek**. Ekibiniz zaten Selenide kullanıyorsa, geçişin büyük kısmı bir kelime değişikliğidir — akıcı locator'lar, otomatik bekleme ve kısa doğrulamalar yakından eşlenir. TestFly, framework tarafından yönetilen yaşam döngüsü, tek bir YAML yapılandırması, erişilebilirlik öncelikli locator'lar ve yerleşik kurumsal raporlama ekler.

---

## Tanıdık olanlar

### Akıcı element API'si

Selenide'nin `$` ve `$$` işlevlerinin doğrudan TestFly karşılıkları vardır:

| Selenide | TestFly |
|---|---|
| `$("#login").click()` | `find("#login").click()` |
| `$("#email").setValue("a@b.com")` | `find("#email").type("a@b.com")` |
| `$(".msg").shouldHave(text("Welcome"))` | `assertThat(find(".msg")).hasText("Welcome")` |
| `$("button").shouldBe(visible)` | `assertThat(find("button")).isVisible()` |
| `$("#submit").shouldBe(enabled)` | `assertThat(find("#submit")).isEnabled()` |
| `$$('.item').first()` | `find(".item").nth(0)` |
| `$("[data-testid='x']")` | `getByTestId("x")` |

### Otomatik bekleme

Her iki framework de etkileşimden önce otomatik olarak bekler. Selenide'de zaman aşımı global ve örtüktür; TestFly'da ise `testfly.yml` içinde yaşar ve `WaitEngine` tarafından uygulanır:

```yaml title="testfly.yml"
timeouts:
  explicit: 10
```

```java
// Her iki framework de tıklamadan önce tıklanabilirlik için otomatik bekler
find("#login").click();
```

### Page Object'ler

Selenide page object'leri `com.codeborne.selenide.SelenidePageObject` sınıfını genişletir veya düz sınıflardır. TestFly page object'leri `BasePage`'i genişletir:

```java title="LoginPage.java"
import io.testfly.test.BasePage;
import org.openqa.selenium.By;

public class LoginPage extends BasePage {

    private static final By EMAIL = By.id("email");
    private static final By PASSWORD = By.id("password");
    private static final By SUBMIT = By.id("submit");

    public void login(String email, String password) {
        type(EMAIL, email);
        type(PASSWORD, password);
        click(SUBMIT);
    }
}
```

---

## Farklı olanlar

### Driver yaşam döngüsü

Selenide, kendi statik driver ve yapılandırmasını `Configuration` üzerinden yönetir. TestFly, `testfly.yml` tarafından yönlendirilen `DriverManager` aracılığıyla yaşam döngüsünü her iş parçacığı için yönetir:

| Konu | Selenide | TestFly |
|---|---|---|
| Tarayıcı seçimi | `Configuration.browser = "chrome"` | `testfly.yml` içinde `browser.name: chrome` |
| Headless | `Configuration.headless = true` | `testfly.yml` içinde `browser.headless: true` |
| Temel URL | `Configuration.baseUrl = "..."` | `testfly.yml` içinde `execution.baseUrl: ...` |
| Zaman aşımı | `Configuration.timeout = 4000` | `testfly.yml` içinde `timeouts.explicit: 10` |
| Paralelik | TestNG/Surefire XML + Selenide statik yapılandırması | TestNG paralel + `execution.parallel`/`threadCount` |

TestFly'nin yaklaşımı, ortama özel ayarları Java kodunun dışında tutar.

### Doğrulamalar (Assertions)

Selenide'nin `shouldHave` / `shouldBe` işlevleri `SelenideElement` üzerindeki koşullardır. TestFly'nin `assertThat(...)` işlevi bir `LocatorAssert` döndürür:

```java title="Selenide"
$("#status").shouldHave(text("Active"));
$("#status").shouldBe(visible, Duration.ofSeconds(5));
```

```java title="TestFly"
assertThat(find("#status")).hasText("Active");
assertThat(find("#status"), 5).isVisible();   // 5 saniyelik geçersiz kılma
```

### Erişilebilirlik öncelikli locator'lar

Selenide çoğunlukla CSS / XPath'e dayanır. TestFly `getByRole`, `getByLabel`, `getByText` vb. kullanımını teşvik eder:

```java
getByRole(Role.BUTTON).withName("Save").click();
getByLabel("Email address").type("a@b.com");
```

Bunlar CSS yeniden düzenlemelerinden sağ çıkar ve kullanıcı niyetine daha yakın okunur.

### Raporlama

Selenide, hata durumunda ekran görüntüleri ve sayfa kaynağını yakalar. TestFly şunları ekler:

- Geçme oranı göstergesi ve zaman çizelgesi içeren kendi kendine yeterli HTML raporu
- JUnit XML çıktısı
- Flakiness analizcisi
- Allure, Slack, Teams ve ReportPortal adaptörleri
- Adım başına ekran görüntüleriyle adım günlüğü

```java
StepLogger.step("Enter credentials");
find("#email").type("a@b.com");
```

---

## Geçiş kontrol listesi

1. **Bağımlılığı değiştirin**
   - `com.codeborne:selenide` bağımlılığını kaldırın
   - `io.testfly:testfly` bağımlılığını ekleyin

2. **Yapılandırmayı `testfly.yml` dosyasına taşıyın**
   - `Configuration.browser` → `browser.name`
   - `Configuration.headless` → `browser.headless`
   - `Configuration.baseUrl` → `execution.baseUrl`
   - `Configuration.timeout` → `timeouts.explicit`

3. **Page object'leri güncelleyin**
   - Selenide page-object kurallarını kullanmak yerine `BasePage`'i genişletin
   - `$("...")` yerine `find("...")` veya `getByRole(...)` kullanın
   - `BasePage` yardımcılarını kullanın: `click(By)`, `type(By, String)`, `upload(By, String)`

4. **Doğrulamaları güncelleyin**
   - `.shouldHave(...)` / `.shouldBe(...)` yerine `assertThat(...).hasText(...)`, `.isVisible()`, `.isEnabled()` vb. kullanın

5. **Selenide statik kurulumunu silin**
   - Test temel sınıflarından `Configuration.*` çağrılarını kaldırın
   - `Selenide.open(...)`, `Selenide.closeWebDriver()` ve driver import'larını kaldırın

6. **`BaseTest`'i genişletin**
   - Selenide test tabanını `public class MyTest extends BaseTest` ile değiştirin
   - `execution.baseUrl` adresine gitmek için `open()` kullanın

7. **Gerekirse raporlama ekleyin**
   - HTML raporu otomatiktir
   - Opsiyonel adaptörler yapılandırma veya SPI aracılığıyla etkinleştirilebilir

---

## Yan yana örnek

### Selenide

```java
public class LoginTest {

    @Test
    public void login() {
        open("/login");
        $("#email").setValue("admin@testfly.io");
        $("#password").setValue("secret");
        $("#login").click();
        $("h1").shouldHave(text("Dashboard"));
    }
}
```

### TestFly

```java
public class LoginTest extends BaseTest {

    @Test
    public void login() {
        open("/login");
        find("#email").type("admin@testfly.io");
        find("#password").type("secret");
        find("#login").click();
        assertThat(find("h1")).hasText("Dashboard");
    }
}
```

---

## Selenide'de ne zaman kalmalı

Selenide olgun ve mükemmel bir seçimdir. Şu durumlarda onunla kalın:

- Tüm kod tabanınız zaten akıcı Selenide ise ve raporlama/yaşam döngüsü boşluklarınız yoksa.
- Tek, statik olarak yapılandırılmış bir aracı tercih ediyorsanız.
- TestFly'ın erişilebilirlik öncelikli locator'larına, adım günlüğüne veya yerleşik CI/kurumsal adaptörlerine ihtiyacınız yoksa.

Bu eklemeleri Selenium / Java / TestNG yapısından ayrılmadan istediğinizde TestFly'ı seçin.

---

## Sonraki adımlar

- [Başlarken](/docs/getting-started) — 5 dakikada ilk TestFly testi
- [BasePage](/docs/guides/base-page) — page-object yardımcıları
- [Semantik Locator'lar](/docs/guides/semantic-locators) — `getByRole`, `getByLabel` vb.
- [Yapılandırma Referansı](/docs/configuration) — tam `testfly.yml`