---
id: reports
title: Hata Raporları
sidebar_position: 8
description: "Başarısız bir mobil testin bıraktıkları: rapor adımları, cihaz ekran görüntüsü, görünüm ağacı metni, ekran kaydı ve crash raporları ile bunları yöneten ayarlar."
---

# Hata Raporları

Başarısız bir mobil test, yeniden çalıştırmadan açıklanabilmelidir. TestFly her eylemi bir adım olarak loglar; test düştüğünde de cihazın ne gösterdiğini, ekranda hangi öğelerin olduğunu, bir videoyu ve uygulama çöktüyse crash raporunu kaydeder.

## Adımlar

Her locator eylemi, eylemin ve sorgunun adını taşıyan bir rapor adımıdır: `Tap getByRole(button, name="Giriş")`, `Fill getByTestId("username")`. Yazılan metin asla loglanmaz. Eylem ve doğrulama hataları son nedeni söyler ve ekrandaki öğeleri listeler:

```
tap on getByText("Giriş") timed out after 5000 ms: the element is disabled.
  - android.widget.Button id="com.example:id/sign_in" text="Giriş" disabled [40,580 1000x120]
```

## Test düştüğünde ne kaydedilir

| Çıktı | Dosya | Ayar (varsayılan) |
|---|---|---|
| Cihaz ekran görüntüsü, HTML rapora da gömülür | `target/reports/screenshots/<test>_mobile_<zaman>.png` | `mobile.screenshotOnFailure` (`true`) |
| Metin olarak görünüm ağacı | `target/reports/screenshots/<test>_mobile_<zaman>.txt` | `mobile.viewTreeOnFailure` (`true`) |
| Ekran kaydı | `target/reports/videos/<test>_mobile_<zaman>.mp4` | `mobile.recordVideo` (`off`) |
| Test sırasında yazılan crash raporları | `target/reports/crashes/<test>_<crash id>.txt` | her zaman |

Kaydedilen her dosya, mutlak yoluyla birlikte başarısız testin bir adımı olarak loglanır. Kayıt asıl hatanın yerini asla almaz: cihaz cevap veremezse (çöktü, sunucu gitti) bir uyarı loglanır ve test kendi hatasıyla düşer.

TestNG, JUnit 5 ve Cucumber'da aynı çalışır.

## Görünüm ağacı {#view-tree}

`.txt` dosyası, mobilecli'nin hata anındaki ekranın metin anlık görüntüsüdür; öğe başına bir girintili satır:

```
@e1 [Window]
  @e2 [NavigationBar] "Giriş"
  @e3 [TextField] "Kullanıcı adı"
  @e4 [SecureTextField] "Şifre"
  @e5 [Button] "Giriş"
```

Ekran görüntüsünün cevaplayamadığı soruları cevaplar: hangi öğe id'leri ve etiketleri vardı, uygulamayı bir uyarı veya klavye örtüyor muydu. Ayrıca bir AI hata analizi için küçük ve okunur bir girdidir.

Aynı metni test sırasında `device().viewTreeText()` ile okuyun.

## Videolar {#videos}

`mobile.recordVideo: failures` her mobil testi kaydeder ve başarısız olanın videosunu saklar; `always` tüm videoları saklar. Ayrıntılar ve sınırlar için bkz. [Ekran kaydı](configuration#screen-recording).

## Crash raporları {#crash-reports}

Uygulama test sırasında çökerse sonraki adım zaman aşımıyla düşer. TestFly bunun üzerine cihaz açıldığından beri yazılan crash raporlarını kaydeder; böylece neden (çökmenin stack trace'i) hatanın yanında olur. Cihazdaki eski crash raporları kopyalanmaz. Kendiniz listelemek ve okumak için `device().crashReports()` ve `device().crashReport(id)`.

## Çıktıları kapatmak

```yaml
mobile:
  screenshotOnFailure: false
  viewTreeOnFailure: false
  recordVideo: off
```

Yalnızca disk alanının önemli olduğu çok büyük suite'lerde kapatın; başarısız test başına birkaç yüz milisaniyeye mal olurlar.
