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

### Doğrulanmış Tıklama (Verified Click)

`assertThat(...).click()` elemana tıklar ve tıklamanın gerçekten elemana ulaştığını doğrular:

```java
assertThat(headerLoginButton).click();
assertThat(submitButton).as("Siparişi gönder").click();
```

Arka planda şunları yapar:

1. Elemanı tam olarak `locator.click()` gibi bulur: chain filtreleri (`nth`, `filter`, `withText`) ve self-healing dahil. Ardından görünür ve etkin olana kadar bekler.
2. Elemanı ekranın ortasına kaydırır ve mouse'u üzerine götürür. Uzun bir formun en altındaki submit butonu önce görünür hale getirilir.
3. Üstünü kapatan bir şey (cookie banner, modal, sticky header) kalmayana kadar bekler.
4. Tıklar ve `click` event'inin elemana ulaştığını kontrol eder.
5. Rapora geçen (PASS) bir `Clicked: ...` adımı yazar.

Bir adım başarısız olursa hata mesajı hangisi olduğunu söyler, örneğin:

```text
Click not performed: element not visible and enabled: By.id: login (No element found for: By.id: login)
Click not performed: By.id: login is covered by <div class='cookie-banner'> (timeout: 10s)
Click did not reach By.id: login (no click event was received)
```

Elemanı bulma adımı, `locator.click()` gibi `timeouts.explicit` süresini kullanır. `within(...)` sonraki adımlara uygulanır, örneğin üstü kapatan elemanın kalkmasını beklemeye.

Soft modda (`softly()` / `softAssert(...)`) hata kaydedilir ve test devam eder. Tıklama başka bir sayfaya geçerse ya da alert açarsa tıklama ulaşmış sayılır ve alert açık bırakılır.

`assertThat(...).click()` yalnızca tıklamanın elemana ulaştığını kanıtlar. Uygulamanın tepki verdiğini kanıtlamak için sonucu doğrulayın, örneğin `assertThatPage().urlContains("/login")` veya `assertThat(loginModal).isVisible()`. Normal `locator.click()` aksiyonu değişmedi.

### Doğrulanmış Yazma ve Temizleme (Verified Fill & Clear)

`assertThat(...).fill(text)` alana yazar ve değerin gerçekten oturduğunu doğrular. `assertThat(...).clear()` aynısını boş değer için yapar:

```java
assertThat(userNameField).fill("admin");
assertThat(passwordField).fill(System.getenv("APP_PASSWORD"));
assertThat(searchField).clear();
```

Arka planda şunları yapar:

1. Elemanı `locator.fill()` gibi bulur (chain filtreleri, self-healing) ve görünür ve etkin olana kadar bekler.
2. `readonly` olmayana kadar bekler, sonra elemanı ekranın ortasına kaydırır.
3. Alanı temizler ve metni yazar, tıpkı `locator.fill()` gibi.
4. Değer metne eşit olana kadar bekler ve kısa bir süre sonra tekrar kontrol eder. Böylece `maxlength`, input maskesi ya da uygulamanın kendi state'i (React, Vue, ...) yüzünden yazıldıktan sonra değişen veya silinen değerler yakalanır.
5. Rapora geçen bir `Filled: ... with [admin]` veya `Cleared: ...` adımı yazar.

Örnek hata mesajları:

```text
Fill not performed: element is read-only: By.id: username (timeout: 10s)
Fill did not stick for By.id: code: expected value [abcdef] but was [abc] (timeout: 10s)
Fill did not stick for By.id: password: expected 7 characters but got 0 (password value hidden) (timeout: 10s)
```

`type="password"` alanlarda değer ne raporda ne de hata mesajlarında görünür, yalnızca uzunluğu yazılır.

Değer birebir eşleşmelidir. Girdiyi yeniden biçimlendiren bir alan, örneğin `5321234567` değerini `(532) 123 45 67` yapan bir telefon maskesi, bu doğrulamada başarısız olur. Böyle alanlarda `locator.fill(...)` kullanıp ardından `assertThat(field).hasValue("(532) 123 45 67")` ile doğrulayın.

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
assertThat(By.id("newsletter")).not().isChecked();
assertThat(By.id("select-all")).isIndeterminate();
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

### `not()` ile Olumsuzlama (Negation)

`not()` bir sonraki doğrulamayı tersine çevirir, tıpkı Playwright'taki `not()` gibi:

```java
assertThat(By.id("status")).not().hasText("Hata");
assertThat(By.id("status")).not().containsText("başarısız");
assertThat(By.name("username")).not().hasValue("guest");
assertThat(By.id("submit")).not().containsClass("loading");
assertThat(By.id("submit")).not().hasAttribute("disabled");
assertThat(By.id("email")).not().hasAttribute("aria-invalid", "true");
assertThat(By.id("search")).not().isFocused();
assertThat(By.id("newsletter")).not().isChecked();
assertThat(By.id("error-banner")).not().isAttached(); // DOM'da eleman yok
```

