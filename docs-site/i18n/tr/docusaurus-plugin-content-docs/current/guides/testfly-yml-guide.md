---
description: "TestFly testlerini yapılandırmak için kullanılan testfly.yml dosyasına dair pratik bir rehber; src/test/resources/testfly.yml içindeki örnek yapılandırmaya dayanır."
id: testfly-yml-guide
title: testfly.yml Rehberi
sidebar_position: 2
---

# `testfly.yml` Rehberi

`testfly.yml`, TestFly'in testlerinizi nasıl çalıştırdığını kontrol eden tek yapılandırma dosyasıdır. `src/test/resources/testfly.yml` altındaki kopya, framework'ün kendi örnek yapılandırmasıdır ve bu depodaki örnek testler çalıştırılırken de kullanılır.

---

## Dosyanın konumu

TestFly, yapılandırmayı şu sırayla çözümler:

1. **Sistem özelliği** — `-Dtestfly.config=/path/to/custom.yml`
2. **Çalışma dizini** — `./testfly.yml` (`pom.xml` veya `build.gradle` yanında)
3. **Classpath** — `src/test/resources/testfly.yml`

Bir tüketici (consumer) projesi için `testfly.yml` dosyasını proje köküne koyun. TestFly framework'ünün içinde ise örnek yapılandırma, test classpath'inde bulunması için `src/test/resources/testfly.yml` içinde yer alır.

---

## Minimum gerekli yapılandırma

Bir testi başlatacak en küçük dosya şudur:

```yaml
execution:
  mode: local
  baseUrl: https://example.com

browser:
  name: chrome

timeouts:
  explicit: 10
  pageLoad: 30
```

Diğer her şey isteğe bağlıdır.

---

## Açıklamalı örnek (`src/test/resources/testfly.yml`)

```yaml
browser:
  name: chrome
  headless: true
  arguments:
    - --start-maximized
    - --disable-notifications
    - --remote-allow-origins=*
  capabilities:
    acceptInsecureCerts: true
    pageLoadStrategy: eager
```

| Anahtar | Ne yapar |
|-----|--------------|
| `name` | Başlatılacak tarayıcı: `chrome`, `firefox`, `edge` veya `safari`. |
| `headless` | Tarayıcıyı görünür bir pencere olmadan çalıştırır. TestFly bir CI ortamı algıladığında otomatik olarak `true` yapılır. |
| `arguments` | Tarayıcı yürütülebilir dosyasına iletilen ek komut satırı bayrakları. |
| `capabilities` | Ham Selenium capability geçersiz kılmaları, örn. kendinden imzalı sertifikalar için `acceptInsecureCerts`. |

```yaml
execution:
  mode: local
  baseUrl: https://www.saucedemo.com/
  gridUrl: http://localhost:4444/wd/hub
  parallel: methods
  threadCount: 4
  maxActiveSessions: 4
```

| Anahtar | Ne yapar |
|-----|--------------|
| `mode` | `local`, `remote`, `browserstack` veya `saucelabs`. |
| `baseUrl` | `open()` ve `BaseCucumberSteps.open()` tarafından kullanılan varsayılan URL. |
| `gridUrl` | `mode: remote` olduğunda kullanılan Selenium Grid / standalone sunucu URL'si. |
| `parallel` | TestNG paralel modu: `none`, `methods`, `classes`, `tests` veya `instances`. |
| `threadCount` | Paralel çalıştırma etkinleştirildiğinde kullanılacak thread sayısı. |
| `maxActiveSessions` | Maksimum eşzamanlı tarayıcı örneği. Fazladan testler başarısız olmak yerine boş bir slot bekler. |

```yaml
api:
  baseUrl: https://fakeapi.net
  timeoutSeconds: 30
  logBody: false
```

| Anahtar | Ne yapar |
|-----|--------------|
| `baseUrl` | `ApiClient` istekleri için varsayılan taban URL. |
| `timeoutSeconds` | Saniye cinsinden istek zaman aşımı. |
| `logBody` | `true` olduğunda, yanıt gövdeleri adım günlüğüne yazılır. |

```yaml
retry:
  enabled: true
  maxAttempts: 2
```

| Anahtar | Ne yapar |
|-----|--------------|
| `enabled` | Global retry anahtarı. |
| `maxAttempts` | Test başına toplam deneme sayısı. `1`, retry yapılmadığı anlamına gelir. `@Retryable(maxAttempts = 3)` ile test bazında geçersiz kılın. |

```yaml
timeouts:
  explicit: 10
  pageLoad: 30
```

| Anahtar | Ne yapar |
|-----|--------------|
| `explicit` | `WaitEngine`, `Locator` ve `BasePage` yardımcı metodlarının kullandığı varsayılan bekleme süresi. |
| `pageLoad` | Saniye cinsinden tarayıcı sayfa yükleme zaman aşımı. |

---

## Ortam profilleri

Tek bir temel dosya tutun ve ortam başına geçersiz kılmalar oluşturun:

```text
testfly.yml            # base config
testfly-staging.yml    # staging overrides
testfly-ci.yml         # CI overrides
```

Profil dosyasında bulunan alanlar yalnızca değiştirilir; diğer her şey temel yapılandırmadan miras alınır.

Bir profili şu şekilde etkinleştirin:

```bash
mvn test -Denv=staging
```

Örnek `testfly-ci.yml`:

```yaml
browser:
  headless: true
  arguments:
    - --no-sandbox
    - --disable-dev-shm-usage

execution:
  parallel: methods
  threadCount: 8
  maxActiveSessions: 8
```

---

## Yaygın kalıplar

### Yerel bir Selenium Grid'e karşı çalıştırma

```yaml
execution:
  mode: remote
  baseUrl: https://www.saucedemo.com/
  gridUrl: http://localhost:4444/wd/hub
```

### BrowserStack'e karşı çalıştırma

```yaml
execution:
  mode: browserstack
  baseUrl: https://www.saucedemo.com/

browserstack:
  username: ${BS_USER}
  accessKey: ${BS_KEY}
  os: Windows
  osVersion: "11"
  browser: chrome
  browserVersion: latest
```

### Geliştirme sırasında hızlı geri bildirim için retry'ı devre dışı bırakma

```yaml
retry:
  enabled: false
```

### Yavaş ortamlar için süreleri artırma

```yaml
timeouts:
  explicit: 20
  pageLoad: 60
```

---

## Doğrulama

TestFly, yapılandırmayı suite başlangıcında doğrular. Eksik zorunlu alanlar veya geçersiz değerler (örn. bilinmeyen bir `parallel` modu), net bir mesajla hemen başarısız olur. Bozuk bir yapılandırmayla `mvn test` çalıştırmak, hiçbir tarayıcı açılmadan önce sorunu yazdırır; böylece hatalı yapılandırılmış bir çalıştırma için zaman harcamazsınız.

---

## Ayrıca bakınız

- [Yapılandırma Referansı](../configuration) — eksiksiz seçenek listesi
- [Tarayıcı Yaşam Döngüsü](../guides/browser-lifecycle)
- [Paralel Çalıştırma](../guides/parallel)
- [API Testleri](../guides/api-testing)