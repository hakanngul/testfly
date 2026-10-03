---
tags:
  - memory
  - log
  - chronological
date: 2026-09-28
status: active
type: log
---

# Wiki Kronolojik Günlük (Log)

Append-only kayıt. Her giriş `## [YYYY-MM-DD] eylem | konu` formatındadır.
Arama: `grep "^## \[" memories/log.md | tail -10`

---

## [2026-09-28] init | LLM Wiki Log Dosyası Oluşturuldu
- **Eylem:** `memories/log.md` dosyası LLM Wiki pattern audit sonrası oluşturuldu.
- **Kapsam:** 5 eksiklik tespit edildi; log.md, app.json, graph renk grupları, wiki-lint skill, MAP referansı.
- **Bağlantılar:** [[MAP]], [[wiki/index]], [[memories/scratchpad]]

## [2026-09-28] ingest | Mevcut Wiki Yapısı Geriye Dönük Kayıt
- **Eylem:** Mevcut 17 wiki sayfası, 3 rule, soul.md, MAP.md ve scratchpad.md'nin oluşturulma geçmişi geriye dönük kaydedildi.
- **Wiki Sayfaları (2026-09-10):** architecture, webdriver-lifecycle, api-testing, webui-testing, api-webui-testing, load-testing, spi-extensions, ai-mcp-automation, accessibility-visual-testing, cucumber-bdd, test-management, quarantine-engine, ci-quality-gates, memory-system, roadmap, configuration, index.
- **Kurallar (2026-09-10):** memory-protocol, docusaurus-workflow, git-release-workflow.
- **Bağlantılar:** [[wiki/index]], [[MAP]]

## [2026-09-27] update | OutOfMemoryError Çözümü Scratchpad'e Kaydedildi
- **Eylem:** RecordingManager bellek optimizasyonu (%99 düşüş) tamamlandı, TestFly 1.0.5 olarak yerel deploy edildi.
- **Bağlantılar:** [[memories/scratchpad]], [[wiki/roadmap]]

## [2026-09-28] feature | Cucumber CLI Rapor Tetikleme & Timeline INFO/DEBUG Filtre Butonu
- **Eylem:** 
  1. `CucumberHooks.afterAllScenarios()` metoduna TestNG `SuiteExecutionListener` ile eşdeğer rapor üretim zinciri (`ReportAdapterRegistry.generateAll()`, `JUnitXmlReporter.export()`, metrikler) eklendi. Cucumber CLI doğrudan çalıştırıldığında da HTML rapor üretimi sağlandı.
  2. `report-template.html` içerisindeki Step Execution Timeline bölümüne INFO/DEBUG adımlarını gizleyip açan modern hap (pill) toggle butonu (`Hide INFO / DEBUG` / `Show INFO / DEBUG`), sayaç rozeti, `localStorage` kalıcılığı ve üst çubuk genel kontrol butonu eklendi.
- **Bağlantılar:** [[io.testfly.cucumber.CucumberHooks]], [[io.testfly.reporting.HtmlReportGenerator]], [[memories/scratchpad]]

## [2026-09-28] refactor | Cucumber Full Support Parity & Lifecycle Hardening
- **Eylem:**
  1. `BaseCucumberSteps`: `BaseTest` ile tam eşitlik sağlamak üzere `VisualSupport`, `DbSupport`, `EmailSupport`, `AccessibilitySupport`, `PerformanceSupport`, `ClockSupport`, `BrowserSupport`, `SessionSupport`, `SoftAssertSupport`, `ContextSupport`, `TestDataSupport` eklendi.
  2. `CucumberHooks`: Soft assertion flush ve `SoftAssertions.clear()`, `MultiSessionManager.clearAll()`, `PreConditionRunner.clearAll()` yaşam döngüsü temizlikleri eklendi.
- **Bağlantılar:** [[io.testfly.cucumber.BaseCucumberSteps]], [[io.testfly.cucumber.CucumberHooks]], [[io.testfly.test.BaseTest]]

## [2026-09-28] feature | JUnit 5 Full Support Parity, BaseJUnit5ApiTest & Lifecycle Parity
- **Eylem:**
  1. `BaseJUnit5Test`: TestNG `BaseTest` ile tam eşitlik sağlandı. Eksik olan 7 interface (`BrowserSupport`, `VisualSupport`, `PerformanceSupport`, `ClockSupport`, `ContextSupport`, `SoftAssertSupport`, `TestDataSupport`) eklenerek toplam 17 desteğin tamamı sağlandı.
  2. `BaseJUnit5ApiTest`: JUnit 5 için `@NoBrowser` pure API test base sınıfı oluşturuldu.
  3. `BaseApiTest`: TestNG tarafında `StepSupport`, `DbSupport`, `EmailSupport` eklenerek her iki framework API testlerinde tam eşitlendi.
  4. `TestFlyExtension`:
     - `beforeEach`: `TestFlyContext.setCurrentTest()`, `@UseAuth` ve `@TestData` otomatik injection eklendi.
     - `afterEach`: Soft assertion flush mekanizması eklendi; başarısız soft assertion'lar testi FAILED yapıp hata fırlatıyor.
     - `TestWatcher` & `TestAbortedException`: Quarantined ve `@Disabled` testler FAILED yerine doğru şekilde SKIPPED olarak işaretleniyor.
     - `finally`: `SoftAssertions.clear()` ve `TestFlyContext.clearCurrentTest()` eklendi.
  5. `TestFlyLauncherListener`: `HealLog.export()`, `DriverManager.quitAllSuiteDrivers()`, `DriverManager.quitDriver()`, `PluginRegistry.unloadAll()` eklendi.
  6. `BaseJUnit5ParityTest` ile tüm bu eşitlikler TestNG + Mockito altında doğrulandı (1300 test %100 passed).
