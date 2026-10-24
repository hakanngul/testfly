---
id: device
title: Cihaz ve Uygulamalar
sidebar_position: 6
description: "MobileDevice: uygulama yaşam döngüsü ve diller, deep link'ler, donanım butonları, ekran yönü, konum, katlanabilirler, görünüm, pano, dosyalar, klavye, ekran görüntüleri, görünüm ağacı, ekran kaydı ve crash raporları."
---

# Cihaz ve Uygulamalar

`device()`, o anki testin `MobileDevice`'ını döndürür: uygulama, sistem ayarları ve ekrandaki tek bir öğe olmayan her şey. TestFly onu mobil testten önce açar ve sonra kapatır; hangi cihaz ve uygulama olacağını [Yapılandırma](configuration) belirler.

```java
device().openUrl("myapp://orders/42");
device().setOrientation(MobileOrientation.LANDSCAPE);
expect(screen().getByText("Sipariş #42")).toBeVisible();
```

Sonraki teste sızacak değişiklikler (ekran yönü, konum) cihaz kapanırken geri alınır.

## Cihaz bilgisi

| Metot | Döndürür |
|---|---|
| `id()` | mobilecli cihaz id'si (Android emülatör: AVD adı; iOS: UDID) |
| `platform()` | `MobilePlatform.IOS` veya `ANDROID` |
| `info()` | `MobileDeviceInfo`: id, ad, platform, tür (simülatör, emülatör, gerçek cihaz), durum, OS sürümü, model |
| `screenSize()` | ekran noktası cinsinden `MobileScreenSize(width, height, scale)` |
| `bundleId()` | yapılandırılan uygulama (`mobile.app.bundleId`) veya boş |
| `actionTimeout()` | eylem ve doğrulamaların varsayılan bekleme süresi |
| `keepsAppState()` | uygulamanın testler arasında durumunu koruyup korumadığı (`mobile.app.reset: none`) |

## Uygulamalar

| Metot | Ne yapar |
|---|---|
| `launchApp(bundleId)` | uygulamayı başlatır (Android: varsa yapılandırılan `mobile.app.activity`) |
| `launchApp(bundleId, List.of("fr-FR", "en-US"))` | uygulamayı yalnızca bu açılış için bu dillerle başlatır (yerelleştirme testleri) |
| `terminateApp(bundleId)` | uygulamayı durdurur |
| `clearAppData(bundleId)` | verisini temizler; Android'de çalışma zamanı izinlerini de |
| `installApp(path)` | mobilecli sunucusunun erişebildiği bir `.apk`, `.app` veya `.ipa` kurar |
| `uninstallApp(bundleId)` | uygulamayı kaldırır |
| `installedApps()` | kurulu uygulamaların bundle id / paket adları |
| `isAppInstalled(bundleId)` | birinin kurulu olup olmadığı |
| `foregroundApp()` | öndeki uygulamanın bundle id / paket adı |
| `backgroundApp(Duration)` | ana ekran, bekle, yapılandırılan uygulamayı geri getir (devam ettirme testleri) |

```java
device().terminateApp(device().bundleId());
device().launchApp(device().bundleId(), List.of("de-DE"));
expect(screen().getByText("Anmelden")).toBeVisible();
```

`launchApp` uygulamanın öne gelmesini beklemez; sonraki locator çağrısı ekranını bekler. Uygulamanın her testten önce nasıl hazırlanacağını (`relaunch`, `clearData`, `none`) `mobile.app.reset` belirler ([Yapılandırma](configuration)).

## Deep link'ler ve butonlar

- `openUrl(url)` bir URL'yi veya deep link'i (`myapp://…`, `https://…` universal/app link) sistemin açacağı gibi açar.
- `pressButton(ad)`: `HOME`, `BACK` (Android), `ENTER`, `VOLUME_UP`, `VOLUME_DOWN`, `POWER`, Android'de ayrıca `APP_SWITCH` ve `DPAD_*` tuşları.

## Ekran yönü, konum ve katlanabilirler

