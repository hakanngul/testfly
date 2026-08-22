---
description: "Tüm Selenium testleri için bir kez giriş yapın: @PreCondition, setup'ı thread başına bir kez çalıştırır ve oturumu (çerezler ve localStorage) her test için önbelleğe alır."
id: precondition
title: "@PreCondition"
sidebar_position: 12
---

# @PreCondition

`@PreCondition`, adlandırılmış bir sağlayıcı metodunu thread başına bir kez çalıştırarak, oturum durumunu (çerezler + localStorage) önbelleğe alarak ve sonraki testler için otomatik olarak geri yükleyerek (giriş gibi) tekrarlanan setup standart kodunu ortadan kaldırır.

---

## Çözdüğü sorun

`@PreCondition` olmadan:

```java
@BeforeMethod
public void login() {
    open("/login");
    new LoginPage(getDriver()).login("admin", "secret");
    // HER testten önce çalışır — yavaş ve kırılgan
}
```

`@PreCondition` ile:

```java
@Test
@PreCondition("loginAsAdmin")
public void viewDashboard() {
    open("/dashboard");  // oturum zaten kurulmuş
}

@Test
@PreCondition("loginAsAdmin")
public void editProfile() {
    open("/profile");    // oturum önbellekten geri yüklendi — yeniden giriş yok
}
```

---

## Adım 1 — Bir koşul sağlayıcısı oluşturun

`BaseConditions` sınıfını genişletin ve metotları `@ConditionProvider` ile işaretleyin:

```java
import io.testfly.precondition.BaseConditions;
import io.testfly.precondition.ConditionProvider;
import org.openqa.selenium.By;

public class AppConditions extends BaseConditions {

    @ConditionProvider("loginAsAdmin")
    public void loginAsAdmin() {
        open("/login");
        type(By.id("username"), "admin");
        type(By.id("password"), "admin123");
        click(By.id("submit"));
    }

    @ConditionProvider("loginAsUser")
    public void loginAsUser() {
        open("/login");
        type(By.id("username"), "testuser");
        type(By.id("password"), "user123");
        click(By.id("submit"));
    }

    @ConditionProvider("acceptCookies")
    public void acceptCookies() {
        open("/");
        click(By.id("accept-all-cookies"));
    }
}
```

---

## Adım 2 — Java SPI ile kaydedin

SPI dosyasını oluşturun:

```
src/main/resources/META-INF/services/io.testfly.precondition.BaseConditions
```

İçeriği:

```
com.example.AppConditions
```

---

## Adım 3 — Testlerinizi işaretleyin

```java
@Test
@PreCondition("loginAsAdmin")
public void viewDashboard() { ... }

@Test
@PreCondition("loginAsUser")
public void viewProfile() { ... }

// Birden fazla koşul
@Test
@PreCondition({"loginAsAdmin", "acceptCookies"})
public void adminWithCookies() { ... }
```

---

## Önbelleğe alma nasıl çalışır

```
Test 1 — @PreCondition("loginAsAdmin")
  → Önbellek yok → loginAsAdmin() çalışır → çerezleri + localStorage'ı önbelleğe alır

Test 2 — @PreCondition("loginAsAdmin")
  → Önbellek isabeti → çerezleri + localStorage'ı geri yükler → girişi atlar

Test 3 — @PreCondition("loginAsAdmin") [retry]
  → Önbellek retry'da geçersiz kılınır → loginAsAdmin() yeniden çalışır → yeniden önbelleğe alır
```

Önbellek **thread başınadır** — paralel yürütme için güvenlidir. Her thread, kendi oturum önbelleğini bağımsız olarak korur.

---

## Birden fazla giriş senaryosu

Farklı roller için farklı koşul adları kullanın:

```java
@ConditionProvider("loginAsAdmin")
public void loginAsAdmin() { login("admin", "admin123"); }

@ConditionProvider("loginAsManager")
public void loginAsManager() { login("manager", "mgr456"); }

@ConditionProvider("loginAsReadOnly")
public void loginAsReadOnly() { login("viewer", "view789"); }

private void login(String user, String pass) {
    open("/login");
    type(By.id("username"), user);
    type(By.id("password"), pass);
    click(By.id("submit"));
}
```

---

## Programatik kayıt

SPI kullanmak istemiyorsanız:

```java
import io.testfly.precondition.PreConditionRegistry;

// Bir @BeforeSuite veya TestFlyPlugin.onLoad() içinde
PreConditionRegistry.register(new AppConditions());
```