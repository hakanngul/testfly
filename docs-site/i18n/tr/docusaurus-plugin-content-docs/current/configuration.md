---
description: "Kapsamlı testfly.yml yapılandırma referansı: tarayıcı, çalıştırma, paralel thread'ler, zaman aşımları, yeniden deneme, AI analizi, API testi, veritabanı, raporlama ve CI kalite kapıları."
id: configuration
title: Yapılandırma Referansı
sidebar_position: 3
---

# Yapılandırma Referansı

TestFly framework'ünün tüm çalışma davranışı `testfly.yml` dosyası ile yönetilir. Bu belge; framework tarafından desteklenen her bir üst düzey bölümün, iç içe geçmiş yapılandırma özelliklerinin, varsayılan değerlerin, ortam değişkeni çözünürlüğünün ve profil geçersiz kılmalarının kapsamlı referansıdır.

---

## Dosya Çözümleme Öncelik Sırası

TestFly test paketi başlatılırken yapılandırma dosyasını şu öncelik sırasına göre arar:

1. **Sistem Özelliği (System Property)** — `-Dtestfly.config=/path/to/custom.yml` (en yüksek öncelik)
2. **Çalışma Dizini (Working Directory)** — `./testfly.yml` (`pom.xml` veya `build.gradle` dosyanızın bulunduğu proje kök dizini)
3. **Classpath Kaynağı** — `src/test/resources/testfly.yml` (yedek konum)

Bu konumlardan hiçbirinde geçerli bir yapılandırma dosyası bulunamazsa, test paketi başlatması açıklayıcı bir `IllegalStateException` ile derhal durdurulur.

---

## Ortam Değişkenleri ve Dinamik Değer Atama

### Yer Tutucu Sözdizimi (`${VAR_NAME}`)

`testfly.yml` içindeki tüm skaler değerler ortam değişkenlerine veya Java sistem özelliklerine başvurabilir:

```yaml
execution:
  baseUrl: ${BASE_URL}

browserstack:
  username: ${BS_USER}
  accessKey: ${BS_KEY}

api:
  auth:
    admin:
      type: bearer
      token: ${API_TOKEN}
```

* Belirtilen ortam değişkeni mevcutsa, çalışma zamanında `${VAR_NAME}` yerine değeri yerleştirilir.
* Değişken tanımlı değilse, TestFly Java sistem özelliklerini kontrol eder (`System.getProperty("VAR_NAME")`).
* İkisi de yoksa `${VAR_NAME}` değişmeden kalır veya bağlama göre boş string olarak değerlendirilir.

### Ortam Profilleri (`-Dtestfly.profile`)

Farklı ortamlar için `testfly-<profil>.yml` adlandırmasıyla geçersiz kılma dosyaları oluşturabilirsiniz:

```text
testfly.yml            # Temel yapılandırma (ortak varsayılanlar)
testfly-staging.yml    # Staging ortamına özel ayarlar
testfly-prod.yml       # Canlı (Production) ortama özel ayarlar
testfly-ci.yml         # CI/CD hattına özel ayarlar
```

Bir profili Maven veya Gradle ile etkinleştirebilirsiniz:

```bash
mvn test -Dtestfly.profile=staging
```

:::tip Derin Birleştirme (Deep Merge)
Profil dosyalarında **yalnızca değiştirmek istediğiniz alanları tanımlamanız yeterlidir**. TestFly, profil dosyasını ana `testfly.yml` dosyasının üzerine birleştirir; belirtilmeyen tüm temel ayarlar korunur.
:::

---

## Ana `testfly.yml` Şablonu

Aşağıdaki açıklamalı şablon, framework'ün desteklediği tüm yapılandırma bloklarını ve önerilen varsayılanları içerir:

