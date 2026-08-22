---
description: "Selenium'da suite ve test yaşam döngüsü noktalarında kodu ExecutionHook ile çalıştırın: alt sınıflandırma yapmadan kurulum, teardown ve hata işleme enjekte edin."
id: hooks
title: Yürütme Hook'ları
sidebar_position: 2
---

# Yürütme Hook'ları

`ExecutionHook`, test yürütme yaşam döngüsünün kilit noktalarına davranış enjekte etmenizi sağlar — suite başlangıcı/bittiği ve test başına başlangıç/bitiş/hata. Tüm metotlar opsiyoneldir (varsayılan no-op).

---

## Bir hook oluşturun

```java
import io.testfly.hooks.ExecutionHook;

public class TimingHook implements ExecutionHook {

    @Override
    public void onSuiteStart() {
        // framework bootstrap'tan sonra, herhangi bir test çalışmadan önce bir kez çağrılır
    }

    @Override
    public void onSuiteEnd() {
        // tüm raporlar üretildikten sonra bir kez çağrılır
    }

    @Override
    public void onTestStart(String testId) {
        // testId = "com.example.LoginTest#loginTest"
        System.out.println("Starting: " + testId);
    }

    @Override
    public void onTestEnd(String testId, String status) {
        // status = "PASSED" veya "SKIPPED"
        metricsClient.record(testId, status);
    }

    @Override
    public void onTestFailure(String testId, Throwable cause) {
        // ekran görüntüsü yakalandıktan sonra, driver kapatılmadan önce çağrılır
        alertService.send("FAILED: " + testId + " — " + cause.getMessage());
    }
}
```

Yalnızca ihtiyacınız olan metotları geçersiz kılın — diğerleri varsayılan olarak no-op'tur.

---

## Java SPI ile kaydettirin (otomatik keşif)

```
src/main/resources/META-INF/services/io.testfly.hooks.ExecutionHook
```

İçerik:

```
com.example.hooks.TimingHook
```

---

## Programatik olarak kaydettirin

```java
import io.testfly.hooks.HookRegistry;

HookRegistry.register(new TimingHook());
```

Bunu suite başlamadan önce çağırın (ör. bir `@BeforeSuite` metodu veya `TestFlyPlugin.onLoad` içinde).

---

## Hook olay sırası

```
onSuiteStart()
  onTestStart("LoginTest#login")
  onTestEnd("LoginTest#login", "PASSED")

  onTestStart("CheckoutTest#checkout")
  onTestFailure("CheckoutTest#checkout", AssertionError)
  // not: onTestFailure tetiklendiğinde onTestEnd ÇAĞRILMAZ

onSuiteEnd()
```

---

## Hata izolasyonu

Hook hataları **izole edilir** — bir hook'taki bir istisna günlüğe kaydedilir ancak diğer hook'ların çalışmasını engellemez veya test sonuçlarını etkilemez.

---

## Hook ve Eklenti (Plugin) karşılaştırması

| | `ExecutionHook` | `TestFlyPlugin` |
|---|---|---|
| Test başına olaylar | Evet | Hayır |
| Suite olayları | Evet | Evet (onLoad/onUnload) |
| Yapılandırmaya erişim | Hayır | Evet (onLoad aracılığıyla) |
| Tipik kullanım | Test başına metrikler, uyarılar | Başlatma, harici istemciler |