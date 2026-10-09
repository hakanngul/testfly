---
description: "Add a custom WebDriver to TestFly: plug in Appium, a custom grid, or a niche browser via NamedDriverProvider without touching the framework. Edge and Safari are already built-in."
id: custom-drivers
title: Custom Drivers
sidebar_position: 3
---

# Custom Drivers

`NamedDriverProvider` lets you plug in any WebDriver implementation — Appium, a custom Selenium Grid wrapper, or a niche browser — without modifying the framework. Custom providers take precedence over the built-in providers.

:::info Edge and Safari are built-in
You do not need a custom provider for Edge or Safari. Set `browser.name: edge` or `browser.name: safari` in `testfly.yml` and TestFly handles them natively.
:::

---

Appium examples require your own compatible Appium Java client dependency and a running Appium server. TestFly does not bundle that client.

## Create a custom driver provider

```java
import io.testfly.driver.NamedDriverProvider;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import org.openqa.selenium.WebDriver;

public class AndroidProvider implements NamedDriverProvider {

    @Override
    public String browserName() {
        return "android";   // matched case-insensitively against browser.name in testfly.yml
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

## Register via Java SPI (auto-discovery)

```
src/main/resources/META-INF/services/io.testfly.driver.NamedDriverProvider
```

Contents:

```
com.example.drivers.AndroidProvider
```

Then set `browser.name` in your config:

```yaml title="testfly.yml"
browser:
  name: android
```

TestFly selects your provider automatically.

---

## Register programmatically

```java
import io.testfly.driver.DriverProviderRegistry;

DriverProviderRegistry.register(new AndroidProvider());
```

---

## BrowserStack example

```java
import io.testfly.driver.NamedDriverProvider;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import java.util.HashMap;

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

        try {
            return new RemoteWebDriver(
                java.net.URI.create("https://hub-cloud.browserstack.com/wd/hub").toURL(), options);
        } catch (java.net.MalformedURLException e) {
            throw new IllegalStateException("Invalid BrowserStack endpoint", e);
        }
    }
}
```

```yaml title="testfly.yml"
browser:
  name: browserstack
```

---

## Appium example

```java
public class AndroidAppProvider implements NamedDriverProvider {

    @Override
    public String browserName() { return "android"; }

    @Override
    public WebDriver createDriver() {
        UiAutomator2Options options = new UiAutomator2Options()
            .setDeviceName("emulator-5554")
            .setApp("/path/to/app.apk");

        try {
            return new AndroidDriver(java.net.URI.create("http://127.0.0.1:4723").toURL(), options);
        } catch (java.net.MalformedURLException e) {
            throw new IllegalStateException("Invalid Appium endpoint", e);
        }
    }
}
```

---

## Provider selection order

1. **Remote mode** (`execution.mode: remote`) → always uses `RemoteDriverProvider`
2. **Custom provider** registered via SPI or programmatically → used if `browser.name` matches `browserName()`
3. **Built-in Chrome** → used if `browser.name: chrome`
4. **Built-in Firefox** → used if `browser.name: firefox`