```yaml
# ── Browser (Tarayıcı) ────────────────────────────────────────────────────────
browser:
  name: chrome                      # chrome | firefox | edge | safari
  headless: false                   # CI ortamı algılandığında otomatik true yapılır
  lifecycle: per-test               # per-test (her teste temiz oturum) | per-suite (thread başına oturum koruma)
  downloadDir: ./target/downloads   # indirilen dosyaların kaydedileceği dizin
  captureConsoleErrors: false       # tarayıcı console.error loglarını topla
  failOnConsoleErrors: false        # SEVERE konsol hatası varsa testi başarısız say
  device:                           # isteğe bağlı mobil emülasyon profili (örn: "iPhone 14")
  arguments:                        # tarayıcı çalıştırılabilirine iletilecek ek bayraklar
    - --start-maximized
    - --disable-notifications
    - --remote-allow-origins=*
  capabilities:                     # doğrudan WebDriver capability geçersiz kılmaları
    acceptInsecureCerts: true
    pageLoadStrategy: eager

# ── Execution (Çalıştırma Modu) ───────────────────────────────────────────────
execution:
  mode: local                       # local | remote | browserstack | saucelabs
  baseUrl: https://example.com      # open("/") çağrılarında kullanılan temel web URL'i
  gridUrl: http://localhost:4444    # Selenium Grid hub URL'i (mode: remote iken)
  parallel: none                    # none | methods | classes | tests | instances
  threadCount: 1                    # parallel etkin olduğunda eşzamanlı çalışan iş parçacığı sayısı
  maxActiveSessions: 5              # eşzamanlı aktif tarayıcı sayısını sınırlayan semafor

  # ── BrowserStack (mode: browserstack)
  browserstack:
    username: ${BS_USER}
    accessKey: ${BS_KEY}
    os: Windows                     # Windows | OS X
    osVersion: "11"
    browser: chrome                 # chrome | firefox | edge | safari
    browserVersion: latest
    device:                         # gerçek mobil cihaz adı (örn: "iPhone 14")
    realMobile: true
    capabilities:                   # ek bstack:options geçersiz kılmaları
      debug: false

  # ── Sauce Labs (mode: saucelabs)
  saucelabs:
    username: ${SAUCE_USER}
    accessKey: ${SAUCE_KEY}
    region: us-west-1               # us-west-1 | eu-central | apac-southeast
    platformName: "Windows 11"
    browser: chrome
    browserVersion: latest
    capabilities:                   # ek sauce:options geçersiz kılmaları
      recordVideo: true

# ── Timeouts (Zaman Aşımları) ────────────────────────────────────────────────
timeouts:
  explicit: 10                      # saniye — WaitEngine, Locator ve assertThat bekleme süresi
  pageLoad: 30                      # saniye — WebDriver sayfa yükleme zaman aşımı

# ── Retry (Yeniden Deneme) ───────────────────────────────────────────────────
retry:
  enabled: true                     # global otomatik yeniden deneme anahtarı
  maxAttempts: 2                    # test başına toplam deneme sayısı (1 = tekrar yok, 2 = 1 asıl + 1 tekrar)

# ── Locators (Seçiciler ve İyileştirme) ───────────────────────────────────────
locators:
  selfHealing: false                # zaman aşımına uğrayan seçicileri alternatif stratejilerle otomatik onar
  testIdAttribute: data-testid      # getByTestId() tarafından sorgulanacak HTML özniteliği

# ── AI Failure Analysis (AI Hata Analizi) ────────────────────────────────────
ai:
  failureAnalysis: false            # test başarısız olduğunda AI ile kök neden analizi üret
  provider: gemini                  # gemini | claude | openai-compatible
  apiKey: ${AI_API_KEY}             # sağlayıcı API anahtarı
  model:                            # isteğe bağlı — varsayılan: gemini-2.5-flash veya claude-haiku-4-5-20251001
  language: tr                      # üretilecek analiz dili: tr, en, de, fr vb.
  timeoutSeconds: 20                # AI sağlayıcısından yanıt bekleme zaman aşımı
  baseUrl:                          # isteğe bağlı — özel uç nokta (openai-compatible için zorunludur)

# ── CI Kalite Kapıları ───────────────────────────────────────────────────────
ci:
  failOnPassRateBelow: 0            # 0 = devre dışı. Örnek: 85 (başarı oranı %85 altındaysa build'i kır)
  maxFlakyTests: -1                 # -1 = devre dışı. Tekrar denemeyle geçen test sayısı aşılırsa build'i kır
  captureMetadata: true             # raporlara Git dalı, commit, build URL bilgilerini otomatik ekle

# ── Bildirimler (Notifications) ──────────────────────────────────────────────
notifications:
  slack:
    webhookUrl: ${SLACK_WEBHOOK}
    notifyOnFailureOnly: false
  teams:
    webhookUrl: ${TEAMS_WEBHOOK}
    notifyOnFailureOnly: false

# ── Reporting (Raporlama) ────────────────────────────────────────────────────
reporting:
  allure:
    enabled: false                  # target/allure-results/ dizinine Allure 2 rapor çıktıları üret
  reportportal:
    enabled: false
    endpoint: http://localhost:8080
    apiKey: ${RP_API_KEY}
    project: testfly_project
    launch: "Regression Suite"
    description: "Otomatik test koşumu"
    attributes: "env:staging;team:qa"
    type: auto                      # auto (Web vs API otomatik algıla) | web | api
    mode: default                   # default | step

# ── Ekran Kaydı (Screen Recording) ───────────────────────────────────────────
recording:
  enabled: false                    # tarayıcı oturumunu MP4 video olarak kaydet
  fps: 2                            # saniyedeki kare sayısı (test kayıtları için 1-5 arası önerilir)
  maxDurationSeconds: 60            # test başına maksimum video uzunluğu (saniye)

# ── Yürütme İzleme (Execution Tracing) ───────────────────────────────────────
tracing:
  enabled: false                    # DOM enstantanelerini ve olay zaman çizelgesini yakala
  captureOnPass: false              # başarılı testler için de izleme kaydet

# ── Görsel Karşılaştırma (Visual Regression) ─────────────────────────────────
visual:
  baselineDir: src/test/resources/baselines  # onaylanmış referans görseller dizini
  diffDir: target/visual-diffs               # görsel uyuşmazlık çıktılarının yazılacağı dizin
  defaultTolerance: 0.01                     # izin verilen piksel fark oranı (0.0 ile 1.0 arası)
  updateBaselines: false                     # true ise geçerli ekran görüntülerini referans olarak kaydeder

# ── Çoklu Oturum İzolasyonu (Sessions) ───────────────────────────────────────
sessions:
  maxPerTest: 2                     # test başına izin verilen izole tarayıcı sayısı (örn: çok kullanıcılı sohbet)

# ── Performans (Core Web Vitals) ─────────────────────────────────────────────
performance:
  captureOnEveryTest: false         # sayfa açılışlarında Core Web Vitals metriklerini otomatik topla
  lcpWarnMs: 2500                   # Largest Contentful Paint uyarı eşiği (ms, 0 = devre dışı)
  fcpWarnMs: 1800                   # First Contentful Paint uyarı eşiği (ms)
  ttfbWarnMs: 800                   # Time to First Byte uyarı eşiği (ms)
  clsWarn: 0.1                      # Cumulative Layout Shift eşiği

# ── Karantina (Quarantine) ───────────────────────────────────────────────────
quarantine:
  enabled: true                     # testfly-quarantine.yml veya Cucumber etiketli testleri otomatik atla
  cucumberTag: quarantine           # karantinaya alınmış senaryoları belirten etiket adı (@ olmadan)

# ── Kararsızlık Takibi (Flakiness) ───────────────────────────────────────────
flakiness:
  historyRuns: 20                   # kararlılık skoru için incelenecek geçmiş koşum sayısı
  highRiskThreshold: 33.0           # yüksek risk sayılacak kararsızlık hata yüzdesi
  failOnHighFlakiness: false        # yüksek riskli kararsız test tespit edilirse build'i kır

# ── Zaman Simülasyonu (Clock Mocking) ────────────────────────────────────────
clock:
  injectHeader: false               # HTTP isteklerine simüle edilmiş tarih başlığı ekle
  headerName: X-Mock-Date           # backend ile zaman senkronizasyonu için özel başlık adı

# ── Ağ Araya Girme (Network Interception) ────────────────────────────────────
network:
  interceptEnabled: false           # CDP üzerinden ağ trafiğine müdahale ve yanıt mock'lamayı etkinleştir

# ── E-posta Doğrulama (Email Verification) ───────────────────────────────────
email:
  provider: mailhog                 # mailhog | mailtrap | outlook | imap
  timeoutSeconds: 30                # beklenen e-postanın gelmesi için maksimum bekleme süresi
  pollIntervalMs: 1000              # gelen kutusunu sorgulama aralığı (ms)
  autoClear: false                  # her testten önce gelen kutusunu otomatik temizle
  mailhog:
    host: localhost
    port: 8025
  mailtrap:
    apiToken: ${MAILTRAP_TOKEN}
    accountId: ${MAILTRAP_ACCOUNT}
    inboxId: ${MAILTRAP_INBOX}
  outlook:
    tenantId: ${AZURE_TENANT_ID}
    clientId: ${AZURE_CLIENT_ID}
    clientSecret: ${AZURE_CLIENT_SECRET}
    mailbox: test@example.com
  imap:
    host: imap.example.com
    port: 993
    ssl: true
    username: ${EMAIL_USER}
    password: ${EMAIL_PASS}
    folder: INBOX

# ── Veritabanı Doğrulamaları (Database) ───────────────────────────────────────
database:
  url: jdbc:postgresql://localhost:5432/maindb
  username: ${DB_USER}
  password: ${DB_PASS}
  driver: org.postgresql.Driver
  datasources:
    analytics:
      url: jdbc:postgresql://localhost:5432/analytics
      username: ${ANALYTICS_USER}
      password: ${ANALYTICS_PASS}

# ── API Testi (API Testing) ──────────────────────────────────────────────────
api:
  baseUrl: https://api.example.com  # ApiClient için varsayılan HTTP adresi
  timeoutSeconds: 30
  logBody: false                    # istek ve yanıt gövdelerini HTML adım günlüğüne ekle
  logContext: true                  # sorgu parametrelerini ve başlıkları günlüğe kaydet
  prettyLog: false                  # JSON yanıtlarını biçimlendirilmiş (girintili) yaz
  logCurl: false                    # başarısız isteklerde eşdeğer curl komutunu yazdır
  truncationLimit: 300              # yanıt gövdelerinin günlüğe yazılacak maksimum karakter sınırı
  maskedHeaders:                    # günlüklere yazılırken maskelenecek başlıklar
    - Authorization
    - Cookie
    - X-Api-Key
  retry:
    enabled: false                  # geçici HTTP hatalarında otomatik yeniden dene
    maxAttempts: 3
    backoffMs: 500
    retryOnStatus: [502, 503, 504]
    retryOnException: true
  auth:
    adminBearer:
      type: bearer
      token: ${ADMIN_TOKEN}
    basicAuth:
      type: basic
      username: apiuser
      password: ${API_PASS}
    oauthClient:
      type: oauth2
      tokenUrl: https://auth.example.com/oauth/token
      clientId: ${CLIENT_ID}
      clientSecret: ${CLIENT_SECRET}

# ── Test Yönetim Sistemleri (Test Management) ────────────────────────────────
testmanagement:
  testrail:
    enabled: false
    url: https://myorg.testrail.io
    username: ${TR_USER}
    apiKey: ${TR_KEY}
    projectId: 1
    suiteId: 10
    runName: "Otomatik Test Koşumu"
    autoCreateRun: true
  xray:
    enabled: false
    mode: cloud                     # cloud | server
    clientId: ${XRAY_ID}
    clientSecret: ${XRAY_SECRET}
    projectKey: PROJ
    testPlanKey: PROJ-100
```

