---
description: "Selenium testinin başarısızlığında otomatik ekran görüntüleri; yönetilecek harici görüntü dosyası veya depolama gerektirmeyen, HTML raporuna Base64 olarak gömülür."
id: screenshots
title: Selenium Ekran Görüntüleri
sidebar_label: Ekran Görüntüleri
sidebar_position: 8
---

# Ekran Görüntüleri

TestFly, test başarısız olduğunda ekran görüntülerini otomatik olarak yakalar ve bunları doğrudan HTML raporuna Base64 olarak gömer — harici görüntü dosyası yok, CI'da bozuk yol yok.

---

## Otomatik başarısızlık ekran görüntüleri

Yapılandırma gerekmez. Bir test başarısız olduğunda, TestFly bir ekran görüntüsü yakalar ve bunu HTML raporundaki testin ayrıntı paneline ekler.

Ekran görüntüsü, Failures sekmesinde küçük resim olarak görünür. Tam boyutlu lightbox'ı açmak için tıklayın.

---

## Adım ekran görüntüleri

`StepLogger` kullanarak testin herhangi bir noktasında ekran görüntüsü yakalayın:

```java
StepLogger.step("After form submission", true);        // INFO + ekran görüntüsü
StepLogger.step("Verify result", StepStatus.PASS, true); // PASS + ekran görüntüsü
```

Adım ekran görüntüleri, her testin ayrıntı panelinin içindeki adım zaman çizelgesinde küçük resimler olarak görünür.

---

## Ekran görüntüleri nasıl saklanır

Ekran görüntüleri Base64 olarak kodlanır ve doğrudan HTML raporuna gömülür. Bu şunları ifade eder:

- Rapor **tek bağımsız bir dosyadır** — onu herhangi bir yere kopyalayın
- Çalıştırmadan sonra çalışma alanının temizlendiği **CI ortamlarında** çalışır
- Yol yapılandırması gerekmez
- Dosya boyutu, ayrı bir görüntü dosyasından daha büyüktür (Base64 ~%33 ek yük ekler)

---

## Yapılandırma

```yaml title="testfly.yml"
screenshots:
  onFailure: true   # varsayılan — her başarısızlıkta ekran görüntüsü yakala
```

Otomatik başarısızlık ekran görüntülerini devre dışı bırakmak için:

```yaml
screenshots:
  onFailure: false
```

Adım ekran görüntüleri (`StepLogger` aracılığıyla), metod çağrısındaki `boolean screenshot` bağımsız değişkeniyle kontrol edilir, bu yapılandırmayla değil.

---

## Özel ekran görüntüsü yakalama

`StepLogger` dışında bir ekran görüntüsü yakalamanız gerekiyorsa (örneğin bir yardımcı metod içinde), doğrudan `ScreenshotManager` kullanın:

```java
import io.testfly.reporting.ScreenshotManager;

// Disk'e kaydet ve (dosya yolunu döndürür)
String path = ScreenshotManager.capture(driver, "my-screenshot");

// Base64 dizesi olarak yakala (gömmek için)
String base64 = ScreenshotManager.captureAsBase64(driver);
```

---

## CI'da ekran görüntüsü

Ekran görüntüleri Base64 gömülü olduğundan, CI çalışma alanı silindikten sonra bile HTML rapor yapıtında doğru şekilde görünürler.

**GitHub Actions** — raporu bir yapıt olarak yükleyin:

```yaml
- name: Upload report
  if: always()
  uses: actions/upload-artifact@v4
  with:
    name: testfly-report
    path: target/testfly-report.html
```

Yapıtı indirin, HTML dosyasını yerel olarak açın — tüm ekran görüntüleri satır içidir.