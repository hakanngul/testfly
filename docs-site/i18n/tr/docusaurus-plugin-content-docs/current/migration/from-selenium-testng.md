---
description: "Bir Selenium + TestNG framework'ünü TestFly'a geçirin: driver factory'nizi, bekleme yardımcılarınızı, yeniden deneme analizcinizi ve raporlama yapıştırmalarınızı silin ve boilerplate'in yan yana nasıl kaybolduğunu görün."
id: from-selenium-testng
title: Selenium + TestNG'den Geçiş
sidebar_label: Selenium + TestNG'den
sidebar_position: 1
---

# Selenium + TestNG'den Geçiş

El ile kurulmuş bir **Selenium + TestNG** framework'ü çalıştırıyorsanız, bir driver factory, bir bekleme yardımcısı, bir yeniden deneme analizcisi, hata anında ekran görüntüsü yapıştırması ve bir raporlama entegrasyonu yazdınız — ve şimdi hepsini bakımını yapıyorsunuz. TestFly, tüm bunları tek bir bağımlılık olarak sunar.

Bu rehber, yan yana bir **"mevcut kurulumunuz → TestFly karşılığı"**dır. Kısa özet: bugün bakımını yaptığınız tesisatın çoğu sadece silinir.

:::info Yeniden öğrenecek bir şey yok
TestFly hâlâ Selenium'dur. `WebDriver`, `By`, `WebElement` ve mevcut page-object desenlerinizin tümü hâlâ çalışır — araç değiştirmiyorsunuz, boilerplate'i kaldırıyorsunuz.
:::

---

## Kurulum — bağımlılıkları değiştirin

Selenium, WebDriverManager ve raporlama bağımlılıklarınızı kaldırın ve bir tane ekleyin:

```xml title="pom.xml"
<dependency>
    <groupId>io.testfly</groupId>
    <artifactId>testfly</artifactId>
    <version>1.0.0</version>
</dependency>
```

TestFly, Selenium'u (ve TestNG'i) geçişli olarak getirir. Artık `selenium-java`, `webdrivermanager` veya bir raporlama kütüphanesini kendiniz bildirmezsiniz.

Ardından küçük bir [`testfly.yml`](/docs/configuration) oluşturun — aşağıdaki [yapılandırma eşlemesine](#config-mapping) bakın.

---

## 1. Driver kurulumu

**Önce** — paralel çalıştırmalar için bir driver factory, `ThreadLocal` uğraşı ve ikili dosyaları indirmek için `WebDriverManager`:

```java title="DriverFactory.java (bunu silin)"
public class DriverFactory {
    private static final ThreadLocal<WebDriver> DRIVER = new ThreadLocal<>();

    public static void createDriver() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-maximized");
        DRIVER.set(new ChromeDriver(options));
        DRIVER.get().manage().timeouts()
              .implicitlyWait(Duration.ofSeconds(10));
    }

    public static WebDriver getDriver() { return DRIVER.get(); }

    public static void quitDriver() {
        DRIVER.get().quit();
        DRIVER.remove();
    }
}

public class BaseTest {
    @BeforeMethod public void setUp()    { DriverFactory.createDriver(); }
    @AfterMethod  public void tearDown() { DriverFactory.quitDriver(); }
}
```

**Sonra** — `BaseTest`'i genişletin. Driver oluşturma, iş parçacığı başına izolasyon ve teardown sizin için halledilir:

```java title="LoginTest.java"
import io.testfly.test.BaseTest;
import org.testng.annotations.Test;

public class LoginTest extends BaseTest {

    @Test
    public void loginTest() {
        open();  // execution.baseUrl adresine gider
        // ...
    }
}
```

- **WebDriverManager yok.** Modern Selenium (4.6+) kendi **Selenium Manager**'ını içerir ve doğru driver ikili dosyasını otomatik olarak indirir. TestFly onu kullanır — `.setup()` çağrılarını ve bağımlılığı silin. Detaylar için bkz. [WebDriverManager'dan Geçiş](/docs/migration/from-webdrivermanager).
- **ThreadLocal yok.** `DriverManager`, driver'ı her iş parçacığı için izole eder, böylece [paralel çalıştırmalar](/docs/guides/parallel) kutu dışı güvenlidir.
- Ham driver'a mı ihtiyacınız var? Hâlâ orada: `getDriver()`.