---

## Detaylı Bölüm Rehberi

## Tarayıcı (Browser) {#browser}

Tarayıcı sağlama, çalıştırma modu, yetenekler ve süreç argümanlarını yönetir.

| Özellik | Tip | Varsayılan | Açıklama |
|---|---|---|---|
| `name` | `string` | `chrome` | Başlatılacak tarayıcı. Geçerli değerler: `chrome`, `firefox`, `edge`, `safari`. |
| `headless` | `boolean` | `false` | Tarayıcıyı görsel bir pencere olmadan arka planda çalıştırır. CI ortamında otomatik `true` yapılır. |
| `lifecycle` | `string` | `per-test` | WebDriver yaşam döngüsü: `per-test` (her testten sonra kapatır) veya `per-suite` (thread başına oturumu testler boyunca açık tutar). |
| `downloadDir` | `string` | `./target/downloads` | İndirilen dosyaların kaydedileceği yerel dizin. |
| `captureConsoleErrors` | `boolean` | `false` | `true` ise yürütme sırasında tarayıcı `console.error` loglarını yakalar. |
| `failOnConsoleErrors` | `boolean` | `false` | `true` ise test sırasında `SEVERE` düzeyinde konsol hatası oluştuğunda testi başarısız sayar. |
| `device` | `string` | `null` | Mobil emülasyon profili adı (örn: `"iPhone 14"`, `"Pixel 7"`). |
| `arguments` | `list<string>` | `[]` | Tarayıcı ikili dosyasına aktarılan komut satırı bayrakları (örn: `--incognito`, `--no-sandbox`). |
| `capabilities` | `map` | `{}` | WebDriver seçeneklerine eklenen ham yetenekler (örn: `acceptInsecureCerts`, `pageLoadStrategy`). |

