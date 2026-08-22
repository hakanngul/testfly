---
description: "TestFly'ı eklentilerle genişletin: TestFlyPlugin, Java SPI ve ServiceLoader aracılığıyla otomatik keşfedilen bir şekilde framework'ün yanında davranış ekler."
id: plugins
title: Eklentiler
sidebar_position: 1
---

# Eklentiler

`TestFlyPlugin`, framework'ün yanında çalışan davranış eklemek için ana genişletme noktasıdır. Eklentiler, Java SPI aracılığıyla otomatik olarak keşfedilir — testlerinizde kayıt kodu gerekmez.

---

## Bir eklenti oluşturun

```java
import io.testfly.config.TestFlyConfig;
import io.testfly.extension.TestFlyPlugin;

public class SlackNotificationPlugin implements TestFlyPlugin {

    @Override
    public String getName() {
        return "slack-notification";
    }

    @Override
    public String minFrameworkVersion() {
        return "0.7.0";   // framework bunu yüklemeden önce kontrol eder
    }

    @Override
    public void onLoad(TestFlyConfig config) {
        // config yüklendikten sonra bir kez çağrılır — ayarları okuyun, bağlantıları açın
        String baseUrl = config.getBrowser().getBaseUrl();
        System.out.println("SlackPlugin initialised for " + baseUrl);
    }

    @Override
    public void onUnload() {
        // tüm raporlar üretildikten sonra bir kez çağrılır — flaşlayın, kapatın, temizleyin
    }
}
```

---

## Java SPI ile kaydettirin (otomatik keşif)

SPI kayıt dosyasını projenizde oluşturun:

```
src/main/resources/META-INF/services/io.testfly.extension.TestFlyPlugin
```

İçerik — satır başına bir tam nitelikli sınıf adı:

```
com.example.plugins.SlackNotificationPlugin
```

TestFly, JAR'ınız sınıf yolundayken bu eklentiyi otomatik olarak keşfeder ve yükler. Listener kaydı yok, yapılandırma girişi yok.

---

## Programatik olarak kaydettirin

Framework bootstrap'ından önce kaydedilmesi gereken eklentiler için:

```java
import io.testfly.extension.PluginRegistry;
import io.testfly.context.TestFlyContext;

PluginRegistry.register(new SlackNotificationPlugin(), TestFlyContext.getConfig());
```

---

## Sürüm uyumluluğu

Eklentinizin gerektirdiği minimum framework sürümünü bildirin:

```java
@Override
public String minFrameworkVersion() {
    return "0.7.0";
}
```

Çalışan framework daha eskiyse, eklenti **bir uyarıyla atlanır** — derlemeyi başarısız yapmaz. Ayrıca `onLoad` içinden de iddia edebilirsiniz:

```java
import io.testfly.extension.FrameworkVersion;

@Override
public void onLoad(TestFlyConfig config) {
    FrameworkVersion.requireAtLeast("0.7.0");  // çok eskiyse IncompatiblePluginException fırlatır
}
```

Çalışma zamanında mevcut sürümü kontrol edin:

```java
String version = FrameworkVersion.get();  // ör. "0.7.0"
```

---

## Eklenti yaşam döngüsü

```
Suite başlar
  → PluginRegistry.loadAll()         // SPI keşfi + onLoad() çağrılır
  → [tüm testler çalışır]
  → Raporlar üretilir
  → PluginRegistry.unloadAll()       // her eklentide onUnload() çağrılır
Suite biter
```

`onLoad` hataları günlüğe kaydedilir ancak suite'i sonlandırmaz. `onUnload` hataları da izole edilir.

---

## Eklentiler ne için iyidir

| Kullanım durumu | Yaklaşım |
|---|---|
| Suite düzeyinde kurulum/teardown | `onLoad` / `onUnload` |
| Framework yapılandırmasını okuma | `onLoad(TestFlyConfig config)` |
| Harici istemcileri başlatma | `onLoad` |
| Metrikleri boşaltma / bağlantıları kapatma | `onUnload` |
| Test başına olaylar | Bunun yerine [`ExecutionHook`](/docs/extensibility/hooks) kullanın |
| Özel rapor üretimi | Bunun yerine [`ReportAdapter`](/docs/extensibility/report-adapters) kullanın |