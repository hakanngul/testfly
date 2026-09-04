---
description: "Tamamlanmış testfly.yml yapılandırma referansı: browser, paralel thread'ler, timeout'lar, retry ve CI quality gate'leri, tüm seçenekler tek yerde."
id: configuration
title: Yapılandırma Referansı
sidebar_position: 3
---

# Yapılandırma Referansı

Tüm framework davranışı `testfly.yml` ile kontrol edilir.

---

## Dosya Çözümleme Sırası

Framework config dosyasını şu öncelik sırasına göre arar:

1. **System property** — `-Dtestfly.config=/path/to/custom.yml`
2. **Çalışma dizini** — `./testfly.yml` (`pom.xml` yanında)
3. **Classpath** — `src/test/resources/testfly.yml`

---

## Tam Referans

```yaml
# ── Browser ────────────────────────────────────────────────────────────────
browser:
  name: chrome              # chrome | firefox | edge | safari
  headless: false           # CI'da true (CI algılandığında otomatik)
  lifecycle: per-test       # per-test (varsayılan) | per-suite

  # Opsiyonel: ek browser argümanları
  arguments:
    - --start-maximized
    - --disable-notifications

  # Opsiyonel: ham capability override'ları
  capabilities:
    acceptInsecureCerts: true

# ── Execution ───────────────────────────────────────────────────────────────
execution:
  mode: local               # local | remote | browserstack | saucelabs
  baseUrl: https://your-app.com
  gridUrl: http://localhost:4444   # sadece remote mode için

  parallel: none            # none | methods | classes | tests | instances
  threadCount: 1            # parallel: none ise yok sayılır
  maxActiveSessions: 5      # maksimum eşzamanlı browser instance (semaphore)

  # ── BrowserStack (mode: browserstack) ──────────────────────────────────────
  browserstack:
    username:      ${BS_USER}
    accessKey:     ${BS_KEY}
    os:            Windows          # Windows | OS X
    osVersion:     "11"
    browser:       chrome           # chrome | firefox | edge | safari
    browserVersion: latest
    device:                         # opsiyonel — mobil cihaz adı
    realMobile:    true
    capabilities:                   # ham bstack:options override'ları
      debug: false

  # ── Sauce Labs (mode: saucelabs) ───────────────────────────────────────────
  saucelabs:
    username:      ${SAUCE_USER}
    accessKey:     ${SAUCE_KEY}
    region:        us-west-1        # us-west-1 | eu-central | apac-southeast
    platformName:  "Windows 11"
    browser:       chrome
    browserVersion: latest
    capabilities:                   # ham sauce:options override'ları
      recordVideo: true

# ── Locators ─────────────────────────────────────────────────────────────────
locators:
  selfHealing: false        # waitForVisible / waitForClickable içinde fail eden locator'ları otomatik fallback ile yeniden dener
  testIdAttribute: data-testid  # getByTestId() tarafından çözülen attribute

# ── Retry ───────────────────────────────────────────────────────────────────
retry:
  enabled: true             # tüm testlere global olarak uygulanır
  maxAttempts: 2            # toplam deneme sayısı (1 = retry yok)

# ── Timeouts ────────────────────────────────────────────────────────────────
timeouts:
  explicit: 10              # saniye — WaitEngine varsayılan timeout
  pageLoad: 30              # saniye — browser page load timeout

# ── CI / Build Quality Gates ────────────────────────────────────────────────
ci:
  failOnPassRateBelow: 80   # 0 = devre dışı. Pass rate < 80% ise build fail
  maxFlakyTests: 3          # -1 = devre dışı. 3'ten fazla test retry edilirse fail
  captureMetadata: true     # CI'da otomatik; false ile devre dışı bırakılır. Sağlayıcı,
                            # branch, commit, build URL'sini raporlara ve metrics JSON'a yazar

# ── Database Assertions ─────────────────────────────────────────────────────
database:
  url:      jdbc:postgresql://localhost/mydb
  username: ${DB_USER}
  password: ${DB_PASS}
  driver:   org.postgresql.Driver  # opsiyonel; çoğu driver URL'den otomatik tanır

  # İsimlendirilmiş datasource'lar (db("reporting") ile erişim)
  datasources:
    reporting:
      url:      jdbc:postgresql://localhost/reporting
      username: ${REPORTING_DB_USER}
      password: ${REPORTING_DB_PASS}

# ── Multi-Session Testing ───────────────────────────────────────────────────
sessions:
  maxPerTest: 2   # test başına maksimum isimlendirilmiş oturum (kaynak sızıntısına karşı koruma)

# ── Email Verification ──────────────────────────────────────────────────────
email:
  provider: mailhog          # mailhog | mailtrap | outlook | imap
  timeoutSeconds: 30         # waitForEmail() için varsayılan bekleme
  pollIntervalMs: 1000       # polling aralığı
  autoClear: false           # her test öncesinde inbox'ı otomatik temizle

  mailhog:
    host: localhost
    port: 8025

  mailtrap:
    apiToken:  ${MAILTRAP_TOKEN}
    accountId: ${MAILTRAP_ACCOUNT_ID}
    inboxId:   ${MAILTRAP_INBOX_ID}

  outlook:
    tenantId:     ${AZURE_TENANT_ID}
    clientId:     ${AZURE_CLIENT_ID}
    clientSecret: ${AZURE_CLIENT_SECRET}
    mailbox:      test-inbox@yourcompany.com

  imap:
    host:     imap.gmail.com
    port:     993
    ssl:      true
    username: ${EMAIL_USER}
    password: ${EMAIL_PASS}
    folder:   INBOX

# ── Reporting ───────────────────────────────────────────────────────────────
reporting:
  allure:
    enabled: false        # Allure 2 sonuçlarını target/allure-results/'a yazar

  reportportal:
    enabled: false
    endpoint: http://localhost:8080
    apiKey: ${RP_API_KEY}
    project: superadmin_personal
    launch: "TestFly Launch"
    description: "Automated TestFly test execution"
    attributes: "env:ci;branch:main"

# ── Clock Mocking ────────────────────────────────────────────────────────────
clock:
  injectHeader: false      # sunucuya X-Mock-Date header'ı gönder
  headerName: X-Mock-Date

# ── AI Failure Analysis ─────────────────────────────────────────────────────
ai:
  failureAnalysis: true        # test fail olduğunda AI ile kök neden analizi üret
  provider: gemini             # gemini | claude | openai-compatible
  apiKey: ${GEMINI_API_KEY}
  model: gemini-2.0-flash      # gemini-2.0-flash | claude-haiku-4-5-20251001 | deepseek-chat
  language: tr                 # analiz dil çıktısı (tr, en, de vb.)
  timeoutSeconds: 20
```

