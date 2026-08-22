---
description: "Selenium testlerinizi sıfır kod değişikliğiyle BrowserStack ve Sauce Labs üzerinde çalıştırın. Tek bir yapılandırma satırını düzenleyerek yerel Chrome'dan bulut tarayıcılara geçin."
id: cloud-execution
title: Bulut Yürütme
sidebar_position: 12
---

# Bulut Yürütme (Cloud Execution)

TestFly, test paketinizi sıfır test-kodu değişikliğiyle **BrowserStack** ve **Sauce Labs** üzerinde çalıştırmayı destekler. `testfly.yml` içindeki bir satırı değiştirerek yerel Chrome'dan bulut tarayıcı çiftliğine geçin.

:::caution TestFly 3.2.1+ gerektirir
Bulut yürütme (`execution.mode: browserstack` veya `saucelabs`), **TestFly 3.2.1 veya sonrasını** gerektirir. Daha eski bir sürümde çalışmaz — bu sayfadaki örnekler ne gösterirse göstersin, yapılandırma yükleme aşamasında `execution.mode` için `local` ve `remote` dışındaki herhangi bir değeri reddeder.
:::

---

## Nasıl çalışır

Dört yürütme modunun tümü aynı sürücü yaşam döngüsünü paylaşır — testler tarayıcının nerede çalıştığından bağımsız olarak `getDriver()`, `open()`, `$()` ve `assertThat()` yöntemlerini aynı şekilde kullanır. Framework, `execution.mode` değerine göre doğru sağlayıcıyı seçer.

| Mod | Nerede çalışır |
|---|---|
| `local` | Sizin makineniz — Selenium Manager üzerinden Chrome veya Firefox |
| `remote` | Kendi Selenium Grid / Selenoid / Moon kurulumunuz |
| `browserstack` | BrowserStack Automate bulutu |
| `saucelabs` | Sauce Labs bulutu |

---

## BrowserStack

### Ön koşullar