---

## Çalıştırma (Execution) {#execution}

Test dağıtımı, temel adresler, eşzamanlılık ve bulut ızgara (grid) sağlayıcılarını kontrol eder.

| Özellik | Tip | Varsayılan | Açıklama |
|---|---|---|---|
| `mode` | `string` | `local` | Çalıştırma ortamı: `local`, `remote`, `browserstack` veya `saucelabs`. |
| `baseUrl` | `string` | `null` | Web testleri için temel URL. `open("/home")` çağrıldığında bu adresin ardına eklenir. |
| `gridUrl` | `string` | `null` | Uzak Selenium Grid adresi (`mode: remote` iken zorunludur). Örnek: `http://localhost:4444`. |
| `parallel` | `string` | `none` | TestNG paralel dağıtım modu: `none`, `methods`, `classes`, `tests`, `instances`. |
| `threadCount` | `int` | `1` | Paralel mod aktifken çalışacak iş parçacığı (worker thread) sayısı. |
| `maxActiveSessions` | `int` | `5` | Eşzamanlı aktif tarayıcı oturumlarını sınırlayan semafor. Ekstra testler yuva boşalana kadar 30 saniyeye kadar bekler. |

#### Bulut Blokları: `browserstack` & `saucelabs`

```yaml
execution:
  mode: browserstack
  browserstack:
    username: ${BS_USER}
    accessKey: ${BS_KEY}
    os: Windows
    osVersion: "11"
    browser: chrome
    browserVersion: latest
    capabilities:
      projectName: "E-Ticaret"
      buildName: "Build #104"
```

