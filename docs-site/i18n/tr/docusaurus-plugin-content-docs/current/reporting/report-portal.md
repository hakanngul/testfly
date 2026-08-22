---
description: "TestNG test sonuçlarını sıfır boilerplate kodla ReportPortal'a gönderin."
id: report-portal
title: ReportPortal Entegrasyonu
sidebar_position: 3
---

# ReportPortal Entegrasyonu

TestFly, TestNG sonuçlarını gerçek zamanlı olarak [ReportPortal](https://reportportal.io/)'a akışla gönderebilir. Entegrasyon, resmî ReportPortal TestNG aracısını kullanır ve `testfly.yml` aracılığıyla tamamen tercihe bağlıdır (opt-in).

## Neler raporlanır

* TestNG'deki her test yöntemi, ReportPortal'da bir test öğesi olarak raporlanır.
* Launch (başlatma) adı, açıklaması ve öznitelikleri `testfly.yml` dosyasından alınır.
* Test durumları, ReportPortal aracısı tarafından otomatik olarak eşlenir.
* Takım (suite) bittikten sonra TestFly, ReportPortal panel URL'sini ve bir özeti konsola yazdırır.

## Ön koşullar

ReportPortal TestNG aracısı, TestFly içinde **isteğe bağlı** bir bağımlılıktır. Bunu projenize açıkça ekleyin:

```xml
<dependency>
    <groupId>com.epam.reportportal</groupId>
    <artifactId>agent-java-testng</artifactId>
    <version>5.6.8</version>
</dependency>
```

Ya da Gradle ile:

```groovy
testImplementation 'com.epam.reportportal:agent-java-testng:5.6.8'
```

## ReportPortal'ı etkinleştirin

`testfly.yml` dosyasına `reporting.reportportal` bloğunu ekleyin:

```yaml
reporting:
  reportportal:
    enabled: true
    endpoint: http://localhost:8080
    apiKey: ${RP_API_KEY}
    project: superadmin_personal
    launch: "TestFly Regression"
    description: "Automated TestFly test execution"
    attributes: "env:ci;branch:main"
```

### Yapılandırma seçenekleri

| Seçenek | Zorunlu | Varsayılan | Açıklama |
|--------|----------|---------|-------------|
| `enabled` | hayır | `false` | ReportPortal raporlamasını açıp kapar. |
| `endpoint` | evet | — | ReportPortal sunucu URL'si. |
| `apiKey` | evet | — | ReportPortal API anahtarı. `${RP_API_KEY}` kullanın ve ortam değişkeniyle ekleyin. |
| `project` | evet | `superadmin_personal` | ReportPortal proje adı. |
| `launch` | evet | `TestFly Launch` | Launch adı. |
| `description` | hayır | `Automated TestFly test execution` | Launch açıklaması. |
| `attributes` | hayır | — | Noktalı virgülle ayrılmış anahtar:değer çiftleri, örn. `env:ci;branch:main`. |

## Nasıl çalışır

1. `FrameworkBootstrap.initialize()` ReportPortal yapılandırmasını okur.
2. Yapılandırma, ReportPortal sistem özelliklerine (`rp.endpoint`, `rp.api.key` vb.) dönüştürülür.
3. `SuiteExecutionListener`, aracı sınıf yolundayken `com.epam.reportportal.testng.ReportPortalTestNGListener` öğesini dinamik olarak kaydeder.
4. Aracı, testler çalışırken sonuçları yükler.
5. Takım bittikten sonra `ReportPortalReportAdapter` panel URL'sini ve özeti günlüğe kaydeder.

## Ortam değişkenleri

Gizli bilgiler için onları kod içine gömmek yerine ortam değişkenlerini kullanın:

```yaml
reporting:
  reportportal:
    apiKey: ${RP_API_KEY}
    endpoint: ${RP_ENDPOINT}
```

## ReportPortal'ı devre dışı bırakma

`enabled: false` değerini ayarlayın veya `reportportal` bloğunu tamamen kaldırın:

```yaml
reporting:
  reportportal:
    enabled: false
```

## Sorun giderme

### `ReportPortal etkin, ancak TestNG aracısı sınıf yolunda değil`

[Ön koşullar](#prerequisites) bölümünde gösterildiği gibi `com.epam.reportportal:agent-java-testng` bağımlılığını proje bağımlılıklarınıza ekleyin.

### Zorunlu alan eksik hataları

Sunucu günlüğünü aşağıdaki gibi mesajlar için kontrol edin:

```text
[TestFly] ReportPortal adapter disabled: reporting.reportportal.endpoint is required ...
```

`endpoint`, `apiKey`, `project` ve `launch` alanlarının hepsinin sağlandığından emin olun.

### Launch, ReportPortal'da görünmüyor

Şunları doğrulayın:

* ReportPortal sunucusuna test çalıştırıcısından erişilebiliyor.
* `apiKey` öğesinin yapılandırılmış `project` içine yazma izni var.
* `project` adı, ReportPortal'daki proje URL kısa adıyla eşleşiyor (büyük/küçük harfe duyarlı).