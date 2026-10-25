---
id: page-objects
title: Page Object'ler
sidebar_position: 3
description: "@MobileFind (iOS ve Android seçicileri yan yana) ve @WaitFor ile MobilePage page object'leri, YAML locator dosyaları ve MobileConditions ile mobil ön koşullar."
---

# Page Object'ler

Bir page object, bir ekranın seçicilerini ve kullanıcının o ekranda attığı adımları tek bir sınıfta toplar; böylece testler kullanıcının niyetini okur ve değişen bir seçici tek yerde düzeltilir. TestFly'ın mobil page object'leri **aynı öğenin iOS ve Android seçicisini yan yana** tutar: tek bir sayfa sınıfı iki platforma da hizmet eder.

## Sayfa yazmak

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

Locator'lar bir YAML dosyasında da durabilir; iki platform için tek dosya. Değerler [seçici dizeleridir](locators#selector-strings):

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

## İlk sayfayı ürettirmek

[Mobile Inspector](inspector)'ın **Record** modu, gerçek cihazda yaptığınız dokunma, yazma, kaydırma ve buton basışlarını `@MobileFind` alanları ve bir `recordedFlow()` metodu olan bir `MobilePage`'e çevirir. Başlangıç noktası olarak kullanın: alanları yeniden adlandırın, akışı niyet metotlarına bölün ve diğer platformun seçicilerini ekleyin.