---

## Browser

### `name`
Kullanılacak browser. Selenium Manager eşleşen driver'ı otomatik indirir.

| Değer | Browser |
|---|---|
| `chrome` | Google Chrome (varsayılan) |
| `firefox` | Mozilla Firefox |
| `edge` | Microsoft Edge |
| `safari` | Safari (sadece macOS) |

### `headless`
Browser'ı görünür pencere olmadan çalıştırır. CI ortamı algılandığında (GitHub Actions, Jenkins, vb.) otomatik olarak `true` yapılır.

### `lifecycle`
WebDriver oturumunun ne zaman kapatılacağını kontrol eder.

| Değer | Davranış |
|---|---|
| `per-test` | Her test metodu için browser açılır ve kapatılır (varsayılan — tam izolasyon) |
| `per-suite` | Browser tüm suite boyunca açık kalır; thread başına bir instance, suite sonunda kapatılır |

:::tip `per-suite` ne zaman kullanılır
Suite'inizde ardışık çok test varsa ve browser başlangıç süresi darboğazsa kullanın. Browser testler arası cookie ve state tutar — test akışını buna göre planlayın.
:::

---

## Execution

### `parallel`
Doğrudan TestNG paralel çalışma moduna eşlenir. Thread sayısı `threadCount` ile ayarlanır. TestFly bu değeri suite bootstrap sırasında TestNG'nin kendi mod setine karşı doğrular; tanınmayan bir değer hem reddedilen değeri hem de kabul edilenleri belirten bir mesajla anında fail eder.

| Değer | Davranış |
|---|---|
| `none` | Sıralı çalışma (varsayılan) |
| `methods` | Her `@Test` metodu kendi thread'inde çalışır |
| `classes` | Her test class'ı kendi thread'inde çalışır |
| `tests` | Suite XML'deki her `<test>` kendi thread'inde çalışır |
| `instances` | Her test class instance'ı kendi thread'inde çalışır |

### `maxActiveSessions`
Maksimum eşzamanlı browser instance sayısı. Test'ler slot bulana kadar (30s'ye kadar) bekler, hemen fail olmaz. Paralel çalışmalarda kaynak tükenmesini önler.

---

## Retry

### `enabled`
`true` olduğunda, tüm test metotları failure durumunda `maxAttempts` kez retry edilir. Global olarak devre dışı bırakmak için `false` yapın.

Belirli bir test için global ayarı override etmek üzere metot üzerine `@Retryable` ekleyin.

```java
@Test
@Retryable(maxAttempts = 3)
public void flakyTest() { ... }
```

---

## Locators

### `selfHealing`
`true` olduğunda, `waitForVisible` / `waitForClickable` içinde fail eden bir locator, orijinal `By` tanımlayıcısından türetilen fallback stratejileriyle otomatik olarak yeniden denenir (`id`, `name`, text, class, `data-testid` veya `placeholder` çıkarımı). Iyileştirilen test'ler HTML raporda `⚠ healed` rozeti alır ve her iyileştirme `target/healed-locators.json`'a yazılır. Varsayılan kapalı. Detaylar için [Self-Healing Locators](./guides/self-healing) sayfasına bakın.

### `testIdAttribute`
`getByTestId()` tarafından çözülen HTML attribute'üdür. Varsayılan `data-testid`; uygulamanıza uyması için `data-qa`, `data-test`, vb. olarak ayarlayın. Detaylar için [Semantic Locators](./guides/semantic-locators) sayfasına bakın.

---

## Ortam Profilleri

Belirli bir ortam için varsayılan config'i profile suffix'i kullanarak override edin:

```
testfly.yml            ← temel config
testfly-staging.yml    ← staging override'ları
testfly-prod.yml       ← prod override'ları
```

Aktifleştirmek için:

```bash
mvn test -Dtestfly.profile=staging
```

Profile dosyasında bulunan alanlar override edilir — gerisi temel config'e fallback yapar.
