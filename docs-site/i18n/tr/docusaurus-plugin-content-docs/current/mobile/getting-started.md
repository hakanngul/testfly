---
id: getting-started
title: Native Mobil (Deneysel)
sidebar_position: 1
description: "Kullanıcının kurduğu mobilecli sunucusu üzerinden Java ile Android ve iOS uygulamalarını sürün: device(), screen().getByRole/Text/TestId, otomatik bekleyen eylemler ve TestNG, JUnit 5, Cucumber üzerinde expect() doğrulamaları."
---

# Native Mobil (Deneysel)

TestFly, native Android ve iOS uygulamalarını Appium olmadan sürebilir. [mobilecli](https://github.com/mobile-next/mobilecli) adlı, **sizin kurup çalıştırdığınız** bir sunucuyla konuşur; TestFly onu paketlemez, indirmez ve değiştirmez. API, [Mobilewright](https://mobilewright.dev)'tan esinlenmiştir. Tasarım ve kararlar: ADR 0002 (`docs/adr/0002-native-mobile-via-mobilecli.md`).

:::caution Deneysel
`io.testfly.mobile` içindeki her şey deneyseldir: `@TestFlyApi` ile işaretli değildir ve bir minor sürümde değişebilir. mobilecli 1.0.20'den kaydedilen yanıtları tekrar oynatan, süreç içi sahte bir mobilecli sunucusuna karşı test edilir. **Henüz gerçek cihaz, emülatör veya simülatörde doğrulanmamıştır.**
:::

## Ön koşullar

1. mobilecli'yi (1.0.20 veya üstü) kurun ve sunucuyu başlatın:

   ```bash
   mobilecli server start          # http://localhost:12000 adresini dinler
   mobilecli devices               # çevrimiçi cihazları ve kimliklerini listeler
   ```

2. mobilecli'nin ajanını cihaza bir kez kurun. Ekran çağrıları ona ihtiyaç duyar:

   ```bash
   mobilecli agent install --device <device-id>
   mobilecli agent status --device <device-id>
   ```

   Android emülatöründe cihaz kimliği adb seri numarası (`emulator-5554`) değil, AVD adıdır (örneğin `Pixel_8_API_34`).

## Yapılandırma

`testfly.yml` içinde `mobile:` bölümü yoksa mobil tarafta hiçbir şey yüklenmez.

```yaml
mobile:
  serverUrl: ${MOBILECLI_URL:-http://localhost:12000}
  platform: android            # android | ios (zorunlu)
  deviceId: ""                 # tam kimlik; deviceName'e göre önceliklidir
  deviceName: ""               # cihaz adına uygulanan regex
  devices: []                  # cihaz havuzu: her teste bir kimlik verilir
  pool: false                  # deviceName ile eşleşen tüm çevrimiçi cihazları havuza al
  app:
    bundleId: com.example.app  # Android paketi / iOS bundle id
    install: ""                # isteğe bağlı .apk/.ipa/.app yolu, cihaz açılırken kurulur
    reset: relaunch            # none | relaunch | clearData
  timeouts:
    actionMs: 5000             # bir eylemin veya doğrulamanın bekleme bütçesi
    pollMs: 100                # ekranın iki okuması arasındaki bekleme
    rpcMs: 30000               # mobilecli'ye tek bir isteğin üst sınırı
    reserveMs: 300000          # aynı JVM'de başka bir testin tuttuğu cihaz için bekleme
  allowRemote: false
  headers: {}                  # örn. Authorization: Bearer ${MOBILECLI_TOKEN}
  screenshotOnFailure: true
```

- **Cihaz seçimi.** `deviceId`, platformun tam olarak bir çevrimiçi cihazıyla eşleşmelidir; yoksa `deviceName` regex'i eşleşmelidir; o da yoksa platformun tam olarak bir çevrimiçi cihazı olmalıdır. Sıfır veya birden çok eşleşme, mobilecli'nin bildirdiği cihazların listesiyle hata verir.
- **Cihaz havuzu (paralel koşum).** Cihazları `devices: [id1, id2]` ile listeleyin ya da `pool: true` ile platformun `deviceName` ile eşleşen tüm çevrimiçi cihazlarını kullanın. Her test boş bir cihaz alır; hepsi kullanımdaysa `reserveMs` kadar bekler, sonra `MobileTimeoutException` ile hata verir. Paralel mobil worker sayısı cihaz sayısını geçmemelidir. `devices`, `deviceId` veya `pool` ile birlikte kullanılamaz. `devices` içindeki çevrimdışı kimlikler bir uyarıyla atlanır.
- **Uygulama sıfırlama.** `relaunch` her testten önce `bundleId`'yi kapatıp açar; `clearData` verisini silip açar; `none` dokunmaz. `none` dışında uygulama testten sonra kapatılır.
- **Güvenlik.** mobilecli'de kimlik doğrulama yoktur, bu yüzden yalnızca loopback adresleri kabul edilir. Uzak bir sunucu için `allowRemote: true` ve `https://` adresi gerekir; örneğin TLS sonlandıran, kimlik doğrulayan bir proxy veya SSH tüneli arkasında mobilecli.

## Test yazmak

| Motor | Bir test nasıl mobil test olur |
|---|---|
| TestNG | `io.testfly.mobile.BaseMobileTest` sınıfından türetin ya da TestFly listener'larıyla zaten çalışan bir sınıfa veya metoda (örneğin bir `BaseTest` alt sınıfında) `@MobileTest` ekleyin |
| JUnit 5 | `io.testfly.mobile.junit5.BaseJUnit5MobileTest` sınıfından türetin ya da `TestFlyExtension` kullanan bir sınıfa veya metoda `@MobileTest` ekleyin |
| Cucumber | senaryoyu veya feature'ı `@mobile` ile etiketleyin; adım sınıfları `MobileSupport` uygular |

Mobil bir testte TestFly tarayıcıyı başlatmaz, testten önce cihazı açar ve sonra kapatır. Cihaz testin thread'ine aittir.

```java
import io.testfly.mobile.BaseMobileTest;
import io.testfly.mobile.MobileRole;
import org.testng.annotations.Test;

public class LoginTest extends BaseMobileTest {

    @Test
    public void signsIn() {
        screen().getByTestId("username").fill("ada");
        screen().getByLabel("Password").fill("secret");
        screen().getByRole(MobileRole.BUTTON, "Sign in").tap();

        expect(screen().getByText("Hello, ada")).toBeVisible();
        apiClient().get("/me").send().assertStatus(200);   // aynı testte backend kontrolü
    }
}
```

Temel sınıflar ayrıca `apiClient()`, `db()`, `mailbox()`, `ctx()`, `step()`, `softAssert()` ve `getTestData()` sağlar.

Cucumber adımları:

```java
public class LoginSteps implements MobileSupport {

    @When("I sign in as {string}")
    public void signIn(String user) {
        screen().getByTestId("username").fill(user);
        screen().getByRole(MobileRole.BUTTON, "Sign in").tap();
    }
}
```

Bir test çalıştırıcısı olmadan cihazı kendiniz açın:

```java
try (MobileDevice device = Mobile.connect()) {   // testfly.yml dosyasını okur
    device.screen().getByText("Settings").tap();
}
```

## Örnekler

Hazır örnekler cihazdaki yerleşik Ayarlar uygulamasını kullanır, uygulama dosyası gerekmez:

| Platform | Test | Çalıştırma |
|---|---|---|
| iOS simülatörü | `src/test/java/io/testfly/examples/mobile/IosSettingsExampleTest.java` | `mvn test -Dtestfly.profile=mobile-ios -Dtest=IosSettingsExampleTest` |
| Android emülatörü | `src/test/java/io/testfly/examples/mobile/AndroidSettingsExampleTest.java` | `mvn test -Dtestfly.profile=mobile-android -Dtest=AndroidSettingsExampleTest` |

Profiller `src/test/resources/testfly-mobile-ios.yml` ve `testfly-mobile-android.yml` dosyalarıdır. Önce `mobilecli server start` ile sunucuyu başlatın ve simülatörü ya da emülatörü açın; metinler İngilizce cihaz varsayar. `Mobile Emulator Smoke` iş akışı iki örneği de GitHub Actions'ta çalıştırır.

## Locator'lar

| Metot | Eşleştiği |
|---|---|
| `getByRole(MobileRole.BUTTON)`, `getByRole(role, "name")` | rolün iki platformdaki native sınıfları; `name` etiket, yoksa metindir |
| `getByText("Save")`, `getByText("sav", false)`, `getByText(Pattern)` | metin, yoksa etiket, yoksa değer (tam, büyük/küçük harf duyarsız alt dize veya regex) |
| `getByLabel(...)` | erişilebilirlik etiketi |
| `getByTestId("login")` | iOS accessibility identifier veya Android resource-id (`login`, `com.app:id/login` ile de eşleşir) |
| `getByPlaceholder(...)` | placeholder metni |
| `getByType("android.widget.Switch")` | ham native sınıf |

`first()`, `last()`, `nth(i)`, `within(container)`, `or(other)`, `and(other)`, `filterHasText(...)`, `filterHasNotText(...)`, `filterHas(locator)`, `filterHasNot(locator)` ve `withTimeout(Duration)` ile daraltın. Locator'lar TestFly içinde ekranın eleman ağacı üzerinde değerlendirilir.

## Page object'ler

Sayfalar `MobilePage`'den türer. `@MobileFind` alanı bir öğenin iOS ve Android seçicisini birlikte tutar; alanları private tutun, testlere niyet metotları verin. Ekranı değiştiren metot bir sonraki sayfayı döndürür, böylece test tek bir fluent zincir olarak okunur.

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

    @MobileFind("role=button;name=Sign in")    // iki platformda aynı seçici
    private MobileLocator signIn;

    public LoginPage signIn(String user) { username.fill(user); signIn.tap(); return this; }
    public void shouldBeClosed() { expect(username).toBeHidden(); }
}

