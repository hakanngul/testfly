---
id: actions
title: Eylemler ve Okumalar
sidebar_position: 4
description: "Otomatik bekleyen mobil eylemler (tap, fill, check, swipe, pinch, drag, scrollIntoView), koordinatla hareketler, öğe okumaları ve açık beklemeler."
---

# Eylemler ve Okumalar

## Bir eylem nasıl bekler {#auto-waiting}

Her locator eylemi ekranı tekrar tekrar okur (her `timeouts.pollMs`, varsayılan 100 ms), öğe **eyleme hazır** olunca öğenin sınırlarının ortasında işlem yapar:

1. tam olarak bir öğe eşleşir ([strict mode](locators#strict-mode));
2. görünürdür;
3. etkindir (enabled);
4. sabittir: sınırları iki okuma arasında değişmemiştir (süren bir animasyon yok).

Öğe `timeouts.actionMs` (varsayılan 5000 ms) içinde hazır olmazsa eylem, son nedeni söyleyen ve görülen öğeleri listeleyen bir `MobileTimeoutException` ile düşer:

```
tap on getByText("Giriş") timed out after 5000 ms: the element is disabled.
  - android.widget.Button id="com.example:id/sign_in" text="Giriş" disabled [40,580 1000x120]
```

Eylemin içindeki her istek bu sürenin kalanıyla sınırlıdır; takılan bir mobilecli sunucusu testi süreden uzun tutamaz. `locator.withTimeout(Duration)` tek bir locator'a farklı süre verir; `mobile.timeouts.actionMs` varsayılanı değiştirir ([Yapılandırma](configuration)).

Eylemler rapora adım olarak yazılır ("Tap getByText("Giriş")"). Yazılan metin loglanmaz; şifreler rapora girmez.

## Dokunma ve metin

| Metot | Ne yapar |
|---|---|
| `tap()` | ortasına dokunur |
| `doubleTap()` | iki kez dokunur |
| `longPress()`, `longPress(Duration.ofSeconds(2))` | uzun basar (mobilecli varsayılan süresi veya verilen süre) |
| `fill("ada")` | alana dokunur, temizler, metni yazar |
| `type("ada")` | `fill` ile aynı (web `Locator.type` gibi adlandırılmış) |
| `append(" Lovelace")` | alana dokunur, mevcut içeriğin sonuna yazar |
| `clear()` | alana dokunur ve temizler |

```java
screen().getByTestId("username").fill(System.getenv("MOBILE_LOGIN_USER"));
screen().getByTestId("password").fill(System.getenv("MOBILE_LOGIN_PASSWORD"));
screen().getByRole(MobileRole.BUTTON, "Giriş").tap();
```

Yazdıktan sonra klavye açık kalıp alanın altındaki öğeleri örtebilir: `device().hideKeyboard()` kapatır ([Cihaz](device#keyboard)).

## Checkbox, radio ve switch

| Metot | Ne yapar |
|---|---|
| `check()` | henüz işaretli değilse dokunur |
| `uncheck()` | işaretliyse dokunur |
| `setChecked(boolean)` | değere göre `check()` veya `uncheck()` |

```java
screen().getByText("Beni hatırla").check();              // iki kez çağırmak güvenli
screen().getByRole(MobileRole.SWITCH, "Wi-Fi").setChecked(false);
```

Öğenin `checked` durumunu görünüm ağacından okurlar. Sonradan yeni durumu doğrulamazlar; uygulama dokunuşu yok sayabiliyorsa `expect(locator).toBeChecked()` ekleyin.

## Öğe üzerinde hareketler

| Metot | Ne yapar |
|---|---|
| `swipe(SwipeDirection.LEFT)` | öğenin ortasından, boyutunun yarısı veya en az 200 px kaydırır (carousel, kaydırarak silme) |
| `pinchOut()` / `pinchIn()` | öğenin ortasında iki parmakla yakınlaştırma / uzaklaştırma (harita, resim) |
| `dragTo(target)` | öğeye basar, `target`'ın ortasına sürükler ve bırakır (sıralama, slider) |
| `scrollIntoView()` | tam olarak bir eşleşme görünene kadar ekranı yukarı kaydırır |
| `scrollIntoView(SwipeDirection.DOWN)` | aynısı, başka yönde |

`scrollIntoView`, ekran artık değişmediğinde (liste sonu) veya süre dolduğunda `MobileTimeoutException` ile durur.

```java
screen().getByText("Gizlilik").scrollIntoView();
screen().getByText("Gizlilik").tap();
screen().getByTestId("row-3").swipe(SwipeDirection.LEFT);
screen().getByRole(MobileRole.BUTTON, "Sil").tap();
```

## Ekran hareketleri ve koordinatlar

`screen()` öğe gerektirmeyen hareketler sunar:

| Metot | Ne yapar |
|---|---|
| `screen().swipe(SwipeDirection.UP)` | ekranın ortasından kaydırır (yüksekliğinin veya genişliğinin yarısı) |
| `screen().tap(x, y)` | bir noktaya dokunur; ekran noktası cinsinden (görünüm ağacının birimi) |
| `screen().doubleTap(x, y)` | bir noktaya iki kez dokunur |
| `screen().longPress(x, y)`, `longPress(x, y, Duration)` | bir noktaya uzun basar |

Koordinatlar cihaz boyutuna ve düzene bağlıdır; locator'ları tercih edin, koordinatları canvas, harita ve oyunlar için kullanın. Beklemez ve yeniden denemez.

## Okumalar {#reads}

Görünür tek bir öğeyi **bekleyen** okumalar:

| Metot | Döndürür |
|---|---|
| `getText()` | görünen metin: `text`, yoksa `label`, yoksa `value` |
| `getValue()` | `value` (metin alanı içeriği, slider konumu); yoksa `""` |
| `getAttribute(name)` | herhangi bir öznitelik (`name`, `label`, `value`, `text`, `identifier`, `type`, `enabled`, … veya ham bir mobilecli alanı); `resource-id`, `content-desc` ve `class` takma ad olarak çalışır; yoksa `null` |
| `bounds()` | ekran noktası cinsinden `MobileBounds(x, y, width, height)` |
| `screenshot()` | yalnızca öğenin PNG görüntüsü (mobilecli kırpar) |
| `node()` | öğe, `MobileNode` anlık görüntüsü olarak (tüm alanlar, çocuklar) |

**O anki ekranı** beklemeden okuyanlar:

| Metot | Döndürür |
|---|---|
| `count()` | eşleşme sayısı |
| `all()` | o anki her eşleşme için bir locator; ör. liste satırlarında döngü |
| `nodes()` | eşleşmeler, `MobileNode` olarak |
| `allTexts()` | her eşleşmenin görünen metni |
| `isPresent()` | en az bir eşleşme var |
| `isVisible()`, `isHidden()` | tam olarak bir görünür eşleşme / hiç görünür eşleşme yok |
| `isEnabled()`, `isDisabled()`, `isSelected()`, `isChecked()`, `isFocused()` | tek eşleşmenin durumu (strict) |

```java
for (MobileLocator row : screen().getByRole(MobileRole.LISTITEM).all()) {
    System.out.println(row.getText());
}
Files.write(Path.of("target/logo.png"), screen().getByTestId("logo").screenshot());
```

Beklemeyen bir okuma ekranı o anki haliyle, belki bir animasyonun ortasında görür. Bir durumu kontrol etmek için yeniden deneyen bir [doğrulamayı](assertions) tercih edin.

## Açık beklemeler

| Metot | Şuna kadar bekler |
|---|---|
| `waitForVisible()` | tam olarak bir eşleşme görünür |
| `waitForHidden()` | görünür eşleşme kalmaz (veya hiçbir şey eşleşmez) |
| `waitForEnabled()` | tek eşleşme etkin olur |
| `waitForDisabled()` | tek eşleşme devre dışı olur |

Eylemler zaten bekler; açık beklemeler sadece ulaşmak istediğiniz ekranlar içindir, örneğin bir sonraki okumadan önce kaybolması gereken bir yükleniyor göstergesi:

```java
screen().getByRole(MobileRole.PROGRESSBAR).waitForHidden();
```

## Donanım butonları ve sistem arayüzü

`device().pressButton("BACK")` bir donanım butonuna basar: `HOME`, `BACK` (Android), `ENTER`, `VOLUME_UP`, `VOLUME_DOWN`, `POWER`, Android'de ayrıca `APP_SWITCH` ve `DPAD_*` tuşları. Uygulamalar, deep link'ler, ekran yönü ve klavye için bkz. [Cihaz](device).

Sistem uyarıları (iOS izin ve izleme pencereleri, Android çalışma zamanı izinleri) `mobile.alerts: accept | dismiss` ile otomatik cevaplanabilir ([Yapılandırma](configuration)).