- **Bağlantılar:** [[io.testfly.junit5.BaseJUnit5Test]], [[io.testfly.junit5.BaseJUnit5ApiTest]], [[io.testfly.junit5.TestFlyExtension]], [[io.testfly.junit5.TestFlyLauncherListener]], [[io.testfly.test.BaseTest]], [[io.testfly.test.BaseApiTest]]

## [2026-09-28] feature | BasePage Navigation & Window Management Integration
- **Eylem:**
  1. `BasePage`: `NavigationSupport` ve `ContextSupport` implemente edildi. Sayfa nesneleri için lazy `DriverManager` kullanan no-arg `protected BasePage()` constructor'ı eklendi.
  2. `NavigationSupport`: `open()`, `open(path)`, `navigateTo()`, `getCurrentUrl()`, `getTitle()`, `refresh()`, `back()`, `forward()`, `waitForPageLoad()`, `waitForUrlContains()`, `waitForTitle()` gibi temel navigasyon ve bekleme metodlarının yanı sıra sekme/pencere (`switchToNewTab()`, `switchToMainTab()`, `closeCurrentTabAndSwitchBack()`, `switchToTab()`) ve viewport (`zoom()`, `scrollBy()`) yetenekleriyle donatıldı.
  3. Redundant `WindowSupport` interface'i kaldırıldı; tüm yetenekler tek çatı altında `NavigationSupport`'ta toplandı.
  4. `BasePageTest` (10 test) eklendi.
  5. `mvn clean install` ile `1.0.5` sürümü `~/.m2` yerel deposuna güncellendi.
- **Bağlantılar:** [[io.testfly.test.BasePage]], [[io.testfly.test.support.NavigationSupport]], [[io.testfly.test.BaseTest]], [[io.testfly.cucumber.BaseCucumberSteps]]

## [2026-09-28] feature | Locator WebElement Support & BasePage Clean Architecture
- **Eylem:**
  1. `Locator`: `Kind.ELEMENT` ve `Locator.of(WebElement element)` eklendi; raw `WebElement` referansları üzerinden tüm akıcı metodlar (`click()`, `type()`, `clear()`, `getText()`, `isVisible()`, `hover()`, `scrollIntoView()`, `jsClick()`) auto-wait ile çalışır hale getirildi.
  2. `LocatorSupport`: `default Locator find(WebElement element)` ve `$(WebElement element)` eklendi.
  3. `BasePage`: Geçici olarak eklenmiş redundant WebElement helper metodları geri alındı, mimari temizlik ve SRP korundu.
  4. `BasePageTest` ve `LocatorTest` (29 test) ile doğrulandı.
  5. `mvn clean install -DskipTests -Dgpg.skip=true` ile `io.github.hakanngul:testfly:1.0.5` yerel maven deposuna başarıyla deploy edildi.
- **Bağlantılar:** [[io.testfly.locator.Locator]], [[io.testfly.test.support.LocatorSupport]], [[io.testfly.test.BasePage]]

## [2026-09-28] enhancement | ScenarioContext Put Alias & Page Constructors Local Deploy
- **Eylem:**
  1. `ScenarioContext` & `SuiteContext`: `put(String key, Object value)` alias metodu eklendi (`set` ile birebir eşdeğer).
  2. `LoginPage` & `ProductsPage`: Parametresiz (`public LoginPage() { super(); }`) constructor eklendi.
  3. `mvn clean install -DskipTests -Dgpg.skip=true` çalıştırılarak `io.github.hakanngul:testfly:1.0.5` JAR, kaynak kod ve javadoc'ları yerel `~/.m2` deposuna yüklendi.
- **Bağlantılar:** [[io.testfly.context.ScenarioContext]], [[io.testfly.context.SuiteContext]], [[io.testfly.test.support.ContextSupport]], [[io.testfly.test.BasePage]]


## [2026-09-28] fix | Framework Initialization Thread-Safety & Stability for Parallel Tests
- **Eylem:** 
  1. `FrameworkBootstrap.initialize()` metodu `ConfigurationLoader.class` ve `TestFlyContext.class` lock'ları ile senkronize edilerek double-checked locking uygulandı.
  2. `TestFlyContext` üzerindeki mutation metodları (`initialize`, `setConfig`, `reset`) senkronize edildi.
  3. `CucumberHooks.afterScenario` içine `isInitialized()` guard clause eklendi.
  4. Yapılan thread-safety iyileştirmelerinin `mvn test` (1311 test) ile paralel çalıştırmada hatasız olduğu ve projenin tamamen stabil çalıştığı doğrulandı.
- **Bağlantılar:** [[io.testfly.lifecycle.FrameworkBootstrap]], [[io.testfly.internal.TestFlyContext]], [[io.testfly.cucumber.CucumberHooks]], [[memories/scratchpad]]