---

## Zaman Aşımları (Timeouts) {#timeouts}

Saniye cinsinden merkezi bekleme süreleri.

| Özellik | Tip | Varsayılan | Açıklama |
|---|---|---|---|
| `explicit` | `int` | `10` | `WaitEngine`, `Locator` ve `assertThat()` DOM sorgularında kullanılan zaman aşımı (saniye). |
| `pageLoad` | `int` | `30` | `WebDriver.Timeouts.pageLoadTimeout()` süresi (saniye). |

---

## Yeniden Deneme (Retry) {#retry}

Başarısız testlerin otomatik olarak yeniden denenmesi.

| Özellik | Tip | Varsayılan | Açıklama |
|---|---|---|---|
| `enabled` | `boolean` | `true` | Otomatik test tekrarını küresel olarak açar/kapatır. |
| `maxAttempts` | `int` | `1` | Test başına toplam deneme sayısı. `1` = tekrar yok, `2` = 1 asıl koşum + 1 tekrar. |

:::info Metot Düzeyinde Geçersiz Kılma
Belli bir test için genel ayarı `@Retryable(maxAttempts = 3)` anotasyonu ile ezebilirsiniz.
:::

---

## Seçiciler (Locators) {#locators}

Akıllı seçici sentezi ve dayanıklılık ayarları.

| Özellik | Tip | Varsayılan | Açıklama |
|---|---|---|---|
| `selfHealing` | `boolean` | `false` | Açıldığında, `waitForVisible` sırasında zaman aşımına uğrayan seçiciler alternatif stratejilerle (id, test-id, text, placeholder) otomatik onarılır ve `target/healed-locators.json` dosyasına yazılır. |
| `testIdAttribute` | `string` | `data-testid` | `getByTestId("submit-btn")` çağrısının hedeflediği HTML niteliği (`data-qa`, `data-test` vb. olarak değiştirilebilir). |

---

## AI Hata Analizi {#ai}

