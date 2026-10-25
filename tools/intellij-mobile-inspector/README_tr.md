# IntelliJ IDEA için TestFly Mobile Inspector

[VS Code Mobile Inspector](../vscode-mobile-inspector/README_tr.md)'ın IntelliJ tabanlı IDE'ler (2024.1+) için
sürümü: aynı arayüz (`media/` dosyaları eklentiye gömülür), IDE'nin gömülü tarayıcısında (JCEF) çalışır ve
IDE temasına uyar. [English](README.md)

![Mobile Inspector](../vscode-mobile-inspector/media/screenshot.png)

## Kurulum

Settings → Plugins → ⚙ → **Install Plugin from Disk…** → `tools/intellij-mobile-inspector/dist/testfly-mobile-inspector-0.4.0.zip`, ardından IDE'yi yeniden başlatın.

## Kullanım

- Soldaki **TestFly Mobile** penceresi: açık ya da kapalı tüm emülatör, simülatör ve cihazlar; **Inspect**, **Boot** ve **Shut down** ile, yetkisiz Android telefonlar ve *Connect Android over Wi‑Fi…*; **Open Mobile Inspector** ya da **Tools → TestFly: Open Mobile Inspector** inspector'ı bir editör sekmesinde açar.
- Inspector bir ekran gösterirken Java ve Kotlin dosyalarında her `@MobileFind` alanının ve `screen().getBy…` çağrısının üstünde kaç öğeyle eşleştiği görünür (code vision; tıklayınca eşleşmeler gösterilir).
- Ekran, `@eN` ref'li UI ağacı ve Appium tarzı arama aracı (TestFly selector, id, accessibility id, XPath, class name, text), Snapshot sekmesi, öğe özellikleri, eşleşme sayılı TestFly seçicileri ve `@MobileFind` alanları, dokunma / uzun basma / yazma / Back / Home / kaydırma, ekran görüntüsünde basılı tutma ve sürükleme, **Live** (ekran birkaç saniyede bir yeniden okunur), mobilecli'yi başlatan veya durduran **Server** düğmesi ve **Record** modu (dokunma, uzun basma, sürükleme, yazma, kaydırma ve butonlar `@MobileFind` alanlı bir `MobilePage` olur): VS Code eklentisindeki gibi. Tuşlar: `R`, `I`, `T`, `E`, `L`, `/`, `Esc`.
- Settings → Tools → **TestFly Mobile Inspector**: mobilecli sunucu adresi (`http://localhost:12000`), mobilecli yolu, otomatik başlatma, eylemden sonra yenileme.

[mobilecli](https://github.com/mobile-next/mobilecli) ve açık bir simülatör, emülatör veya cihaz gerekir (iOS: bir kez `mobilecli agent install --device <udid>`).

## Derleme

```bash
./gradlew test buildPlugin    # build/distributions/testfly-mobile-inspector-<sürüm>.zip
```

Arayüz `../vscode-mobile-inspector/media/` altındadır; orada değiştirince iki IDE de alır.
