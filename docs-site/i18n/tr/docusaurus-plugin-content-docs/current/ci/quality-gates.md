---
description: "Düşük başarı oranında veya çok sayıda tutarsız testte CI derlemenizi başarısıza düşürün. Selenium test kalite kapılarını yapılandırın; böylece yeşil bir pipeline gerçek hataları asla gizlemez."
id: quality-gates
title: Kalite Kapıları
sidebar_position: 3
---

# Kalite Kapıları

Test sonuçları kabul edilebilir bir eşiğin altına düştüğünde CI derlemenizi başarısıza düşürün. Bu, "yeşil bir pipeline"ın yaygın test başarısızlıklarını gizlemesini önler.

---

## Maven Surefire başarısızlık eşiği

Varsayılan olarak Maven, herhangi bir test başarısız olursa derlemeyi başarısıza düşürür. Belirli bir başarısızlık yüzdesine izin vermek için:

```xml title="pom.xml"
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.2.5</version>
    <configuration>
        <failIfNoTests>true</failIfNoTests>
    </configuration>
</plugin>
```

`failIfNoTests: true`, hiçbir test keşfedilemezse derlemenin başarısıza düşmesini sağlar — sağlık kontrolü olarak yararlıdır.

---

## Sıfır testte başarısıza düşme

Test keşfiniz veya takım yapılandırmanız bozulursa Maven, 0 test çalıştırıldığı halde memnuniyetle "BUILD SUCCESS" (derleme başarılı) raporlayacaktır. Bunu önleyin:

```xml
<configuration>
    <failIfNoTests>true</failIfNoTests>
</configuration>
```

---

## Özel başarı oranı kapısı (shell betiği)

JUnit XML çıktısını ayrıştırın ve başarı oranı bir eşiğin altına düşerse derlemeyi başarısıza düşürün:

```bash title=".github/workflows/test.yml (ek adım)"
- name: Quality gate — 90% pass rate required
  run: |
    TOTAL=$(grep -r 'tests=' target/surefire-reports/TEST-*.xml | \
            grep -oP 'tests="\K[0-9]+' | awk '{s+=$1} END {print s}')
    FAILED=$(grep -r 'failures=\|errors=' target/surefire-reports/TEST-*.xml | \
             grep -oP '(failures|errors)="\K[0-9]+' | awk '{s+=$1} END {print s}')
    PASSED=$((TOTAL - FAILED))
    RATE=$(echo "scale=1; $PASSED * 100 / $TOTAL" | bc)
    echo "Pass rate: $RATE% ($PASSED/$TOTAL)"
    if (( $(echo "$RATE < 90" | bc -l) )); then
      echo "FAILED: pass rate $RATE% is below 90% threshold"
      exit 1
    fi
```

---

## dorny/test-reporter kapısı

GitHub Actions'ta `dorny/test-reporter` kullanıldığında, herhangi bir test başarısız olursa adım workflow'u başarısıza düşürür:

```yaml
- name: Publish test results
  uses: dorny/test-reporter@v1
  if: always()
  with:
    name: Test Results
    path: '**/surefire-reports/TEST-*.xml'
    reporter: java-junit
    fail-on-error: true     # varsayılan true — test başarısızlıklarında workflow'u başarısıza düşürür
    fail-on-empty: false
```

---

## Yeniden deneme farkındalıklı kalite kapıları

TestFly, yeniden deneme sayılarını `target/testfly-metrics.json` içinde kaydeder. Bir derleme sonrası betik, çok fazla testin tutarsız olup olmadığını kontrol edebilir:

```json title="target/testfly-metrics.json (örnek)"
{
  "flakyTests": 3,
  "recoveredTests": 2,
  "total": 50,
  "passed": 48
}
```

```bash
- name: Quality gate — max 5% flaky tests
  run: |
    FLAKY=$(cat target/testfly-metrics.json | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('flakyTests', 0))")
    TOTAL=$(cat target/testfly-metrics.json | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('total', 1))")
    RATE=$(echo "scale=1; $FLAKY * 100 / $TOTAL" | bc)
    echo "Flaky rate: $RATE% ($FLAKY/$TOTAL)"
    if (( $(echo "$RATE > 5" | bc -l) )); then
      echo "FAILED: flaky rate $RATE% exceeds 5% threshold"
      exit 1
    fi
```