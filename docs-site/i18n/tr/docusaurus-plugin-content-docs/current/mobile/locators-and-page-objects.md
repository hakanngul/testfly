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

- **Eylemler:** `tap()`, `doubleTap()`, `longPress()`, `fill(text)` (`type(text)` ile aynı; önce temizler), `append(text)`, `clear()`, `swipe(SwipeDirection)`. Tam olarak bir öğe eşleşene ve görünür, etkin ve sabit olana kadar bekler, sonra öğenin ortasında işlem yapar. Birden çok eşleşme, `first()`, `last()` veya `nth(i)` biri seçmedikçe hata verir (strict mode).
- **Okumalar:** `getText()`, `getValue()`, `node()` görünür bir öğeyi bekler; `count()`, `nodes()`, `isVisible()` beklemeden o anki ekranı okur; `waitForVisible()` ve `waitForHidden()` açıkça bekler.
- **Doğrulamalar:** `expect(locator)` ile `toBeVisible()`, `toBeHidden()`, `toBeEnabled()`, `toBeDisabled()`, `toBeSelected()`, `toBeChecked()`, `toBeFocused()`, `toHaveText(...)`, `toContainText(...)`, `toHaveValue(...)`, `toHaveCount(n)` ve `not()`. `timeouts.actionMs` bitene kadar yeniden dener.
- **Cihaz:** `device()`; `launchApp`, `terminateApp`, `clearAppData`, `installApp`, `openUrl` (deep link), `pressButton("BACK")`, `screenSize()` ve `screenshot()` sunar; `screen().swipe(...)` ekran boyunca kaydırır.
- **Cihaz ayarları:** `device().setOrientation(MobileOrientation.LANDSCAPE)` ve `orientation()`; `setLocation(enlem, boylam)` ve `clearLocation()`. İkisi de cihaz kapanırken geri alınır, böylece sonraki test eski yönde ve gerçek konumla başlar. `startRecording(path)` / `stopRecording()` ekranı elle kaydeder; otomatik kayıt için [`recordVideo`](configuration#screen-recording).

Eylemler rapora adım olarak yazılır ("Tap getByText(…)"); yazılan metin loglanmaz. Bir test başarısız olduğunda cihaz ekran görüntüsü eklenir (`screenshotOnFailure`).
