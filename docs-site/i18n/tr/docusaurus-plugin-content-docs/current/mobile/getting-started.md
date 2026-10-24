---
id: getting-started
title: Native Mobil (Deneysel)
sidebar_position: 1
description: "Kullanıcının kurduğu mobilecli sunucusu üzerinden Java ile Android ve iOS uygulamalarını sürün: page object'ler, otomatik bekleyen locator'lar ve TestNG, JUnit 5, Cucumber üzerinde expect() doğrulamaları."
---

# Native Mobil (Deneysel)

TestFly, native Android ve iOS uygulamalarını Appium olmadan sürebilir. **Sizin kurduğunuz** bir sunucu olan [mobilecli](https://github.com/mobile-next/mobilecli) ile konuşur; TestFly onu paketlemez, indirmez veya değiştirmez, ama sunucu çalışmıyorsa kurulu binary'yi başlatabilir. API, [Mobilewright](https://mobilewright.dev)'tan esinlenmiştir. Tasarım ve kararlar: ADR 0002 (`docs/adr/0002-native-mobile-via-mobilecli.md`).

:::caution Deneysel
`io.testfly.mobile` içindeki her şey deneyseldir: `@TestFlyApi` ile işaretli değildir ve bir minor sürümde değişebilir. `Mobile Emulator Smoke` iş akışı örnekleri her ilgili değişiklikte Android emülatörlerinde (API 30 ve 34) ve bir iOS simülatöründe çalıştırır; gerçek telefonlar henüz CI kapsamında değildir.
:::

| Sayfa | Kapsadığı |
|---|---|
| Bu sayfa | kurulum, TestNG, JUnit 5 ve Cucumber'da ilk test, hazır örnekler |
| [Locator'lar ve page object'ler](locators-and-page-objects) | `getBy…` locator'ları, seçici dizeleri, `MobilePage`/`@MobileFind`/`@WaitFor`, YAML locator dosyaları, ön koşullar (`MobileConditions` ile `@PreCondition`), eylemler ve doğrulamalar |
| [Yapılandırma](configuration) | `mobile:` bloğu, cihaz seçimi, cihaz havuzu, uygulama sıfırlama, sunucuyu otomatik başlatma, profiller, güvenlik |
| [CI](ci) | Android emülatörü veya iOS simülatörüyle GitHub Actions'ta çalıştırma |
| [Sorun giderme](troubleshooting) | istisnalar, sık sorunlar, sınırlar |

## Ön koşullar

1. mobilecli 1.0.20 veya üstünü kurun, örneğin `npm install -g mobilecli`. Android için Android SDK (`adb`), iOS simülatörleri için macOS'ta Xcode gerekir.
2. Bir emülatör veya simülatör açın ya da telefon bağlayın ve kimliğini bulun:

   ```bash
   mobilecli devices
   ```

   Android emülatöründe kimlik adb seri numarası (`emulator-5554`) değil, AVD adıdır (örneğin `Pixel_8_API_34`). iOS'ta simülatörün UDID'sidir.
3. **iOS:** mobilecli'nin ajanını cihaza bir kez kurun; ekran okumaları ve dokunuşlar ona ihtiyaç duyar. Android ajan gerektirmez (`agent status` yalnızca bunu bildirir):

   ```bash
   mobilecli agent install --device <device-id>
   mobilecli agent status --device <device-id>
   ```

   **Gerçek iPhone**'da ajanın o cihaz için imzalanması gerekir: `mobilecli agent install --device <udid> --provisioning-profile /path/to/profile.mobileprovision` (geçerli bir Apple provisioning profile). Gerçek cihazda webview ve Safari sekmeleri için ayrıca **Ayarlar > Uygulamalar > Safari > İleri Düzey > Web Denetçisi** açık olmalıdır. Dosya push/pull gerçek iPhone'da yoktur.

Sunucuyu kendiniz başlatmanız gerekmez: `http://localhost:12000` cevap vermezse TestFly `mobilecli server start` çalıştırır ve JVM kapanırken durdurur (bkz. [sunucuyu otomatik başlatma](configuration#server-auto-start)). Kendiniz başlatmanız da (`mobilecli server start`) çalışır.

## En küçük yapılandırma

Etkin `testfly.yml` (ya da profil, `-Dtestfly.profile=<ad>` → `testfly-<ad>.yml`) içinde `mobile:` bölümü yoksa mobil tarafta hiçbir şey yüklenmez:

```yaml
mobile:
  platform: ios                     # android | ios
  deviceId: ${MOBILE_DEVICE_ID:-}   # boşsa platformun tek çevrimiçi cihazı
  app:
    bundleId: com.example.app       # iOS bundle id / Android paketi
```

Diğer tüm anahtarların varsayılanı vardır; bkz. [Yapılandırma](configuration). Yalnızca mobil olan bir profil `browser`, `execution` veya `timeouts` bloğu gerektirmez.

## İlk testiniz

| Motor | Bir test nasıl mobil test olur |
|---|---|
| TestNG | `io.testfly.mobile.BaseMobileTest`'ten türetin ya da TestFly listener'larıyla çalışan bir sınıf veya metoda `@MobileTest` koyun (örneğin bir `BaseTest` alt sınıfında) |
| JUnit 5 | `io.testfly.mobile.junit5.BaseJUnit5MobileTest`'ten türetin ya da `TestFlyExtension` kullanan bir sınıf veya metoda `@MobileTest` koyun |
| Cucumber | senaryoyu veya feature'ı `@mobile` ile etiketleyin; step sınıfları `MobileSupport` uygular |

Mobil bir testte TestFly tarayıcıyı atlar, testten önce cihazı açar ve sonra kapatır. Cihaz testin thread'ine aittir.

[Page object'lerle](locators-and-page-objects#page-objects) bir test tek bir zincir olarak okunur:

```java
public class LoginTest extends BaseMobileTest {

    @Test
    public void signsIn() {
        new HomePage()
                .openLogin()
                .signIn(System.getenv("MOBILE_LOGIN_USER"), System.getenv("MOBILE_LOGIN_PASSWORD"))
                .shouldBeClosed();
    }
}
```

Kısa kontroller için doğrudan locator'lar da çalışır:

```java
screen().getByTestId("username").fill("ada");
screen().getByRole(MobileRole.BUTTON, "Sign in").tap();
expect(screen().getByText("Hello, ada")).toBeVisible();
apiClient().get("/me").send().assertStatus(200);   // aynı testte backend kontrolü
```

Temel sınıflar ayrıca `apiClient()`, `db()`, `mailbox()`, `ctx()`, `step()`, `softAssert()` ve `getTestData()` sağlar.

Cucumber adımları sayfa örneklerini adımlar arasında tutar, çünkü bir sayfa oluşturmak onun ekranını bekler:

```java
public class LoginSteps implements MobileSupport {
    private HomePage home;
    private LoginPage login;

    @Given("the app is on the home screen")
    public void onHome() { home = new HomePage(); }

    @When("I open the login form")
    public void openLogin() { login = home.openLogin(); }
}
```

Test runner dışında cihazı kendiniz açın:

```java
try (MobileDevice device = Mobile.connect()) {   // testfly.yml'i okur
    device.screen().getByText("Settings").tap();
}
```

## Örnekler

| Örnek | Motor | Çalıştırma |
|---|---|---|
| `examples/mobile/IosSettingsExampleTest` | TestNG, iOS Ayarlar uygulaması | `mvn test -Dtestfly.profile=mobile-ios -Dtest=IosSettingsExampleTest` |
| `examples/mobile/AndroidSettingsExampleTest` | TestNG, Android Ayarlar uygulaması | `mvn test -Dtestfly.profile=mobile-android -Dtest=AndroidSettingsExampleTest` |
| `examples/mobile/IosLoginExampleTest`, `AndroidLoginExampleTest` | TestNG, `HomePage`/`LoginPage` page object'leri, `@MobileTest(platform = …)` | `MOBILE_LOGIN_USER=… MOBILE_LOGIN_PASSWORD=… mvn test -Dtest='IosLoginExampleTest,AndroidLoginExampleTest'` (`ios:` ve `android:` bloklu tek profil `testfly-mobile-login.yml`'i kendileri seçer) |
| `examples/mobile/IosLoginJUnit5ExampleTest`, `AndroidLoginJUnit5ExampleTest` | JUnit 5, aynı sayfalar ve profil | `MOBILE_LOGIN_USER=… MOBILE_LOGIN_PASSWORD=… mvn test -Dtest='*LoginJUnit5ExampleTest'` |
| `examples/cucumber/mobile/IosLoginCucumberRunner` | Cucumber, `features-mobile/ios_login.feature` | `mvn test -Dtest=IosLoginCucumberRunner` |

Page object yazarken ekranı incelemek için `tools/vscode-mobile-inspector/` altındaki VS Code eklentisi ekran görüntüsünü, `@eN` ref'li UI ağacını ve yapıştırmaya hazır `@MobileFind` seçicilerini gösterir (`npm run package` ile derlenir); `tools/intellij-mobile-inspector/` aynı inspector'ın IntelliJ IDEA sürümüdür.

Tüm yollar `src/test/java/io/testfly/` altındadır. Ayarlar örnekleri uygulama dosyası gerektirmez; metinler İngilizce cihaz varsayar. Kimlik bilgileri yalnızca ortam değişkenlerinden gelir.
