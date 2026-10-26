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
| **Temel Altyapı** | Gatling 3.16.0 + Netty asenkron IO | Java 21+ `HttpClient` + Sanal İş Parçacıkları |
| **Bağımlılık Durumu** | İsteğe bağlı (`gatling-charts-highcharts`) | Java standart kütüphanesine gömülü |
| **Süreç İzolasyonu** | Forked ayrı JVM alt süreci | Test iş parçacığında aynı süreçte çalışır |
| **Yerel Rapor** | İnteraktif Highcharts HTML raporu | Bütünleşik TestFly HTML gösterge paneli |
| **Alt Süreç Logu** | `gatling-subprocess.log` | Standart TestFly log akışı |
| **Metrik Kaynağı** | TestFly'ın kendi yan dosyası `testfly-metrics.json` (sayılar, durum kodları, milisaniyelik gecikme histogramı) | Süreç içinde ölçülür |
| **Gecikme Yüzdelikleri** | p50, p75, p90, p95, p99 (kesin, doğrusal interpolasyon) | p50, p75, p90, p95, p99 (kesin, doğrusal interpolasyon) |
| **Gecikme Kapsamı** | HTTP yanıtı alan istekler | Bağlantı hatalarının ve zaman aşımlarının geçen süresi de dahil |
| **HTTP Durum Kodları** | Yanıt başına gerçek durum | Yanıt başına gerçek durum |
| **Kontrollerde JSONPath** | Tam Gatling JSONPath (`$` olmayan yol `$.yol` olarak okunur) | Yalnızca `$.a.b`, `$.a[0].b`, `$[0]` (aynı `$.yol` kuralı) |
| **`users` / `rampUp` / `hold`** | Evet | Evet |
| **[İş yükü modelleri](./fluent-api.md#workload-models)** (`workload(...)`) | Evet | **Hayır**: herhangi bir istekten önce reddedilir |
| **[Simülasyonlar](./simulations-sla.md)** (`loadSimulation(...)`) | Evet | **Hayır**: herhangi bir istekten önce reddedilir |
| **[SLA'lar](./simulations-sla.md#slas)** (`sla(...)`) | Evet, TestFly değerlendirir; ayrıca Gatling raporunda yerel assertion olarak görünür | Evet, TestFly değerlendirir |
| **[Baseline'lar](./baselines.md)** ve `testfly-loadtest-result.json` | Evet | Evet |
| **Eksik (partial) koşu tespiti** | Evet (kaybolan sonuçlar, `maxDuration` durdurması) | Gerekmez: yarıda kesilen koşu `IllegalStateException` ile başarısız olur |
| **Önerilen Kullanım** | Stres testleri, CI/CD hatları | Yerel duman testleri, hızlı PR doğrulamaları |

Gatling, TestFly'ın isteğe bağlı (optional) bir bağımlılığıdır. Gatling motorunu ve yalnızca Gatling'de çalışan özellikleri kullanmak için kendi projenize `io.gatling.highcharts:gatling-charts-highcharts` ekleyin; TestFly, Gatling 3.16.0 ile derlenir ve test edilir.

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

- Her isteğin `requestTimeoutSeconds` zaman aşımı vardır. Simülasyonun `maxDuration` değeri; yük verme süresi (`rampUp + hold` veya iş yükünün toplam süresi), senaryo adımı başına `requestTimeoutSeconds` ile en uzun düşünme süresi ve 30 saniyenin toplamıdır. Bu süre dolunca Gatling durur ve raporunu yine de yazar; TestFly metrikleri eksik (partial) olarak işaretler.
- TestFly alt JVM'i en fazla `maxDuration + 120s` bekler. Hâlâ çalışıyorsa alt süreç ve alt süreçleri sonlandırılır ve koşu `IllegalStateException` ile başarısız olur. Test iş parçacığı kesildiğinde (interrupt) ve ana JVM kapandığında da alt süreç sonlandırılır.
- Sıfırdan farklı çıkış kodu koşuyu başarısız kılar. Exception çıkış kodunu ve `gatling-subprocess.log` dosyasındaki kök hata satırlarını ve son 20 satırını içerir; kimlik bilgileri (`Authorization`, `Cookie`, `Bearer` token'ları ile token, password, secret ve API-key değerleri) `***` ile maskelenir. Tek istisna: bir [SLA](./simulations-sla.md#slas) yerel Gatling assertion'ları olarak kaydedildiyse 2 çıkış kodu yalnızca "Gatling assertion'ları başarısız" demektir. Koşu her zamanki gibi ayrıştırılır ve kararı TestFly'ın kendi SLA değerlendirmesi verir.
- Hiçbir isteğin gönderilemediği bir koşu (her adım oluşturulamaz, örneğin çözümlenmemiş bir `${var}` yüzünden) 1 çıkış koduyla başarısız olmaz. TestFly metriklerini döndürür: her istek `-1` durumuyla KO'dur. Gatling hiç istek gönderilmediğinde rapor üretmediği için, Gatling'in HTML raporu yazmadığını belirten bir WARN loglanır.
- TestFly metrikleri simülasyon çalışırken kendisi kaydeder ve koşu dizinindeki `testfly-metrics.json` dosyasına yazar: istek adı başına deneme, OK ve KO sayıları, durum kodları ve milisaniyelik gecikme histogramı. `LoadTestMetrics` yalnızca bu dosyadan oluşturulur. TestFly, Gatling'in rapor dosyalarını ve Gatling 3.13'ten beri ikili (binary) ve iç format olan `simulation.log` dosyasını okumaz. Dosya yoksa koşu `IllegalStateException` ile başarısız olur.
- Oluşturulamayan bir istek (örneğin `${var}` değişkeninin değeri olmayan bir istek) yanıtsız KO (`-1` durumu) sayılır. Gatling'in kendi raporu bu isteği içermez; bu yüzden Gatling'in istek toplamları TestFly'ınkinden düşük olabilir.
- Başlatılıp sonucu kaydedilmemiş istekler varsa veya koşu `maxDuration` tarafından durdurulduysa koşu **eksiktir (partial)**. TestFly bir WARN loglar ve `LoadTestMetrics.partial()` değerini `true` yapar. Bu durumda gecikme, durum kodu, hata oranı ve `assertThroughputBelow` doğrulamaları başarısız olur, tüm SLA kuralları başarısız olur ve bu koşudan sonuç dosyası ya da baseline yazılmaz.
- `users/rampUp/hold` döngüsü, `hold` bittiğinde devam eden yinelemeyi JDK motorunda olduğu gibi tamamlar; bu yüzden bir koşu `rampUp + hold` süresinden bir yineleme kadar uzun sürebilir.
- Gatling'in HTTP ısınma (warm-up) isteği ve kullanım analitiği kapalıdır; koşu yalnızca test edilen sisteme bağlanır.

JDK motoru iptal için aynı kuralı izler: test iş parçacığı kesildiğinde (interrupt) veya koşu güvenlik zaman aşımını (`rampUp + hold + cooldown + 60s`) geçtiğinde sanal kullanıcılar durdurulur ve koşu `IllegalStateException` ile başarısız olur. Yarım metrikler hiçbir zaman normal sonuç olarak döndürülmez.

Eşzamanlılık ve throughput donanıma, hedef servise ve senaryoya bağlıdır. Sabit kapasite veya başlangıç süresi garantisi yoktur; kendi yükünüzü ölçün. JDK yük motoru kendi transport’unu kullanır; ApiClient interceptor ve mock kuralları otomatik uygulanmaz.
