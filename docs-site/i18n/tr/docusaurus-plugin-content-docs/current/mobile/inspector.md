---
id: inspector
title: Mobile Inspector (VS Code ve IntelliJ)
sidebar_position: 9
description: "VS Code ve IntelliJ IDEA için TestFly Mobile Inspector: canlı cihaz ekranı, UI ağacı, öğe öznitelikleri, sıralı TestFly seçicileri, @MobileFind alanları, cihaz kontrolleri ve Record modu ile kod üretimi."
---

# Mobile Inspector

Mobile Inspector, TestFly ve mobilecli için yapılmış, Appium Inspector benzeri bir araçtır. IDE'nizin içinde, aynı arayüzle bir **VS Code eklentisi** ve bir **IntelliJ IDEA plugin'i** olarak çalışır ve page object yazmanıza yardım eder:

- cihaz ekranı; imlecin altındaki öğe çerçevelenir;
- mobilecli'nin `@eN` ref'leriyle UI ağacı ve mobilecli'nin metin anlık görüntüsü;
- seçili öğenin tüm öznitelikleri;
- o anki ekrandaki eşleşme sayılarıyla sıralanmış, `@MobileFind` alanı olarak yapıştırmaya hazır TestFly seçicileri;
- cihazda dokunma, yazma, kaydırma ve donanım butonları; ya da ekran görüntüsünde basılı tutma ve sürükleme;
- **Live**: siz uygulamayı kullanırken ekran birkaç saniyede bir yeniden okunur;
- **Record** modu: cihazı kullanın, attığınız adımlarla bir page object alın;
- VS Code'da page object'lerinizdeki `@MobileFind` alanlarının üstünde eşleşme sayıları.

![Mobile Inspector](/img/mobile/inspector.png)

## Kurulum

İki paket de depoda; marketplace erişimi gerekmez.

**VS Code** (1.85 veya üstü):

```bash
code --install-extension tools/vscode-mobile-inspector/dist/testfly-mobile-inspector-0.5.0.vsix
```

veya Extensions görünümü → `…` → **Install from VSIX…**.

