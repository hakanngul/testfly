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

Aşağıdaki örnek metrik JSON'unu ayrıştırır ve Slack'e tek satırlık bir özet gönderir.

:::note `SlackClient` varsayımsaldır
`SlackClient`, **sizin kendi** Slack entegrasyonunuzu (incoming-webhook sarmalayıcısı, resmi Slack SDK'sı vb.) temsil eder. TestFly'ın bir parçası **değildir** ve framework ile birlikte gelmez. Jackson (`ObjectMapper`) ise TestFly bağımlılığı olarak zaten classpath'tedir.
:::

```java
import io.testfly.reporting.ReportAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.slack.SlackClient; // varsayımsal — kendi Slack entegrasyonunuz
import java.io.File;
import java.io.IOException;

public class SlackReportAdapter implements ReportAdapter {

    private final SlackClient slack = new SlackClient(System.getenv("SLACK_WEBHOOK_URL"));

    @Override
    public String getName() {
        return "slack";
    }

    @Override
    public void generate(File metricsJson) {
        // ReportAdapter.generate(File) checked exception bildirmez,
        // bu yüzden Jackson'ın IOException'ı burada ele alınmalıdır.
        final JsonNode root;
        try {
            root = new ObjectMapper().readTree(metricsJson);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read TestFly metrics: " + metricsJson, e);
        }

        int total    = root.path("totalTests").asInt();
        int passed   = root.path("passedTests").asInt();
        int failed   = root.path("failedTests").asInt();
        double rate  = root.path("passRate").asDouble();

        String message = String.format(
            "Test run complete — %d/%d passed (%.1f%%)%s",
            passed, total, rate,
            failed > 0 ? " :red_circle: " + failed + " failures" : " :white_check_mark:"
        );

        slack.post(message);
    }
}
```

Özet alanları (`totalTests`, `passedTests`, `failedTests`, `passRate`) metrik JSON'una yazılan anahtarlarla eşleşir. `generate()` içinden fırlatılan bir exception registry tarafından yakalanıp loglanır; build'i düşürmez ve diğer adaptörleri durdurmaz.

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

`generate()` metoduna geçirilen `metricsJson` dosyası (varsayılan olarak `target/testfly-metrics.json` — bkz. [Çıktı konumu](#report-paths)) şunları içerir:

```json
{
  "totalTests": 25,
  "passedTests": 23,
  "failedTests": 1,
  "skippedTests": 1,
  "passRate": 92.0,
  "flakyTests": 2,
  "recoveredTests": 1,
  "totalTimeMs": 45231,
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

Framework başlatılırken (bootstrap) registry şu sırayla doldurulur:

1. Yerleşik `HtmlReportAdapter`
2. SPI ile keşfedilen adaptörler (`ServiceLoader` keşif sırasına göre)
3. Programatik olarak kaydedilen adaptörler — örn. bir eklentinin `onLoad` metodundan, ayrıca Allure adaptörü gibi isteğe bağlı yerleşik adaptörler

Suite bittiğinde adaptörler, registry'nin bir anlık görüntüsü üzerinde **kayıt sırasına göre seri olarak** çalışır — asla paralel değil, çünkü adaptörler aynı çıktı dosyalarını okuyabilir veya yazabilir. Her adaptör aynı `metricsJson` `File` nesnesini alır ve sonraki adaptörler çalıştığında HTML adaptörü raporunu çoktan yazmıştır. Paylaşılan dosyaları salt okunur kabul edin ve kendi çıktınızı ayrı bir dosya adına yazın. Bir adaptörün fırlattığı exception loglanır (`[TestFly] ReportAdapter [name] failed: ...`) ve kalan adaptörler yine çalışır.

:::caution Bootstrap'tan sonra kaydedin
`register(...)` yalnızca listeye ekleme yapar. Framework başlatılmadan önce çağırırsanız adaptörünüz HTML adaptörünün **önüne** yerleşir. Yukarıdaki sırayı korumak için `TestFlyPlugin.onLoad` içinden (veya daha sonra) kaydedin.
:::

## Çıktı konumu (`ReportPaths`) {#report-paths}

`ReportPaths`, rapor temel dizinini şöyle çözümler:

- Ayarlıysa ve boş değilse `testfly.reports.dir` sistem özelliği (örn. `-Dtestfly.reports.dir=target/junit5`)
- Aksi halde Gradle düzeni için `build` (`build/` var ve `target/` yoksa)
- Aksi halde `target`

`generate()` metoduna geçirilen metrik dosyası `<baseDir>/testfly-metrics.json`, HTML rapor ise `<baseDir>/testfly-report.html` olur. Birden fazla motoru (örneğin Surefire ile TestNG ve Failsafe ile JUnit 5) farklı `testfly.reports.dir` değerlerine yönlendirin ki birbirlerinin raporlarının üzerine yazmasınlar. Kendi çıktı yollarınızı `target/` diye sabitlemek yerine `metricsJson.getParentFile()` üzerinden çözümleyin.

---

## Allure entegrasyonu

TestFly yerleşik Allure sonuç adaptörü içerir. `reporting.allure.enabled: true` ayarını açın ve [Allure rehberini](/docs/reporting/allure) izleyin; bu adaptör için `allure-testng` bağımlılığı gerekmez.

Allure’ın TestNG listener’ını ayrıca kullanırsanız o harici entegrasyonu kendiniz yapılandırın. İki entegrasyonun aynı sonuç klasörüne yazmasını veya aynı testlerin iki kez sayılmasını önleyin.