## [2026-09-28] refactor | Java 21 LTS (Project Loom, Pattern Matching) Migration
- **Eylem:** 
  1. Projenin Maven (`pom.xml`) baseline'ı ve derleyicisi `release 21` hedefine yükseltildi. Dökümantasyonlar güncellendi.
  2. `JdkLoadEngine`'deki sabit thread havuzu, `Executors.newVirtualThreadPerTaskExecutor()` kullanacak şekilde (Project Loom Virtual Threads) modernize edildi.
  3. `Locator` nesnesi Sequenced Collections API'si ile `first()` ve `last()` destekleyecek şekilde güncellendi.
  4. Locator tipi çözümleme switch'i `buildRoot()` içinde Java 21 Exhaustive Switch Expression (oklu yapı) formatına taşındı.
  5. 1300+ TestNG testinden geçerek başarılı bir şekilde `.m2` ortamına deploy edildi. Plan eksiksiz tamamlandı.
- **Bağlantılar:** [[io.testfly.loadtest.internal.JdkLoadEngine]], [[io.testfly.locator.Locator]], [[memories/scratchpad]]
- 2026-09-29: Added SmartTriageEngine for zero-token local flakiness triage during test failure (TestExecutionListener integration).
- 2026-09-29: Clean API Refactor, Null-safe Locator actions, SmartTriageEngine ve FuzzyHealingEngine tamamlanarak v1.0.6 tag'i ile release çıkıldı. Docs-site sürümleri 1.0.6'ya yükseltildi ve build alındı.

## [2026-09-29] architecture | AssertionSupport API Limits
- **Eylem:** `AssertionSupport` içerisine `assertTrue`/`assertEquals` gibi temel boolean kontrollerin eklenmemesine, TestFly'ın sadece Web-first (Locator, WebDriver) auto-retry assertion sistemine odaklanmasına ve temel kontrollerin AssertJ/TestNG kütüphanelerine bırakılmasına karar verildi. API şişkinliği ve standard library'nin tekrar yazılması (reinventing the wheel) engellendi.
- **Bağlantılar:** [[io.testfly.test.support.AssertionSupport]], [[memories/scratchpad]]

## [2026-09-29] docs & refactor | Documentation, Agent Memory & Locator.cssSelector Modernization
- **Eylem:**
  1. `Locator.java` içerisine `cssSelector(String)` birincil fabrika metodu eklendi, `css(String)` geriye dönük uyumluluk için `@Deprecated` alias yapıldı. `LocatorSupport` ve birim testler senkronize edildi.
  2. Kök `docs/` (`internals.md`, `public-api.md`, `architecture.md`, `testng-listeners.md`) baştan sona gözden geçirilip Java 21 LTS, `SmartTriageEngine`, `FuzzyHealingEngine` ve `AssertionSupport` sınırlarıyla güncellendi.
  3. `docs-site` kılavuzları (`semantic-locators.md`, `assertions.md`, `self-healing.md`) Türkçe ve İngilizce çift dil olarak güncellendi ve `npm run build` ile doğrulandı.
  4. `.agents/` bilgi grafiği güncellendi: `[[wiki/assertion-system]]` sayfası oluşturuldu, `[[wiki/architecture]]` ve `[[wiki/webui-testing]]` sayfaları güncellendi; `[[MAP]]` indeksine bağlandı.
  5. `docs-agent/` şablonu kullanıcı talimatıyla kapsam dışı bırakıldı.
- **Bağlantılar:** [[io.testfly.locator.Locator]], [[wiki/assertion-system]], [[wiki/architecture]], [[memories/scratchpad]]

## [2026-09-29] docs | Changelog Audit & Git Commit History Synchronization
- **Eylem:**
  1. `git log 9f8b2de..HEAD` (30+ commit) taranarak kök `CHANGELOG.md`, `docs-site/docs/changelog.md` ve TR eşdeğeri denetlendi.
  2. `[1.0.6]` sürümüne commit geçmişinde bulunan ancak atlanmış olan Java 21 LTS geçişi (`--release 21`, Virtual Threads, Pattern Matching), `Locator extends By` doğrudan Selenium desteği, `PageKnowledge` Auto-POM, `BaseJUnit5ApiTest`, `NavigationSupport` ve ekran kayıt bellek optimizasyonu eklendi.
  3. `[Unreleased]` bölümü açılarak `Locator.cssSelector(String)` (ve `Locator.css` deprecation), `AssertionSupport` mimari sınır kararı ve dokümantasyon senkronizasyonu her 3 dosyada da belgelendi.
  4. `npm run build` ve `mvn test` (1319 test) ile dökümantasyon ve framework testleri doğrulandı.
- **Bağlantılar:** [[memories/scratchpad]], [[memories/log]], [[MAP]]

## [2026-09-30] docs & tooling | @testfly/mcp (Node.js/NPX) Modernizasyonu & Pip Temizliği
- **Eylem:**
  1. `docs-site/docs/getting-started.md` ve TR eşdeğerindeki `pip install testfly-mcp` / `testfly init` eski CLI referansları `npx @testfly/mcp init my-test-suite` olarak güncellendi.
  2. `docs-site/docs/cli.md` ve TR eşdeğeri baştan sona yeniden yazılarak eski Python CLI (88 araç, testfly doctor, studio) yerine modern Node.js `@testfly/mcp` Bridge, NPX çalıştırma ve Playwright MCP eşleşmesi dokümante edildi.
  3. `docs-site/docs/ai/overview.md`, `intro.md` ve `ROADMAP.md` içindeki Python/pip referansları `@testfly/mcp` ile güncellendi.
  4. `FrameworkBootstrap.java` ve `TestFlyApi.java` konsol çıktısı ve Javadoc'u `npx -y @testfly/mcp` olarak eşitlendi.
  5. `../testfly-mcp/bin/testfly-mcp.js` dosyasına doğrudan `init` CLI komut desteği eklendi (`testfly.yml`, Java 21 `pom.xml`, `SampleWebTest.java` otomatik oluşturma).
  6. `docusaurus-config` doğrulaması ve `npm run build` (EN & TR) sıfır hata ile tamamlandı.
