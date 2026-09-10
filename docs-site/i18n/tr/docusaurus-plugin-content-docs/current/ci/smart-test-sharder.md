---
id: smart-test-sharder
title: Smart Test Sharder (LPT Bin-Packing)
sidebar_label: Smart Test Sharder
sidebar_position: 6
description: "CI/CD paralel koşumlarında darboğazları ortadan kaldırmak için Longest Processing Time (LPT) bin-packing algoritmasıyla test paketini optimum dengeyle dağıtın."
---

# Smart Test Sharder

**Smart Test Sharder**, CI/CD boru hatlarındaki "en yavaş worker darboğazını" (straggler effect) ortadan kaldırır. Testleri körlemesine (alfabetik veya round-robin) bölmek yerine, **Longest Processing Time (LPT) Bin-Packing algoritmasını** uygulayarak tüm worker'ların neredeyse aynı anda tamamlanmasını sağlar.

```
Smart Sharder Olmadan (Round-Robin / Kör Dağıtım):
Worker 1 [4dk]  ████
Worker 2 [18dk] ██████████████████  ← Pipeline 18 dakika boyunca kilitli kalır!
Worker 3 [2dk]  ██
Worker 4 [5dk]  █████

Smart Test Sharder ile (LPT Bin-Packing):
Worker 1 [7dk 15sn] ███████
Worker 2 [7dk 20sn] ███████
Worker 3 [7dk 18sn] ███████
Worker 4 [7dk 12sn] ███████          ← Pipeline 7dk 20sn'de biter (%59 daha hızlı!)
```

---

## Nasıl Çalışır?

1. **Geçmiş Sürelerin Çözümlenmesi:** TestFly, önceki test sürelerini `target/testfly-metrics.json` (veya `testfly-metrics-history/`) dosyasından okur.
2. **LPT Sıralaması:** Tüm testler sürelerine göre büyükten küçüğe sıralanır ($t_1 \ge t_2 \ge \dots \ge t_n$).
3. **Greedy Min-Heap Ataması:** Her test, o an en az birikmiş iş yüküne sahip olan worker node'una atanır.
4. **4/3-Yaklaşımlı Matematiksel Garanti:** LPT algoritması, çok işlemcili zamanlama probleminde optimuma en yakın yük dağılımını matematiksel olarak garanti eder.

---

## Seçenek 1 — Sıfır Konfigürasyonlu TestNG Dağıtımı (Önerilen)

TestFly'ı TestNG ile kullanırken yalnızca `-Dtestfly.shard.total` ve `-Dtestfly.shard.index` sistem özelliklerini vermeniz yeterlidir. TestFly'ın yerleşik `ShardingMethodInterceptor` bileşeni suite'i XML dosyasına dokunmadan otomatik olarak filtreler:

```bash
# Node 1 / 4 (Index 0)
mvn test -Dtestfly.shard.total=4 -Dtestfly.shard.index=0

# Node 2 / 4 (Index 1)
mvn test -Dtestfly.shard.total=4 -Dtestfly.shard.index=1
```

### CI Ortam Değişkenlerini Otomatik Algılama

TestFly standart CI node ortam değişkenlerini otomatik olarak tanır:
- `CI_NODE_TOTAL`
- `CI_NODE_INDEX`

Bu değişkenler mevcutsa (örneğin GitLab CI, CircleCI veya Kubernetes Job ortamlarında), `-Dtestfly.shard.*` parametresi vermenize bile gerek yoktur — TestFly dağıtımı kendisi başlatır!

---

## Seçenek 2 — CLI ile Dinamik Test Seçimi (`testfly shard`)

Özel Maven Surefire komutları veya kabuk betikleri için TestFly CLI kullanarak dinamik `-Dtest` deseni üretebilirsiniz:

```bash
# Mevcut shard için virgülle ayrılmış test sınıflarını döndürür
mvn test -Dtest=$(testfly shard --total 4 --index $CI_NODE_INDEX)
```

### CLI Seçenekleri

```bash
testfly shard --help
```

| Bayrak | Açıklama | Varsayılan |
| :--- | :--- | :--- |
| `--total`, `-t` | Toplam paralel shard sayısı. | `2` |
| `--index`, `-i` | Mevcut worker node'unun 0 tabanlı indeksi. | `0` |
| `--metrics`, `-m` | `testfly-metrics.json` dosya yolu. | `target/testfly-metrics.json` |
| `--format`, `-f` | Çıktı formatı: `surefire`, `json`, `testng-xml`, `dashboard`. | `surefire` |
| `--output`, `-o` | Çıktının yazılacağı opsiyonel dosya yolu. | `None` |

---

## `testfly.yml` ile Yapılandırma

Varsayılan sharding davranışını `testfly.yml` içinde de tanımlayabilirsiniz:

```yaml title="testfly.yml"
execution:
  sharding:
    enabled: true
    total: 4
    index: 0
    strategy: lpt # "lpt" (önerilen) veya "round-robin"
    metricsFile: target/testfly-metrics.json
```

---

## Ağırlık Anotasyonları (`@TestWeight`)

Henüz geçmiş metriği bulunmayan yeni testler veya özellikle uzun süren uçtan uca senaryolar için tahmini süreyi `@TestWeight` ile belirtebilirsiniz:

```java
import io.testfly.sharding.TestWeight;
import io.testfly.test.BaseTest;
import org.testng.annotations.Test;

public class CheckoutE2ETest extends BaseTest {

    @TestWeight(seconds = 90)
    @Test(description = "3D Secure ödeme içeren uçtan uca sipariş tamamlama")
    public void testFullCheckoutFlow() {
        // ...
    }
}
```

---

## CI/CD Pipeline Örnekleri

### GitHub Actions Matrix

```yaml title=".github/workflows/test.yml"
name: Parallel Test Suite

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    strategy:
      fail-fast: false
      matrix:
        shard: [0, 1, 2, 3] # 4 paralel worker

    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
          cache: 'maven'

      # LPT optimizasyonu için önceki metrikleri önbellekten çek
      - name: Restore TestFly Metrics
        uses: actions/cache@v4
        with:
          path: target/testfly-metrics.json
          key: testfly-metrics-${{ github.ref_name }}
          restore-keys: |
            testfly-metrics-

      - name: Run Sharded Tests
        run: |
          mvn test -Dtestfly.shard.total=4 -Dtestfly.shard.index=${{ matrix.shard }}
```

### GitLab CI Parallel Matrix

```yaml title=".gitlab-ci.yml"
test:
  stage: test
  parallel: 4 # GitLab CI_NODE_TOTAL=4 ve CI_NODE_INDEX=1..4 değerlerini atar
  script:
    # TestFly otomatik olarak değişkenleri tanır ve ilgili shard'ı çalıştırır
    - mvn test
```

---

## Gerçek Zamanlı Konsol Raporu

Sharding devredeyken TestFly suite başlangıcında yük dağılım tablosunu konsola basar:

```text
================================================================================
✈  TestFly Smart Test Sharder — LPT Bin-Packing
================================================================================
Total Tests: 48 | Total Shards: 4 | Active Shard: 1 of 4 (Index: 0)
Suite Total Time: 22m 40s | Makespan (Bottleneck): 5m 45s | Efficiency: 98.6%
--------------------------------------------------------------------------------
Shard 0  [CURRENT NODE]:  12 tests ~ 5m 45s    (25.3% load)
Shard 1                :  12 tests ~ 5m 40s    (25.0% load)
Shard 2                :  12 tests ~ 5m 42s    (25.1% load)
Shard 3                :  12 tests ~ 5m 33s    (24.6% load)
================================================================================
```