1. [browserstack.com](https://www.browserstack.com) sitesinde kayıt olun
2. **Automate** → **Access Key** bölümüne gidin — kullanıcı adınızı ve erişim anahtarınızı kopyalayın

### Yapılandırma

```yaml title="testfly.yml"
execution:
  mode: browserstack
  browserstack:
    username:      ${BS_USER}          # ortam değişkeni olarak veya doğrudan ayarlayın
    accessKey:     ${BS_KEY}
    os:            Windows
    osVersion:     "11"
    browser:       chrome
    browserVersion: latest

browser:
  name: chrome   # her testfly.yml için zorunludur; yukarıdaki browserstack.browser değerinden ayrıdır

timeouts:
  explicit: 10
  pageLoad: 30
```

`browser` ve `timeouts`, her `testfly.yml` için zorunlu üst düzey bloklardır — bkz. [Yapılandırma Başvurusu](/docs/configuration).

Testleri çalıştırmadan önce ortam değişkenlerini ayarlayın:

```bash
export BS_USER=your_username
export BS_KEY=your_access_key
mvn test
```

### Masaüstü tarayıcılar

```yaml
execution:
  mode: browserstack
  browserstack:
    username:     ${BS_USER}
    accessKey:    ${BS_KEY}
    os:           Windows           # Windows | OS X
    osVersion:    "11"              # 11 | 10 | Sonoma | Ventura | …
    browser:      chrome            # chrome | firefox | edge | safari
    browserVersion: latest          # latest | 120.0 | 119.0 | …

browser:
  name: chrome

timeouts:
  explicit: 10
  pageLoad: 30
```

### Mobil cihazlar

```yaml
execution:
  mode: browserstack
  browserstack:
    username:     ${BS_USER}
    accessKey:    ${BS_KEY}
    browser:      chrome
    device:       "Samsung Galaxy S23"
    realMobile:   true

browser:
  name: chrome

timeouts:
  explicit: 10
  pageLoad: 30
```

### Ham özellik (capability) geçersiz kılmaları

`capabilities` altındaki herhangi bir anahtar `bstack:options` içine birleştirilir:

```yaml
execution:
  mode: browserstack
  browserstack:
    username:   ${BS_USER}
    accessKey:  ${BS_KEY}
    os:         Windows
    osVersion:  "11"
    browser:    chrome
    capabilities:
      debug: true
      networkLogs: true
      consoleLogs: verbose
      video: true

browser:
  name: chrome

timeouts:
  explicit: 10
  pageLoad: 30
```

### HTML raporunda oturum bağlantısı

Her testten sonra BrowserStack oturum URL'si otomatik olarak yakalanır. Test detay panelinde bir **☁ View Session** bağlantısı belirir — tıklayarak o test çalıştırması için video, ağ günlükleri ve konsol çıktısını içeren BrowserStack panosunu açarsınız.

---

## Sauce Labs

### Ön koşullar

1. [saucelabs.com](https://saucelabs.com) sitesinde kayıt olun
2. **Account** → **User Settings** bölümüne gidin — kullanıcı adınızı ve erişim anahtarınızı kopyalayın

### Yapılandırma

```yaml title="testfly.yml"
execution:
  mode: saucelabs
  saucelabs:
    username:      ${SAUCE_USER}
    accessKey:     ${SAUCE_KEY}
    region:        us-west-1          # us-west-1 | eu-central | apac-southeast
    platformName:  "Windows 11"
    browser:       chrome
    browserVersion: latest

browser:
  name: chrome

timeouts:
  explicit: 10
  pageLoad: 30
```

### Bölgeler

| Değer | Veri merkezi |
|---|---|
| `us-west-1` | ABD Batı (varsayılan) |
| `eu-central` | AB (Frankfurt) |
| `apac-southeast` | APAC (Güneydoğu Asya) |

### Ham özellik (capability) geçersiz kılmaları

`capabilities` altındaki anahtarlar `sauce:options` içine birleştirilir:

```yaml
execution:
  mode: saucelabs
  saucelabs:
    username:   ${SAUCE_USER}
    accessKey:  ${SAUCE_KEY}
    region:     eu-central
    platformName: "Windows 11"
    browser:    chrome
    browserVersion: latest
    capabilities:
      tags: ["regression", "nightly"]
      build: "v2.1.0"
      recordVideo: true

browser:
  name: chrome

timeouts:
  explicit: 10
  pageLoad: 30
```

### HTML raporunda oturum bağlantısı

BrowserStack ile aynı — her testin detay panelinde, video ve günlükleri içeren Sauce Labs test panosuna bağlanan bir **☁ View Session** bağlantısı belirir.

---

## Bulutta paralel yürütme

Paralel yapılandırma yerel ile aynı şekilde çalışır — `testfly.yml` içinde ayarlayın, bulut sağlayıcı buna göre ölçeklenir:

```yaml
execution:
  mode: browserstack
  parallel:    methods
  threadCount: 4        # 4 eşzamanlı BrowserStack oturumu
  browserstack:
    username:  ${BS_USER}
    accessKey: ${BS_KEY}
    os:        Windows
    osVersion: "11"
    browser:   chrome
    browserVersion: latest

browser:
  name: chrome

timeouts:
  explicit: 10
  pageLoad: 30
```

:::info Oturum limitleri
BrowserStack / Sauce Labs planınızın, `threadCount` içinde yapılandırdığınız eşzamanlı oturum sayısına izin verdiğinden emin olun. Framework'ün semafor tabanlı oturum koruması yine de uygulanır.
:::

---

## Ortamlar arasında geçiş yapma

Hiçbir YAML dosyasını değiştirmeden yürütme hedeflerini değiştirmek için Maven profillerini veya ortam değişkenlerini kullanın:

```bash
# Yerel
mvn test

# BrowserStack
BS_USER=user BS_KEY=key mvn test -Dtestfly.config=config/browserstack.yml

# Sauce Labs
SAUCE_USER=user SAUCE_KEY=key mvn test -Dtestfly.config=config/saucelabs.yml
```

Her ortam için ayrı YAML dosyaları tutun — her biri ortak ayarları içe aktarır ve yalnızca `execution.mode` ile bulut kimlik bilgilerini geçersiz kılar.

---

## Tam yapılandırma başvurusu

```yaml title="testfly.yml"
execution:
  mode: browserstack   # local | remote | browserstack | saucelabs

  # ── BrowserStack ───────────────────────────────────────────────
  browserstack:
    username:      ${BS_USER}
    accessKey:     ${BS_KEY}
    os:            Windows          # Windows | OS X
    osVersion:     "11"
    browser:       chrome           # chrome | firefox | edge | safari
    browserVersion: latest
    device:                         # isteğe bağlı — mobil cihaz adı
    realMobile:    true
    capabilities:                   # ham bstack:options geçersiz kılmaları
      debug: false
      networkLogs: false

  # ── Sauce Labs ─────────────────────────────────────────────────
  saucelabs:
    username:      ${SAUCE_USER}
    accessKey:     ${SAUCE_KEY}
    region:        us-west-1        # us-west-1 | eu-central | apac-southeast
    platformName:  "Windows 11"
    browser:       chrome
    browserVersion: latest
    capabilities:                   # ham sauce:options geçersiz kılmaları
      recordVideo: true

browser:
  name: chrome

timeouts:
  explicit: 10
  pageLoad: 30
```