// test (BaseMobileTest, JUnit 5 mobil testi veya Cucumber adımları)
new HomePage().openLogin().signIn("ada").shouldBeClosed();
```

- `new SomePage()` testin cihazını kullanır, alanlara koşan platformun locator'ını atar, `@WaitFor` alanlarını bekler, sonra `onLoad()` çağırır. `MobilePage(MobileScreen)` ekranı açıkça alır (test runner dışında).
- `@MobileFind` alanlarına başlangıç değeri vermeyin: Java alt sınıf alan başlatıcılarını temel kurucudan sonra çalıştırır ve locator'ı ezer. `onLoad()` bu başlatıcılardan önce çalışır.
- Sayfa oluşturmak onu bekler; Cucumber'da sayfa örneklerini adımlar arasında step sınıfının alanlarında tutun, kapanmış bir sayfayı yeniden oluşturmayın.
- Seçici dizeleri: çıplak dize test id'dir; yoksa `testid=…`, `text=…`, `label=…`, `placeholder=…`, `type=…` veya `role=button;name=…` (tam eşleşme). `screen().locate("…")` aynı dizeleri alır.
- Platformun kendi seçicisi boşsa `value` kullanılır. Koşan platform için seçicisi olmayan alan yalnızca kullanıldığında hata verir.
- Düz sınıflar da çalışır: `page(Type.class)` veya `MobilePages.init(page, screen)` `@MobileFind` alanlarını bekleme ve `onLoad()` olmadan doldurur.
- Örnek: `src/test/java/io/testfly/examples/mobile/HomePage.java` ve `LoginPage.java`; `IosLoginExampleTest` ve Cucumber `IosLoginSteps` kullanır.

## Eylemler ve doğrulamalar

- **Eylemler:** `tap()`, `doubleTap()`, `longPress()`, `fill(text)` (`type(text)` ile aynı), `append(text)`, `clear()`, `swipe(SwipeDirection)`. Tam olarak bir eleman eşleşene ve görünür, etkin ve sabit olana kadar bekler, sonra elemanın ortasında işlem yapar. Birden çok eşleşme, `first()`, `last()` veya `nth(i)` biri seçmedikçe hata verir (strict mode).
- **Okumalar:** `getText()`, `getValue()`, `node()` görünür bir eleman bekler; `count()`, `nodes()`, `isVisible()` beklemeden o anki ekranı okur.
- **Doğrulamalar:** `expect(locator)` ile `toBeVisible()`, `toBeHidden()`, `toBeEnabled()`, `toBeDisabled()`, `toBeSelected()`, `toBeChecked()`, `toBeFocused()`, `toHaveText(...)`, `toContainText(...)`, `toHaveValue(...)`, `toHaveCount(n)` ve `not()`. `timeouts.actionMs` bitene kadar yeniden denenir.
- **Cihaz:** `device()`; `launchApp`, `terminateApp`, `clearAppData`, `installApp`, `openUrl` (deep link), `pressButton("BACK")`, `screenSize()` ve `screenshot()` sunar; `screen().swipe(...)` ekran boyunca kaydırır.

Eylemler rapora adım olarak yazılır ("Tap getByText(…)"); yazılan metin kaydedilmez. Bir test başarısız olduğunda cihaz ekran görüntüsü eklenir (`screenshotOnFailure`).

## Hatalar

| İstisna | Ne zaman |
|---|---|
| `MobileServerUnavailableException` | mobilecli'ye ulaşılamıyor: `mobilecli server start` ile başlatın |
| `MobileVersionException` | mobilecli 1.0.20'den eski |
| `DeviceSelectionException` | seçiciler sıfır veya birden çok çevrimiçi cihazla eşleşiyor ya da havuzdaki hiçbir cihaz çevrimiçi değil |
| `DeviceNotFoundException` | mobilecli cihazı tanımıyor |
| `MobileLocatorException` | strict mode: birden çok eleman eşleşti |
| `MobileTimeoutException` | bir eylemin veya isteğin süresi doldu; mesaj son görülen elemanları listeler |
| `MobileAssertionError` | bir `expect(...)` zamanında geçmedi |
| `MobileRpcException` | mobilecli'nin diğer hataları; başarısız bir ekran okuması ajan kontrolünü belirtir |

## Sınırlar

- Test başına bir cihaz; JVM genelindeki rezervasyon bir cihazı aynı anda yalnızca bir testin kullanmasına izin verir. Havuz yoksa paralel worker'lar tek cihazı sırayla paylaşır. Cihazları paylaşan ayrı JVM'ler veya makineler koordine edilmez.
- `launchApp` uygulamanın öne gelmesini beklemez; bunun yerine ilk locator çağrısı bekler.
- Henüz WebView, ekran kaydı veya WebSocket desteği yok.
- Özel arka uçlar `io.testfly.mobile.spi.MobileDriver` arayüzünü uygulayıp `Mobile.connect(driver, settings)` ile cihaz açabilir.