| Metot | Ne yapar |
|---|---|
| `orientation()`, `setOrientation(MobileOrientation.LANDSCAPE)` | ekran yönünü okur / döndürür; ilk değişiklikten önceki yön cihaz kapanırken geri yüklenir |
| `setLocation(enlem, boylam)` | GPS konumunu geçersiz kılar (derece); cihaz kapanırken temizlenir |
| `clearLocation()` | gerçek konuma döner |
| `setFoldState("half-open")` | katlanabilir simülatör/emülatörün menteşesini ayarlar: `folded`, `half-open` veya `open` |

## Görünüm ve animasyonlar

- `setAppearance("dark")` / `setAppearance("light")` sistem temasını değiştirir.
- `setAnimations(false)` sistem animasyonlarını kapatır: ekranlar daha çabuk durulur, beklemeler kısalır. `setAnimations(true)` ile geri açın.

İkisi de yapılandırmada tüm testler için ayarlanabilir (`mobile.appearance`, `mobile.animations`).

## Klavye {#keyboard}

- `isKeyboardShown()` ekran klavyesinin açık olup olmadığını söyler.
- `hideKeyboard()` kapatır: Android BACK'e basar; iOS klavyedeki `Done`, `Return` veya `Hide keyboard` tuşuna dokunur, klavyede böyle bir tuş yoksa hata verir (o zaman alanın dışına dokunun).

## Pano ve dosyalar

| Metot | Ne yapar |
|---|---|
| `clipboard()`, `setClipboard(text)`, `clearClipboard()` | cihaz panosunu okur / yazar (kopyala-yapıştır akışları, tek kullanımlık kodlar) |
| `pushFile(yerelYol, "/sdcard/Download/photo.jpg")` | yerel bir dosyayı cihaza kopyalar (Android, iOS simülatör), en çok 1 MB |
| `pullFile(uzakYol, yerelYol)` | cihazdaki bir dosyayı yerel yola kopyalar ve döndürür, en çok 1 MB |

Dosya aktarımı gerçek iPhone'larda yoktur.

## Ekran görüntüleri ve görünüm ağacı

| Metot | Döndürür |
|---|---|
| `device().screenshot()`, `screen().screenshot()` | tüm ekranın PNG'si |
| `locator.screenshot()` | tek bir öğenin PNG'si ([Eylemler](actions#reads)) |
| `screen().viewTree()` | o anki ağaç, `MobileNode`'lar olarak (tür, metin, label, identifier, sınırlar, durum, çocuklar) |
| `device().viewTreeText()` | ağaç, mobilecli'nin girintili metin anlık görüntüsü olarak; öğe başına bir satır |

```
@e1 [Window]
  @e2 [NavigationBar] "Ayarlar"
  @e3 [Button] "Genel"
  @e4 [Switch] "Wi-Fi"
```

Başarısız bir test ekran görüntüsünü ve bu metni otomatik kaydeder ([Hata Raporları](reports)).

## Ekran kaydı

`startRecording(path)` ekranı bir MP4 dosyasına kaydetmeye başlar, `stopRecording()` durdurur ve yolu döndürür. Her testi kaydedip başarısızların videolarını saklamak için bunun yerine `mobile.recordVideo: failures` kullanın ([Hata Raporları](reports#videos)). Kayıt yerel bir mobilecli sunucusu gerektirir.

## Crash raporları

`crashReports()` cihazdaki crash raporlarını listeler (`MobileCrashReport(id, processName, timestamp)`), `crashReport(id)` birini metin olarak döndürür. Bir mobil test düştüğünde test sırasında yazılan crash raporları otomatik kaydedilir ([Hata Raporları](reports#crash-reports)).

## WebView'ler

`webViews()` ve `webView()` öndeki uygulamanın gömülü webview'lerini döndürür: bkz. [WebView'ler](webviews).

## Cihazı kendiniz açmak

Test çalıştırıcısı dışında veya ikinci bir cihaz için `Mobile.connect` ile açın. `testfly.yml`'nin `mobile:` bloğunu okur ve try-with-resources ile kapanır:

```java
try (MobileDevice device = Mobile.connect()) {                       // mobile.platform
    device.screen().getByText("Ayarlar").tap();
}
try (MobileDevice ios = Mobile.connect(MobilePlatform.IOS)) { … }    // mobile.ios bloğu mobile'ın üzerine
try (MobileDevice d = Mobile.connect(mobileConfig)) { … }            // kendi kurduğunuz bir TestFlyConfig.Mobile
```
