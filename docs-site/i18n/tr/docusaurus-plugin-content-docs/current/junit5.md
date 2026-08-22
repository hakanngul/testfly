---
description: "TestFly'yi JUnit 5 ile çalıştırın: TestNG ile aynı yaşam döngüsü, bekleme ve raporlama için @ExtendWith(TestFlyExtension) veya BaseJUnit5Test ile tercihli olarak kullanın."
id: junit5
title: JUnit 5 Desteği
sidebar_position: 10
---

# JUnit 5 Desteği

TestFly, hem **TestNG** (yerleşik) hem de **JUnit 5** (tercihli) destekler. JUnit 5 entegrasyonu aynı yaşam döngüsünü sağlar — sürücü yönetimi, HTML raporu, adım zaman çizelgesi, ekran görüntüleri, AI hata analizi, izleme ve flakiness takibi — bağımlılığı eklemenin ötesinde sıfır yapılandırmayla.

---

## Kurulum

```xml title="pom.xml"
<dependency>
    <groupId>io.testfly</groupId>
    <artifactId>testfly</artifactId>
    <version>1.0.0</version>
</dependency>

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
```

Ek bir Surefire yapılandırması gerekmez — Maven Surefire 3.x, JUnit 5'i otomatik algılar.

---

## Seçenek A — `BaseJUnit5Test`'i genişletin

En basit yaklaşım. TestNG'de `BaseTest`'i genişletmekle aynıdır:

```java
class LoginTest extends BaseJUnit5Test {

    @Test
    void validLogin() {
        open();
        step("Enter credentials");
        find("input#username").type("admin");
        find("input#password").type("secret");
        find("button[type='submit']").click();

        step("Verify dashboard", true);
        assertThat(By.id("dashboard")).isVisible();
    }
}
```

`BaseJUnit5Test` şunları sağlar: `getDriver()`, `getWait()`, `open()`, `open(path)`, `$()`, `assertThat()`, `step()`.

---

## Seçenek B — `@EnableTestFly`

Kendi temel sınıfınız için birleşik ek açıklama:

```java
@EnableTestFly
abstract class AppTest {
    protected WebDriver getDriver() { return DriverManager.getDriver(); }
}
```

---

## Seçenek C — Parametre enjeksiyonu

Temel sınıf gerekmez. `WebDriver`'ı doğrudan test metotlarına enjekte edin:

```java
@ExtendWith(TestFlyExtension.class)
class LoginTest {

    @Test
    void validLogin(WebDriver driver) {
        driver.get("https://example.com/login");
        driver.findElement(By.id("username")).sendKeys("admin");
        driver.findElement(By.cssSelector("button[type='submit']")).click();
    }
}
```

---

## @PreCondition — Oturum önbelleği

Adlandırılmış bir kurulum adımını bir kez çalıştırmak ve oturumu (çerezler + localStorage) önbelleğe almak için bir metoda veya sınıfa `@PreCondition` kullanın. Aynı koşul adına sahip sonraki testler, kurulumu tekrarlamak yerine önbelleğe alınmış oturumu geri yükler.

```java
class DashboardTest extends BaseJUnit5Test {

    @Test
    @PreCondition("loginAsAdmin")
    @DisplayName("View dashboard — session restored, no re-login")
    void viewDashboard() {
        open("/dashboard");
        assertThat(By.id("welcome-header")).isVisible();
    }

    @Test
    @PreCondition("loginAsAdmin")
    @DisplayName("Edit profile — same cached session reused")
    void editProfile() {
        open("/profile");
        assertThat(By.id("profile-form")).isVisible();
    }
}
```

Bir sınıfa uygulamak, tüm test metotlarını kapsar:

```java
@PreCondition("loginAsAdmin")
class AdminTest extends BaseJUnit5Test {
    @Test void viewUsers() { ... }
    @Test void viewReports() { ... }
}
```

Koşul sağlayıcı, TestNG ile aynı şekilde SPI üzerinden kayıtlı bir `BaseConditions` alt sınıfında bulunur:

```java
public class AppConditions extends BaseConditions {

    @ConditionProvider("loginAsAdmin")
    public void loginAsAdmin() {
        open("/");
        new LoginPage(getDriver()).login("admin", "secret");
    }
}
```

```
src/test/resources/META-INF/services/io.testfly.precondition.BaseConditions
→ com.yourcompany.conditions.AppConditions
```

Yeniden deneme sırasında, önbelleğe alınmış oturum otomatik olarak geçersiz kılınır ve sağlayıcı yeniden çalışır.

---

## Yeniden deneme

Bir metoda veya sınıfa `@Retryable` kullanın. Framework, her denemede **taze bir sürücüyle** yeniden dener:

```java
class LoginTest extends BaseJUnit5Test {

    @Test
    @Retryable(maxAttempts = 1)   // 1 yeniden deneme = toplam 2 çalıştırma
    void flakyLogin() {
        open();
        new LoginPage(getDriver()).login("admin", "secret");
        assertThat(By.id("dashboard")).isVisible();
    }
}
```

`@Retryable`'ı sınıfa koyarak tüm test metotlarına uygulayın:

```java
@Retryable(maxAttempts = 2)
class FlakyTest extends BaseJUnit5Test { ... }
```

`maxAttempts`, `testfly.yml` içindeki global `retry.maxAttempts` değerini geçersiz kılar. Atlanırsa yapılandırma değeri kullanılır:

```yaml title="testfly.yml"
retry:
  enabled: true
  maxAttempts: 1
```

Yeniden denenmiş testler HTML raporunda bir **↻ Nx** rozeti gösterir.

---

## Paralel yürütme

```properties title="src/test/resources/junit-platform.properties"
junit.jupiter.execution.parallel.enabled=true
junit.jupiter.execution.parallel.mode.default=concurrent
junit.jupiter.execution.parallel.config.strategy=fixed
junit.jupiter.execution.parallel.config.fixed.parallelism=4
```

ThreadLocal sürücü izolasyonu, üç seçeneği de iş parçacığı güvenli yapar.

---

## TestNG ile özellik paritesi

| Özellik | Durum |
|---|---|
| Otomatik sürücü sağlama | ✅ |
| `testfly.yml` yapılandırması | ✅ |
| Paralel yürütme | ✅ |
| `WaitEngine` | ✅ |
| `BasePage` | ✅ |
| `StepLogger` / `step()` | ✅ |
| HTML raporu + adım zaman çizelgesi | ✅ |
| Hata anında ekran görüntüsü | ✅ |
| AI hata analizi | ✅ |
| İz görüntüleyici | ✅ |
| Kendini iyileştiren (self-healing) locator'lar | ✅ |
| Akıcı Locator API'si `$()` | ✅ |
| Web-öncelikli doğrulamalar `assertThat()` | ✅ |
| Ağ taklidi | ✅ |
| Görsel regresyon | ✅ |
| Flakiness tahmini | ✅ |
| Video kaydı | ✅ |
| JUnit XML çıktısı | Yerel |
| `@Retryable` yeniden deneme | ✅ |
| `@PreCondition` oturum önbelleği | ✅ |