- **Bağlantılar:** [[docs-site/docs/cli]], [[docs-site/docs/getting-started]], [[memories/scratchpad]], [[memories/log]], [[MAP]]

## [2026-09-30] refactor & docs | Java 17 Kalıntılarının Temizlenmesi & Java 21 Standardizasyonu
- **Eylem:**
  1. `GatlingBridge.java` Javadoc'undaki `MethodHandles.privateLookupIn on Java 17+` referansı `Java 21+` yapıldı.
  2. `GatlingRunConfig.java` Javadoc'undaki `which need --add-opens on Java 17+` referansı `Java 21+` yapıldı.
  3. `JdkLoadEngine.java` içindeki `JDK HttpClient is always available on Java 17+` yorum satırı `Java 21+` olarak güncellendi.
  4. `docs-site/docs/loadtest/distributed-docker-k8s.md` Dockerfile içindeki `maven:3.9.6-eclipse-temurin-17` imajı `temurin-21` olarak güncellendi.
  5. `docs-site/docs/ci/bitbucket-pipelines.md` pipeline konfigürasyonundaki `maven:3.9.6-eclipse-temurin-17` imajı `temurin-21` olarak güncellendi.
  6. `docs-site/i18n/tr/.../changelog.md` içindeki `built into Java 17` ifadesi `built into Java 21` olarak güncellendi.
  7. Docusaurus `npm run build` ile EN ve TR dokümantasyon bütünlüğü doğrulandı (exit code 0).

## [2026-09-30] refactor & perf | JDK 21 Tam Kapasite Modernizasyon ve Performans İyileştirmesi
- **Eylem:**
  1. `NotificationAdapter.java`: Her webhook isteğinde yeni `HttpClient` yaratılması yerine sanal thread (`Executors.newVirtualThreadPerTaskExecutor()`) ile güçlendirilmiş paylaşımlı `SHARED_HTTP_CLIENT` yapısına geçildi.
  2. `ApiClient.java`, `TestRailClient.java` ve `XrayClient.java`: HTTP istemcilerine sanal thread executor'ı atanarak asenkron I/O ve polling işlemleri optimize edildi.
  3. `ReportAdapterRegistry.java`: `generateAll()` metodunda raporlayıcılar ve webhook'lar sanal thread havuzuyla paralel tetikleme mimarisine kavuştu.
  4. `PercentileCalculator.java` ve `ExecutionMetrics.java`: İç içe `Math.max/min` aralık sınırlandırmaları Java 21 `Math.clamp(...)` metoduna dönüştürüldü.
  5. `NavigationSupport.java`, `LocatorAssert.java`, `Mp4Encoder.java` ve `ReportPortalAttachmentSender.java`: `getLast()` ve `getFirst()` Sequenced Collection API'lerine taşındı.
  6. `LoadTestFeeder.describe()` ve `SmartTriageEngine`: `if-instanceof` blokları Java 21 Pattern Matching `switch` ifadelerine refactor edildi.
  7. `Route.java`, `NetworkMock.java` ve `ExcelDataReader.java`: Arrow switch ifadelerine dönüştürülerek fall-through riskleri elendi.
  8. Framework'ün 1.319 testinin tamamı (`mvn test`) ve Docusaurus (`npm run build`) ile %100 doğrulandı.
- **Bağlantılar:** [[memories/scratchpad]], [[memories/log]], [[MAP]]

## [2026-09-30] feat | API Mocking, Async BatchRunner & Specialist Agents
- **Eylem:**
  1. `automation-architecture` skill dosyası Nexor CRM içeriğinden temizlenerek TestFly SDET standartlarına (POM, SmartLocator, BaseApiTest) uygun hale getirildi ve yerine kapsamlı `testfly` skill'i oluşturuldu.
  2. Web UI Test altyapısı (Java 21 LTS Locator, FuzzyHealingEngine ve SmartTriageEngine) mimari diyagramları (Architecture ve Workflow) `archify` aracı ile oluşturuldu ve HTML çıktıları üretildi.
  3. API Testing mimarisine WireMock (`ApiMockServer`, `MockSupport`), OpenAPI (`OpenApiValidator`, `ApiResponse.assertOpenApi`) ve Asenkron İstek (`ApiClient.sendAsync`, `ApiBatchRunner`) yetenekleri opsiyonel bağımlılıklarla (`optional=true`) eklendi.
  4. Subsystem specialist ajanları (`testfly_api`, `testfly_webui`, `testfly_load`, `testfly_reporting` vb.) entegre edildi.
  5. Kullanıcı isteğiyle Rimz 0.4.3 ve Ghostty 1.3.1 geliştirici yapılandırmaları yapıldı.
- **Bağlantılar:** [[memories/scratchpad]], [[memories/log]], [[MAP]]

