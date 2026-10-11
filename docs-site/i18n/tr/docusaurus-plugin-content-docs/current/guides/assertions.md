---
description: "TestFly'da assertThat() ve fluent softAssert() ile web-öncelikli, otomatik yeniden deneyen doğrulamalar."
id: assertions
title: Web-Öncelikli Doğrulamalar (assertThat)
sidebar_label: Doğrulamalar (Assertions)
sidebar_position: 4
---

# Web-Öncelikli Doğrulamalar (Assertions)

TestFly, Playwright'ın `expect()` ve AssertJ kütüphanelerinden esinlenen web-öncelikli assertion'lar sunar. DOM ve sayfa doğrulamalarının tümü, koşul sağlanana veya zaman aşımına uğrayana kadar `WebDriverWait` ile otomatik olarak yoklar (poll eder). İstisna [AI doğrulamalarıdır](#semantic-ai-assertions-satisfiesai--violatesai): bunlar yoklama yapmaz. Doğrulama öncesinde manuel bekleme veya `Thread.sleep()` yazmanıza gerek kalmaz.

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
3. Hem Selenium [`By`](https://github.com/hakanngul/testfly/blob/development/src/main/java/io/testfly/assertion/SeleniumAssert.java) hem de akıcı [`Locator`](https://github.com/hakanngul/testfly/blob/development/src/main/java/io/testfly/locator/Locator.java) (`$()`, `getByRole()` vb.) nesnelerini kabul eder.

### Hangi eleman kontrol edilir

- `count`, hem `By` hem `Locator` için görünür veya gizli tüm eşleşmeleri sayar (`Locator.count()` ile aynı sayı).
- Diğer matcher'lar tek bir elemanı kontrol eder. `By` ile bu ilk eşleşmedir. `Locator` ile zincirin tamamı (`filter`, `withText`, `nth`, ...) her yoklamada yeniden çözülür ve ilk **görünür** eşleşme kontrol edilir; görünür eşleşme yoksa ilk eşleşme kontrol edilir. Böylece gizli kopyalar (örneğin mobil ve masaüstü menü) görünür olanı gölgelemez, `input[type=hidden]` gibi gizli elemanlar da `hasValue` veya `hasAttribute` ile kontrol edilebilir.
- Doğrulamalar `locators.strict` ayarını uygulamaz: birden fazla eşleşme hata sayılmaz.

---

## Mevcut Matcher'lar

### Görünürlük (Visibility)

```java
assertThat(By.id("welcome-banner")).isVisible();
assertThat(By.cssSelector(".spinner")).isHidden();
```

### Metin (Text)

```java
// Elemanın metniyle birebir eşleşme; baştaki ve sondaki boşluklar kırpılır
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

`isEnabled` ve `isDisabled` elemanın görünür olmasını da şart koşar; gizli bir eleman `actual: [not displayed]` ile başarısız olur. `isChecked`, yerel checkbox, radio button veya `<option>` elemanının seçili durumunu okur.

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
assertThat($(".card")).count(3);   // gizli kartlar da sayılır
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
// Başarısız olursa: "[OAuth girişi sonrası profil resmi] Expected element to be visible: By.id: user-avatar (actual: [not found], timeout: 10s)"
```

`describedAs(...)`, `as(...)` ile aynıdır. Sayfa doğrulamaları (`assertThat(getDriver())`, `assertThatPage()`) bunun yerine mevcut başlık ve URL'yi raporlar: `(actual title: [...], actual url: [...], timeout: ...)`.

### `actual` ne raporlar

Her hata `(actual: [...], timeout: ...)` ile biter ve polling sırasında gözlenen son değeri gösterir:

| Doğrulama | `actual` gösterir |
|---|---|
| `hasText`, `containsText` | elemanın metni, tırnak içinde, tek satır (en fazla 200 karakter) |
| `hasValue` | tırnak içinde değer, değer yoksa `null` |
| `hasAttribute(name, value)` | `name="..."` veya `attribute 'name' absent` |
| `hasAttribute(name)` | `attribute 'name' absent` |
| `hasCssValue` | `property="..."` |
| `hasClass` | `class="..."` |
| `isVisible`, `isHidden` | `not displayed`, `displayed` |
| `isEnabled`, `isDisabled` | `not displayed`, `disabled`, `enabled` |
| `isChecked` | `not selected` |
| `isFocused` | `focused element: <input#other>` |
| `count` | bulunan eleman sayısı, örneğin `2 element(s)` |

Tek elemanlı matcher'ların tümü, eşleşme yoksa `not found`, kontrol sırasında sayfa elemanı değiştirdiyse `element went stale` raporlar.

Bir `Locator` çözülemezse, örneğin `$(".row").within(By.id("grid"))` kapsayıcısı hiç görünmezse veya `nth(5)` aralık dışındaysa, doğrulama `LocatorException` yerine normal bir `AssertionError` ile başarısız olur (`actual: [locator failed: ...]`).

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

Sayfa durumu da aynı şekilde çalışır: `softAssert(getDriver()).hasTitle("Dashboard")` veya `softAssertPage().urlContains("/orders")`.

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

## Semantik AI Doğrulamaları (`satisfiesAi` & `violatesAi`) {#semantic-ai-assertions-satisfiesai--violatesai}

Karmaşık sayfa durumları, dinamik mesajlar veya birebir metin eşleşmesinin kırılgan kaldığı görsel akışlar için TestFly, LLM ile değerlendirilen semantik doğrulamalar sunar:

```java
// Sayfa düzeyinde semantik kontrol
assertThatPage().satisfiesAi("Sepet özeti toplam fiyatı ve en az 1 ürünü gösteriyor");
assertThatPage().violatesAi("Hata banner'ı, erişim reddi veya 500 sunucu uyarısı");

// BaseTest/BasePage içindeki kısayol
assertWithAi("Kullanıcı profili aktif abonelik durumunu gösteriyor");

// Eleman düzeyinde semantik kontrol
assertThat(find(".status-pill")).satisfiesAi("Başarılı bir ödemeyi gösteriyor");
```

Tüm ayrıntılar, aşırı istek (throttle) korumaları ve örnekler için [Agentic Testing & Otonom AI Rehberi](../ai/agentic-testing#2-semantik-doğrulamalar-satisfiesai--violatesai)'ne bakın.

---

## Doğrulama Sınırları: İlkel Veri Kontrolleri (Primitive Assertions)

TestFly'ın `assertThat()` mekanizması, otomatik bekleme (polling) ve yeniden deneme özellikleriyle **Web ve DOM öğelerine** özel olarak odaklanmıştır.

Standart Java ilkel veri kontrolleri (örneğin `assertEquals(user.getAge(), 30)` veya liste boyutu kontrolleri) için TestFly ile birlikte **AssertJ** veya test çalıştırıcınızın dahili assertion kütüphanelerini (`TestNG` / `JUnit 5`) kullanmanız tavsiye edilir. TestFly, API şişkinliğini önlemek ve mevcut Java test ekosistemiyle mükemmel uyum sağlamak için genel amaçlı doğrulama araçlarını baştan icat etmez.
