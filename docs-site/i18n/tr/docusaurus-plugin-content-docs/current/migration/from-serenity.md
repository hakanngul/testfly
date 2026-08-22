---
description: "Serenity BDD'den TestFly'a geçiş: Screenplay/Page Object desenlerinizi koruyun, ağır raporlama/yaşam döngüsü yapısını hafif YAML odaklı yapılandırmayla değiştirin ve CI entegrasyonunu basitleştirin."
id: from-serenity
title: Serenity BDD'den Geçiş
sidebar_label: Serenity BDD'den
sidebar_position: 5
---

# Serenity BDD'den Geçiş

Serenity BDD ve TestFly, her ikisi de Selenium'un üzerinde durur ve okunabilir, raporlanabilir testler üretmeyi hedefler. Serenity BDD, BDD, Screenplay ve kendi ayrıntılı canlı dokümantasyonuna güçlü şekilde yaslanır. TestFly daha hafiftir: size aynı okunabilir testleri ve sağlam raporlamayı sunar, ancak daha az anotasyon töreni, tek bir YAML yapılandırma dosyası ve daha küçük bir bağımlılık alanıyla.

---

## Tanıdık olanlar

### Page Object'ler / Screenplay

Serenity Page Object'leri kullanıyorsanız, geçiş doğrudandır:

| Serenity | TestFly |
|---|---|
| `PageObject` | `BasePage` |
| `@FindBy(id = "email")` | `By.id("email")` alanı veya satır içi `find("#email")` |
| `element(email).type("...")` | `type(EMAIL, "...")` veya `find("#email").type("...")` |
| `WebElementFacade` | `find(...)` ile `Locator` / `WebElement` |
| Serenity matcher'larıyla `Assert.assertThat(...)` | `assertThat(find(...)).hasText(...)` |

### Adım raporlama

Serenity, her `@Step` metodunu raporuna kaydeder. TestFly aynı amaç için `StepLogger` kullanır:

```java title="Serenity"
@Step("Enter credentials")
public void entersCredentials(String user, String pass) { ... }
```

```java title="TestFly"
StepLogger.step("Enter credentials");
find("#email").type(user);
```

Her ikisi de HTML raporunda insan tarafından okunabilir bir zaman çizelgesi üretir.

### BDD / Cucumber

Serenity'nin Cucumber entegrasyonu önemli bir çekim noktasıdır. TestFly'ın da bir Cucumber köprüsü vardır:

```java
public class MySteps extends BaseCucumberSteps { ... }
```

Tam kurulum için bkz. [Cucumber](/docs/cucumber).

---

## Farklı olanlar

### Yapılandırma modeli

Serenity, `serenity.conf` / `serenity.properties` ve birçok JVM özelliğini kullanır. TestFly tek bir `testfly.yml` kullanır:

```yaml title="testfly.yml"
browser:
  name: chrome
  headless: false

execution:
  baseUrl: https://your-app.com
  parallel: methods
  threadCount: 4

timeouts:
  explicit: 10

retry:
  enabled: true
  maxAttempts: 2
```

### Driver yaşam döngüsü

Serenity, driver'ları kendi `WebDriverManager` / `WebDriverFacade` aracılığıyla yönetir. TestFly, iş parçacığı yerel izolasyonu ile `DriverManager` kullanır:

```java
protected WebDriver getDriver() { ... }   // BaseTest / BasePage'den
```

`@Managed` anotasyonları yok, `PageFactory` yok, driver alan enjeksiyonu yok.

### Doğrulamalar

Serenity, Hamcrest/Fest doğrulamalarını sarar ve otomatik bekleme ekler. TestFly `LocatorAssert` sağlar:

```java title="Serenity"
loginButton.shouldBeVisible();
loginButton.shouldContainText("Sign in");
```

```java title="TestFly"
assertThat(find("#login")).isVisible();
assertThat(find("#login")).hasText("Sign in");
```

### Raporlama felsefesi

Serenity çok ayrıntılı canlı dokümantasyon üretir. TestFly odaklanmış bir HTML panosu üretir:

- Geçme oranı göstergesi ve suite özeti
- Ekran görüntüleriyle test başına zaman çizelgesi
- Flakiness radarı
- Yeniden deneme rozetleri
- CI alımı için JUnit XML
- Opsiyonel Allure / Slack / Teams / ReportPortal adaptörleri

