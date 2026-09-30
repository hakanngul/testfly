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

- 2026-09-30: `automation-architecture` skill dosyası Nexor CRM içeriğinden temizlenerek TestFly SDET standartlarına (POM, SmartLocator, BaseApiTest) uygun hale getirildi.
- **[2026-09-30]** Web UI Test altyapısı (Java 21 LTS Locator, FuzzyHealingEngine ve SmartTriageEngine) mimari diyagramları (Architecture ve Workflow) `archify` aracı ile oluşturuldu ve HTML çıktıları üretildi.
- 2026-09-30: `automation-architecture` skill tamamen kaldırıldı (Nexor içerikleri). Yerine `testfly` skill'i oluşturuldu — 3 araştırmacı ajan ile kaynak kod derinlemesine incelenerek (BaseTest, BasePage, Locator, ApiClient, ApiResponse, ApiAuth, WaitEngine, DriverManager, LocatorAssert, PageAssert, StepLogger, SelfHealing, SPI, FrameworkBootstrap) profesyonel SDET mimari referans dokümanı yazıldı. MAP.md güncellendi.
- 2026-09-30: API Testing mimarisine WireMock (ApiMockServer, MockSupport), OpenAPI (OpenApiValidator, ApiResponse.assertOpenApi) ve Asenkron İstek (ApiClient.sendAsync, ApiBatchRunner) yetenekleri opsiyonel bağımlılıklarla (optional=true) eklendi.
- 2026-09-30: Kullanıcı isteğiyle Rimz 0.4.3 kurulumu tamamlandı: Homebrew tmux 3.7c; kullanıcı yapılandırmasında tmux backend, Europe/Istanbul, `agy.agent=antigravity`, `peer.layout=codex,agy+term`. Codex ve Antigravity hook'ları mevcut ayarlara eklendi; Codex başlangıç ekranından Rimz hook güveni onaylandı. `rimz-testfly-118677` odasında Codex ve agy boş interaktif oturumları, ayrıca terminal açıldı. agy 1.2.14 açılışta statusLine `_rimz_managed` alanını kaldırdığı için doctor yanlış eksik hook bildirdi; hook kurulumu tekrarlandı. Web erişimi için ttyd kurulu değil; SSH hedefi verilmedi. Proje kodu değiştirilmedi. Bağlantı: [[memories/scratchpad]].
- 2026-09-30: Rimz v0.4.3 resmi Antigravity adaptör belgesi üzerinden GUI sınırı doğrulandı: süreç eşleştirme yalnızca `agy`; `antigravity` masaüstü süreçleri kapsam dışı. Kullanıcıya masaüstünde proje açma, AGENTS.md okuma ve eşzamanlı düzenlemeler için ayrı worktree kullanma akışı açıklandı. Bağlantı: [[memories/scratchpad]].
- 2026-09-30: Ghostty 1.3.1 kullanıcı config.ghostty dosyası yedeklenerek geliştirici kullanımına düzenlendi. `Moonkai` yazımı `Monokai Pro Machine` olarak düzeltildi; font `JetBrainsMono Nerd Font Mono`. Standart Cmd+W/Ctrl+C/Cmd+S davranışları geri getirildi; özel Shift+Enter kaldırıldı, sol Option Alt oldu; Rimz bildirimleri ve pencere durum kaydı açıldı. Kaydedilen dosya +validate-config ile doğrulandı. Yedek: config.ghostty.backup-20260930-230358. Bağlantı: [[memories/scratchpad]].
