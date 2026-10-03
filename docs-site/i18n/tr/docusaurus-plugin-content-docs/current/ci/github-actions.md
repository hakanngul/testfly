---
description: "Selenium testlerini GitHub Actions'ta çalıştırın: Chrome'u kuran, takımı headless modda çalıştıran ve her push'ta HTML raporunu yükleyen kopyala-yapıştır bir workflow."
id: github-actions
title: GitHub Actions
sidebar_position: 1
---

# GitHub Actions

TestFly testlerinizi her push'ta ve pull request'te çalıştırın. Aşağıdaki workflow Chrome'u kurar, takımı çalıştırır ve HTML raporunu indirilebilir bir yapıt olarak yükler.

---

## Temel workflow

```yaml title=".github/workflows/test.yml"
name: Selenium Tests

on:
  push:
    branches: [main, master]
  pull_request:

permissions:
  contents: read

jobs:
  test:
    runs-on: ubuntu-latest
    timeout-minutes: 30

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven

      - name: Install Chrome
        uses: browser-actions/setup-chrome@v1

      - name: Run tests
        run: mvn test -B

      - name: Upload HTML report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: testfly-report
          path: target/testfly-report.html
```

`permissions: contents: read` workflow token'ını en az yetkiyle sınırlar; `timeout-minutes` ise takılan bir tarayıcı oturumunun varsayılan altı saat boyunca runner dakikası tüketmesini engeller.

---

## Headless Chrome

CI çalıştırıcılarında ekran yoktur, bu yüzden tarayıcılar headless çalışmalıdır. TestFly CI ortamını otomatik algılar (GitHub Actions `GITHUB_ACTIONS=true` ve `CI=true` değişkenlerini ayarlar) ve açılışta `browser.headless=true` değerini zorlar — logda `[TestFly] CI override: browser.headless=true` satırı görünür. Ek bir ortam değişkeni gerekmez.

İsterseniz `testfly.yml` içinde açıkça da belirtebilirsiniz:

```yaml title="testfly.yml"
browser:
  name: chrome
  headless: true
```

:::note Ortam değişkeniyle geçersiz kılma
TestFly, `SELENIUM_HEADLESS` veya `-Dbrowser.name` gibi serbest değişkenleri okumaz. Ortam değerleri yapılandırmaya yalnızca `testfly.yml` içindeki `${VAR}` / `${VAR:-default}` yer tutucularıyla ulaşır ve yer tutucular yalnızca **metin (string)** alanlarda çözülür (örneğin `browser.name`, `execution.baseUrl`). `headless` veya `threadCount` gibi boolean ve sayısal alanlar sabit değer olmalıdır — ortama göre farklılaşmaları gerekiyorsa bir profil dosyası kullanın (`-Dtestfly.profile=ci` → `testfly-ci.yml`).
:::

```yaml title="testfly.yml"
browser:
  name: ${TESTFLY_BROWSER:-chrome}
```

---

## JUnit XML test sonuçlarını yayınlama

Check run yayınlamak `checks: write` yetkisi gerektirir. Diğer job'lar salt okunur varsayılanı korusun diye bu yetkiyi job düzeyinde verin:

```yaml
jobs:
  test:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    permissions:
      contents: read
      checks: write

    steps:
      # ... checkout, setup-java, testleri çalıştır ...

      - name: Publish test results
        uses: dorny/test-reporter@v1
        if: always()
        with:
          name: Test Results
          path: '**/surefire-reports/TEST-*.xml'
          reporter: java-junit
          fail-on-empty: false
```

Bu, geçti/kaldı sayılarını doğrudan GitHub Actions özetinde ve PR denetimlerinde gösterir. Fork'lardan gelen pull request'lerde token, `permissions` ayarından bağımsız olarak salt okunurdur; bu nedenle yayınlama adımı orada check oluşturamaz.

---

## Matrix — birden çok tarayıcı

Yukarıdaki `${TESTFLY_BROWSER:-chrome}` yer tutucusunu kullanın; ortam değişkenleri YAML içinde referans verilmedikçe tarayıcı alanlarını geçersiz kılmaz.

```yaml
jobs:
  test:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    strategy:
      fail-fast: false
      matrix:
        browser: [chrome, firefox]

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven

      - name: Install Chrome
        if: matrix.browser == 'chrome'
        uses: browser-actions/setup-chrome@v1

      - name: Install Firefox
        if: matrix.browser == 'firefox'
        uses: browser-actions/setup-firefox@v1

      - name: Run tests
        run: mvn test -B
        env:
          TESTFLY_BROWSER: ${{ matrix.browser }}

      - name: Upload report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: report-${{ matrix.browser }}
          path: target/testfly-report.html
```

---

## Maven bağımlılıklarını önbellekleme

`setup-java` içindeki `cache: maven` seçeneği `~/.m2/repository` klasörünü otomatik olarak önbelleğe alır. Bu, sonraki çalıştırmalarda derleme süresini önemli ölçüde azaltır.

---

## Paralel testlerle tam örnek

```yaml title="testfly.yml"
execution:
  mode: local
  baseUrl: https://example.com
  parallel: methods
  threadCount: 4
```

Bu paralel ayarlarını `testfly.yml` içinde commit'leyin — CI çalıştırıcısı bunları otomatik olarak alır. `threadCount` varsayılan `1` değerinde bırakılırsa (ve `parallel` `none` değilse) TestFly, CI algılandığında değeri runner'ın CPU çekirdek sayısına göre ayarlar.