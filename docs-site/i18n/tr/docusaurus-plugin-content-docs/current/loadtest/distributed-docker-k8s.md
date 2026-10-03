---
id: distributed-docker-k8s
title: Dağıtık Yük Testi (Docker ve Kubernetes)
sidebar_label: Docker ve Kubernetes
sidebar_position: 10
description: "TestFly yük ve performans testlerini, birden fazla Docker konteynerini veya Kubernetes pod'unu bağımsız yük üreticisi olarak çalıştırarak yatay ölçeklendirin."
---

# Docker ve Kubernetes ile Dağıtık Yük Testi

Duman (smoke) ve regresyon yük testleri için çoğu zaman tek bir makine yeterlidir. Tek bir üretici ihtiyaç duyduğunuz yükü üretemediğinde **yatay** ölçeklendirebilirsiniz: yük suite'ini bir konteynere paketleyin ve birden fazla kopyasını **Docker Compose** ile veya bir **Kubernetes** Job olarak paralel çalıştırın.

Her konteyner bağımsız bir TestFly koşumudur. Ulaşılabilecek eşzamanlılık ve throughput donanıma, hedef servise ve senaryoya bağlıdır. Sabit bir kapasite garantisi yoktur, bu yüzden kendi yükünüzü ölçün (bkz. [Çalıştırma Motorları](./engines.md)).

---

## 1. Ayarları Ortam Değişkenleriyle Aktarma

TestFly, `testfly.yml` içindeki **string** değerlerde `${VAR}` ve `${VAR:-default}` placeholder'larını çözer (shell ortamı, `.env` veya `-D` sistem property'si). `loadtest.users` veya `reporting.reportportal.enabled` gibi sayısal ve boolean anahtarlar placeholder'dan çözülmez. Bunları sabit değer olarak yazın ya da değişkeni test kodunda okuyun.

```yaml title="testfly.yml"
execution:
  mode: local
  baseUrl: ${TESTFLY_API_BASEURL:-https://staging.example.com}

loadtest:
  baseUrl: ${TESTFLY_API_BASEURL:-https://staging.example.com}
  engine: auto
  maxUsers: 5000          # upper bound per container; users above this are clamped
  resultsDir: target/loadtest
```

Konteyner başına kullanıcı sayısını testte okuyun ve SLO'larınızı akıcı yük assertion'larıyla doğrulayın:

```java title="CheckoutLoadTest.java"
import io.testfly.loadtest.BaseLoadTest;
import org.testng.annotations.Test;

public class CheckoutLoadTest extends BaseLoadTest {

    private static final int USERS =
            Integer.parseInt(System.getenv().getOrDefault("TESTFLY_LOAD_VUSERS", "100"));

    @Test
    public void checkoutUnderLoad() {
        load("/api/checkout")
            .users(USERS)
            .run()
            .assertErrorRateBelow(0.001)   // 0.0–1.0 → 0.1%
            .assertP99Below(800)           // ms
            .assertP95Below(500);
    }
}
```

---

## 2. TestFly Yük Testlerini Konteynerleştirme

Performans suite'inizi çalıştıran minimal, headless bir Docker konteyneri oluşturun:

```dockerfile title="Dockerfile.loadtest"
FROM maven:3.9.6-eclipse-temurin-21

WORKDIR /app
COPY pom.xml .
COPY testfly.yml .
COPY src ./src

# Pre-fetch dependencies
RUN mvn dependency:go-offline -B

# Execute performance tests
ENTRYPOINT ["mvn", "test", "-Dtest=*LoadTest", "-B"]
```

İmajı derleyin:
```bash
docker build -t testfly-load-runner:latest -f Dockerfile.loadtest .
```

---

## 3. Çok Worker'lı Docker Compose

Birden fazla worker'ı yerelde veya ayrılmış bir VM üzerinde çalıştırın. Her worker sonuçlarını ayrı bir volume'a yazar:

```yaml title="docker-compose.load.yml"
services:
  load-worker-1:
    image: testfly-load-runner:latest
    environment:
      - TESTFLY_LOAD_VUSERS=2500
      - TESTFLY_API_BASEURL=https://target-service.internal
    volumes:
      - ./target/load-results-1:/app/target

  load-worker-2:
    image: testfly-load-runner:latest
    environment:
      - TESTFLY_LOAD_VUSERS=2500
      - TESTFLY_API_BASEURL=https://target-service.internal
    volumes:
      - ./target/load-results-2:/app/target
```

---

## 4. Kubernetes Üzerinde Yatay Ölçeklendirme (Job manifest)

Yükü bir kümeye yaymak için aynı imajı paralel bir Kubernetes Job olarak çalıştırın:

```yaml title="loadtest-job.yaml"
apiVersion: batch/v1
kind: Job
metadata:
  name: testfly-load-surge
spec:
  parallelism: 20       # 20 concurrent pods
  completions: 20
  template:
    spec:
      containers:
      - name: load-runner
        image: your-registry.io/testfly-load-runner:latest
        resources:
          requests:
            cpu: "2"
            memory: "4Gi"
          limits:
            cpu: "4"
            memory: "8Gi"
        env:
        - name: TESTFLY_LOAD_VUSERS
          value: "500"
        - name: TESTFLY_API_BASEURL
          value: "https://target-service.internal"
        - name: RP_API_KEY
          valueFrom:
            secretKeyRef:
              name: reportportal
              key: apiKey
      restartPolicy: Never
```

### Sonuçlar ve Kalite Kapıları

- **Pod başına sonuçlar:** Her pod kendi yük metriklerini (`testfly-metrics.json`, TestFly HTML raporu ve `loadtest.reportEnabled: true` ise `loadtest-report.html`) kendi `target/` dizini altında üretir. TestFly metrikleri pod'lar arasında birleştirmez. Her pod'un `target/` dizinini toplayın (ör. paylaşılan bir volume'a veya object storage'a) ve sonuçları kendiniz karşılaştırın ya da birleştirin.
- **ReportPortal (isteğe bağlı):** `reporting.reportportal.enabled: true`, `endpoint` ve `apiKey: ${RP_API_KEY}` yapılandırıldığında sonuçları ReportPortal TestNG agent'ı yükler. Her pod kendi launch'ını raporlar. Yerleşik `ReportPortalReportAdapter` yalnızca yapılandırmayı doğrular ve dashboard URL'ini içeren bir launch özeti yazdırır. Canlı gecikme yüzdeliklerini aktarmaz.
- **SLO kontrolleri:** Gecikme ve hata oranı eşiklerini akıcı yük assertion'larına (`assertP95Below`, `assertP99Below`, `assertErrorRateBelow`, `assertThroughputAbove`, …) koyun. Böylece SLO'su ihlal edilen pod'un testi başarısız olur.
- **Suite seviyesinde kapı:** `BuildThresholdEnforcer` her koşumdaki test sonuçları üzerinden yalnızca `ci.failOnPassRateBelow` ve `ci.maxFlakyTests` değerlerini değerlendirir. Gecikme veya hata oranını kontrol etmez.

```yaml title="testfly.yml"
ci:
  failOnPassRateBelow: 100.0
  maxFlakyTests: 0
```