Yapay zeka destekli hata sınıflandırma ve öneri motoru.

| Özellik | Tip | Varsayılan | Açıklama |
|---|---|---|---|
| `failureAnalysis` | `boolean` | `false` | `true` ise test çöktüğünde hata yığını, adım günlüğü ve DOM durumunu LLM modeline gönderir. |
| `provider` | `string` | `claude` | AI arka ucu: `gemini`, `claude` veya `openai-compatible`. |
| `apiKey` | `string` | `null` | Seçilen sağlayıcı için yetkilendirme anahtarı. |
| `model` | `string` | `null` | Hedef model adı. Boş bırakıldığında Gemini için `gemini-2.5-flash`, Claude için `claude-haiku-4-5-20251001` varsayılandır. |
| `baseUrl` | `string` | `null` | Özel API adresi (`openai-compatible` sağlayıcılar için zorunludur). |
| `language` | `string` | `tr` | Üretilecek analiz raporunun dili (`tr`, `en`, `de`, `fr` vb.). |
| `timeoutSeconds` | `int` | `20` | AI yanıtı için maksimum bekleme süresi (saniye). |

---

## CI Kalite Kapıları {#ci}

CI ortamı algılama ve derleme başarı kriterleri.

| Özellik | Tip | Varsayılan | Açıklama |
|---|---|---|---|
| `failOnPassRateBelow` | `double` | `0` | Minimum test başarı yüzdesi (örn: `85.0`). Başarı bu oranın altındaysa derleme kırılır. `0` devre dışıdır. |
| `maxFlakyTests` | `int` | `-1` | Tekrar denemeyle geçen (flaky) test sayısı bu sınırı aşarsa derleme kırılır. `-1` devre dışıdır. |
| `captureMetadata` | `boolean` | Otomatik | CI ortam bilgilerini (Git dalı, commit SHA, PR no, build URL) raporlara işler. |

---

## Raporlama (Reporting) {#reporting}

Harici test panoları ve sonuç portalları entegrasyonu.

#### `reportportal`

| Özellik | Tip | Varsayılan | Açıklama |
|---|---|---|---|
| `enabled` | `boolean` | `false` | Sonuçları ReportPortal'a aktarmayı açar. |
| `endpoint` | `string` | `null` | ReportPortal sunucu adresi (örn: `http://reportportal.sirketim.com:8080`). |
| `apiKey` | `string` | `null` | Kullanıcı API Erişim Belirteci. |
| `project` | `string` | `superadmin_personal` | Proje adı. |
| `launch` | `string` | `TestFly Suite` | ReportPortal'da açılacak test koşumunun adı. |
| `type` | `string` | `auto` | Koşum zenginleştirme tipi: `auto` (Web vs API otomatik algılar), `web` veya `api`. |
| `mode` | `string` | `default` | Dinleyici modu: `default` veya `step`. |
| `attributes` | `string` | `""` | Koşuma eklenecek etiketler (örn: `"env:ci;takim:qa"`). |

#### `allure`

| Özellik | Tip | Varsayılan | Açıklama |
|---|---|---|---|
| `enabled` | `boolean` | `false` | `target/allure-results/` dizinine Allure 2 çıktıları üretir. |

---

## Bildirimler (Notifications) {#notifications}

Test koşumu tamamlandığında webhook üzerinden özet bildirim gönderimi.

| Özellik | Tip | Varsayılan | Açıklama |
|---|---|---|---|
| `slack.webhookUrl` | `string` | `null` | Slack Incoming Webhook URL'i. |
| `slack.notifyOnFailureOnly` | `boolean` | `false` | Yalnızca başarısız test varsa bildirim gönder. |
| `teams.webhookUrl` | `string` | `null` | Microsoft Teams Connector Webhook URL'i. |
| `teams.notifyOnFailureOnly` | `boolean` | `false` | Yalnızca başarısız test varsa bildirim gönder. |

---

## API Testi (API Testing) {#api}

Yerleşik REST istemcisi (`ApiClient` & `BaseApiTest`) yapılandırması.

