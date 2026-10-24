---
id: locators-and-page-objects
title: Locator'lar ve Page Object'ler
sidebar_position: 2
description: "MobileLocator sorguları, seçici dizeleri, @MobileFind ve @WaitFor ile MobilePage page object'leri, otomatik bekleyen eylemler ve expect() doğrulamaları."
---

# Locator'lar ve Page Object'ler

## Locator'lar

`screen()` locator döndürür. Locator tembel bir sorgudur: oluşturmak cihaza dokunmaz; her eylem veya okuma onu taze bir görünüm ağacı üzerinde değerlendirir.

| Metot | Eşleştiği |
|---|---|
| `getByRole(MobileRole.BUTTON)`, `getByRole(role, "name")` | rolün iki platformdaki native sınıfları; `name` etiket, yoksa metindir |
| `getByText("Save")`, `getByText("sav", false)`, `getByText(Pattern)` | metin, yoksa etiket, yoksa değer (tam, büyük/küçük harf duyarsız alt dize veya regex) |
| `getByLabel(...)` | erişilebilirlik etiketi |
| `getByTestId("login")` | iOS accessibility identifier veya Android resource-id (`login`, `com.app:id/login` ile de eşleşir) |
| `getByPlaceholder(...)` | placeholder metni |
| `getByType("android.widget.Switch")` | ham native sınıf |
| `locate("role=button;name=Sign in")` | bir [seçici dizesi](#selector-strings) |

`first()`, `last()`, `nth(i)`, `within(container)`, `or(other)`, `and(other)`, `filterHasText(...)`, `filterHasNotText(...)`, `filterHas(locator)`, `filterHasNot(locator)` ve `withTimeout(Duration)` ile daraltın.

### Koddan locator

`MobileLocator`'da Appium ve web `Locator` adlarıyla statik fabrikalar vardır: `id(...)`, `accessibilityId(...)`, `xpath(...)`, `iosClassChain(...)`, `iosPredicate(...)`, `className(...)`, `byTestId`, `byText`, `byLabel`, `byPlaceholder`, `byRole(role[, name])` ve `of(seçiciDizesi)`. Her kullanımda o anki testin cihazına bağlanırlar; bu yüzden `static final` alan paralel koşularda güvenlidir. Açık cihazı olmayan bir thread'de kullanılırsa `MobileException` verirler.

```java
private static final MobileLocator SIGN_IN = MobileLocator.xpath("//*[@text='Sign in']");
SIGN_IN.tap();
```

Test id'lerini tercih edin (iOS'ta `accessibilityIdentifier`, Android'de `resource-id`): cihaz diliyle değişmezler. Ardından isimli roller, en son metinler gelir.

## Seçici dizeleri {#selector-strings}

`@MobileFind` ve `screen().locate(...)` öğe başına bir dize alır:

| Dize | Karşılığı |
|---|---|
| `LoginScreen.loginButton` (`=` yok) | `getByTestId("LoginScreen.loginButton")` |
| `testid=com.app:id/btnLogin` | `getByTestId("com.app:id/btnLogin")` |
| `text=Sign in` | `getByText("Sign in")` (tam) |
| `label=Password` | `getByLabel("Password")` |
| `placeholder=Username` | `getByPlaceholder("Username")` |
| `type=XCUIElementTypeSwitch` | `getByType("XCUIElementTypeSwitch")` |
| `role=button` / `role=button;name=Sign in` | `getByRole(MobileRole.BUTTON[, "Sign in"])` |
| `id=com.app:id/btnLogin` | `testid=` ile aynı (Appium `id`) |
| `accessibility=loginButton` | iOS identifier, name veya label; Android content-desc (Appium `accessibilityId`) |
| `xpath=//android.widget.Button[@text='Sign in']` | görünüm ağacı üzerinde XPath 1.0 |
| ``chain=**/XCUIElementTypeCell[`label == 'Orders'`]`` | iOS class chain (ortak alt küme) |
| `predicate=label BEGINSWITH 'Sign'` | iOS NSPredicate (ortak alt küme) |

XPath, class chain ve predicate, mobilecli'nin döndürdüğü ağaç üzerinde TestFly içinde çalışır; mobilecli'de bu sorgular yoktur. Sınırlar:

- **XPath:** öğeler `XCUIElementType…` (iOS) veya Android sınıf adıyla, bir `hierarchy` kökü altında adlandırılır. Öznitelikler: `resource-id`, `content-desc`, `text`, `name`, `label`, `value`, `placeholder`, `type`, `class`, `enabled`, `visible`, `selected`, `checked`, `focused`, `x`, `y`, `width`, `height`. mobilecli etiketsiz kapsayıcıları atar; Appium Inspector'dan kopyalanan tam yollu XPath'ler eşleşmeyebilir, id, metin veya tipe dayalı olanlar eşleşir.
- **Class chain:** `/` ile ayrılmış adımlar, her derinlik için `**/`, bir tip (`XCUIElementType` önekli veya öneksiz) ya da `*`, ve ``[`predicate`]`` veya `[n]` (1 tabanlı, negatif sondan) filtreleri.
- **Predicate:** `==`, `!=`, `BEGINSWITH`, `ENDSWITH`, `CONTAINS`, `LIKE`, `MATCHES` ve `[c]`/`[d]`; `AND`, `OR`, `NOT` ve parantezle birleşir; `name`, `label`, `value`, `type`, `identifier`, `text`, `placeholderValue` ve durum bayrakları üzerinde.

Bilinmeyen bir anahtar, bilinmeyen bir rol veya boş değer, geçerli biçimleri listeleyen bir `MobileLocatorException` verir.

## Page object'ler {#page-objects}

Sayfalar `MobilePage`'den türer. `@MobileFind` alanı bir öğenin iOS ve Android seçicisini birlikte tutar; Appium'daki `@iOSXCUITFindBy` ve `@AndroidFindBy` çiftine benzer. Alanları private tutun, testlere niyet metotları verin; ekranı değiştiren metot bir sonraki sayfayı döndürür.

```java
public class HomePage extends MobilePage {
    @MobileFind(ios = "HomeScreen.loginButton", android = "com.example:id/btnHome")
    @WaitFor                                    // new HomePage() görünene kadar bekler
    private MobileLocator loginButton;

    @MobileFind(ios = "cookieBannerAcceptAllButton")
    private MobileLocator cookieAccept;

    @Override
    protected void onLoad() {                   // @WaitFor alanları göründükten sonra çalışır
        tapIfShown(cookieAccept);
    }

    public LoginPage openLogin() {
        loginButton.tap();
        return new LoginPage();
    }
}

public class LoginPage extends MobilePage {
    @MobileFind(ios = "LoginScreen.nameTextField", android = "com.example:id/etUser")
    @WaitFor
    private MobileLocator username;

    @MobileFind("role=button;name=Sign in")    // value: iki platformda aynı seçici
    private MobileLocator signIn;

    public LoginPage signIn(String user) { username.fill(user); signIn.tap(); return this; }
    public void shouldBeClosed() { expect(username).toBeHidden(); }
}

new HomePage().openLogin().signIn("ada").shouldBeClosed();
```

`new SomePage()` sırasıyla şunları yapar:

1. testin cihazını alır (`MobilePage(MobileScreen)` ekranı açıkça alır, örneğin test runner dışında);
2. üst sınıflar dahil her `@MobileFind` alanına koşan platformun locator'ını atar: `ios`/`android`, yoksa `value`;
3. her `@WaitFor` alanının görünmesini `timeouts.actionMs` içinde bekler;
4. `onLoad()` çağırır.

Kurallar:

- `@MobileFind` alanlarına başlangıç değeri (`= null`) vermeyin: Java alt sınıf alan başlatıcılarını temel kurucudan sonra çalıştırır ve locator'ı ezer. `onLoad()` bu başlatıcılardan önce çalışır.
- Koşan platform için seçicisi olmayan alan yine bir locator alır, ama kullanıldığında hata verir ("LoginPage.username: no android selector"). Böylece sayfalar yalnızca bir platformda olan öğeleri tutabilir.
- Alanlar statik olmayan `MobileLocator` olmalıdır; başka bir tip sayfa oluşturulurken hata verir.
- `tapIfShown(locator)` şu an görünen bir öğeye dokunur ve kaybolmasını bekler; öğe görünmüyorsa beklemeden `false` döner.
- Sayfa oluşturmak ekranını bekler. Cucumber'da sayfa örneklerini adımlar arasında step sınıfının alanlarında tutun; ekranı çoktan gitmiş bir sayfayı yeniden oluşturmayın.
- Seçici dizesi yerine bir alan, Appium'daki `@iOSXCUITFindBy(accessibility = …)` gibi platform başına bir tipli öznitelik kullanabilir: `iosAccessibility`, `iosId`, `iosXpath`, `iosClassChain`, `iosPredicate`, `iosClassName`, `androidAccessibility`, `androidId`, `androidXpath`, `androidClassName`. Bir platform için iki tane vermek (örneğin `android` ve `androidId`) sayfa oluşturulurken hata verir.

  ```java
  @MobileFind(iosAccessibility = "LoginScreen.loginButton", androidId = "com.example:id/btnLogin")
  private MobileLocator login;
  ```
- Düz sınıflar da çalışır: `page(Type.class)` (`MobileSupport`'tan) veya `MobilePages.init(page, screen)`, `@MobileFind` alanlarını bekleme ve `onLoad()` olmadan doldurur.

Örnek sayfalar `src/test/java/io/testfly/examples/mobile/HomePage.java` ve `LoginPage.java` dosyalarıdır.

## YAML locator dosyaları {#yaml-locators}

Locator'lar bir YAML dosyasında da durabilir; iki platform için tek dosya. Değerler [seçici dizeleridir](#selector-strings):

```yaml
# src/test/resources/mobile-locators/login.yml
login:
  username:
    ios: LoginScreen.nameTextField
    android: com.example:id/etUsername
  signIn: role=button;name=Sign in     # düz değer: iki platform
  welcome:
    mobile: text=Welcome back           # mobile: iki platform
```

```java
MobileLocators locators = MobileLocators.load("mobile-locators/login.yml");   // önce classpath, yoksa dosya yolu
locators.get("login.username").fill("ada");                                    // koşan platformun seçicisi
```

- Gruplar iç içe olabilir; anahtarlar noktayla birleşir (`login.username`). Yalnızca `ios`, `android` ve `mobile` anahtarları olan bir map tek bir locator'dır; `ios`/`android`, `mobile`'a göre önceliklidir.
- Dosya yüklenirken her seçici kontrol edilir; geçersiz olan, anahtarını belirterek hata verir. Bilinmeyen bir anahtar, anahtarları listeleyerek hata verir.
- Koşan platform için seçicisi olmayan anahtar, `@MobileFind`'taki gibi kullanıldığında hata veren bir locator döndürür.
- `get(key)` testin cihazını kullanır; `get(key, screen)` ekranı açıkça alır. Dosyayı bir kez yükleyip paylaşın (örneğin bir `static final` alanda): cihaz durumu tutmaz.
- Örnek: `src/test/resources/mobile-locators/login.yml`.

## Ön koşullar (precondition) {#preconditions}

Login gibi ortak hazırlık her testte tekrarlanmak yerine bir ön koşulda durur. Sağlayıcıyı page object'lerle bir `MobileConditions` alt sınıfında yazın ve `@PreCondition` ile isteyin:

```java
public class LoginConditions extends MobileConditions {
    @ConditionProvider("loggedIn")
    public void loggedIn() {
        new HomePage().openLogin().signIn(System.getenv("MOBILE_LOGIN_USER"), System.getenv("MOBILE_LOGIN_PASSWORD"));
    }
}

@PreCondition("loggedIn")
@Test
public void showsTheBalance() { /* giriş yapılmış olarak başlar */ }
```

- Sağlayıcıları `src/test/resources/META-INF/services/io.testfly.mobile.MobileConditions` dosyasına (satır başına bir sınıf adı) kaydedin ya da suite'ten önce `MobileConditions.register(new LoginConditions())` çağırın.
- Ne zaman çalışır: uygulama durumu tarayıcı çerezleri gibi saklanamaz. `mobile.app.reset: none` ile uygulama durumunu korur, bu yüzden koşul cihaz başına bir kez çalışır; `relaunch` veya `clearData` ile onu isteyen her testten önce çalışır. Yeniden denemede (retry) her zaman tekrar çalışır.
- TestNG `@PreCondition`'ı test metodundan okur; JUnit 5 metottan, sonra sınıftan. Cucumber okumaz: `Background` kullanın.
- Mobil bir testte `@PreCondition` adları yalnızca `MobileConditions` sağlayıcılarına bakar; web `BaseConditions` sağlayıcıları tarayıcı testleri içindir.
- Örnek: `src/test/java/io/testfly/examples/mobile/LoginConditions.java`.

## Eylemler ve doğrulamalar

- **Eylemler:** `tap()`, `doubleTap()`, `longPress()`, `fill(text)` (`type(text)` ile aynı; önce temizler), `append(text)`, `clear()`, `swipe(SwipeDirection)`, `pinchOut()` / `pinchIn()` (öğenin ortasında yakınlaştırma / uzaklaştırma) ve `dragTo(target)`. Tam olarak bir öğe eşleşene ve görünür, etkin ve sabit olana kadar bekler, sonra öğenin ortasında işlem yapar. Birden çok eşleşme, `first()`, `last()` veya `nth(i)` biri seçmedikçe hata verir (strict mode).
- **Kaydırma:** `scrollIntoView()` yukarı (veya `scrollIntoView(SwipeDirection)` yönünde) kaydırır, ta ki tek bir eşleşme görünene kadar; liste sonunda veya `timeouts.actionMs` dolunca hata verir.
- **Okumalar:** `getText()`, `getValue()`, `getAttribute(name)` ve `node()` görünür bir öğeyi bekler; `getAttribute`, `resource-id`, `content-desc` ve `class` adlarını da kabul eder, olmayan öznitelik için `null` döner. `count()`, `nodes()`, `allTexts()`, `isVisible()`, `isPresent()`, `isHidden()`, `isEnabled()`, `isDisabled()`, `isSelected()`, `isChecked()` ve `isFocused()` beklemeden o anki ekranı okur; `is…` durum okumaları birden fazla eşleşmede hata verir (strict). `waitForVisible()` ve `waitForHidden()` açıkça bekler.
- **Doğrulamalar:** `expect(locator)` ile `toBeVisible()`, `toBeHidden()`, `toBeEnabled()`, `toBeDisabled()`, `toBeSelected()`, `toBeChecked()`, `toBeFocused()`, `toHaveText(...)`, `toContainText(...)`, `toHaveValue(...)`, `toHaveCount(n)` ve `not()`. `timeouts.actionMs` bitene kadar yeniden dener.
- **Cihaz:** `device()`; `launchApp`, `terminateApp`, `clearAppData`, `installApp`, `openUrl` (deep link), `pressButton("BACK")`, `screenSize()` ve `screenshot()` sunar; `screen().swipe(...)` ekran boyunca kaydırır.
- **Cihaz ayarları:** `device().setOrientation(MobileOrientation.LANDSCAPE)` ve `orientation()`; `setLocation(enlem, boylam)` ve `clearLocation()`. İkisi de cihaz kapanırken geri alınır, böylece sonraki test eski yönde ve gerçek konumla başlar. `startRecording(path)` / `stopRecording()` ekranı elle kaydeder; otomatik kayıt için [`recordVideo`](configuration#screen-recording). `setAnimations(false)` ve `setAppearance("dark")` (yapılandırmada [`animations` / `appearance`](configuration) olarak da var).
- **Pano ve dosyalar:** `clipboard()`, `setClipboard(text)`, `clearClipboard()`; `pushFile(yerelYol, "/sdcard/Download/photo.jpg")` ve `pullFile(uzakYol, yerelYol)` (Android ve iOS simülatör, dosya başına en çok 1 MB).
- **Uygulamalar ve klavye:** `isAppInstalled(id)`, `foregroundApp()`, `uninstallApp(id)`, `backgroundApp(Duration)` (ana ekran, bekle, uygulamaya dön); `isKeyboardShown()` ve `hideKeyboard()` (Android BACK'e basar; iOS klavyedeki Done/Return tuşuna dokunur).
- **Crash raporları:** `crashReports()` ve `crashReport(id)`. Bir mobil test düştüğünde, test sırasında yazılan crash raporları `target/reports/crashes/` altına kaydedilir ve adım olarak loglanır.
- **Webview'ler:** `device().webViews()` öndeki uygulamanın webview'lerini (ve görünen Chrome/Safari sekmelerini) listeler; `webView()` görünen tek webview'i döndürür. `MobileWebView`; `url()`, `title()`, `content()`, `evaluate(js)`, `query(css)`, `navigate(url)`, `reload()`, `goBack()`, `goForward()` ve `waitForLoad()` sunar.

Eylemler rapora adım olarak yazılır ("Tap getByText(…)"); yazılan metin loglanmaz. Bir test başarısız olduğunda cihaz ekran görüntüsü eklenir (`screenshotOnFailure`).
