# TestFly Inspector

Tek VS Code eklentisinde iki inceleyici. **Mobile Inspector**, [mobilecli](https://github.com/mobile-next/mobilecli)
üzerine kurulu, Appium Inspector benzeri bir iOS/Android ekran inceleyicidir: cihaz ekranı, mobilecli'nin `@eN`
ref'leriyle UI ağacı, seçtiğiniz öğenin tüm özellikleri ve page object'e yapıştırılacak TestFly seçicisi ve
`@MobileFind` alanı. **Browser Inspector** aynısını Chrome veya Edge'deki web sayfaları için yapar: sayfa, DOM ağacı
ve eşleşme sayılarıyla sıralı TestFly `Locator`'ları. [English](README.md)

Etkinlik çubuğundaki **TestFly** simgesi, **Mobile / Browser** geçişi olan bir görünüm açar.

![Mobile Inspector](media/screenshot.png)

## Neler yapar

- **Ekran**: üzerine gelince çerçevelenen cihaz ekran görüntüsü; tıklayınca imlecin altındaki en küçük öğe seçilir. **Tap** modunda tıklama cihaza dokunur, yarım saniye basılı tutmak uzun basar, sürüklemek başladığı yerden bittiği yere kaydırır; her birinden sonra ekran yeniden okunur.
- **Live**: ekranın üstündeki **Live** anahtarı, siz uygulamayı cihazda kullanırken ekranı 2, 3, 5 veya 10 saniyede bir yeniden okur; seçim, açık dallar ve arama sorgusu korunur. Varsayılan kapalıdır; `L` açıp kapatır. Bir okuma öncekini bekler, panel gizliyken ya da bir öğeye metin yazarken durur ve bir hatadan sonra Live kendini kapatır.
- **Record** (codegen): **Record** modunda her dokunma, uzun basma, sürükleme, yazılan metin (öğe paneli), kaydırma ve cihaz butonu cihazda çalışır ve bir adım olur (bir öğe üzerinde başlayan sürükleme `element.swipe(SwipeDirection.UP)` olur). **Recorded** sekmesi, her öğe için bir `@MobileFind` alanı (en iyi tekil seçici) ve `recordedFlow()` metodu olan bir `MobilePage` gösterir; **Copy** panoya kopyalar.
- **UI ağacı**: tipler, metinler, id'ler ve `@eN` ref'leriyle `mobilecli dump ui` ağacı; ok tuşlarıyla gezinme. Arama aracı Appium Inspector'daki *Search for element* gibi çalışır: bir strateji (TestFly selector, id, accessibility id, XPath, class name, text, contains text) ve bir seçici, TestFly'ın kendi kurallarıyla eşleştirilir; eşleşmeler vurgulanır, *1 of N*, `Enter` / `Shift+Enter` gezinir, kopyalama düğmesi `@MobileFind` için TestFly seçicisini verir. **Snapshot** sekmesi `mobilecli snapshot` ile aynı metni gösterir.
- **Öğe**: özellikler, durum bayrakları ve sınırlar; sıralı TestFly seçicileri (id, `role=…;name=…`, `label=`, `placeholder=`, `text=`, `xpath=`) ve her birinin bu ekranda kaç öğeyle eşleştiği (✓ tekil veya *N eşleşme*); tek tek ya da `@MobileFind` alanı olarak kopyalanır.
- **Eylemler**: dokunma, uzun basma, dokunup yazma, Back, Home, kaydırma; karşılık gelen `mobilecli io tap --device … @eN` komutunu kopyalama.
- **Sunucu**: cihaz listesinin yanındaki düğme mobilecli sunucusunun cevap verip vermediğini gösterir (yeşil açık, kırmızı kapalı) ve sunucuyu başlatır ya da durdurur. VS Code dışında başlatılmış bir sunucu da durdurulabilir (JSON-RPC `server.shutdown`); durdurduktan sonra eklenti, siz düğmeye basana kadar sunucuyu yeniden başlatmaz.

## Editörde eşleşme sayıları

Bir inspector açıkken Java dosyalarında her TestFly locator'ının üstünde, incelenen ekranda veya sayfada kaç öğeyle eşleştiğini gösteren bir satır çıkar: `✓ 1 match on Pixel 8`, `⚠ 3 matches on shop.example.com`, `✗ no match`; cihazın platformu için seçicisi olmayan bir `@MobileFind` için `no iOS selector`. Kapsadıkları: `@MobileFind` alanları (platformun seçicisi `MobilePages`'ın seçtiği gibi), `screen().getByTestId/getByText/getByLabel/getByPlaceholder/getByRole(MobileRole…)` çağrıları ve web'de `getByTestId`, `getByRole(Role…)`, `getByLabel`, `getByPlaceholder`, `getByText`, `getByAltText`, `getByTitle`, `findById`, `findByName`, `findByClassName`, `findByXpath` ve `find("css")`. Yalnızca sabit metin argümanlar okunur; bir sabitten kurulan locator atlanır. Sayılar her yenilemeyle (ve Live ile) güncellenir. Satıra tıklamak eşleşmeleri inspector'ın arama kutusunda gösterir. `testflyInspector.codeLens` ile kapatılır.

