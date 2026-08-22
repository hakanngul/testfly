---
description: "BaseTest, her TestFly testinin genişlettiği tek sınıftır (superclass) ve gereken yegâne kurulumdur. Driver yaşam döngüsü, beklemeler ve raporlama ek maliyet olmadan gelir."
id: base-test
title: BaseTest
sidebar_position: 1
---

# BaseTest

`BaseTest`, tüm TestFly testleri için zorunlu üst sınıftır (superclass). Onu genişletmek, gereken tek kurulumdur.

---

## Kullanım

```java
import io.testfly.test.BaseTest;
import org.testng.annotations.Test;

public class LoginTest extends BaseTest {

    @Test
    public void loginTest() {
        open();  // navigate to baseUrl
        // test steps
    }
}
```

---

## BaseTest ne yapar

- `SuiteExecutionListener` kaydeder (framework başlatma, yapılandırma yükleme, rapor üretimi)
- `TestExecutionListener` kaydeder (driver oluşturma, hata durumunda ekran görüntüsü, metrik kaydı)
- Testlerin `WebDriver` veya yapılandırmayla asla doğrudan etkileşime girmemesi için yardımcı metodlar sağlar

---

## Kullanılabilir metodlar

### `open()`
Tarayıcıyı `testfly.yml` içinde yapılandırılan `baseUrl` adresine götürür.

```java
open();  // → browser.get(config.execution.baseUrl)
```

### `open(String path)`
`baseUrl + path` adresine gider.

```java
open("/login");    // → browser.get("https://your-app.com/login")
open("/admin");    // → browser.get("https://your-app.com/admin")
```

### `getDriver()`
Geçerli thread'e bağlı `WebDriver` örneğini döndürür.

```java
WebDriver driver = getDriver();
```

:::caution
Test kodunuzda asla `driver.quit()` veya `new ChromeDriver()` çağırmayın.
Framework, driver yaşam döngüsünü yönetir — her testten önce oluşturur ve sonrasında kapatır.
:::

---

## Kurallar

| Kural | Gerekçe |
|---|---|
| `WebDriver`'ı manuel oluşturmayın | Framework yaşam döngüsünü yönetir |
| `driver.quit()` çağırmayın | Sonraki adımlarda oturum hatalarına neden olur |
| `Thread.sleep()` kullanmayın | Bunun yerine `WaitEngine` kullanın |
| `@AfterMethod` içinde retry yönetmeyin | Framework retry'ı otomatik olarak yönetir |