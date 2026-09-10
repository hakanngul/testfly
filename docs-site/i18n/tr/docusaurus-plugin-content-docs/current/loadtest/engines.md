---
id: engines
title: Çalıştırma Motorları (Gatling ve JDK)
description: "Gatling ve yerel JDK yük motorları arasındaki mimari farklar, kaynak tüketimi ve kapasite karşılaştırması."
sidebar_position: 8
---

# Çalıştırma Motorları (Gatling ve JDK)

TestFly, motor bağımsız bir mimariye sahiptir. İki farklı motor seçeneği sunar: Kurumsal ölçekli testler için yüksek başarımlı Gatling motoru ve yerel hızlı doğrulamalar için sıfır bağımlılıklı JDK motoru.

---

## 1. Motor Karşılaştırması

| Özellik | Gatling Motoru (`gatling`) | JDK Motoru (`jdk`) |
| :--- | :--- | :--- |
| **Temel Altyapı** | Gatling 3.10.x + Netty asenkron IO | Java 17+ `HttpClient` + Sanal İş Parçacıkları |
| **Bağımlılık Durumu** | İsteğe bağlı (`gatling-charts-highcharts`)| Java standart kütüphanesine gömülü |
| **Maksimum Eşzamanlılık**| 10.000+ sanal kullanıcı | ~100–500 sanal kullanıcı |
| **Süreç İzolasyonu** | Forked ayrı JVM alt süreci | Test iş parçacığında aynı süreçte çalışır |
| **Yerel Rapor** | İnteraktif Highcharts HTML raporu | Bütünleşik TestFly HTML gösterge paneli |
| **Alt Süreç Logu** | `gatling-subprocess.log` | Standart TestFly log akışı |
| **Başlama Maliyeti** | ~1.5–2.5 saniye (JVM ısınması) | < 20 ms (anında) |
| **Önerilen Kullanım** | Stres testleri, CI/CD hatları | Yerel duman testleri, hızlı PR doğrulamaları |

---

## 2. Otomatik Motor Seçimi (`auto`)

Varsayılan `loadtest.engine: auto` ayarı, sınıf yolunu dinamik olarak inceler:

```java
// Sınıf yolunda io.gatling.app.Gatling sınıfı mevcutsa:
//    GatlingEngine devreye girer
// Yoksa:
//    Hata vermeden sessizce JdkLoadEngine altyapısına geri çekilir
```

Bu sayede Gatling bağımlılıkları olmayan makinelerde de testler hatasız şekilde koşturulabilir.

---

## 3. Motoru Açıkça Belirtme

İstediğiniz motoru konfigürasyondan veya kod içinden zorunlu tutabilirsiniz:

### `testfly.yml` Dosyasında
```yaml
loadtest:
  engine: gatling # veya 'jdk'
```

### Kod İçinde (Akıcı API)
```java
load("/api/health")
    .engine("jdk") // Hızlı sonuç için JDK motorunu zorunlu kılar
    .users(10)
    .run();
```

### Anotasyon ile
```java
@Test
@LoadEngine("gatling")
public void stresTesti() {
    load("/api/payment/checkout").run();
}
```
