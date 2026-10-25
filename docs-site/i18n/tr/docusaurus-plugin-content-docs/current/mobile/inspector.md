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
- cihazda dokunma, yazma, kaydırma ve donanım butonları;
- **Record** modu: cihazı kullanın, attığınız adımlarla bir page object alın.

![Mobile Inspector](/img/mobile/inspector.png)

## Kurulum

İki paket de depoda; marketplace erişimi gerekmez.

**VS Code** (1.85 veya üstü):

```bash
code --install-extension tools/vscode-mobile-inspector/dist/testfly-mobile-inspector-0.2.2.vsix
```

veya Extensions görünümü → `…` → **Install from VSIX…**.

**IntelliJ IDEA** (2024.1 veya üstü, Community veya Ultimate ve diğer IntelliJ tabanlı IDE'ler): Settings → Plugins → ⚙ → **Install Plugin from Disk…** → `tools/intellij-mobile-inspector/dist/testfly-mobile-inspector-0.2.0.zip`, sonra IDE'yi yeniden başlatın. Önce eski sürümü kaldırın.

**İkisi de** mobilecli 1.0.20 veya üstü ile açık bir simülatör, emülatör ya da bağlı bir cihaz ister. iOS'ta mobilecli agent'ını cihaz başına bir kez kurun (`mobilecli agent install --device <udid>`). `http://localhost:12000` cevap vermezse inspector `mobilecli server start`'ı kendisi çalıştırır.

## Açmak

- **VS Code:** soldaki etkinlik çubuğunda **TestFly Mobile** simgesi → **Open Mobile Inspector**, veya komut paleti → **TestFly: Open Mobile Inspector**.
- **IntelliJ:** soldaki **TestFly Mobile** araç penceresi → **Open Mobile Inspector**, veya **Tools → TestFly: Open Mobile Inspector**. Inspector bir editör sekmesi olarak açılır.

Tek bir çevrimiçi cihaz varsa hemen seçilip okunur; yoksa cihaz seçiciden seçin.

## Pencere

| Alan | Ne gösterir |
|---|---|
| Araç çubuğu | platform rozetiyle cihaz seçici, **Refresh**, tıklama modu (Inspect / Tap / Record), cihaz kontrolleri, durum ve son okuma süresi |
| **Screen** | ekran görüntüsü; üzerine gelmek bir öğeyi çerçeveler, tıklamak seçer (Inspect) veya dokunur (Tap, Record). **Bounds** tüm öğeleri çerçeveler |
| **UI Tree** | türler, metinler ve `@eN` ref'leriyle öğe ağacı; metin, id, tür veya ref ile filtreleme; ok tuşlarıyla gezinme ve açma |
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

## Cihazda işlem yapmak

- **Element paneli:** **Tap** ve **Long press** öğenin ortasına; **Type** öğeye dokunur ve yanındaki alandaki metni yazar (Enter da yazar); **Copy CLI tap** karşılık gelen `mobilecli io tap` komutunu kopyalar.
- **Tap modu** (`T`): ekrana tıklamak cihazda o noktaya dokunur.
- **Araç çubuğu:** Back (Android), Home ve ekranın ortasından yukarı, aşağı, sola, sağa kaydırma. Yukarı kaydırma listeyi aşağı kaydırır; sola kaydırma carousel'i ileri götürür.

Her eylemden sonra ekran yeniden okunur (aşağıdaki ayar). Bir cihazın ilk okuması en uzun sürer; sonraki okumalar ağacı ve sıkıştırılmış ekran görüntüsünü paralel alır ve görüntüyü gelir gelmez gösterir.

## Record modu (kod üretimi) {#record}

**Record**'a geçin (`E`; ekran kırmızı çerçeve alır). Artık yaptığınız her şey cihazda çalışır **ve** bir adım olur:

| Yaptığınız | Kaydedilen |
|---|---|
| ekranda bir öğeye tıklamak | `alan.tap();` |
| Element panelinde **Long press** | `alan.longPress();` |
| Element panelinde **Type** | `alan.fill("metin");` |
| öğe olmayan bir noktaya tıklamak | `screen().tap(x, y);` |
| bir kaydırma butonu | `screen().swipe(SwipeDirection.UP);` |
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

## Tuşlar

| Tuş | Eylem |
|---|---|
| `R` | ekranı yenile |
| `I`, `T`, `E` | Inspect, Tap, Record modu |
| `/` | ağacı filtrele |
| `↑` `↓` `←` `→` | ağaçta gezin, kapat / aç |
| `Esc` | seçimi kaldır |

## Ayarlar

| VS Code (`settings.json`) | IntelliJ (Settings → Tools → TestFly Mobile Inspector) | Varsayılan | Anlamı |
|---|---|---|---|
| `testflyInspector.serverUrl` | mobilecli sunucu URL'si | `http://localhost:12000` | mobilecli sunucusu (JSON-RPC `/rpc`) |
| `testflyInspector.mobilecliPath` | mobilecli yolu | `mobilecli` | sunucuyu başlatan dosya; IDE'nin PATH'inde yoksa tam yolu girin |
| `testflyInspector.autoStartServer` | sunucuyu otomatik başlat | `true` | cevap veren yoksa yerel bir sunucu başlatır (uzak URL'ler için asla) |
| `testflyInspector.autoRefreshAfterAction` | eylemlerden sonra yenile | `true` | dokunma, yazma, kaydırma ve butonlardan sonra ekranı yeniden okur |

Inspector'ın başlattığı sunucu inspector kapanınca durdurulur; zaten çalışan bir sunucuya dokunulmaz.

## Sorun giderme

| Belirti | Çözüm |
|---|---|
| "mobilecli server … is not reachable" | mobilecli kurulu değil veya IDE'nin PATH'inde yok: mobilecli yolu ayarını girin ya da `mobilecli server start`'ı kendiniz çalıştırın |
| Cihaz seçicide cihaz yok | bir simülatör/emülatör açın (`mobilecli devices` onu online göstermeli), sonra seçicinin yanındaki yenile butonu |
| iOS: her okuma agent'ı anarak hata veriyor | `mobilecli agent install --device <udid>` |
| IntelliJ: "needs JCEF" | IDE gömülü tarayıcısı olmayan bir runtime ile çalışıyor; JCEF içeren bir JetBrains Runtime'a geçin (Help → Find Action → Choose Boot Java Runtime) |
| Eklenti etkinlik çubuğunda yok | kurduktan sonra pencereyi yeniden yükleyin (VS Code) veya IDE'yi yeniden başlatın (IntelliJ) |
