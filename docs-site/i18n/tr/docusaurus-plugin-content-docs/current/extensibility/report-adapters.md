---
description: "Selenium test sonuçlarını ReportAdapter ile istediğiniz formata dönüştürün: Slack mesajları, Allure, e-posta özetleri veya metrik JSON'undan özel panolar."
id: report-adapters
title: Rapor Adaptörleri
sidebar_position: 4
---

# Rapor Adaptörleri

`ReportAdapter`, metrik JSON'undan istediğiniz çıktı biçimini üretmenizi sağlar — Slack mesajları, Allure girdisi, e-posta özetleri, özel panolar. Yerleşik HTML adaptörü her zaman çalışır; sizin adaptörleriniz ondan sonra eklenir.

---

## Bir rapor adaptörü oluşturun

```java
import io.testfly.reporting.ReportAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;

public class SlackReportAdapter implements ReportAdapter {

    @Override
    public String getName() {
        return "slack";
    }

    @Override
    public void generate(File metricsJson) {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(metricsJson);

        int total    = root.path("total").asInt();
        int passed   = root.path("passed").asInt();
        int failed   = root.path("failed").asInt();
        double rate  = root.path("passRate").asDouble();

        String message = String.format(
            "Test run complete — %d/%d passed (%.1f%%)%s",
            passed, total, rate,
            failed > 0 ? " :red_circle: " + failed + " failures" : " :white_check_mark:"
        );

        SlackClient.post("#test-results", message);
    }
}
```

---

## Java SPI ile kaydettirin (otomatik keşif)

```
src/main/resources/META-INF/services/io.testfly.reporting.ReportAdapter
```

İçerik:

```
com.example.reporting.SlackReportAdapter
```

---

## Programatik olarak kaydettirin

```java
import io.testfly.reporting.ReportAdapterRegistry;

ReportAdapterRegistry.register(new SlackReportAdapter());
```

---

## Metrik JSON yapısı

`generate()` öğesine geçirilen `metricsJson` dosyası (`target/testfly-metrics.json`) şunları içerir:

```json
{
  "total": 25,
  "passed": 23,
  "failed": 1,
  "skipped": 1,
  "passRate": 92.0,
  "flakyTests": 2,
  "recoveredTests": 1,
  "totalDurationMs": 45231,
  "tests": [
    {
      "testId": "LoginTest#validLogin",
      "testClassName": "LoginTest",
      "status": "PASSED",
      "startTime": 1710000000000,
      "endTime": 1710000002341,
      "totalMs": 2341,
      "retryCount": 0,
      "errorMessage": null,
      "stackTrace": null,
      "steps": [
        { "name": "Open login page", "offsetMs": 0, "status": "INFO", "screenshotBase64": null }
      ]
    }
  ]
}
```

---

## Adaptör yürütme sırası

1. Yerleşik `HtmlReportAdapter` (her zaman önce)
2. SPI ile keşfedilen adaptörler (keşif sırasına göre)
3. Programatik olarak kaydedilen adaptörler

Her adaptör bağımsız çalışır — birindeki hata günlüğe kaydedilir ancak diğerlerinin çalışmasını engellemez.

---

## Allure entegrasyonu

Allure TestNG bağımlılığını ekleyerek TestFly'nin yanında Allure'u çalıştırın. İkisi de listener'ları SPI aracılığıyla bağımsız olarak kaydeder:

```xml title="pom.xml"
<dependency>
    <groupId>io.qameta.allure</groupId>
    <artifactId>allure-testng</artifactId>
    <version>2.25.0</version>
</dependency>
```

`ReportAdapter` gerekmez — Allure doğrudan TestNG'e bağlanır. Tek bir çalıştırmadan hem TestFly HTML raporunu hem de eksiksiz bir Allure raporunu elde edersiniz.