**IntelliJ IDEA** (2024.1 veya üstü, Community veya Ultimate ve diğer IntelliJ tabanlı IDE'ler): Settings → Plugins → ⚙ → **Install Plugin from Disk…** → `tools/intellij-mobile-inspector/dist/testfly-mobile-inspector-0.4.0.zip`, sonra IDE'yi yeniden başlatın. Önce eski sürümü kaldırın.

**İkisi de** mobilecli 1.0.20 veya üstü ile açık bir simülatör, emülatör ya da bağlı bir cihaz ister. iOS'ta mobilecli agent'ını cihaz başına bir kez kurun (`mobilecli agent install --device <udid>`). `http://localhost:12000` cevap vermezse inspector `mobilecli server start`'ı kendisi çalıştırır. Cihaz listesinin yanındaki **Server** düğmesi sunucunun cevap verip vermediğini gösterir ve sunucuyu başlatır ya da durdurur; bunu kendi başlattığınız bir sunucu için de yapar. Durdurduktan sonra siz düğmeye basana kadar sunucu yeniden başlatılmaz.

## Açmak

- **VS Code:** soldaki etkinlik çubuğunda **TestFly** simgesi → **Mobile** → **Open Mobile Inspector**, veya komut paleti → **TestFly: Open Mobile Inspector**. Aynı görünümün **Browser** tarafı web sayfaları için [Browser Inspector](../guides/browser-inspector.md)'ı açar.
- **IntelliJ:** soldaki **TestFly Mobile** araç penceresi → **Open Mobile Inspector**, veya **Tools → TestFly: Open Mobile Inspector**. Inspector bir editör sekmesi olarak açılır.

Tek bir çevrimiçi cihaz varsa hemen seçilip okunur; yoksa cihaz seçiciden seçin ya da cihaz listesinde **Inspect**'e basın.

## Cihaz listesi {#devices}

TestFly görünümünün **Mobile** tarafı (VS Code) ve **TestFly Mobile** araç penceresi (IntelliJ), mobilecli'nin bildiği tüm cihazları açık ya da kapalı, üç grupta listeler: **Android** (emülatörler ve telefonlar), **iOS simulators** ve **iOS devices**.

| Satır | Düğmeler |
|---|---|
| **READY**: çevrimiçi | **Inspect** inspector'ı o cihazda açar; emülatör ve simülatörlerde ayrıca **Shut down** (⏻) vardır |
| kapalı emülatör veya simülatör | **Boot** başlatır ve hazır olana kadar bekler (soğuk bir Android açılışı bir iki dakika sürebilir) |
| **UNAUTHORIZED** (Android) | telefon *USB hata ayıklamaya izin ver* onayını bekliyor; telefonda onaylayın |
| **Wi‑Fi** (Android) | ağ üzerinden bağlı; ✕ düğmesi bağlantıyı keser (`adb disconnect`) |

Liste görünürken 10 saniyede bir yeniden okunur; taktığınız, başka yerden başlattığınız veya kapattığınız cihazlar kendiliğinden görünür; **Reload** hemen okur. Simülatörler ilk açılışlarından sonra, emülatörler Android SDK'nın AVD'lerinden listelenir. *Devices* yanındaki yeşil veya kırmızı nokta mobilecli sunucusunun cevap verip vermediğini gösterir.

### Gerçek cihazlar

Bir telefon, mobilecli ona ulaşabildiğinde listelenir:

- **Android**: Geliştirici seçenekleri → **USB hata ayıklama** açık ve telefonda *USB hata ayıklamaya izin ver* onaylanmış olmalı. O zamana kadar mobilecli telefonu listelemez; liste onu yine de `adb devices`'tan **UNAUTHORIZED** olarak gösterir (adb `ANDROID_HOME`, `ANDROID_SDK_ROOT`, SDK'nın varsayılan klasörü veya `PATH`'te aranır).
- **iPhone**: kilidi açık, *Bu Bilgisayara Güven* onaylanmış ve **Geliştirici Modu** açık olmalı (Ayarlar → Gizlilik ve Güvenlik). mobilecli kilitli veya güvenilmeyen bir telefonu atlar. Ekranı okumak için ayrıca telefona bir kez mobilecli agent'ı kurulur: `mobilecli agent install --device <udid> --provisioning-profile <dosya>` (agent'ı cihazınız için imzalayabilen bir provisioning profile).

### Wi‑Fi üzerinden

- **Android 11+**: telefonda Geliştirici seçenekleri → **Kablosuz hata ayıklama**, bilgisayarla aynı ağda. Cihaz listesinde **Connect Android over Wi‑Fi**'ı açın: eşleştirmek için (bir kez) *Eşleme koduyla cihaz eşle* altındaki adresi ve 6 haneli kodu, bağlanmak için Kablosuz hata ayıklama ekranındaki adresi kodsuz girin. Bu `adb pair` ve `adb connect` çalıştırır; telefon sonra **Wi‑Fi** rozetiyle görünür ve kablolu gibi incelenir. Eski telefonlar: USB üzerinden `adb tcpip 5555`, sonra `<ip>:5555`'e bağlanın.
- **iPhone**: bir kez kabloyla bağlayıp Xcode → Devices and Simulators'da *Connect via network*'ü açın. Aynı ağda kablosuz da listelenir (mobilecli ağdaki cihazları da bulur). Ekranın Wi‑Fi üzerinden okunup okunamayacağı mobilecli'nin agent'a bağlantısına bağlıdır; okuma başarısız olursa kabloyu kullanın.

## Pencere

