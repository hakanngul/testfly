---
description: "TestFly, Jenkins, GitHub Actions ve GitLab CI'nin standart test sonucu raporlaması için yerel olarak ayrıştırdığı JUnit uyumlu XML üretir."
id: junit-xml
title: Selenium JUnit XML Raporu
sidebar_label: JUnit XML
sidebar_position: 2
---

# JUnit XML

TestFly, `target/surefire-reports/TEST-TestFly.xml` konumunda JUnit uyumlu bir XML raporu üretir. Bu biçim, neredeyse tüm CI sistemleri ve test raporlama araçları tarafından anlaşılır.

---

## Rapor konumu

```
target/
└── surefire-reports/
    └── TEST-TestFly.xml
```

---

## Biçim

```xml
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="TestFly" tests="12" failures="1" errors="0" skipped="1" time="34.21">

    <testcase classname="LoginTest" name="validLogin" time="2.341"/>

    <testcase classname="CheckoutTest" name="checkout_withInvalidCard" time="1.823">
        <failure message="Expected [Order confirmed] but found [Payment declined]"
                 type="org.openqa.selenium.NoSuchElementException">
            org.openqa.selenium.NoSuchElementException: no such element
                at com.example.tests.CheckoutTest.checkout_withInvalidCard(CheckoutTest.java:42)
                ...
        </failure>
    </testcase>

    <testcase classname="ProfileTest" name="updateAvatar" time="0.0">
        <skipped/>
    </testcase>

</testsuite>
```

---

## Hata mesajları

`<failure>` üzerindeki `message` özniteliği, genel bir "Test başarısız oldu" yer tutucusu değil; testinizden gelen gerçek düşüm (assertion) mesajını veya istisna mesajını içerir.

Bu, başarısızlık özetlerini görüntüleyen CI araçlarının (GitHub Actions, Jenkins, Azure Pipelines) tam raporu açmaya gerek kalmadan anlamlı hata açıklamaları göstermesini sağlar.

---

## CI entegrasyonu

### GitHub Actions — dorny/test-reporter

```yaml
- name: Test sonuçlarını yayınla
  uses: dorny/test-reporter@v1
  if: always()
  with:
    name: TestFly Sonuçları
    path: '**/surefire-reports/TEST-*.xml'
    reporter: java-junit
    fail-on-empty: false
```

### Jenkins

```groovy
post {
    always {
        junit '**/surefire-reports/TEST-*.xml'
    }
}
```

### Azure Pipelines

```yaml
- task: PublishTestResults@2
  condition: always()
  inputs:
    testResultsFormat: JUnit
    testResultsFiles: '**/surefire-reports/TEST-*.xml'
    testRunTitle: 'TestFly'
```

---

## Maven Surefire XML

Maven Surefire de `target/surefire-reports/` içinde sınıf başına kendi XML dosyalarını üretir. Her iki XML dosyası kümesi de geçerli bir JUnit biçimidir ve birlikte yayınlanabilir.

Tüm XML dosyalarını yayınlamak için:

```yaml
path: 'target/surefire-reports/TEST-*.xml'
```