Kuruluşunuz Serenity'nin öyküsel canlı-dokümantasyon raporlarına bağımlıysa, TestFly'nin raporu kasıtlı olarak daha basittir. Geçiş yapmadan önce basitleştirilmiş formatın paydaş ihtiyaçlarını karşılayıp karşılamadığını değerlendirin.

---

## Geçiş kontrol listesi

1. **Bağımlılıkları değiştirin**
   - `net.serenity-bdd:*` mekanizmalarını kaldırın
   - `io.testfly:testfly` ekleyin

2. **Yapılandırmayı taşıyın**
   - `serenity.conf` / `serenity.properties` dosyasını `testfly.yml` dosyasına dönüştürün
   - `webdriver.driver` → `browser.name`
   - `serenity.take.screenshots` → ekran görüntüsü yapılandırması hatada otomatiktir
   - `serenity.timeout` → `timeouts.explicit`
   - `serenity.restart.browser.for.each` → `browser.lifecycle`

3. **Page object'leri güncelleyin**
   - `PageObject` yerine `BasePage`'i genişletin
   - `@FindBy` alanlarını `By` sabitleri veya satır içi `find(...)` çağrılarıyla değiştirin
   - `BasePage` yardımcılarını kullanın: `click(By)`, `type(By, String)`, `select(By, String)`

4. **Serenity adımlarını değiştirin**
   - `@Step` ile işaretlenmiş metotlar → `StepLogger.step("...")` çağrıları
   - Veya metotları koruyun ve giriş noktalarına `StepLogger.step(...)` ekleyin

5. **Doğrulamaları güncelleyin**
   - Serenity `shouldBeVisible`, `shouldContainText` vb. ifadelerini `assertThat(find(...)).*` ile değiştirin

6. **Test tabanını güncelleyin**
   - `SerenityRunner` / `SerenityJUnit5Extension` öğesini TestFly'ın `BaseTest` veya `BaseJUnit5Test` sınıfıyla değiştirin

7. **Cucumber adımları (kullanılıyorsa)**
   - `Serenity Cucumber` adım tanımlarını `BaseCucumberSteps` ile değiştirin

---

## Yan yana örnek

### Serenity Page Object

```java
public class LoginPage extends PageObject {

    @FindBy(id = "email")
    private WebElementFacade email;

    @FindBy(id = "password")
    private WebElementFacade password;

    @FindBy(id = "login")
    private WebElementFacade loginButton;

    @Step("Login as {0}")
    public void login(String user, String pass) {
        email.type(user);
        password.type(pass);
        loginButton.click();
    }
}
```

### TestFly Page Object

```java
public class LoginPage extends BasePage {

    private static final By EMAIL = By.id("email");
    private static final By PASSWORD = By.id("password");
    private static final By LOGIN = By.id("login");

    public void login(String user, String pass) {
        StepLogger.step("Login as " + user);
        type(EMAIL, user);
        type(PASSWORD, pass);
        click(LOGIN);
    }
}
```

---

## Serenity'de ne zaman kalmalı

Serenity şu durumlarda güçlü bir uyumdur:

- Paydaş onayı için zengin canlı-dokümantasyon raporlarına güveniyorsanız.
- Ekibiniz tamamen Screenplay desenine ve `@Step` odaklı BDD'ye bağlıysa.
- Serenity'ye özel anotasyonlarda ve eklentilerde büyük bir mevcut yatırımınız varsa.

Modern locator'lar ve daha basit CI entegrasyonu içeren daha hafif, YAML ile yapılandırılmış, Selenium yerli bir framework istediğinizde TestFly daha iyi bir uyumdur.

---

## Sonraki adımlar

- [Başlarken](/docs/getting-started) — 5 dakikada ilk TestFly testi
- [BasePage](/docs/guides/base-page) — page-object yardımcıları
- [Adım Günlüğü](/docs/guides/step-logging) — adlandırılmış adımlar ve ekran görüntüleri
- [Cucumber](/docs/cucumber) — TestFly'da BDD köprüsü
- [Yapılandırma Referansı](/docs/configuration) — tam `testfly.yml`