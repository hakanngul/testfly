---
id: locators
title: Locator'lar
sidebar_position: 2
description: "Native iOS ve Android öğelerini MobileLocator ile bulun: getBy… sorguları, roller, daraltmalar, strict mode, seçici dizeleri, XPath, iOS class chain ve predicate."
---

# Locator'lar

`MobileLocator`, cihaz ekranındaki öğeler için **tembel (lazy) bir sorgudur**. Bir locator oluşturmak veya daraltmak cihaza dokunmaz. Her eylem, okuma veya doğrulama sorguyu mobilecli'den gelen taze bir görünüm ağacı üzerinde değerlendirir; eylemler ve doğrulamalar öğe hazır olana veya süre dolana kadar yeniden dener.

```java
MobileLocator signIn = screen().getByRole(MobileRole.BUTTON, "Giriş");   // henüz bir şey olmaz
signIn.tap();                                                           // ekranı okur, bekler, dokunur
```

## Öğe bulmak

`screen()` (`BaseMobileTest`, `BaseJUnit5MobileTest`, `MobilePage` veya herhangi bir `MobileSupport` sınıfından) o anki testin cihazının ekranını döndürür.

| Metot | Eşleşen |
|---|---|
| `getByTestId("login")` | iOS accessibility identifier veya Android `resource-id`. Kısa id (`login`) tam id ile de eşleşir (`com.app:id/login`). |
| `getByRole(MobileRole.BUTTON)` | rolün tüm öğeleri (iki platformun native sınıfları, bkz. [Roller](#roles)) |
| `getByRole(MobileRole.BUTTON, "Giriş")`, `getByRole(role, Pattern)` | erişilebilir adı (label, yoksa text) verilen ada eşit olan veya regex'e uyan rol öğeleri |
| `getByText("Kaydet")` | metni (yoksa label, yoksa value) tam olarak `Kaydet` olan öğeler |
| `getByText("kayd", false)` | büyük/küçük harf duyarsız alt dize |
| `getByText(Pattern.compile("^Kay"))` | düzenli ifade (`find()`) |
| `getByLabel("Şifre")`, `getByLabel("şif", false)` | erişilebilirlik etiketi (iOS `label`, Android `content-desc`) |
| `getByPlaceholder("Kullanıcı adı")`, `getByPlaceholder("kull", false)` | placeholder / hint metni |
| `getByType("android.widget.Switch")` | ham native sınıf (`XCUIElementTypeSwitch`, `Switch`, …) |
| `locate("role=button;name=Giriş")` | bir [seçici dizesi](#selector-strings) |

**Hangisini kullanmalı:** önce test id'ler. Cihaz dilinden ve yeniden tasarımlardan etkilenmezler (iOS'ta `accessibilityIdentifier`, Android'de `android:id`). Sonra adıyla birlikte roller, sonra label'lar. Metinler en son: dil ve metin değişikliklerinde bozulurlar.

## Roller {#roles}

`getByRole` bir rolü iki platformun native sınıflarına eşler; aynı sorgu iOS'ta ve Android'de çalışır:

| `MobileRole` | iOS | Android |
|---|---|---|
| `BUTTON` | Button | Button, ImageButton, FloatingActionButton, MaterialButton |
| `TEXTFIELD` | TextField, SecureTextField, SearchField | EditText, TextInputEditText, ReactEditText |
| `TEXT` | StaticText | TextView, MaterialTextView, ReactTextView |
| `IMAGE` | Image | ImageView, ShapeableImageView, ReactImageView |
| `SWITCH` | Switch, Toggle | Switch, ToggleButton |
| `CHECKBOX` | — | CheckBox |
| `RADIO` | — | RadioButton |
| `SLIDER` | Slider | SeekBar |
| `PROGRESSBAR` | ProgressIndicator, ActivityIndicator | ProgressBar |
| `ALERT` | Alert, Sheet | — |
| `COMBOBOX` | Picker, PickerWheel | Spinner |
| `LIST` | Table, CollectionView, ScrollView | ListView, RecyclerView, ReactScrollView |
| `LISTITEM` | Cell, Other | LinearLayout, RelativeLayout |
| `TAB` | Tab, TabBar | Tab, TabBar |
| `LINK` | Link | — |
| `HEADER` | NavigationBar | Toolbar |

## Locator'ı daraltmak

Daraltmalar yeni bir locator döndürür; asıl locator değişmez.

| Metot | Sonuç |
|---|---|
| `first()`, `last()`, `nth(i)` | konuma göre tek eşleşme (0 tabanlı; negatif indeks sondan sayar) |
| `within(container)` | `container` eşleşmesinin içinde kalan eşleşmeler (ağaçta veya, düz listelerde, sınırlara göre) |
| `or(other)` | iki locator'dan herhangi birinin eşleşmeleri, ekran sırasıyla (iki tasarım için yedek) |
| `and(other)` | iki locator'ın da eşleştiği öğeler |
| `filterHasText("Stokta")`, `filterHasText(Pattern)` | alt ağacında metni içeren eşleşmeler (büyük/küçük harf duyarsız) |
| `filterHasNotText("Tükendi")` | alt ağacında metni içermeyen eşleşmeler |
| `filterHas(inner)`, `filterHasNot(inner)` | `inner` öğesi içeren / içermeyen eşleşmeler |
| `withTimeout(Duration.ofSeconds(15))` | eylemleri, okumaları ve doğrulamaları için başka bir bekleme süresiyle aynı sorgu |

```java
MobileLocator rows = screen().getByRole(MobileRole.LISTITEM);
screen().getByRole(MobileRole.BUTTON, "Sil").within(rows.filterHasText("Pixel 8")).tap();
screen().getByTestId("newCheckout").or(screen().getByTestId("checkout")).tap();
```

## Strict mode

Eylemler, bekleyen okumalar (`getText`, `getAttribute`, …) ve tek öğeli doğrulamalar **tam olarak bir** eşleşme ister. Sorgu birden çok öğeyle eşleşirse, eşleşmeleri listeleyen bir `MobileLocatorException` ile hemen hata verirler:

```
Strict mode: getByText("Genel") matched 2 elements for tap. Use first(), last(), nth(i) or a narrower query.
```

Sorguyu daraltın (bir rol, bir test id, `within(...)`) veya `first()`, `last()`, `nth(i)` ile birini seçin. `count()`, `nodes()`, `allTexts()`, `all()`, `toHaveCount(n)` ve `toBeHidden()` tüm eşleşmelere bakar, strict değildir.

## Koddan locator {#locators-in-code}

`MobileLocator`'ın Appium'daki ve web `Locator`'daki gibi adlandırılmış statik fabrikaları da vardır. `screen().getBy…`'den farklı olarak **onları kullanan thread'in cihazına** bağlanırlar; bu yüzden paralel koşularda `static final` alan güvenlidir:

```java
private static final MobileLocator SIGN_IN = MobileLocator.id("com.example:id/btnLogin");
private static final MobileLocator TERMS = MobileLocator.xpath("//*[@text='Koşullar']");

SIGN_IN.tap();
```

| Fabrika | Karşılığı |
|---|---|
| `MobileLocator.id(id)` | `getByTestId(id)` (Appium `id`) |
| `MobileLocator.accessibilityId(id)` | iOS identifier, name veya label; Android content-desc (Appium `accessibilityId`) |
| `MobileLocator.xpath(ifade)` | görünüm ağacı üzerinde [XPath](#xpath) |
| `MobileLocator.iosClassChain(chain)`, `iosPredicate(predicate)` | [class chain / predicate](#class-chain-and-predicate) |
| `MobileLocator.className(type)` | `getByType(type)` |
| `byTestId`, `byText`, `byLabel`, `byPlaceholder`, `byRole(role[, name])` | `getBy…` metotları |
| `MobileLocator.of("text=Giriş")` | bir seçici dizesi |

Açık cihazı olmayan bir thread'de kullanılırlarsa `MobileException` ile hata verirler.

## Seçici dizeleri {#selector-strings}

`@MobileFind`, YAML locator dosyaları ve `screen().locate(...)` öğe başına tek bir dize alır:

| Dize | Karşılığı |
|---|---|
| `LoginScreen.loginButton` (`=` yok) | `getByTestId("LoginScreen.loginButton")` |
| `testid=com.app:id/btnLogin`, `id=com.app:id/btnLogin` | `getByTestId("com.app:id/btnLogin")` |
| `text=Giriş` | `getByText("Giriş")` (tam) |
| `label=Şifre` | `getByLabel("Şifre")` |
| `placeholder=Kullanıcı adı` | `getByPlaceholder("Kullanıcı adı")` |
| `type=XCUIElementTypeSwitch` | `getByType("XCUIElementTypeSwitch")` |
| `role=button`, `role=button;name=Giriş` | `getByRole(MobileRole.BUTTON[, "Giriş"])` |
| `accessibility=loginButton` | `MobileLocator.accessibilityId("loginButton")` |
| `xpath=//android.widget.Button[@text='Giriş']` | XPath |
| ``chain=**/XCUIElementTypeCell[`label == 'Siparişler'`]`` | iOS class chain |
| `predicate=label BEGINSWITH 'Gir'` | iOS NSPredicate |

Bilinmeyen bir anahtar, bilinmeyen bir rol veya boş değer, geçerli biçimleri listeleyen bir `MobileLocatorException` ile hata verir.

## XPath {#xpath}

mobilecli'de XPath yoktur; TestFly XPath 1.0'ı mobilecli'nin döndürdüğü ağaç üzerinde kendisi değerlendirir. Öğeler iOS'ta `XCUIElementType…`, Android'de Android sınıflarıyla (`android.widget.Button`) adlandırılır ve bir `hierarchy` kökü altındadır.

Öznitelikler: `resource-id`, `content-desc`, `text`, `name`, `label`, `value`, `placeholder`, `type`, `class`, `enabled`, `visible`, `selected`, `checked`, `focused`, `x`, `y`, `width`, `height`.

```java
MobileLocator.xpath("//android.widget.Button[@resource-id='com.app:id/save']");
MobileLocator.xpath("//XCUIElementTypeCell[.//XCUIElementTypeStaticText[@label='Siparişler']]");
MobileLocator.xpath("(//android.widget.EditText)[2]");
```

mobilecli etiketsiz kapsayıcı görünümleri atar; bu yüzden Appium Inspector'dan kopyalanan uzun mutlak XPath'ler eşleşmeyebilir. Id, metin veya türe göre XPath'ler eşleşir.

## Class chain ve predicate {#class-chain-and-predicate}

İkisi de TestFly tarafından değerlendirilir (ortak alt kümeler), iOS ve Android ağaçlarında aynı şekilde.

- **Class chain:** `/` ile ayrılmış parçalar, her derinlik için `**/`, bir tür (`XCUIElementType` önekiyle veya öneksiz) ya da `*`, ve ``[`predicate`]`` veya `[n]` filtreleri (1 tabanlı, negatif sondan). Örnek: ``**/XCUIElementTypeCell[`name BEGINSWITH 'order'`][1]``.
- **Predicate:** `==`, `!=`, `BEGINSWITH`, `ENDSWITH`, `CONTAINS`, `LIKE`, `MATCHES`; `[c]` (büyük/küçük harf duyarsız) ve `[d]` (aksan duyarsız) ile; `AND`, `OR`, `NOT` ve parantezlerle birleştirilir. Anahtarlar: `name`, `label`, `value`, `type`, `identifier`, `text`, `placeholderValue` ve durum bayrakları (`enabled`, `visible`, `selected`, …). Örnek: `type == 'XCUIElementTypeButton' AND label CONTAINS[c] 'kaydet'`.

## Seçici bulmak

[Mobile Inspector](inspector) (VS Code ve IntelliJ) ekranı, UI ağacını ve o anki ekrandaki eşleşme sayılarıyla sıralanmış seçicileri gösterir ve bunları `@MobileFind` alanı olarak kopyalar. IDE olmadan `mobilecli dump ui --device <id>` aynı ağacı JSON olarak yazdırır.