| Alan | Ne gösterir |
|---|---|
| Araç çubuğu | platform rozetiyle cihaz seçici, **Refresh**, tıklama modu (Inspect / Tap / Record), cihaz kontrolleri, durum ve son okuma süresi |
| **Screen** | ekran görüntüsü; üzerine gelmek bir öğeyi çerçeveler, tıklamak seçer (Inspect) veya dokunur (Tap, Record). **Live** birkaç saniyede bir yeniden okur, **Bounds** tüm öğeleri çerçeveler |
| **UI Tree** | türler, metinler ve `@eN` ref'leriyle öğe ağacı ve Appium tarzı bir arama aracı; ok tuşlarıyla gezinme ve açma |
| **Snapshot** | mobilecli'nin ekran metin anlık görüntüsü (`mobilecli snapshot`), **Copy** ile |
| **Recorded** | Record modunun kodu (aşağıda) |
| **Element** | seçili öğe: eylemler, TestFly seçicileri, page object alanı, öznitelikler ve durum |

Paneller kenarlarından sürüklenerek boyutlandırılır; boyutlar, mod ve açık ağaç düğümleri hatırlanır.

## Seçici bulmak

Ekranda veya ağaçta bir öğe seçin. **Element** paneli TestFly'ın onun için kullanabileceği tüm seçicileri en iyiden başlayarak listeler:

1. test id (`resource-id` / accessibility identifier), tam ve kısa hali;
2. adıyla rol (`role=button;name=Giriş`);
3. label, placeholder, metin;
4. id veya metinle XPath, son çare olarak konumla.

Her seçici o anki ekranda kaç öğeyle eşleştiğini gösterir: **✓ unique**, **N matches** veya **no match**. XPath sayıları TestFly'ın değerlendirdiği ağacın aynısını kullanır; framework ile tutarlıdır. En iyi tekil seçici vurgulanır ve hazır bir alan olarak gösterilir:

```java
@MobileFind(android = "com.demo:id/btnLogin")
private MobileLocator btnLogin;
```

Bir seçiciyi, alanı veya herhangi bir öznitelik değerini (üzerine tıklayarak) kopyalayın. Alan adları id'den veya label'dan gelir (`Giriş Yap` → `girisYap`). Inspector'ı her platform için bir kez çalıştırıp `ios` ve `android` seçicilerini tek bir `@MobileFind`'da birleştirin.

## Öğe aramak

Ağacın üstündeki kutu, Appium Inspector'daki **Search for element** gibi çalışır: bir strateji seçin, seçiciyi yazın ve Enter'a basın.

| Strateji | Örnek | Karşılığı |
|---|---|---|
| TestFly selector | `btnLogin`, `text=Giriş`, `role=button;name=Go`, `//android.widget.Button` | `MobileScreen.locate(…)` ve `@MobileFind`; `/` veya `(` ile başlayan değer XPath olarak okunur |
| id | `com.app:id/login` veya `login` | `id=…` (resource-id veya accessibility identifier, kısa hali de) |
| accessibility id | `Login` | `accessibility=…` (iOS identifier veya name, Android content-desc) |
| XPath | `//android.widget.EditText[@text='Email']` | `xpath=…`, TestFly'ın XPath'inin kullandığı ağaç belgesinde |
| class name | `android.widget.CheckBox`, `XCUIElementTypeButton` | `type=…` (büyük-küçük harf duyarsız) |
| text | `Giriş` | `text=…` (birebir) |
| contains text | `bol` | bir metnin, label'ın, id'nin, türün veya `@eN` ref'inin herhangi bir parçası; yalnızca inspector'da |

Ağaç filtrelenmez: eşleşmeler vurgulanır, kutuda *1 of N* görünür ve ilk eşleşme seçilir. **Enter** / **Shift+Enter** (veya oklar) eşleşmeler arasında gezinir. Eşleşme TestFly'ın kendi kurallarıyla yapılır; burada bir öğeyi bulan seçici testte de bulur: `text=`, `label=` ve `placeholder=` birebirdir, `role=` `MobileRole`'ün native türlerini kullanır. Geçersiz bir seçici **Invalid** gösterir, sebebi ipucundadır. Kopyalama düğmesi sorguyu `@MobileFind` için TestFly seçicisi olarak panoya koyar. iOS `chain=` ve `predicate=` seçicileri inspector'da değerlendirilmez. Strateji hatırlanır ve sorgu her yenilemeden sonra yeniden çalışır.

## Cihazda işlem yapmak

