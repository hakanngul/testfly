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
| **Temel Altyapı** | Gatling 3.13.x + Netty asenkron IO | Java 21+ `HttpClient` + Sanal İş Parçacıkları |
| **Bağımlılık Durumu** | İsteğe bağlı (`gatling-charts-highcharts`)| Java standart kütüphanesine gömülü |
| **Süreç İzolasyonu** | Forked ayrı JVM alt süreci | Test iş parçacığında aynı süreçte çalışır |
| **Yerel Rapor** | İnteraktif Highcharts HTML raporu | Bütünleşik TestFly HTML gösterge paneli |
| **Alt Süreç Logu** | `gatling-subprocess.log` | Standart TestFly log akışı |
| **Gecikme Yüzdelikleri** | p50, p75, p95, p99 (p90 ölçülmez) | p50, p75, p90, p95, p99 |
| **HTTP Durum Kodları** | Yanıt başına gerçek durum (`testfly-status.json`) | Yanıt başına gerçek durum |
| **Kontrollerde JSONPath** | Tam Gatling JSONPath (`$` olmayan yol `$.yol` olarak okunur) | Yalnızca `$.a.b`, `$.a[0].b`, `$[0]` (aynı `$.yol` kuralı) |
| **Önerilen Kullanım** | Stres testleri, CI/CD hatları | Yerel duman testleri, hızlı PR doğrulamaları |

---

## 2. Otomatik Motor Seçimi (`auto`)

Varsayılan `loadtest.engine: auto` ayarı, sınıf yolunu dinamik olarak inceler:

```java
// Sınıf yolunda io.gatling.app.Gatling sınıfı mevcutsa:
//    GatlingEngine devreye girer
// Yoksa:
//    ClassNotFoundException fırlatmadan JdkLoadEngine altyapısına geri çekilir
```

Bu sayede Gatling bağımlılıkları olmayan makinelerde de testler hatasız şekilde koşturulabilir. Geri çekilme sessiz değildir: JVM'deki ilk geri çekilme bir WARN loglar ve her koşu çözümlenen motoru adım adında gösterir, örneğin `engine=jdk (auto: Gatling not on classpath)`. `metrics().engine()` gerçekten çalışan motoru döndürür.

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
@io.testfly.loadtest.LoadTest(engine = "gatling")
public void stresTesti() {
    load("/api/payment/checkout").run();
}
```


TestFly, herhangi bir istek gönderilmeden önce senaryoyu çözümlenen motora göre doğrular ve o motorun doğru çalıştıramayacağı girdilerde `IllegalArgumentException` fırlatır; örneğin JDK alt kümesi dışındaki bir JSONPath veya hem body hem form parametresi içeren bir adım. Bkz. [Fluent API](./fluent-api.md#kontroller-ve-istek-seçenekleri).

## 4. Gatling Koşu Yaşam Döngüsü

Her Gatling koşusu kendi alt JVM'inde ve kendi sonuç dizininde (`<resultsDir>/<run-id>/`) çalışır:

- Her isteğin `requestTimeoutSeconds` zaman aşımı vardır. Simülasyonun `maxDuration` değeri `rampUp + hold + requestTimeoutSeconds + 30s` olur; bu süre dolunca Gatling durur ve raporunu yine de yazar.
- TestFly alt JVM'i en fazla `maxDuration + 120s` bekler. Hâlâ çalışıyorsa alt süreç ve alt süreçleri sonlandırılır ve koşu `IllegalStateException` ile başarısız olur. Test iş parçacığı kesildiğinde (interrupt) ve ana JVM kapandığında da alt süreç sonlandırılır.
- Sıfırdan farklı çıkış kodu koşuyu başarısız kılar. Exception çıkış kodunu ve `gatling-subprocess.log` dosyasının son 20 satırını içerir; kimlik bilgileri (`Authorization`, `Cookie`, `Bearer` token'ları ile token, password, secret ve API-key değerleri) `***` ile maskelenir.
- HTTP durum kodları koşu dizininde, Gatling raporunun yanında `testfly-status.json` dosyasına yazılır. Gecikmeler Gatling'in `js/stats.js` dosyasından okunur. TestFly, Gatling 3.13'ten beri ikili (binary) ve iç format olan `simulation.log` dosyasını asla ayrıştırmaz.
- Gatling'in HTTP ısınma (warm-up) isteği ve kullanım analitiği kapalıdır; koşu yalnızca test edilen sisteme bağlanır.

JDK motoru iptal için aynı kuralı izler: test iş parçacığı kesildiğinde (interrupt) veya koşu güvenlik zaman aşımını (`rampUp + hold + cooldown + 60s`) geçtiğinde sanal kullanıcılar durdurulur ve koşu `IllegalStateException` ile başarısız olur. Yarım metrikler hiçbir zaman normal sonuç olarak döndürülmez.

Eşzamanlılık ve throughput donanıma, hedef servise ve senaryoya bağlıdır. Sabit kapasite veya başlangıç süresi garantisi yoktur; kendi yükünüzü ölçün. JDK yük motoru kendi transport’unu kullanır; ApiClient interceptor ve mock kuralları otomatik uygulanmaz.