:::caution Örtük beklentiyi bırakın
`implicitlyWait(...)` öğesini silin. TestFly'ın locator'ları açıkça otomatik bekler; örtük ve açık bekelemeleri karıştırmak, flaky ve yavaş testlerin klasik kaynağıdır.
:::

---

## 2. Beklemeler

**Önce** — her sayfaya import edilen `WebDriverWait` / `ExpectedConditions` sarmalayan bir `WaitUtils` yardımcısı:

```java title="WaitUtils.java (bunu silin)"
public class WaitUtils {
    public static WebElement waitVisible(WebDriver driver, By locator) {
        return new WebDriverWait(driver, Duration.ofSeconds(10))
            .until(ExpectedConditions.visibilityOfElementLocated(locator));
    }
    public static void waitClickable(WebDriver driver, By locator) {
        new WebDriverWait(driver, Duration.ofSeconds(10))
            .until(ExpectedConditions.elementToBeClickable(locator));
    }
}

// kullanım
WaitUtils.waitVisible(driver, By.id("login")).click();
```

**Sonra** — her locator **otomatik bekler** ve `WaitEngine` (sizin `timeouts.explicit` değerinizden önceden yapılandırılmış) açık durumları kapsar:

```java
find("#login").click();                          // tıklanabilirlik için otomatik bekler
getWait().waitForInvisible(By.cssSelector(".spinner"));
getWait().waitForText(By.cssSelector("h1"), "Welcome back");
```

`Thread.sleep()` yok, sayfa başına `WebDriverWait` kurulumu yok, `driver` taşımak yok. [WaitEngine rehberine](/docs/guides/wait-engine) bakın.

---

## 3. Yeniden deneme / flaky testler

**Önce** — bir `IRetryAnalyzer` artı onu her metoda bağlayan bir listener:

```java title="RetryAnalyzer.java + RetryListener.java (ikisini de silin)"
public class RetryAnalyzer implements IRetryAnalyzer {
    private int count = 0;
    private static final int MAX = 2;
    @Override public boolean retry(ITestResult result) {
        return count++ < MAX;
    }
}

public class RetryListener implements IAnnotationTransformer {
    @Override public void transform(ITestAnnotation ann, Class c,
                                    Constructor ctor, Method m) {
        ann.setRetryAnalyzer(RetryAnalyzer.class);
    }
}
// + listener'ı testng.xml dosyasına kaydedin
```

**Sonra** — tek bir yapılandırma satırı tüm suite için yeniden denemeyi açık hale getirir:

```yaml title="testfly.yml"
retry:
  enabled: true
  maxAttempts: 2   # ilk çalıştırma dahil toplam deneme sayısı
```

Gerektiğinde test başına `@Retryable` ile geçersiz kılın:

```java
@Test
@Retryable(maxAttempts = 3)
public void flakyTest() { /* ... */ }
```

Kurtarılan ve hâlâ başarısız olan yeniden denemeler rapor içinde ayrıştırılır. [Yeniden Deneme rehberine](/docs/guides/retry) bakın.

---

## 4. Hata durumunda ekran görüntüleri

**Önce** — `onTestFailure` içinde driver'a uzanan, bir PNG kodlayan ve onu raporunuzun bulabileceği bir yere yazan bir `ITestListener`:

```java title="ScreenshotListener.java (bunu silin)"
public class ScreenshotListener implements ITestListener {
    @Override public void onTestFailure(ITestResult result) {
        WebDriver driver = DriverFactory.getDriver();
        File png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
        // .../screenshots dizinine kopyalayın, rapora ekleyin, IOException ile başa çıkın...
    }
}
```

**Sonra** — hiçbir şey. TestFly her hatada otomatik olarak bir ekran görüntüsü yakalar ve onu HTML raporuna gömer. Listener'ı silin. [Ekran Görüntüleri](/docs/guides/screenshots) bölümüne bakın.

---

## 5. Raporlama

