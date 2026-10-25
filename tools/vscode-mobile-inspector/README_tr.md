# TestFly Mobile Inspector

[mobilecli](https://github.com/mobile-next/mobilecli) üzerine kurulu, VS Code içinde Appium Inspector benzeri
bir iOS/Android ekran inceleyici. Cihaz ekranını, mobilecli'nin `@eN` ref'leriyle UI ağacını, seçtiğiniz
öğenin tüm özelliklerini ve page object'e yapıştırılacak TestFly seçicisini ve `@MobileFind` alanını
gösterir. [English](README.md)

![Mobile Inspector](media/screenshot.png)

## Neler yapar

- **Ekran**: üzerine gelince çerçevelenen cihaz ekran görüntüsü; tıklayınca imlecin altındaki en küçük öğe seçilir. **Tap** modunda tıklama cihaza dokunur ve ekran yeniden okunur.
- **Record** (codegen): **Record** modunda her dokunma, yazılan metin (öğe paneli), kaydırma ve cihaz butonu cihazda çalışır ve bir adım olur. **Recorded** sekmesi, her öğe için bir `@MobileFind` alanı (en iyi tekil seçici) ve `recordedFlow()` metodu olan bir `MobilePage` gösterir; **Copy** panoya kopyalar.
- **UI ağacı**: tipler, metinler, id'ler ve `@eN` ref'leriyle `mobilecli dump ui` ağacı; `/` ile filtre, ok tuşlarıyla gezinme. **Snapshot** sekmesi `mobilecli snapshot` ile aynı metni gösterir.
- **Öğe**: özellikler, durum bayrakları ve sınırlar; sıralı TestFly seçicileri (id, `role=…;name=…`, `label=`, `placeholder=`, `text=`, `xpath=`) ve her birinin bu ekranda kaç öğeyle eşleştiği (✓ tekil veya *N eşleşme*); tek tek ya da `@MobileFind` alanı olarak kopyalanır.
- **Eylemler**: dokunma, uzun basma, dokunup yazma, Back, Home, kaydırma; karşılık gelen `mobilecli io tap --device … @eN` komutunu kopyalama.

## Gereksinimler

- Kurulu [mobilecli](https://github.com/mobile-next/mobilecli) (`npm i -g mobilecli` veya release dosyası). Yerel sunucu cevap vermezse eklenti `mobilecli server start` çalıştırır.
- Açık bir simülatör, emülatör veya cihaz. iOS'ta mobilecli ajanı bir kez kurulur: `mobilecli agent install --device <udid>`.

## Kurulum ve kullanım

```bash
code --install-extension testfly-mobile-inspector-0.2.0.vsix
```

Komut paleti → **TestFly: Open Mobile Inspector**. Tuşlar: `R` yenile, `I` inceleme, `T` dokunma, `E` kayıt, `/` filtre, `Esc` seçimi kaldır.

| Ayar | Varsayılan | |
|---|---|---|
| `testflyInspector.serverUrl` | `http://localhost:12000` | mobilecli sunucusu (JSON-RPC `/rpc`) |
| `testflyInspector.mobilecliPath` | `mobilecli` | sunucuyu başlatmak için |
| `testflyInspector.autoStartServer` | `true` | yerel sunucu kapalıysa başlat |
| `testflyInspector.autoRefreshAfterAction` | `true` | eylemden sonra ekranı yeniden oku |

## Notlar

- Eşleşme sayıları TestFly'ın locator kurallarının sadeleştirilmiş hâline göre hesaplanır; XPath sayımı TestFly'ın XPath motoruyla aynı belge yapısını kullanır. Önemli bir seçiciyi testte de doğrulayın.
- Ekran canlı yayın değil, yenilemede alınan anlık görüntüdür.
- Dokunmalar `device.io.tap` ile öğenin ortasına gider; mobilecli JSON-RPC'sinde ref ile dokunma yok (sadece CLI'da var), bu yüzden ref'ler bilgi ve kopyalanan CLI komutu içindir.

## Geliştirme

```bash
npm ci
npm test                 # seçici ve RPC istemci testleri (node:test)
node test/preview.js .   # webview'i sahte cihazla çizer (Playwright + Chromium gerekir)
npm run package          # testfly-mobile-inspector-<sürüm>.vsix üretir
```
