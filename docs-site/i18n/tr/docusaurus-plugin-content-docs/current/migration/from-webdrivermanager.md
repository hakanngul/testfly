---
description: "WebDriverManager'dan uzaklaşın: modern Selenium, Selenium Manager'ı bünyesinde barındırır, böylece TestFly driver ikili dosyalarını sıfır WebDriverManager koduyla çözer — kurulum çağrılarını ve bağımlılığı silin."
id: from-webdrivermanager
title: WebDriverManager'dan Geçiş
sidebar_label: WebDriverManager'dan
sidebar_position: 2
---

# WebDriverManager'dan Geçiş

Projeniz tarayıcı driver'larını indirmek için `WebDriverManager.chromedriver().setup()` (veya genel olarak Boni García'nın `io.github.bonigarcia:webdrivermanager` kütüphanesini) çağırıyorsa, **hepsini silebilirsiniz**. Modern Selenium, driver ikili dosyalarını kendisi çözer ve TestFly bu mekanizmayı kutu dışı kullanır.

:::info WebDriverManager neden vardı
Tarihsel olarak Selenium, makinenizde eşleşen bir `chromedriver`/`geckodriver` ikili dosyasına ihtiyaç duyuyordu ve bunu kurulu tarayıcıyla senkronize tutmak can sıkıcıydı. WebDriverManager bunu doğru ikili dosyayı çalışma zamanında indirerek çözdü. **Selenium 4.6.0'dan** (Kasım 2022) itibaren bu iş Selenium'un kendi içine taşındı — aşağıya bakın.
:::

---

## Selenium Manager onu yerleşik hale getirir

**Selenium 4.6.0** ile başlayarak Selenium, **Selenium Manager'ı** sunar: `selenium-java` ile birlikte gelen otomatik driver çözümleme aracı. Hiçbir driver ikili dosyası bulunamadığında Selenium tarayıcı sürümünüzü algılar, doğru driver'ı indirir ve önbelleğe alır — **kod ve ekstra bağımlılık olmadan**.

TestFly modern Selenium üzerine kuruludur, bu yüzden bu zaten etkindir. Onu çağırmazsınız, yapılandırmazsınız veya doğrudan bağımlı olmazsınız.

---

## Önce / sonra

**Önce** — bir bağımlılık olarak WebDriverManager ve oluşturduğunuz her driver'dan önce bir `setup()` çağrısı:

```xml title="pom.xml (bunu kaldırın)"
<dependency>
    <groupId>io.github.bonigarcia</groupId>
    <artifactId>webdrivermanager</artifactId>
    <version>5.x</version>
</dependency>
```

```java title="DriverFactory.java (setup çağrılarını kaldırın)"
import io.github.bonigarcia.wdm.WebDriverManager;

WebDriverManager.chromedriver().setup();
WebDriver driver = new ChromeDriver();

// Firefox
WebDriverManager.firefoxdriver().setup();
WebDriver driver = new FirefoxDriver();
```

**Sonra** — hiçbir şey. `setup()` çağrısı ve WebDriverManager bağımlılığı yok. TestFly'da driver'ı siz oluşturmazsınız bile — [`BaseTest`](/docs/guides/base-test) sınıfını genişletin ve framework onu yapar:

```java title="LoginTest.java"
import io.testfly.test.BaseTest;
import org.testng.annotations.Test;

public class LoginTest extends BaseTest {

    @Test
    public void loginTest() {
        open();  // driver zaten oluşturuldu — ikili dosya otomatik olarak çözüldü
        // ...
    }
}
```

Tarayıcıyı kodda değil yapılandırmada seçin:

```yaml title="testfly.yml"
browser:
  name: chrome   # chrome | firefox | edge | safari
```

---

## Neler silinir

| Önce | Sonra |
|---|---|
| `pom.xml` / `build.gradle` içindeki `webdrivermanager` bağımlılığı | ✅ Kaldırıldı — yerini hiçbir şey almıyor |
| `WebDriverManager.chromedriver().setup()` çağrıları | ✅ Selenium Manager (otomatik) |
| Tarayıcı başına `.setup()` dalları (Chrome/Firefox/Edge) | ✅ Tek bir `browser.name` yapılandırma satırı |
| Tarayıcılarla eşleşecek şekilde driver sürümlerini sabitleme | ✅ Selenium Manager onları sizin için eşleştirir |

---

## SSS

**Belirli bir tarayıcı sürümü için hâlâ WebDriverManager'a ihtiyacım var mı?**
Hayır. Selenium Manager kurulu tarayıcıyı algılar ve Edge ve Firefox dahil eşleşen bir driver'ı otomatik olarak indirir.

**Çevrimdışı / hava boşluklu CI ne olacak?**
Selenium Manager driver'ları `~/.cache/selenium` altında önbelleğe alır. Önbelleği bir kez ısıtın (veya CI görüntünüze gömün) ve sonraki çalıştırmalar ağ gerektirmez. Bu, WebDriverManager'ın kullandığı aynı önbelleğe alma yaklaşımıdır.

**Belirli bir driver ikili dosyasını işaret edebilir miyim?**
Evet — Selenium, bir tane ayarlarsanız standart driver-yolu sistem özelliklerini (ör. `webdriver.chrome.driver`) onurlandırır, böylece önceden sağlanan ikili dosyalar çalışmaya devam eder. Artık tek ihtiyacınız *buna sahip olmanıza gerek olmaması*.

---

## Sonraki adımlar

- [Selenium + TestNG'den Geçiş](/docs/migration/from-selenium-testng) — tam framework geçişi (driver factory, bekleme, yeniden deneme, raporlama)
- [Başlarken](/docs/getting-started) — 5 dakikalık sürüm
- [Yapılandırma Referansı](/docs/configuration) — tam `testfly.yml`