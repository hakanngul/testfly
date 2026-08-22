---
description: "TestFly'yi Gradle ile kullanın: Maven Central JAR'ı ile TestNG veya JUnit 5 Selenium testlerini çalıştırmak için önerilen build.gradle kurulumu."
id: gradle
title: Gradle Derleme Desteği
sidebar_position: 3
---

# Gradle Derleme Desteği

TestFly, kutudan çıktığı gibi Gradle ile çalışır — Maven Central'daki JAR, derleme aracından bağımsızdır. Bu sayfa, hem Groovy DSL (`build.gradle`) hem Kotlin DSL (`build.gradle.kts`) için önerilen kurulumu kapsar.

---

## Adım 1 — Bağımlılığı ekleyin

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

<Tabs>
<TabItem value="groovy" label="Groovy DSL (build.gradle)">

```groovy title="build.gradle"
plugins {
    id 'java'
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation 'io.testfly:testfly:2.6.0'
}
```

</TabItem>
<TabItem value="kotlin" label="Kotlin DSL (build.gradle.kts)">

```kotlin title="build.gradle.kts"
plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("io.testfly:testfly:2.6.0")
}
```

</TabItem>
</Tabs>

---

## Adım 2 — Test yürütmeyi yapılandırın

### TestNG (varsayılan koşturucu)

<Tabs>
<TabItem value="groovy" label="Groovy DSL">

```groovy title="build.gradle"
test {
    useTestNG {
        // İsteğe bağlı: bir testng.xml paket dosyasını işaret edin
        // suites 'src/test/resources/testng.xml'
    }

    // Sistem özelliklerini iletin, böylece -Denv=staging CLI'dan çalışır
    systemProperties System.properties

    // Test çıktısını konsolda göster
    testLogging {
        events 'passed', 'skipped', 'failed'
        showStandardStreams = false
    }
}
```

</TabItem>
<TabItem value="kotlin" label="Kotlin DSL">

```kotlin title="build.gradle.kts"
tasks.test {
    useTestNG {
        // İsteğe bağlı: bir testng.xml paket dosyasını işaret edin
        // suites("src/test/resources/testng.xml")
    }

    // Sistem özelliklerini iletin, böylece -Denv=staging CLI'dan çalışır
    systemProperties(System.getProperties().mapKeys { it.key.toString() })

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}
```

</TabItem>
</Tabs>

### JUnit 5 köprüsü

`BaseJUnit5Test` veya `@EnableTestFly` kullanıyorsanız:

<Tabs>
<TabItem value="groovy" label="Groovy DSL">

```groovy title="build.gradle"
dependencies {
    testImplementation 'io.testfly:testfly:2.6.0'
    testImplementation 'org.junit.jupiter:junit-jupiter-api:5.10.2'
    testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.10.2'
}

test {
    useJUnitPlatform()
    systemProperties System.properties
}
```

</TabItem>
<TabItem value="kotlin" label="Kotlin DSL">

```kotlin title="build.gradle.kts"
dependencies {
    testImplementation("io.testfly:testfly:2.6.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
}

tasks.test {
    useJUnitPlatform()
    systemProperties(System.getProperties().mapKeys { it.key.toString() })
}
```

</TabItem>
</Tabs>

---

## Adım 3 — Yapılandırma dosyası

**Proje kökünde** (`build.gradle` ile aynı düzeyde) `testfly.yml` oluşturun:

```yaml title="testfly.yml"
execution:
  mode: local
  baseUrl: https://example.com

browser:
  name: chrome
  headless: true

retry:
  enabled: true
  maxAttempts: 2
```

---

## Testleri çalıştırma

```bash
# Tüm testleri çalıştır
./gradlew test

# Tek bir test sınıfını çalıştır
./gradlew test --tests "com.example.LoginTest"

# Tek bir test metodunu çalıştır
./gradlew test --tests "com.example.LoginTest.validLogin"

# Bir ortam profiliyle çalıştır
./gradlew test -Denv=staging

# Birden çok JVM argümanı ilet
./gradlew test -Dbrowser.name=firefox -Dbrowser.headless=true
```

---

## Test raporu konumları

| Rapor türü | Gradle yolu |
|---|---|
| HTML raporu (TestFly) | `build/testfly-report/index.html` |
| JUnit XML (TestFly) | `build/test-results/test/TEST-TestFly.xml` |
| Gradle'ın kendi HTML raporu | `build/reports/tests/test/index.html` |
| Allure sonuçları (etkinse) | `build/allure-results/` |

:::info JUnit XML otomatik algılama
TestFly, bir `build/` dizininin var olup olmadığını ve `target/` dizininin var olmadığını kontrol ederek Gradle'ı otomatik algılar, ardından XML'i `build/test-results/test/` dizinine yazar. Gerekirse `-Dtestfly.reports.dir=path/to/dir` ile geçersiz kılın.
:::

---

## Paralel yürütme

Gradle ile paralel çalıştırmalar için, `testfly.yml` ile birlikte `test` görevini yapılandırın:

<Tabs>
<TabItem value="groovy" label="Groovy DSL">

```groovy title="build.gradle"
test {
    useTestNG()
    maxParallelForks = 4          // Gradle worker süreçleri
    systemProperties System.properties
}
```

</TabItem>
<TabItem value="kotlin" label="Kotlin DSL">

```kotlin title="build.gradle.kts"
tasks.test {
    useTestNG()
    maxParallelForks = 4
    systemProperties(System.getProperties().mapKeys { it.key.toString() })
}
```

</TabItem>
</Tabs>

```yaml title="testfly.yml"
execution:
  parallel: methods
  threadCount: 4
```

---

## İsteğe bağlı bağımlılıklar

Bunlar TestFly JAR'ında `compileOnly` / isteğe bağlıdır — yalnızca ilgili özelliği kullanıyorsanız ekleyin:

| Özellik | Bağımlılık |
|---|---|
| Excel `@TestData` | `testImplementation 'org.apache.poi:poi-ooxml:5.2.5'` |
| E-posta doğrulama (IMAP) | `testImplementation 'com.sun.mail:jakarta.mail:2.0.1'` |
| Cucumber | `testImplementation 'io.cucumber:cucumber-java:7.15.0'` + `testImplementation 'io.cucumber:cucumber-junit-platform-engine:7.15.0'` |

---

## Tam örnek proje

Minimal çalışan bir Gradle projesi (Groovy DSL, TestNG):

```
my-tests/
├── build.gradle
├── testfly.yml
└── src/
    └── test/
        └── java/
            └── com/example/
                └── LoginTest.java
```

```groovy title="build.gradle"
plugins {
    id 'java'
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation 'io.testfly:testfly:2.6.0'
}

test {
    useTestNG()
    systemProperties System.properties
    testLogging { events 'passed', 'skipped', 'failed' }
}
```

```java title="src/test/java/com/example/LoginTest.java"
import io.testfly.test.BaseTest;
import org.openqa.selenium.By;
import org.testng.annotations.Test;

public class LoginTest extends BaseTest {

    @Test
    public void validLogin() {
        open("/login");
        find("input#username").type("admin");
        find("input#password").type("secret");
        find("button[type='submit']").click();
        assertThat(By.id("dashboard")).isVisible();
    }
}
```

```bash
./gradlew test
```