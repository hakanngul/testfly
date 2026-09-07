# TestFly Kapsamlı Mimari & Kod Denetim Kılavuzu (Full Project Audit Guide)

Bu doküman, **TestFly** test otomasyon framework'ünün **tüm paket ve modüllerini**, her modüldeki kritik dosyaları ve bağımsız bir AI aracı veya kıdemli mimar tarafından denetlenmesi gereken noktaları içerir.

---

## 📑 İçindekiler
1. [Agentic AI & Doğal Dil Eylem Derleme (`agent/`, `assertion/ai/`)](#1-agentic-ai--doğal-dil-eylem-derleme)
2. [LLM Sağlayıcıları & Ağ Entegrasyonu (`ai/`)](#2-llm-sağlayıcıları--ağ-entegrasyonu)
3. [İki Kademeli Self-Healing (Statik + AI) (`healing/`, `ai/`)](#3-iki-kademeli-self-healing-statik--ai)
4. [Hata Analizi & Otomatik Yama Üretimi (`ai/remediation/`, `listeners/`)](#4-hata-analizi--otomatik-yama-üretimi)
5. [PreCondition & Akıllı Oturum Önbellekleme (`precondition/`)](#5-precondition--akıllı-oturum-önbellekleme)
6. [Selenium 4 CDP Network Interception & Mocking (`network/`)](#6-selenium-4-cdp-network-interception--mocking)
7. [Thread-Safe WebDriver Yönetimi & Yaşam Döngüsü (`driver/`, `session/`)](#7-thread-safe-webdriver-yönetimi--yaşam-döngüsü)
8. [WaitEngine, Akıllı Locator & Shadow DOM (`wait/`, `locator/`, `shadow/`)](#8-waitengine-akıllı-locator--shadow-dom)
9. [Web-First & Soft Assertions (`assertion/`)](#9-web-first--soft-assertions)
10. [REST API İstemcisi & Doğrulamalar (`client/`, `api/`)](#10-rest-api-istemcisi--doğrulamalar)
11. [Görsel Regresyon Testleri (`visual/`)](#11-görsel-regresyon-testleri)
12. [Erişilebilirlik Testleri (Axe-Core) (`accessibility/`)](#12-erişilebilirlik-testleri-axe-core)
13. [Web Performans & Core Web Vitals (`performance/`)](#13-web-performans--core-web-vitals)
14. [Ekran & Video Kaydı (`recording/`)](#14-ekran--video-kaydı)
15. [E-Posta Doğrulama İstemcileri (`email/`)](#15-e-posta-doğrulama-istemcileri)
16. [Veritabanı İstemcisi & Assertions (`db/`)](#16-veritabanı-istemcisi--assertions)
17. [Dinamik Test Verisi Yükleyicileri (`testdata/`)](#17-dinamik-test-verisi-yükleyicileri)
18. [Flakiness Analizi & Karantina Sistemi (`flakiness/`, `quarantine/`)](#18-flakiness-analizi--karantina-sistemi)
19. [Zaman & Tarayıcı Saati Manipülasyonu (`clock/`)](#19-zaman--tarayıcı-saati-manipülasyonu)
20. [Tarayıcı Yardımcıları & Donanım Emülasyonu (`browser/`)](#20-tarayıcı-yardımcıları--donanım-emülasyonu)
21. [Raporlama & Entegrasyonlar (`reporting/`, `testmanagement/`)](#21-raporlama--entegrasyonlar)
22. [CI / Quality Gate Mekanizmaları (`ci/`, `listeners/`)](#22-ci--quality-gate-mekanizmaları)
23. [Cucumber BDD & JUnit 5 Köprüleri (`cucumber/`, `junit5/`)](#23-cucumber-bdd--junit-5-köprüleri)
24. [SPI Eklenti & Yaşam Döngüsü Kancaları (`extension/`, `hooks/`)](#24-spi-eklenti--yaşam-döngüsü-kancaları)
25. [Konfigürasyon & Dokümantasyon Tutarlılığı (`config/`, `docs-site/`)](#25-konfigürasyon--dokümantasyon-tutarlılığı)
26. [Bağımlılık İzolasyonu & Transitivite (`pom.xml`)](#26-bağımlılık-izolasyonu--transitivite)
27. [Framework Başlatma Motoru (`lifecycle/FrameworkBootstrap.java`)](#27-framework-başlatma-motoru)
28. [Bellek & State Sızıntısı (`context/`, `internal/`)](#28-bellek--state-sızıntısı)
29. [CI/CD Pipeline Yapılandırması (`.github/`, `ci/`)](#29-cicd-pipeline-yapılandırması)
30. [Dokümantasyon Sitesi Derleme & Link Sağlığı (`docs-site/`)](#30-dokümantasyon-sitesi-derleme--link-sağlığı)
31. [Statik Kod Analizi & Kalite Kapıları (`pom.xml` `-Pquality`)](#31-statik-kod-analizi--kalite-kapıları)

---

## 1. Agentic AI & Doğal Dil Eylem Derleme
Doğal dil hedeflerini (`act("...")`) LLM ile Selenium adımlarına derler, `.testfly/action-cache.json` dosyasına dondurur ve sıfır gecikmeyle (0 ms) yeniden oynatır.

### İncelenecek Dosyalar:
- `src/main/java/io/testfly/test/BaseTest.java`
- `src/main/java/io/testfly/test/BasePage.java`
- `src/main/java/io/testfly/agent/ActionCompiler.java`
- `src/main/java/io/testfly/agent/ActionCache.java`
- `src/main/java/io/testfly/agent/ActionExecutor.java`
- `src/main/java/io/testfly/agent/ActionPlan.java`
- `src/main/java/io/testfly/agent/ActionStep.java`
- `src/main/java/io/testfly/agent/ActionType.java`
- `src/main/java/io/testfly/assertion/ai/AiAssertEngine.java`
- `src/main/java/io/testfly/ai/DomPruner.java`

### Denetim Soruları:
1. `ActionCompiler` içerisindeki DOM filtreleme prompt'u token sınırını aşmayacak şekilde optimize edilmiş mi?
2. `ActionCache`: Başarısız plan `invalidate` edildiğinde diskteki JSON'dan siliniyor mu?
3. `ActionExecutor`: LLM'in ürettiği `css=`, `xpath=`, `id=` önekleri ve tırnak işaretleri temizleniyor mu?

---

## 2. LLM Sağlayıcıları & Ağ Entegrasyonu
OpenAI, Anthropic Claude, Google Gemini, DeepSeek ve Alibaba Cloud Qwen (Token Plan) REST API haberleşmesi.

### İncelenecek Dosyalar:
- `src/main/java/io/testfly/ai/AiProvider.java`
- `src/main/java/io/testfly/ai/AiProviderRegistry.java`
- `src/main/java/io/testfly/ai/OpenAiCompatibleProvider.java`
- `src/main/java/io/testfly/ai/ClaudeProvider.java`
- `src/main/java/io/testfly/ai/GeminiProvider.java`

### Denetim Soruları:
1. `OpenAiCompatibleProvider.buildEndpointUrl()`: `/compatible-mode/v1` ve `/chat/completions` uç noktalarını çakışma olmadan doğru bağlıyor mu?
2. `ClaudeProvider`: Özel proxy gateway URL'leri (örn. `.../apps/anthropic`) destekleniyor mu ve `Authorization: Bearer` başlığı gönderiliyor mu?
3. Reasoning modelleri için token sınırı (`max_tokens: 2048`) ve timeout kontrolleri yeterli mi?

---

## 3. İki Kademeli Self-Healing (Statik + AI)
Element bulunamadığında önce statik kurallar (erişilebilirlik, ebeveyn-çocuk, metin), ardından LLM devreye girer; iyileşen seçici `.testfly/healed-locators.json` içine kaydedilir.

### İncelenecek Dosyalar:
- `src/main/java/io/testfly/healing/SelfHealingLocator.java`
- `src/main/java/io/testfly/healing/HealingCache.java`
- `src/main/java/io/testfly/healing/HealEvent.java`
- `src/main/java/io/testfly/healing/HealLog.java`
- `src/main/java/io/testfly/ai/AiHealingEngine.java`

### Denetim Soruları:
1. `SelfHealingLocator`: Statik onarım başarısız olduğunda `AiHealingEngine`'e geçiş sırası mimari olarak temiz mi?
2. `HealingCache`: Paralel testlerde diske yazma işlemi (dosya kilitleme/concurrency) thread-safe mi?

---

## 4. Hata Analizi & Otomatik Yama Üretimi
Test patladığında kök neden analizi üretir ve kaynak koda uygulanabilecek `.patch` Unified Diff dosyası oluşturur.

### İncelenecek Dosyalar:
- `src/main/java/io/testfly/ai/AiFailureAnalyzer.java`
- `src/main/java/io/testfly/ai/remediation/RemediationPatchGenerator.java`
- `src/main/java/io/testfly/ai/remediation/SourceCodeLocator.java`
- `src/main/java/io/testfly/listeners/TestExecutionListener.java`

### Denetim Soruları:
1. `SourceCodeLocator`: Hatanın gerçekleştiği Java sınıfını ve satır numarasını stack trace üzerinden doğru ayrıştırabiliyor mu?
2. `RemediationPatchGenerator`: Üretilen git diff formatı `git apply` komutuyla doğrudan uygulanabilir standartta mı?

---

## 5. PreCondition & Akıllı Oturum Önbellekleme
Tekrarlayan UI login adımlarını atlamak için cookies/localStorage durumunu belleğe alır.

### İncelenecek Dosyalar:
- `src/main/java/io/testfly/precondition/PreCondition.java`
- `src/main/java/io/testfly/precondition/ConditionProvider.java`
- `src/main/java/io/testfly/precondition/BaseConditions.java`
- `src/main/java/io/testfly/precondition/PreconditionSessionCache.java`
- `src/main/java/io/testfly/precondition/PreConditionRunner.java`
- `src/main/java/io/testfly/precondition/ApiHealthChecker.java`

### Denetim Soruları:
1. `PreconditionSessionCache`: ThreadLocal paralel test koşularında diğer testlerin oturum verilerini ezme riski var mı?
2. Oturum düştüğünde (401/403) cache'i otomatik geçersiz kılıp yeniden UI login yaptıran mekanizma var mı?

---

## 6. Selenium 4 CDP Network Interception & Mocking
Chrome DevTools Protocol (CDP) üzerinden istek durdurma, yanıtlama (mock) ve ağ trafiği doğrulama.

### İncelenecek Dosyalar:
- `src/main/java/io/testfly/network/NetworkMock.java`
- `src/main/java/io/testfly/network/Route.java`
- `src/main/java/io/testfly/network/RouteRule.java`
- `src/main/java/io/testfly/network/StubBuilder.java`
- `src/main/java/io/testfly/network/NetworkAssert.java`
- `src/main/java/io/testfly/network/RecordedRequest.java`

### Denetim Soruları:
1. Chromium dışı tarayıcılarda (Firefox, Safari) CDP session başlatılmak istendiğinde zarif bir hata (graceful exception) veriliyor mu?
2. Test bittiğinde CDP listener'ları temizlenip bellek sızıntısı engelleniyor mu?

---

## 7. Thread-Safe WebDriver Yönetimi & Yaşam Döngüsü
`ThreadLocal<WebDriver>` ile tam thread izolasyonu, bulut grid ve çoklu tarayıcı oturumu desteği.

### İncelenecek Dosyalar:
- `src/main/java/io/testfly/driver/DriverManager.java`
- `src/main/java/io/testfly/driver/DriverProvider.java`
- `src/main/java/io/testfly/driver/DriverProviderRegistry.java`
- `src/main/java/io/testfly/driver/LocalChromeDriverProvider.java`
- `src/main/java/io/testfly/driver/LocalFirefoxDriverProvider.java`
- `src/main/java/io/testfly/driver/LocalSafariDriverProvider.java`
- `src/main/java/io/testfly/driver/LocalEdgeDriverProvider.java`
- `src/main/java/io/testfly/driver/RemoteDriverProvider.java`
- `src/main/java/io/testfly/driver/BrowserStackProvider.java`
- `src/main/java/io/testfly/driver/SauceLabsProvider.java`
- `src/main/java/io/testfly/session/MultiSessionManager.java`

### Denetim Soruları:
1. `DriverManager.quitDriver()` çağrıldığında `driverThreadLocal.remove()` yapılıyor mu?
2. `MultiSessionManager`: Aynı test metodu içinde 2 farklı kullanıcı oturumu (örn. alıcı ve satıcı) bağımsız pencerelerde yönetilebiliyor mu?

---

## 8. WaitEngine, Akıllı Locator & Shadow DOM
Sıfır `Thread.sleep()`, erişilebilirlik öncelikli ARIA seçiciler ve açık/kapalı Shadow DOM delme.

### İncelenecek Dosyalar:
- `src/main/java/io/testfly/wait/WaitEngine.java`
- `src/main/java/io/testfly/locator/Locator.java`
- `src/main/java/io/testfly/locator/Role.java`
- `src/main/java/io/testfly/shadow/ShadowDom.java`

### Denetim Soruları:
1. `WaitEngine`: `StaleElementReferenceException` durumlarında yeniden deneme (polling retry) mekanizması doğru çalışıyor mu?
2. `ShadowDom`: İç içe (nested) shadow-root ağaçlarında JavaScript injection ile arama yapabiliyor mu?
3. `Role.java`: W3C ARIA rollerini CSS veya XPath seçicilere dönüştürme mantığı standartlara uygun mu?

---

## 9. Web-First & Soft Assertions
Test durdurmayan yumuşak doğrulamalar ve otomatik beklemeli web assertion'ları.

### İncelenecek Dosyalar:
- `src/main/java/io/testfly/assertion/SeleniumAssert.java`
- `src/main/java/io/testfly/assertion/LocatorAssert.java`
- `src/main/java/io/testfly/assertion/PageAssert.java`
- `src/main/java/io/testfly/assertion/SoftAssertions.java`
- `src/main/java/io/testfly/assertion/SoftAssertionCollector.java`

### Denetim Soruları:
1. `SoftAssertions`: Test sonunda `assertAll()` çağrılmadığında TestNG listener'ı testi otomatik olarak fail ediyor mu?
2. `LocatorAssert.hasText()` veya `isVisible()` kontrolleri `WaitEngine` explicit timeout süresince bekliyor mu?

---

## 10. REST API İstemcisi & Doğrulamalar
Harici HTTP kütüphanesi gerektirmeyen (JDK `HttpClient` tabanlı) akıcı API test istemcisi.

### İncelenecek Dosyalar:
- `src/main/java/io/testfly/client/ApiClient.java`
- `src/main/java/io/testfly/client/ApiResponse.java`
- `src/main/java/io/testfly/api/AuthStrategy.java`
- `src/main/java/io/testfly/test/BaseApiTest.java`

### Denetim Soruları:
1. `ApiClient`: Bearer token, Basic auth ve özel header yönetimini thread-safe şekilde sunuyor mu?
2. `ApiResponse`: JSONPath sorgulama ve JSON şema doğrulama yetenekleri hatasız çalışıyor mu?

---

## 11. Görsel Regresyon Testleri
Piksel farkı hesaplama ve tolerans kontrolü.

### İncelenecek Dosyalar:
- `src/main/java/io/testfly/visual/VisualAssert.java`
- `src/main/java/io/testfly/visual/VisualTolerance.java`

### Denetim Soruları:
1. Temel ekran görüntüsü (baseline) bulunamadığında otomatik oluşturma bayrağı (`-Dtestfly.visual.updateBaselines=true`) nasıl çalışıyor?
2. Fark çıktısı (diff image) hata raporlarına görsel olarak ekleniyor mu?

---

## 12. Erişilebilirlik Testleri (Axe-Core)
WCAG 2.1 AA/AAA standartlarına uygunluk denetimi.

### İncelenecek Dosyalar:
- `src/main/java/io/testfly/accessibility/AxeRunner.java`
- `src/main/java/io/testfly/accessibility/AccessibilityAssert.java`
- `src/main/java/io/testfly/accessibility/AxeViolation.java`

### Denetim Soruları:
1. Axe-core JS kütüphanesi sayfaya nasıl enjekte ediliyor?
2. İhlaller raporlanırken HTML elementi ve çözüm önerisi net olarak veriliyor mu?

---

## 13. Web Performans & Core Web Vitals
LCP, CLS, FID ve TTFB metriklerinin Performance API üzerinden toplanması.

### İncelenecek Dosyalar:
- `src/main/java/io/testfly/performance/PerformanceCollector.java`
- `src/main/java/io/testfly/performance/PerformanceAssert.java`
- `src/main/java/io/testfly/performance/PerformanceMetrics.java`

### Denetim Soruları:
1. Tarayıcının Performance Navigation Timing API'si SPA (Single Page App) geçişlerinde doğru metrik üretiyor mu?
2. Metrik eşik değerleri aşıldığında test nasıl sonlandırılıyor?

---

## 14. Ekran & Video Kaydı
Test yürütümünün MP4 ve animated GIF formatında kaydedilmesi.

### İncelenecek Dosyalar:
- `src/main/java/io/testfly/recording/RecordingManager.java`
- `src/main/java/io/testfly/recording/Mp4Encoder.java`
- `src/main/java/io/testfly/recording/GifEncoder.java`

### Denetim Soruları:
1. `mode: retain-on-failure`: Test başarılı olduğunda geçici kareler bellekten ve diskten siliniyor mu?
2. Video kaydı yüksek çözünürlükte sistem kaynaklarını tüketiyor mu (FPS ve çözünürlük optimizasyonu)?

---

## 15. E-Posta Doğrulama İstemcileri
Aktivasyon ve OTP maillerini yakalama.

### İncelenecek Dosyalar:
- `src/main/java/io/testfly/email/MailboxClient.java`
- `src/main/java/io/testfly/email/EmailCriteria.java`
- `src/main/java/io/testfly/email/MailhogProvider.java`
- `src/main/java/io/testfly/email/MailtrapProvider.java`
- `src/main/java/io/testfly/email/OutlookProvider.java`
- `src/main/java/io/testfly/email/ImapProvider.java`

### Denetim Soruları:
1. E-posta gelene kadar bekleme (polling wait) döngüsü `WaitEngine` mantığı ile uyumlu mu?
2. Regex ile e-posta içerisinden link veya OTP kodu çıkarma yardımcıları güvenli mi?

---

## 16. Veritabanı İstemcisi & Assertions
Test öncesi ve sonrası DB veri doğrulama.

### İncelenecek Dosyalar:
- `src/main/java/io/testfly/db/DbClient.java`
- `src/main/java/io/testfly/db/DbConnectionFactory.java`
- `src/main/java/io/testfly/db/DbQuery.java`
- `src/main/java/io/testfly/db/DbAssertException.java`

### Denetim Soruları:
1. Veritabanı bağlantı havuzu (connection pooling) sızıntıları engelliyor mu?
2. Parametreli sorgular (SQL Injection koruması) destekleniyor mu?

---

## 17. Dinamik Test Verisi Yükleyicileri
CSV ve Excel dosyalarından veri okuma.

### İncelenecek Dosyalar:
- `src/main/java/io/testfly/testdata/TestData.java`
- `src/main/java/io/testfly/testdata/TestDataLoader.java`
- `src/main/java/io/testfly/testdata/ExcelDataReader.java`
- `src/main/java/io/testfly/testdata/TestDataStore.java`

### Denetim Soruları:
1. Excel (Apache POI) bağımlılığı opsiyonel mi (`<optional>true</optional>`), POI olmadan CSV çalışabiliyor mu?
2. `@DataProvider` entegrasyonu paralel testleri destekliyor mu?

---

## 18. Flakiness Analizi & Karantina Sistemi
Test kararsızlık skorlama ve karantinaya alma.

### İncelenecek Dosyalar:
- `src/main/java/io/testfly/flakiness/FlakinessAnalyzer.java`
- `src/main/java/io/testfly/flakiness/FlakinessScore.java`
- `src/main/java/io/testfly/quarantine/QuarantineLoader.java`

### Denetim Soruları:
1. `testfly-quarantine.yml` dosyasında listelenen testler çalıştırılmadan sessizce skip ediliyor mu?
2. Flakiness geçmişi (`target/metrics-history/`) geçmiş koşularla nasıl birleştiriliyor?

---

## 19. Zaman & Tarayıcı Saati Manipülasyonu
JavaScript saatini (`Date.now`) dondurma ve zaman yolculuğu.

### İncelenecek Dosyalar:
- `src/main/java/io/testfly/clock/TestClock.java`
- `src/main/java/io/testfly/test/support/ClockSupport.java`

### Denetim Soruları:
1. Sayfa yenilendiğinde (refresh) saatin donuk kalması `Page.addScriptToEvaluateOnNewDocument` ile garanti edilmiş mi?

---

## 20. Tarayıcı Yardımcıları & Donanım Emülasyonu
Konsol hataları, pano, coğrafi konum ve cihaz emülasyonu.

### İncelenecek Dosyalar:
- `src/main/java/io/testfly/browser/ConsoleErrorCollector.java`
- `src/main/java/io/testfly/browser/ClipboardHelper.java`
- `src/main/java/io/testfly/browser/GeoLocation.java`
- `src/main/java/io/testfly/browser/DeviceEmulator.java`
- `src/main/java/io/testfly/browser/StorageHelper.java`

### Denetim Soruları:
1. `failOnConsoleErrors: true` ayarında JavaScript SEVERE hataları testi beklenen şekilde durduruyor mu?
2. `ClipboardHelper`: Headless modda pano okuma/yazma izinleri nasıl aşılıyor?

---

## 21. Raporlama & Entegrasyonlar
HTML, JUnit XML, Allure, ReportPortal, TestRail ve Xray.

### İncelenecek Dosyalar:
- `src/main/java/io/testfly/reporting/HtmlReportGenerator.java`
- `src/main/java/io/testfly/reporting/AllureReportAdapter.java`
- `src/main/java/io/testfly/reporting/reportportal/ReportPortalReportAdapter.java`
- `src/main/java/io/testfly/testmanagement/TestRailClient.java`
- `src/main/java/io/testfly/testmanagement/XrayClient.java`

### Denetim Soruları:
1. `HtmlReportGenerator`: Test adımları, ekran görüntüleri ve AI analizleri tek bir statik HTML dosyasında doğru render ediliyor mu?
2. Allure ve ReportPortal SPI adaptörleri opsiyonel bağımlılıklar yüklü olmadığında çökme yaşatıyor mu?

---

## 22. CI / Quality Gate Mekanizmaları
Test başarı oranı eşiği ve flaky test limiti.

### İncelenecek Dosyalar:
- `src/main/java/io/testfly/ci/CiThresholdEnforcer.java`
- `src/main/java/io/testfly/ci/CiEnvironmentDetector.java`
- `src/main/java/io/testfly/listeners/RetryAnnotationTransformer.java`
- `src/main/java/io/testfly/listeners/RetryListener.java`

### Denetim Soruları:
1. `failOnPassRateBelow: 95.0` kuralı sağlandığında build exit code `1` ile mi sonlanıyor?
2. `@Retryable` ile tekrar edilen testlerde pass rate hesaplaması nasıl yapılıyor?

---

## 23. Cucumber BDD & JUnit 5 Köprüleri
Alternatif test koşucuları için TestFly altyapısı.

### İncelenecek Dosyalar:
- `src/main/java/io/testfly/cucumber/BaseCucumberSteps.java`
- `src/main/java/io/testfly/cucumber/BaseCucumberTest.java`
- `src/main/java/io/testfly/junit5/BaseJUnit5Test.java`
- `src/main/java/io/testfly/junit5/TestFlyExtension.java`

### Denetim Soruları:
1. Cucumber adımlarında PicoContainer enjeksiyonu olmadan `BaseCucumberSteps` üzerinden thread-safe driver erişimi sağlanıyor mu?
2. JUnit 5 `@RegisterExtension` yaşam döngüsü TestNG ile tam özellik eşliğine (feature parity) sahip mi?

---

## 24. SPI Eklenti & Yaşam Döngüsü Kancaları
Kullanıcıların framework'e kendi provider ve hook'larını eklemesi (ServiceLoader).

### İncelenecek Dosyalar:
- `src/main/java/io/testfly/extension/TestFlyPlugin.java`
- `src/main/java/io/testfly/extension/PluginRegistry.java`
- `src/main/java/io/testfly/hooks/ExecutionHook.java`
- `src/main/java/io/testfly/hooks/HookRegistry.java`

### Denetim Soruları:
1. `minFrameworkVersion()` kontrolü eski plugin'lerin uyumsuzluk yaratmasını engelliyor mu?
2. `ExecutionHook.onTestFailure()` kancası diğer tüm reporting kancalarından önce mi tetikleniyor?

---

## 25. Konfigürasyon & Dokümantasyon Tutarlılığı
POJO nesneleri ile YAML ve doküman eşleşmesi.

### İncelenecek Dosyalar:
- `src/main/java/io/testfly/config/TestFlyConfig.java`
- `src/main/java/io/testfly/config/ConfigurationLoader.java`
- `testfly.yml`
- `docs-site/docs/configuration.md`

### Denetim Soruları:
1. `TestFlyConfig.java` içindeki tüm getter/setter alanları ile `docs-site/docs/configuration.md` dokümanındaki YAML alanları harfiyen uyuşuyor mu?
2. `${VAR}` ortam değişkeni enjeksiyonu tüm tiplerde (boolean, int, String) doğru tip dönüşümü yapıyor mu?

---

## 26. Bağımlılık İzolasyonu & Transitivite
Kullanıcının projesine sızabilecek gereksiz bağımlılıkların önlenmesi ve Maven Central yayınlama sözleşmesi.

### İncelenecek Dosyalar:
- `pom.xml`

### Denetim Soruları:
1. Cucumber (`cucumber-java`), JUnit 5 (`junit-jupiter-api`), Apache POI (`poi-ooxml`), Jakarta Mail ve `json-schema-validator` gibi opsiyonel entegrasyonlar `<optional>true</optional>` olarak işaretlenmiş mi?
2. `central-publishing-maven-plugin` ve `maven-gpg-plugin` yapılandırmalarında eksik veya hardcoded credential sızıntısı var mı?

---

## 27. Framework Başlatma Motoru (Single Point of Boot)
TestFly'ın tüm alt sistemlerini (konfigürasyon, `.env`, healing cache, plugin'ler, video kaydedici) ayağa kaldıran ana merkez.

### İncelenecek Dosyalar:
- `src/main/java/io/testfly/lifecycle/FrameworkBootstrap.java`

### Denetim Soruları:
1. `FrameworkBootstrap.init()` sırasında bileşenlerin başlatılma sırası mantıksal olarak doğru mu (örn. önce konfigürasyon, sonra provider'lar)?
2. `.env` dosyası bulunamadığında veya bozuk bir healing cache olduğunda framework sessizce çöküyor mu yoksa kullanıcı dostu bir hata mı üretiyor?

---

## 28. Bellek & State Sızıntısı (Thread Leakage)
Paralel testlerde ve testler arası geçişte veri izolasyonu.

### İncelenecek Dosyalar:
- `src/main/java/io/testfly/internal/TestFlyContext.java`
- `src/main/java/io/testfly/context/SuiteContext.java`

### Denetim Soruları:
1. Test sonlandığında (`tearDown`) tüm `ThreadLocal` değişkenler kesin olarak temizleniyor mu (`remove()`)?
2. Static haritalarda (Map) veya listelerde zamanla biriken referanslar bellek sızıntısına (OutOfMemoryError) neden olabilir mi?

---

## 29. CI/CD Pipeline Yapılandırması
GitHub Actions ve Jenkins üzerinde otomasyon hattı.

### İncelenecek Dosyalar:
- `.github/workflows/testfly-ci.yml`
- `.github/workflows/release.yml`
- `ci/Jenkinsfile`

### Denetim Soruları:
1. Headless tarayıcı çalıştırma parametreleri CI ortamlarında stabil mi?
2. Test raporları (HTML, Allure sonuçları, JUnit XML, metrics JSON) testler patlasa dahi (`always()`) arşivleniyor mu?

---

## 30. Dokümantasyon Sitesi Derleme & Link Sağlığı
Docusaurus 3 tabanlı dokümantasyon sitesinin kod ile senkronizasyonu.

### İncelenecek Dosyalar:
- `docs-site/docusaurus.config.js`
- `docs-site/docs/`
- `docs-site/i18n/tr/`

### Denetim Soruları:
1. Dokümanlarda kırık iç bağlantı (broken markdown link) veya eksik rota var mı?
2. İngilizce (`docs/`) ve Türkçe (`i18n/tr/`) doküman sayfaları ve başlıkları birebir örtüşüyor mu?

---

## 31. Statik Kod Analizi & Kalite Kapıları
Kod standartları, güvenlik açıkları ve eşzamanlılık (concurrency) denetimi.

### İncelenecek Dosyalar:
- `pom.xml` (`quality` profili: JaCoCo, SpotBugs, Checkstyle, PMD)

### Denetim Soruları:
1. SpotBugs veya Checkstyle kurallarında gözden kaçan thread-safety veya mutable static field uyarıları var mı?
2. Kod kapsamı (JaCoCo coverage) eşik değerleri yeterli mi?

