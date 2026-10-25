# TestFly Inspector

Tek VS Code eklentisinde iki inceleyici. **Mobile Inspector**, [mobilecli](https://github.com/mobile-next/mobilecli)
üzerine kurulu, Appium Inspector benzeri bir iOS/Android ekran inceleyicidir: cihaz ekranı, mobilecli'nin `@eN`
ref'leriyle UI ağacı, seçtiğiniz öğenin tüm özellikleri ve page object'e yapıştırılacak TestFly seçicisi ve
`@MobileFind` alanı. **Browser Inspector** aynısını Chrome veya Edge'deki web sayfaları için yapar: sayfa, DOM ağacı
ve eşleşme sayılarıyla sıralı TestFly `Locator`'ları. [English](README.md)

Etkinlik çubuğundaki **TestFly** simgesi, **Mobile / Browser** geçişi olan bir görünüm açar.

![Mobile Inspector](media/screenshot.png)

## Neler yapar

- **Ekran**: üzerine gelince çerçevelenen cihaz ekran görüntüsü; tıklayınca imlecin altındaki en küçük öğe seçilir. **Tap** modunda tıklama cihaza dokunur ve ekran yeniden okunur.
- **Record** (codegen): **Record** modunda her dokunma, yazılan metin (öğe paneli), kaydırma ve cihaz butonu cihazda çalışır ve bir adım olur. **Recorded** sekmesi, her öğe için bir `@MobileFind` alanı (en iyi tekil seçici) ve `recordedFlow()` metodu olan bir `MobilePage` gösterir; **Copy** panoya kopyalar.
- **UI ağacı**: tipler, metinler, id'ler ve `@eN` ref'leriyle `mobilecli dump ui` ağacı; `/` ile filtre, ok tuşlarıyla gezinme. **Snapshot** sekmesi `mobilecli snapshot` ile aynı metni gösterir.
- **Öğe**: özellikler, durum bayrakları ve sınırlar; sıralı TestFly seçicileri (id, `role=…;name=…`, `label=`, `placeholder=`, `text=`, `xpath=`) ve her birinin bu ekranda kaç öğeyle eşleştiği (✓ tekil veya *N eşleşme*); tek tek ya da `@MobileFind` alanı olarak kopyalanır.
- **Eylemler**: dokunma, uzun basma, dokunup yazma, Back, Home, kaydırma; karşılık gelen `mobilecli io tap --device … @eN` komutunu kopyalama.
- **Sunucu**: cihaz listesinin yanındaki düğme mobilecli sunucusunun cevap verip vermediğini gösterir (yeşil açık, kırmızı kapalı) ve sunucuyu başlatır ya da durdurur. VS Code dışında başlatılmış bir sunucu da durdurulabilir (JSON-RPC `server.shutdown`); durdurduktan sonra eklenti, siz düğmeye basana kadar sunucuyu yeniden başlatmaz.

## Browser Inspector

- **Başlatma**: TestFly görünümü → **Browser**: sayfa URL'si, bulunan bir tarayıcı (Chrome, Edge, Chromium), viewport, **Headless** ve **Keep session** (çerez ve oturumlar bir TestFly profilinde korunur), sonra **Launch and inspect**. Son sayfalar tek tıkla açılır. Komut paleti → **TestFly: Open Browser Inspector** bir URL sorar.
- **Sayfa** (üstte): sayfanın tamamı, üzerine gelince çerçevelenen tek bir ekran görüntüsü olarak; tıklayınca imlecin altındaki en küçük öğe seçilir. **Click** modunda tıklama tarayıcıda o noktaya tıklar ve sayfa yeniden okunur. Geri, ileri, yenile ve adres çubuğu.
- **DOM ağacı** (sol alt): etiketler, metinler, test id'ler, id'ler ve sınıflar; `/` ile filtre, ok tuşlarıyla gezinme.
- **Locators** (sağ alt): `getByTestId`, `getByRole(Role.…, "ad")`, `getByLabel`, `getByPlaceholder`, `findById`, `getByText` ve tekil bir `find("css")`; önce tekiller, her biri sayfadaki eşleşme sayısıyla. Sayılar sayfanın içinde TestFly'ın kendi kurallarıyla hesaplanır (`Role` seçicileri, erişilebilir ad, büyük-küçük harf duyarsız "içerir", en içteki metin). Tek tek ya da alan olarak kopyalanır: `private final Locator signIn = getByTestId("sign-in");`. Click ve Type seçilen öğe üzerinde çalışır. **Attributes** tüm özellikleri, rolü, erişilebilir adı ve durumu listeler.
- WebDriver veya ek paket gerekmez: tarayıcı bir pipe üzerinden DevTools protokolüyle yönetilir (`--remote-debugging-pipe`). iframe ve shadow root içi listelenmez.

## Gereksinimler

- Kurulu [mobilecli](https://github.com/mobile-next/mobilecli) (`npm i -g mobilecli` veya release dosyası). Yerel sunucu cevap vermezse eklenti `mobilecli server start` çalıştırır.
- Açık bir simülatör, emülatör veya cihaz. iOS'ta mobilecli ajanı bir kez kurulur: `mobilecli agent install --device <udid>`.
- Browser Inspector: Chrome, Edge veya Chromium. Olağan kurulum yerinde bulunur ya da `testflyInspector.browserPath` ayarını girin.

## Kurulum ve kullanım

```bash
code --install-extension testfly-mobile-inspector-0.3.0.vsix
```

Etkinlik çubuğu → **TestFly**, veya komut paleti → **TestFly: Open Mobile Inspector** / **TestFly: Open Browser Inspector**. Mobil tuşlar: `R` yenile, `I` inceleme, `T` dokunma, `E` kayıt, `/` filtre, `Esc` seçimi kaldır. Tarayıcı tuşları: `R` yenile, `I` inceleme, `C` tıklama, `/` filtre, `Esc` seçimi kaldır.

| Ayar | Varsayılan | |
|---|---|---|
| `testflyInspector.serverUrl` | `http://localhost:12000` | mobilecli sunucusu (JSON-RPC `/rpc`) |
| `testflyInspector.mobilecliPath` | `mobilecli` | sunucuyu başlatmak için |
| `testflyInspector.autoStartServer` | `true` | yerel sunucu kapalıysa başlat |
| `testflyInspector.autoRefreshAfterAction` | `true` | eylemden sonra ekranı yeniden oku |
| `testflyInspector.browserPath` | boş | Chrome, Edge veya Chromium dosyası; boşken kurulu olan bulunur |
| `testflyInspector.testIdAttribute` | `data-testid` | `getByTestId()`'nin okuduğu özellik; `locators.testIdAttribute` ile aynı tutun |

## Notlar

- Eşleşme sayıları TestFly'ın locator kurallarının sadeleştirilmiş hâline göre hesaplanır; XPath sayımı TestFly'ın XPath motoruyla aynı belge yapısını kullanır. Önemli bir seçiciyi testte de doğrulayın.
- Ekran canlı yayın değil, yenilemede alınan anlık görüntüdür.
- Dokunmalar `device.io.tap` ile öğenin ortasına gider; mobilecli JSON-RPC'sinde ref ile dokunma yok (sadece CLI'da var), bu yüzden ref'ler bilgi ve kopyalanan CLI komutu içindir.

## Geliştirme

```bash
npm ci
npm test                 # seçici, RPC istemci ve tarayıcı testleri (node:test); tarayıcı testi Chrome/Edge/Chromium
                         # bulunduğunda ya da TESTFLY_TEST_BROWSER birini gösterdiğinde çalışır
node test/preview.js .   # webview'i sahte cihazla çizer (Playwright + Chromium gerekir)
npm run package          # testfly-mobile-inspector-<sürüm>.vsix üretir
```