| Özellik | Tip | Varsayılan | Açıklama |
|---|---|---|---|
| `baseUrl` | `string` | `null` | API testleri için varsayılan HTTP adresi (tanımsızsa `execution.baseUrl` kullanılır). |
| `timeoutSeconds` | `int` | `30` | İstek zaman aşımı (saniye). |
| `logBody` | `boolean` | `false` | İstek ve yanıt gövdelerini HTML adım raporuna ekle. |
| `logContext` | `boolean` | `true` | Başlıkları, sorgu parametrelerini ve HTTP metotlarını günlüğe yaz. |
| `prettyLog` | `boolean` | `false` | JSON gövdelerini biçimlendirerek yaz. |
| `logCurl` | `boolean` | `false` | Başarısız isteklerde eşdeğer `curl` komutunu yazdır. |
| `truncationLimit` | `int` | `300` | Yanıt gövdelerinin günlüğe yazılacağı maksimum karakter sayısı. |
| `maskedHeaders` | `list<string>` | `["Authorization", "Cookie", "X-Api-Key"]` | Günlüklerde maskelenecek başlıklar. |

#### `api.retry`

Geçici sunucu hatalarında (örn: 502, 503, 504) HTTP düzeyinde yeniden deneme politikası:

```yaml
api:
  retry:
    enabled: true
    maxAttempts: 3
    backoffMs: 500
    retryOnStatus: [502, 503, 504]
    retryOnException: true
```

#### `api.auth`

Testlerde `@UseAuth("ad")` veya `apiClient().withAuth("ad")` ile çağrılan adlandırılmış kimlik doğrulama profilleri:

```yaml
api:
  auth:
    adminBearer:
      type: bearer
      token: ${SECRET_TOKEN}
    gatewayUser:
      type: basic
      username: testuser
      password: ${USER_PASS}
    keyAuth:
      type: apiKey
      headerName: X-API-Token
      apiKey: ${API_KEY}
    oauth2Service:
      type: oauth2
      tokenUrl: https://auth.sirketim.com/oauth/token
      clientId: ${CLIENT_ID}
      clientSecret: ${CLIENT_SECRET}
```

---

## Veritabanı (Database) {#database}

`db()` yardımcısı ile veritabanı durumu doğrulama ve veri tohumlama bağlantısı.