- **Element paneli:** **Tap** ve **Long press** öğenin ortasına; **Type** öğeye dokunur ve yanındaki alandaki metni yazar (Enter da yazar); **Copy CLI tap** karşılık gelen `mobilecli io tap` komutunu kopyalar.
- **Tap modu** (`T`): ekrana tıklamak cihazda o noktaya dokunur. Fare düğmesini yarım saniye basılı tutmak uzun basar; sürüklemek, bastığınız yerden bıraktığınız yere kaydırır (sürükleme kesikli bir çizgiyle görünür).
- **Araç çubuğu:** Back (Android), Home ve ekranın ortasından yukarı, aşağı, sola, sağa kaydırma. Yukarı kaydırma listeyi aşağı kaydırır; sola kaydırma carousel'i ileri götürür.

Her eylemden sonra ekran yeniden okunur (aşağıdaki ayar). Bir cihazın ilk okuması en uzun sürer; sonraki okumalar ağacı ve sıkıştırılmış ekran görüntüsünü paralel alır ve görüntüyü gelir gelmez gösterir.

## Live

Uygulamayı cihazda kullanırken inspector'ın sizi takip etmesi için ekranın üstündeki **Live**'ı açın (`L`): ekran ve ağaç 2, 3, 5 veya 10 saniyede bir yeniden okunur (anahtarın yanındaki liste). Seçim, açık ağaç dalları ve arama sorgusu korunur; ekranda bir şey değişmediyse yalnızca görüntü yenilenir. Bir okuma ancak önceki bittikten sonra başlar; Live panel gizliyken veya bir öğe için metin yazarken durur ve bağlantısı kopan bir cihaz gibi bir hatadan sonra kendini kapatır. Varsayılan olarak kapalıdır ve panel başına hatırlanır.

## Record modu (kod üretimi) {#record}

**Record**'a geçin (`E`; ekran kırmızı çerçeve alır). Artık yaptığınız her şey cihazda çalışır **ve** bir adım olur:

| Yaptığınız | Kaydedilen |
|---|---|
| ekranda bir öğeye tıklamak | `alan.tap();` |
| Element panelinde **Long press** | `alan.longPress();` |
| Element panelinde **Type** | `alan.fill("metin");` |
| öğe olmayan bir noktaya tıklamak | `screen().tap(x, y);` |
| bir öğe (veya nokta) üzerinde basılı tutmak | `alan.longPress();` (`screen().longPress(x, y);`) |
| bir öğe üzerinde başlayan sürükleme | `alan.swipe(SwipeDirection.UP);` (sürüklemenin yönü) |
| bir kaydırma butonu veya öğe üzerinde olmayan sürükleme | `screen().swipe(SwipeDirection.UP);` |
| Back / Home | `device().pressButton("BACK");` |

**Recorded** sekmesi (rozeti adım sayısını gösterir) her öğe için en iyi tekil seçiciyle bir `@MobileFind` alanı olan bir page object ve sırasıyla adımları gösterir:

![Record modu](/img/mobile/inspector-record.png)

```java
public class RecordedPage extends MobilePage {

    @MobileFind(android = "com.demo:id/etUserName")
    private MobileLocator etUserName;

    @MobileFind(android = "com.demo:id/btnSubmit")
    private MobileLocator btnSubmit;

    public void recordedFlow() {
        etUserName.fill("12345");
        btnSubmit.tap();
        screen().swipe(SwipeDirection.UP);
    }
}
```

**Copy** panoya kopyalar, **Clear** baştan başlatır. İlk taslak olarak görün: sınıfı ve alanları yeniden adlandırın, `recordedFlow()`'u sonraki sayfayı döndüren niyet metotlarına bölün, ekranı belirleyen alana `@WaitFor` ekleyin, doğrulamalar ve diğer platformun seçicilerini ekleyin. Yazılan metin yazdığınız gibi kaydedilir; kimlik bilgilerini ortam değişkenleriyle değiştirin.

## Editörde eşleşme sayıları {#code-lens}

