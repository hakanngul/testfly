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
2. Her doğrulama adımını otomatik olarak [`StepLogger`](https://github.com/hakanngul/testfly/blob/development/src/main/java/io/testfly/steps/StepLogger.java)'a kaydeder ve HTML raporda görselleştirir.
3. Hem Selenium [`By`](https://github.com/hakanngul/testfly/blob/development/src/main/java/io/testfly/assertion/SeleniumAssert.java) hem de akıcı [`Locator`](https://github.com/hakanngul/testfly/blob/development/src/main/java/io/testfly/locator/Locator.java) (`$()`, `getByRole()` vb.) nesneleriyle tam uyumlu çalışır.

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

### Form Alanları ve Doğrulama (Form Inputs & Validation)

Input alanlarının görünür metni yoktur, bu yüzden `hasText()` onlarda çalışmaz. Bunun yerine değeri doğrulayın:

```java
// Değer kontrolleri
assertThat(passwordField).hasValue();          // boş olmayan herhangi bir değer
assertThat(searchField).hasEmptyValue();
assertThat(emailField).containsValue("@example.com");
assertThat(phoneField).valueMatches("\\+90 \\d{3} \\d{3} \\d{4}");

// Şifre maskeleme ("şifreyi göster" düğmesi)
assertThat(passwordField).isPasswordHidden();  // type="password"
showPasswordToggle.click();
assertThat(passwordField).isPasswordVisible(); // type artık "password" değil

// Input nitelikleri
assertThat(usernameField).hasType("text").hasPlaceholder("Kullanıcı adı").hasMaxLength(20);
assertThat(usernameField).isRequired();        // required veya aria-required="true"
assertThat(usernameField).isEditable();        // görünür, etkin, readonly değil
assertThat(referenceField).isReadOnly();

// HTML5 kısıt doğrulaması
assertThat(emailField).isInvalid().hasValidationMessage("@");
assertThat(emailField).isValid();
```

`hasValidationMessage()` tarayıcının kendi mesajını kontrol eder. Bu mesaj tarayıcıya ve tarayıcının diline göre değişir.

Kodunuzda bir input'un değerini okumak için `locator.getValue()` kullanın. `getText()` input'larda her zaman `""` döndürür.

### Metin Kalıpları ve Listeler (Text Patterns & Lists)

```java
assertThat(By.id("order")).hasTextMatching("Sipariş #\\d+ alındı");
assertThat(By.id("status")).hasTextIgnoringCase("aktif");

// Eşleşen tüm elemanlar: sıralı / sırasız
assertThat($(".breadcrumb li")).hasTexts("Ana Sayfa", "Ürünler", "Ayakkabı");
assertThat($(".tag")).containsTexts("java", "selenium");
```

### Seçim (Selection)

```java
assertThat(By.id("newsletter")).isNotChecked();
assertThat(By.cssSelector("option[value='tr']")).isSelected();
assertThat(By.id("country")).hasSelectedOption("Türkiye");
assertThat(By.id("country")).hasOptions("Almanya", "Türkiye", "ABD");
```

### Erişilebilirlik ve Kimlik (Accessibility & Identity)

```java
assertThat(By.id("search")).hasRole("searchbox").hasAccessibleName("Ürün ara");
assertThat(By.cssSelector("main > section")).hasId("results");
assertThat(By.id("footer-cta")).isInViewport();
```

### Olumsuz Doğrulamalar (Negations)

Her olumsuz doğrulama ayrı bir metottur. Olumsuz doğrulamalar da elemanın sayfada olmasını ister, yalnızca özelliğin eşleşmemesi gerekir. Eleman yoksa doğrulama tekrar dener ve sonunda başarısız olur. Elemanın olmadığını doğrulamak için `doesNotExist()` veya `isHidden()` kullanın.

```java
assertThat(By.id("status")).doesNotHaveText("Hata");
assertThat(By.id("status")).doesNotContainText("başarısız");
assertThat(By.name("username")).doesNotHaveValue("guest");
assertThat(By.id("submit")).doesNotHaveClass("loading");
assertThat(By.id("submit")).doesNotHaveAttribute("disabled");
assertThat(By.id("email")).doesNotHaveAttribute("aria-invalid", "true");
assertThat(By.id("search")).isNotFocused();
assertThat(By.id("error-banner")).doesNotExist(); // DOM'da eleman yok
```

### Eleman Sayısı ve Varlık (Count & Presence)

```java
assertThat(By.cssSelector("table tbody tr")).count(5);
assertThat($(".card")).count(3);
assertThat($(".card")).countAtLeast(1).countAtMost(10);

assertThat(By.id("csrf-token")).exists();         // DOM'da var, görünür olsun ya da olmasın
assertThat(By.id("error-banner")).doesNotExist(); // DOM'da yok
```

Bir `Locator` (`$()`, `getByRole()`, ...) üzerinde doğrulama yaptığınızda yalnızca görünür elemanlar eşleşir. Bu yüzden `count`, `exists` ve `doesNotExist` gizli elemanları saymaz. Gizli elemanların da sayılması gerekiyorsa `By` verin.

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

// Alert'ler (alert açık bırakılır)
assertThatPage().hasAlert().hasAlertText("Başarıyla kaydedildi");
assertThatPage().hasNoAlert();

// Cookie'ler ve localStorage
assertThatPage().hasCookie("session").hasCookie("locale", "tr");
assertThatPage().doesNotHaveCookie("tracking");
assertThatPage().hasLocalStorageItem("theme", "dark");

// Pencereler / sekmeler
assertThatPage().hasWindowCount(2);
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

---

## Doğrulama Sınırları: İlkel Veri Kontrolleri (Primitive Assertions)

TestFly'ın `assertThat()` mekanizması, otomatik bekleme (polling) ve yeniden deneme özellikleriyle **Web ve DOM öğelerine** özel olarak odaklanmıştır.

Standart Java ilkel veri kontrolleri (örneğin `assertEquals(user.getAge(), 30)` veya liste boyutu kontrolleri) için TestFly ile birlikte **AssertJ** veya test çalıştırıcınızın dahili assertion kütüphanelerini (`TestNG` / `JUnit 5`) kullanmanız tavsiye edilir. TestFly, API şişkinliğini önlemek ve mevcut Java test ekosistemiyle mükemmel uyum sağlamak için genel amaçlı doğrulama araçlarını baştan icat etmez.
