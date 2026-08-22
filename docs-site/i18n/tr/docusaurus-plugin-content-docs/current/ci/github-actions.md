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

jobs:
  test:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
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

---

## Headless Chrome

CI çalıştırıcılarındaki Chrome, headless modda çalışmalıdır. Bunu `testfly.yml` içinde yapılandırın:

```yaml title="testfly.yml"
browser:
  type: chrome
  headless: true
```

Ya da yalnızca CI'de bir ortam değişkeni geçersiz kılması kullanarak ayarlayın (yapılandırma yüklemeniz destekliyorsa):

```yaml
      - name: Run tests
        run: mvn test -B
        env:
          SELENIUM_HEADLESS: true
```

---

## JUnit XML test sonuçlarını yayınlama

```yaml
      - name: Publish test results
        uses: dorny/test-reporter@v1
        if: always()
        with:
          name: Test Results
          path: '**/surefire-reports/TEST-*.xml'
          reporter: java-junit
          fail-on-empty: false
```

Bu, geçti/kaldı sayılarını doğrudan GitHub Actions özetinde ve PR denetimlerinde gösterir.

---

## Matrix — birden çok tarayıcı

```yaml
jobs:
  test:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        browser: [chrome, firefox]

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: maven

      - name: Install Chrome
        if: matrix.browser == 'chrome'
        uses: browser-actions/setup-chrome@v1

      - name: Install Firefox
        if: matrix.browser == 'firefox'
        uses: browser-actions/setup-firefox@v1

      - name: Run tests
        run: mvn test -B -Dbrowser.type=${{ matrix.browser }}

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

```yaml
      - name: Run tests
        run: mvn test -B -Dparallel=methods -DthreadCount=4
```

Ya da paralel ayarlarını `testfly.yml` içinde tanımlayıp commit'leyin — CI çalıştırıcısı bunları otomatik olarak alır.