## Browser Inspector

- **Başlatma**: TestFly görünümü → **Browser**: sayfa URL'si, bulunan bir tarayıcı (Chrome, Edge, Chromium), viewport, **Show browser window** (varsayılan kapalı: sayfa yalnızca inspector'da görünür) ve **Keep session** (çerez ve oturumlar bir TestFly profilinde korunur; **Clear saved session** siler), sonra **Launch and inspect**. Son sayfalar tek tıkla açılır. Komut paleti → **TestFly: Open Browser Inspector** bir URL sorar. Araç çubuğundaki **Close browser** ya da sekmeyi kapatmak tarayıcıyı kapatır.
- **Sayfa** (üstte): sayfanın tamamı, üzerine gelince çerçevelenen tek bir ekran görüntüsü olarak; tıklayınca imlecin altındaki en küçük öğe seçilir. **Click** modunda tıklama tarayıcıda o noktaya tıklar ve sayfa yeniden okunur. Geri, ileri, yenile ve adres çubuğu.
- **DOM ağacı** (sol alt): etiketler, metinler, test id'ler, id'ler ve sınıflar. Açık shadow root'lar ve aynı kökenli iframe'ler, DevTools'taki gibi `#shadow-root` ve `#document` düğümleri olarak görünür; başka kökenli bir iframe incelenmedi olarak gösterilir. Arama kutusu Chrome DevTools'taki *Find by string, selector, or XPath* gibi çalışır (`#homepage-layout`, `.widget`, `//div[@id='x']` veya metin), seçiciler shadow root ve frame'lerin içinde de aranır: eşleşmeler vurgulanır, *1 of N*, `Enter` / `Shift+Enter` aralarında gezinir.
- **Locators** (sağ alt): `getByTestId`, `getByRole(Role.…, "ad")`, `getByLabel`, `getByPlaceholder`, `findById`, `getByText` ve tekil bir `find("css")`; önce tekiller, her biri sayfadaki eşleşme sayısıyla. Sayılar sayfanın içinde TestFly'ın kendi kurallarıyla hesaplanır (`Role` seçicileri, erişilebilir ad, büyük-küçük harf duyarsız "içerir", en içteki metin). Tek tek ya da alan olarak kopyalanır: `private final Locator signIn = getByTestId("sign-in");`. Click ve Type seçilen öğe üzerinde çalışır. **Attributes** tüm özellikleri, rolü, erişilebilir adı ve durumu listeler.
  - Bir iframe içinde sayılar frame'in belgesinde hesaplanır ve öğeye nasıl ulaşılacağını bir `withinFrame(By.cssSelector("#card"), () -> cardNumber.click());` örneği gösterir.
  - Bir shadow root içinde TestFly locator'ları öğeye ulaşmaz; inspector `ShadowDom.pierce("#host", "#target")` (yalnızca CSS) ve bir `private WebElement payNow() { return ShadowDom.pierce(…); }` üyesi verir.
