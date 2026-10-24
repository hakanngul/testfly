---
id: troubleshooting
title: Mobil Sorun Giderme
sidebar_position: 5
description: "TestFly native mobil modülünün istisnaları, mobilecli, cihazlar ve locator'larla ilgili sık sorunlar ve mevcut sınırlar."
---

# Mobil Sorun Giderme

## İstisnalar

| İstisna | Ne zaman |
|---|---|
| `MobileServerUnavailableException` | mobilecli'ye ulaşılamıyor ve başlatılamadı: mobilecli yok (`mobilecliPath`), kapandı veya `serverStartMs` içinde cevap vermedi; mesaj log dosyasını gösterir |
| `MobileVersionException` | mobilecli 1.0.20'den eski |
| `DeviceSelectionException` | seçiciler sıfır veya birden çok çevrimiçi cihazla eşleşiyor ya da havuzdaki hiçbir cihaz çevrimiçi değil |
| `DeviceNotFoundException` | mobilecli cihazı tanımıyor |
| `MobileLocatorException` | strict mode: birden çok öğe eşleşti; geçersiz seçici dizesi; koşan platform için seçicisi olmayan sayfa alanı |
| `MobileTimeoutException` | bir eylem, `@WaitFor` veya istek süresini aştı; mesaj son görülen öğeleri listeler |
| `MobileAssertionError` | bir `expect(...)` zamanında geçmedi |
| `MobileRpcException` | diğer mobilecli hataları; başarısız bir ekran okuması ajan kontrolünü belirtir |

## Sık sorunlar

| Belirti | Neden ve çözüm |
|---|---|
| `mobilecli not found at 'mobilecli'` | mobilecli, test JVM'inin PATH'inde değil (IDE'lerin PATH'i çoğu zaman daha kısadır). `mobile.mobilecliPath`'i tam yola ayarlayın (`which mobilecli`). |
| `did not answer within 20000 ms` | otomatik başlatılan sunucu yavaş veya hata veriyor; `target/mobilecli-autostart.log` dosyasını okuyun ya da `timeouts.serverStartMs`'i artırın. |
| `Native mobile needs a 'mobile:' section` | etkin profilde `mobile:` bloğu yok; `-Dtestfly.profile=<ad>` verin veya bloğu ekleyin. |
| iOS'ta ilk `tap` veya `@WaitFor` zaman aşımına düşüyor ("reading the view tree timed out") | DeviceKit yeni simülatörde hâlâ açılıyor. Ekranı bir kez uzun zaman aşımıyla okuyun (bkz. [CI](ci#steps-of-a-job)) ya da `timeouts.rpcMs`'i artırın. |
| iOS'ta her ekran okuması başarısız, mesaj ajanı gösteriyor | mobilecli ajanı eksik: `mobilecli agent install --device <id>` (Android ajan gerektirmez). |
| Android emülatöründe `DeviceSelectionException` | kimlik `emulator-5554` değil, AVD adıdır (`Pixel_8_API_34`). |
| `Strict mode: … matched 2 elements` | sorgu belirsiz; örneğin bir Ayarlar satırı, aynı etikete sahip bir StaticText de içeren bir Button'dır. Bir rol (`getByRole(BUTTON, "General")`), test id veya `first()` kullanın. |
| Bir page object alanı `null` | alanın başlangıç değeri (`= null`) locator'ı ezdi; kaldırın. |
| `LoginPage.x: no android selector` | `@MobileFind`'ta `android` (veya `value`) seçicisi yok; Android id'sini ekleyin. |
| Bir Cucumber adımı sayfa oluştururken zaman aşımına düşüyor | sayfanın ekranı çoktan gitti; önceki adımdaki sayfa örneğini tutun. |
| Metinler eşleşmiyor | cihaz dili metinlerden farklı; test id'lerini tercih edin. |

## Sınırlar

- Test başına bir cihaz; JVM genelindeki rezervasyon bir cihazı aynı anda yalnızca bir testin kullanmasına izin verir. Havuz olmadan paralel worker'lar tek cihazı sırayla paylaşır. Cihazları paylaşan ayrı JVM'ler veya makineler koordine edilmez.
- Uygulama durumu tarayıcı çerezleri gibi saklanamaz; bu yüzden mobil `@PreCondition`, `mobile.app.reset: none` uygulama durumunu korumadıkça her testten önce yeniden çalışır. Cucumber `@PreCondition`'ı okumaz; `Background` kullanın.
- `launchApp` uygulamanın ön plana gelmesini beklemez; bunun yerine ilk locator çağrısı bekler.
- WebView ve WebSocket desteği henüz yok. Ekran kaydı yerel bir mobilecli sunucusu gerektirir; kayıt yapamayan bir cihaz uyarı yazar ve videosuz çalışır.
- Özel backend'ler `io.testfly.mobile.spi.MobileDriver` arayüzünü uygulayıp `Mobile.connect(driver, settings)` ile cihaz açabilir.