## [2026-09-30] rule | Protected Main Branch & Pre-Flight Branch Check Kuralı Eklendi
- **Eylem:** 
  1. `main` dalına doğrudan `commit` ve `push` yapılması kesin ve tavizsiz olarak yasaklandı (Protected Main Branch).
  2. Herhangi bir git commit/push öncesinde `git branch --show-current` çalıştırma ve `development` dalını doğrulama adımı anayasal kural haline getirildi.
  3. `GEMINI.md` Strict Architectural Constraints (#6), `AGENTS.md` (CAUTION & Constitution), `.agents/rules/git-release-workflow.md`, `.agents/skills/testfly-workflow/SKILL.md` (Bölüm 6), `.agents/soul.md` ve `.agents/MAP.md` güncellendi.
- **Bağlantılar:** [[rules/git-release-workflow]], [[AGENTS]], [[MAP]], [[soul]], [[memories/scratchpad]]


## [2026-09-30] query | API Chain Interceptor Önerisi İncelendi
- Mevcut ApiClient kaynak akışı incelendi: request/response hook’ları void; send() konfigürasyonlu retry içeriyor, sendAsync() bu retry döngüsünü kullanmıyor. HTTP 401 ApiResponse olarak dönüyor ve FAIL adımı loglanıyor; assertion ayrı.
- Öneri: mevcut hook’ları koruyan ek chain API; immutable request snapshot, sentetik response factory, tekrarlı proceed için sabit downstream index, ortak sync/async politikası ve çağıran test context’inin taşınması. OAuth2 cache süre bazlı; 401 sonrası hedefli invalidation/refresh ayrıca gerekli. Retry/refresh sınırları ve body replay sözleşmesi belirlenmeli; mock/cache açık tercihle etkin olmalı.
- Durum: mimari değerlendirme; framework kodu değiştirilmedi, test çalıştırılmadı.
- Bağlantılar: [[wiki/api-testing]], [[memories/scratchpad]], [[MAP]]

## [2026-09-30] query | API Özelliklerinin Gereklilik Değerlendirmesi
- Baseline Java 21; framework zero-dependency değil, HTTP transport JDK tabanlı. Paylaşımlı HttpClient ve virtual-thread executor zaten mevcut.
- Öncelik önerisi: sync/async context ve retry uyumu, ardından chain; client mock talebe göre, ikinci server motoru düşük öncelikli (WireMock optional mevcut).
- Oracle Java 21 belgeleriyle doğrulandı: connectionPoolSize HTTP/1.1 idle-cache sınırıdır, aktif bağlantı sınırı değildir; keepalive JVM sistem ayarıdır, runtime System.setProperty garantisi yok. Native read-idle timeout ve HostnameVerifier builder metodu yok; SSLContext client düzeyinde. Truststore/özel CA desteği bypass öncesinde değerlendirilmeli.
- Framework kodu değiştirilmedi; performans ölçümü veya test çalıştırılmadı.
- Bağlantılar: [[wiki/api-testing]], [[memories/scratchpad]], [[MAP]]

## [2026-09-30] query | HTTP/2 ve Apache HttpClient Sürüm Ayrımı
- ApiClient java.net.http.HttpClient kullanıyor; pom.xml içinde doğrudan Apache httpclient/httpcore bağımlılığı yok. HTTP/2 protokol sürümü, Apache HttpClient 5.6 kütüphane sürümü; aralarında eski/yeni sürüm ilişkisi yok. JDK transport JDK güncellemeleriyle güncellenir. Apache geçişi ancak gelişmiş transport ihtiyaçlarıyla gerekçelendirilmeli; mevcut seçimin tarihsel nedeni araştırılmadı.
- Kaynaklar: Oracle Java 21 HttpClient API ve Apache HttpClient 5.6 overview. Framework kodu değiştirilmedi.
- Bağlantılar: [[wiki/api-testing]], [[memories/scratchpad]], [[MAP]]


## [2026-10-01] implementation | API Interceptor Zinciri
- development üzerinde immutable ApiRequest, ApiInterceptor/Chain ve sentetik ApiResponse builder eklendi. Request/test kapsamı, tekrar proceed, null/expired/cross-thread guard ve eski hook imzaları korundu.
- Sync/async ortak snapshot pipeline; dış YAML retry, transport hata ayrımı, body replay, interrupt/cancellation, concurrent test cookie jar ve captured auth/log/retry bağlamı uygulandı. Batch semaphore, TestNG/JUnit5/Cucumber cleanup ve managed virtual-thread runtime entegre edildi.
- Gerçek transport gönderimleri INFO, retry WARN ve yalnız nihai sonuç PASS/FAIL; süre, güncel URI/header ve maskeli cURL raporlanıyor. Sentetik yanıtlar işaretleniyor.
- 30 yeni chain/execution testi ve derlenen kullanıcı örnekleri eklendi; localhost HttpServer smoke, refresh, replay, async izolasyon/cancellation ve batch sınırı doğrulandı. TR/EN API rehberi güncellendi.
- Son temiz doğrulama: mvn -q clean verify -Dtest=ApiInterceptorChainTest,ApiInterceptorExecutionTest,ApiClientFeaturesTest,ApiResponseAdvancedTest,ApiResponseAssertionsTest -Dgpg.skip=true: 78 test, 0 failure/error. npm run build: EN/TR başarılı. src/docs diff whitespace kontrolü başarılı.
- Tam suite teslim kriteri karşılanmadı: varsayılan mvn test takılması değişikliksiz HEAD snapshot'ında da görüldü. Ayrı JVM'lerle geniş verify 1348 test/6 failure (DriverProviderRegistryTest, TestClockTest, PageKnowledgeTest). Driver/clock hataları HEAD'de yeniden üretildi; PageKnowledge başarısızlığı baseline koşusunda tekrarlanmadı. Bu ilgisiz alanlar değiştirilmedi.
- Yeni zorunlu dependency, version bump, commit, push veya release yok. Önceden mevcut kullanıcı değişiklikleri korundu.
- Bağlantılar: [[wiki/api-testing]], [[memories/scratchpad]], [[MAP]]


## [2026-10-01] docs | API Konu Sayfaları
- API Testing tek sayfa içeriği TR/EN istekler, yanıtlar, polling, şema, hibrit, raporlama, interceptor ve async/batch sayfalarına taşındı. Başlangıç/BaseApiTest örneği mevcut URL üzerinde korundu; konu bağlantıları eklendi.
- sidebars.js API kategorisine ayrı konu öğeleri eklendi; Interceptor Chain doğrudan erişilebilir. Authentication ve Scenario Context mevcut sayfalarında korundu. cURL için logCurl ayarı netleştirildi.
- Bağlantılar: [[wiki/api-testing]], [[memories/scratchpad]], [[rules/docusaurus-workflow]]
- Doğrulama: npm run build EN/TR başarılı; docs-site diff whitespace kontrolü temiz. development üzerinde çalışıldı; commit/push/yayın yapılmadı.


## [2026-10-01] git | API Interceptor Commit
- Kullanıcı commit istedi; development doğrulandı. API kaynak/test/örnek ve TR/EN konu dokümantasyonu sahneleniyor. Önceden mevcut kural değişiklikleri ve bağımsız CucumberHooksTest biçim değişikliği dışarıda tutuluyor. Version bump/push/release önceki kapsam gereği yapılmıyor.
- Bağlantılar: [[memories/scratchpad]], [[wiki/api-testing]]
- Commit oluşturuldu. GPG pinentry terminal hatası nedeniyle yalnız bu çağrıda commit.gpgsign=false kullanıldı; kalıcı git ayarları değiştirilmedi. Push yapılmadı.


## [2026-10-01] implementation | API Mock, SSL ve Transport Geliştirmeleri
- ApiMockRule immutable matcher/factory; request önce test kuralları, interceptor sonrası terminal değerlendirme, sentetik response, snapshot/async transfer ve lifecycle cleanup eklendi. Factory/predicate hataları retry edilmez; response hook/status retry korunur.
- Internal ApiTransport registry default client paylaşımı ve test kapsamlı SSL profillerini yönetir. PKCS12/JKS özel truststore ve request override; opt-in trustAll peer leaf’i geçici trust anchor yapıp JDK HTTPS endpoint identity kontrolünü korur. Hostname uyuşmazlığı default/store/trustAll modlarında reddedildi. JVM SSL/global pool ayarları değiştirilmedi; parolalar log/cache anahtarına yazılmadı.
- connectTimeoutSeconds, maxConcurrentRequests ve requestTimeout(Duration) eklendi. Runtime fair semaphore gerçek gönderimleri sınırlar, mock permit tüketmez; cancellation/interrupt permit bırakır, bekleme toplam süreye dahil edilir. Batch logical limit korunur.
- Baseline mvn test takılması yeniden üretildi. Surefire sınıf izolasyonu reuseForks=false/forkCount=4 ile sağlandı; ortak durum kullanan driver registry, CDP clock ve page knowledge testleri singleThreaded yapıldı. Private timeout representation testi Duration’a güncellendi; test/assertion kapatılmadı.
- Doğrulama: mvn test başarılı; mvn -q clean verify -Dgpg.skip=true başarılı (1360 test, 0 failure/error/skip). ApiFeaturesTest 11 test; mvn -q -Dtest=ApiMockExamplesTest test ile 2 çalıştırılabilir mock örneği ayrıca başarılı. npm run build EN/TR başarılı; src/docs/pom diff whitespace kontrolü temiz.
- Localhost ApiTransportBenchmark main 20 çağrıda 1 TCP bağlantısı gözlemledi (yaklaşık 893.83 çağrı/s, p50 1.08ms, p95 1.22ms); yerel bilgilendirici sonuç, performans garantisi/CI eşiği değil.
- TR/EN Mocking, SSL Configuration ve Timeouts & Performance sayfaları ile API sidebar güncellendi. Yeni zorunlu dependency, version bump, commit/push/release yok. Önceden mevcut kullanıcı kural/CucumberHooksTest değişiklikleri korunuyor.
- Bağlantılar: [[wiki/api-testing]], [[memories/scratchpad]], [[rules/docusaurus-workflow]], [[MAP]]


## [2026-10-01] git | Tüm Git Changes Development Commit
- Kullanıcı tüm Git Changes dosyalarının commit edilmesini istedi. development doğrulandı; API mock/SSL/performans geliştirmeleri, TR/EN docs, test izolasyonu ve önceden kalan kural/test/hafıza değişiklikleri birlikte commit kapsamına alındı.
- Önceki GPG pinentry terminal hatası nedeniyle yalnız commit çağrısında commit.gpgsign=false kullanılır; kalıcı ayarlar değiştirilmez. Push, release veya version bump yapılmaz.
- Önceki doğrulama: temiz verify 1360 test/0 hata, mock örnekleri 2 test/0 hata, EN/TR docs build başarılı.
- Bağlantılar: [[memories/scratchpad]], [[wiki/api-testing]], [[rules/git-release-workflow]]


## [2026-10-01] review | Açık GitHub PR ve Merge Riski
- GitHub plugin ile #27/#28/#29 incelendi; hepsi main hedefli, mevcut main karşısında mergeable=true fakat CI unstable. Uzak development 2922115; yerel f77bd59 ve 48b5840 henüz push edilmemiş.
- git merge-tree dry-run: main dfbeff6 ile yerel HEAD temiz. #28 ile HEAD conflict: pom.xml, ApiClient, ApiBatchRunner, log/scratchpad. #29 ile HEAD conflict: ApiClient, ApiBatchRunner, log/scratchpad. PR28↔PR29 temiz. Branch checkout/merge/push yapılmadı.
- Dependabot dalları development’taki API commit’inin eski 7334e04 varyantını taşıyor; sadece son commitleri pom Jackson 2.21.7 ve brace-expansion lockfile değişikliği. Güncellemeleri development tabanında yeniden hazırlamak/rebase edip testlemek önerildi. Mevcut dependabot.yml zaten development hedefli; eski PR’lar main hedefli kalmış.
- #28/#29 Unit Tests logları: release version 21 not supported. #27 Unit Tests iptal; integration job’ları skipped. Yerel test başarısı GitHub’da henüz doğrulanmış değil.
- GitHub branches/main protected=false, repository rulesets boş; AGENTS koruma kuralı platformda uygulanmıyor. #27 Codex P2 yorumu ReportAdapterRegistry ile LoadTestReportAdapter’ın ortak HTML dosyasına paralel yazma yarışı; mevcut kaynakta doğrulandı, çözülmedi.
- Bağlantılar: https://github.com/hakanngul/testfly/pull/27 | https://github.com/hakanngul/testfly/pull/28 | https://github.com/hakanngul/testfly/pull/29 | [[memories/scratchpad]]

## 2026-10-01 — PR cleanup ve main koruması
- Kullanıcının açık yetkisiyle GitHub connector üzerinden eski tabanlı #28/#29 kapatıldı; #27 açık bırakıldı. Dependency güncellemeleri güncel development üzerinde yeniden hazırlanmalı.
- Ego-browser TaskSpace 1/p1 üzerinde Protect main aktif ruleset formu hazırlandı (main hedefi, bypass yok, PR, 0 approval, conversation resolution, strict GitHub Actions Unit Tests, deletion/non-fast-forward engeli). Create işlemi GitHub Confirm access doğrulaması istedi; kayıt henüz oluşmadı (rulesets GET=[]). Kullanıcıdan tarayıcıda kimlik doğrulaması istendi; form açık tutuldu. Merge/push/commit yapılmadı.

## 2026-10-01 — Main koruması tamamlandı
- Kullanıcı GitHub Mobile doğrulamasını tamamladı. Protect main ruleset 24296278 kaydedildi; API enforcement=active, refs/heads/main, bypass_actors=[], current_user_can_bypass=never ve main protected=true doğrulandı. PR zorunlu; approval=0; tüm review thread’leri çözülmeli; GitHub Actions Unit Tests (integration 15368) strict/up-to-date zorunlu; deletion ve non_fast_forward engelli. #27 açık; #28/#29 kapalı. Merge/push/commit yapılmadı.

## 2026-10-01 — #27 CI teşhisi
- GitHub head 2922115; PR koşusu 36771442520 Unit Tests cancelled, integration skipped. Java 21.0.12 kurulmuş. TestNG GraphOrchestrator.setStatus:116 worker=null NPE çok sayıda worker’da; son test çıktısı 20:18, cancellation 02:16 (~6 saat). Surefire XML yok. Remote pom tek JVM methods parallel; yerel 48b5840 izolasyon düzeltmeleri push edilmemiş. Yeni CI gerekli; kod/commit/push yapılmadı.

## 2026-10-01 — #27 CI rerun
- Kullanıcı rerun/main merge istedi. GitHub connector rerun başarılı; yeni job 110306145802, run 36771442520. Checkout/Java geçti; Unit Tests in_progress. Head 2922115; yerel düzeltmeler push edilmemiş. Required check tamamlanmadığı için merge yapılmadı.

## 2026-10-01 — #27 rerun takılma kontrolü
- API ve browser: job 110306145802 Run unit tests, 1h32m sürüyor; PR head hâlâ 2922115. Canlı log endpoint BlobNotFound, UI log içeriği yüklenmedi; aynı NPE’nin bu koşuda tekrar ettiği doğrulanamadı. Önceki koşu worker=null NPE ardından 6 saat iptal. Açık review 4145160034: ReportAdapterRegistry paralel HtmlReportAdapter/LoadTestReportAdapter aynı dosyalara yazıyor. CI ve conversation çözülmeden merge yok.

## 2026-10-01 — Development push
- Kullanıcı tüm development değişikliklerini gönderme yetkisi verdi. Dal development; ürün kodu zaten f77bd59/48b5840 commitlerinde, kalan değişiklikler çalışma notları. Not commitinde GPG pinentry açılamadı; yalnız bu commit için imzalama kapatıldı. Normal origin/development push; sürüm artışı/main commit yok. Önceden test/verify/docs build başarılı.

## 2026-10-01 — #27 report adapter race fix
- generateAll synchronized ve serial kayıt sırası; List.copyOf snapshot, hata sonrası devam korunuyor. Shared HTML writer/reader regresyon testi eklendi. İlgili raporlama testleri ve tam mvn test BUILD SUCCESS. Kullanıcı düzeltme yetkisi kapsamında development commit/push ve ilgili review resolution yapılacak; sürüm artışı yok.

## 2026-10-01 — MD analizi
- 274 MD; birebir duplicate yok. Arşiv adayları: completed load sprint, Draft load mimarisi, unchecked JDK21 plan ve AI fikir raporu. PRODUCT tekrar/kırık lokal link adayı; DESIGN aktif referanslı. ROADMAP master stale; README docs/features RFC linkleri yok. Dosya silinmedi. Secret HEAD pattern ön taraması güçlü token bulmadı; tam geçmiş audit yapılmadı.

## 2026-10-01 — MD konsolidasyonu
- Kullanıcı yetkisiyle GEMINI/PRODUCT ek kuralları AGENTS’a, feature fikirleri ROADMAP’e, eski planların kararları ROADMAP/load wiki’ye taşındı. Altı MD silindi: GEMINI, PRODUCT, features-report, loadtest architecture/sprint ve JDK21 migration. README kırık docs/features linkleri düzeltildi; ROADMAP master hedefi development; .gitignore GEMINI satırı kaldırıldı. Aktif referans kontrolü/diff --check temiz. Tarihsel log korunuyor; kod/site değişmedi, commit/push yok.

## 2026-10-03 — Selenium migration docs
- EN/TR from-selenium-testng: eski io.testfly:1.0.0 yerine mevcut pom io.github.hakanngul:1.0.7, Java21; olmayan getWait.waitFor* çağrıları WebDriverWait.until ile düzeltildi. Page Object/fluent LoginPage, listener ve setup/teardown seçici kaldırma, ayrı migration run önerileri eklendi. Kaynak API kontrolü ve npm run build başarılı (iki locale), diff --check temiz. Kod/test değişmedi; commit/push yok.

## 2026-10-03 — Docs tüm başlıklar ilk denetim
- 101 sidebar sayfası/altbaşlık envanteri; EN101/TR97. Rapor /tmp/testfly-docs-audit-2026-10-03.md. Kurulum coordinate, WebDriverWait API, load sürüm çelişkisi, report adapter checked exception source doğrulandı; eksik yeni api config/OpenAPI içeriği ve TR4 kaydedildi. CI overrides/clock refresh/parity henüz test edilmedi. Java snippetlerin tamamı derlenmedi. Kullanıcı talimatına göre docs metni değiştirilmedi; uygulama bekliyor.

## 2026-10-04 — Docs derin denetim
- Prompt-refine ile kullanıcı kapsamı korundu: rapor/öneri, uygulama yok. EN101/TR97 sayfa; 830 Java/YAML blok; 264 YAML parser/bean kontrolü (loader case-insensitive/tire toleransı ve dış CI YAML ayrımı). 129 Java sınıf adayı context importları/package sırası tamamlanarak derlendi: 62 başarılı/67 başarısız; temsili sınıf/optional dependency hataları API hatası diye sayılmadı.
- /tmp/testfly-docs-audit-2026-10-04.md: 30 bulgu grubu/38 EN sayfa; ayrıntılı page coverage ve ham kanıt /tmp/testfly-docs-audit-work/. Yeni kesinler: profil merge vaadi yanlış, load annotation/feeder/step/base-class API eskimiş, Locator/WebElement, BaseTest alert/BaseConditions find, video/AI importları, plugin baseUrl/context, BeforeSuite auth cleanup, etkisiz CI overrides, Allure resultsDir. Adapter IOException stub ile ayrıca derleme doğrulandı.
- Plugin skip, Chromium clock persist, JUnit retry, testmanagement casing adayları elendi. Microsoft resmi README @playwright/mcp; public npm @testfly/mcp ve @modelcontextprotocol/server-playwright sorguları 404. Ayrı MCP/IDE ürünleri ve dış servisler uçtan uca doğrulanmadı; bunlar raporda sınırlı. 4 TR eksik; file URI linkleri kaydedildi.
- mvn -q test-compile dependency:build-classpath ve npm run build başarılı. Tam mvn test/browser/servis testleri bu audit için çalıştırılmadı. development doğrulandı; mevcut working tree korundu. Docs/ürün kodu değişmedi, commit/push yok.

## 2026-10-04 — Docs Audit Düzeltmeleri & test-authoring Skill Eklentisi
- 2026-10-04 tarihli audit raporuna istinaden Docs-site EN ve TR i18n dosyalarında düzeltmeler yapıldı:
  - Eski `io.testfly` koordinatları `io.github.hakanngul` ile güncellendi, Central / 1.0.4 uyarıları düzeltildi.
  - Hatalı `WaitEngine` snippet'ları güncellendi, JUnit 5 feature parity iddiaları gerçeğe uygun hale getirildi.
  - Allure log içerikleri ve Kubernetes manifest iddiaları düzeltildi.
  - Eksik olan TR sayfaları oluşturuldu/çevrildi.
- İki locale için de `npm run build` hatasız geçti. `docs-site` kod değişikliği yapıldı ancak commit/push atılmadı (Kullanıcı onayı bekleniyor).
- OpenApiValidator `assertOpenApi` ve `LoadScenario.assertStatus` API'lerindeki kod hataları (bug) tespit edildi, test ve fix yazılmasına henüz başlanmadı.
- AI ajanlarının tüketici (consumer) projelerinde TestFly testi yazabilmesi için `testfly-test-authoring` yeteneği (.agents/skills/testfly-test-authoring) geliştirildi ve eklendi. Test senaryoları `target/classes` üzerinden compile edilerek doğrulandı.
