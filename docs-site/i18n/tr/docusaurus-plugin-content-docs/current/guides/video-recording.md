---
description: "Playwright'ın retain-on-failure mantığına göre tasarlanan, test hata aldığında otomatik Web UI video kaydı ve HTML/Allure raporlarına gömülü oynatıcı."
id: video-recording
title: Video Kaydı (Video Recording)
sidebar_label: Video Kaydı
sidebar_position: 9
---

# Video Kaydı (Video Recording)

TestFly, Playwright'ın `video: 'retain-on-failure'` özelliğinden esinlenen **sıfır harici bağımlılıklı Web UI video kaydı** yeteneği sunar.

Özellik aktif edildiğinde, TestFly test çalışırken tarayıcı etkileşimlerini canlı olarak kaydeder. Test başarılı olursa hafızadaki tüm kareler anında temizlenir ve diske hiçbir dosya yazılmaz. Test hata aldığında ise yakalanan kareler otomatik olarak standart **H.264 MP4 videosuna** (veya GIF'e) dönüştürülür; etkileşimli HTML raporuna, Allure sonuçlarına ve izleme (trace) dosyalarına doğrudan eklenir.

---

## Öne Çıkan Özellikler

- **Sıfır Yerel Bağımlılık (Saf Java MP4 Kodlayıcı)**: Dahili JCodec H.264 video kodlayıcı kullanır. İşletim sisteminde `ffmpeg`, `X11` veya harici ikili dosyalar gerektirmez. Headless Docker konteynerlerinde, Linux CI, GitHub Actions, macOS ve Windows üzerinde doğrudan çalışır.
- **Chrome DevTools Protocol (CDP v152) Screencast**: Chromium tabanlı tarayıcılarda (Chrome ve Edge), kareler CDP `Page.startScreencast` protokolüyle asenkron ve bloklamayan akışla yakalanır; WebDriver komutlarını yavaşlatmaz.
- **Akıllı Saklama (`retain-on-failure`)**: Yalnızca başarısız olan testler video dosyasını saklar. Başarılı testlerde video diskte yer kaplamaz, CI depolama maliyetini ve koşum süresini korur.
- **Etkileşimli HTML5 Video Oynatıcı**: `target/testfly-report.html` raporu içine Base64 veri URI (`data:video/mp4;base64,...`) olarak gömülür. Oynat/duraklat, zaman çubuğu, döngü (loop) ve tam ekran lightbox penceresi sunar.
- **Doğrudan Allure Entegrasyonu**: `video/mp4` MIME türüyle Allure eklerine eklenir, Allure'un kendi yerel video oynatıcısında sorunsuz izlenir.
- **Headless Çözünürlük Optimizasyonu**: `--start-maximized` ayarlandığında, TestFly headless modda otomatik olarak `--window-size=1920,1080` uygulayarak Chromium'un varsayılan 800x600 çözünürlüğe düşmesini engeller ve tam masaüstü görünümünde kayıt alır.
- **Tüm Test Çatılarıyla Uyumlu**: **TestNG** (`BaseTest`), **JUnit 5** (`BaseJUnit5Test`) ve **Cucumber 7 BDD** (`@TestFlySession`) ile doğrudan çalışır.

---

## Yapılandırma (`testfly.yml`)

Video kaydını `testfly.yml` dosyanızda şu şekilde yapılandırabilirsiniz:

```yaml
recording:
  enabled: true                    # Video kaydını etkinleştir / devre dışı bırak (varsayılan: false)
  mode: retain-on-failure          # 'retain-on-failure' (varsayılan) | 'on' | 'off'
  format: mp4                      # 'mp4' (varsayılan, H.264 video) | 'gif'
  fps: 5                           # Saniyedeki kare sayısı (1-10 önerilir, varsayılan: 2)
  maxDurationSeconds: 60           # Maksimum video süresi güvenlik sınırı (varsayılan: 60)
  cdp: true                        # Chrome/Edge üzerinde yerel CDP screencast kullan (varsayılan: true)
```

### Yapılandırma Seçenekleri

| Anahtar | Tür | Varsayılan | Açıklama |
|---|---|---|---|
| `enabled` | `boolean` | `false` | Video kaydını açıp kapatan ana anahtar. |
| `mode` | `string` | `retain-on-failure` | `retain-on-failure`: Test geçerse kareleri atar, kalırsa video üretir.<br/>`on` / `always`: Tüm testler için kaydı saklar.<br/>`off`: Video kaydını devre dışı bırakır. |
| `format` | `string` | `mp4` | Video çıktı formatı: `mp4` (standart H.264 video, varsayılan) veya `gif` (hareketli GIF). |
| `fps` | `int` | `2` | Saniyede yakalanan kare hızı (daha yüksek değerler daha akıcı video üretir, önerilen 2–5). |
| `maxDurationSeconds` | `int` | `60` | Uzun süren testlerde bellek şişmesini önleyen güvenlik süresi üst sınırı. |
| `cdp` | `boolean` | `true` | True olduğunda Chromium tarayıcılarda CDP `Page.startScreencast` kullanır; Firefox/Safari üzerinde periyodik ekran görüntüsü örneklemesine geri döner. |

---

## Çalışma Mantığı

```
Test Başlar  ──►  RecordingSession başlar
                         │
                  Tarayıcı İşlemleri
                         │
         ┌───────────────┴───────────────┐
         ▼                               ▼
    Test Başarılı                   Test Başarısız
         │                               │
Bellekteki kareler silinir        Kareler MP4'e kodlanır
(0 bayt disk kullanımı)           (target/recordings/*.mp4)
                                         │
                                 Otomatik Eklenir:
                                 • target/testfly-report.html (<video>)
                                 • target/allure-results/ (video/mp4)
                                 • target/traces/{TestAdı}-trace.html
```

### 1. Test Başlangıcı
- Web UI test metodu başladığında TestFly iş parçacığına izole bir `RecordingSession` başlatır.
- Eğer Chrome/Edge ve `cdp: true` ise, DevTools oturumuna bağlanarak bloklamayan JPEG kare akışını başlatır.

### 2. Test Başarılı Olduğunda (`retain-on-failure` modu)
- Bellekte biriktirilen tüm kareler anında temizlenir.
- Diske hiçbir video dosyası yazılmaz; CI disk alanı ve performansı korunur.

### 3. Test Hata Aldığında
- Kayıt oturumu son durumu yakalar ve akışı durdurur.
- Yakalanan kareler JCodec H.264 ile `target/recordings/{paket_SinifAdi_metotAdi}.mp4` dosyasına dönüştürülür.
- Video otomatik olarak şu raporlara eklenir:
  1. `target/testfly-report.html` (test detay çekmecesinde, Hata Radarı'nda ve Tam Ekran Lightbox'ta Base64 HTML5 video oynatıcı).
  2. `target/allure-results/` (`video/mp4` MIME türünde `Execution Video` eki).
  3. `target/traces/{SinifAdi}/{metotAdi}-trace.html` (trace oynatıcı).

---

## Örnek Test Kodu

Aşağıdaki örnek TestNG sınıfında `retain-on-failure` mantığı gösterilmektedir:

```java
package io.testfly.examples.testng;

import io.testfly.core.BaseTest;
import org.testng.Assert;
import org.testng.annotations.Test;

public class WebUiRecordingExampleTest extends BaseTest {

    @Test(description = "Başarılı test: Video kaydı otomatik olarak silinir")
    public void successfulLoginTest() {
        open("https://www.saucedemo.com/");
        $("#user-name").val("standard_user");
        $("#password").val("secret_sauce");
        $("#login-button").click();
        
        Assert.assertTrue(getDriver().getCurrentUrl().contains("inventory.html"),
                "Kullanıcı envanter sayfasına yönlendirilmeli");
        // Diske hiçbir video kaydedilmez!
    }

    @Test(description = "Hata alan test: Video kaydı MP4 olarak derlenir ve raporlara eklenir")
    public void failingCheckoutTest() {
        open("https://www.saucedemo.com/");
        $("#user-name").val("standard_user");
        $("#password").val("secret_sauce");
        $("#login-button").click();

        // Kasıtlı hata:
        Assert.assertEquals(getDriver().getTitle(), "Beklenen Başlık Uyuşmazlığı",
                "MP4 video kaydını tetiklemek için kasıtlı hata");
        // Bir MP4 video oluşturulup testfly-report.html ve Allure raporuna eklenir!
    }
}
```

---

## Headless Tarayıcı Çözünürlük Optimizasyonu

CI/CD ortamlarında testler genelde headless modda çalıştırılır (`headless: true`). Chromium varsayılan olarak headless çalışırken klasik `--start-maximized` parametresini yok sayar ve `800x600` çözünürlük kullanır. Bu durum sitelerin mobil/tablet düzenine küçülmesine ve videoların sıkışık görünmesine yol açar.

TestFly, `testfly.yml` içinde `--start-maximized` tanımlandığında bunu algılar ve headless modda otomatik olarak `--window-size=1920,1080` uygular:

```yaml
browser:
  name: chrome
  headless: true
  arguments:
    - --start-maximized
    - --disable-notifications
```

Bu sayede:
- Video kayıtları mobil yerine **tam 1080p masaüstü görünümünde** kaydedilir.
- Hata ekran görüntüleri masaüstü düzenini yansıtır.
- Responsive hamburger menüler gibi beklenmeyen düzen kaymaları engellenir.

---

## Kaydedilen Videoları İzleme

### TestFly HTML Raporunda
`target/testfly-report.html` dosyasını tarayıcınızda açın:
1. Başarısız testi **Suite Explorer** veya **Flakiness Radar** üzerinden seçin.
2. Test detay panelinde **🎥 Execution Video Recording** bölümünü açın.
3. Dahili HTML5 video oynatıcıyı kullanın:
   - Oynat, duraklat ve zaman çizelgesinde ileri/geri sar.
   - Sesi ayarla veya sessize al.
   - Videoya tıklayarak **Tam Ekran Lightbox Oynatıcı** moduna geç.

### Allure Raporunda
Allure raporlaması etkinse:
```bash
allure serve target/allure-results
```
Başarısız testin **Overview** sekmesindeki **Attachments** altında **Execution Video (`.mp4`)** dosyasını görebilir ve doğrudan Allure web arayüzünde izleyebilirsiniz.