Mobile Inspector bir ekran gösterirken Java dosyalarında (IntelliJ'de Kotlin dosyalarında da) her `@MobileFind` alanının ve her `screen().getByTestId(…)`, `getByText`, `getByLabel`, `getByPlaceholder`, `getByType` ve `getByRole(MobileRole.…, "ad")` çağrısının üstünde bir satır çıkar:

```java
✓ 1 match on Pixel 8
@MobileFind(android = "com.demo:id/btnLogin", ios = "loginButton")
private MobileLocator login;

✗ no match on Pixel 8
@MobileFind("text=Cancel")
private MobileLocator cancel;
```

Seçici, TestFly'ın cihazın platformunda kullandığıdır: türlü bir öznitelik (`androidId`, `iosXpath`, …), yoksa `android` / `ios`, yoksa `value`; platform için seçicisi olmayan bir alan *no iOS selector* gösterir. Yalnızca sabit metinler okunur; bir sabitten kurulan seçici atlanır. Sayılar her yenilemeyi ve Live'ı izler. Bir satıra tıklayınca eşleşmeleri arama aracında görürsünüz. [Browser Inspector](../guides/browser-inspector.md#code-lens) aynısını web locator'ları için yapar. VS Code'da `testflyInspector.codeLens` ile kapatılır; IntelliJ'de bunlar code vision ipuçlarıdır ve Settings → Editor → Inlay Hints → Code vision'dan diğer code vision ipuçlarıyla birlikte gizlenir.

## Tuşlar

| Tuş | Eylem |
|---|---|
| `R` | ekranı yenile |
| `I`, `T`, `E` | Inspect, Tap, Record modu |
| `L` | Live aç / kapat |
| `/` | arama aracına odaklan; `Enter` / `Shift+Enter` sonraki / önceki eşleşme |
| `↑` `↓` `←` `→` | ağaçta gezin, kapat / aç |
| `Esc` | seçimi kaldır |

## Ayarlar

| VS Code (`settings.json`) | IntelliJ (Settings → Tools → TestFly Mobile Inspector) | Varsayılan | Anlamı |
|---|---|---|---|
| `testflyInspector.serverUrl` | mobilecli sunucu URL'si | `http://localhost:12000` | mobilecli sunucusu (JSON-RPC `/rpc`) |
| `testflyInspector.mobilecliPath` | mobilecli yolu | `mobilecli` | sunucuyu başlatan dosya; IDE'nin PATH'inde yoksa tam yolu girin |
| `testflyInspector.autoStartServer` | sunucuyu otomatik başlat | `true` | cevap veren yoksa yerel bir sunucu başlatır (uzak URL'ler için asla) |
| `testflyInspector.autoRefreshAfterAction` | eylemlerden sonra yenile | `true` | dokunma, yazma, kaydırma ve butonlardan sonra ekranı yeniden okur |
| `testflyInspector.codeLens` | — | `true` | bir inspector açıkken Java dosyalarında locator'ların üstünde eşleşme sayıları |

Inspector'ın başlattığı sunucu inspector kapanınca durdurulur; zaten çalışan bir sunucuya dokunulmaz.

## Sorun giderme

| Belirti | Çözüm |
|---|---|
| "mobilecli server … is not reachable" | mobilecli kurulu değil veya IDE'nin PATH'inde yok: mobilecli yolu ayarını girin ya da `mobilecli server start`'ı kendiniz çalıştırın |
| Cihaz seçicide cihaz yok | bir simülatör/emülatör açın (`mobilecli devices` onu online göstermeli), sonra seçicinin yanındaki yenile butonu |
| iOS: her okuma agent'ı anarak hata veriyor | `mobilecli agent install --device <udid>` |
| IntelliJ: "needs JCEF" | IDE gömülü tarayıcısı olmayan bir runtime ile çalışıyor; JCEF içeren bir JetBrains Runtime'a geçin (Help → Find Action → Choose Boot Java Runtime) |
| Eklenti etkinlik çubuğunda yok | kurduktan sonra pencereyi yeniden yükleyin (VS Code) veya IDE'yi yeniden başlatın (IntelliJ) |