| Özellik | Tip | Varsayılan | Açıklama |
|---|---|---|---|
| `url` | `string` | `null` | Varsayılan veri kaynağı için JDBC bağlantı dizesi. |
| `username` | `string` | `null` | Veritabanı kullanıcısı. |
| `password` | `string` | `null` | Veritabanı parolası. |
| `driver` | `string` | `null` | JDBC sürücü sınıfı (çoğu popüler veritabanında URL'den otomatik algılanır). |
| `datasources` | `map` | `{}` | `db("ad")` ile erişilen adlandırılmış veri kaynakları. |

---

## E-posta Doğrulama (Email) {#email}

Gelen kutusu test entegrasyonları (`Mailhog`, `Mailtrap`, `Outlook Graph`, `IMAP`).

| Özellik | Tip | Varsayılan | Açıklama |
|---|---|---|---|
| `provider` | `string` | `mailhog` | Aktif sağlayıcı: `mailhog`, `mailtrap`, `outlook`, `imap`. |
| `timeoutSeconds` | `int` | `30` | E-postanın gelmesi için bekleme süresi (saniye). |
| `pollIntervalMs` | `int` | `1000` | Gelen kutusunu kontrol etme aralığı (ms). |
| `autoClear` | `boolean` | `false` | Her test metodundan önce gelen kutusunu temizle. |

---

## Performans (Performance) {#performance}

Web testlerinde Google Core Web Vitals metriklerini otomatik toplama ve doğrulama.

| Özellik | Tip | Varsayılan | Açıklama |
|---|---|---|---|
| `captureOnEveryTest` | `boolean` | `false` | Her `open()` çağrısında CWV metriklerini topla. |
| `lcpWarnMs` | `double` | `0` | Largest Contentful Paint uyarı eşiği (ms, 0 = devre dışı). |
| `fcpWarnMs` | `double` | `0` | First Contentful Paint uyarı eşiği (ms). |
| `ttfbWarnMs` | `double` | `0` | Time to First Byte uyarı eşiği (ms). |
| `clsWarn` | `double` | `0` | Cumulative Layout Shift skor eşiği (örn: `0.1`). |

---

## Görsel Karşılaştırma (Visual) {#visual}

Piksel tabanlı ekran görüntüsü karşılaştırması ve referans görsel yönetimi.

| Özellik | Tip | Varsayılan | Açıklama |
|---|---|---|---|
| `baselineDir` | `string` | `src/test/resources/baselines` | Onaylanmış referans görseller dizini. |
| `diffDir` | `string` | `target/visual-diffs` | Fark tespit edilen görsellerin yazılacağı dizin. |
| `defaultTolerance` | `double` | `0` | İzin verilen piksel fark tolerans oranı (örn: %2 için `0.02`). |
| `updateBaselines` | `boolean` | `false` | Test koşumundaki güncel ekran görüntülerini referans görsel olarak kaydet. |

---

## Ekran Kaydı ve İzleme {#recording}

Hata ayıklama ve denetim uyumluluğu için oturum yakalama.

* **`recording.enabled`**: Test koşumunun MP4 video kaydını alır.
* **`recording.fps`**: Saniyedeki kare sayısı (varsayılan `2`).
* **`recording.maxDurationSeconds`**: Test başına maksimum video süresi (varsayılan `60` sn).
* **`tracing.enabled`**: Adım adım DOM ve ağ olaylarını yakalar.
* **`tracing.captureOnPass`**: Başarılı testlerde de izleme kaydeder (varsayılan `false`).

---

## Karantina (Quarantine) {#quarantine}

Kararsız veya bakım altındaki testlerin otomatik yönetimi.

| Özellik | Tip | Varsayılan | Açıklama |
|---|---|---|---|
| `enabled` | `boolean` | `true` | `testfly-quarantine.yml` içinde listelenen veya Cucumber etiketi alan testleri otomatik atlar. |
| `cucumberTag` | `string` | `quarantine` | Karantinadaki senaryoları belirten Cucumber etiketi (@ olmadan). |

---

## Kararsızlık Takibi (Flakiness) {#flakiness}

Geçmiş test kararlılık skorlaması ve kırılgan test önleme.

| Özellik | Tip | Varsayılan | Açıklama |
|---|---|---|---|
| `historyRuns` | `int` | `20` | Skorlama için incelenecek geçmiş çalıştırma sayısı. |
| `highRiskThreshold` | `double` | `33.0` | Yüksek riskli sayılacak hata yüzdesi. |
| `failOnHighFlakiness` | `boolean` | `false` | Yüksek riskli test varsa derlemeyi başarısız sayar. |

---

## Test Yönetim Sistemleri {#testmanagement}

Test sonuçlarını ve koşum bağlantılarını Jira ve TestRail'e otomatik aktarır.

```yaml
testmanagement:
  testrail:
    enabled: true
    url: https://sirketim.testrail.io
    username: ${TR_USER}
    apiKey: ${TR_KEY}
    projectId: 1
    suiteId: 2
    autoCreateRun: true

  xray:
    enabled: true
    mode: cloud                 # cloud | server
    clientId: ${XRAY_ID}
    clientSecret: ${XRAY_SECRET}
    projectKey: PROJ
    testPlanKey: PROJ-12
```

---

## Saat ve Ağ Müdahalesi {#clock}

* **`clock.injectHeader`**: Tarayıcı isteklerine sahte tarih HTTP başlığı ekler (varsayılan `false`).
* **`clock.headerName`**: Özel başlık adı (varsayılan `"X-Mock-Date"`).
* **`network.interceptEnabled`**: Chrome DevTools Protokolü üzerinden ağ trafiğine müdahale ve stubbing'i açar (varsayılan `false`).

---

## Doğrulama ve Başlatma Tanılaması

TestFly, başlatma sırasında katı bir şema doğrulaması uygular. Geçersiz bir paralel çalıştırma modu, eksik bir bulut kimlik bilgisi veya hatalı bir URL tespit edilirse; hiçbir tarayıcı ayağa kaldırılmadan çalışma anında durdurulur ve kaynak israfı engellenir:

```text
[TestFly] Configuration validation failed:
- execution.parallel: 'gecersiz_mod' geçerli bir mod değil. İzin verilenler: [none, methods, classes, tests, instances]
- execution.baseUrl: Geçerli ve mutlak bir HTTP/HTTPS adresi olmalıdır
```
