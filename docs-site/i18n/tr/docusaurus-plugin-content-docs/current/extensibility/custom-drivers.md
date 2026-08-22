---
description: "TestFly'a özel bir WebDriver ekleyin: NamedDriverProvider aracılığıyla Appium'u, özel bir grid'i veya niş bir tarayıcıyı framework'e dokunmadan bağlayın. Edge ve Safari zaten yerleşiktir."
id: custom-drivers
title: Özel Driver'lar
sidebar_position: 3
---

# Özel Driver'lar

`NamedDriverProvider`, framework'ü değiştirmeden herhangi bir WebDriver uygulamasını bağlamanızı sağlar — Appium, özel bir Selenium Grid sarmalayıcısı veya niş bir tarayıcı. Özel sağlayıcılar, yerleşik sağlayıcılara göre öncelik kazanır.

:::info Edge ve Safari yerleşiktir
Edge veya Safari için özel bir sağlayıcıya ihtiyacınız yok. `testfly.yml` içinde `browser.name: edge` veya `browser.name: safari` ayarlayın ve TestFly bunları yerel olarak halleder.
:::

---

## Özel bir driver sağlayıcısı oluşturun

```java
import io.testfly.driver.NamedDriverProvider;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import org.openqa.selenium.WebDriver;

public class AndroidProvider implements NamedDriverProvider {

    @Override
    public String browserName() {
        return "android";   // testfly.yml içindeki browser.name ile büyük/küçük harfe duyarsız eşleştirilir
    }

    @Override
    public WebDriver createDriver() {
        UiAutomator2Options options = new UiAutomator2Options();
        options.setPlatformName("Android");
        options.setDeviceName("Pixel 7");
        options.setApp("/path/to/app.apk");
        return new AndroidDriver(options);
    }
}
```

---

## Java SPI ile kaydettirin (otomatik keşif)

```
src/main/resources/META-INF/services/io.testfly.driver.NamedDriverProvider
```

İçerik:

```
com.example.drivers.AndroidProvider
```

Ardından yapılandırmanızda `browser.name` değerini ayarlayın:

```yaml title="testfly.yml"
browser:
  name: android
```

TestFly sağlayıcınızı otomatik olarak seçer.

---

## Programatik olarak kaydettirin

```java
import io.testfly.driver.DriverProviderRegistry;

DriverProviderRegistry.register(new AndroidProvider());
```

---

## BrowserStack örneği

```java
public class BrowserStackProvider implements NamedDriverProvider {

    @Override
    public String browserName() { return "browserstack"; }

    @Override
    public WebDriver createDriver() {
        ChromeOptions options = new ChromeOptions();
        HashMap<String, Object> bstackOptions = new HashMap<>();
        bstackOptions.put("userName", System.getenv("BROWSERSTACK_USERNAME"));
        bstackOptions.put("accessKey", System.getenv("BROWSERSTACK_ACCESS_KEY"));
        bstackOptions.put("browserName", "Chrome");
        bstackOptions.put("browserVersion", "latest");
        options.setCapability("bstack:options", bstackOptions);

        return new RemoteWebDriver(
            new URL("https://hub-cloud.browserstack.com/wd/hub"), options
        );
    }
}
```

```yaml title="testfly.yml"
browser:
  name: browserstack
```

---

## Appium örneği

```java
public class AndroidAppProvider implements NamedDriverProvider {

    @Override
    public String browserName() { return "android"; }

    @Override
    public WebDriver createDriver() {
        UiAutomator2Options options = new UiAutomator2Options()
            .setDeviceName("emulator-5554")
            .setApp("/path/to/app.apk");

        return new AndroidDriver(new URL("http://127.0.0.1:4723"), options);
    }
}
```

---

## Sağlayıcı seçim sırası

1. **Uzak mod** (`browser.mode: remote`) → her zaman `RemoteDriverProvider` kullanır
2. **Özel sağlayıcı** SPI veya programatik olarak kaydedilir → `browser.name` değeri `browserName()` ile eşleşiyorsa kullanılır
3. **Yerleşik Chrome** → `browser.name: chrome` ise kullanılır
4. **Yerleşik Firefox** → `browser.name: firefox` ise kullanılır