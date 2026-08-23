---
description: "İlk Selenium testini 5 dakikadan kısa sürede çalıştır: tek bağımlılık ekle, BaseTest extend et, WebDriver kurulumu veya tekrarlayan kod olmadan çalıştır."
id: getting-started
title: Hızlı Başlangıç
sidebar_position: 2
---

# Hızlı Başlangıç

İlk TestFly testini 5 dakikadan kısa sürede çalıştırın.

---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

## Ön Koşullar

- Java 17+
- Maven 3.8+ **veya** Gradle 7+
- Chrome veya Firefox yüklü

:::info
WebDriver binary'leri gerekmez — Selenium Manager browser driver'larını otomatik indirir.
:::

---

## Adım 1 — Bağımlılığı Ekle

<Tabs>
<TabItem value="maven" label="Maven (pom.xml)">

```xml title="pom.xml"
<dependency>
    <groupId>io.testfly</groupId>
    <artifactId>testfly</artifactId>
    <version>1.0.0</version>
</dependency>
```

</TabItem>
<TabItem value="gradle-groovy" label="Gradle Groovy (build.gradle)">

```groovy title="build.gradle"
dependencies {
    testImplementation 'io.testfly:testfly:1.0.0'
}

test {
    useTestNG()
    systemProperties System.properties
}
```

</TabItem>
<TabItem value="gradle-kotlin" label="Gradle Kotlin (build.gradle.kts)">

```kotlin title="build.gradle.kts"
dependencies {
    testImplementation("io.testfly:testfly:1.0.0")
}

tasks.test {
    useTestNG()
    systemProperties(System.getProperties().mapKeys { it.key.toString() })
}
```

</TabItem>
</Tabs>

:::tip Gradle kullanıyor musunuz?
Paralel yapılandırma, JUnit 5, isteğe bağlı bağımlılıklar ve rapor konumları için tam [Gradle Kurulum Rehberi](/docs/gradle)'ne bakın.
:::

---

## Adım 2 — Yapılandırma Dosyasını Oluştur

Proje köküne `testfly.yml` oluşturun (`pom.xml` veya `build.gradle` yanına):

```yaml title="testfly.yml"
browser:
  name: chrome
  headless: false

execution:
  baseUrl: https://your-app.com

retry:
  enabled: true
  maxAttempts: 2

timeouts:
  explicit: 10
  pageLoad: 30
```

---

## Adım 3 — İlk Testini Yaz

```java title="src/test/java/com/example/LoginTest.java"
import io.testfly.test.BaseTest;
import org.testng.Assert;
import org.testng.annotations.Test;

public class LoginTest extends BaseTest {

    @Test(description = "Geçerli kullanıcı giriş yapabilir")
    public void loginTest() {
        open();  // baseUrl'e gider
        // test adımların buraya
        Assert.assertTrue(getDriver().getTitle().contains("Dashboard"));
    }
}
```

---

## Adım 4 — TestNG Suite'i Oluştur

```xml title="testng.xml"
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE suite SYSTEM "https://testng.org/testng-1.0.dtd">
<suite name="testfly-suite" verbose="1">
    <test name="MyTests">
        <classes>
            <class name="com.example.LoginTest"/>
        </classes>
    </test>
</suite>
```

---

## Adım 5 — Çalıştır

<Tabs>
<TabItem value="maven" label="Maven">

```bash
mvn test
```

</TabItem>
<TabItem value="gradle" label="Gradle">

```bash
./gradlew test
```

</TabItem>
</Tabs>

---

## Neler Olur

1. Framework `testfly.yml`'i yükler
2. Chrome otomatik başlar
3. Testin çalışır
4. Herhangi bir failure'da ekran görüntüsü yakalanır
5. Browser kapanır
6. HTML rapor oluşturulur: `target/testfly-report.html` (Maven) veya `build/testfly-report/` (Gradle)
7. Metrics JSON: `target/testfly-metrics.json`

---

## Proje Yapısı

<Tabs>
<TabItem value="maven" label="Maven">

```
your-project/
├── pom.xml
├── testfly.yml
├── testng.xml
└── src/test/java/com/example/
    ├── pages/LoginPage.java
    └── tests/LoginTest.java
```

</TabItem>
<TabItem value="gradle" label="Gradle">

```
your-project/
├── build.gradle (veya build.gradle.kts)
├── testfly.yml
├── testng.xml
└── src/test/java/com/example/
    ├── pages/LoginPage.java
    └── tests/LoginTest.java
```

</TabItem>
</Tabs>

---

## Çalışan Örnek Proje

Tam çalışan proje şu adreste:
**https://github.com/hakanngul/testfly-test**

Klonlayın, `mvn test` (veya `./gradlew test`) çalıştırın; page object'leri, adım loglama ve retry yapılandırılmış tam çalışan bir suite'iniz olacak.

---

## Sonraki Adımlar

- [Yapılandırma Referansı](/docs/configuration) — tüm config seçenekleri
- [BasePage](/docs/guides/base-page) — temiz page object'ler yaz
- [Step Logging](/docs/guides/step-logging) — testlerine adlandırılmış adımlar ekle