- `not()` yalnızca hemen arkasından gelen doğrulamaya uygulanır. `assertThat(x).not().isVisible().hasText("a")` zincirinde `hasText` tersine çevrilmez.
- Playwright'taki gibi, olumsuzlanan doğrulamaların çoğu eşleşen eleman yoksa da geçer. Elemanın var olmasını da istiyorsanız önce onu doğrulayın: `assertThat(status).isVisible().not().hasText("Hata")`.
- `not()`, `click()`, `fill()` veya `clear()` ile birlikte kullanılamaz.
- Hata mesajları ve rapor adımları olumsuzlamayı gösterir: `Expected NOT text [Hata] for: ...`, `[not] Assert text 'Hata' ...`.

### Eleman Sayısı ve Varlık (Count & Presence)

```java
assertThat(By.cssSelector("table tbody tr")).count(5);
assertThat($(".card")).count(3);
assertThat($(".card")).countAtLeast(1).countAtMost(10);

assertThat(By.id("csrf-token")).isAttached();          // DOM'da var, görünür olsun ya da olmasın (diğer adı: exists())
assertThat(By.id("error-banner")).not().isAttached(); // DOM'da yok
```

Bir `Locator` (`$()`, `getByRole()`, ...) üzerinde doğrulama yaptığınızda yalnızca görünür elemanlar eşleşir. Bu yüzden `count`, `hasCount`, `exists` ve `isAttached` gizli elemanları saymaz. Gizli elemanların da sayılması gerekiyorsa `By` verin.

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

## Playwright Eşliği (Playwright Parity)

TestFly'ın eleman doğrulamaları Playwright'ın JavaScript isimlerini (`expect(locator).toBeVisible()`) değil, Playwright for Java isimlerini (`assertThat(locator).isVisible()`) izler:

| Playwright JS | TestFly |
|---|---|
| `expect(l).toBeAttached()` | `assertThat(l).isAttached()` |
| `toBeChecked()` / `toBeChecked({indeterminate: true})` | `isChecked()` / `isIndeterminate()` |
| `toBeDisabled()` / `toBeEnabled()` / `toBeEditable()` | `isDisabled()` / `isEnabled()` / `isEditable()` |
| `toBeEmpty()` | `isEmpty()` |
| `toBeFocused()` | `isFocused()` |
| `toBeHidden()` / `toBeVisible()` | `isHidden()` / `isVisible()` |
| `toBeInViewport({ratio})` | `isInViewport()` / `isInViewport(ratio)` |
| `toContainText(text \| regex \| array)` | `containsText(String \| Pattern \| String[])` |
| `toContainClass(...)` | `containsClass(String \| List<String>)` |
| `toHaveAccessibleName/Description/ErrorMessage` | `hasAccessibleName` / `hasAccessibleDescription` / `hasAccessibleErrorMessage` (`String` veya `Pattern`) |
| `toHaveAttribute(name, value \| regex)` | `hasAttribute(name, String \| Pattern)` |
| `toHaveClass(regex)` | `hasClass(Pattern)` |
| `toHaveCount(n)` | `hasCount(n)` |
| `toHaveCSS(name, value \| regex)` | `hasCSS(name, String \| Pattern)` |
| `toHaveId(...)` | `hasId(String \| Pattern)` |
| `toHaveJSProperty(name, value)` | `hasJSProperty(name, value)` |
| `toHaveRole(role)` | `hasRole(Role.BUTTON)` / `hasRole("button")` |
| `toHaveText(text \| regex \| array)` | `hasText(String \| Pattern \| String[] \| Pattern[])` |
| `toHaveValue(...)` / `toHaveValues([...])` | `hasValue(String \| Pattern)` / `hasValues(...)` |
| `.not` | `.not()` |

`isEmpty()`, `input`, `textarea` ve `select` için değeri (yalnızca boşluklardan oluşan değer boş sayılmaz), diğer elemanlar için kırpılmış metin içeriğini kontrol eder.

`Pattern` argümanları, Playwright'taki regex'ler gibi değerin herhangi bir yerinde eşleşir. Tam eşleşme için `^...$` kullanın: `hasText(Pattern.compile("^Sipariş #\\d+$"))`.

**Playwright'tan farklar:**

- `hasClass(String)` tek bir class adına bakar; Playwright buna `containsClass` der. Tüm `class` attribute'unu eşleştirmek için `hasClass(Pattern)` kullanın.
- Playwright'ın options nesnelerinin karşılığı yoktur. `timeout` için `within(...)`, `visible: false` / `enabled: false` / `checked: false` için `not()`, `ignoreCase` için `hasTextIgnoringCase(...)` kullanın.
- `toMatchAriaSnapshot` ve eleman ekran görüntüleri yoktur. Sayfa ekran görüntüleri için görsel test bölümüne bakın.

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
