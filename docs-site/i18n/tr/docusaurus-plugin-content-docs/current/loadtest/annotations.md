---
id: annotations
title: Deklaratif Anotasyonlar (@LoadTest)
description: "@LoadTest ve @LoadEngine anotasyonları ile test sınıfları ve metotları üzerinde deklaratif performans tanımlamaları yapın."
sidebar_position: 4
---

# Deklaratif Anotasyonlar (@LoadTest)

TestFly, akıcı kodlama yönteminin yanında `@LoadTest` ve `@LoadEngine` anotasyonları ile deklaratif yük testlerini de destekler. Bu yaklaşım, ekipler arası standartları korumak ve test konfigürasyonunu doğrulama mantığından ayırmak için idealdir.

---

## 1. `@LoadTest` Anotasyonu

`@LoadTest` anotasyonunu herhangi bir test metoduna veya sınıfına ekleyebilirsiniz:

```java
import io.testfly.loadtest.LoadTest;
import io.testfly.test.BaseTest;
import org.testng.annotations.Test;

public class EnvanterKarsilastirmaSuite extends BaseTest {

    @Test
    @LoadTest(
        users = 50,
        rampUpSeconds = 10,
        durationSeconds = 30,
        targetRps = 200
    )
    public void testEnvanterApi() {
        load("/api/inventory/status")
            .run()
            .assertP95Below(150)
            .assertNoStatus(500);
    }
}
```

Anotasyonda belirtilen değerler, `testfly.yml` içerisindeki varsayılan değerlerin üzerine yazılır. Eğer metot içinde akıcı API ile açık bir çağrı (örneğin `.users(100)`) yapılırsa, kod içindeki değer önceliklidir.

### Anotasyon Nitelikleri

| Nitelik | Tip | Varsayılan | Açıklama |
| :--- | :--- | :--- | :--- |
| `users` | `int` | `1` | Eşzamanlı sanal kullanıcı sayısı. |
| `rampUpSeconds` | `int` | `0` | Doğrusal artış süresi (saniye). |
| `durationSeconds` | `int` | `10` | Zirve yük süresi (saniye). |
| `targetRps` | `int` | `0` | Maksimum istek hızı (0 = sınırsız). |
| `scenarioName` | `String` | `""` | Raporlarda görünecek senaryo adı. |

---

## 2. `@LoadEngine` Anotasyonu

`testfly.yml` dosyasını değiştirmeden belirli bir test için istenen motoru zorunlu kılabilirsiniz:

```java
import io.testfly.loadtest.LoadEngine;
import io.testfly.loadtest.LoadTest;
import io.testfly.test.BaseTest;
import org.testng.annotations.Test;

public class YuksekYogunlukTesti extends BaseTest {

    @Test
    @LoadEngine("gatling") // Kesinlikle Gatling motorunu devreye sokar
    @LoadTest(users = 500, rampUpSeconds = 20, durationSeconds = 60)
    public void yogunTrafikSimulasyonu() {
        load("/api/heavy-computation")
            .run()
            .assertThroughputAbove(400);
    }
}
```

Desteklenen motor isimleri:
- `"auto"`: Sınıf yolunda Gatling arar; bulamazsa yerel JDK iş parçacıklarına döner.
- `"gatling"`: Gatling bulunamazsa testi çalıştırmayıp derhal hata fırlatır.
- `"jdk"`: Her zaman JDK HttpClient ve sanal iş parçacıklarını kullanır.

---

## 3. Sınıf Düzeyinde Kalıtım

Tüm test sınıfını anotasyonla işaretleyerek sınıf içerisindeki tüm testlerin bu yük profilini paylaşmasını sağlayabilirsiniz:

```java
@LoadTest(users = 25, durationSeconds = 15)
@LoadEngine("gatling")
public class UrunServisiTestleri extends BaseTest {

    @Test
    public void testOneCikanUrunler() {
        load("/api/featured").run().assertP95Below(200);
    }

    @Test
    @LoadTest(users = 100) // Sadece 'users' değerini ezer, motor ve süreyi sınıftan devralır
    public void testAramaServisi() {
        load("/api/search?q=telefon").run().assertP95Below(350);
    }
}
```