- **Record**: **Record** modunda sayfaya tıklamak tarayıcıda tıklar ve bir adım olur; Type, adres çubuğu, geri, ileri ve yenile de öyle. **Recorded** sekmesi her öğe için bir `Locator` alanı (en iyi tekil locator), `open("…")` ve `recordedFlow()` içeren bir `BasePage` gösterir; frame içindeki adımlar `withinFrame(...)` ile sarılır.
- **Console** ve **Network** sekmeleri: sayfanın konsol mesajları ve yakalanmamış hataları (seviye ve metin filtresi, sekmede hata sayısı) ile istekleri; durum, metot, tür, boyut ve süreyle (tür ve URL filtresi; başarısız ve 4xx/5xx istekler kırmızı). **Preserve log** açık değilse sayfa değişince ikisi de temizlenir; bir satıra tıklamak onu kopyalar.
- WebDriver veya ek paket gerekmez: tarayıcı bir pipe üzerinden DevTools protokolüyle yönetilir (`--remote-debugging-pipe`).

## Gereksinimler

- Kurulu [mobilecli](https://github.com/mobile-next/mobilecli) (`npm i -g mobilecli` veya release dosyası). Yerel sunucu cevap vermezse eklenti `mobilecli server start` çalıştırır.
- Açık bir simülatör, emülatör veya cihaz. iOS'ta mobilecli ajanı bir kez kurulur: `mobilecli agent install --device <udid>`.
- Browser Inspector: Chrome, Edge veya Chromium. Olağan kurulum yerinde bulunur ya da `testflyInspector.browserPath` ayarını girin.

## Kurulum ve kullanım

```bash
code --install-extension testfly-mobile-inspector-0.4.0.vsix
```

Etkinlik çubuğu → **TestFly**, veya komut paleti → **TestFly: Open Mobile Inspector** / **TestFly: Open Browser Inspector**. Mobil tuşlar: `R` yenile, `I` inceleme, `T` dokunma, `E` kayıt, `L` live, `/` arama, `Esc` seçimi kaldır. Tarayıcı tuşları: `R` yenile, `I` inceleme, `C` tıklama, `E` kayıt, `/` arama, `Esc` seçimi kaldır.

| Ayar | Varsayılan | |
|---|---|---|
| `testflyInspector.serverUrl` | `http://localhost:12000` | mobilecli sunucusu (JSON-RPC `/rpc`) |
| `testflyInspector.mobilecliPath` | `mobilecli` | sunucuyu başlatmak için |
| `testflyInspector.autoStartServer` | `true` | yerel sunucu kapalıysa başlat |
| `testflyInspector.autoRefreshAfterAction` | `true` | eylemden sonra ekranı yeniden oku |
| `testflyInspector.browserPath` | boş | Chrome, Edge veya Chromium dosyası; boşken kurulu olan bulunur |
| `testflyInspector.testIdAttribute` | `data-testid` | `getByTestId()`'nin okuduğu özellik; `locators.testIdAttribute` ile aynı tutun |
| `testflyInspector.codeLens` | `true` | bir inspector açıkken Java dosyalarında TestFly locator'larının üstünde eşleşme sayıları |

## Notlar

- Eşleşme sayıları TestFly'ın locator kurallarının sadeleştirilmiş hâline göre hesaplanır; XPath sayımı TestFly'ın XPath motoruyla aynı belge yapısını kullanır. Önemli bir seçiciyi testte de doğrulayın.
- Ekran video yayını değil, yenilemede (Live ile birkaç saniyede bir) alınan anlık görüntüdür.
- Dokunmalar `device.io.tap` ile öğenin ortasına gider; mobilecli JSON-RPC'sinde ref ile dokunma yok (sadece CLI'da var), bu yüzden ref'ler bilgi ve kopyalanan CLI komutu içindir.

## Geliştirme

```bash
npm ci
npm test                 # seçici, RPC istemci ve tarayıcı testleri (node:test); tarayıcı testi Chrome/Edge/Chromium
                         # bulunduğunda ya da TESTFLY_TEST_BROWSER birini gösterdiğinde çalışır
node test/preview.js .   # webview'i sahte cihazla çizer (Playwright + Chromium gerekir)
npm run package          # testfly-mobile-inspector-<sürüm>.vsix üretir
```
