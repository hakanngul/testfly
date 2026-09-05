---
description: "TestFly'da assertThat() ve fluent softAssert() ile web-öncelikli, otomatik yeniden deneyen doğrulamalar."
id: assertions
title: Web-Öncelikli Doğrulamalar (assertThat)
sidebar_label: Doğrulamalar (Assertions)
sidebar_position: 4
---

# Web-Öncelikli Doğrulamalar (Assertions)

TestFly, Playwright'ın `expect()` ve AssertJ kütüphanelerinden esinlenen web-öncelikli assertion'lar sunar. Tüm doğrulamalar, koşul sağlanana veya zaman aşımına uğrayana kadar DOM'u `WebDriverWait` ile otomatik olarak yoklar (poll eder). Doğrulama öncesinde manuel bekleme veya `Thread.sleep()` yazmanıza gerek kalmaz.

```java
import static io.testfly.assertion.SeleniumAssert.assertThat;
// Veya doğrudan BaseTest, BasePage, BaseJUnit5Test ve BaseCucumberSteps içerisinden:
assertThat(By.id("dashboard")).isVisible();
```

---

## Neden Web-Öncelikli Doğrulamalar?

Geleneksel assertion'lar (TestNG `Assert.assertTrue(el.isDisplayed())` veya JUnit `assertTrue`), koşulu tek bir anda hemen değerlendirir. Eleman henüz animasyon aşamasındaysa, veri çekiliyorsa veya render edilmeye devam ediyorsa test gereksiz yere başarısız olur (flaky test).

TestFly'ın `assertThat()` mekanizması:
1. `testfly.yml` dosyasındaki `timeouts.explicit` (varsayılan 10s) süresince **otomatik yeniden dener**.
2. Her doğrulama adımını otomatik olarak [`StepLogger`](file:///src/main/java/io/testfly/steps/StepLogger.java)'a kaydeder ve HTML raporda görselleştirir.
3. Hem Selenium [`By`](file:///src/main/java/io/testfly/assertion/SeleniumAssert.java#L35) hem de akıcı [`Locator`](file:///src/main/java/io/testfly/assertion/SeleniumAssert.java#L43) (`$()`, `getByRole()` vb.) nesneleriyle tam uyumlu çalışır.

---

## Mevcut Matcher'lar

### Görünürlük (Visibility)

```java
assertThat(By.id("welcome-banner")).isVisible();
assertThat(By.cssSelector(".spinner")).isHidden();
```

### Metin (Text)

```java
// Birebir metin eşleşmesi (trimmed)
assertThat(By.tagName("h1")).hasText("Dashboard");

// Alt metin içerme kontrolü
assertThat(By.id("greeting")).containsText("Hoş geldiniz");
```

### Etkileşim ve Durum (Interactability)

```java
assertThat(By.id("submit-btn")).isEnabled();
assertThat(By.id("delete-btn")).isDisabled();
assertThat(By.id("terms-checkbox")).isChecked();
assertThat(By.id("search-input")).isFocused();
```

### Nitelikler & CSS (Attributes & CSS)

```java
// Nitelik değeri eşleşmesi
assertThat(By.name("username")).hasValue("admin");
assertThat(By.id("link")).hasAttribute("target", "_blank");

// Nitelik varlık kontrolü (değerden bağımsız)
assertThat(By.id("btn")).hasAttribute("disabled");

// CSS özellik değeri kontrolü
assertThat(By.id("error-toast")).hasCssValue("color", "rgb(255, 0, 0)");

// CSS class varlığı
assertThat(By.id("item")).hasClass("active");
```

### Eleman Sayısı (Count)

```java
assertThat(By.cssSelector("table tbody tr")).count(5);
assertThat($(".card")).count(3);
```

### Sayfa ve URL Doğrulamaları (PageAssert)

Sayfa başlığı (`title`) ve adresini (`url`) otomatik bekleme (auto-wait) ile doğrulayın — artık ham `getDriver().getTitle()` veya `getCurrentUrl()` ile TestNG/JUnit assertion'larına gerek yok:

```java
// Başlık kontrolleri
assertThat(getDriver()).hasTitle("Dashboard");
assertThat(getDriver()).titleContains("Sauce");
assertThatPage().hasTitle("Products");

// URL kontrolleri
assertThat(getDriver()).hasUrl("https://example.com/dashboard");
assertThat(getDriver()).urlContains("/inventory");
assertThat(getDriver()).urlMatches(".*\\/orders\\/\\d+");

// Özel mesaj ve zaman aşımıyla
assertThat(getDriver())
    .as("Giriş sonrası panele yönlenmeli")
    .within(Duration.ofSeconds(5))
    .urlContains("/dashboard");
```

---

## Özelleştirme ve Niteleyiciler

### Özel Zaman Aşımı (`within`)

Rapor indirme veya büyük dosya yüklemeleri gibi yavaş bileşenler için global `timeouts.explicit` değerini adım bazında geçersiz kılabilirsiniz:

```java
// Duration kullanarak
assertThat(By.id("export-ready-toast"))
    .within(Duration.ofSeconds(30))
    .isVisible();

// Saniye kısayolu ile
assertThat(By.id("quick-tooltip"))
    .within(2)
    .isVisible();
```

### Anlamlı Hata Mesajları (`as`)

Bir doğrulamanın *neden* yapıldığını açıklayan bağlam mesajı ekleyin:

```java
assertThat(By.id("user-avatar"))
    .as("OAuth girişi sonrası profil resmi")
    .isVisible();
// Başarısız olursa: "[OAuth girişi sonrası profil resmi] Expected element to be visible: By.id: user-avatar (timeout: 10s)"
```

---

## Soft Doğrulamalar (`softAssert`)

İlk hatada testin durmasını istemediğiniz, tüm hataları toplayıp test sonunda topluca görmek istediğiniz senaryolarda **Soft Assertions** kullanılır.

TestFly üç farklı akıcı kullanım sunar:

### 1. Doğrudan `softAssert(locator)`

`BaseTest`, `BasePage`, `BaseJUnit5Test` ve `BaseCucumberSteps` içinde yerleşik olarak sunulur:

```java
softAssert(By.id("username")).hasValue("johndoe");
softAssert(By.id("email")).hasValue("john@example.com");
softAssert(By.id("status-badge")).hasText("Active");

// Kullanıcı adı veya e-posta hatalı olsa bile test devam eder.
// Framework test bitiminde tüm hataları topluca raporlar ve testi fail eder.
```

### 2. Akıcı `.softly()` Niteleyicisi

```java
assertThat(By.id("header")).softly().hasText("Dashboard");
assertThat(By.id("avatar")).softly().isVisible();
```

### 3. `softAssert().assertThat(locator)` Üzerinden

```java
softAssert().assertThat(By.id("total")).hasText("100 TL");
```

Geleneksel boolean doğrulamaları da dilediğiniz gibi kullanabilirsiniz:

```java
softAssert().that(items.size() > 0, "Öğe listesi boş olmamalı");
```
