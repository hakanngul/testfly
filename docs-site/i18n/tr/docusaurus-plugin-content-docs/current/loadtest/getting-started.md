---
id: getting-started
title: Yük Testine Başlarken
description: "TestFly test paketinizin içinden Gatling veya hafif JDK sanal iş parçacıklarıyla doğrudan yük ve performans testleri koşturun."
sidebar_position: 1
---

# Yük Testine Başlarken

TestFly 1.1.0 ile birlikte **Yük ve Performans Testi** yetenekleri doğrudan otomasyon çatı mimarisine dahil edildi. Artık UI, API ve yük testlerinizi aynı temel sınıfları (`BaseTest`, `BaseApiTest`, `BaseJUnit5Test`) kullanarak bağlam değiştirmeden yan yana yazabilirsiniz.

---

## Neden TestFly ile Yük Testi?

Geleneksel otomasyon yapılarında performans testleri fonksiyonel testlerden ayrı tutulur; farklı araçlarda yazılır, ayrı depolarda (repository) tutulur ve nadiren çalıştırılır. TestFly bu bariyeri ortadan kaldırır:

- **Bütünleşik Test Paketi:** 50 kullanıcılı bir yük testini TestNG veya JUnit 5 içinde standart bir `@Test` metodu olarak çalıştırın.
- **Sıfır Konfigürasyonlu Motor:** Sınıf yolunda (classpath) Gatling varsa Gatling motorunu kullanır, yoksa hiçbir harici bağımlılığa ihtiyaç duymadan yerel JDK motoruna sorunsuzca geri çekilir.
- **Akıcı (Fluent) ve Bildirime Dayalı (Declarative) API:** Senaryolarınızı ister `load(url).users(50)...` ile akıcı şekilde, ister `@LoadTest(users = 50)` ile deklaratif olarak tanımlayın.
- **Bütünleşik Raporlama:** Throughput, gecikme yüzdelikleri (P50, P90, P95, P99) ve Gatling'in interaktif Highcharts raporlarını doğrudan TestFly HTML raporunda, Allure'da ve ReportPortal'da görüntüleyin.

---

## 1. Kurulum

Projenizin `pom.xml` dosyasında TestFly `1.1.0` sürümünün tanımlı olduğundan emin olun:

```xml
<dependency>
    <groupId>io.github.hakanngul</groupId>
    <artifactId>testfly</artifactId>
    <version>1.1.0</version>
    <scope>test</scope>
</dependency>
```

### İsteğe Bağlı: Gatling Yüksek Başarımlı Motor

Kurumsal ölçekli yük simülasyonları için Gatling bağımlılığını ekleyebilirsiniz:

```xml
<dependency>
    <groupId>io.gatling.highcharts</groupId>
    <artifactId>gatling-charts-highcharts</artifactId>
    <version>3.10.3</version>
    <scope>test</scope>
</dependency>
```

:::tip Sıfır Konfigürasyonlu Geri Çekilme
Sınıf yolunda Gatling kütüphanesi bulunmadığında TestFly otomatik olarak yerel JDK sanal iş parçacığı motorunu kullanır. Başlamak için Gatling yüklemeniz şart değildir!
:::

---

## 2. İlk Yük Testinizi Yazın

`BaseTest` (veya `BaseApiTest`) sınıfını genişletin ve `load(url)` çağrısı yapın:

```java
package com.example.tests;

import io.testfly.test.BaseTest;
import org.testng.annotations.Test;
import java.time.Duration;

public class SiparisYukTesti extends BaseTest {

    @Test
    public void testSiparisServisiYukAltinda() {
        load("/api/v1/orders")
            .users(20)
            .rampUp(Duration.ofSeconds(5))
            .hold(Duration.ofSeconds(15))
            .run()
            .assertThroughputAbove(50.0)
            .assertP95Below(250)
            .assertErrorRateBelow(0.01)
            .assertNoStatus(500);
    }
}
```

### Arka Planda Neler Olur?

1. **Adres Çözümleme:** `/api/v1/orders` yolu `testfly.yml` dosyanızdaki `execution.baseUrl` ile otomatik birleştirilir.
2. **Çalıştırma:** En uygun motor (varsa Gatling, yoksa JDK) seçilir; 5 saniye içinde 20 eşzamanlı sanal kullanıcıya kademeli olarak çıkılır ve 15 saniye bu yük korunur.
3. **Doğrulama (Assertions):** Yanıt süreleri ve HTTP durum kodları toplanarak performans kriterleri (`assertP95Below`, `assertErrorRateBelow` vb.) denetlenir.
4. **Raporlama:** Metrikler, grafikler ve Gatling rapor bağlantısı otomatik olarak `target/reports/testfly-report.html` dosyasına işlenir.

---

## 3. Anotasyonlar ile Deklaratif Çalıştırma

Standart performans testleriniz için yük profilini `@LoadTest` ile belirtebilirsiniz:

```java
import io.testfly.loadtest.LoadTest;
import io.testfly.test.BaseTest;
import org.testng.annotations.Test;

public class UrunKatalogYukTesti extends BaseTest {

    @Test
    @LoadTest(users = 50, rampUpSeconds = 10, durationSeconds = 30)
    public void testUrunKatalogu() {
        load("/api/products")
            .run()
            .assertP95Below(300);
    }
}
```

---

## Sıradaki Adımlar

- Global ayarlar için [Konfigürasyon](./configuration.md) kılavuzunu inceleyin.
- Çok adımlı akışlar için [Akıcı API Rehberi](./fluent-api.md) sayfasına göz atın.
- Dinamik verilerle besleme yapmak için [Veri Besleyiciler (Feeders)](./feeders.md) bölümünü keşfedin.
- Performans doğrulama kuralları için [Doğrulamalar (Assertions)](./assertions.md) sayfasına bakın.