**Önce** — ExtentReports/Allure bağlayın: bir listener, `@AfterSuite` içinde bir `flush()` ve kodunuza dağılmış test başına günlüğe kaydetme çağrıları.

**Sonra** — `target/testfly-report.html` adresinde kendi kendine yeten bir **HTML raporu** (geçme oranı göstergesi, yeniden denemeler, gömülü ekran görüntüleri, flakiness) ve CI için bir **JUnit XML** dosyası — her ikisi de her çalıştırmadan sonra otomatik olarak üretilir. Daha zengin raporlar istiyorsanız opsiyonel [Adım Günlüğü](/docs/guides/step-logging) API'siyle adlandırılmış adımlar ekleyin.

Bkz. [HTML Raporu](/docs/reporting/html-report) ve [JUnit XML](/docs/reporting/junit-xml).

---

## Neler silinir

| Mevcut kurulumunuz | TestFly |
|---|---|
| `DriverFactory` + `ThreadLocal<WebDriver>` | ✅ Yerleşik — `BaseTest`'i genişletin |
| `WebDriverManager.chromedriver().setup()` | ✅ Selenium Manager (otomatik) |
| Örtük bekleme yapılandırması | ✅ Otomatik bekleyen locator'lar |
| `WaitUtils` / `WebDriverWait` yardımcıları | ✅ `WaitEngine` + otomatik bekleme |
| `IRetryAnalyzer` + `IAnnotationTransformer` | ✅ `retry:` yapılandırması + `@Retryable` |
| Hata anında ekran görüntüsü `ITestListener` | ✅ Hata durumunda otomatik |
| ExtentReports/Allure bağlantısı | ✅ HTML raporu + JUnit XML |
| `@BeforeMethod` / `@AfterMethod` yaşam döngüsü yapıştırması | ✅ Framework tarafından yönetilen yaşam döngüsü |

Page object'leriniz ve `@Test` metotlarınız aynı kalır — sadece kısalırlar.

---

## Yapılandırma eşlemesi

`testng.xml` özniteliklerinde ve dağınık sabitlerde yaşayan ayarlar tek bir dosyaya taşınır:

```yaml title="testfly.yml"
execution:
  baseUrl: https://your-app.com
  parallel: methods        # şuydu: <suite parallel="methods">
  threadCount: 4           # şuydu: thread-count="4"

browser:
  name: chrome
  headless: false          # CI algılandığında otomatik olarak true zorlanır

timeouts:
  explicit: 10             # şuydu: WaitUtils sabitiniz
  pageLoad: 30

retry:
  enabled: true
  maxAttempts: 2           # şuydu: RetryAnalyzer MAX
```

Test sınıflarınızı listelemek için hâlâ minimal bir `testng.xml` tutarsınız — TestFly kendi listener'larını kaydeder, böylece `<listeners>` bloğunu kaldırabilirsiniz. Her seçenek için [Yapılandırma Referansına](/docs/configuration) bakın.

---

## Kademeli geçiş

Her şeyi bir kerede dönüştürmek zorunda değilsiniz:

1. Bağımlılığı ve bir `testfly.yml` dosyası ekleyin.
2. **Bir** test sınıfını `BaseTest`'e yönlendirin, `@BeforeMethod`/`@AfterMethod` metotlarını silin ve çalıştırın.
3. Yeşil olduğunda, son sınıf onlara atıfta bulunmayı bıraktıkça `DriverFactory`, `WaitUtils`, yeniden deneme analizciniz ve ekran görüntüsü listener'ınızı silin.

Çünkü TestFly *Selenium'dur*, yarısı geçirilmiş bir suite sorunsuz çalışır.

---

## Sonraki adımlar

- [Başlarken](/docs/getting-started) — 5 dakikalık sürüm
- [BaseTest](/docs/guides/base-test) / [BasePage](/docs/guides/base-page) — genişleteceğiniz temel sınıflar
- [Erişilebilirlik Öncelikli Locator'lar](/docs/guides/semantic-locators) — boilerplate gittikten sonra `getByRole`/`getByLabel`
- [Yapılandırma Referansı](/docs/configuration) — tam `testfly.yml`